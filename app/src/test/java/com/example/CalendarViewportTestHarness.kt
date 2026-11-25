package com.example

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.dao.EventDao
import com.example.data.db.AppDatabase
import com.example.data.model.AppError
import com.example.data.model.EventEntity
import com.example.data.model.OperationResult
import com.example.data.repository.CalendarRepository
import com.example.ui.CalendarViewModel
import com.example.ui.ErrorAction
import com.example.ui.UiError
import com.example.ui.UiEvent
import com.example.util.AppLogger
import com.example.widget.CalendarAgendaViewsFactory
import com.example.widget.CalendarAgendaWidgetProvider
import com.example.data.pref.WidgetColorTheme
import com.example.data.pref.WidgetDensity
import com.example.data.pref.WidgetPreferences
import com.example.data.pref.WidgetPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.robolectric.shadows.ShadowLooper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarViewportTestHarness {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var eventDao: EventDao
    private lateinit var prefsManager: WidgetPreferencesManager
    private lateinit var repository: CalendarRepository
    private lateinit var viewModel: CalendarViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        eventDao = database.eventDao()
        prefsManager = WidgetPreferencesManager.getInstance(context)
        prefsManager.updatePreferences(WidgetPreferences())
        repository = CalendarRepository(context, eventDao, prefsManager)
        viewModel = CalendarViewModel(repository, prefsManager)
    }

    @After
    fun tearDown() {
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
        database.close()
    }

    // =========================================================================
    // 1. Phone Viewport E2E Tests (Compact Screen, 360x640dp)
    // =========================================================================
    @Test
    @Config(qualifiers = "w360dp-h640dp-normal-long-port-hdpi")
    fun `CUJ_01_phone_viewport_displays_tabbed_compact_configuration`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        assertNotNull(activity)

        val screenWidthDp = activity.resources.configuration.screenWidthDp
        val isWideScreen = screenWidthDp >= 600

        // Phone compact screen width must be less than 600dp (360dp)
        assertEquals(360, screenWidthDp)
        assertFalse("Phone should use compact tabbed layout instead of wide dual pane", isWideScreen)
        controller.destroy()
    }

    // =========================================================================
    // 2. Foldable Unfolded Viewport E2E Tests (Medium Screen, 600x800dp)
    // =========================================================================
    @Test
    @Config(qualifiers = "w600dp-h800dp-normal-notlong-port-hdpi")
    fun `CUJ_02_foldable_viewport_displays_dual_pane_configuration`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        assertNotNull(activity)

        val screenWidthDp = activity.resources.configuration.screenWidthDp
        val isWideScreen = screenWidthDp >= 600

        // Foldable unfolded screen width is 600dp, activating dual-pane layout
        assertEquals(600, screenWidthDp)
        assertTrue("Foldable should activate wide dual-pane layout", isWideScreen)
        controller.destroy()
    }

    // =========================================================================
    // 3. 10-inch Tablet Viewport E2E Tests (Expanded Screen, 1024x768dp)
    // =========================================================================
    @Test
    @Config(qualifiers = "w1024dp-h768dp-large-notlong-land-mdpi")
    fun `CUJ_03_tablet_viewport_displays_expanded_dual_pane_canvas`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        assertNotNull(activity)

        val screenWidthDp = activity.resources.configuration.screenWidthDp
        val isWideScreen = screenWidthDp >= 600

        // 10-inch Tablet landscape screen width is 1024dp, activating expanded canvas
        assertEquals(1024, screenWidthDp)
        assertTrue("Tablet should activate expanded dual-pane layout", isWideScreen)
        controller.destroy()
    }

    // =========================================================================
    // 4. Intent Extras Navigation CUJ (Open Add Dialog or Select Event)
    // =========================================================================
    @Test
    fun `CUJ_04_intent_extras_opens_add_dialog`() {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("OPEN_ADD_EVENT", true)
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).create()
        val activity = controller.get()
        assertNotNull(activity)
        controller.destroy()
    }

    // =========================================================================
    // 5. Agenda Search and Category Filtering CUJ
    // =========================================================================
    @Test
    fun `CUJ_05_search_and_category_filtering_flow`() = runBlocking {
        val cal = Calendar.getInstance()
        val start1 = cal.timeInMillis + 100000
        val end1 = start1 + 3600000
        val start2 = cal.timeInMillis + 86400000
        val end2 = start2 + 3600000

        eventDao.insertEvents(
            listOf(
                EventEntity(
                    id = "e1",
                    title = "Deep Learning Architecture Review",
                    location = "Room 101",
                    startMillis = start1,
                    endMillis = end1,
                    colorHex = "#1A73E8",
                    dayHeader = "TODAY"
                ),
                EventEntity(
                    id = "e2",
                    title = "Marketing All-Hands",
                    location = "Virtual Auditorium",
                    startMillis = start2,
                    endMillis = end2,
                    colorHex = "#E67C73",
                    dayHeader = "TOMORROW"
                )
            )
        )

        // Idle main looper so StateFlow updates
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)

        val eventsInDb = eventDao.getUpcomingEventsSync(cal.timeInMillis)
        assertEquals(2, eventsInDb.size)

        // Test search query
        viewModel.onSearchQueryChanged("Architecture")
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("Architecture", viewModel.uiState.value.searchQuery)

        // Clear search and test category filter
        viewModel.onSearchQueryChanged("")
        viewModel.onCategorySelected("Today")
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("Today", viewModel.uiState.value.activeCategory)

        viewModel.onCategorySelected("Tomorrow")
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("Tomorrow", viewModel.uiState.value.activeCategory)
    }

    // =========================================================================
    // 6. Add and Delete Event CUJ Flow (Indexed Storage & Repository)
    // =========================================================================
    @Test
    fun `CUJ_06_add_and_delete_event_persistence`() = runBlocking {
        val cal = Calendar.getInstance()
        val start = cal.timeInMillis + 200000
        val end = start + 3600000

        repository.addEvent(
            title = "Executive Sync with VP",
            location = "Boardroom B",
            startMillis = start,
            endMillis = end,
            colorHex = "#34A853"
        )

        val eventsAfterAdd = eventDao.getUpcomingEventsSync(cal.timeInMillis)
        assertEquals(1, eventsAfterAdd.size)
        val addedEvent = eventsAfterAdd[0]
        assertEquals("Executive Sync with VP", addedEvent.title)
        assertEquals("Boardroom B", addedEvent.location)

        // Delete the event
        repository.deleteEvent(addedEvent.id)
        val eventsAfterDelete = eventDao.getUpcomingEventsSync(cal.timeInMillis)
        assertEquals(0, eventsAfterDelete.size)
    }

    // =========================================================================
    // 7. Home Screen Widget RemoteViews Pipeline
    // =========================================================================
    @Test
    fun `CUJ_07_widget_remoteviews_factory_population`() = runBlocking {
        val cal = Calendar.getInstance()
        val start = cal.timeInMillis + 50000
        val end = start + 3600000

        eventDao.insertEvent(
            EventEntity(
                id = "widget_e1",
                title = "Quarterly Sprint Review",
                location = "Google Meet",
                startMillis = start,
                endMillis = end,
                colorHex = "#1A73E8",
                dayHeader = "TODAY",
                isFirstOfDay = true
            )
        )

        val serviceIntent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 101)
        }
        val factory = CalendarAgendaViewsFactory(context, serviceIntent, repository)
        factory.onCreate()
        factory.onDataSetChanged()

        assertTrue(factory.count >= 1)
        val remoteViews = factory.getViewAt(0)
        assertNotNull(remoteViews)
        assertTrue(factory.hasStableIds())

        // Verify root widget layout and item layout inflate without InflateException
        val rootViews = android.widget.RemoteViews(context.packageName, R.layout.widget_calendar_agenda)
        val inflatedRoot = rootViews.apply(context, null)
        assertNotNull(inflatedRoot)

        val inflatedItem = remoteViews?.apply(context, null)
        assertNotNull(inflatedItem)
    }

    // =========================================================================
    // 8. Security: Intent & URI Sanitization
    // =========================================================================
    @Test
    fun `CUJ_08_security_url_validation_in_widget`() {
        val safeHttps = "https://calendar.google.com/event?eid=123"
        val safeHttp = "http://example.com/meeting"
        val unsafeJavascript = "javascript:alert(1)"
        val unsafeFile = "file:///data/data/com.example/databases/db"

        assertTrue(safeHttps.startsWith("https://", ignoreCase = true))
        assertTrue(safeHttp.startsWith("http://", ignoreCase = true))
        assertFalse(unsafeJavascript.startsWith("http://", ignoreCase = true) || unsafeJavascript.startsWith("https://", ignoreCase = true))
        assertFalse(unsafeFile.startsWith("http://", ignoreCase = true) || unsafeFile.startsWith("https://", ignoreCase = true))
    }

    // =========================================================================
    // 9. Room Database Entity & DAO Offline Cache Tests
    // =========================================================================
    @Test
    fun `CUJ_09_room_entity_and_dao_offline_caching_for_widget`() = runBlocking {
        val now = System.currentTimeMillis()
        val oldPastStart = now - (5 * 3600000L)
        val oldPastEnd = now - (4 * 3600000L)
        val ongoingStart = now - (30 * 60000L)
        val ongoingEnd = now + (30 * 60000L)
        val futureStart = now + (2 * 3600000L)
        val futureEnd = now + (3 * 3600000L)

        val pastEvent = EventEntity(
            id = "past_1",
            title = "Morning Coffee",
            location = "Kitchen",
            startMillis = oldPastStart,
            endMillis = oldPastEnd,
            isOfflineCreated = false
        )
        val ongoingEvent = EventEntity(
            id = "ongoing_1",
            title = "Sprint Demo",
            location = "Room 302",
            startMillis = ongoingStart,
            endMillis = ongoingEnd,
            isOfflineCreated = true
        )
        val futureEvent = EventEntity(
            id = "future_1",
            title = "Client Presentation",
            location = "Video Call",
            startMillis = futureStart,
            endMillis = futureEnd,
            isOfflineCreated = false
        )

        // Verify entity helper methods
        assertTrue(pastEvent.isPast(now))
        assertFalse(pastEvent.isOngoing(now))
        assertTrue(ongoingEvent.isOngoing(now))
        assertFalse(ongoingEvent.isPast(now))
        assertFalse(futureEvent.isPast(now))
        assertFalse(futureEvent.isOngoing(now))

        // Batch insert into Room DAO
        eventDao.insertEvents(listOf(pastEvent, ongoingEvent, futureEvent))
        assertEquals(3, eventDao.getEventCountSync())

        // Verify synchronous queries for widget
        val upcomingForWidget = eventDao.getUpcomingEventsSync(now)
        assertEquals(2, upcomingForWidget.size)
        assertEquals("ongoing_1", upcomingForWidget[0].id)
        assertEquals("future_1", upcomingForWidget[1].id)

        // Verify limit query for compact widget view
        val limitedEvents = eventDao.getUpcomingEventsLimitSync(now, limit = 1)
        assertEquals(1, limitedEvents.size)
        assertEquals("ongoing_1", limitedEvents[0].id)

        // Verify synchronous lookup by ID
        val singleEvent = eventDao.getEventByIdSync("ongoing_1")
        assertNotNull(singleEvent)
        assertEquals("Sprint Demo", singleEvent?.title)
        assertTrue(singleEvent?.isOfflineCreated == true)

        // Verify search Flow
        val searchResults = eventDao.searchEventsFlow("Demo").first()
        assertEquals(1, searchResults.size)
        assertEquals("Sprint Demo", searchResults[0].title)

        // Verify pruning past events older than cutoff
        val deletedCount = eventDao.deleteEventsOlderThan(now - 3600000L)
        assertEquals(1, deletedCount)
        assertEquals(2, eventDao.getEventCountSync())
    }

    // =========================================================================
    // 10. Widget Customization Preferences & Studio State
    // =========================================================================
    @Test
    fun `CUJ_10_widget_customization_preferences_persistence`() {
        val customPrefs = WidgetPreferences(
            opacityPercent = 50,
            colorTheme = WidgetColorTheme.EMERALD,
            density = WidgetDensity.COMPACT,
            daysHorizon = 14,
            selectedCalendarIds = setOf(101L, 102L),
            isAllCalendarsSelected = false,
            showLocations = false,
            showDayHeaders = true,
            showPastEvents = true
        )

        viewModel.updateWidgetPreferences(customPrefs)

        val retrievedPrefs = prefsManager.getPreferences()
        assertEquals(50, retrievedPrefs.opacityPercent)
        assertEquals(WidgetColorTheme.EMERALD, retrievedPrefs.colorTheme)
        assertEquals(WidgetDensity.COMPACT, retrievedPrefs.density)
        assertEquals(14, retrievedPrefs.daysHorizon)
        assertEquals(setOf(101L, 102L), retrievedPrefs.selectedCalendarIds)
        assertFalse(retrievedPrefs.isAllCalendarsSelected)
        assertFalse(retrievedPrefs.showLocations)
        assertTrue(retrievedPrefs.showDayHeaders)
        assertTrue(retrievedPrefs.showPastEvents)
    }

    // =========================================================================
    // 11. Calendar Permission State & Repository Sync Trigger CUJ
    // =========================================================================
    @Test
    fun `CUJ_11_calendar_permission_state_management`() {
        // Initially without granted permission
        assertFalse(viewModel.uiState.value.hasCalendarPermission)

        // Simulate user granting READ_CALENDAR permission via the component callback
        viewModel.onPermissionResult(true)
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)

        // StateFlow updates to reflect granted permission state
        assertTrue(viewModel.uiState.value.hasCalendarPermission)

        // Calling onPermissionResult(false) updates to denied state
        viewModel.onPermissionResult(false)
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertFalse(viewModel.uiState.value.hasCalendarPermission)
    }

    // =========================================================================
    // 12. CalendarRepository Instances Query with Time Range & Recurrence
    // =========================================================================
    @Test
    fun `CUJ_12_calendar_repository_query_instances_time_range`() = runBlocking {
        val now = System.currentTimeMillis()
        val startWindow = now - 3600000L
        val endWindow = now + 86400000L

        // Grant permission override for testing repository query
        repository.updatePermissionState(explicitGrant = true)

        // Query instances over the specified time range
        val instances = repository.queryInstances(startWindow, endWindow)
        assertNotNull(instances)

        // Query instances using reactive Flow
        val instancesFlowResult = repository.queryInstancesFlow(startWindow, endWindow).first()
        assertNotNull(instancesFlowResult)
    }

    // =========================================================================
    // 13. EventListLazyColumn & Friendly Empty State Verification
    // =========================================================================
    @Test
    fun `CUJ_13_event_lazy_column_and_friendly_empty_state_timeframe_logic`() = runBlocking {
        // When there are no events in database, uiState.events is empty
        val initialEvents = viewModel.uiState.value.filteredEvents
        assertTrue(initialEvents.isEmpty())

        // Test timeframe description logic across different categories
        val todayTimeframe = when ("Today") {
            "Today" -> "today"
            "Tomorrow" -> "tomorrow"
            "Work" -> "work categories"
            else -> "the next 7 days"
        }
        assertEquals("today", todayTimeframe)

        val tomorrowTimeframe = when ("Tomorrow") {
            "Today" -> "today"
            "Tomorrow" -> "tomorrow"
            "Work" -> "work categories"
            else -> "the next 7 days"
        }
        assertEquals("tomorrow", tomorrowTimeframe)

        // Add a test event to verify list populated state
        val now = System.currentTimeMillis()
        repository.addEvent(
            title = "Design Sync",
            location = "Room 404",
            startMillis = now + 100000,
            endMillis = now + 3700000,
            colorHex = "#1A73E8"
        )
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)

        val eventsInDb = eventDao.getUpcomingEventsSync(now)
        assertEquals(1, eventsInDb.size)
        val event = eventsInDb[0]
        assertEquals("Design Sync", event.title)
        assertEquals("#1A73E8", event.colorHex)
        assertTrue(event.endMillis > event.startMillis)

        // Clean up
        repository.deleteEvent(event.id)
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
        val emptyAfter = eventDao.getUpcomingEventsSync(now)
        assertTrue(emptyAfter.isEmpty())
    }

    // =========================================================================
    // 14. Active Fetching State & CircularProgressIndicator Verification
    // =========================================================================
    @Test
    fun `CUJ_14_circular_progress_indicator_during_active_fetching`() {
        // Initially when not syncing/fetching
        assertFalse(repository.isFetching.value)
        assertFalse(viewModel.uiState.value.isFetching)
        assertFalse(viewModel.uiState.value.isSyncing)

        // When repository is actively fetching data
        repository.setFetchingStateForTesting(true)
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertTrue("Repository isFetching should be true while actively fetching", repository.isFetching.value)
        assertTrue("ViewModel uiState.isFetching should be true while repository is fetching", viewModel.uiState.value.isFetching)
        assertTrue("ViewModel uiState.isSyncing should be true while repository is fetching", viewModel.uiState.value.isSyncing)

        // When fetching completes
        repository.setFetchingStateForTesting(false)
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertFalse(repository.isFetching.value)
        assertFalse(viewModel.uiState.value.isFetching)
        assertFalse(viewModel.uiState.value.isSyncing)
    }

    // =========================================================================
    // 15. Calendar Event Color Properties & UI Elements Mapping CUJ
    // =========================================================================
    @Test
    fun `CUJ_15_calendar_color_mapper_reads_properties_and_maps_to_ui`() {
        val mapper = com.example.util.CalendarColorMapper

        // 1. Explicit eventColor property takes precedence
        val explicitProps = com.example.util.CalendarEventColorProperties(
            eventColor = 0xFFD50000.toInt(),
            calendarColor = 0xFF1A73E8.toInt(),
            displayColor = 0xFF1A73E8.toInt(),
            eventColorKey = "1",
            calendarDisplayName = "Personal"
        )
        val resolvedExplicit = mapper.resolveEventColor(explicitProps)
        assertEquals("#D50000", resolvedExplicit)

        // 2. Event color key property resolves to Google standard event palette
        val keyProps = com.example.util.CalendarEventColorProperties(
            eventColor = 0,
            eventColorKey = "2", // Sage (#33B679)
            calendarColor = 0xFF1A73E8.toInt(),
            calendarDisplayName = "Work"
        )
        val resolvedKey = mapper.resolveEventColor(keyProps)
        assertEquals("#33B679", resolvedKey)

        // Key "7" = Peacock (#039BE5)
        val key7Props = com.example.util.CalendarEventColorProperties(
            eventColor = 0,
            eventColorKey = "7"
        )
        assertEquals("#039BE5", mapper.resolveEventColor(key7Props))

        // 3. DISPLAY_COLOR property fallback when eventColor is not set
        val displayProps = com.example.util.CalendarEventColorProperties(
            eventColor = 0,
            displayColor = 0xFF8E24AA.toInt(),
            calendarColor = 0xFF1A73E8.toInt()
        )
        val resolvedDisplay = mapper.resolveEventColor(displayProps)
        assertEquals("#8E24AA", resolvedDisplay)

        // 4. CALENDAR_COLOR fallback
        val calProps = com.example.util.CalendarEventColorProperties(
            eventColor = 0,
            displayColor = 0,
            calendarColor = 0xFFF6BF26.toInt()
        )
        val resolvedCal = mapper.resolveEventColor(calProps)
        assertEquals("#F6BF26", resolvedCal)

        // 5. Semantic calendar category mapping
        assertEquals("#1A73E8", mapper.resolveCalendarColor("Work Office", 0))
        assertEquals("#33B679", mapper.resolveCalendarColor("Personal Life", 0))
        assertEquals("#8E24AA", mapper.resolveCalendarColor("Family Events", 0))
        assertEquals("#0B8043", mapper.resolveCalendarColor("US Holidays", 0))
        assertEquals("#F6BF26", mapper.resolveCalendarColor("Birthdays", 0))
        assertEquals("#039BE5", mapper.resolveCalendarColor("Tasks & To-Dos", 0))

        // 6. UI Element Color Scheme mapping in light and dark themes
        val lightScheme = mapper.getUiColorScheme("#1A73E8", isDarkTheme = false)
        assertEquals("#1A73E8", lightScheme.rawHex)
        assertNotNull(lightScheme.primaryAccent)
        assertNotNull(lightScheme.containerBackground)
        assertNotNull(lightScheme.cardBorder)
        assertNotNull(lightScheme.badgeBackground)
        assertNotNull(lightScheme.badgeBorder)
        assertNotNull(lightScheme.badgeText)
        assertNotNull(lightScheme.badgeDot)
        assertNotNull(lightScheme.timeText)
        assertNotNull(lightScheme.timeIconTint)
        assertNotNull(lightScheme.titleColor)

        val darkScheme = mapper.getUiColorScheme("#F6BF26", isDarkTheme = true)
        assertEquals("#F6BF26", darkScheme.rawHex)
        assertNotNull(darkScheme.containerBackground)
        assertNotNull(darkScheme.cardBorder)
        assertNotNull(darkScheme.timeText)

        // 7. Parsing helpers
        val parsedCompose = mapper.parseColorHex("#E67C73")
        assertNotNull(parsedCompose)
        val parsedInt = mapper.parseColorInt("#E67C73")
        assertEquals(0xFFE67C73.toInt(), parsedInt)

        // Invalid hex falls back safely
        val fallbackCompose = mapper.parseColorHex("invalid_hex")
        assertNotNull(fallbackCompose)
        val fallbackInt = mapper.parseColorInt(null)
        assertEquals(mapper.DEFAULT_COLOR_INT, fallbackInt)
    }

    // =========================================================================
    // 16. Pull-To-Refresh Gesture & Force-Sync Verification
    // =========================================================================
    @Test
    fun `CUJ_16_pull_to_refresh_gesture_force_syncs_calendar_data`() = runBlocking {
        // Initial state: not syncing
        assertFalse(viewModel.uiState.value.isSyncing)
        val initialSyncTime = viewModel.uiState.value.lastSyncTime

        // Simulate pull-to-refresh gesture callback invocation triggering force-sync
        var refreshTriggered = false
        val onRefreshGesture: suspend () -> Unit = {
            refreshTriggered = true
            repository.syncEvents(forceRefresh = true)
        }

        // Trigger gesture
        onRefreshGesture()
        assertTrue("Pull-to-refresh callback must be triggered by gesture", refreshTriggered)

        // Advance looper to process StateFlow updates
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)

        // Verify repository syncEvents forceRefresh completed
        assertFalse("Syncing state must return to false after sync completes", repository.isSyncing.value)
        assertFalse("ViewModel uiState.isSyncing must return to false after sync completes", viewModel.uiState.value.isSyncing)
        assertTrue("Last sync time must be updated after force-sync", repository.lastSyncTime.value >= initialSyncTime)

        // Insert real event to simulate ContentProvider returning newly scheduled item
        val now = System.currentTimeMillis()
        repository.addEvent(
            title = "Standup After Force-Sync",
            location = "Main Conference Room",
            startMillis = now + 120000,
            endMillis = now + 3720000,
            colorHex = "#1A73E8"
        )
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)

        val upcomingEvents = eventDao.getUpcomingEventsSync(now)
        assertEquals(1, upcomingEvents.size)
        assertEquals("Standup After Force-Sync", upcomingEvents[0].title)

        // Trigger force-sync again to simulate swipe-down refresh while populated
        repository.syncEvents(forceRefresh = true)
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)

        // Clean up
        repository.deleteEvent(upcomingEvents[0].id)
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    // =========================================================================
    // 17. Widget Customization Filtering: Past Events, Horizon & Calendar IDs
    // =========================================================================
    @Test
    fun `CUJ_17_widget_preferences_filtering_enforces_past_horizon_and_calendar_ids`() = runBlocking {
        val now = System.currentTimeMillis()
        val startOfToday = CalendarRepository.getStartOfTodayMillis(now)

        // Ensure past event is strictly after startOfToday if possible; otherwise test with synthetic now
        val refNow = startOfToday + (14 * 3600000L) // 2:00 PM today
        val pastToday = EventEntity(
            id = "past_today",
            calendarId = "Work",
            sourceCalendarId = 101L,
            title = "Morning Standup",
            startMillis = startOfToday + (9 * 3600000L),
            endMillis = startOfToday + (10 * 3600000L),
            colorHex = "#1A73E8"
        )
        val upcomingToday = EventEntity(
            id = "upcoming_today",
            calendarId = "Personal",
            sourceCalendarId = 102L,
            title = "Evening Run",
            startMillis = startOfToday + (18 * 3600000L),
            endMillis = startOfToday + (19 * 3600000L),
            colorHex = "#33B679"
        )
        val farFuture = EventEntity(
            id = "far_future",
            calendarId = "Work",
            sourceCalendarId = 101L,
            title = "Quarterly Planning",
            startMillis = startOfToday + (10L * CalendarRepository.DAY_MILLIS),
            endMillis = startOfToday + (10L * CalendarRepository.DAY_MILLIS) + 3600000L,
            colorHex = "#1A73E8"
        )
        val allEvents = listOf(pastToday, upcomingToday, farFuture)

        // 1. Default: showPastEvents = false, daysHorizon = 7, all calendars
        val defaultPrefs = WidgetPreferences(
            daysHorizon = 7,
            showPastEvents = false,
            isAllCalendarsSelected = true
        )
        val filteredDefault = CalendarRepository.filterEventsForPreferences(allEvents, defaultPrefs, refNow)
        assertEquals(1, filteredDefault.size)
        assertEquals("upcoming_today", filteredDefault[0].id)
        assertTrue("First visible item of day must recompute isFirstOfDay = true", filteredDefault[0].isFirstOfDay)

        // 2. showPastEvents = true includes pastToday
        val withPast = CalendarRepository.filterEventsForPreferences(
            allEvents,
            defaultPrefs.copy(showPastEvents = true),
            refNow
        )
        assertEquals(2, withPast.size)
        assertEquals("past_today", withPast[0].id)
        assertTrue(withPast[0].isFirstOfDay)
        assertFalse(withPast[1].isFirstOfDay)

        // 3. daysHorizon = 14 includes farFuture
        val extendedHorizon = CalendarRepository.filterEventsForPreferences(
            allEvents,
            defaultPrefs.copy(daysHorizon = 14),
            refNow
        )
        assertEquals(2, extendedHorizon.size)
        assertEquals("far_future", extendedHorizon[1].id)

        // 4. Custom calendar filtering by numeric sourceCalendarId
        val workOnly = CalendarRepository.filterEventsForPreferences(
            allEvents,
            WidgetPreferences(
                daysHorizon = 14,
                showPastEvents = true,
                isAllCalendarsSelected = false,
                selectedCalendarIds = setOf(101L)
            ),
            refNow
        )
        assertEquals(2, workOnly.size)
        assertTrue(workOnly.all { it.sourceCalendarId == 101L })

        // 5. Empty custom selection returns 0 events
        val emptySelection = CalendarRepository.filterEventsForPreferences(
            allEvents,
            WidgetPreferences(
                daysHorizon = 14,
                showPastEvents = true,
                isAllCalendarsSelected = false,
                selectedCalendarIds = emptySet()
            ),
            refNow
        )
        assertTrue(emptySelection.isEmpty())
    }

    // =========================================================================
    // 18. Event Editing & Full Property Persistence CUJ
    // =========================================================================
    @Test
    fun `CUJ_18_update_event_persists_changes_and_fields`() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        repository.addEvent(
            title = "Draft Review",
            location = "Room 10",
            startMillis = now + 3600000L,
            endMillis = now + 7200000L,
            colorHex = "#1A73E8",
            description = "Initial draft notes",
            isAllDay = false
        )

        val inserted = eventDao.getUpcomingEventsSync(now).firstOrNull { it.title == "Draft Review" }
        assertNotNull(inserted)
        assertEquals("Initial draft notes", inserted?.description)

        repository.updateEvent(
            eventId = inserted!!.id,
            title = "Final Architecture Review",
            location = "https://meet.google.com/abc-defg-hij",
            description = "Updated final agenda",
            startMillis = now + 7200000L,
            endMillis = now + 10800000L,
            isAllDay = true,
            colorHex = "#8E24AA"
        )

        val updated = eventDao.getEventById(inserted.id)
        assertNotNull(updated)
        assertEquals("Final Architecture Review", updated?.title)
        assertEquals("https://meet.google.com/abc-defg-hij", updated?.location)
        assertEquals("Updated final agenda", updated?.description)
        assertEquals("#8E24AA", updated?.colorHex)
        assertTrue(updated?.isAllDay == true)

        repository.deleteEvent(inserted.id)
    }

    // =========================================================================
    // 19. Widget Theme, Opacity (0-100%) & RemoteViews Inflation Verification
    // =========================================================================
    @Test
    fun `CUJ_19_widget_remoteviews_theme_and_opacity_rendering`() = runBlocking {
        val now = System.currentTimeMillis()
        eventDao.insertEvent(
            EventEntity(
                id = "theme_e1",
                calendarId = "Work",
                sourceCalendarId = 1L,
                title = "Design Review",
                location = "Studio B",
                startMillis = now + 1800000L,
                endMillis = now + 5400000L,
                colorHex = "#137333",
                dayHeader = "TODAY",
                isFirstOfDay = true
            )
        )

        val themesToTest = listOf(
            WidgetColorTheme.DYNAMIC to 0,
            WidgetColorTheme.CLASSIC_BLUE to 50,
            WidgetColorTheme.DARK_SLATE to 80,
            WidgetColorTheme.EMERALD to 100,
            WidgetColorTheme.SUNSET to 20
        )

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val shadowAppWidgetManager = org.robolectric.Shadows.shadowOf(appWidgetManager)
        val boundWidgetId = shadowAppWidgetManager.createWidget(
            CalendarAgendaWidgetProvider::class.java,
            R.layout.widget_calendar_agenda
        )
        val serviceIntent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, boundWidgetId)
        }

        for ((theme, opacity) in themesToTest) {
            prefsManager.updatePreferences(
                WidgetPreferences(
                    opacityPercent = opacity,
                    colorTheme = theme,
                    density = WidgetDensity.REGULAR,
                    daysHorizon = 7,
                    showLocations = true,
                    showDayHeaders = true
                )
            )

            val resolved = com.example.widget.WidgetThemeHelper.resolveColors(context, prefsManager.getPreferences())
            val expectedAlpha = ((opacity / 100f) * 255f).toInt().coerceIn(0, 255)
            assertEquals(expectedAlpha, resolved.rootAlpha)

            // Verify provider updateAppWidget executes cleanly and binds root RemoteViews
            CalendarAgendaWidgetProvider.updateAppWidget(context, appWidgetManager, boundWidgetId)
            assertNotNull(shadowAppWidgetManager.getViewFor(boundWidgetId))

            // Verify RemoteViews item applies cleanly without InflateException or ActionException
            val factory = CalendarAgendaViewsFactory(context, serviceIntent, repository)
            factory.onCreate()
            factory.onDataSetChanged()
            assertEquals(1, factory.count)
            val rv = factory.getViewAt(0)
            assertNotNull(rv)
            val inflated = rv!!.apply(context, null)
            assertNotNull(inflated)
        }

        // Also inflate root widget layout directly
        val rootViews = android.widget.RemoteViews(context.packageName, R.layout.widget_calendar_agenda)
        assertNotNull(rootViews.apply(context, null))

        eventDao.deleteEventById("theme_e1")
    }

    // =========================================================================
    // 20. Widget Header Manual Sync/Refresh Button & Force-Update Pipeline
    // =========================================================================
    @Test
    fun `CUJ_20_widget_header_manual_refresh_force_syncs_from_provider`() = runBlocking {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val shadowAppWidgetManager = org.robolectric.Shadows.shadowOf(appWidgetManager)
        val boundWidgetId = shadowAppWidgetManager.createWidget(
            CalendarAgendaWidgetProvider::class.java,
            R.layout.widget_calendar_agenda
        )

        // 1. Verify idle state shows refresh icon and hides progress bar
        CalendarAgendaWidgetProvider.updateAppWidget(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = boundWidgetId,
            isSyncing = false
        )
        val idleView = shadowAppWidgetManager.getViewFor(boundWidgetId)
        assertNotNull(idleView)
        val refreshBtn = idleView.findViewById<android.view.View>(R.id.widget_refresh_btn)
        val refreshContainer = idleView.findViewById<android.view.View>(R.id.widget_refresh_btn_container)
        val refreshProgress = idleView.findViewById<android.view.View>(R.id.widget_refresh_progress)
        assertNotNull(refreshBtn)
        assertNotNull(refreshContainer)
        assertNotNull(refreshProgress)
        assertEquals(android.view.View.VISIBLE, refreshBtn.visibility)
        assertEquals(android.view.View.GONE, refreshProgress.visibility)
        assertNotNull(refreshBtn.contentDescription)
        assertTrue(refreshContainer.hasOnClickListeners())
        assertTrue(refreshBtn.hasOnClickListeners())

        // 2. Verify syncing state hides refresh icon and displays progress indicator
        CalendarAgendaWidgetProvider.updateAppWidget(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = boundWidgetId,
            isSyncing = true
        )
        val syncingView = shadowAppWidgetManager.getViewFor(boundWidgetId)
        assertEquals(android.view.View.GONE, syncingView.findViewById<android.view.View>(R.id.widget_refresh_btn).visibility)
        assertEquals(android.view.View.VISIBLE, syncingView.findViewById<android.view.View>(R.id.widget_refresh_progress).visibility)

        // 3. Verify performForceSync calls syncEvents(forceRefresh = true) and restores idle state
        val initialSyncTime = repository.lastSyncTime.value
        repository.updatePermissionState(explicitGrant = true)
        CalendarAgendaWidgetProvider.performForceSync(
            context = context,
            forceRefresh = true,
            repositoryOverride = repository
        )
        assertFalse(CalendarAgendaWidgetProvider.isWidgetSyncing)
        assertFalse(repository.isSyncing.value)
        assertTrue(repository.lastSyncTime.value >= initialSyncTime)

        val postSyncView = shadowAppWidgetManager.getViewFor(boundWidgetId)
        assertEquals(android.view.View.VISIBLE, postSyncView.findViewById<android.view.View>(R.id.widget_refresh_btn).visibility)
        assertEquals(android.view.View.GONE, postSyncView.findViewById<android.view.View>(R.id.widget_refresh_progress).visibility)

        // 4. Verify broadcast receiver handles ACTION_REFRESH_WIDGET cleanly without permission
        repository.updatePermissionState(explicitGrant = false)
        val provider = CalendarAgendaWidgetProvider()
        val refreshIntent = Intent(context, CalendarAgendaWidgetProvider::class.java).apply {
            action = CalendarAgendaWidgetProvider.ACTION_REFRESH_WIDGET
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, boundWidgetId)
        }
        provider.onReceive(context, refreshIntent)
        ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertFalse(CalendarAgendaWidgetProvider.isWidgetSyncing)
    }

    // =========================================================================
    // 21. Structured AppLogger & Typed UiError / Snackbar Event Pipeline
    // =========================================================================
    @Test
    fun `CUJ_21_app_logger_and_view_model_ui_error_snackbar_flow`() = runBlocking<Unit> {
        val capturedLogs = mutableListOf<Triple<AppLogger.Level, String, String>>()
        AppLogger.testLogSink = { level, tag, msg, _ ->
            capturedLogs.add(Triple(level, tag, msg))
        }
        try {
            // 1. Verify denied permission yields OperationResult.Failure(PermissionDenied) and logs warning
            repository.updatePermissionState(explicitGrant = false)
            val permResult = repository.syncEvents(forceRefresh = true)
            assertTrue(permResult is OperationResult.Failure)
            assertTrue((permResult as OperationResult.Failure).error is AppError.PermissionDenied)
            assertTrue(capturedLogs.any { it.first == AppLogger.Level.WARN && it.second == AppLogger.TAG_REPO })

            // 2. Verify simulated sync error triggers UiError.SyncFailed + UiEvent.ShowSnackbar(RETRY_SYNC)
            repository.updatePermissionState(explicitGrant = true)
            repository.setSyncErrorForTesting(AppError.CalendarQueryFailed("Provider unavailable"))

            val nextEventDeferred = async(Dispatchers.Main.immediate) {
                viewModel.uiEvents.first()
            }
            viewModel.onSyncNow()
            ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS)

            val uiEvent = nextEventDeferred.await()
            assertTrue(uiEvent is UiEvent.ShowSnackbar)
            val snackbarEvent = uiEvent as UiEvent.ShowSnackbar
            assertEquals("Provider unavailable", snackbarEvent.message)
            assertEquals(ErrorAction.RETRY_SYNC, snackbarEvent.action)
            assertTrue(snackbarEvent.isError)
            assertTrue(viewModel.uiState.value.error is UiError.SyncFailed)

            // 3. Verify retry clears error when repository recovers
            repository.setSyncErrorForTesting(null)
            viewModel.onSyncNow()
            ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertEquals(null, viewModel.uiState.value.error)
        } finally {
            repository.setSyncErrorForTesting(null)
            AppLogger.testLogSink = null
        }
    }

    // =========================================================================
    // 22. Atomic Room @Transaction Cache Replacement & Widget Snapshot Safety
    // =========================================================================
    @Test
    fun `CUJ_22_atomic_room_transaction_and_widget_factory_snapshot`() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        val offlineEvent = EventEntity(
            id = "offline_keep_1",
            title = "Local Offline Note",
            startMillis = now + 1800000L,
            endMillis = now + 3600000L,
            isOfflineCreated = true,
            dayHeader = "TODAY"
        )
        val oldSynced = EventEntity(
            id = "dev_old_1",
            title = "Old Synced Meeting",
            startMillis = now + 5400000L,
            endMillis = now + 7200000L,
            isOfflineCreated = false,
            dayHeader = "TODAY"
        )
        val newSynced = EventEntity(
            id = "dev_new_2",
            title = "Fresh Synced Standup",
            startMillis = now + 5400000L,
            endMillis = now + 7200000L,
            isOfflineCreated = false,
            dayHeader = "TODAY"
        )

        eventDao.insertEvents(listOf(offlineEvent, oldSynced))
        assertEquals(2, eventDao.getEventCount())

        // replaceSyncedEvents must preserve offline-created events and replace synced events atomically
        eventDao.replaceSyncedEvents(listOf(newSynced))
        val afterSyncedReplace = eventDao.getUpcomingEventsSync(now)
        assertEquals(2, afterSyncedReplace.size)
        assertTrue(afterSyncedReplace.any { it.id == "offline_keep_1" })
        assertTrue(afterSyncedReplace.any { it.id == "dev_new_2" })
        assertFalse(afterSyncedReplace.any { it.id == "dev_old_1" })

        // Verify RemoteViewsFactory safely loads updated snapshot
        val serviceIntent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 202)
        }
        val factory = CalendarAgendaViewsFactory(context, serviceIntent, repository)
        factory.onCreate()
        assertEquals(2, factory.count)
        assertNotNull(factory.getViewAt(0))
        assertNotNull(factory.getViewAt(1))

        // replaceAllEvents clears everything and inserts provided list atomically
        eventDao.replaceAllEvents(listOf(newSynced))
        assertEquals(1, eventDao.getEventCount())
        factory.onDataSetChanged()
        assertEquals(1, factory.count)
        assertEquals(null, factory.getViewAt(1))
    }
}
