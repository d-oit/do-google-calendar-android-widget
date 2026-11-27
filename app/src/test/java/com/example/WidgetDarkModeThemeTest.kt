package com.example

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.material3.ColorProviders
import androidx.glance.testing.unit.assertHasText
import androidx.glance.testing.unit.hasTestTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.EventEntity
import com.example.data.pref.WidgetColorTheme
import com.example.data.pref.WidgetPreferences
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.repository.CalendarRepository
import com.example.widget.CalendarAgendaViewsFactory
import com.example.widget.CalendarGlanceWidgetContent
import com.example.widget.WidgetThemeHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetDarkModeThemeTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var prefsManager: WidgetPreferencesManager
    private lateinit var repository: CalendarRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
        database.close()
    }

    private fun createNightModeContext(nightMode: Boolean): Context {
        val config = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (nightMode) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return context.createConfigurationContext(config)
    }

    @Test
    fun `all_widget_themes_adapt_to_system_dark_mode_using_material3_color_schemes`() {
        val lightCtx = createNightModeContext(nightMode = false)
        val darkCtx = createNightModeContext(nightMode = true)

        assertFalse(WidgetThemeHelper.isSystemDarkMode(lightCtx))
        assertTrue(WidgetThemeHelper.isSystemDarkMode(darkCtx))

        val adaptiveThemes = listOf(
            WidgetColorTheme.DYNAMIC,
            WidgetColorTheme.CLASSIC_BLUE,
            WidgetColorTheme.EMERALD,
            WidgetColorTheme.SUNSET
        )

        adaptiveThemes.forEach { theme ->
            val prefs = WidgetPreferences(colorTheme = theme, opacityPercent = 85)
            val resolvedLight = WidgetThemeHelper.resolveColors(lightCtx, prefs)
            val resolvedDark = WidgetThemeHelper.resolveColors(darkCtx, prefs)

            assertFalse("Expected light theme for $theme in day mode", resolvedLight.isDark)
            assertTrue("Expected dark theme for $theme in night mode", resolvedDark.isDark)

            // Dark mode surface must have low luminance and distinct from light mode surface
            assertTrue(
                "Expected dark root surface for $theme",
                ColorUtils.calculateLuminance(resolvedDark.rootBgColor) < 0.15
            )
            assertTrue(
                "Expected dark item surface for $theme",
                ColorUtils.calculateLuminance(resolvedDark.itemBgColor) < 0.20
            )
            assertTrue(
                "Expected bright title color in dark mode for $theme",
                ColorUtils.calculateLuminance(resolvedDark.titleColor) > 0.70
            )
        }

        // DARK_SLATE always remains dark regardless of system light/dark setting
        val darkSlatePrefs = WidgetPreferences(colorTheme = WidgetColorTheme.DARK_SLATE)
        assertTrue(WidgetThemeHelper.resolveColors(lightCtx, darkSlatePrefs).isDark)
        assertTrue(WidgetThemeHelper.resolveColors(darkCtx, darkSlatePrefs).isDark)
    }

    @Test
    fun `event_titles_subtitles_and_event_times_meet_wcag_contrast_on_dark_backgrounds`() {
        val darkCtx = createNightModeContext(nightMode = true)
        val opacities = listOf(0, 25, 55, 85, 100)
        val darkEventHexes = listOf("#616161", "#3F51B5", "#0B8043", "#8E24AA", "#1A73E8", "#D50000")

        WidgetColorTheme.values().forEach { theme ->
            opacities.forEach { opacity ->
                val resolved = WidgetThemeHelper.resolveColors(
                    darkCtx,
                    WidgetPreferences(colorTheme = theme, opacityPercent = opacity)
                )
                assertTrue(resolved.isDark)

                val titleContrast = ColorUtils.calculateContrast(resolved.titleColor, resolved.itemBgColor)
                assertTrue(
                    "Title contrast too low ($titleContrast) for $theme at $opacity%",
                    titleContrast >= 7.0
                )

                val subtitleContrast = ColorUtils.calculateContrast(resolved.subtitleColor, resolved.itemBgColor)
                assertTrue(
                    "Subtitle contrast too low ($subtitleContrast) for $theme at $opacity%",
                    subtitleContrast >= 4.5
                )

                darkEventHexes.forEach { hex ->
                    val readableTimeColor = WidgetThemeHelper.resolveEventTimeColor(hex, resolved)
                    val timeContrast = ColorUtils.calculateContrast(readableTimeColor, resolved.itemBgColor)
                    assertTrue(
                        "Time color contrast too low ($timeContrast) for hex=$hex on $theme",
                        timeContrast >= 4.5
                    )
                }
            }
        }
    }

    @Test
    fun `classic_remote_views_factory_renders_readable_titles_and_times_in_dark_mode`() = runBlocking {
        val darkCtx = createNightModeContext(nightMode = true)
        val now = System.currentTimeMillis()
        val event = EventEntity(
            id = "dark_mode_evt",
            calendarId = "Work",
            sourceCalendarId = 1L,
            title = "Architecture Review — Material 3 Dark Mode Compatibility",
            location = "Room 5A",
            startMillis = now + 3_600_000L,
            endMillis = now + 7_200_000L,
            colorHex = "#3F51B5", // Dark Blueberry that would be unreadable unadjusted
            dayHeader = "TODAY",
            isFirstOfDay = true
        )
        database.eventDao().insertEvent(event)

        prefsManager.updatePreferences(
            WidgetPreferences(colorTheme = WidgetColorTheme.CLASSIC_BLUE, opacityPercent = 90)
        )
        val expectedTheme = WidgetThemeHelper.resolveColors(darkCtx, prefsManager.getPreferences())
        assertTrue(expectedTheme.isDark)

        val factory = CalendarAgendaViewsFactory(darkCtx, Intent(), repository)
        factory.onCreate()
        assertEquals(1, factory.count)

        val rv = factory.getViewAt(0)!!
        val inflated = rv.apply(darkCtx, FrameLayout(darkCtx))
        val titleTv = inflated.findViewById<TextView>(R.id.widget_item_title)
        val timeTv = inflated.findViewById<TextView>(R.id.widget_item_time)

        assertEquals(event.title, titleTv.text.toString())
        assertEquals(expectedTheme.titleColor, titleTv.currentTextColor)

        val actualTitleContrast = ColorUtils.calculateContrast(titleTv.currentTextColor, expectedTheme.itemBgColor)
        assertTrue("Expected high-contrast title in classic widget, got $actualTitleContrast", actualTitleContrast >= 7.0)

        val actualTimeContrast = ColorUtils.calculateContrast(timeTv.currentTextColor, expectedTheme.itemBgColor)
        assertTrue("Expected readable time color in classic widget, got $actualTimeContrast", actualTimeContrast >= 4.5)
    }

    @Test
    fun `glance_widget_renders_cleanly_with_material3_color_providers_in_dark_mode`() = runGlanceAppWidgetUnitTest {
        val darkCtx = createNightModeContext(nightMode = true)
        setContext(darkCtx)
        setAppWidgetSize(DpSize(300.dp, 260.dp))

        val now = System.currentTimeMillis()
        val event = EventEntity(
            id = "glance_dark_evt",
            calendarId = "Personal",
            sourceCalendarId = 2L,
            title = "Evening Basketball Qualifiers Watch Party",
            startMillis = now + 3_600_000L,
            endMillis = now + 7_200_000L,
            colorHex = "#616161",
            dayHeader = "TODAY",
            isFirstOfDay = true
        )

        val prefs = WidgetPreferences(colorTheme = WidgetColorTheme.EMERALD, opacityPercent = 85)
        val schemes = WidgetThemeHelper.resolveMaterial3ColorSchemes(darkCtx, prefs.colorTheme)
        val darkTheme = WidgetThemeHelper.resolveColors(darkCtx, prefs)
        assertTrue(darkTheme.isDark)

        provideComposable {
            GlanceTheme(
                colors = ColorProviders(
                    light = schemes.light,
                    dark = schemes.dark
                )
            ) {
                CalendarGlanceWidgetContent(
                    events = listOf(event),
                    prefs = prefs,
                    theme = darkTheme,
                    hasPermission = true
                )
            }
        }

        onNode(hasTestTag("glance_event_title_${event.id}")).assertHasText(event.title)
        onNode(hasTestTag("glance_event_time_${event.id}")).assertExists()
    }
}
