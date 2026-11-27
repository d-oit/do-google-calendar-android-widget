package com.example

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasText
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.EventEntity
import com.example.data.pref.WidgetPreferences
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.pref.WidgetTimeDisplayMode
import com.example.data.pref.WidgetTitleLineLimit
import com.example.data.repository.CalendarRepository
import com.example.ui.components.EventItemCard
import com.example.ui.components.WidgetCustomizerControls
import com.example.ui.components.WidgetPreviewCard
import com.example.ui.theme.AgendaWidgetTheme
import com.example.util.CalendarDateFormatter
import com.example.widget.CalendarAgendaViewsFactory
import com.example.widget.CalendarGlanceWidgetContent
import com.example.widget.WidgetThemeHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventTitleVisibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var prefsManager: WidgetPreferencesManager
    private lateinit var repository: CalendarRepository
    private lateinit var originalLocale: Locale

    private val longTitle =
        "FIBA World Cup 2027 European Qualifiers — Germany vs. Spain Live Broadcast"

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

    private fun sampleLongTitleEvent(now: Long = System.currentTimeMillis()): EventEntity {
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val end = start + 2L * 60L * 60L * 1000L + 30L * 60L * 1000L
        return EventEntity(
            id = "fiba_long_evt",
            calendarId = "Frei verfügbare Basketball Livestreams",
            sourceCalendarId = 10L,
            title = longTitle,
            location = "Mercedes-Benz Arena",
            startMillis = start,
            endMillis = end,
            colorHex = "#E67C73",
            dayHeader = "TOMORROW",
            isFirstOfDay = true
        )
    }

    @Test
    fun `preferences_default_to_full_title_and_persist_visibility_options`() {
        val defaults = WidgetPreferences()
        assertEquals(WidgetTitleLineLimit.FULL, defaults.titleLineLimit)
        assertEquals(Int.MAX_VALUE, defaults.titleLineLimit.maxLines)
        assertEquals(WidgetTimeDisplayMode.FULL_RANGE, defaults.timeDisplayMode)
        assertTrue(defaults.showCalendarName)

        val custom = WidgetPreferences(
            titleLineLimit = WidgetTitleLineLimit.TWO_LINES,
            timeDisplayMode = WidgetTimeDisplayMode.START_ONLY,
            showCalendarName = false
        )
        prefsManager.updatePreferences(custom)

        val reloaded = WidgetPreferencesManager(context).getPreferences()
        assertEquals(WidgetTitleLineLimit.TWO_LINES, reloaded.titleLineLimit)
        assertEquals(WidgetTimeDisplayMode.START_ONLY, reloaded.timeDisplayMode)
        assertFalse(reloaded.showCalendarName)
    }

    @Test
    fun `date_formatter_honors_full_range_start_only_and_hide_time_modes`() {
        val event = sampleLongTitleEvent()

        val fullRange = CalendarDateFormatter.formatEventTime(
            startMillis = event.startMillis,
            endMillis = event.endMillis,
            isAllDay = false,
            context = context,
            timeDisplayMode = WidgetTimeDisplayMode.FULL_RANGE
        )
        val startOnly = CalendarDateFormatter.formatEventTime(
            startMillis = event.startMillis,
            endMillis = event.endMillis,
            isAllDay = false,
            context = context,
            timeDisplayMode = WidgetTimeDisplayMode.START_ONLY
        )
        val hidden = CalendarDateFormatter.formatEventTime(
            startMillis = event.startMillis,
            endMillis = event.endMillis,
            isAllDay = false,
            context = context,
            timeDisplayMode = WidgetTimeDisplayMode.HIDE
        )

        assertTrue("Expected full range to contain dash separator, got $fullRange", fullRange.contains("–"))
        assertFalse("Expected start-only to omit range separator, got $startOnly", startOnly.contains("–"))
        assertTrue("Expected start-only to be non-empty", startOnly.isNotBlank())
        assertEquals("", hidden)
    }

    @Test
    fun `classic_remote_views_factory_applies_title_max_lines_and_visibility_toggles`() = runBlocking {
        val event = sampleLongTitleEvent()
        database.eventDao().insertEvent(event)

        // 1. Default FULL title, FULL_RANGE time, calendar name visible
        prefsManager.updatePreferences(WidgetPreferences())
        val factory = CalendarAgendaViewsFactory(context, Intent(), repository)
        factory.onCreate()
        assertEquals(1, factory.count)

        val fullRv = factory.getViewAt(0)!!
        val fullInflated = fullRv.apply(context, FrameLayout(context))
        val fullTitleTv = fullInflated.findViewById<TextView>(R.id.widget_item_title)
        val fullTimeTv = fullInflated.findViewById<TextView>(R.id.widget_item_time)
        val fullCalTv = fullInflated.findViewById<TextView>(R.id.widget_item_calendar)

        assertEquals(longTitle, fullTitleTv.text.toString())
        assertEquals(Int.MAX_VALUE, fullTitleTv.maxLines)
        assertEquals(View.VISIBLE, fullTimeTv.visibility)
        assertTrue(fullTimeTv.text.toString().contains("–"))
        assertEquals(View.VISIBLE, fullCalTv.visibility)
        assertEquals(event.calendarId, fullCalTv.text.toString())

        // 2. 2-line limit, HIDE time, hide calendar name
        prefsManager.updatePreferences(
            WidgetPreferences(
                titleLineLimit = WidgetTitleLineLimit.TWO_LINES,
                timeDisplayMode = WidgetTimeDisplayMode.HIDE,
                showCalendarName = false
            )
        )
        val hiddenRv = factory.getViewAt(0)!!
        val hiddenInflated = hiddenRv.apply(context, FrameLayout(context))
        val limitedTitleTv = hiddenInflated.findViewById<TextView>(R.id.widget_item_title)
        val hiddenTimeTv = hiddenInflated.findViewById<TextView>(R.id.widget_item_time)
        val hiddenCalTv = hiddenInflated.findViewById<TextView>(R.id.widget_item_calendar)

        assertEquals(2, limitedTitleTv.maxLines)
        assertEquals(View.GONE, hiddenTimeTv.visibility)
        assertEquals(View.GONE, hiddenCalTv.visibility)
    }

    @Test
    fun `glance_widget_renders_full_title_and_respects_time_and_calendar_visibility`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 260.dp))
        val event = sampleLongTitleEvent()

        val defaultPrefs = WidgetPreferences()
        val theme = WidgetThemeHelper.resolveColors(context, defaultPrefs)
        provideComposable {
            CalendarGlanceWidgetContent(
                events = listOf(event),
                prefs = defaultPrefs,
                theme = theme,
                hasPermission = true
            )
        }
        onNode(hasTestTag("glance_event_title_${event.id}")).assertHasText(longTitle)
        onNode(hasTestTag("glance_event_time_${event.id}")).assertExists()
        onNode(hasTestTag("glance_event_calendar_${event.id}")).assertHasText(event.calendarId)

        val minimalPrefs = WidgetPreferences(
            titleLineLimit = WidgetTitleLineLimit.FULL,
            timeDisplayMode = WidgetTimeDisplayMode.HIDE,
            showCalendarName = false,
            showLocations = false
        )
        provideComposable {
            CalendarGlanceWidgetContent(
                events = listOf(event),
                prefs = minimalPrefs,
                theme = WidgetThemeHelper.resolveColors(context, minimalPrefs),
                hasPermission = true
            )
        }
        onNode(hasText(longTitle)).assertExists()
        onNode(hasTestTag("glance_event_time_${event.id}")).assertDoesNotExist()
        onNode(hasTestTag("glance_event_calendar_${event.id}")).assertDoesNotExist()
        onNode(hasTestTag("glance_event_location_${event.id}")).assertDoesNotExist()
    }

    @Test
    fun `preview_agenda_card_and_customizer_controls_update_visibility_options`() {
        val event = sampleLongTitleEvent()
        var currentPrefs = WidgetPreferences()

        composeTestRule.setContent {
            AgendaWidgetTheme(dynamicColor = false) {
                WidgetCustomizerControls(
                    preferences = currentPrefs,
                    availableCalendars = emptyList(),
                    onUpdatePreferences = { currentPrefs = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("title_lines_chip_two_lines").performClick()
        assertEquals(WidgetTitleLineLimit.TWO_LINES, currentPrefs.titleLineLimit)

        composeTestRule.onNodeWithTag("time_display_chip_start_only").performClick()
        assertEquals(WidgetTimeDisplayMode.START_ONLY, currentPrefs.timeDisplayMode)

        composeTestRule.onNodeWithTag("show_calendar_name_switch").performClick()
        assertFalse(currentPrefs.showCalendarName)
    }

    @Test
    fun `preview_and_agenda_card_display_full_title_and_toggle_metadata`() {
        val event = sampleLongTitleEvent()
        val hiddenMetaPrefs = WidgetPreferences(
            titleLineLimit = WidgetTitleLineLimit.FULL,
            timeDisplayMode = WidgetTimeDisplayMode.HIDE,
            showCalendarName = false,
            showLocations = false
        )

        composeTestRule.setContent {
            AgendaWidgetTheme(dynamicColor = false) {
                androidx.compose.foundation.layout.Column {
                    WidgetPreviewCard(
                        events = listOf(event),
                        preferences = hiddenMetaPrefs,
                        isSyncing = false,
                        onRefresh = {},
                        onPinWidget = {},
                        onAddEvent = {}
                    )
                    EventItemCard(
                        event = event,
                        onClick = {},
                        onDelete = {},
                        widgetPreferences = hiddenMetaPrefs
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("widget_preview_event_title_${event.id}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("widget_preview_event_time_${event.id}").assertDoesNotExist()
        composeTestRule.onNodeWithTag("widget_preview_event_calendar_${event.id}").assertDoesNotExist()

        composeTestRule.onNodeWithTag("event_title_${event.id}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("event_time_${event.id}").assertDoesNotExist()
        composeTestRule.onNodeWithTag("event_calendar_badge_${event.id}").assertDoesNotExist()
    }

    @Test
    fun `new_visibility_strings_are_localized_in_all_supported_locales`() {
        val locales = listOf(Locale("en"), Locale("de"), Locale("es"), Locale("fr"))
        val resIds = listOf(
            R.string.widget_customizer_title_lines_label,
            R.string.widget_title_lines_full,
            R.string.widget_title_lines_two,
            R.string.widget_title_lines_one,
            R.string.widget_customizer_time_display_label,
            R.string.widget_time_display_range,
            R.string.widget_time_display_start_only,
            R.string.widget_time_display_hide,
            R.string.widget_customizer_show_calendar_name
        )

        locales.forEach { locale ->
            val localizedCtx = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { setLocale(locale) }
            )
            resIds.forEach { resId ->
                assertTrue(
                    "Missing translation for ${locale.language} resId=$resId",
                    localizedCtx.getString(resId).isNotBlank()
                )
            }
        }
    }
}
