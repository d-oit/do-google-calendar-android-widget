package com.example.data.repository

import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import com.example.data.model.CalendarDiagnosticEntry
import com.example.data.model.CalendarSyncDiagnostics
import com.example.data.model.SyncRepairReport
import com.example.data.model.SyncRootCause
import com.example.data.pref.WidgetPreferences
import com.example.util.AppLogger
import java.util.TimeZone

/**
 * Inspects Android's native [CalendarContract] provider & [ContentResolver] sync adapter state
 * and performs safe, zero-API-key sync repair actions (enabling SYNC_EVENTS/VISIBLE flags and
 * requesting expedited account syncs for connected Google accounts).
 */
class CalendarSyncDiagnosticsHelper(
    private val context: Context
) {
    companion object {
        const val GOOGLE_CALENDAR_PACKAGE = "com.google.android.calendar"
        private const val DIAGNOSTIC_WINDOW_DAYS = 60
    }

    fun isGoogleCalendarAppInstalled(): Boolean {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(GOOGLE_CALENDAR_PACKAGE)
            if (launchIntent != null) return true
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time"))
            pm.resolveActivity(viewIntent, PackageManager.MATCH_DEFAULT_ONLY) != null
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_SYNC, "Unable to query installed calendar apps", e)
            false
        }
    }

    fun readMasterSyncAutomatically(): Boolean? {
        return try {
            ContentResolver.getMasterSyncAutomatically()
        } catch (e: SecurityException) {
            AppLogger.w(AppLogger.TAG_SYNC, "READ_SYNC_SETTINGS missing for master sync check", e)
            null
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_SYNC, "Failed reading master auto-sync state", e)
            null
        }
    }

    fun readAccountAutoSync(accountName: String, accountType: String): Boolean? {
        if (accountName.isBlank() || accountType.isBlank() || accountType == CalendarContract.ACCOUNT_TYPE_LOCAL) {
            return null
        }
        return try {
            ContentResolver.getSyncAutomatically(Account(accountName, accountType), CalendarContract.AUTHORITY)
        } catch (e: SecurityException) {
            AppLogger.w(AppLogger.TAG_SYNC, "Permission denied reading account auto-sync for $accountName", e)
            null
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_SYNC, "Failed reading account auto-sync for $accountName", e)
            null
        }
    }

    fun isAccountSyncActive(accountName: String, accountType: String): Boolean {
        if (accountName.isBlank() || accountType.isBlank() || accountType == CalendarContract.ACCOUNT_TYPE_LOCAL) {
            return false
        }
        return try {
            ContentResolver.isSyncActive(Account(accountName, accountType), CalendarContract.AUTHORITY)
        } catch (e: Exception) {
            AppLogger.d(AppLogger.TAG_SYNC, "Unable to read isSyncActive for $accountName: ${e.message}")
            false
        }
    }

    fun isAccountSyncPending(accountName: String, accountType: String): Boolean {
        if (accountName.isBlank() || accountType.isBlank() || accountType == CalendarContract.ACCOUNT_TYPE_LOCAL) {
            return false
        }
        return try {
            ContentResolver.isSyncPending(Account(accountName, accountType), CalendarContract.AUTHORITY)
        } catch (e: Exception) {
            AppLogger.d(AppLogger.TAG_SYNC, "Unable to read isSyncPending for $accountName: ${e.message}")
            false
        }
    }

    fun countRawEventsPerCalendar(): Map<Long, Int> {
        val counts = mutableMapOf<Long, Int>()
        try {
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.CALENDAR_ID
            )
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events.DELETED} = 0",
                null,
                null
            )?.use { cursor ->
                val calIdIdx = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
                while (cursor.moveToNext()) {
                    val calId = if (calIdIdx != -1 && !cursor.isNull(calIdIdx)) cursor.getLong(calIdIdx) else -1L
                    counts[calId] = (counts[calId] ?: 0) + 1
                }
            }
        } catch (e: SecurityException) {
            AppLogger.w(AppLogger.TAG_SYNC, "Permission denied counting raw CalendarContract.Events", e)
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_SYNC, "Error counting raw CalendarContract.Events", e)
        }
        return counts
    }

    fun buildDiagnostics(
        hasReadPermission: Boolean,
        hasWritePermission: Boolean,
        calendars: List<CalendarAccountInfo>,
        prefs: WidgetPreferences,
        upcoming60DayEvents: List<com.example.data.model.EventEntity>,
        lastQueryError: String?,
        nowMillis: Long = System.currentTimeMillis()
    ): CalendarSyncDiagnostics {
        val masterSync = readMasterSyncAutomatically()
        val gcalInstalled = isGoogleCalendarAppInstalled()
        val rawCountsByCal = if (hasReadPermission) countRawEventsPerCalendar() else emptyMap()
        val filteredEvents = CalendarRepository.filterEventsForPreferences(
            events = upcoming60DayEvents,
            prefs = prefs,
            nowMillis = nowMillis
        )

        val upcoming60ByCal = upcoming60DayEvents.groupingBy { it.sourceCalendarId }.eachCount()
        val filteredByCal = filteredEvents.groupingBy { it.sourceCalendarId }.eachCount()

        val entries = calendars.map { cal ->
            CalendarDiagnosticEntry(
                calendarInfo = cal,
                totalRawEventsCount = rawCountsByCal[cal.id] ?: 0,
                upcoming60DayInstancesCount = upcoming60ByCal[cal.id] ?: 0,
                inCurrentHorizonCount = filteredByCal[cal.id] ?: 0,
                autoSyncEnabled = readAccountAutoSync(cal.accountName, cal.accountType),
                isSyncActive = isAccountSyncActive(cal.accountName, cal.accountType),
                isSyncPending = isAccountSyncPending(cal.accountName, cal.accountType)
            )
        }

        val totalRawProviderEvents = rawCountsByCal.values.sum()
        val eventsIn60DayWindow = upcoming60DayEvents.size
        val eventsMatchingFilters = filteredEvents.size

        val rootCause = classifyRootCause(
            hasReadPermission = hasReadPermission,
            calendars = entries,
            masterSyncEnabled = masterSync,
            totalRawProviderEvents = totalRawProviderEvents,
            eventsIn60DayWindow = eventsIn60DayWindow,
            eventsMatchingFilters = eventsMatchingFilters,
            lastQueryError = lastQueryError
        )

        return CalendarSyncDiagnostics(
            checkedAtMillis = nowMillis,
            deviceTimeZoneId = TimeZone.getDefault().id,
            hasReadPermission = hasReadPermission,
            hasWritePermission = hasWritePermission,
            masterSyncEnabled = masterSync,
            isGoogleCalendarAppInstalled = gcalInstalled,
            activeDaysHorizon = prefs.daysHorizon,
            showPastEvents = prefs.showPastEvents,
            isAllCalendarsSelected = prefs.isAllCalendarsSelected,
            selectedCalendarIdsCount = prefs.selectedCalendarIds.size,
            calendars = entries,
            totalRawProviderEvents = totalRawProviderEvents,
            eventsIn60DayWindow = eventsIn60DayWindow,
            eventsMatchingCurrentFilters = eventsMatchingFilters,
            lastQueryError = lastQueryError,
            rootCause = rootCause
        )
    }

    internal fun classifyRootCause(
        hasReadPermission: Boolean,
        calendars: List<CalendarDiagnosticEntry>,
        masterSyncEnabled: Boolean?,
        totalRawProviderEvents: Int,
        eventsIn60DayWindow: Int,
        eventsMatchingFilters: Int,
        lastQueryError: String?
    ): SyncRootCause {
        if (!hasReadPermission) return SyncRootCause.PERMISSION_DENIED
        if (!lastQueryError.isNullOrBlank() && eventsMatchingFilters == 0) {
            return SyncRootCause.PROVIDER_QUERY_ERROR
        }
        if (calendars.isEmpty()) return SyncRootCause.NO_CALENDARS_ON_DEVICE
        if (eventsMatchingFilters > 0) return SyncRootCause.HEALTHY

        if (eventsIn60DayWindow > 0 || totalRawProviderEvents > 0) {
            return SyncRootCause.EVENTS_OUTSIDE_HORIZON_OR_FILTERS
        }

        val googleEntries = calendars.filter { it.calendarInfo.accountType == "com.google" }
        val targetEntries = googleEntries.ifEmpty { calendars }

        if (targetEntries.any { !it.calendarInfo.isSyncEventsEnabled || !it.calendarInfo.isVisible }) {
            return SyncRootCause.CALENDAR_SYNC_FLAG_OFF
        }

        val anyAccountAutoSyncOff = targetEntries.any { it.autoSyncEnabled == false }
        if (masterSyncEnabled == false || anyAccountAutoSyncOff) {
            return SyncRootCause.MASTER_OR_ACCOUNT_SYNC_DISABLED
        }

        return SyncRootCause.PROVIDER_EMPTY_NEEDS_GOOGLE_SYNC
    }

    /**
     * Enables SYNC_EVENTS=1 and VISIBLE=1 on disabled calendars (when WRITE_CALENDAR is granted)
     * and requests an expedited manual Android sync on [CalendarContract.AUTHORITY] for all
     * discovered non-local accounts.
     */
    fun repairAndRequestOsSync(
        calendars: List<CalendarAccountInfo>,
        hasWritePermission: Boolean
    ): SyncRepairReport {
        var repairedCalendars = 0
        if (hasWritePermission) {
            calendars.forEach { cal ->
                if (!cal.isSyncEventsEnabled || !cal.isVisible) {
                    try {
                        val calUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, cal.id)
                        val values = ContentValues().apply {
                            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                            put(CalendarContract.Calendars.VISIBLE, 1)
                        }
                        val updated = context.contentResolver.update(calUri, values, null, null)
                        if (updated > 0) {
                            repairedCalendars++
                            AppLogger.i(
                                AppLogger.TAG_SYNC,
                                "Enabled SYNC_EVENTS=1 and VISIBLE=1 on calendar id=${cal.id} (${cal.displayName})"
                            )
                        }
                    } catch (e: SecurityException) {
                        AppLogger.w(AppLogger.TAG_SYNC, "SecurityException updating calendar flags for id=${cal.id}", e)
                    } catch (e: Exception) {
                        AppLogger.w(AppLogger.TAG_SYNC, "Failed updating calendar flags for id=${cal.id}", e)
                    }
                }
            }
        }

        val uniqueAccounts = calendars
            .filter { it.accountName.isNotBlank() && it.accountType.isNotBlank() && it.accountType != CalendarContract.ACCOUNT_TYPE_LOCAL }
            .distinctBy { "${it.accountType}::${it.accountName}" }

        var autoSyncEnabledCount = 0
        var syncRequestedCount = 0

        uniqueAccounts.forEach { cal ->
            val account = Account(cal.accountName, cal.accountType)
            try {
                if (ContentResolver.getSyncAutomatically(account, CalendarContract.AUTHORITY).not()) {
                    ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true)
                    autoSyncEnabledCount++
                    AppLogger.i(AppLogger.TAG_SYNC, "Enabled calendar auto-sync for ${cal.accountName}")
                }
            } catch (e: SecurityException) {
                AppLogger.w(AppLogger.TAG_SYNC, "Unable to modify auto-sync for ${cal.accountName}", e)
            } catch (e: Exception) {
                AppLogger.w(AppLogger.TAG_SYNC, "Error checking/enabling auto-sync for ${cal.accountName}", e)
            }

            try {
                val extras = Bundle().apply {
                    putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                }
                ContentResolver.requestSync(account, CalendarContract.AUTHORITY, extras)
                syncRequestedCount++
                AppLogger.i(
                    AppLogger.TAG_SYNC,
                    "Requested manual expedited CalendarContract sync for ${cal.accountName} (${cal.accountType})"
                )
            } catch (e: Exception) {
                AppLogger.w(AppLogger.TAG_SYNC, "Failed calling requestSync for ${cal.accountName}", e)
            }
        }

        return SyncRepairReport(
            calendarsFlagRepairedCount = repairedCalendars,
            accountsAutoSyncEnabledCount = autoSyncEnabledCount,
            accountsSyncRequestedCount = syncRequestedCount,
            syncedEventsAfterRepair = 0
        )
    }

    fun getDiagnosticWindowDays(): Int = DIAGNOSTIC_WINDOW_DAYS
}
