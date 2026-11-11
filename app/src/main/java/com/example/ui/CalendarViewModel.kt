package com.example.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.EventEntity
import com.example.data.pref.WidgetPreferences
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.repository.CalendarAccountInfo
import com.example.data.repository.CalendarRepository
import com.example.widget.CalendarAgendaWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CalendarUiState(
    val events: List<EventEntity> = emptyList(),
    val filteredEvents: List<EventEntity> = emptyList(),
    val widgetPreferences: WidgetPreferences = WidgetPreferences(),
    val isSyncing: Boolean = false,
    val isFetching: Boolean = false,
    val lastSyncTime: Long = System.currentTimeMillis(),
    val isConnected: Boolean = false,
    val hasCalendarPermission: Boolean = false,
    val syncedAccountName: String? = null,
    val availableCalendars: List<CalendarAccountInfo> = emptyList(),
    val searchQuery: String = "",
    val activeCategory: String = "All",
    val isAddEventDialogOpen: Boolean = false,
    val selectedEventForDetails: EventEntity? = null
)

private data class ViewFilters(
    val search: String,
    val category: String,
    val isAddOpen: Boolean,
    val selectedEvent: EventEntity?
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

class CalendarViewModel(
    private val repository: CalendarRepository,
    private val preferencesManager: WidgetPreferencesManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _activeCategory = MutableStateFlow("All")
    private val _isAddDialogOpen = MutableStateFlow(false)
    private val _selectedEvent = MutableStateFlow<EventEntity?>(null)

    private val _filtersFlow = combine(
        _searchQuery,
        _activeCategory,
        _isAddDialogOpen,
        _selectedEvent
    ) { search, category, isAddOpen, selectedEvent ->
        ViewFilters(search, category, isAddOpen, selectedEvent)
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

    val uiState: StateFlow<CalendarUiState> = combine(
        repository.getUpcomingEventsFlow().onStart { emit(emptyList()) }.catch { emit(emptyList()) },
        _syncInfoFlow,
        _filtersFlow
    ) { events: List<EventEntity>, syncInfo: SyncInfo, filters: ViewFilters ->
        val filtered = events.filter { event ->
            val matchesSearch = filters.search.isBlank() ||
                event.title.contains(filters.search, ignoreCase = true) ||
                (event.location?.contains(filters.search, ignoreCase = true) == true)

            val matchesCategory = when (filters.category) {
                "Today" -> event.dayHeader == "TODAY"
                "Tomorrow" -> event.dayHeader == "TOMORROW"
                "Work" -> event.colorHex == "#1A73E8" || event.colorHex == "#34A853"
                else -> true
            }

            matchesSearch && matchesCategory
        }

        CalendarUiState(
            events = events,
            filteredEvents = filtered,
            widgetPreferences = syncInfo.widgetPreferences,
            isSyncing = syncInfo.isSyncing,
            isFetching = syncInfo.isSyncing,
            lastSyncTime = syncInfo.lastSyncTime,
            isConnected = syncInfo.isConnected,
            hasCalendarPermission = syncInfo.hasCalendarPermission,
            syncedAccountName = syncInfo.syncedAccountName,
            availableCalendars = syncInfo.availableCalendars,
            searchQuery = filters.search,
            activeCategory = filters.category,
            isAddEventDialogOpen = filters.isAddOpen,
            selectedEventForDetails = filters.selectedEvent
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CalendarUiState(
            hasCalendarPermission = repository.checkCalendarPermission(),
            widgetPreferences = preferencesManager.getPreferences(),
            isSyncing = repository.isSyncing.value,
            isFetching = repository.isFetching.value
        )
    )

    val isFetching: StateFlow<Boolean> = repository.isFetching

    init {
        if (repository.checkCalendarPermission()) {
            viewModelScope.launch {
                repository.syncEvents(forceRefresh = true)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String) {
        _activeCategory.value = category
    }

    fun onSyncNow() {
        viewModelScope.launch {
            repository.syncEvents(forceRefresh = true)
        }
    }

    fun syncWithGoogleApi(authHeader: String) {
        viewModelScope.launch {
            repository.syncGoogleCalendarApi(authHeader)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _permissionOverride.value = granted
        repository.updatePermissionState(explicitGrant = granted)
        if (granted) {
            viewModelScope.launch {
                repository.syncEvents(forceRefresh = true)
            }
        }
    }

    fun updateWidgetPreferences(preferences: WidgetPreferences) {
        preferencesManager.updatePreferences(preferences)
    }

    fun openAddEventDialog() {
        _isAddDialogOpen.value = true
    }

    fun closeAddEventDialog() {
        _isAddDialogOpen.value = false
    }

    fun selectEvent(event: EventEntity?) {
        _selectedEvent.value = event
    }

    fun addEvent(
        title: String,
        location: String?,
        startMillis: Long,
        endMillis: Long,
        colorHex: String
    ) {
        viewModelScope.launch {
            repository.addEvent(title, location, startMillis, endMillis, colorHex)
            _isAddDialogOpen.value = false
        }
    }

    fun deleteEvent(id: String) {
        viewModelScope.launch {
            repository.deleteEvent(id)
            if (_selectedEvent.value?.id == id) {
                _selectedEvent.value = null
            }
        }
    }

    fun pinWidgetToHomeScreen(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, CalendarAgendaWidgetProvider::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val callbackIntent = Intent(context, CalendarAgendaWidgetProvider::class.java).apply {
                    action = CalendarAgendaWidgetProvider.ACTION_REFRESH_WIDGET
                }
                val successCallback = PendingIntent.getBroadcast(
                    context,
                    99,
                    callbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                appWidgetManager.requestPinAppWidget(provider, null, successCallback)
                Toast.makeText(context, "Adding Google Calendar widget to Home Screen...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Launcher does not support direct widget pinning. Please add from launcher widget menu.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Long-press your home screen, choose Widgets, and select Calendar Agenda.", Toast.LENGTH_LONG).show()
        }
    }
}
