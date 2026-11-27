package com.example

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasText
import androidx.glance.testing.unit.hasTestTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.EventEntity
import com.example.data.pref.WidgetColorTheme
import com.example.data.pref.WidgetDensity
import com.example.data.pref.WidgetPreferences
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.pref.WidgetTimeDisplayMode
import com.example.data.pref.WidgetTitleLineLimit
import com.example.data.repository.CalendarAccountInfo
import com.example.data.repository.CalendarRepository
import com.example.ui.CalendarViewModel
import com.example.ui.ErrorAction
import com.example.ui.UiEvent
import com.example.ui.components.WidgetCustomizerControls
import com.example.ui.components.WidgetPreviewCard
import com.example.ui.theme.AgendaWidgetTheme
import com.example.util.CalendarDateFormatter
import com.example.widget.CalendarAgendaViewsFactory
import com.example.widget.CalendarAgendaWidgetProvider
import com.example.widget.CalendarGlanceWidgetContent
import com.example.widget.WidgetThemeHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Material3OverhaulUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var prefsManager: WidgetPreferencesManager
    private lateinit var repository: CalendarRepository
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsManager = WidgetPreferencesManager.getInstance(context)
        prefsManager.updatePreferences(WidgetPreferences())
        repository = CalendarRepository(context, database.eventDao(), prefsManager)
    }

    @After
    fun tearDown() {
        prefsManager.updatePreferences(WidgetPreferences())
        Locale.setDefault(originalLocale)
        database.close()
    }

    @Test
    fun `timing_cue_formatter_returns_now_imminent_and_null_accurately`() {
        val now = 1_750_000_000_000L

        val liveCue = CalendarDateFormatter.formatEventTimingCue(
            context = context,
            startMillis = now - 10 * 60_000L,
            endMillis = now + 20 * 60_000L,
            isAllDay = false,
            nowMillis = now
        )
        assertNotNull(liveCue)
        assertTrue(liveCue!!.isLive)
        assertEquals(context.getString(R.string.widget_badge_now), liveCue.label)

        val imminentCue = CalendarDateFormatter.formatEventTimingCue(
            context = context,
            startMillis = now + 25 * 60_000L,
            endMillis = now + 85 * 60_000L,
            isAllDay = false,
            nowMillis = now
        )
        assertNotNull(imminentCue)
        assertFalse(imminentCue!!.isLive)
        assertEquals(context.getString(R.string.widget_badge_in_minutes, 25), imminentCue.label)

        val distantCue = CalendarDateFormatter.formatEventTimingCue(
            context = context,
            startMillis = now + 120 * 60_000L,
            endMillis = now + 180 * 60_000L,
            isAllDay = false,
            nowMillis = now
        )
        assertNull(distantCue)

        val allDayCue = CalendarDateFormatter.formatEventTimingCue(
            context = context,
            startMillis = now - 60_000L,
            endMillis = now + 86_400_000L,
            isAllDay = true,
            nowMillis = now
        )
        assertNull(allDayCue)

        val endedCue = CalendarDateFormatter.formatEventTimingCue(
            context = context,
            startMillis = now - 120 * 60_000L,
            endMillis = now - 30 * 60_000L,
            isAllDay = false,
            nowMillis = now
        )
        assertNull(endedCue)
    }

    @Test
    fun `viewmodel_reset_widget_preferences_emits_undo_event_and_restores_previous`() = runBlocking {
        val viewModel = CalendarViewModel(repository, prefsManager)
        val customized = WidgetPreferences(
            opacityPercent = 40,
            colorTheme = WidgetColorTheme.SUNSET,
            density = WidgetDensity.COMPACT,
            titleLineLimit = WidgetTitleLineLimit.ONE_LINE,
            timeDisplayMode = WidgetTimeDisplayMode.START_ONLY,
            showLocations = false
        )
        viewModel.updateWidgetPreferences(customized)
        assertEquals(customized, prefsManager.getPreferences())

        val snackbarDeferred = async { viewModel.uiEvents.first() }
        viewModel.resetWidgetPreferencesToDefault(context.getString(R.string.msg_widget_reset_defaults))

        assertEquals(WidgetPreferences(), prefsManager.getPreferences())
        val event = snackbarDeferred.await() as UiEvent.ShowSnackbar
        assertEquals(ErrorAction.UNDO_WIDGET_RESET, event.action)
        assertEquals(context.getString(R.string.msg_widget_reset_defaults), event.message)

        viewModel.undoWidgetPreferencesReset()
        assertEquals(customized, prefsManager.getPreferences())
    }

    @Test
    fun `widget_preview_supports_wallpaper_presets_smart_empty_state_and_live_cues`() {
        var addClicked = 0
        var openCalendarClicked = 0
        var pinClicked = 0

        composeTestRule.setContent {
            AgendaWidgetTheme(darkTheme = true, dynamicColor = false) {
                WidgetPreviewCard(
                    events = emptyList(),
                    preferences = WidgetPreferences(opacityPercent = 0),
                    isSyncing = false,
                    onRefresh = {},
                    onPinWidget = { pinClicked++ },
                    onAddEvent = { addClicked++ },
                    onOpenCalendar = { openCalendarClicked++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("wallpaper_preset_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wallpaper_preset_light").performClick()
        composeTestRule.onNodeWithTag("wallpaper_preset_vibrant").performClick()
        composeTestRule.onNodeWithTag("wallpaper_preset_dark").performClick()

        composeTestRule.onNodeWithTag("widget_preview_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("widget_preview_empty_illustration").assertIsDisplayed()
        composeTestRule.onNodeWithTag("widget_preview_empty_add_action").performClick()
        composeTestRule.onNodeWithTag("widget_preview_empty_calendar_action").performClick()
        composeTestRule.onNodeWithTag("pin_widget_button").performClick()

        assertEquals(1, addClicked)
        assertEquals(1, openCalendarClicked)
        assertEquals(1, pinClicked)
    }

    @Test
    fun `widget_preview_renders_live_timing_cue_only_for_ongoing_and_imminent_events`() {
        val now = System.currentTimeMillis()
        val liveEvent = EventEntity(
            id = "live_1",
            calendarId = "Work",
            sourceCalendarId = 1L,
            title = "Design Critique",
            location = "Studio A",
            startMillis = now - 5 * 60_000L,
            endMillis = now + 25 * 60_000L,
            colorHex = "#1A73E8",
            dayHeader = "TODAY",
            isFirstOfDay = true
        )
        val distantEvent = EventEntity(
            id = "later_2",
            calendarId = "Work",
            sourceCalendarId = 1L,
            title = "Evening Retrospective",
            location = null,
            startMillis = now + 4 * 3600_000L,
            endMillis = now + 5 * 3600_000L,
            colorHex = "#0F9D58",
            dayHeader = "TODAY",
            isFirstOfDay = false
        )

        composeTestRule.setContent {
            AgendaWidgetTheme(darkTheme = true, dynamicColor = false) {
                WidgetPreviewCard(
                    events = listOf(liveEvent, distantEvent),
                    preferences = WidgetPreferences(),
                    isSyncing = false,
                    onRefresh = {},
                    onPinWidget = {},
                    onAddEvent = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("widget_preview_event_cue_live_1")
            .assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.widget_badge_now))
        composeTestRule.onNodeWithTag("widget_preview_event_cue_later_2")
            .assertDoesNotExist()
    }

    @Test
    fun `widget_customizer_groups_controls_into_m3_cards_and_updates_preferences`() {
        var latestPrefs = WidgetPreferences()
        val calendars = listOf(
            CalendarAccountInfo(
                id = 1L,
                accountName = "alex@example.com",
                displayName = "Work",
                accountType = "com.google",
                isPrimary = true,
                colorHex = "#1A73E8"
            ),
            CalendarAccountInfo(
                id = 2L,
                accountName = "alex@example.com",
                displayName = "Personal",
                accountType = "com.google",
                isPrimary = false,
                colorHex = "#0F9D58"
            )
        )

        composeTestRule.setContent {
            AgendaWidgetTheme(darkTheme = true, dynamicColor = false) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    WidgetCustomizerControls(
                        preferences = latestPrefs,
                        availableCalendars = calendars,
                        onUpdatePreferences = { latestPrefs = it }
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("customizer_card_appearance").assertIsDisplayed()
        composeTestRule.onNodeWithTag("opacity_slider").assertIsDisplayed()
        composeTestRule.onNodeWithTag("theme_chip_emerald").performScrollTo().performClick()
        assertEquals(WidgetColorTheme.EMERALD, latestPrefs.colorTheme)

        composeTestRule.onNodeWithTag("customizer_card_typography").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("density_chip_compact").performScrollTo().performClick()
        assertEquals(WidgetDensity.COMPACT, latestPrefs.density)

        composeTestRule.onNodeWithTag("title_lines_chip_one_line").performScrollTo().performClick()
        assertEquals(WidgetTitleLineLimit.ONE_LINE, latestPrefs.titleLineLimit)

        composeTestRule.onNodeWithTag("time_display_chip_start_only").performScrollTo().performClick()
        assertEquals(WidgetTimeDisplayMode.START_ONLY, latestPrefs.timeDisplayMode)

        composeTestRule.onNodeWithTag("customizer_card_schedule").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("customizer_card_display").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `glance_widget_renders_smart_empty_state_and_responsive_timing_cue`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 260.dp))

        val prefs = WidgetPreferences()
        val theme = WidgetThemeHelper.resolveColors(context, prefs)

        provideComposable {
            CalendarGlanceWidgetContent(
                events = emptyList(),
                prefs = prefs,
                theme = theme,
                hasPermission = true,
                syncRevision = 1,
                lastSyncMillis = System.currentTimeMillis()
            )
        }

        onNode(hasTestTag("glance_empty_illustration")).assertExists()
        onNode(hasTestTag("glance_empty_text"))
            .assertHasText(context.getString(R.string.widget_no_events))
        onNode(hasTestTag("glance_empty_add_action")).assertExists()

        val now = System.currentTimeMillis()
        val liveEvent = EventEntity(
            id = "glance_live_1",
            calendarId = "Work",
            sourceCalendarId = 1L,
            title = "Live Standup",
            location = "Room 1",
            startMillis = now - 5 * 60_000L,
            endMillis = now + 20 * 60_000L,
            colorHex = "#1A73E8",
            dayHeader = "TODAY",
            isFirstOfDay = true
        )

        provideComposable {
            CalendarGlanceWidgetContent(
                events = listOf(liveEvent),
                prefs = prefs,
                theme = theme,
                hasPermission = true,
                syncRevision = 2,
                lastSyncMillis = now
            )
        }

        onNode(hasTestTag("glance_event_cue_glance_live_1")).assertExists()

        // Narrow widget omits trailing timing cue to keep title readable
        setAppWidgetSize(DpSize(160.dp, 260.dp))
        provideComposable {
            CalendarGlanceWidgetContent(
                events = listOf(liveEvent),
                prefs = prefs,
                theme = theme,
                hasPermission = true,
                syncRevision = 3,
                lastSyncMillis = now
            )
        }
        onNode(hasTestTag("glance_event_cue_glance_live_1")).assertDoesNotExist()
    }

    @Test
    fun `classic_remoteviews_renders_smart_empty_state_and_timing_cue`() = runBlocking {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val shadowAppWidgetManager = Shadows.shadowOf(appWidgetManager)
        val widgetId = shadowAppWidgetManager.createWidget(
            CalendarAgendaWidgetProvider::class.java,
            R.layout.widget_calendar_agenda
        )

        CalendarAgendaWidgetProvider.updateAppWidget(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = widgetId,
            isSyncing = false
        )

        val rootView = shadowAppWidgetManager.getViewFor(widgetId)
        val emptyText = rootView.findViewById<TextView>(R.id.widget_empty_text)
        val emptyAddButton = rootView.findViewById<View>(R.id.widget_empty_add_container)
        assertNotNull(emptyText)
        assertNotNull(emptyAddButton)
        assertEquals(context.getString(R.string.widget_no_events), emptyText.text.toString())

        val now = System.currentTimeMillis()
        val liveEvent = EventEntity(
            id = "rv_live",
            calendarId = "Work",
            sourceCalendarId = 1L,
            title = "Architecture Sync",
            location = "Conf B",
            startMillis = now - 5 * 60_000L,
            endMillis = now + 25 * 60_000L,
            colorHex = "#1A73E8",
            dayHeader = "TODAY",
            isFirstOfDay = true
        )
        val distantEvent = EventEntity(
            id = "rv_later",
            calendarId = "Work",
            sourceCalendarId = 1L,
            title = "Next Week Review",
            location = null,
            startMillis = now + 5 * 3600_000L,
            endMillis = now + 6 * 3600_000L,
            colorHex = "#0F9D58",
            dayHeader = "TODAY",
            isFirstOfDay = false
        )

        database.eventDao().replaceAllEvents(listOf(liveEvent, distantEvent))
        val factory = CalendarAgendaViewsFactory(
            context = context,
            intent = Intent(),
            repository = repository
        )
        factory.onCreate()
        factory.onDataSetChanged()
        assertEquals(2, factory.count)

        val parent = FrameLayout(context)
        val liveRemoteViews = factory.getViewAt(0)
        assertNotNull(liveRemoteViews)
        val liveItemView = liveRemoteViews!!.apply(context, parent)
        val liveCueContainer = liveItemView.findViewById<View>(R.id.widget_item_cue_container)
        val liveCueText = liveItemView.findViewById<TextView>(R.id.widget_item_cue_text)
        assertEquals(View.VISIBLE, liveCueContainer.visibility)
        assertEquals(context.getString(R.string.widget_badge_now), liveCueText.text.toString())

        val distantRemoteViews = factory.getViewAt(1)
        assertNotNull(distantRemoteViews)
        val distantItemView = distantRemoteViews!!.apply(context, parent)
        val distantCueContainer = distantItemView.findViewById<View>(R.id.widget_item_cue_container)
        assertEquals(View.GONE, distantCueContainer.visibility)
    }

    @Test
    fun `all_new_m3_overhaul_strings_are_localized_across_en_de_es_fr`() {
        val resourceIds = listOf(
            R.string.widget_no_events,
            R.string.topbar_more_options_desc,
            R.string.menu_reset_widget_defaults,
            R.string.menu_sync_diagnostics,
            R.string.menu_about_updates,
            R.string.msg_widget_reset_defaults,
            R.string.action_undo,
            R.string.widget_wallpaper_label,
            R.string.widget_wallpaper_dark,
            R.string.widget_wallpaper_light,
            R.string.widget_wallpaper_vibrant,
            R.string.widget_customizer_section_appearance,
            R.string.widget_customizer_section_typography,
            R.string.widget_customizer_section_schedule,
            R.string.widget_customizer_section_display,
            R.string.widget_customizer_pin_compact,
            R.string.widget_empty_action_add,
            R.string.widget_empty_action_calendar,
            R.string.widget_badge_now
        )

        val locales = listOf(Locale.ENGLISH, Locale.GERMAN, Locale("es"), Locale.FRENCH)
        val enValues = mutableMapOf<Int, String>()

        locales.forEach { locale ->
            val config = Configuration(context.resources.configuration).apply {
                setLocale(locale)
            }
            val localizedContext = context.createConfigurationContext(config)
            resourceIds.forEach { resId ->
                val value = localizedContext.getString(resId)
                assertTrue("Empty string for $resId in $locale", value.isNotBlank())
                if (locale == Locale.ENGLISH) {
                    enValues[resId] = value
                }
            }
            val formattedMinutes = localizedContext.getString(R.string.widget_badge_in_minutes, 15)
            assertTrue("Minutes badge should include 15 in $locale", formattedMinutes.contains("15"))
        }

        // Verify non-English translations differ from English for multi-word copy
        listOf(Locale.GERMAN, Locale("es"), Locale.FRENCH).forEach { locale ->
            val config = Configuration(context.resources.configuration).apply {
                setLocale(locale)
            }
            val localizedContext = context.createConfigurationContext(config)
            assertFalse(
                "widget_no_events should be localized in $locale",
                localizedContext.getString(R.string.widget_no_events) == enValues[R.string.widget_no_events]
            )
            assertFalse(
                "menu_reset_widget_defaults should be localized in $locale",
                localizedContext.getString(R.string.menu_reset_widget_defaults) == enValues[R.string.menu_reset_widget_defaults]
            )
        }
    }
}
