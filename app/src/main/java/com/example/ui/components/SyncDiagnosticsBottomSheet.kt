package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CalendarDiagnosticEntry
import com.example.data.model.CalendarSyncDiagnostics
import com.example.data.model.SyncRootCause
import com.example.util.CalendarColorMapper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDiagnosticsBottomSheet(
    diagnostics: CalendarSyncDiagnostics?,
    isRunningDiagnostics: Boolean,
    isRepairingSync: Boolean,
    onDismiss: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onRepairAndForceSync: () -> Unit,
    onExpandFiltersQuickFix: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    onOpenGoogleCalendar: () -> Unit,
    onCopyReport: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("sync_diagnostics_sheet")
    ) {
        SyncDiagnosticsSheetContent(
            diagnostics = diagnostics,
            isRunningDiagnostics = isRunningDiagnostics,
            isRepairingSync = isRepairingSync,
            onDismiss = onDismiss,
            onRefreshDiagnostics = onRefreshDiagnostics,
            onRepairAndForceSync = onRepairAndForceSync,
            onExpandFiltersQuickFix = onExpandFiltersQuickFix,
            onOpenSyncSettings = onOpenSyncSettings,
            onOpenGoogleCalendar = onOpenGoogleCalendar,
            onCopyReport = onCopyReport,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        )
    }
}

@Composable
fun SyncDiagnosticsSheetContent(
    diagnostics: CalendarSyncDiagnostics?,
    isRunningDiagnostics: Boolean,
    isRepairingSync: Boolean,
    onDismiss: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onRepairAndForceSync: () -> Unit,
    onExpandFiltersQuickFix: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    onOpenGoogleCalendar: () -> Unit,
    onCopyReport: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.diagnostics_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.diagnostics_sheet_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRefreshDiagnostics,
                enabled = !isRunningDiagnostics && !isRepairingSync,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("diagnostics_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.diagnostics_refresh_desc),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("diagnostics_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.diagnostics_close_desc),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = isRunningDiagnostics || isRepairingSync) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diagnostics_loading_bar"),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (diagnostics == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else {
            // 1. Root-Cause Diagnosis Card
            RootCauseDiagnosisCard(
                diagnostics = diagnostics,
                isRepairingSync = isRepairingSync,
                onRepairAndForceSync = onRepairAndForceSync,
                onExpandFiltersQuickFix = onExpandFiltersQuickFix,
                onOpenSyncSettings = onOpenSyncSettings,
                onOpenGoogleCalendar = onOpenGoogleCalendar
            )

            // 2. System & Query Window Card
            SystemHealthCard(diagnostics = diagnostics)

            // 3. Per-Calendar Provider Inspector
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diagnostics_calendars_section"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.diagnostics_section_calendars, diagnostics.calendars.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (diagnostics.calendars.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.diagnostics_no_calendars_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    diagnostics.calendars.forEach { entry ->
                        CalendarDiagnosticRowCard(entry = entry)
                    }
                }
            }

            // 4. One-Tap Repair & Emulator Tools
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.diagnostics_section_actions),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Button(
                    onClick = onRepairAndForceSync,
                    enabled = !isRepairingSync && diagnostics.hasReadPermission,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("diagnostics_repair_sync_button")
                ) {
                    if (isRepairingSync) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRepairingSync) {
                            stringResource(R.string.diagnostics_action_repairing)
                        } else {
                            stringResource(R.string.diagnostics_action_force_repair)
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenSyncSettings,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("diagnostics_open_sync_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.diagnostics_action_sync_settings),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenGoogleCalendar,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("diagnostics_open_google_calendar_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.diagnostics_action_open_gcal),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                TextButton(
                    onClick = { onCopyReport(diagnostics.buildPlainTextReport()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("diagnostics_copy_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.diagnostics_action_copy_report))
                }
            }
        }
    }
}

@Composable
private fun RootCauseDiagnosisCard(
    diagnostics: CalendarSyncDiagnostics,
    isRepairingSync: Boolean,
    onRepairAndForceSync: () -> Unit,
    onExpandFiltersQuickFix: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    onOpenGoogleCalendar: () -> Unit
) {
    val isHealthy = diagnostics.rootCause == SyncRootCause.HEALTHY
    val isFilterMismatch = diagnostics.rootCause == SyncRootCause.EVENTS_OUTSIDE_HORIZON_OR_FILTERS

    val containerColor = when {
        isHealthy -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        isFilterMismatch -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
    }
    val contentColor = when {
        isHealthy -> MaterialTheme.colorScheme.onSecondaryContainer
        isFilterMismatch -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    val icon = when {
        isHealthy -> Icons.Default.CheckCircle
        isFilterMismatch -> Icons.Default.FilterAlt
        else -> Icons.Default.SyncProblem
    }

    val (headline, explanation) = when (diagnostics.rootCause) {
        SyncRootCause.HEALTHY ->
            "Calendar Sync Healthy" to
                "Showing ${diagnostics.eventsMatchingCurrentFilters} upcoming event(s) from Android CalendarContract."

        SyncRootCause.PERMISSION_DENIED ->
            "Calendar Permission Missing" to
                "Grant READ_CALENDAR and WRITE_CALENDAR permissions so the app can read your device Google accounts."

        SyncRootCause.NO_CALENDARS_ON_DEVICE ->
            "No Calendars Registered on Device" to
                "Android CalendarContract returned 0 calendars. Sign into a Google account in Android Settings and open Google Calendar once."

        SyncRootCause.CALENDAR_SYNC_FLAG_OFF ->
            "Calendar Sync Flag Disabled (SYNC_EVENTS = 0)" to
                "Your Google account is registered on this device, but one or more calendars have sync or visibility turned off in CalendarProvider. Tap Force Google Sync & Repair below to enable them."

        SyncRootCause.MASTER_OR_ACCOUNT_SYNC_DISABLED ->
            "Android Account Auto-Sync Is Paused" to
                "Your Google account is present, but Android Auto-Sync for Calendar is disabled. Enable sync in Android Sync Settings or run Force Google Sync & Repair."

        SyncRootCause.PROVIDER_EMPTY_NEEDS_GOOGLE_SYNC ->
            "Google Calendar Has Not Synced to This Device Yet" to
                if (diagnostics.isGoogleCalendarAppInstalled) {
                    "Your Google account row exists in Android CalendarContract, but Android's local provider currently holds 0 raw events. Tap Force Google Sync & Repair, or open Google Calendar once to finish initial cloud sync on this emulator."
                } else {
                    "Your Google account is connected, but 0 events exist in Android CalendarContract and the Google Calendar app/sync adapter was not detected. Open Android Sync Settings or install/open Google Calendar on the emulator to sync cloud events."
                }

        SyncRootCause.EVENTS_OUTSIDE_HORIZON_OR_FILTERS ->
            "Events Found Outside Current Filters" to
                "Android CalendarContract has ${diagnostics.totalRawProviderEvents} raw event(s) (${diagnostics.eventsIn60DayWindow} in the next 60 days), but 0 match your active ${diagnostics.activeDaysHorizon}-day / calendar filter."

        SyncRootCause.PROVIDER_QUERY_ERROR ->
            "CalendarProvider Query Error" to
                (diagnostics.lastQueryError ?: "Android CalendarContract threw an unexpected error while querying events.")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostics_root_cause_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier.testTag("diagnostics_root_cause_title")
                )
            }

            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.92f)
            )

            if (isFilterMismatch) {
                Button(
                    onClick = onExpandFiltersQuickFix,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("diagnostics_expand_filters_button")
                ) {
                    Text(
                        text = stringResource(R.string.diagnostics_action_expand_filters),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else if (diagnostics.rootCause == SyncRootCause.CALENDAR_SYNC_FLAG_OFF) {
                Button(
                    onClick = onRepairAndForceSync,
                    enabled = !isRepairingSync,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("diagnostics_quick_repair_flag_button")
                ) {
                    Text(
                        text = stringResource(R.string.diagnostics_action_force_repair),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else if (diagnostics.rootCause == SyncRootCause.MASTER_OR_ACCOUNT_SYNC_DISABLED) {
                OutlinedButton(
                    onClick = onOpenSyncSettings,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.diagnostics_action_sync_settings))
                }
            } else if (diagnostics.rootCause == SyncRootCause.PROVIDER_EMPTY_NEEDS_GOOGLE_SYNC && diagnostics.isGoogleCalendarAppInstalled) {
                OutlinedButton(
                    onClick = onOpenGoogleCalendar,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.diagnostics_action_open_gcal))
                }
            }
        }
    }
}

@Composable
private fun SystemHealthCard(diagnostics: CalendarSyncDiagnostics) {
    val formattedClock = remember(diagnostics.checkedAtMillis) {
        SimpleDateFormat("EEE, MMM d, yyyy • h:mm a", Locale.getDefault())
            .format(Date(diagnostics.checkedAtMillis))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostics_system_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.diagnostics_section_system),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            DiagnosticKeyValueRow(
                label = "Device Clock",
                value = "$formattedClock (${diagnostics.deviceTimeZoneId})"
            )
            DiagnosticKeyValueRow(
                label = "Permissions",
                value = "Read: ${if (diagnostics.hasReadPermission) "Granted" else "Denied"} · Write: ${if (diagnostics.hasWritePermission) "Granted" else "Denied"}"
            )
            DiagnosticKeyValueRow(
                label = "Master Auto-Sync",
                value = when (diagnostics.masterSyncEnabled) {
                    true -> "Enabled"
                    false -> "Paused / Off"
                    null -> "Unavailable"
                }
            )
            DiagnosticKeyValueRow(
                label = "Google Calendar App",
                value = if (diagnostics.isGoogleCalendarAppInstalled) "Installed" else "Not detected"
            )
            DiagnosticKeyValueRow(
                label = "Active Filter",
                value = "${diagnostics.activeDaysHorizon}d horizon · Past: ${if (diagnostics.showPastEvents) "Shown" else "Hidden"} · ${if (diagnostics.isAllCalendarsSelected) "All calendars" else "${diagnostics.selectedCalendarIdsCount} selected"}"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CountMetricTile(label = "Raw All-Time", count = diagnostics.totalRawProviderEvents)
                CountMetricTile(label = "Next 60 Days", count = diagnostics.eventsIn60DayWindow)
                CountMetricTile(label = "Shown Now", count = diagnostics.eventsMatchingCurrentFilters)
            }

            if (!diagnostics.lastQueryError.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Last Provider Error: ${diagnostics.lastQueryError}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticKeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CountMetricTile(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarDiagnosticRowCard(entry: CalendarDiagnosticEntry) {
    val info = entry.calendarInfo
    val dotColor = remember(info.colorHex) { CalendarColorMapper.parseColorHex(info.colorHex) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostics_calendar_item_${info.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(dotColor, CircleShape)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.displayName.ifBlank { info.accountName },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${info.accountName} • ${info.accountType.ifBlank { "local" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusBadgePill(
                    text = if (info.isSyncEventsEnabled) "SYNC_EVENTS=1" else "SYNC_EVENTS=0",
                    isHealthy = info.isSyncEventsEnabled
                )
                StatusBadgePill(
                    text = if (info.isVisible) "Visible" else "Hidden",
                    isHealthy = info.isVisible
                )
                if (entry.autoSyncEnabled != null) {
                    StatusBadgePill(
                        text = if (entry.autoSyncEnabled) "Auto-Sync On" else "Auto-Sync Off",
                        isHealthy = entry.autoSyncEnabled
                    )
                }
                if (entry.isSyncActive) {
                    StatusBadgePill(text = "Syncing Now", isHealthy = true)
                } else if (entry.isSyncPending) {
                    StatusBadgePill(text = "Sync Queued", isHealthy = true)
                }
            }

            Text(
                text = "Raw: ${entry.totalRawEventsCount} · Next 60d: ${entry.upcoming60DayInstancesCount} · Shown: ${entry.inCurrentHorizonCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusBadgePill(
    text: String,
    isHealthy: Boolean
) {
    val bg = if (isHealthy) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val fg = if (isHealthy) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
