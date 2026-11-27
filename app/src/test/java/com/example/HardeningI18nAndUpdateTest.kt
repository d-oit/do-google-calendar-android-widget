package com.example

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.EventEntity
import com.example.data.pref.WidgetPreferences
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.repository.CalendarRepository
import com.example.data.update.AppUpdateChecker
import com.example.data.update.SemVer
import com.example.data.update.UpdateCheckResult
import com.example.ui.AgendaFilterCategory
import com.example.ui.CalendarViewModel
import com.example.ui.UpdateCheckState
import com.example.ui.components.AboutReleaseBottomSheet
import com.example.ui.theme.AgendaWidgetTheme
import com.example.util.AppLogger
import com.example.util.CalendarDateFormatter
import com.example.util.LogSanitizer
import com.example.widget.CalendarAgendaWidgetProvider
import com.example.widget.CalendarGlanceWidgetContent
import com.example.widget.WidgetThemeHelper
import kotlinx.coroutines.Dispatchers
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
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HardeningI18nAndUpdateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalLocale = Locale.getDefault()
        AppLogger.clearRecentLogs()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        AppLogger.clearRecentLogs()
    }

    @Test
    fun `widget_click_redirection_blocks_untrusted_extras_and_verifies_against_room_cache`() {
        // 1. Unknown event ID falls back strictly to MainActivity (ignores any spoofed EXTRA_EVENT_LINK)
        val spoofedFallback = CalendarAgendaWidgetProvider.resolveVerifiedItemClickIntent(
            context = context,
            eventId = "spoofed_nonexistent_id",
            eventTitle = "Fake"
        )
        assertEquals(MainActivity::class.java.name, spoofedFallback.component?.className)
        assertNull(spoofedFallback.data)

        // 2. URI allowlist strictly rejects javascript:, file:, intent:, and arbitrary content:// providers
        assertFalse(CalendarAgendaWidgetProvider.isVerifiedSafeEventUri("javascript:alert(1)"))
        assertFalse(CalendarAgendaWidgetProvider.isVerifiedSafeEventUri("file:///etc/passwd"))
        assertFalse(CalendarAgendaWidgetProvider.isVerifiedSafeEventUri("content://com.other.provider/secrets"))
        assertFalse(CalendarAgendaWidgetProvider.isVerifiedSafeEventUri("http://insecure.example.com/meet"))
        assertTrue(CalendarAgendaWidgetProvider.isVerifiedSafeEventUri("content://com.android.calendar/events/42"))
        assertTrue(CalendarAgendaWidgetProvider.isVerifiedSafeEventUri("https://calendar.google.com/event?eid=123"))

        // 3. Verified event stored in AppDatabase resolves to its verified content:// calendar URI
        val db = AppDatabase.getInstance(context)
        val now = System.currentTimeMillis()
        val verifiedEvent = EventEntity(
            id = "verified_evt_99",
            title = "Verified Security Sync",
            startMillis = now + 3_600_000L,
            endMillis = now + 7_200_000L,
            colorHex = "#1A73E8",
            htmlLink = "content://com.android.calendar/events/99"
        )
        runBlocking {
            db.eventDao().insertEvent(verifiedEvent)
        }
        try {
            val resolved = runBlocking(Dispatchers.IO) {
                CalendarAgendaWidgetProvider.resolveVerifiedItemClickIntent(
                    context = context,
                    eventId = "verified_evt_99",
                    eventTitle = "Verified Security Sync"
                )
            }
            assertEquals(Intent.ACTION_VIEW, resolved.action)
            assertEquals("content://com.android.calendar/events/99", resolved.data.toString())
        } finally {
            runBlocking {
                db.eventDao().deleteEventById("verified_evt_99")
            }
        }
    }

    @Test
    fun `log_sanitizer_redacts_pii_and_app_logger_caps_ring_buffer`() {
        val raw = "Syncing alice.smith@example.com from https://meet.google.com/abc-defg-hij?auth=secret123 Bearer eyJhbGciOiJIUzI1NiJ9"
        val sanitized = LogSanitizer.sanitize(raw)

        assertFalse(sanitized.contains("alice.smith@example.com"))
        assertTrue(sanitized.contains("a***@example.com"))
        assertFalse(sanitized.contains("secret123"))
        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertTrue(sanitized.contains("https://meet.google.com/[redacted]"))
        assertTrue(sanitized.contains("Bearer [redacted]"))

        AppLogger.clearRecentLogs()
        repeat(150) { idx ->
            AppLogger.i(AppLogger.TAG_SYNC, "Batch entry #$idx for user$idx@gmail.com")
        }
        val entries = AppLogger.getRecentLogs()
        assertEquals(120, entries.size)
        assertTrue(entries.all { !it.message.contains("user") || it.message.contains("u***@gmail.com") })

        AppLogger.clearRecentLogs()
        assertTrue(AppLogger.getRecentLogs().isEmpty())
    }

    @Test
    fun `i18n_es_fr_de_translations_load_cleanly_with_valid_format_specifiers`() {
        val locales = listOf(Locale("en"), Locale("es"), Locale("fr"), Locale("de"))
        val simpleResIds = listOf(
            R.string.app_name,
            R.string.tab_agenda,
            R.string.tab_widget_setup,
            R.string.filter_all,
            R.string.filter_today,
            R.string.filter_tomorrow,
            R.string.filter_all_day,
            R.string.agenda_empty_title,
            R.string.about_sheet_title,
            R.string.pin_chooser_title
        )

        locales.forEach { locale ->
            val config = Configuration(context.resources.configuration).apply {
                setLocale(locale)
            }
            val localizedContext = context.createConfigurationContext(config)
            simpleResIds.forEach { resId ->
                val text = localizedContext.getString(resId)
                assertTrue("Expected non-blank translation for ${locale.language}", text.isNotBlank())
            }
            // Validate formatted strings compile and interpolate arguments cleanly
            assertTrue(localizedContext.getString(R.string.widget_customizer_horizon_days, 7).contains("7"))
            assertTrue(localizedContext.getString(R.string.about_updates_available, "0.2.0", "0.1.0").contains("0.2.0"))
            assertTrue(localizedContext.getString(R.string.diagnostics_hidden_events_banner, 3, 7).contains("3"))
        }

        // Verify distinct localized values across es/fr/de for Today header
        val esCtx = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale("es")) })
        val frCtx = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale("fr")) })
        val deCtx = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale("de")) })
        assertEquals("Hoy", esCtx.getString(R.string.day_header_today))
        assertEquals("Aujourd'hui", frCtx.getString(R.string.day_header_today))
        assertEquals("Heute", deCtx.getString(R.string.day_header_today))
    }

    @Test
    fun `calendar_date_formatter_handles_12h_24h_and_midnight_rollover`() {
        val afternoon = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 26, 15, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        Locale.setDefault(Locale.US)
        val time24 = CalendarDateFormatter.formatTime(afternoon, is24Hour = true)
        val time12 = CalendarDateFormatter.formatTime(afternoon, is24Hour = false)
        assertTrue("Expected 24-hour time to contain 15:30, got $time24", time24.contains("15:30"))
        assertTrue("Expected 12-hour time to contain 3:30, got $time12", time12.contains("3:30"))

        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefsManager = WidgetPreferencesManager.getInstance(context)
            val repo = CalendarRepository(context, db.eventDao(), prefsManager)
            val viewModel = CalendarViewModel(repo, prefsManager)

            val todayStart = CalendarDateFormatter.getStartOfTodayMillis()
            val tomorrowStart = todayStart + CalendarDateFormatter.DAY_MILLIS
            assertFalse(viewModel.refreshDayBoundaryIfNeeded(nowMillis = todayStart + 1_000L))
            assertTrue(viewModel.refreshDayBoundaryIfNeeded(nowMillis = tomorrowStart + 1_000L))

            // Verify locale-independent AgendaFilterCategory mapping
            assertEquals(AgendaFilterCategory.Today, AgendaFilterCategory.fromKey("Today"))
            assertEquals(AgendaFilterCategory.AllDay, AgendaFilterCategory.fromKey("All Day"))
            assertEquals(
                AgendaFilterCategory.CalendarSource("Personal"),
                AgendaFilterCategory.fromKey("Personal")
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun `app_update_checker_validates_semver_https_allowlist_and_states`() = runBlocking {
        assertTrue(SemVer.parse("0.2.0")!! > SemVer.parse("0.1.0")!!)
        assertTrue(SemVer.parse("1.0.0")!! > SemVer.parse("1.0.0-rc.1")!!)
        assertNull(SemVer.parse("not-a-version"))

        // 1. Blank or placeholder slug returns NotConfigured without hitting network
        val notConfigured = AppUpdateChecker(
            currentVersionName = "0.1.0",
            repositorySlug = "",
            fetcher = { error("Should not fetch when unconfigured") }
        ).checkForUpdates()
        assertEquals(UpdateCheckResult.NotConfigured, notConfigured)

        // 2. Newer release returns UpdateAvailable and sanitizes untrusted html_url to trusted GitHub URL
        val updateAvailable = AppUpdateChecker(
            currentVersionName = "0.1.0",
            repositorySlug = "octocat/agenda-widget",
            fetcher = {
                """
                {
                  "tag_name": "v0.2.0",
                  "html_url": "https://evil.example.com/phish",
                  "body": "Added Spanish, French, and German translations."
                }
                """.trimIndent()
            }
        ).checkForUpdates()
        assertTrue(updateAvailable is UpdateCheckResult.UpdateAvailable)
        val avail = updateAvailable as UpdateCheckResult.UpdateAvailable
        assertEquals("0.2.0", avail.latestVersion)
        assertEquals("https://github.com/octocat/agenda-widget/releases/tag/v0.2.0", avail.releaseUrl)

        // 3. Matching version returns UpToDate
        val upToDate = AppUpdateChecker(
            currentVersionName = "0.1.0",
            repositorySlug = "octocat/agenda-widget",
            fetcher = { """{"tag_name":"v0.1.0","html_url":"https://github.com/octocat/agenda-widget/releases/tag/v0.1.0"}""" }
        ).checkForUpdates()
        assertTrue(upToDate is UpdateCheckResult.UpToDate)

        // 4. Network error surfaces clean Error result
        val errorResult = AppUpdateChecker(
            currentVersionName = "0.1.0",
            repositorySlug = "octocat/agenda-widget",
            fetcher = { throw IllegalStateException("GitHub API responded with HTTP 403") }
        ).checkForUpdates()
        assertTrue(errorResult is UpdateCheckResult.Error)
    }

    @Test
    fun `glance_widget_renders_syncing_state_and_about_sheet_triggers_update_check`() = runBlocking {
        val prefs = WidgetPreferences()
        val theme = WidgetThemeHelper.resolveColors(context, prefs)
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(DpSize(300.dp, 240.dp))
            provideComposable {
                CalendarGlanceWidgetContent(
                    events = emptyList(),
                    prefs = prefs,
                    theme = theme,
                    hasPermission = true,
                    syncRevision = 2,
                    lastSyncMillis = 0L,
                    isSyncing = true
                )
            }
            onNode(hasText(context.getString(R.string.widget_sync_in_progress))).assertExists()
        }

        var checkedUpdates = false
        var openedReleaseUrl: String? = null
        composeTestRule.setContent {
            AgendaWidgetTheme(dynamicColor = false) {
                AboutReleaseBottomSheet(
                    updateCheckState = UpdateCheckState.UpdateAvailable(
                        currentVersion = "0.1.0",
                        latestVersion = "0.2.0",
                        releaseNotes = "Performance and i18n release",
                        releaseUrl = "https://github.com/octocat/agenda-widget/releases/tag/v0.2.0"
                    ),
                    onDismiss = {},
                    onCheckForUpdates = { checkedUpdates = true },
                    onOpenReleaseUrl = { openedReleaseUrl = it },
                    onOpenDiagnostics = {},
                    onOpenAppLanguageSettings = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("about_version_value").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_check_updates_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("about_open_release_button").performScrollTo().performClick()
        assertTrue(checkedUpdates)
        assertNotNull(openedReleaseUrl)
    }
}
