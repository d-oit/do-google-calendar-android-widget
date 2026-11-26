package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.EventEntity
import com.example.ui.AgendaFilterCategory
import com.example.ui.CalendarViewModel

/**
 * LazyColumn UI component that displays the list of events provided by CalendarViewModel.
 * Displays each event's title, scheduled time range, and color indicators.
 *
 * Wrapped in a pull-to-refresh container allowing pull-down gestures to force-sync
 * calendar data from the device ContentProvider.
 *
 * When there are no events scheduled for the selected timeframe, renders a friendly,
 * informative empty state component directly inside the LazyColumn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListLazyColumn(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 80.dp),
    onEventClick: (EventEntity) -> Unit = { viewModel.selectEvent(it) },
    onDeleteEvent: (String) -> Unit = { viewModel.deleteEvent(it) },
    onAddEvent: () -> Unit = { viewModel.openAddEventDialog() },
    onSyncNow: () -> Unit = { viewModel.onSyncNow() }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
        onEventClick = onEventClick,
        onDeleteEvent = onDeleteEvent,
        onAddEvent = onAddEvent,
        onSyncNow = onSyncNow,
        onOpenDiagnostics = { viewModel.openDiagnosticsSheet() },
        onExpandFiltersQuickFix = { viewModel.applyFilterQuickFix() },
        contentPadding = contentPadding,
        modifier = modifier
    )
}

/**
 * Decoupled LazyColumn UI component that renders events with title, time, and color indicators,
 * or displays a friendly empty state component inside the LazyColumn when empty.
 *
 * Includes a PullToRefreshBox with custom Material 3 refresh indicator gesture to force-sync
 * calendar data from Android's native CalendarContract ContentProvider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListLazyColumn(
    events: List<EventEntity>,
    timeframe: String,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    hasCalendarPermission: Boolean = true,
    syncedAccountName: String? = null,
    availableCalendarsCount: Int = 0,
    hiddenEventsOutsideFilterCount: Int = 0,
    activeDaysHorizon: Int = 7,
    contentPadding: PaddingValues = PaddingValues(bottom = 80.dp),
    onEventClick: (EventEntity) -> Unit = {},
    onDeleteEvent: (String) -> Unit = {},
    onAddEvent: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onOpenDiagnostics: (() -> Unit)? = null,
    onExpandFiltersQuickFix: (() -> Unit)? = null
) {
    val performRefresh = onRefresh ?: onSyncNow
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = performRefresh,
        state = pullToRefreshState,
        modifier = modifier
            .fillMaxSize()
            .testTag("pull_to_refresh_box"),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                color = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .testTag("pull_to_refresh_indicator")
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("event_lazy_column"),
            contentPadding = contentPadding,
            verticalArrangement = if (events.isEmpty()) Arrangement.Center else Arrangement.spacedBy(4.dp)
        ) {
            if (events.isEmpty()) {
                item(key = "friendly_empty_state") {
                    FriendlyEmptyEventsState(
                        timeframe = timeframe,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        hasCalendarPermission = hasCalendarPermission,
                        syncedAccountName = syncedAccountName,
                        availableCalendarsCount = availableCalendarsCount,
                        hiddenEventsOutsideFilterCount = hiddenEventsOutsideFilterCount,
                        activeDaysHorizon = activeDaysHorizon,
                        onAddEvent = onAddEvent,
                        onSyncNow = onSyncNow,
                        onOpenDiagnostics = onOpenDiagnostics,
                        onExpandFiltersQuickFix = onExpandFiltersQuickFix,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 8.dp)
                    )
                }
            } else {
                items(events, key = { it.id }) { event ->
                    EventItemCard(
                        event = event,
                        onClick = { onEventClick(event) },
                        onDelete = { onDeleteEvent(event.id) }
                    )
                }
            }
        }
    }
}

/**
 * Friendly empty state component displaying an inviting layout when no upcoming events
 * are found for the selected timeframe.
 */
@Composable
fun FriendlyEmptyEventsState(
    timeframe: String,
    modifier: Modifier = Modifier,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    hasCalendarPermission: Boolean = true,
    syncedAccountName: String? = null,
    availableCalendarsCount: Int = 0,
    hiddenEventsOutsideFilterCount: Int = 0,
    activeDaysHorizon: Int = 7,
    onAddEvent: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onOpenDiagnostics: (() -> Unit)? = null,
    onExpandFiltersQuickFix: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("friendly_empty_state_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Visual Material 3 illustration for empty schedule, or search-off icon when filtering
            if (isSearchActive) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(68.dp)
                        .testTag("empty_state_icon_badge")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = "No events",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 1.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .widthIn(max = 200.dp)
                        .fillMaxWidth(0.68f)
                        .aspectRatio(4f / 3f)
                        .testTag("empty_state_illustration")
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_empty_calendar),
                        contentDescription = stringResource(R.string.empty_calendar_illustration_desc),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                    )
                }
            }

            // Headline (locale-safe match against localized filter labels + English fallback)
            val todayLabel = stringResource(R.string.filter_today)
            val tomorrowLabel = stringResource(R.string.filter_tomorrow)
            val allDayLabel = stringResource(R.string.filter_all_day)
            val isTodayFrame = timeframe.equals(todayLabel, ignoreCase = true) ||
                timeframe.equals("today", ignoreCase = true)
            val isTomorrowFrame = timeframe.equals(tomorrowLabel, ignoreCase = true) ||
                timeframe.equals("tomorrow", ignoreCase = true)
            val isAllDayFrame = timeframe.equals(allDayLabel, ignoreCase = true) ||
                timeframe.equals("all-day events", ignoreCase = true) ||
                timeframe.equals("all day", ignoreCase = true)

            val titleText = when {
                isSearchActive -> stringResource(R.string.agenda_empty_search_title)
                isTodayFrame -> stringResource(R.string.agenda_empty_today_title)
                isTomorrowFrame -> stringResource(R.string.agenda_empty_tomorrow_title)
                isAllDayFrame -> stringResource(R.string.agenda_empty_all_day_title)
                else -> stringResource(R.string.agenda_empty_title)
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("empty_state_title")
            )

            // Friendly description
            val subtitleText = when {
                isSearchActive -> stringResource(R.string.agenda_empty_search_query_subtitle, searchQuery)
                isTodayFrame -> stringResource(R.string.agenda_empty_today_subtitle)
                isTomorrowFrame -> stringResource(R.string.agenda_empty_tomorrow_subtitle)
                isAllDayFrame -> stringResource(R.string.agenda_empty_all_day_subtitle)
                else -> stringResource(R.string.agenda_empty_horizon_subtitle, timeframe)
            }

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .testTag("empty_state_message")
            )

            // Sync / Account Status Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val statusColor = if (hasCalendarPermission && availableCalendarsCount > 0) {
                        Color(0xFF34A853)
                    } else {
                        Color(0xFFFBBC04)
                    }

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, CircleShape)
                    )

                    val statusLabel = when {
                        !hasCalendarPermission -> stringResource(R.string.status_permission_needed_sync)
                        availableCalendarsCount > 0 -> {
                            val accountDisplay = syncedAccountName?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.status_synced_calendars_count, availableCalendarsCount)
                            stringResource(R.string.status_synced_with_account, accountDisplay)
                        }
                        else -> stringResource(R.string.status_local_calendar_active)
                    }

                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Add event button (Primary CTA)
                Button(
                    onClick = onAddEvent,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("empty_add_event_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_add_event),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Sync now button
                OutlinedButton(
                    onClick = onSyncNow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("empty_sync_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.agenda_action_sync_now),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Inline quick-fix banner when diagnostics detects events outside active filters
            if (!isSearchActive && hiddenEventsOutsideFilterCount > 0 && onExpandFiltersQuickFix != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_hidden_events_banner")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(
                                    R.string.diagnostics_hidden_events_banner,
                                    hiddenEventsOutsideFilterCount,
                                    activeDaysHorizon
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                        Button(
                            onClick = onExpandFiltersQuickFix,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("empty_expand_filters_btn")
                        ) {
                            Text(
                                text = stringResource(R.string.diagnostics_action_expand_filters),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Troubleshoot Sync shortcut
            if (!isSearchActive && onOpenDiagnostics != null) {
                TextButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("empty_troubleshoot_sync_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_troubleshoot_sync),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
