package com.example.data.model

import com.example.data.repository.CalendarAccountInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Classified root cause explaining why a device or emulator calendar may show zero events.
 */
enum class SyncRootCause {
    PERMISSION_DENIED,
    NO_CALENDARS_ON_DEVICE,
    MASTER_OR_ACCOUNT_SYNC_DISABLED,
    CALENDAR_SYNC_FLAG_OFF,
    PROVIDER_EMPTY_NEEDS_GOOGLE_SYNC,
    EVENTS_OUTSIDE_HORIZON_OR_FILTERS,
    PROVIDER_QUERY_ERROR,
    HEALTHY
}

/**
 * Per-calendar diagnostic telemetry read directly from Android's CalendarContract and ContentResolver.
 */
data class CalendarDiagnosticEntry(
    val calendarInfo: CalendarAccountInfo,
    val totalRawEventsCount: Int,
    val upcoming60DayInstancesCount: Int,
    val inCurrentHorizonCount: Int,
    val autoSyncEnabled: Boolean?,
    val isSyncActive: Boolean,
    val isSyncPending: Boolean
)

/**
 * Full snapshot of Android CalendarContract health, device time window, sync adapter state,
 * and raw vs filtered event counts.
 */
data class CalendarSyncDiagnostics(
    val checkedAtMillis: Long,
    val deviceTimeZoneId: String,
    val hasReadPermission: Boolean,
    val hasWritePermission: Boolean,
    val masterSyncEnabled: Boolean?,
    val isGoogleCalendarAppInstalled: Boolean,
    val activeDaysHorizon: Int,
    val showPastEvents: Boolean,
    val isAllCalendarsSelected: Boolean,
    val selectedCalendarIdsCount: Int,
    val calendars: List<CalendarDiagnosticEntry>,
    val totalRawProviderEvents: Int,
    val eventsIn60DayWindow: Int,
    val eventsMatchingCurrentFilters: Int,
    val lastQueryError: String?,
    val rootCause: SyncRootCause
) {
    val hasUnsyncedOrHiddenGoogleCalendar: Boolean
        get() = calendars.any { cal ->
            cal.calendarInfo.accountType == "com.google" &&
                (!cal.calendarInfo.isSyncEventsEnabled || !cal.calendarInfo.isVisible)
        }

    fun buildPlainTextReport(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("=== Agenda Widget Sync Diagnostics ===")
        sb.appendLine("Checked At: ${formatter.format(Date(checkedAtMillis))}")
        sb.appendLine("Timezone: $deviceTimeZoneId")
        sb.appendLine("Root Cause: $rootCause")
        sb.appendLine("Permissions: READ=$hasReadPermission, WRITE=$hasWritePermission")
        sb.appendLine("Master Auto-Sync: ${masterSyncEnabled?.toString() ?: "Unknown"}")
        sb.appendLine("Google Calendar App Installed: $isGoogleCalendarAppInstalled")
        sb.appendLine(
            "Filters: horizon=${activeDaysHorizon}d, showPast=$showPastEvents, allCalendars=$isAllCalendarsSelected (selected=$selectedCalendarIdsCount)"
        )
        sb.appendLine(
            "Event Counts: rawAllTime=$totalRawProviderEvents, next60d=$eventsIn60DayWindow, matchingFilters=$eventsMatchingCurrentFilters"
        )
        if (!lastQueryError.isNullOrBlank()) {
            sb.appendLine("Last Query Error: $lastQueryError")
        }
        sb.appendLine("Calendars (${calendars.size}):")
        if (calendars.isEmpty()) {
            sb.appendLine("  (none)")
        } else {
            calendars.forEach { entry ->
                val info = entry.calendarInfo
                sb.appendLine(
                    "  - [${info.id}] ${info.displayName} (${info.accountName} / ${info.accountType}) " +
                        "visible=${info.isVisible}, syncEvents=${info.isSyncEventsEnabled}, " +
                        "autoSync=${entry.autoSyncEnabled}, active=${entry.isSyncActive}, pending=${entry.isSyncPending}, " +
                        "raw=${entry.totalRawEventsCount}, next60d=${entry.upcoming60DayInstancesCount}, shown=${entry.inCurrentHorizonCount}"
                )
            }
        }
        return sb.toString().trimEnd()
    }
}

/**
 * Outcome summary returned after running one-tap calendar sync repair.
 */
data class SyncRepairReport(
    val calendarsFlagRepairedCount: Int,
    val accountsAutoSyncEnabledCount: Int,
    val accountsSyncRequestedCount: Int,
    val syncedEventsAfterRepair: Int
)
