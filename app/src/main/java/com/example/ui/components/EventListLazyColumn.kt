package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.EventEntity
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
    val timeframe = when (uiState.activeCategory) {
        "Today" -> "today"
        "Tomorrow" -> "tomorrow"
        "Work" -> "work categories"
        else -> if (uiState.widgetPreferences.daysHorizon == 1) "today" else "the next ${uiState.widgetPreferences.daysHorizon} days"
    }

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
        onEventClick = onEventClick,
        onDeleteEvent = onDeleteEvent,
        onAddEvent = onAddEvent,
        onSyncNow = onSyncNow,
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
    contentPadding: PaddingValues = PaddingValues(bottom = 80.dp),
    onEventClick: (EventEntity) -> Unit = {},
    onDeleteEvent: (String) -> Unit = {},
    onAddEvent: () -> Unit = {},
    onSyncNow: () -> Unit = {}
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
                        onAddEvent = onAddEvent,
                        onSyncNow = onSyncNow,
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
    onAddEvent: () -> Unit = {},
    onSyncNow: () -> Unit = {}
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
            // Visual badge icon
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(68.dp)
                    .testTag("empty_state_icon_badge")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.SearchOff else Icons.Default.EventAvailable,
                        contentDescription = "No events",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // Headline
            val titleText = when {
                isSearchActive -> "No matching events"
                timeframe.equals("today", ignoreCase = true) -> "All clear for today!"
                timeframe.equals("tomorrow", ignoreCase = true) -> "Nothing scheduled tomorrow"
                timeframe.contains("work", ignoreCase = true) -> "No work events scheduled"
                else -> "No upcoming events scheduled"
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
                isSearchActive -> "We couldn't find any events matching \"$searchQuery\". Try checking for typos or searching a different keyword."
                timeframe.equals("today", ignoreCase = true) -> "You have no upcoming events for today. Enjoy your free time or schedule something new!"
                timeframe.equals("tomorrow", ignoreCase = true) -> "Your calendar for tomorrow is completely open. Plan ahead by adding an event below."
                timeframe.contains("work", ignoreCase = true) -> "There are no work-related calendar events found in this timeframe."
                else -> "Your calendar is clear for $timeframe. Add an event or sync with your Google accounts."
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
                        !hasCalendarPermission -> "Calendar permission required to sync Google accounts"
                        availableCalendarsCount > 0 -> "Synced with ${syncedAccountName ?: "$availableCalendarsCount Google Calendars"}"
                        else -> "Local calendar active (Add Google account in Android Settings)"
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
                        text = "Add Event",
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
                        text = "Sync Now",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
