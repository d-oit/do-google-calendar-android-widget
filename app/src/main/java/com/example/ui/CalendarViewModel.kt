package com.example.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AppError
import com.example.data.model.CalendarSyncDiagnostics
import com.example.data.model.EventEntity
import com.example.data.model.OperationResult
import com.example.data.pref.WidgetPreferences
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.repository.CalendarAccountInfo
import com.example.data.repository.CalendarRepository
import com.example.data.update.AppUpdateChecker
import com.example.data.update.UpdateCheckResult
import com.example.util.AppLogger
import com.example.util.CalendarDateFormatter
import com.example.widget.CalendarAgendaWidgetProvider
import com.example.widget.CalendarGlanceWidgetReceiver
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ErrorAction {
    NONE,
    RETRY_SYNC,
    OPEN_SETTINGS
}

enum class WidgetPinType {
    GLANCE,
    CLASSIC
}

/**
 * Locale-independent agenda filter category.
 * Preserves canonical keys (`All`, `Today`, `Tomorrow`, `All Day`) for persistence & tests.
 */
sealed interface AgendaFilterCategory {
    val canonicalKey: String

    data object All : AgendaFilterCategory {
        override val canonicalKey: String = "All"
    }

    data object Today : AgendaFilterCategory {
        override val canonicalKey: String = "Today"
    }

    data object Tomorrow : AgendaFilterCategory {
        override val canonicalKey: String = "Tomorrow"
    }

    data object AllDay : AgendaFilterCategory {
        override val canonicalKey: String = "All Day"
    }

    data class CalendarSource(val displayName: String) : AgendaFilterCategory {
        override val canonicalKey: String = displayName
    }

    companion object {
        fun fromKey(key: String): AgendaFilterCategory = when (key) {
            "All" -> All
            "Today" -> Today
            "Tomorrow" -> Tomorrow
            "All Day" -> AllDay
            else -> CalendarSource(key)
        }
    }
}

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class UpToDate(val currentVersion: String) : UpdateCheckState
    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val releaseUrl: String,
        val releaseName: String = "",
        val releaseNotes: String = ""
    ) : UpdateCheckState
    data object NotConfigured : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}

sealed interface UiError {
    val message: String
    val action: ErrorAction

    data class PermissionRequired(
        override val message: String,
        override val action: ErrorAction = ErrorAction.OPEN_SETTINGS
    ) : UiError

    data class SyncFailed(
        override val message: String,
        override val action: ErrorAction = ErrorAction.RETRY_SYNC
    ) : UiError

    data class EventOperationFailed(
        override val message: String,
        override val action: ErrorAction = ErrorAction.NONE
    ) : UiError
}

sealed interface UiEvent {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val action: ErrorAction = ErrorAction.NONE,
        val isError: Boolean = false
    ) : UiEvent
}

data class CalendarUiState(
    val events: List<EventEntity> = emptyList(),
    val filteredEvents: List<EventEntity> = emptyList(),
    val widgetPreferences: WidgetPreferences = WidgetPreferences(),
    val isSyncing: Boolean = false,
    val isFetching: Boolean = false,
    val lastSyncTime: Long = 0L,
    val isConnected: Boolean = false,
    val hasCalendarPermission: Boolean = false,
    val syncedAccountName: String? = null,
    val availableCalendars: List<CalendarAccountInfo> = emptyList(),
    val searchQuery: String = "",
    val activeCategory: String = AgendaFilterCategory.All.canonicalKey,
    val activeFilterCategory: AgendaFilterCategory = AgendaFilterCategory.All,
    val isAddEventDialogOpen: Boolean = false,
    val editingEvent: EventEntity? = null,
    val selectedEventForDetails: EventEntity? = null,
    val error: UiError? = null,
    val isDiagnosticsSheetOpen: Boolean = false,
    val isRunningDiagnostics: Boolean = false,
    val isRepairingSync: Boolean = false,
    val syncDiagnostics: CalendarSyncDiagnostics? = null,
    val isAboutSheetOpen: Boolean = false,
    val isPinChooserOpen: Boolean = false,
    val updateCheckState: UpdateCheckState = UpdateCheckState.Idle
)

private data class DiagnosticsPanelState(
    val isOpen: Boolean = false,
    val isRunning: Boolean = false,
    val isRepairing: Boolean = false,
    val snapshot: CalendarSyncDiagnostics? = null
)

private data class ModalUiState(
    val isAddOpen: Boolean = false,
    val editingEvent: EventEntity? = null,
    val selectedEvent: EventEntity? = null,
    val isAboutOpen: Boolean = false,
    val isPinChooserOpen: Boolean = false
)

private data class FilteredAgendaPayload(
    val prefFilteredEvents: List<EventEntity>,
    val finalFilteredEvents: List<EventEntity>,
    val searchQuery: String,
    val filterCategory: AgendaFilterCategory
)

private data class RepoStatus(
    val isSyncing: Boolean,
    val lastSyncTime: Long,
    val isConnected: Boolean,
    val hasCalendarPermission: Boolean
)

private data class SyncInfo(
    val isSyncing: Boolean,
    val lastSyncTime: Long,
    val isConnected: Boolean,
    val hasCalendarPermission: Boolean,
    val syncedAccountName: String?,
    val availableCalendars: List<CalendarAccountInfo>,
    val widgetPreferences: WidgetPreferences
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: CalendarRepository,
    private val preferencesManager: WidgetPreferencesManager,
    private val updateChecker: AppUpdateChecker = AppUpdateChecker()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _activeFilterCategory = MutableStateFlow<AgendaFilterCategory>(AgendaFilterCategory.All)
    private val _currentDayStartMillis = MutableStateFlow(CalendarDateFormatter.getStartOfTodayMillis())

    private val _modalState = MutableStateFlow(ModalUiState())
    private val _uiError = MutableStateFlow<UiError?>(null)
    private val _diagnosticsState = MutableStateFlow(DiagnosticsPanelState())
    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)

    private var providerObserverJob: Job? = null
    private var debouncedObserverSyncJob: Job? = null
    private var lastResumeSyncMillis: Long = 0L

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        AppLogger.e(AppLogger.TAG_VM, "Unhandled coroutine exception in CalendarViewModel", throwable)
        val message = throwable.message?.takeIf { it.isNotBlank() } ?: "An unexpected calendar error occurred."
        val error = UiError.SyncFailed(message)
        _uiError.value = error
        _uiEvents.tryEmit(
            UiEvent.ShowSnackbar(
                message = message,
                actionLabel = "Retry",
                action = ErrorAction.RETRY_SYNC,
                isError = true
            )
        )
    }

    private val _permissionOverride = MutableStateFlow<Boolean?>(null)

    private val _repoStatusFlow = combine(
        repository.isSyncing,
        repository.lastSyncTime,
        repository.isConnected,
        repository.hasCalendarPermission,
        _permissionOverride
    ) { isSyncing, lastSync, isConnected, hasPerm, override ->
        RepoStatus(isSyncing, lastSync, isConnected, override ?: hasPerm)
    }

    private val _syncInfoFlow = combine(
        _repoStatusFlow,
        repository.syncedAccountName,
        repository.availableCalendars,
        preferencesManager.preferencesFlow
    ) { status, acctName, calendars, prefs ->
        SyncInfo(
            isSyncing = status.isSyncing,
            lastSyncTime = status.lastSyncTime,
            isConnected = status.isConnected,
            hasCalendarPermission = status.hasCalendarPermission,
            syncedAccountName = acctName,
            availableCalendars = calendars,
            widgetPreferences = prefs
        )
    }

    /**
     * Reactive database stream driven by [_currentDayStartMillis] so crossing midnight
     * automatically shifts the Room query window and refreshes TODAY/TOMORROW boundaries.
     */
    private val _upcomingEventsFlow = _currentDayStartMillis
        .flatMapLatest { dayStart ->
            repository.getUpcomingEventsFlow(dayStart)
                .onStart { emit(emptyList()) }
                .catch { throwable ->
                    AppLogger.e(AppLogger.TAG_VM, "Database event stream error", throwable)
                    _uiError.value = UiError.SyncFailed("Failed reading cached calendar events.")
                    emit(emptyList())
                }
        }

    /**
     * Dedicated filtering pipeline offloaded to [Dispatchers.Default] and decoupled from
     * dialog/bottom-sheet toggles so opening modals never re-filters the event list.
     */
    private val _filteredAgendaFlow = combine(
        _upcomingEventsFlow,
        preferencesManager.preferencesFlow,
        _searchQuery,
        _activeFilterCategory,
        _currentDayStartMillis
    ) { events, prefs, search, filterCategory, dayStart ->
        val nowMillis = maxOf(System.currentTimeMillis(), dayStart)
        val prefFiltered = CalendarRepository.filterEventsForPreferences(
            events = events,
            prefs = prefs,
            nowMillis = nowMillis
        )
        val trimmedSearch = search.trim()
        val categoryAndSearchFiltered = prefFiltered.filter { event ->
            val matchesSearch = trimmedSearch.isEmpty() ||
                event.title.contains(trimmedSearch, ignoreCase = true) ||
                (event.location?.contains(trimmedSearch, ignoreCase = true) == true) ||
                (event.description?.contains(trimmedSearch, ignoreCase = true) == true)

            val matchesCategory = when (filterCategory) {
                AgendaFilterCategory.All -> true
                AgendaFilterCategory.Today -> CalendarDateFormatter.isToday(event.startMillis, nowMillis)
                AgendaFilterCategory.Tomorrow -> CalendarDateFormatter.isTomorrow(event.startMillis, nowMillis)
                AgendaFilterCategory.AllDay -> event.isAllDay
                is AgendaFilterCategory.CalendarSource ->
                    event.calendarId.equals(filterCategory.displayName, ignoreCase = true)
            }
            matchesSearch && matchesCategory
        }

        FilteredAgendaPayload(
            prefFilteredEvents = prefFiltered,
            finalFilteredEvents = CalendarRepository.prepareDayHeaders(categoryAndSearchFiltered, nowMillis),
            searchQuery = search,
            filterCategory = filterCategory
        )
    }.flowOn(Dispatchers.Default)

    private val _auxPanelsFlow = combine(
        _modalState,
        _uiError,
        _diagnosticsState,
        _updateCheckState
    ) { modal, error, diag, updateState ->
        AuxPanels(modal, error, diag, updateState)
    }

    private data class AuxPanels(
        val modal: ModalUiState,
        val error: UiError?,
        val diagnostics: DiagnosticsPanelState,
        val updateState: UpdateCheckState
    )

    val uiState: StateFlow<CalendarUiState> = combine(
        _filteredAgendaFlow,
        _syncInfoFlow,
        _auxPanelsFlow
    ) { agenda, syncInfo, aux ->
        CalendarUiState(
            events = agenda.prefFilteredEvents,
            filteredEvents = agenda.finalFilteredEvents,
            widgetPreferences = syncInfo.widgetPreferences,
            isSyncing = syncInfo.isSyncing,
            isFetching = syncInfo.isSyncing,
            lastSyncTime = syncInfo.lastSyncTime,
            isConnected = syncInfo.isConnected,
            hasCalendarPermission = syncInfo.hasCalendarPermission,
            syncedAccountName = syncInfo.syncedAccountName,
            availableCalendars = syncInfo.availableCalendars,
            searchQuery = agenda.searchQuery,
            activeCategory = agenda.filterCategory.canonicalKey,
            activeFilterCategory = agenda.filterCategory,
            isAddEventDialogOpen = aux.modal.isAddOpen,
            editingEvent = aux.modal.editingEvent,
            selectedEventForDetails = aux.modal.selectedEvent,
            error = aux.error,
            isDiagnosticsSheetOpen = aux.diagnostics.isOpen,
            isRunningDiagnostics = aux.diagnostics.isRunning,
            isRepairingSync = aux.diagnostics.isRepairing,
            syncDiagnostics = aux.diagnostics.snapshot,
            isAboutSheetOpen = aux.modal.isAboutOpen,
            isPinChooserOpen = aux.modal.isPinChooserOpen,
            updateCheckState = aux.updateState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CalendarUiState(
            hasCalendarPermission = repository.checkCalendarPermission(),
            widgetPreferences = preferencesManager.getPreferences(),
            isSyncing = repository.isSyncing.value,
            isFetching = repository.isFetching.value,
            lastSyncTime = preferencesManager.getLastSuccessfulSyncMillis()
        )
    )

    val isFetching: StateFlow<Boolean> = repository.isFetching

    init {
        if (repository.checkCalendarPermission()) {
            performSync(forceRefresh = true, notifyOnFailure = false)
            startObservingProviderChanges()
        }
    }

    /**
     * Checks whether the local calendar date rolled over (e.g., past midnight or timezone changed)
     * and updates [_currentDayStartMillis] so reactive queries & headers stay accurate.
     */
    fun refreshDayBoundaryIfNeeded(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val latestDayStart = CalendarDateFormatter.getStartOfTodayMillis(nowMillis)
        if (latestDayStart != _currentDayStartMillis.value) {
            AppLogger.d(AppLogger.TAG_VM, "Day boundary changed -> refreshing upcoming schedule window")
            _currentDayStartMillis.value = latestDayStart
            return true
        }
        return false
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String) {
        _activeFilterCategory.value = AgendaFilterCategory.fromKey(category)
    }

    fun onFilterCategorySelected(category: AgendaFilterCategory) {
        _activeFilterCategory.value = category
    }

    fun onSyncNow() {
        refreshDayBoundaryIfNeeded()
        performSync(forceRefresh = true, notifyOnFailure = true)
    }

    fun dismissError() {
        _uiError.value = null
    }

    private fun performSync(forceRefresh: Boolean, notifyOnFailure: Boolean) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiError.value = null
            when (val result = repository.syncEvents(forceRefresh = forceRefresh)) {
                is OperationResult.Success -> {
                    _uiError.value = null
                    if (result.data == 0 || _diagnosticsState.value.isOpen) {
                        val snapshot = repository.collectSyncDiagnostics()
                        _diagnosticsState.value = _diagnosticsState.value.copy(snapshot = snapshot)
                    }
                }
                is OperationResult.Failure -> {
                    val uiErr = mapAppError(result.error, isSyncOperation = true)
                    _uiError.value = uiErr
                    if (notifyOnFailure) {
                        emitErrorSnackbar(uiErr)
                    }
                }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _permissionOverride.value = granted
        repository.updatePermissionState(explicitGrant = granted)
        if (granted) {
            _uiError.value = null
            performSync(forceRefresh = true, notifyOnFailure = true)
            startObservingProviderChanges()
        } else {
            providerObserverJob?.cancel()
            providerObserverJob = null
            AppLogger.w(AppLogger.TAG_VM, "User denied calendar permission request")
            val err = UiError.PermissionRequired("Calendar permission is required to sync your schedule.")
            _uiError.value = err
            emitErrorSnackbar(err)
        }
    }

    fun startObservingProviderChanges() {
        if (providerObserverJob?.isActive == true) return
        if (!repository.checkCalendarPermission()) return
        providerObserverJob = viewModelScope.launch(coroutineExceptionHandler) {
            repository.observeCalendarChanges().collect {
                debouncedObserverSyncJob?.cancel()
                debouncedObserverSyncJob = viewModelScope.launch(coroutineExceptionHandler) {
                    delay(500L)
                    AppLogger.d(AppLogger.TAG_VM, "CalendarContract change observed; refreshing cache & widgets")
                    performSync(forceRefresh = false, notifyOnFailure = false)
                }
            }
        }
    }

    fun onLifecycleResume() {
        val rolledOver = refreshDayBoundaryIfNeeded()
        val now = System.currentTimeMillis()
        if (!rolledOver && now - lastResumeSyncMillis < 2_000L) return
        lastResumeSyncMillis = now

        repository.updatePermissionState()
        if (repository.checkCalendarPermission()) {
            startObservingProviderChanges()
            performSync(forceRefresh = rolledOver, notifyOnFailure = false)
        } else if (_diagnosticsState.value.isOpen) {
            runDiagnostics()
        }
    }

    fun openDiagnosticsSheet() {
        _modalState.value = _modalState.value.copy(isAboutOpen = false)
        _diagnosticsState.value = _diagnosticsState.value.copy(isOpen = true)
        runDiagnostics()
    }

    fun closeDiagnosticsSheet() {
        _diagnosticsState.value = _diagnosticsState.value.copy(isOpen = false)
    }

    fun openAboutSheet() {
        _modalState.value = _modalState.value.copy(isAboutOpen = true)
    }

    fun closeAboutSheet() {
        _modalState.value = _modalState.value.copy(isAboutOpen = false)
    }

    fun openPinWidgetChooser() {
        _modalState.value = _modalState.value.copy(isPinChooserOpen = true)
    }

    fun closePinWidgetChooser() {
        _modalState.value = _modalState.value.copy(isPinChooserOpen = false)
    }

    fun checkForUpdates(
        currentVersion: String = BuildConfig.VERSION_NAME,
        repositorySlug: String = BuildConfig.GITHUB_REPOSITORY
    ) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _updateCheckState.value = UpdateCheckState.Checking
            val checker = if (currentVersion == BuildConfig.VERSION_NAME && repositorySlug == BuildConfig.GITHUB_REPOSITORY) {
                updateChecker
            } else {
                AppUpdateChecker(currentVersionName = currentVersion, repositorySlug = repositorySlug)
            }
            when (val result = checker.checkForUpdates()) {
                is UpdateCheckResult.UpToDate -> {
                    _updateCheckState.value = UpdateCheckState.UpToDate(result.currentVersion)
                }
                is UpdateCheckResult.UpdateAvailable -> {
                    _updateCheckState.value = UpdateCheckState.UpdateAvailable(
                        currentVersion = result.currentVersion,
                        latestVersion = result.latestVersion,
                        releaseUrl = result.releaseUrl,
                        releaseName = "v${result.latestVersion}",
                        releaseNotes = result.releaseNotes
                    )
                }
                is UpdateCheckResult.NotConfigured -> {
                    _updateCheckState.value = UpdateCheckState.NotConfigured
                }
                is UpdateCheckResult.Error -> {
                    _updateCheckState.value = UpdateCheckState.Error(result.message)
                }
            }
        }
    }

    fun runDiagnostics() {
        viewModelScope.launch(coroutineExceptionHandler) {
            _diagnosticsState.value = _diagnosticsState.value.copy(isRunning = true)
            try {
                val snapshot = repository.collectSyncDiagnostics()
                _diagnosticsState.value = _diagnosticsState.value.copy(
                    isRunning = false,
                    snapshot = snapshot
                )
            } finally {
                if (_diagnosticsState.value.isRunning) {
                    _diagnosticsState.value = _diagnosticsState.value.copy(isRunning = false)
                }
            }
        }
    }

    fun repairAndForceAccountSync(context: Context) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _diagnosticsState.value = _diagnosticsState.value.copy(isRepairing = true)
            try {
                when (val result = repository.repairCalendarSyncAndRequestOsSync()) {
                    is OperationResult.Success -> {
                        val report = result.data
                        val snapshot = repository.collectSyncDiagnostics()
                        _diagnosticsState.value = _diagnosticsState.value.copy(
                            isRepairing = false,
                            snapshot = snapshot
                        )
                        val msg = when {
                            report.calendarsFlagRepairedCount > 0 -> context.getString(
                                R.string.diagnostics_repaired_and_synced_msg,
                                report.calendarsFlagRepairedCount
                            )
                            report.accountsSyncRequestedCount > 0 -> context.getString(
                                R.string.diagnostics_sync_requested_msg,
                                report.accountsSyncRequestedCount
                            )
                            else -> context.getString(R.string.diagnostics_no_syncable_accounts_msg)
                        }
                        _uiEvents.tryEmit(UiEvent.ShowSnackbar(message = msg))
                    }
                    is OperationResult.Failure -> {
                        _diagnosticsState.value = _diagnosticsState.value.copy(isRepairing = false)
                        val err = mapAppError(result.error, isSyncOperation = true)
                        _uiError.value = err
                        emitErrorSnackbar(err)
                    }
                }
            } finally {
                if (_diagnosticsState.value.isRepairing) {
                    _diagnosticsState.value = _diagnosticsState.value.copy(isRepairing = false)
                }
            }
        }
    }

    fun applyFilterQuickFix() {
        val current = preferencesManager.getPreferences()
        val updated = current.copy(
            daysHorizon = maxOf(current.daysHorizon, 30),
            isAllCalendarsSelected = true,
            showPastEvents = true
        )
        updateWidgetPreferences(updated)
        _activeFilterCategory.value = AgendaFilterCategory.All
        _searchQuery.value = ""
        runDiagnostics()
    }

    fun notifyMessage(message: String, isError: Boolean = false) {
        _uiEvents.tryEmit(UiEvent.ShowSnackbar(message = message, isError = isError))
    }

    fun updateWidgetPreferences(preferences: WidgetPreferences) {
        val previous = preferencesManager.getPreferences()
        preferencesManager.updatePreferences(preferences)
        val horizonIncreased = preferences.daysHorizon > previous.daysHorizon && preferences.daysHorizon > 30
        if (horizonIncreased && repository.checkCalendarPermission()) {
            performSync(forceRefresh = false, notifyOnFailure = true)
        }
        if (_diagnosticsState.value.isOpen) {
            runDiagnostics()
        }
    }

    fun openAddEventDialog() {
        _modalState.value = _modalState.value.copy(
            editingEvent = null,
            isAddOpen = true
        )
    }

    fun openEditEventDialog(event: EventEntity) {
        _modalState.value = _modalState.value.copy(
            selectedEvent = null,
            editingEvent = event,
            isAddOpen = true
        )
    }

    fun closeAddEventDialog() {
        _modalState.value = _modalState.value.copy(
            isAddOpen = false,
            editingEvent = null
        )
    }

    fun selectEvent(event: EventEntity?) {
        _modalState.value = _modalState.value.copy(selectedEvent = event)
    }

    fun openEventById(eventId: String) {
        val inMemory = uiState.value.events.find { it.id == eventId }
        if (inMemory != null) {
            _modalState.value = _modalState.value.copy(selectedEvent = inMemory)
            return
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            val fromDb = repository.getEventById(eventId)
            if (fromDb != null) {
                _modalState.value = _modalState.value.copy(selectedEvent = fromDb)
            } else {
                AppLogger.w(AppLogger.TAG_VM, "Requested event id=$eventId not found")
            }
        }
    }

    fun addEvent(
        title: String,
        location: String?,
        startMillis: Long,
        endMillis: Long,
        colorHex: String,
        description: String? = null,
        isAllDay: Boolean = false,
        targetCalendarId: Long? = null
    ) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiError.value = null
            when (
                val result = repository.addEvent(
                    title = title,
                    location = location,
                    startMillis = startMillis,
                    endMillis = endMillis,
                    colorHex = colorHex,
                    description = description,
                    isAllDay = isAllDay,
                    targetCalendarId = targetCalendarId
                )
            ) {
                is OperationResult.Success -> {
                    _modalState.value = _modalState.value.copy(isAddOpen = false, editingEvent = null)
                    if (result.data.isOfflineCreated && !repository.checkWriteCalendarPermission()) {
                        _uiEvents.tryEmit(
                            UiEvent.ShowSnackbar(
                                message = "Saved locally. Grant calendar write permission to sync to Google Calendar.",
                                actionLabel = "Settings",
                                action = ErrorAction.OPEN_SETTINGS,
                                isError = false
                            )
                        )
                    }
                }
                is OperationResult.Failure -> {
                    val err = mapAppError(result.error, isSyncOperation = false)
                    _uiError.value = err
                    emitErrorSnackbar(err)
                }
            }
        }
    }

    fun updateEvent(
        eventId: String,
        title: String,
        location: String?,
        description: String?,
        startMillis: Long,
        endMillis: Long,
        isAllDay: Boolean,
        colorHex: String,
        targetCalendarId: Long? = null
    ) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiError.value = null
            when (
                val result = repository.updateEvent(
                    eventId = eventId,
                    title = title,
                    location = location,
                    description = description,
                    startMillis = startMillis,
                    endMillis = endMillis,
                    isAllDay = isAllDay,
                    colorHex = colorHex,
                    targetCalendarId = targetCalendarId
                )
            ) {
                is OperationResult.Success -> {
                    _modalState.value = _modalState.value.copy(isAddOpen = false, editingEvent = null)
                }
                is OperationResult.Failure -> {
                    val err = mapAppError(result.error, isSyncOperation = false)
                    _uiError.value = err
                    emitErrorSnackbar(err)
                }
            }
        }
    }

    fun deleteEvent(id: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiError.value = null
            when (val result = repository.deleteEvent(id)) {
                is OperationResult.Success -> {
                    if (_modalState.value.selectedEvent?.id == id) {
                        _modalState.value = _modalState.value.copy(selectedEvent = null)
                    }
                }
                is OperationResult.Failure -> {
                    val err = mapAppError(result.error, isSyncOperation = false)
                    _uiError.value = err
                    emitErrorSnackbar(err)
                }
            }
        }
    }

    fun pinWidgetToHomeScreen(
        context: Context,
        widgetType: WidgetPinType = WidgetPinType.CLASSIC
    ) {
        closePinWidgetChooser()
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val providerClass = when (widgetType) {
                WidgetPinType.GLANCE -> CalendarGlanceWidgetReceiver::class.java
                WidgetPinType.CLASSIC -> CalendarAgendaWidgetProvider::class.java
            }
            val provider = ComponentName(context, providerClass)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    val callbackIntent = Intent(context, CalendarAgendaWidgetProvider::class.java).apply {
                        action = CalendarAgendaWidgetProvider.ACTION_REFRESH_WIDGET
                    }
                    val successCallback = PendingIntent.getBroadcast(
                        context,
                        if (widgetType == WidgetPinType.GLANCE) 100 else 99,
                        callbackIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    appWidgetManager.requestPinAppWidget(provider, null, successCallback)
                    _uiEvents.tryEmit(
                        UiEvent.ShowSnackbar(
                            message = context.getString(R.string.msg_widget_pin_requested)
                        )
                    )
                } else {
                    _uiEvents.tryEmit(
                        UiEvent.ShowSnackbar(
                            message = context.getString(R.string.msg_widget_pin_unsupported)
                        )
                    )
                }
            } else {
                _uiEvents.tryEmit(
                    UiEvent.ShowSnackbar(
                        message = context.getString(R.string.msg_widget_pin_legacy)
                    )
                )
            }
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_VM, "Failed requesting widget pin on launcher", e)
            _uiEvents.tryEmit(
                UiEvent.ShowSnackbar(
                    message = context.getString(R.string.msg_widget_pin_unsupported),
                    isError = true
                )
            )
        }
    }

    private fun mapAppError(error: AppError, isSyncOperation: Boolean): UiError {
        return when (error) {
            is AppError.PermissionDenied -> UiError.PermissionRequired(error.message)
            is AppError.CalendarQueryFailed -> UiError.SyncFailed(error.message)
            is AppError.EventWriteFailed,
            is AppError.DatabaseFailed,
            is AppError.Unexpected -> {
                if (isSyncOperation) {
                    UiError.SyncFailed(error.message)
                } else {
                    UiError.EventOperationFailed(error.message)
                }
            }
        }
    }

    private fun emitErrorSnackbar(error: UiError) {
        val actionLabel = when (error.action) {
            ErrorAction.RETRY_SYNC -> "Retry"
            ErrorAction.OPEN_SETTINGS -> "Settings"
            ErrorAction.NONE -> null
        }
        _uiEvents.tryEmit(
            UiEvent.ShowSnackbar(
                message = error.message,
                actionLabel = actionLabel,
                action = error.action,
                isError = true
            )
        )
    }
}
