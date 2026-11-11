package com.example.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.data.api.GoogleCalendarApiService
import com.example.data.dao.EventDao
import com.example.data.model.EventEntity
import com.example.data.pref.WidgetPreferencesManager
import com.example.util.CalendarColorMapper
import com.example.util.CalendarEventColorProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val colorHex: String = "#1A73E8"
)

class CalendarRepository(
    private val context: Context,
    private val eventDao: EventDao,
    private val preferencesManager: WidgetPreferencesManager = WidgetPreferencesManager.getInstance(context)
) {
    private val apiService by lazy { GoogleCalendarApiService.create() }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()
    val isFetching = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTime = _lastSyncTime.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _hasCalendarPermission = MutableStateFlow(checkCalendarPermission())
    val hasCalendarPermission = _hasCalendarPermission.asStateFlow()

    private val _syncedAccountName = MutableStateFlow<String?>(null)
    val syncedAccountName = _syncedAccountName.asStateFlow()

    private val _availableCalendars = MutableStateFlow<List<CalendarAccountInfo>>(emptyList())
    val availableCalendars = _availableCalendars.asStateFlow()

    init {
        updatePermissionState()
    }

    private var _hasExplicitPermissionOverride: Boolean? = null

    fun checkCalendarPermission(): Boolean {
        if (_hasExplicitPermissionOverride != null) return _hasExplicitPermissionOverride == true
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
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
        } catch (_: Throwable) {
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

    private fun getStartOfTodayMillis(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getUpcomingEventsFlow(): Flow<List<EventEntity>> {
        val threshold = getStartOfTodayMillis()
        return eventDao.getUpcomingEventsFlow(threshold)
    }

    /**
     * Synchronous query for AppWidget and initial loads.
     * Returns genuine events from the local database or device calendar.
     */
    fun getUpcomingEventsSync(): List<EventEntity> {
        val threshold = getStartOfTodayMillis()
        val list = eventDao.getUpcomingEventsSync(threshold)
        if (list.isNotEmpty()) return list

        if (checkCalendarPermission()) {
            val deviceEvents = fetchDeviceCalendarEvents()
            if (deviceEvents.isNotEmpty()) {
                val formatted = prepareDayHeaders(deviceEvents)
                try {
                    eventDao.insertEventsSync(formatted)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return formatted
            }
        }
        return emptyList()
    }

    /**
     * Synchronize events directly from native Android device Google Calendar accounts.
     * ZERO API KEYS NEEDED — uses native CalendarContract with Android login credentials.
     * ZERO MOCKUP DATA — purges any legacy test relics.
     */
    suspend fun syncEvents(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        updatePermissionState()

        purgeLegacySeedEvents()

        val realEvents = mutableListOf<EventEntity>()

        try {
            if (checkCalendarPermission()) {
                val cals = queryAvailableCalendars()
                val deviceEvents = fetchDeviceCalendarEvents()
                realEvents.addAll(deviceEvents)
                _isConnected.value = cals.isNotEmpty() || realEvents.isNotEmpty()
            }

            if (realEvents.isNotEmpty()) {
                val formatted = prepareDayHeaders(realEvents)
                eventDao.clearSyncedEvents()
                eventDao.insertEvents(formatted)
                _isConnected.value = true
            } else {
                if (checkCalendarPermission()) {
                    eventDao.clearSyncedEvents()
                }
            }
            _lastSyncTime.value = System.currentTimeMillis()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSyncing.value = false
            preferencesManager.notifyWidgetsChanged()
        }
    }

    /**
     * Sync calendars and events directly with Google Calendar API v3 using OAuth token.
     */
    suspend fun syncGoogleCalendarApi(authHeader: String): Result<Int> = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        try {
            val formattedAuth = if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
                authHeader
            } else {
                "Bearer $authHeader"
            }

            val calListResp = apiService.getCalendarList(formattedAuth)
            val calItems = calListResp.items ?: emptyList()

            val calendarAccountInfos = calItems.mapIndexed { index, item ->
                val hex = item.backgroundColor ?: getCalendarColor(item.summary ?: "Calendar", 0)
                CalendarAccountInfo(
                    id = index.toLong() + 1000L,
                    accountName = item.summary ?: "Google Calendar",
                    displayName = item.summary ?: "Google Calendar",
                    accountType = "com.google",
                    isPrimary = item.primary == true,
                    color = 0,
                    colorHex = hex
                )
            }
            if (calendarAccountInfos.isNotEmpty()) {
                _availableCalendars.value = calendarAccountInfos
                val primaryCal = calendarAccountInfos.firstOrNull { it.isPrimary } ?: calendarAccountInfos.first()
                _syncedAccountName.value = primaryCal.accountName
            }

            val fetchedEvents = mutableListOf<EventEntity>()
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(getStartOfTodayMillis()))

            val calendarsToSync = if (calItems.isNotEmpty()) calItems else listOf(
                com.example.data.api.GoogleCalendarListItem(id = "primary", summary = "Google Calendar")
            )

            for (cal in calendarsToSync) {
                try {
                    val eventsResp = apiService.getUpcomingEventsWithAuth(
                        authHeader = formattedAuth,
                        calendarId = cal.id,
                        timeMin = nowIso,
                        singleEvents = true,
                        orderBy = "startTime",
                        maxResults = 50
                    )
                    val items = eventsResp.items ?: emptyList()
                    val calColorHex = cal.backgroundColor ?: getCalendarColor(cal.summary ?: "Google Calendar", 0)

                    for (item in items) {
                        val startMillis = parseGoogleDateTime(item.start?.dateTime, item.start?.date)
                        val endMillis = parseGoogleDateTime(item.end?.dateTime, item.end?.date).let {
                            if (it <= startMillis) startMillis + 3600000L else it
                        }
                        val isAllDay = item.start?.date != null

                        val eventColorHex = item.colorId?.let {
                            GoogleCalendarApiService.GOOGLE_EVENT_COLOR_MAP[it]
                        } ?: calColorHex

                        fetchedEvents.add(
                            EventEntity(
                                id = "gapi_${item.id}",
                                calendarId = cal.summary ?: "Google Calendar",
                                title = item.summary ?: "(No title)",
                                description = item.description,
                                location = item.location,
                                startMillis = startMillis,
                                endMillis = endMillis,
                                isAllDay = isAllDay,
                                colorHex = eventColorHex,
                                htmlLink = item.htmlLink,
                                dayHeader = computeDayHeader(startMillis),
                                isFirstOfDay = false
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (fetchedEvents.isNotEmpty()) {
                val formatted = prepareDayHeaders(fetchedEvents)
                eventDao.clearSyncedEvents()
                eventDao.insertEvents(formatted)
                _isConnected.value = true
                _lastSyncTime.value = System.currentTimeMillis()
                preferencesManager.notifyWidgetsChanged()
            }

            Result.success(fetchedEvents.size)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    private fun parseGoogleDateTime(dateTimeStr: String?, dateStr: String?): Long {
        if (!dateTimeStr.isNullOrBlank()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    return java.time.OffsetDateTime.parse(dateTimeStr).toInstant().toEpochMilli()
                } catch (_: Throwable) {}
            }
            try {
                val clean = if (dateTimeStr.length >= 19) dateTimeStr.substring(0, 19) else dateTimeStr
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                return sdf.parse(clean)?.time ?: System.currentTimeMillis()
            } catch (_: Throwable) {}
        }
        if (!dateStr.isNullOrBlank()) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                return sdf.parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (_: Throwable) {}
        }
        return System.currentTimeMillis()
    }

    private suspend fun purgeLegacySeedEvents() {
        try {
            for (i in 1..25) {
                eventDao.deleteEventById("seed_$i")
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
                CalendarContract.Calendars.CALENDAR_COLOR_KEY
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

                while (cursor.moveToNext()) {
                    val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                    val accountName = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else ""
                    val accountType = if (typeIdx != -1) cursor.getString(typeIdx) ?: "" else ""
                    val displayName = if (displayIdx != -1) cursor.getString(displayIdx) ?: accountName else accountName
                    val isPrimary = if (primaryIdx != -1) cursor.getInt(primaryIdx) == 1 else false
                    val owner = if (ownerIdx != -1) cursor.getString(ownerIdx) ?: "" else ""
                    val color = if (colorIdx != -1) cursor.getInt(colorIdx) else 0
                    val colorKey = if (colorKeyIdx != -1) cursor.getString(colorKeyIdx) else null
                    val hex = CalendarColorMapper.resolveCalendarColor(displayName, color, colorKey, context.contentResolver)

                    list.add(
                        CalendarAccountInfo(
                            id = id,
                            accountName = accountName.ifBlank { owner },
                            displayName = displayName,
                            accountType = accountType,
                            isPrimary = isPrimary,
                            color = color,
                            colorHex = hex
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * Query real upcoming events from Android's CalendarContract.
     * Uses CalendarContract.Instances as the primary source for expanding recurring and non-recurring events,
     * and falls back to CalendarContract.Events directly so events are never missed if visibility flags or recurrence expansion differ.
     */
    fun fetchDeviceCalendarEvents(
        customStartMillis: Long? = null,
        customEndMillis: Long? = null
    ): List<EventEntity> {
        val events = mutableListOf<EventEntity>()
        if (!checkCalendarPermission()) return events

        val prefs = preferencesManager.getPreferences()
        val now = System.currentTimeMillis()
        val startMillis = customStartMillis ?: getStartOfTodayMillis()
        val horizonDays = prefs.daysHorizon.coerceIn(1, 60)
        val endMillis = customEndMillis ?: (now + (horizonDays.toLong() * 24 * 60 * 60 * 1000))

        val calendarFilter = if (!prefs.isAllCalendarsSelected && prefs.selectedCalendarIds.isNotEmpty()) {
            prefs.selectedCalendarIds
        } else {
            null
        }

        // 1. Try CalendarContract.Instances first (handles both recurring expansion & non-recurring single events)
        val instanceEvents = queryInstances(startMillis, endMillis, calendarFilter)
        events.addAll(instanceEvents)

        // 2. If Instances returned empty, query Events table directly as a fallback
        if (events.isEmpty()) {
            val directEvents = fetchDirectEventsInternal(startMillis, endMillis)
            events.addAll(directEvents)
        }
        return events
    }

    /**
     * Queries the Android native CalendarContract.Instances table for fetching recurring and non-recurring
     * events based on a specified time range ([startMillis] to [endMillis]).
     *
     * In Android's CalendarProvider:
     * - The Instances table dynamically computes and expands all recurring event rules (RRULE, RDATE, EXDATE)
     *   into individual chronological occurrences within the queried range.
     * - Non-recurring (single) events that fall within [startMillis, endMillis] are also included.
     * - Each row contains the specific instance's [CalendarContract.Instances.BEGIN] and [CalendarContract.Instances.END],
     *   along with [CalendarContract.Instances.EVENT_ID] referencing the parent event.
     *
     * @param startMillis The start timestamp in epoch milliseconds.
     * @param endMillis The end timestamp in epoch milliseconds.
     * @param calendarIds Optional set of calendar IDs to filter. If null, queries all permitted calendars.
     * @return Chronologically ordered list of [EventEntity] instances.
     */
    fun queryInstances(
        startMillis: Long,
        endMillis: Long,
        calendarIds: Set<Long>? = null
    ): List<EventEntity> {
        val events = mutableListOf<EventEntity>()
        if (!checkCalendarPermission()) return events

        try {
            val prefs = preferencesManager.getPreferences()

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startMillis)
            ContentUris.appendId(builder, endMillis)
            val uri = builder.build()

            val projection = arrayOf(
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

            var selection: String? = null
            var selectionArgs: Array<String>? = null

            val targetCalendarIds = calendarIds ?: if (!prefs.isAllCalendarsSelected && prefs.selectedCalendarIds.isNotEmpty()) {
                prefs.selectedCalendarIds
            } else {
                null
            }

            if (!targetCalendarIds.isNullOrEmpty()) {
                val placeholders = targetCalendarIds.joinToString(",") { "?" }
                selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
                selectionArgs = targetCalendarIds.map { it.toString() }.toTypedArray()
            }

            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIndex = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val descIndex = cursor.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val locIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val beginIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIndex = cursor.getColumnIndex(CalendarContract.Instances.END)
                val allDayIndex = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val displayColorIndex = cursor.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)
                val eventColorIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_COLOR)
                val calColorIndex = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_COLOR)
                val eventColorKeyIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_COLOR_KEY)
                val calNameIndex = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                val rruleIndex = cursor.getColumnIndex(CalendarContract.Instances.RRULE)

                while (cursor.moveToNext()) {
                    val eventId = if (idIndex != -1) cursor.getString(idIndex) ?: "" else ""
                    val title = if (titleIndex != -1) cursor.getString(titleIndex) ?: "(No title)" else "(No title)"
                    val desc = if (descIndex != -1) cursor.getString(descIndex) else null
                    val loc = if (locIndex != -1) cursor.getString(locIndex) else null
                    val begin = if (beginIndex != -1) cursor.getLong(beginIndex) else 0L
                    val end = if (endIndex != -1) cursor.getLong(endIndex) else begin + 3600000L
                    val allDay = if (allDayIndex != -1) cursor.getInt(allDayIndex) == 1 else false
                    val displayColor = if (displayColorIndex != -1 && !cursor.isNull(displayColorIndex)) cursor.getInt(displayColorIndex) else null
                    val eventColor = if (eventColorIndex != -1 && !cursor.isNull(eventColorIndex)) cursor.getInt(eventColorIndex) else null
                    val calColor = if (calColorIndex != -1 && !cursor.isNull(calColorIndex)) cursor.getInt(calColorIndex) else null
                    val eventColorKey = if (eventColorKeyIndex != -1 && !cursor.isNull(eventColorKeyIndex)) cursor.getString(eventColorKeyIndex) else null
                    val calName = if (calNameIndex != -1) cursor.getString(calNameIndex) ?: "Google Calendar" else "Google Calendar"
                    val rrule = if (rruleIndex != -1) cursor.getString(rruleIndex) else null

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

                    // Distinguish instance unique ID combining parent eventId and instance begin timestamp.
                    // This guarantees each occurrence of a recurring series has a distinct, stable unique primary key.
                    val instanceUniqueId = "dev_${eventId}_$begin"

                    events.add(
                        EventEntity(
                            id = instanceUniqueId,
                            calendarId = calName,
                            title = title,
                            description = desc,
                            location = if (prefs.showLocations) loc else null,
                            startMillis = begin,
                            endMillis = if (end <= begin) begin + 3600000L else end,
                            isAllDay = allDay,
                            colorHex = colorHex,
                            htmlLink = "content://com.android.calendar/events/$eventId",
                            dayHeader = computeDayHeader(begin),
                            isFirstOfDay = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        customEndMillis: Long? = null
    ): List<EventEntity> {
        val events = mutableListOf<EventEntity>()
        try {
            val prefs = preferencesManager.getPreferences()
            val startMillis = customStartMillis ?: getStartOfTodayMillis()
            val horizonDays = prefs.daysHorizon.coerceIn(1, 60)
            val endMillis = customEndMillis ?: (System.currentTimeMillis() + (horizonDays.toLong() * 24 * 60 * 60 * 1000))

            val projection = arrayOf(
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
                CalendarContract.Events.CALENDAR_ID
            )

            val selection = "${CalendarContract.Events.DELETED} = 0 AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())

            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val descIdx = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
                val displayColorIdx = cursor.getColumnIndex(CalendarContract.Events.DISPLAY_COLOR)
                val eventColorIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_COLOR)
                val calColorIdx = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_COLOR)
                val eventColorKeyIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_COLOR_KEY)
                val calNameIdx = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val title = if (titleIdx != -1) cursor.getString(titleIdx) ?: "(No title)" else "(No title)"
                    val desc = if (descIdx != -1) cursor.getString(descIdx) else null
                    val loc = if (locIdx != -1) cursor.getString(locIdx) else null
                    val start = if (startIdx != -1) cursor.getLong(startIdx) else 0L
                    val end = if (endIdx != -1) cursor.getLong(endIdx) else start + 3600000L
                    val allDay = if (allDayIdx != -1) cursor.getInt(allDayIdx) == 1 else false
                    val displayColor = if (displayColorIdx != -1 && !cursor.isNull(displayColorIdx)) cursor.getInt(displayColorIdx) else null
                    val eventColor = if (eventColorIdx != -1 && !cursor.isNull(eventColorIdx)) cursor.getInt(eventColorIdx) else null
                    val calColor = if (calColorIdx != -1 && !cursor.isNull(calColorIdx)) cursor.getInt(calColorIdx) else null
                    val eventColorKey = if (eventColorKeyIdx != -1 && !cursor.isNull(eventColorKeyIdx)) cursor.getString(eventColorKeyIdx) else null
                    val calName = if (calNameIdx != -1) cursor.getString(calNameIdx) ?: "Google Calendar" else "Google Calendar"

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
                            title = title,
                            description = desc,
                            location = if (prefs.showLocations) loc else null,
                            startMillis = start,
                            endMillis = if (end <= start) start + 3600000L else end,
                            isAllDay = allDay,
                            colorHex = colorHex,
                            htmlLink = "content://com.android.calendar/events/$id",
                            dayHeader = computeDayHeader(start),
                            isFirstOfDay = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            e.printStackTrace()
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
            e.printStackTrace()
            null
        }
    }

    private fun writeEventToDeviceCalendar(
        title: String,
        location: String?,
        startMillis: Long,
        endMillis: Long
    ): Long? {
        if (!checkWriteCalendarPermission()) return null
        val calendarId = findPrimaryCalendarId() ?: ensureLocalCalendarExists() ?: 1L
        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, "Created via Google Calendar Agenda Widget")
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.let { ContentUris.parseId(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun prepareDayHeaders(events: List<EventEntity>): List<EventEntity> {
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

    /**
     * Add a genuine event to the device Google Calendar account and local cache.
     */
    suspend fun addEvent(
        title: String,
        location: String?,
        startMillis: Long,
        endMillis: Long,
        colorHex: String = "#1A73E8"
    ) = withContext(Dispatchers.IO) {
        val deviceEventId = writeEventToDeviceCalendar(title, location, startMillis, endMillis)
        val id = if (deviceEventId != null) {
            "dev_${deviceEventId}_$startMillis"
        } else {
            "evt_${System.currentTimeMillis()}"
        }
        val dayHeader = computeDayHeader(startMillis)
        val newEvent = EventEntity(
            id = id,
            calendarId = _syncedAccountName.value ?: "Google Calendar",
            title = title,
            location = location,
            startMillis = startMillis,
            endMillis = endMillis,
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
    }

    /**
     * Delete an event from the device Google Calendar account and local cache.
     */
    suspend fun deleteEvent(id: String) = withContext(Dispatchers.IO) {
        if (id.startsWith("dev_") && checkWriteCalendarPermission()) {
            try {
                val parts = id.split("_")
                if (parts.size >= 2) {
                    val rawId = parts[1].toLongOrNull()
                    if (rawId != null) {
                        val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, rawId)
                        context.contentResolver.delete(deleteUri, null, null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        eventDao.deleteEventById(id)
        recomputeDayHeaders()
        preferencesManager.notifyWidgetsChanged()
    }

    private suspend fun recomputeDayHeaders() {
        val threshold = getStartOfTodayMillis()
        val list = eventDao.getUpcomingEventsSync(threshold)
        val updated = prepareDayHeaders(list)
        eventDao.clearAll()
        eventDao.insertEvents(updated)
    }

    companion object {
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
                val sdf = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                sdf.format(Date(millis)).uppercase(Locale.getDefault())
            }
        }

        private fun formatDayTag(millis: Long): String {
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            return sdf.format(Date(millis))
        }

        fun formatEventTime(startMillis: Long, endMillis: Long, isAllDay: Boolean): String {
            if (isAllDay) return "All day"
            val timeSdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            val startStr = timeSdf.format(Date(startMillis))
            val endStr = timeSdf.format(Date(endMillis))
            return "$startStr – $endStr"
        }
    }
}
