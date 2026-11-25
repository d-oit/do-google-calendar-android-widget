package com.example

import android.accounts.Account
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.dao.EventDao
import com.example.data.db.AppDatabase
import com.example.data.model.CalendarDiagnosticEntry
import com.example.data.model.CalendarSyncDiagnostics
import com.example.data.model.OperationResult
import com.example.data.model.SyncRootCause
import com.example.data.pref.WidgetPreferences
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.repository.CalendarAccountInfo
import com.example.data.repository.CalendarRepository
import com.example.ui.CalendarViewModel
import com.example.ui.components.SyncDiagnosticsSheetContent
import com.example.ui.theme.MyApplicationTheme
import com.example.widget.CalendarAgendaViewsFactory
import com.example.widget.CalendarGlanceWidgetContent
import com.example.widget.WidgetThemeHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarSyncDiagnosticsRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var eventDao: EventDao
    private lateinit var prefsManager: WidgetPreferencesManager
    private lateinit var repository: CalendarRepository
    private lateinit var provider: FakeCalendarContractProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val providerInfo = ProviderInfo().apply {
            authority = CalendarContract.AUTHORITY
        }
        provider = Robolectric.buildContentProvider(FakeCalendarContractProvider::class.java)
            .create(providerInfo)
            .get()
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        eventDao = database.eventDao()
        prefsManager = WidgetPreferencesManager.getInstance(context)
        prefsManager.updatePreferences(WidgetPreferences())
        repository = CalendarRepository(context, eventDao, prefsManager)
        repository.updatePermissionState(explicitGrant = true)
    }

    @After
    fun tearDown() {
        provider.clearAll()
        database.close()
    }

    @Test
    fun `CUJ_28_google_account_with_sync_events_off_diagnosed_and_repaired_to_widgets`() = runBlocking {
        val accountEmail = "dmmotec@gmail.com"
        val account = Account(accountEmail, "com.google")
        ContentResolver.setMasterSyncAutomatically(true)
        ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, false)

        provider.addCalendar(
            FakeCalendarRow(
                id = 10L,
                accountName = accountEmail,
                accountType = "com.google",
                displayName = "Primary Google Calendar",
                isPrimary = true,
                visible = 0,
                syncEvents = 0
            )
        )

        val beforeDiagnostics = repository.collectSyncDiagnostics()
        assertEquals(SyncRootCause.CALENDAR_SYNC_FLAG_OFF, beforeDiagnostics.rootCause)
        assertTrue(beforeDiagnostics.hasUnsyncedOrHiddenGoogleCalendar)
        assertEquals(1, beforeDiagnostics.calendars.size)
        assertFalse(beforeDiagnostics.calendars.first().calendarInfo.isSyncEventsEnabled)

        val repairResult = repository.repairCalendarSyncAndRequestOsSync()
        assertTrue(repairResult is OperationResult.Success)
        val report = (repairResult as OperationResult.Success).data
        assertEquals(1, report.calendarsFlagRepairedCount)
        assertEquals(1, report.accountsSyncRequestedCount)
        assertEquals(1, provider.calendars.first().syncEvents)
        assertEquals(1, provider.calendars.first().visible)
        assertTrue(ContentResolver.getSyncAutomatically(account, CalendarContract.AUTHORITY))

        // Simulate Google Calendar Sync Adapter populating an upcoming event
        val now = System.currentTimeMillis()
        provider.addEvent(
            FakeEventRow(
                id = 101L,
                calendarId = 10L,
                calendarDisplayName = "Primary Google Calendar",
                title = "Emulator Synced Meeting",
                location = "Google Meet",
                startMillis = now + TimeUnit.HOURS.toMillis(2),
                endMillis = now + TimeUnit.HOURS.toMillis(3)
            )
        )

        val syncResult = repository.syncEvents(forceRefresh = true)
        assertTrue(syncResult is OperationResult.Success)
        assertEquals(1, (syncResult as OperationResult.Success).data)

        val afterDiagnostics = repository.collectSyncDiagnostics()
        assertEquals(SyncRootCause.HEALTHY, afterDiagnostics.rootCause)
        assertEquals(1, afterDiagnostics.eventsMatchingCurrentFilters)

        // Verify RemoteViews widget factory reads the synced event
        val factory = CalendarAgendaViewsFactory(context, Intent(), repository)
        factory.onCreate()
        factory.onDataSetChanged()
        assertEquals(1, factory.count)

        // Verify Jetpack Glance widget renders the synced event
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(DpSize(300.dp, 240.dp))
            val prefs = prefsManager.getPreferences()
            val theme = WidgetThemeHelper.resolveColors(context, prefs)
            val syncedEvents = repository.getUpcomingEventsSync()
            provideComposable {
                CalendarGlanceWidgetContent(
                    events = syncedEvents,
                    prefs = prefs,
                    theme = theme,
                    hasPermission = true,
                    syncRevision = 1,
                    lastSyncMillis = now
                )
            }
            onNode(hasText("Emulator Synced Meeting")).assertExists()
        }
    }

    @Test
    fun `CUJ_29_google_account_with_zero_raw_events_classified_as_needs_google_sync`() = runBlocking {
        val accountEmail = "dmmotec@gmail.com"
        val account = Account(accountEmail, "com.google")
        ContentResolver.setMasterSyncAutomatically(true)
        ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true)

        provider.addCalendar(
            FakeCalendarRow(
                id = 20L,
                accountName = accountEmail,
                accountType = "com.google",
                displayName = accountEmail,
                isPrimary = true,
                visible = 1,
                syncEvents = 1
            )
        )

        val diagnostics = repository.collectSyncDiagnostics()
        assertEquals(SyncRootCause.PROVIDER_EMPTY_NEEDS_GOOGLE_SYNC, diagnostics.rootCause)
        assertEquals(0, diagnostics.totalRawProviderEvents)
        assertEquals(1, diagnostics.calendars.size)

        val reportText = diagnostics.buildPlainTextReport()
        assertTrue(reportText.contains(accountEmail))
        assertTrue(reportText.contains("PROVIDER_EMPTY_NEEDS_GOOGLE_SYNC"))
    }

    @Test
    fun `CUJ_30_events_outside_7_day_horizon_diagnosed_and_fixed_by_quick_filter_expansion`() = runBlocking {
        val accountEmail = "dmmotec@gmail.com"
        val account = Account(accountEmail, "com.google")
        ContentResolver.setMasterSyncAutomatically(true)
        ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true)

        provider.addCalendar(
            FakeCalendarRow(
                id = 30L,
                accountName = accountEmail,
                accountType = "com.google",
                displayName = "Work",
                isPrimary = true,
                visible = 1,
                syncEvents = 1
            )
        )

        val now = System.currentTimeMillis()
        val inFourteenDays = now + TimeUnit.DAYS.toMillis(14)
        provider.addEvent(
            FakeEventRow(
                id = 301L,
                calendarId = 30L,
                calendarDisplayName = "Work",
                title = "Board Review Two Weeks Out",
                location = "HQ",
                startMillis = inFourteenDays,
                endMillis = inFourteenDays + TimeUnit.HOURS.toMillis(1)
            )
        )

        prefsManager.updatePreferences(WidgetPreferences(daysHorizon = 7, showPastEvents = false))
        repository.syncEvents(forceRefresh = true)

        val diagBefore = repository.collectSyncDiagnostics()
        assertEquals(SyncRootCause.EVENTS_OUTSIDE_HORIZON_OR_FILTERS, diagBefore.rootCause)
        assertEquals(1, diagBefore.totalRawProviderEvents)
        assertEquals(1, diagBefore.eventsIn60DayWindow)
        assertEquals(0, diagBefore.eventsMatchingCurrentFilters)
        assertTrue(repository.getUpcomingEventsSync().isEmpty())

        val viewModel = CalendarViewModel(repository, prefsManager)
        viewModel.applyFilterQuickFix()
        ShadowLooper.idleMainLooper()

        val updatedPrefs = prefsManager.getPreferences()
        assertEquals(30, updatedPrefs.daysHorizon)
        assertTrue(updatedPrefs.isAllCalendarsSelected)
        assertTrue(updatedPrefs.showPastEvents)

        val visibleEvents = repository.getUpcomingEventsSync()
        assertEquals(1, visibleEvents.size)
        assertEquals("Board Review Two Weeks Out", visibleEvents.first().title)
    }

    @Test
    fun `CUJ_31_ongoing_multiday_event_and_extended_column_fallback_resilience`() = runBlocking {
        provider.addCalendar(
            FakeCalendarRow(
                id = 40L,
                accountName = "dmmotec@gmail.com",
                accountType = "com.google",
                displayName = "Conference",
                isPrimary = true,
                visible = 1,
                syncEvents = 1
            )
        )

        val now = System.currentTimeMillis()
        // Multi-day event that started yesterday and ends tomorrow
        provider.addEvent(
            FakeEventRow(
                id = 401L,
                calendarId = 40L,
                calendarDisplayName = "Conference",
                title = "3-Day Android Summit",
                location = "Hall A",
                startMillis = now - TimeUnit.DAYS.toMillis(1),
                endMillis = now + TimeUnit.DAYS.toMillis(1)
            )
        )

        // 1. Simulate provider rejecting extended color columns on Instances -> core projection succeeds
        provider.rejectExtendedColorColumns = true
        val instances = repository.queryInstances(
            startMillis = CalendarRepository.getStartOfTodayMillis(now),
            endMillis = CalendarRepository.getHorizonEndMillis(7, now)
        )
        assertEquals(1, instances.size)
        assertEquals("3-Day Android Summit", instances.first().title)

        // 2. Simulate empty Instances table -> direct Events fallback catches ongoing multi-day event
        provider.returnEmptyInstances = true
        val fallbackSync = repository.syncEvents(forceRefresh = true)
        assertTrue(fallbackSync is OperationResult.Success)
        assertEquals(1, (fallbackSync as OperationResult.Success).data)
        assertEquals("3-Day Android Summit", repository.getUpcomingEventsSync().first().title)
    }

    @Test
    fun `CUJ_32_content_observer_detects_provider_event_arrival`() = runBlocking {
        val observedDeferred = async {
            repository.observeCalendarChanges().first()
        }
        ShadowLooper.idleMainLooper()
        context.contentResolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)
        ShadowLooper.idleMainLooper()

        observedDeferred.await()
        assertTrue(observedDeferred.isCompleted)
    }

    @Test
    fun `CUJ_33_sync_diagnostics_sheet_compose_ui_renders_and_triggers_callbacks`() {
        var repairClicked = false
        var settingsClicked = false
        var openGcalClicked = false
        var copiedReport: String? = null

        val sampleDiag = CalendarSyncDiagnostics(
            checkedAtMillis = System.currentTimeMillis(),
            deviceTimeZoneId = "America/Los_Angeles",
            hasReadPermission = true,
            hasWritePermission = true,
            masterSyncEnabled = true,
            isGoogleCalendarAppInstalled = true,
            activeDaysHorizon = 7,
            showPastEvents = false,
            isAllCalendarsSelected = true,
            selectedCalendarIdsCount = 0,
            calendars = listOf(
                CalendarDiagnosticEntry(
                    calendarInfo = CalendarAccountInfo(
                        id = 10L,
                        accountName = "dmmotec@gmail.com",
                        displayName = "Primary Google Calendar",
                        accountType = "com.google",
                        isPrimary = true,
                        isVisible = false,
                        isSyncEventsEnabled = false
                    ),
                    totalRawEventsCount = 0,
                    upcoming60DayInstancesCount = 0,
                    inCurrentHorizonCount = 0,
                    autoSyncEnabled = true,
                    isSyncActive = false,
                    isSyncPending = false
                )
            ),
            totalRawProviderEvents = 0,
            eventsIn60DayWindow = 0,
            eventsMatchingCurrentFilters = 0,
            lastQueryError = null,
            rootCause = SyncRootCause.CALENDAR_SYNC_FLAG_OFF
        )

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                SyncDiagnosticsSheetContent(
                    diagnostics = sampleDiag,
                    isRunningDiagnostics = false,
                    isRepairingSync = false,
                    onDismiss = {},
                    onRefreshDiagnostics = {},
                    onRepairAndForceSync = { repairClicked = true },
                    onExpandFiltersQuickFix = {},
                    onOpenSyncSettings = { settingsClicked = true },
                    onOpenGoogleCalendar = { openGcalClicked = true },
                    onCopyReport = { copiedReport = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("diagnostics_root_cause_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("diagnostics_calendar_item_10").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Primary Google Calendar", substring = true).assertExists()
        composeTestRule.onNodeWithText("SYNC_EVENTS=0").assertExists()

        composeTestRule.onNodeWithTag("diagnostics_repair_sync_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("diagnostics_open_sync_settings_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("diagnostics_open_google_calendar_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("diagnostics_copy_report_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertTrue(repairClicked)
        assertTrue(settingsClicked)
        assertTrue(openGcalClicked)
        assertTrue(copiedReport?.contains("dmmotec@gmail.com") == true)
    }
}

private data class FakeCalendarRow(
    val id: Long,
    val accountName: String,
    val accountType: String,
    val displayName: String,
    val isPrimary: Boolean,
    val color: Int = 0xFF1A73E8.toInt(),
    val accessLevel: Int = CalendarContract.Calendars.CAL_ACCESS_OWNER,
    var visible: Int = 1,
    var syncEvents: Int = 1
)

private data class FakeEventRow(
    val id: Long,
    val calendarId: Long,
    val calendarDisplayName: String,
    val title: String,
    val location: String? = null,
    val description: String? = null,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Int = 0,
    val deleted: Int = 0,
    val color: Int = 0xFF1A73E8.toInt(),
    val rrule: String? = null
)

/**
 * In-memory Android CalendarContract ContentProvider for deterministic Robolectric JVM testing
 * of Calendars, Events, Instances, column fallbacks, and SYNC_EVENTS/VISIBLE flag repairs.
 */
private class FakeCalendarContractProvider : ContentProvider() {
    val calendars = mutableListOf<FakeCalendarRow>()
    val events = mutableListOf<FakeEventRow>()
    var rejectExtendedColorColumns: Boolean = false
    var returnEmptyInstances: Boolean = false

    fun addCalendar(row: FakeCalendarRow) {
        calendars.add(row)
    }

    fun addEvent(row: FakeEventRow) {
        events.add(row)
    }

    fun clearAll() {
        calendars.clear()
        events.clear()
        rejectExtendedColorColumns = false
        returnEmptyInstances = false
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val path = uri.path ?: ""
        return when {
            path.startsWith("/calendars") -> queryCalendars(projection)
            path.startsWith("/instances/when/") -> queryInstances(uri, projection)
            path.startsWith("/events") -> queryEvents(projection, selection, selectionArgs)
            else -> MatrixCursor(projection ?: arrayOf("_id"))
        }
    }

    private fun queryCalendars(projection: Array<out String>?): Cursor {
        val cols = projection ?: arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_COLOR_KEY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS
        )
        val cursor = MatrixCursor(cols)
        calendars.forEach { cal ->
            val row = cols.map { col ->
                when (col) {
                    CalendarContract.Calendars._ID -> cal.id
                    CalendarContract.Calendars.ACCOUNT_NAME -> cal.accountName
                    CalendarContract.Calendars.ACCOUNT_TYPE -> cal.accountType
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME -> cal.displayName
                    CalendarContract.Calendars.IS_PRIMARY -> if (cal.isPrimary) 1 else 0
                    CalendarContract.Calendars.OWNER_ACCOUNT -> cal.accountName
                    CalendarContract.Calendars.CALENDAR_COLOR -> cal.color
                    CalendarContract.Calendars.CALENDAR_COLOR_KEY -> null
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL -> cal.accessLevel
                    CalendarContract.Calendars.VISIBLE -> cal.visible
                    CalendarContract.Calendars.SYNC_EVENTS -> cal.syncEvents
                    else -> null
                }
            }.toTypedArray()
            cursor.addRow(row)
        }
        return cursor
    }

    private fun queryInstances(uri: Uri, projection: Array<out String>?): Cursor {
        if (rejectExtendedColorColumns && projection?.contains(CalendarContract.Instances.EVENT_COLOR_KEY) == true) {
            throw IllegalArgumentException("Invalid column eventColor_index in simulated provider")
        }
        val cols = projection ?: arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.CALENDAR_ID
        )
        val cursor = MatrixCursor(cols)
        if (returnEmptyInstances) return cursor

        val segments = uri.pathSegments
        val windowStart = segments.getOrNull(segments.size - 2)?.toLongOrNull() ?: Long.MIN_VALUE
        val windowEnd = segments.lastOrNull()?.toLongOrNull() ?: Long.MAX_VALUE

        events.filter { it.deleted == 0 && it.startMillis <= windowEnd && it.endMillis >= windowStart }
            .forEach { evt ->
                val row = cols.map { col ->
                    when (col) {
                        CalendarContract.Instances.EVENT_ID -> evt.id
                        CalendarContract.Instances.TITLE -> evt.title
                        CalendarContract.Instances.DESCRIPTION -> evt.description
                        CalendarContract.Instances.EVENT_LOCATION -> evt.location
                        CalendarContract.Instances.BEGIN -> evt.startMillis
                        CalendarContract.Instances.END -> evt.endMillis
                        CalendarContract.Instances.ALL_DAY -> evt.allDay
                        CalendarContract.Instances.DISPLAY_COLOR -> evt.color
                        CalendarContract.Instances.EVENT_COLOR -> evt.color
                        CalendarContract.Instances.CALENDAR_COLOR -> evt.color
                        CalendarContract.Instances.EVENT_COLOR_KEY -> null
                        CalendarContract.Instances.CALENDAR_DISPLAY_NAME -> evt.calendarDisplayName
                        CalendarContract.Instances.CALENDAR_ID -> evt.calendarId
                        CalendarContract.Instances.RRULE -> evt.rrule
                        CalendarContract.Instances.EVENT_TIMEZONE -> "UTC"
                        else -> null
                    }
                }.toTypedArray()
                cursor.addRow(row)
            }
        return cursor
    }

    private fun queryEvents(
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Cursor {
        if (rejectExtendedColorColumns && projection?.contains(CalendarContract.Events.EVENT_COLOR_KEY) == true) {
            throw IllegalArgumentException("Invalid column eventColor_index in simulated Events provider")
        }
        val cols = projection ?: arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND
        )
        val cursor = MatrixCursor(cols)

        val filtered = events.filter { evt ->
            if (evt.deleted != 0) return@filter false
            if (selection != null && selection.contains(CalendarContract.Events.DTSTART) && selectionArgs != null && selectionArgs.size >= 2) {
                val endWindow = selectionArgs[0].toLongOrNull() ?: Long.MAX_VALUE
                val startWindow = selectionArgs[1].toLongOrNull() ?: Long.MIN_VALUE
                evt.startMillis <= endWindow && evt.endMillis >= startWindow
            } else {
                true
            }
        }

        filtered.forEach { evt ->
            val row = cols.map { col ->
                when (col) {
                    CalendarContract.Events._ID -> evt.id
                    CalendarContract.Events.TITLE -> evt.title
                    CalendarContract.Events.DESCRIPTION -> evt.description
                    CalendarContract.Events.EVENT_LOCATION -> evt.location
                    CalendarContract.Events.DTSTART -> evt.startMillis
                    CalendarContract.Events.DTEND -> evt.endMillis
                    CalendarContract.Events.ALL_DAY -> evt.allDay
                    CalendarContract.Events.DISPLAY_COLOR -> evt.color
                    CalendarContract.Events.EVENT_COLOR -> evt.color
                    CalendarContract.Events.CALENDAR_COLOR -> evt.color
                    CalendarContract.Events.EVENT_COLOR_KEY -> null
                    CalendarContract.Events.CALENDAR_DISPLAY_NAME -> evt.calendarDisplayName
                    CalendarContract.Events.CALENDAR_ID -> evt.calendarId
                    CalendarContract.Events.RRULE -> evt.rrule
                    else -> null
                }
            }.toTypedArray()
            cursor.addRow(row)
        }
        return cursor
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        val path = uri.path ?: return 0
        if (path.startsWith("/calendars/") && values != null) {
            val calId = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return 0
            val target = calendars.firstOrNull { it.id == calId } ?: return 0
            if (values.containsKey(CalendarContract.Calendars.SYNC_EVENTS)) {
                target.syncEvents = values.getAsInteger(CalendarContract.Calendars.SYNC_EVENTS) ?: target.syncEvents
            }
            if (values.containsKey(CalendarContract.Calendars.VISIBLE)) {
                target.visible = values.getAsInteger(CalendarContract.Calendars.VISIBLE) ?: target.visible
            }
            context?.contentResolver?.notifyChange(CalendarContract.Calendars.CONTENT_URI, null)
            return 1
        }
        return 0
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun getType(uri: Uri): String? = null
}
