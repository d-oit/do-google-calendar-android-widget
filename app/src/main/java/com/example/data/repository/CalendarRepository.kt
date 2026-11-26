package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
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
import com.example.util.CalendarDateFormatter
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
import java.util.concurrent.atomic.AtomicBoolean

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
    private val providerDataSource = CalendarProviderDataSource(context) { errorMessage ->
        _lastQueryError = errorMessage
    }
    private val eventWriter = CalendarEventWriter(context)

    private val legacySeedPurged = AtomicBoolean(false)

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

    private var _hasExplicitPermissionOverride: Boolean? = null

    @Volatile
    private var _simulatedSyncErrorForTesting: AppError? = null

    init {
        updatePermissionState()
    }

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

    fun updatePermissionState(explicitGrant: Boolean? = null, refreshCalendars: Boolean = true) {
        if (explicitGrant != null) {
            _hasExplicitPermissionOverride = explicitGrant
        }
        val granted = explicitGrant ?: checkCalendarPermission()
        _hasCalendarPermission.value = granted
        if (granted) {
            if (refreshCalendars) {
                val cals = queryAvailableCalendars()
                _isConnected.value = cals.isNotEmpty()
            }
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

    fun getUpcomingEventsFlow(startOfTodayMillis: Long = getStartOfTodayMillis()): Flow<List<EventEntity>> {
        return eventDao.getUpcomingEventsFlow(startOfTodayMillis)
    }

    suspend fun getEventById(id: String): EventEntity? = withContext(Dispatchers.IO) {
        eventDao.getEventById(id)
    }

    /**
     * Synchronous query for AppWidget and initial loads.
     * Returns genuine events filtered strictly by [WidgetPreferences].
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
     * ZERO MOCKUP DATA — purges any legacy test relics once per process.
     */
    suspend fun syncEvents(forceRefresh: Boolean = false): OperationResult<Int> = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        updatePermissionState(refreshCalendars = false)
        purgeLegacySeedEventsOnce()

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
            preferencesManager.notifyWidgetsChanged(debounced = false)
        }
    }

    private suspend fun purgeLegacySeedEventsOnce() {
        if (!legacySeedPurged.compareAndSet(false, true)) return
        try {
            val removed = eventDao.deleteLegacySeedEvents()
            if (removed > 0) {
                AppLogger.i(AppLogger.TAG_REPO, "Purged $removed legacy seed event row(s)")
            }
        } catch (e: CancellationException) {
            legacySeedPurged.set(false)
            throw e
        } catch (e: Exception) {
            legacySeedPurged.set(false)
            AppLogger.w(AppLogger.TAG_REPO, "Failed purging legacy seed events", e)
        }
    }

    fun getCalendarColor(calName: String, rawColorInt: Int): String {
        return CalendarColorMapper.resolveCalendarColor(calName, rawColorInt, contentResolver = context.contentResolver)
    }

    fun queryAvailableCalendars(): List<CalendarAccountInfo> {
        if (!checkCalendarPermission()) return emptyList()
        val list = providerDataSource.queryAvailableCalendars()
        _availableCalendars.value = list
        val googleCal = list.firstOrNull { it.accountType == "com.google" }
            ?: list.firstOrNull { it.isPrimary }
            ?: list.firstOrNull()
        _syncedAccountName.value = googleCal?.let { it.accountName.ifBlank { it.displayName } }
        return list
    }

    private fun fetchAllDeviceCalendarEvents(horizonDays: Int): List<EventEntity> {
        if (!checkCalendarPermission()) return emptyList()
        val now = System.currentTimeMillis()
        val startMillis = getStartOfTodayMillis(now)
        val endMillis = getHorizonEndMillis(horizonDays.coerceIn(1, 60), now)
        val instanceEvents = providerDataSource.queryInstances(startMillis, endMillis, calendarIds = null)
        if (instanceEvents.isNotEmpty()) return instanceEvents
        return providerDataSource.fetchDirectEvents(startMillis, endMillis, calendarIds = null)
    }

    fun fetchDeviceCalendarEvents(
        customStartMillis: Long? = null,
        customEndMillis: Long? = null
    ): List<EventEntity> {
        if (!checkCalendarPermission()) return emptyList()
        val prefs = preferencesManager.getPreferences()
        if (!prefs.isAllCalendarsSelected && prefs.selectedCalendarIds.isEmpty()) {
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val startMillis = customStartMillis ?: getStartOfTodayMillis(now)
        val horizonDays = prefs.daysHorizon.coerceIn(1, 60)
        val endMillis = customEndMillis ?: getHorizonEndMillis(horizonDays, now)
        val calendarFilter = if (!prefs.isAllCalendarsSelected) prefs.selectedCalendarIds else null

        val instanceEvents = providerDataSource.queryInstances(startMillis, endMillis, calendarFilter)
        if (instanceEvents.isNotEmpty()) return instanceEvents
        return providerDataSource.fetchDirectEvents(startMillis, endMillis, calendarFilter)
    }

    fun queryInstances(
        startMillis: Long,
        endMillis: Long,
        calendarIds: Set<Long>? = null
    ): List<EventEntity> {
        if (!checkCalendarPermission()) return emptyList()
        return providerDataSource.queryInstances(startMillis, endMillis, calendarIds)
    }

    fun queryInstancesFlow(
        startMillis: Long,
        endMillis: Long,
        calendarIds: Set<Long>? = null
    ): Flow<List<EventEntity>> = flow {
        emit(queryInstances(startMillis, endMillis, calendarIds))
    }.flowOn(Dispatchers.IO)

    fun ensureLocalCalendarExists(): Long? {
        if (!checkWriteCalendarPermission()) return null
        return eventWriter.ensureLocalCalendarExists(_syncedAccountName.value)
    }

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
            val writeResult = if (checkWriteCalendarPermission()) {
                eventWriter.insertEvent(
                    title = title,
                    location = location,
                    description = description,
                    startMillis = startMillis,
                    endMillis = endMillis,
                    isAllDay = isAllDay,
                    colorHex = colorHex,
                    targetCalendarId = targetCalendarId,
                    preferredAccountName = _syncedAccountName.value
                )
            } else {
                null
            }
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
                dayHeader = computeDayHeader(startMillis),
                isFirstOfDay = false,
                isOfflineCreated = deviceEventId == null,
                htmlLink = if (deviceEventId != null) "content://com.android.calendar/events/$deviceEventId" else null
            )
            eventDao.insertEvent(newEvent)
            recomputeDayHeaders()
            _isConnected.value = true
            preferencesManager.notifyWidgetsChanged(debounced = false)
            AppLogger.d(AppLogger.TAG_REPO, "Added event id=$id (deviceSynced=${deviceEventId != null})")
            OperationResult.Success(newEvent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(AppLogger.TAG_REPO, "Failed adding calendar event", e)
            OperationResult.Failure(
                AppError.EventWriteFailed(
                    operation = "add",
                    message = context.getString(R.string.error_add_event_failed),
                    cause = e
                )
            )
        }
    }

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
            val resolvedCalId: Long = targetCalendarId
                ?: existing.sourceCalendarId.takeIf { it > 0L }
                ?: (if (checkCalendarPermission()) eventWriter.findPrimaryCalendarId() else null)
                ?: -1L

            if (parentId != null && checkWriteCalendarPermission()) {
                eventWriter.updateEvent(
                    parentEventId = parentId,
                    title = title.trim(),
                    location = location,
                    description = description,
                    startMillis = startMillis,
                    endMillis = endMillis,
                    isAllDay = isAllDay,
                    colorHex = colorHex,
                    calendarId = resolvedCalId
                )
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
            preferencesManager.notifyWidgetsChanged(debounced = false)
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

    suspend fun deleteEvent(id: String): OperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val existing = eventDao.getEventById(id)
            val rawId = existing?.parentEventId ?: parseParentEventId(id)
            if (rawId != null && checkWriteCalendarPermission()) {
                eventWriter.deleteEvent(rawId)
            }
            eventDao.deleteEventById(id)
            recomputeDayHeaders()
            preferencesManager.notifyWidgetsChanged(debounced = false)
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
        const val DAY_MILLIS = CalendarDateFormatter.DAY_MILLIS

        fun getStartOfTodayMillis(referenceMillis: Long = System.currentTimeMillis()): Long =
            CalendarDateFormatter.getStartOfTodayMillis(referenceMillis)

        fun getHorizonEndMillis(daysHorizon: Int, referenceMillis: Long = System.currentTimeMillis()): Long =
            CalendarDateFormatter.getHorizonEndMillis(daysHorizon, referenceMillis)

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
            return prepareDayHeaders(filtered, nowMillis)
        }

        fun prepareDayHeaders(
            events: List<EventEntity>,
            referenceMillis: Long = System.currentTimeMillis()
        ): List<EventEntity> {
            val sorted = events.sortedBy { it.startMillis }
            var lastDayTag = ""
            return sorted.map { event ->
                val dayTag = CalendarDateFormatter.formatDayTag(event.startMillis)
                val isFirst = (dayTag != lastDayTag)
                lastDayTag = dayTag
                event.copy(
                    dayHeader = CalendarDateFormatter.computeCanonicalDayHeader(event.startMillis, referenceMillis),
                    isFirstOfDay = isFirst
                )
            }
        }

        private fun parseParentEventId(id: String): Long? {
            if (!id.startsWith("dev_")) return null
            val parts = id.split("_")
            return if (parts.size >= 2) parts[1].toLongOrNull() else null
        }

        fun formatLastSyncIndicator(
            context: Context,
            lastSyncMillis: Long,
            nowMillis: Long = System.currentTimeMillis(),
            compact: Boolean = false
        ): String = CalendarDateFormatter.formatLastSyncIndicator(context, lastSyncMillis, nowMillis, compact)

        fun computeDayHeader(millis: Long): String =
            CalendarDateFormatter.computeCanonicalDayHeader(millis)

        fun formatEventTime(
            startMillis: Long,
            endMillis: Long,
            isAllDay: Boolean,
            context: Context? = null
        ): String = CalendarDateFormatter.formatEventTime(startMillis, endMillis, isAllDay, context)

        fun formatFullDateTime(
            startMillis: Long,
            endMillis: Long,
            isAllDay: Boolean,
            context: Context? = null
        ): String = CalendarDateFormatter.formatFullDateTime(startMillis, endMillis, isAllDay, context)
    }
}
