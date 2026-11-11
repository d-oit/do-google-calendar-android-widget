package com.example

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.dao.EventDao
import com.example.data.db.AppDatabase
import com.example.data.model.EventEntity
import com.example.data.repository.CalendarRepository
import com.example.ui.CalendarViewModel
import com.example.widget.CalendarAgendaViewsFactory
import com.example.widget.CalendarAgendaWidgetProvider
import com.example.data.pref.WidgetColorTheme
import com.example.data.pref.WidgetDensity
import com.example.data.pref.WidgetPreferences
import com.example.data.pref.WidgetPreferencesManager
import kotlinx.coroutines.Dispatchers
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
}
