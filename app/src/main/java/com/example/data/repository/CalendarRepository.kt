package com.example.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.dao.EventDao
import com.example.data.model.AppError
import com.example.data.model.CalendarSyncDiagnostics
import com.example.data.model.EventEntity
import com.example.data.model.OperationResult
import com.example.data.model.SyncRepairReport
import com.example.data.pref.WidgetPreferences
import com.example.data.pref.WidgetPreferencesManager
import com.example.util.AppLogger
import com.example.util.CalendarColorMapper
import com.example.util.CalendarEventColorProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CalendarAccountInfo(
    val id: Long,
    val accountName: String,
    val displayName: String,
    val accountType: String,
    val isPrimary: Boolean,
    val color: Int = 0,
    val colorHex: String = "#1A73E8",
    val isWritable: Boolean = true,
    val isVisible: Boolean = true,
    val isSyncEventsEnabled: Boolean = true
)

class CalendarRepository(
    private val context: Context,
    private val eventDao: EventDao,
    private val preferencesManager: WidgetPreferencesManager = WidgetPreferencesManager.getInstance(context)
) {
    private val diagnosticsHelper = CalendarSyncDiagnosticsHelper(context)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()
    val isFetching = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(preferencesManager.getLastSuccessfulSyncMillis())
    val lastSyncTime = _lastSyncTime.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _hasCalendarPermission = MutableStateFlow(checkCalendarPermission())
    val hasCalendarPermission = _hasCalendarPermission.asStateFlow()

    private val _syncedAccountName = MutableStateFlow<String?>(null)
    val syncedAccountName = _syncedAccountName.asStateFlow()

    private val _availableCalendars = MutableStateFlow<List<CalendarAccountInfo>>(emptyList())
    val availableCalendars = _availableCalendars.asStateFlow()

    @Volatile
    private var _lastQueryError: String? = null
    val lastQueryError: String?
        get() = _lastQueryError

    init {
        updatePermissionState()
    }

    private var _hasExplicitPermissionOverride: Boolean? = null
    @Volatile
    private var _simulatedSyncErrorForTesting: AppError? = null

    fun checkCalendarPermission(): Boolean {
        if (_hasExplicitPermissionOverride != null) return _hasExplicitPermissionOverride == true
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: RuntimeException) {
            AppLogger.w(AppLogger.TAG_REPO, "Failed checking READ_CALENDAR permission", e)
            false
        }
    }

    fun checkWriteCalendarPermission(): Boolean {
        if (_hasExplicitPermissionOverride != null) return _hasExplicitPermissionOverride == true
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: RuntimeException) {
            AppLogger.w(AppLogger.TAG_REPO, "Failed checking WRITE_CALENDAR permission", e)
            false
        }
    }

    fun updatePermissionState(explicitGrant: Boolean? = null) {
        if (explicitGrant != null) {
            _hasExplicitPermissionOverride = explicitGrant
        }
        val granted = explicitGrant ?: checkCalendarPermission()
        _hasCalendarPermission.value = granted
        if (granted) {
            val cals = queryAvailableCalendars()
            _isConnected.value = cals.isNotEmpty()
        } else {
            _isConnected.value = false
        }
    }

    fun setFetchingStateForTesting(fetching: Boolean) {
        _isSyncing.value = fetching
    }

    fun setSyncErrorForTesting(error: AppError?) {
        _simulatedSyncErrorForTesting = error
    }

    fun getUpcomingEventsFlow(): Flow<List<EventEntity>> {
        val threshold = getStartOfTodayMillis()
        return eventDao.getUpcomingEventsFlow(threshold)
    }

    suspend fun getEventById(id: String): EventEntity? = withContext(Dispatchers.IO) {
        eventDao.getEventById(id)
    }

    /**
     * Synchronous query for AppWidget and initial loads.
     * Returns genuine events filtered strictly by [WidgetPreferences] (past-events toggle,
     * days horizon, and selected calendar sources).
     */
    fun getUpcomingEventsSync(): List<EventEntity> {
        val prefs = preferencesManager.getPreferences()
        val now = System.currentTimeMillis()
        val threshold = getStartOfTodayMillis(now)
        val cached = eventDao.getUpcomingEventsSync(threshold)
        if (cached.isNotEmpty()) {
            return filterEventsForPreferences(cached, prefs, now)
        }

        if (checkCalendarPermission()) {
            val deviceEvents = fetchAllDeviceCalendarEvents(maxOf(prefs.daysHorizon, 30))
            if (deviceEvents.isNotEmpty()) {
                val formatted = prepareDayHeaders(deviceEvents)
                try {
                    eventDao.insertEventsSync(formatted)
                } catch (e: Exception) {
                    AppLogger.w(AppLogger.TAG_REPO, "Failed caching events during sync widget read", e)
                }
                return filterEventsForPreferences(formatted, prefs, now)
            }
        }
        return emptyList()
    }

    /**
     * Synchronize events directly from native Android device Google Calendar accounts.
     * ZERO API KEYS NEEDED — uses native CalendarContract with Android login credentials.
     * ZERO MOCKUP DATA — purges any legacy test relics.
     */
    suspend fun syncEvents(forceRefresh: Boolean = false): OperationResult<Int> = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        updatePermissionState()

        purgeLegacySeedEvents()

        try {
            _simulatedSyncErrorForTesting?.let { simulatedError ->
                AppLogger.w(AppLogger.TAG_REPO, "Simulated sync error triggered: ${simulatedError.message}")
                return@withContext OperationResult.Failure(simulatedError)
            }

            if (!checkCalendarPermission()) {
                AppLogger.w(AppLogger.TAG_REPO, "Sync skipped: READ_CALENDAR permission not granted")
                _isConnected.value = false
                return@withContext OperationResult.Failure(
                    AppError.PermissionDenied(
                        permission = Manifest.permission.READ_CALENDAR,
                        message = context.getString(R.string.error_calendar_permission_required)
                    )
                )
            }

            if (forceRefresh) {
                CalendarColorMapper.loadColorsFromProvider(context.contentResolver, forceReload = true)
            }

            _lastQueryError = null
            val cals = queryAvailableCalendars()
            val prefs = preferencesManager.getPreferences()
            val fetchHorizon = maxOf(prefs.daysHorizon, 30)
            val realEvents = fetchAllDeviceCalendarEvents(fetchHorizon)
            _isConnected.value = cals.isNotEmpty() || realEvents.isNotEmpty()

            if (realEvents.isNotEmpty()) {
                val formatted = prepareDayHeaders(realEvents)
                eventDao.replaceSyncedEvents(formatted)
                _isConnected.value = true
            } else {
                eventDao.replaceSyncedEvents(emptyList())
            }
            if (forceRefresh) {
                recomputeDayHeaders()
            }
            val completedAt = System.currentTimeMillis()
            _lastSyncTime.value = completedAt
            preferencesManager.setLastSuccessfulSyncMillis(completedAt)
            AppLogger.d(AppLogger.TAG_REPO, "Synced ${realEvents.size} event(s) across ${cals.size} calendar(s)")
            OperationResult.Success(realEvents.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            AppLogger.e(AppLogger.TAG_REPO, "SecurityException while syncing CalendarContract", e)
            OperationResult.Failure(
                AppError.PermissionDenied(
                    permission = Manifest.permission.READ_CALENDAR,
                    message = context.getString(R.string.error_calendar_permission_required)
                )
            )
        } catch (e: Exception) {
            AppLogger.e(AppLogger.TAG_REPO, "Failed to sync events from CalendarContract", e)
            OperationResult.Failure(
                AppError.CalendarQueryFailed(
                    message = context.getString(R.string.error_sync_failed),
                    cause = e
                )
            )
        } finally {
            _isSyncing.value = false
            preferencesManager.notifyWidgetsChanged()
        }
    }

    private suspend fun purgeLegacySeedEvents() {
        try {
            for (i in 1..25) {
                eventDao.deleteEventById("seed_$i")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_REPO, "Failed purging legacy seed events", e)
        }
    }

    /**
     * Map calendar name and raw color to official Google Calendar palette colors.
     */
    fun getCalendarColor(calName: String, rawColorInt: Int): String {
        return CalendarColorMapper.resolveCalendarColor(calName, rawColorInt, contentResolver = context.contentResolver)
    }

    /**
     * Query all Google Calendar accounts & sub-calendars present on the device.
     */
    fun queryAvailableCalendars(): List<CalendarAccountInfo> {
        if (!checkCalendarPermission()) return emptyList()
        val list = mutableListOf<CalendarAccountInfo>()
        try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.CALENDAR_COLOR_KEY,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.SYNC_EVENTS
            )

            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val typeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val displayIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val primaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                val ownerIdx = cursor.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT)
                val colorIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
                val colorKeyIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR_KEY)
                val accessIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
                val visibleIdx = cursor.getColumnIndex(CalendarContract.Calendars.VISIBLE)
                val syncEventsIdx = cursor.getColumnIndex(CalendarContract.Calendars.SYNC_EVENTS)

                while (cursor.moveToNext()) {
                    val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                    val accountName = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else ""
                    val accountType = if (typeIdx != -1) cursor.getString(typeIdx) ?: "" else ""
                    val displayName = if (displayIdx != -1) cursor.getString(displayIdx) ?: accountName else accountName
                    val isPrimary = if (primaryIdx != -1) cursor.getInt(primaryIdx) == 1 else false
                    val owner = if (ownerIdx != -1) cursor.getString(ownerIdx) ?: "" else ""
                    val color = if (colorIdx != -1) cursor.getInt(colorIdx) else 0
                    val colorKey = if (colorKeyIdx != -1) cursor.getString(colorKeyIdx) else null
                    val accessLevel = if (accessIdx != -1) cursor.getInt(accessIdx) else CalendarContract.Calendars.CAL_ACCESS_OWNER
                    val isWritable = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                    val isVisible = if (visibleIdx != -1 && !cursor.isNull(visibleIdx)) cursor.getInt(visibleIdx) != 0 else true
                    val isSyncEventsEnabled = if (syncEventsIdx != -1 && !cursor.isNull(syncEventsIdx)) cursor.getInt(syncEventsIdx) != 0 else true
                    val hex = CalendarColorMapper.resolveCalendarColor(displayName, color, colorKey, context.contentResolver)

                    list.add(
                        CalendarAccountInfo(
                            id = id,
                            accountName = accountName.ifBlank { owner },
                            displayName = displayName,
                            accountType = accountType,
                            isPrimary = isPrimary,
                            color = color,
                            colorHex = hex,
                            isWritable = isWritable,
                            isVisible = isVisible,
                            isSyncEventsEnabled = isSyncEventsEnabled
                        )
                    )
                }
            }

            _availableCalendars.value = list

            val googleCal = list.firstOrNull { it.accountType == "com.google" } ?: list.firstOrNull { it.isPrimary } ?: list.firstOrNull()
            if (googleCal != null) {
                _syncedAccountName.value = googleCal.accountName.ifBlank { googleCal.displayName }
            } else {
                _syncedAccountName.value = null
            }
        } catch (e: SecurityException) {
            _lastQueryError = "Permission denied querying calendars: ${e.message}"
            AppLogger.w(AppLogger.TAG_REPO, "Permission denied querying CalendarContract.Calendars", e)
        } catch (e: Exception) {
            _lastQueryError = "Calendars query failed: ${e.message ?: e.javaClass.simpleName}"
            AppLogger.e(AppLogger.TAG_REPO, "Error querying CalendarContract.Calendars", e)
        }
        return list
    }

    /**
     * Fetches events from all permitted calendars across [horizonDays] so switching filters
     * and horizons in the Customization Studio works instantaneously from local cache.
     */
    private fun fetchAllDeviceCalendarEvents(horizonDays: Int): List<EventEntity> {
        if (!checkCalendarPermission()) return emptyList()
        val now = System.currentTimeMillis()
        val startMillis = getStartOfTodayMillis(now)
        val endMillis = getHorizonEndMillis(horizonDays.coerceIn(1, 60), now)

        val instanceEvents = queryInstances(startMillis, endMillis, calendarIds = null)
        if (instanceEvents.isNotEmpty()) return instanceEvents
        return fetchDirectEventsInternal(startMillis, endMillis, calendarIds = null)
    }

    /**
     * Query real upcoming events from Android's CalendarContract respecting current [WidgetPreferences].
     */
    fun fetchDeviceCalendarEvents(
        customStartMillis: Long? = null,
        customEndMillis: Long? = null
    ): List<EventEntity> {
        val events = mutableListOf<EventEntity>()
        if (!checkCalendarPermission()) return events

        val prefs = preferencesManager.getPreferences()
        if (!prefs.isAllCalendarsSelected && prefs.selectedCalendarIds.isEmpty()) {
            return events
        }

        val now = System.currentTimeMillis()
        val startMillis = customStartMillis ?: getStartOfTodayMillis(now)
        val horizonDays = prefs.daysHorizon.coerceIn(1, 60)
        val endMillis = customEndMillis ?: getHorizonEndMillis(horizonDays, now)

        val calendarFilter = if (!prefs.isAllCalendarsSelected) {
            prefs.selectedCalendarIds
        } else {
            null
        }

        val instanceEvents = queryInstances(startMillis, endMillis, calendarFilter)
        events.addAll(instanceEvents)

        if (events.isEmpty()) {
            val directEvents = fetchDirectEventsInternal(startMillis, endMillis, calendarFilter)
            events.addAll(directEvents)
        }
        return events
    }

    /**
     * Queries the Android native CalendarContract.Instances table for recurring and non-recurring
     * events within [startMillis]..[endMillis].
     */
    fun queryInstances(
        startMillis: Long,
        endMillis: Long,
        calendarIds: Set<Long>? = null
    ): List<EventEntity> {
        val events = mutableListOf<EventEntity>()
        if (!checkCalendarPermission()) return events
        if (calendarIds != null && calendarIds.isEmpty()) return events

        try {
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startMillis)
            ContentUris.appendId(builder, endMillis)
            val uri = builder.build()

            val fullProjection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.EVENT_COLOR,
                CalendarContract.Instances.CALENDAR_COLOR,
                CalendarContract.Instances.EVENT_COLOR_KEY,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.RRULE,
                CalendarContract.Instances.EVENT_TIMEZONE
            )

            val coreProjection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_COLOR,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.RRULE
            )

            var selection: String? = null
            var selectionArgs: Array<String>? = null

            if (!calendarIds.isNullOrEmpty()) {
                val placeholders = calendarIds.joinToString(",") { "?" }
                selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
                selectionArgs = calendarIds.map { it.toString() }.toTypedArray()
            }

            val cursor = try {
                context.contentResolver.query(
                    uri,
                    fullProjection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Instances.BEGIN} ASC"
                )
            } catch (e: IllegalArgumentException) {
                AppLogger.w(AppLogger.TAG_REPO, "Extended Instances projection rejected; retrying with core projection", e)
                context.contentResolver.query(
                    uri,
                    coreProjection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Instances.BEGIN} ASC"
                )
            } catch (e: SQLiteException) {
                AppLogger.w(AppLogger.TAG_REPO, "SQLiteException on extended Instances projection; retrying with core projection", e)
                context.contentResolver.query(
                    uri,
                    coreProjection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Instances.BEGIN} ASC"
                )
            }

            cursor?.use { c ->
                val idIndex = c.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIndex = c.getColumnIndex(CalendarContract.Instances.TITLE)
                val descIndex = c.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val locIndex = c.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val beginIndex = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIndex = c.getColumnIndex(CalendarContract.Instances.END)
                val allDayIndex = c.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val displayColorIndex = c.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)
                val eventColorIndex = c.getColumnIndex(CalendarContract.Instances.EVENT_COLOR)
                val calColorIndex = c.getColumnIndex(CalendarContract.Instances.CALENDAR_COLOR)
                val eventColorKeyIndex = c.getColumnIndex(CalendarContract.Instances.EVENT_COLOR_KEY)
                val calNameIndex = c.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                val calIdIndex = c.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
                val rruleIndex = c.getColumnIndex(CalendarContract.Instances.RRULE)

                while (c.moveToNext()) {
                    val eventIdLong = if (idIndex != -1 && !c.isNull(idIndex)) c.getLong(idIndex) else null
                    val eventIdStr = eventIdLong?.toString() ?: ""
                    val title = if (titleIndex != -1) c.getString(titleIndex) ?: "(No title)" else "(No title)"
                    val desc = if (descIndex != -1) c.getString(descIndex) else null
                    val loc = if (locIndex != -1) c.getString(locIndex) else null
                    val rawBegin = if (beginIndex != -1) c.getLong(beginIndex) else 0L
                    val rawEnd = if (endIndex != -1) c.getLong(endIndex) else rawBegin + 3600000L
                    val allDay = if (allDayIndex != -1) c.getInt(allDayIndex) == 1 else false
                    val begin = if (allDay) utcAllDayToLocalMillis(rawBegin) else rawBegin
                    val end = if (allDay) utcAllDayToLocalMillis(rawEnd) else rawEnd
                    val displayColor = if (displayColorIndex != -1 && !c.isNull(displayColorIndex)) c.getInt(displayColorIndex) else null
                    val eventColor = if (eventColorIndex != -1 && !c.isNull(eventColorIndex)) c.getInt(eventColorIndex) else null
                    val calColor = if (calColorIndex != -1 && !c.isNull(calColorIndex)) c.getInt(calColorIndex) else null
                    val eventColorKey = if (eventColorKeyIndex != -1 && !c.isNull(eventColorKeyIndex)) c.getString(eventColorKeyIndex) else null
                    val calName = if (calNameIndex != -1) c.getString(calNameIndex) ?: "Google Calendar" else "Google Calendar"
                    val sourceCalId = if (calIdIndex != -1 && !c.isNull(calIdIndex)) c.getLong(calIdIndex) else -1L
                    val rrule = if (rruleIndex != -1) c.getString(rruleIndex) else null

                    val colorHex = CalendarColorMapper.resolveEventColor(
                        CalendarEventColorProperties(
                            eventColor = eventColor,
                            calendarColor = calColor,
                            displayColor = displayColor,
                            eventColorKey = eventColorKey,
                            calendarDisplayName = calName
                        ),
                        contentResolver = context.contentResolver
                    )

                    val instanceUniqueId = "dev_${eventIdStr}_$begin"

                    events.add(
                        EventEntity(
                            id = instanceUniqueId,
                            calendarId = calName,
                            sourceCalendarId = sourceCalId,
                            parentEventId = eventIdLong,
                            title = title,
                            description = desc,
                            location = loc,
                            startMillis = begin,
                            endMillis = if (end <= begin) begin + 3600000L else end,
                            isAllDay = allDay,
                            isRecurring = !rrule.isNullOrBlank(),
                            colorHex = colorHex,
                            htmlLink = if (eventIdLong != null) "content://com.android.calendar/events/$eventIdLong" else null,
                            dayHeader = computeDayHeader(begin),
                            isFirstOfDay = false
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            _lastQueryError = "Permission denied querying Instances: ${e.message}"
            AppLogger.w(AppLogger.TAG_REPO, "Permission denied querying CalendarContract.Instances", e)
        } catch (e: Exception) {
            _lastQueryError = "Instances query failed: ${e.message ?: e.javaClass.simpleName}"
            AppLogger.e(AppLogger.TAG_REPO, "Error querying CalendarContract.Instances", e)
        }
        return events
    }

    /**
     * Reactive Flow that queries CalendarContract.Instances based on the specified time range.
     */
    fun queryInstancesFlow(
        startMillis: Long,
        endMillis: Long,
        calendarIds: Set<Long>? = null
    ): Flow<List<EventEntity>> = flow {
        emit(queryInstances(startMillis, endMillis, calendarIds))
    }.flowOn(Dispatchers.IO)

    private fun fetchDirectEventsInternal(
        customStartMillis: Long? = null,
        customEndMillis: Long? = null,
        calendarIds: Set<Long>? = null
    ): List<EventEntity> {
        val events = mutableListOf<EventEntity>()
        if (calendarIds != null && calendarIds.isEmpty()) return events
        try {
            val prefs = preferencesManager.getPreferences()
            val now = System.currentTimeMillis()
            val startMillis = customStartMillis ?: getStartOfTodayMillis(now)
            val horizonDays = prefs.daysHorizon.coerceIn(1, 60)
            val endMillis = customEndMillis ?: getHorizonEndMillis(horizonDays, now)

            val fullProjection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.DISPLAY_COLOR,
                CalendarContract.Events.EVENT_COLOR,
                CalendarContract.Events.CALENDAR_COLOR,
                CalendarContract.Events.EVENT_COLOR_KEY,
                CalendarContract.Events.CALENDAR_DISPLAY_NAME,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.RRULE
            )

            val coreProjection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.CALENDAR_COLOR,
                CalendarContract.Events.CALENDAR_DISPLAY_NAME,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.RRULE
            )

            // Include ongoing / multi-day events that began before startMillis and end within or after the window
            val selectionBuilder = StringBuilder(
                "${CalendarContract.Events.DELETED} = 0 AND ${CalendarContract.Events.DTSTART} <= ? AND (${CalendarContract.Events.DTEND} >= ? OR (${CalendarContract.Events.DTEND} IS NULL AND ${CalendarContract.Events.DTSTART} >= ?))"
            )
            val argsList = mutableListOf(endMillis.toString(), startMillis.toString(), startMillis.toString())

            if (!calendarIds.isNullOrEmpty()) {
                val placeholders = calendarIds.joinToString(",") { "?" }
                selectionBuilder.append(" AND ${CalendarContract.Events.CALENDAR_ID} IN ($placeholders)")
                calendarIds.forEach { argsList.add(it.toString()) }
            }

            val cursor = try {
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    fullProjection,
                    selectionBuilder.toString(),
                    argsList.toTypedArray(),
                    "${CalendarContract.Events.DTSTART} ASC"
                )
            } catch (e: IllegalArgumentException) {
                AppLogger.w(AppLogger.TAG_REPO, "Extended Events projection rejected; retrying with core projection", e)
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    coreProjection,
                    selectionBuilder.toString(),
                    argsList.toTypedArray(),
                    "${CalendarContract.Events.DTSTART} ASC"
                )
            } catch (e: SQLiteException) {
                AppLogger.w(AppLogger.TAG_REPO, "SQLiteException on extended Events projection; retrying with core projection", e)
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    coreProjection,
                    selectionBuilder.toString(),
                    argsList.toTypedArray(),
                    "${CalendarContract.Events.DTSTART} ASC"
                )
            }

            cursor?.use { c ->
                val idIdx = c.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
                val descIdx = c.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val locIdx = c.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val startIdx = c.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = c.getColumnIndex(CalendarContract.Events.DTEND)
                val allDayIdx = c.getColumnIndex(CalendarContract.Events.ALL_DAY)
                val displayColorIdx = c.getColumnIndex(CalendarContract.Events.DISPLAY_COLOR)
                val eventColorIdx = c.getColumnIndex(CalendarContract.Events.EVENT_COLOR)
                val calColorIdx = c.getColumnIndex(CalendarContract.Events.CALENDAR_COLOR)
                val eventColorKeyIdx = c.getColumnIndex(CalendarContract.Events.EVENT_COLOR_KEY)
                val calNameIdx = c.getColumnIndex(CalendarContract.Events.CALENDAR_DISPLAY_NAME)
                val calIdIdx = c.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
                val rruleIdx = c.getColumnIndex(CalendarContract.Events.RRULE)

                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val title = if (titleIdx != -1) c.getString(titleIdx) ?: "(No title)" else "(No title)"
                    val desc = if (descIdx != -1) c.getString(descIdx) else null
                    val loc = if (locIdx != -1) c.getString(locIdx) else null
                    val rawStart = if (startIdx != -1) c.getLong(startIdx) else 0L
                    val rawEnd = if (endIdx != -1 && !c.isNull(endIdx)) c.getLong(endIdx) else rawStart + 3600000L
                    val allDay = if (allDayIdx != -1) c.getInt(allDayIdx) == 1 else false
                    val start = if (allDay) utcAllDayToLocalMillis(rawStart) else rawStart
                    val end = if (allDay) utcAllDayToLocalMillis(rawEnd) else rawEnd
                    val displayColor = if (displayColorIdx != -1 && !c.isNull(displayColorIdx)) c.getInt(displayColorIdx) else null
                    val eventColor = if (eventColorIdx != -1 && !c.isNull(eventColorIdx)) c.getInt(eventColorIdx) else null
                    val calColor = if (calColorIdx != -1 && !c.isNull(calColorIdx)) c.getInt(calColorIdx) else null
                    val eventColorKey = if (eventColorKeyIdx != -1 && !c.isNull(eventColorKeyIdx)) c.getString(eventColorKeyIdx) else null
                    val calName = if (calNameIdx != -1) c.getString(calNameIdx) ?: "Google Calendar" else "Google Calendar"
                    val sourceCalId = if (calIdIdx != -1 && !c.isNull(calIdIdx)) c.getLong(calIdIdx) else -1L
                    val rrule = if (rruleIdx != -1) c.getString(rruleIdx) else null

                    val colorHex = CalendarColorMapper.resolveEventColor(
                        CalendarEventColorProperties(
                            eventColor = eventColor,
                            calendarColor = calColor,
                            displayColor = displayColor,
                            eventColorKey = eventColorKey,
                            calendarDisplayName = calName
                        ),
                        contentResolver = context.contentResolver
                    )

                    events.add(
                        EventEntity(
                            id = "dev_${id}_$start",
                            calendarId = calName,
                            sourceCalendarId = sourceCalId,
                            parentEventId = id,
                            title = title,
                            description = desc,
                            location = loc,
                            startMillis = start,
                            endMillis = if (end <= start) start + 3600000L else end,
                            isAllDay = allDay,
                            isRecurring = !rrule.isNullOrBlank(),
                            colorHex = colorHex,
                            htmlLink = "content://com.android.calendar/events/$id",
                            dayHeader = computeDayHeader(start),
                            isFirstOfDay = false
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            _lastQueryError = "Permission denied querying Events: ${e.message}"
            AppLogger.w(AppLogger.TAG_REPO, "Permission denied querying CalendarContract.Events", e)
        } catch (e: Exception) {
            _lastQueryError = "Events query failed: ${e.message ?: e.javaClass.simpleName}"
            AppLogger.e(AppLogger.TAG_REPO, "Error querying CalendarContract.Events", e)
        }
        return events
    }

    private fun findPrimaryCalendarId(): Long? {
        if (!checkCalendarPermission()) return null
        try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.IS_PRIMARY
            )
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val typeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)

                var googleCalId: Long? = null
                var fallbackId: Long? = null

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    if (fallbackId == null) fallbackId = id
                    val acctType = if (typeIdx != -1) cursor.getString(typeIdx) ?: "" else ""
                    if (googleCalId == null && acctType == "com.google") {
                        googleCalId = id
                    }
                }
                return googleCalId ?: fallbackId
            }
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_REPO, "Error finding primary calendar ID", e)
        }
        return null
    }

    fun ensureLocalCalendarExists(): Long? {
        if (!checkWriteCalendarPermission()) return null
        val existingId = findPrimaryCalendarId()
        if (existingId != null) return existingId

        return try {
            val targetAccount = _syncedAccountName.value ?: "Local Calendar"
            val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, targetAccount)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build()

            val values = ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, targetAccount)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, "Google Calendar")
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Google Calendar")
                put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF1A73E8.toInt())
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, targetAccount)
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            }

            val resultUri = context.contentResolver.insert(uri, values)
            resultUri?.let { ContentUris.parseId(it) }
        } catch (e: Exception) {
            AppLogger.e(AppLogger.TAG_REPO, "Failed to create fallback local calendar", e)
            null
        }
    }

    private fun buildEventContentValues(
        title: String,
        location: String?,
        description: String?,
        startMillis: Long,
        endMillis: Long,
        isAllDay: Boolean,
        colorHex: String,
        calendarId: Long
    ): ContentValues {
        val dtStart = if (isAllDay) localMillisToUtcAllDay(startMillis) else startMillis
        val dtEnd = if (isAllDay) {
            val utcEnd = localMillisToUtcAllDay(endMillis)
            if (utcEnd <= dtStart) dtStart + DAY_MILLIS else utcEnd
        } else {
            if (endMillis <= startMillis) startMillis + 3600000L else endMillis
        }
        val colorInt = CalendarColorMapper.parseColorInt(colorHex)

        return ContentValues().apply {
            put(CalendarContract.Events.DTSTART, dtStart)
            put(CalendarContract.Events.DTEND, dtEnd)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description?.trim()?.ifEmpty { null } ?: "Created via Agenda Widget")
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (isAllDay) "UTC" else TimeZone.getDefault().id)
            put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
            put(CalendarContract.Events.EVENT_COLOR, colorInt)
            if (!location.isNullOrBlank()) {
                put(CalendarContract.Events.EVENT_LOCATION, location.trim())
            } else {
                putNull(CalendarContract.Events.EVENT_LOCATION)
            }
        }
    }

    private fun writeEventToDeviceCalendar(
        title: String,
        location: String?,
        description: String?,
        startMillis: Long,
        endMillis: Long,
        isAllDay: Boolean,
        colorHex: String,
        targetCalendarId: Long?
    ): Pair<Long, Long>? {
        if (!checkWriteCalendarPermission()) return null
        val calendarId = targetCalendarId ?: findPrimaryCalendarId() ?: ensureLocalCalendarExists() ?: return null
        return try {
            val values = buildEventContentValues(
                title = title,
                location = location,
                description = description,
                startMillis = startMillis,
                endMillis = endMillis,
                isAllDay = isAllDay,
                colorHex = colorHex,
                calendarId = calendarId
            )
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val insertedId = uri?.let { ContentUris.parseId(it) } ?: return null
            insertedId to calendarId
        } catch (e: SecurityException) {
            AppLogger.w(AppLogger.TAG_REPO, "WRITE_CALENDAR denied writing event to provider", e)
            null
        } catch (e: Exception) {
            AppLogger.e(AppLogger.TAG_REPO, "Failed inserting event into CalendarContract", e)
            null
        }
    }

    /**
     * Add a genuine event to the chosen device calendar and local cache.
     */
    suspend fun addEvent(
        title: String,
        location: String?,
        startMillis: Long,
        endMillis: Long,
        colorHex: String = "#1A73E8",
        description: String? = null,
        isAllDay: Boolean = false,
        targetCalendarId: Long? = null
    ): OperationResult<EventEntity> = withContext(Dispatchers.IO) {
        try {
            val writeResult = writeEventToDeviceCalendar(
                title = title,
                location = location,
                description = description,
                startMillis = startMillis,
                endMillis = endMillis,
                isAllDay = isAllDay,
                colorHex = colorHex,
                targetCalendarId = targetCalendarId
            )
            val deviceEventId = writeResult?.first
            val resolvedCalId = writeResult?.second ?: targetCalendarId ?: -1L
            val calName = _availableCalendars.value.firstOrNull { it.id == resolvedCalId }?.displayName
                ?: _syncedAccountName.value
                ?: "Google Calendar"

            val id = if (deviceEventId != null) {
                "dev_${deviceEventId}_$startMillis"
            } else {
                "evt_${System.currentTimeMillis()}"
            }
            val dayHeader = computeDayHeader(startMillis)
            val newEvent = EventEntity(
                id = id,
                calendarId = calName,
                sourceCalendarId = resolvedCalId,
                parentEventId = deviceEventId,
                title = title.trim(),
                description = description?.trim()?.ifEmpty { null },
                location = location?.trim()?.ifEmpty { null },
                startMillis = startMillis,
                endMillis = if (endMillis <= startMillis) startMillis + 3600000L else endMillis,
                isAllDay = isAllDay,
                colorHex = colorHex,
                dayHeader = dayHeader,
                isFirstOfDay = false,
                isOfflineCreated = deviceEventId == null,
                htmlLink = if (deviceEventId != null) "content://com.android.calendar/events/$deviceEventId" else null
            )
            eventDao.insertEvent(newEvent)
            recomputeDayHeaders()
            _isConnected.value = true
            preferencesManager.notifyWidgetsChanged()
            AppLogger.d(
                AppLogger.TAG_REPO,
                "Added event id=$id (deviceSynced=${deviceEventId != null})"
            )
            OperationResult.Success(newEvent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(AppLogger.TAG_REPO, "Failed adding event '$title'", e)
            OperationResult.Failure(
                AppError.EventWriteFailed(
                    operation = "add",
                    message = context.getString(R.string.error_add_event_failed),
                    cause = e
                )
            )
        }
    }

    /**
     * Update an existing event in the device calendar and local Room cache.
     */
    suspend fun updateEvent(
        eventId: String,
        title: String,
        location: String?,
        description: String?,
        startMillis: Long,
        endMillis: Long,
        isAllDay: Boolean,
        colorHex: String,
        targetCalendarId: Long? = null
    ): OperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val existing = eventDao.getEventById(eventId)
            if (existing == null) {
                AppLogger.w(AppLogger.TAG_REPO, "Cannot update missing event id=$eventId")
                return@withContext OperationResult.Failure(
                    AppError.EventWriteFailed(
                        operation = "update",
                        message = context.getString(R.string.error_update_event_failed)
                    )
                )
            }
            val parentId = existing.parentEventId ?: parseParentEventId(eventId)
            val resolvedCalId = targetCalendarId ?: existing.sourceCalendarId.takeIf { it > 0L } ?: findPrimaryCalendarId() ?: -1L

            if (parentId != null && checkWriteCalendarPermission()) {
                try {
                    val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, parentId)
                    val values = buildEventContentValues(
                        title = title.trim(),
                        location = location,
                        description = description,
                        startMillis = startMillis,
                        endMillis = endMillis,
                        isAllDay = isAllDay,
                        colorHex = colorHex,
                        calendarId = if (resolvedCalId > 0L) resolvedCalId else 1L
                    )
                    context.contentResolver.update(updateUri, values, null, null)
                } catch (e: Exception) {
                    AppLogger.w(AppLogger.TAG_REPO, "Failed updating event id=$parentId in CalendarContract", e)
                }
            }

            val calName = _availableCalendars.value.firstOrNull { it.id == resolvedCalId }?.displayName
                ?: existing.calendarId

            val updated = existing.copy(
                calendarId = calName,
                sourceCalendarId = resolvedCalId,
                title = title.trim(),
                description = description?.trim()?.ifEmpty { null },
                location = location?.trim()?.ifEmpty { null },
                startMillis = startMillis,
                endMillis = if (endMillis <= startMillis) startMillis + 3600000L else endMillis,
                isAllDay = isAllDay,
                colorHex = colorHex,
                dayHeader = computeDayHeader(startMillis),
                updatedAt = System.currentTimeMillis()
            )
            eventDao.updateEvent(updated)
            recomputeDayHeaders()
            preferencesManager.notifyWidgetsChanged()
            AppLogger.d(AppLogger.TAG_REPO, "Updated event id=$eventId")
            OperationResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(AppLogger.TAG_REPO, "Failed updating event id=$eventId", e)
            OperationResult.Failure(
                AppError.EventWriteFailed(
                    operation = "update",
                    message = context.getString(R.string.error_update_event_failed),
                    cause = e
                )
            )
        }
    }

    /**
     * Delete an event from the device Google Calendar account and local cache.
     */
    suspend fun deleteEvent(id: String): OperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val existing = eventDao.getEventById(id)
            val rawId = existing?.parentEventId ?: parseParentEventId(id)
            if (rawId != null && checkWriteCalendarPermission()) {
                try {
                    val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, rawId)
                    context.contentResolver.delete(deleteUri, null, null)
                } catch (e: Exception) {
                    AppLogger.w(AppLogger.TAG_REPO, "Failed deleting event id=$rawId from CalendarContract", e)
                }
            }
            eventDao.deleteEventById(id)
            recomputeDayHeaders()
            preferencesManager.notifyWidgetsChanged()
            AppLogger.d(AppLogger.TAG_REPO, "Deleted event id=$id")
            OperationResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(AppLogger.TAG_REPO, "Failed deleting event id=$id", e)
            OperationResult.Failure(
                AppError.EventWriteFailed(
                    operation = "delete",
                    message = context.getString(R.string.error_delete_event_failed),
                    cause = e
                )
            )
        }
    }

    /**
     * Collects a live diagnostic snapshot of Android CalendarContract permissions, system sync
     * adapter flags, discovered calendars, and raw vs filtered event counts.
     */
    suspend fun collectSyncDiagnostics(): CalendarSyncDiagnostics = withContext(Dispatchers.IO) {
        val hasRead = checkCalendarPermission()
        val hasWrite = checkWriteCalendarPermission()
        val prefs = preferencesManager.getPreferences()
        val calendars = if (hasRead) queryAvailableCalendars() else emptyList()
        val upcoming60 = if (hasRead) {
            fetchAllDeviceCalendarEvents(diagnosticsHelper.getDiagnosticWindowDays())
        } else {
            emptyList()
        }
        diagnosticsHelper.buildDiagnostics(
            hasReadPermission = hasRead,
            hasWritePermission = hasWrite,
            calendars = calendars,
            prefs = prefs,
            upcoming60DayEvents = upcoming60,
            lastQueryError = _lastQueryError
        )
    }

    /**
     * Repairs disabled SYNC_EVENTS/VISIBLE flags on device calendars, enables account auto-sync
     * where permitted, requests an expedited manual sync on [CalendarContract.AUTHORITY], and
     * performs a fresh local cache sync.
     */
    suspend fun repairCalendarSyncAndRequestOsSync(): OperationResult<SyncRepairReport> = withContext(Dispatchers.IO) {
        try {
            if (!checkCalendarPermission()) {
                return@withContext OperationResult.Failure(
                    AppError.PermissionDenied(
                        permission = Manifest.permission.READ_CALENDAR,
                        message = context.getString(R.string.error_calendar_permission_required)
                    )
                )
            }
            val calendars = queryAvailableCalendars()
            val baseReport = diagnosticsHelper.repairAndRequestOsSync(
                calendars = calendars,
                hasWritePermission = checkWriteCalendarPermission()
            )
            val syncOutcome = syncEvents(forceRefresh = true)
            val syncedCount = when (syncOutcome) {
                is OperationResult.Success -> syncOutcome.data
                is OperationResult.Failure -> 0
            }
            OperationResult.Success(baseReport.copy(syncedEventsAfterRepair = syncedCount))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(AppLogger.TAG_SYNC, "Failed executing calendar sync repair", e)
            OperationResult.Failure(
                AppError.CalendarQueryFailed(
                    message = context.getString(R.string.error_sync_failed),
                    cause = e
                )
            )
        }
    }

    /**
     * Emits whenever Android's [CalendarContract] Events or Calendars tables change (for example,
     * when Google Calendar's background sync adapter finishes pulling cloud events).
     */
    fun observeCalendarChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }

        var registered = false
        if (checkCalendarPermission()) {
            try {
                context.contentResolver.registerContentObserver(
                    CalendarContract.Events.CONTENT_URI,
                    true,
                    observer
                )
                context.contentResolver.registerContentObserver(
                    CalendarContract.Calendars.CONTENT_URI,
                    true,
                    observer
                )
                registered = true
                AppLogger.d(AppLogger.TAG_REPO, "Registered CalendarContract ContentObserver")
            } catch (e: SecurityException) {
                AppLogger.w(AppLogger.TAG_REPO, "Permission denied registering CalendarContract observer", e)
            } catch (e: Exception) {
                AppLogger.w(AppLogger.TAG_REPO, "Unable to register CalendarContract observer", e)
            }
        }

        awaitClose {
            if (registered) {
                try {
                    context.contentResolver.unregisterContentObserver(observer)
                    AppLogger.d(AppLogger.TAG_REPO, "Unregistered CalendarContract ContentObserver")
                } catch (e: Exception) {
                    AppLogger.w(AppLogger.TAG_REPO, "Error unregistering CalendarContract observer", e)
                }
            }
        }
    }

    private suspend fun recomputeDayHeaders() {
        val threshold = getStartOfTodayMillis()
        val list = eventDao.getUpcomingEventsSync(threshold)
        val updated = prepareDayHeaders(list)
        eventDao.replaceAllEvents(updated)
    }

    companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L

        fun getStartOfTodayMillis(referenceMillis: Long = System.currentTimeMillis()): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = referenceMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        fun getHorizonEndMillis(daysHorizon: Int, referenceMillis: Long = System.currentTimeMillis()): Long {
            val safeDays = daysHorizon.coerceIn(1, 60)
            return getStartOfTodayMillis(referenceMillis) + (safeDays.toLong() * DAY_MILLIS)
        }

        /**
         * Filters cached events strictly according to [WidgetPreferences]:
         * - [WidgetPreferences.showPastEvents]: excludes already-finished events today when false
         * - [WidgetPreferences.daysHorizon]: enforces the upper date boundary
         * - [WidgetPreferences.isAllCalendarsSelected] / [WidgetPreferences.selectedCalendarIds]
         * Recomputes `isFirstOfDay` on the filtered result so date group headers stay accurate.
         */
        fun filterEventsForPreferences(
            events: List<EventEntity>,
            prefs: WidgetPreferences,
            nowMillis: Long = System.currentTimeMillis()
        ): List<EventEntity> {
            val startOfToday = getStartOfTodayMillis(nowMillis)
            val minEndMillis = if (prefs.showPastEvents) startOfToday else nowMillis
            val horizonEndMillis = getHorizonEndMillis(prefs.daysHorizon, nowMillis)

            val filtered = events.filter { event ->
                val matchesPastRule = event.endMillis >= minEndMillis
                val matchesHorizon = event.startMillis < horizonEndMillis
                val matchesCalendar = if (prefs.isAllCalendarsSelected) {
                    true
                } else if (prefs.selectedCalendarIds.isEmpty()) {
                    false
                } else {
                    event.sourceCalendarId in prefs.selectedCalendarIds
                }
                matchesPastRule && matchesHorizon && matchesCalendar
            }
            return prepareDayHeaders(filtered)
        }

        fun prepareDayHeaders(events: List<EventEntity>): List<EventEntity> {
            val sorted = events.sortedBy { it.startMillis }
            var lastDayTag = ""
            return sorted.map { event ->
                val dayTag = formatDayTag(event.startMillis)
                val isFirst = (dayTag != lastDayTag)
                lastDayTag = dayTag
                event.copy(
                    dayHeader = computeDayHeader(event.startMillis),
                    isFirstOfDay = isFirst
                )
            }
        }

        private fun parseParentEventId(id: String): Long? {
            if (!id.startsWith("dev_")) return null
            val parts = id.split("_")
            return if (parts.size >= 2) parts[1].toLongOrNull() else null
        }

        private fun utcAllDayToLocalMillis(utcMillis: Long): Long {
            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = utcMillis
            }
            return Calendar.getInstance().apply {
                set(
                    utcCal.get(Calendar.YEAR),
                    utcCal.get(Calendar.MONTH),
                    utcCal.get(Calendar.DAY_OF_MONTH),
                    0,
                    0,
                    0
                )
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        private fun localMillisToUtcAllDay(localMillis: Long): Long {
            val localCal = Calendar.getInstance().apply {
                timeInMillis = localMillis
            }
            return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(
                    localCal.get(Calendar.YEAR),
                    localCal.get(Calendar.MONTH),
                    localCal.get(Calendar.DAY_OF_MONTH),
                    0,
                    0,
                    0
                )
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        private val dayHeaderFormat = ThreadLocal.withInitial {
            SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        }
        private val dayTagFormat = ThreadLocal.withInitial {
            SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        }
        private val timeFormat = ThreadLocal.withInitial {
            SimpleDateFormat("h:mm a", Locale.getDefault())
        }
        private val fullDateFormat = ThreadLocal.withInitial {
            SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        }
        private val syncTimeSameDayFormat = ThreadLocal.withInitial {
            SimpleDateFormat("h:mm a", Locale.getDefault())
        }
        private val syncTimeOtherDayFormat = ThreadLocal.withInitial {
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        }

        fun formatLastSyncIndicator(
            context: Context,
            lastSyncMillis: Long,
            nowMillis: Long = System.currentTimeMillis(),
            compact: Boolean = false
        ): String {
            if (lastSyncMillis <= 0L) {
                return context.getString(R.string.widget_sync_never)
            }
            val elapsedMillis = (nowMillis - lastSyncMillis).coerceAtLeast(0L)
            if (elapsedMillis < 60_000L && !compact) {
                return context.getString(R.string.widget_sync_just_now)
            }
            val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val syncCal = Calendar.getInstance().apply { timeInMillis = lastSyncMillis }
            val isSameDay = nowCal.get(Calendar.YEAR) == syncCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) == syncCal.get(Calendar.DAY_OF_YEAR)

            val formattedTime = if (isSameDay) {
                syncTimeSameDayFormat.get()?.format(Date(lastSyncMillis)) ?: ""
            } else {
                syncTimeOtherDayFormat.get()?.format(Date(lastSyncMillis)) ?: ""
            }
            return if (compact) {
                formattedTime
            } else {
                context.getString(R.string.widget_sync_time_prefix, formattedTime)
            }
        }

        fun computeDayHeader(millis: Long): String {
            val nowCal = Calendar.getInstance()
            val targetCal = Calendar.getInstance().apply { timeInMillis = millis }

            val nowYear = nowCal.get(Calendar.YEAR)
            val targetYear = targetCal.get(Calendar.YEAR)
            val nowDay = nowCal.get(Calendar.DAY_OF_YEAR)
            val targetDay = targetCal.get(Calendar.DAY_OF_YEAR)

            return if (nowYear == targetYear && nowDay == targetDay) {
                "TODAY"
            } else if (nowYear == targetYear && targetDay == nowDay + 1) {
                "TOMORROW"
            } else {
                val formatted = dayHeaderFormat.get()?.format(Date(millis)) ?: ""
                formatted.uppercase(Locale.getDefault())
            }
        }

        private fun formatDayTag(millis: Long): String {
            return dayTagFormat.get()?.format(Date(millis)) ?: ""
        }

        fun formatEventTime(startMillis: Long, endMillis: Long, isAllDay: Boolean): String {
            if (isAllDay) return "All day"
            val formatter = timeFormat.get()
            val startStr = formatter?.format(Date(startMillis)) ?: ""
            val endStr = formatter?.format(Date(endMillis)) ?: ""
            return "$startStr – $endStr"
        }

        fun formatFullDateTime(startMillis: Long, endMillis: Long, isAllDay: Boolean): String {
            val dateStr = fullDateFormat.get()?.format(Date(startMillis)) ?: ""
            val timeStr = formatEventTime(startMillis, endMillis, isAllDay)
            return "$dateStr • $timeStr"
        }
    }
}
