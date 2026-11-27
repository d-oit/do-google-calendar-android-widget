package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.EventEntity
import com.example.ui.AgendaFilterCategory
import com.example.ui.CalendarUiState

@Composable
fun AgendaScheduleContent(
    uiState: CalendarUiState,
    onSearchQueryChanged: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onSelectEvent: (EventEntity) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onOpenAddEvent: () -> Unit,
    onSyncNow: () -> Unit = {},
    onOpenDiagnostics: (() -> Unit)? = null,
    onExpandFiltersQuickFix: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dynamicCalendarSources = remember(uiState.availableCalendars) {
        uiState.availableCalendars
            .map { it.displayName }
            .distinct()
            .filter {
                it.isNotBlank() &&
                    it !in listOf("All", "Today", "Tomorrow", "All Day")
            }
            .take(3)
            .map { AgendaFilterCategory.CalendarSource(it) }
    }

    val categories: List<AgendaFilterCategory> = remember(dynamicCalendarSources) {
        listOf(
            AgendaFilterCategory.All,
            AgendaFilterCategory.Today,
            AgendaFilterCategory.Tomorrow,
            AgendaFilterCategory.AllDay
        ) + dynamicCalendarSources
    }

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text(stringResource(R.string.search_events_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.search_clear_desc)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("agenda_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                val label = when (category) {
                    AgendaFilterCategory.All -> stringResource(R.string.filter_all)
                    AgendaFilterCategory.Today -> stringResource(R.string.filter_today)
                    AgendaFilterCategory.Tomorrow -> stringResource(R.string.filter_tomorrow)
                    AgendaFilterCategory.AllDay -> stringResource(R.string.filter_all_day)
                    is AgendaFilterCategory.CalendarSource -> category.displayName
                }
                FilterChip(
                    selected = uiState.activeFilterCategory == category,
                    onClick = { onCategorySelected(category.canonicalKey) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("category_chip_${category.canonicalKey}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val timeframe = when (val active = uiState.activeFilterCategory) {
            AgendaFilterCategory.Today -> stringResource(R.string.filter_today)
            AgendaFilterCategory.Tomorrow -> stringResource(R.string.filter_tomorrow)
            AgendaFilterCategory.AllDay -> stringResource(R.string.filter_all_day)
            AgendaFilterCategory.All -> if (uiState.widgetPreferences.daysHorizon == 1) {
                stringResource(R.string.filter_today)
            } else {
                stringResource(R.string.widget_customizer_horizon_days, uiState.widgetPreferences.daysHorizon)
            }
            is AgendaFilterCategory.CalendarSource -> active.displayName
        }

        val hiddenOutsideFilter = uiState.syncDiagnostics?.let { diag ->
            if (diag.eventsMatchingCurrentFilters == 0) diag.eventsIn60DayWindow else 0
        } ?: 0

        EventListLazyColumn(
            events = uiState.filteredEvents,
            timeframe = timeframe,
            isRefreshing = uiState.isSyncing,
            onRefresh = onSyncNow,
            isSearchActive = uiState.searchQuery.isNotBlank(),
            searchQuery = uiState.searchQuery,
            hasCalendarPermission = uiState.hasCalendarPermission,
            syncedAccountName = uiState.syncedAccountName,
            availableCalendarsCount = uiState.availableCalendars.size,
            hiddenEventsOutsideFilterCount = hiddenOutsideFilter,
            activeDaysHorizon = uiState.widgetPreferences.daysHorizon,
            widgetPreferences = uiState.widgetPreferences,
            onEventClick = onSelectEvent,
            onDeleteEvent = onDeleteEvent,
            onAddEvent = onOpenAddEvent,
            onSyncNow = onSyncNow,
            onOpenDiagnostics = onOpenDiagnostics,
            onExpandFiltersQuickFix = onExpandFiltersQuickFix,
            modifier = Modifier.weight(1f)
        )
    }
}
