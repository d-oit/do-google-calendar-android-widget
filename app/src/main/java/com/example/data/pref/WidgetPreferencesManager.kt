package com.example.data.pref

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.example.R
import com.example.util.AppLogger
import com.example.util.RuntimeEnvironment
import com.example.widget.CalendarAgendaWidgetProvider
import com.example.widget.GlanceWidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class WidgetColorTheme(
    val id: String,
    val displayName: String,
    val primaryHex: String,
    @StringRes val labelResId: Int
) {
    DYNAMIC("dynamic", "Material You", "#1A73E8", R.string.widget_theme_material_you),
    CLASSIC_BLUE("classic_blue", "Google Blue", "#1A73E8", R.string.widget_theme_classic_blue),
    DARK_SLATE("dark_slate", "Midnight Dark", "#1E1E1E", R.string.widget_theme_dark_slate),
    EMERALD("emerald", "Emerald Green", "#137333", R.string.widget_theme_emerald),
    SUNSET("sunset", "Sunset Amber", "#E65100", R.string.widget_theme_warm_sunset);

    companion object {
        fun fromId(id: String): WidgetColorTheme =
            values().firstOrNull { it.id == id } ?: DYNAMIC
    }
}

enum class WidgetDensity(
    val id: String,
    val displayName: String,
    val titleSp: Float,
    val timeSp: Float,
    @StringRes val labelResId: Int
) {
    COMPACT("compact", "Compact", 12f, 10f, R.string.widget_density_compact),
    REGULAR("regular", "Regular", 14f, 12f, R.string.widget_density_cozy),
    LARGE("large", "Large", 16f, 13f, R.string.widget_density_spacious);

    companion object {
        fun fromId(id: String): WidgetDensity =
            values().firstOrNull { it.id == id } ?: REGULAR
    }
}

enum class WidgetTitleLineLimit(
    val id: String,
    val maxLines: Int,
    @StringRes val labelResId: Int
) {
    FULL("full", Int.MAX_VALUE, R.string.widget_title_lines_full),
    TWO_LINES("two_lines", 2, R.string.widget_title_lines_two),
    ONE_LINE("one_line", 1, R.string.widget_title_lines_one);

    companion object {
        fun fromId(id: String): WidgetTitleLineLimit =
            values().firstOrNull { it.id == id } ?: FULL
    }
}

enum class WidgetTimeDisplayMode(
    val id: String,
    @StringRes val labelResId: Int
) {
    FULL_RANGE("full_range", R.string.widget_time_display_range),
    START_ONLY("start_only", R.string.widget_time_display_start_only),
    HIDE("hide", R.string.widget_time_display_hide);

    companion object {
        fun fromId(id: String): WidgetTimeDisplayMode =
            values().firstOrNull { it.id == id } ?: FULL_RANGE
    }
}

data class WidgetPreferences(
    val opacityPercent: Int = 85,
    val colorTheme: WidgetColorTheme = WidgetColorTheme.DYNAMIC,
    val density: WidgetDensity = WidgetDensity.REGULAR,
    val daysHorizon: Int = 7,
    val selectedCalendarIds: Set<Long> = emptySet(),
    val isAllCalendarsSelected: Boolean = true,
    val showLocations: Boolean = true,
    val showDayHeaders: Boolean = true,
    val showPastEvents: Boolean = false,
    val titleLineLimit: WidgetTitleLineLimit = WidgetTitleLineLimit.FULL,
    val timeDisplayMode: WidgetTimeDisplayMode = WidgetTimeDisplayMode.FULL_RANGE,
    val showCalendarName: Boolean = true
)

class WidgetPreferencesManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val widgetUpdateExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        AppLogger.w(AppLogger.TAG_PREFS, "Uncaught exception updating widgets", throwable)
    }
    private val widgetUpdateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + widgetUpdateExceptionHandler)

    @Volatile
    private var pendingWidgetNotifyJob: Job? = null

    private val _preferencesFlow = MutableStateFlow(loadPreferences())
    val preferencesFlow = _preferencesFlow.asStateFlow()

    private val _lastSuccessfulSyncMillis = MutableStateFlow(loadLastSuccessfulSyncMillis())
    val lastSuccessfulSyncMillis = _lastSuccessfulSyncMillis.asStateFlow()

    fun getPreferences(): WidgetPreferences = _preferencesFlow.value

    fun getLastSuccessfulSyncMillis(): Long = _lastSuccessfulSyncMillis.value

    fun setLastSuccessfulSyncMillis(timestampMillis: Long) {
        _lastSuccessfulSyncMillis.value = timestampMillis
        prefs.edit().putLong(KEY_LAST_SUCCESSFUL_SYNC_MILLIS, timestampMillis).apply()
        AppLogger.d(AppLogger.TAG_PREFS, "Recorded last successful sync timestamp=$timestampMillis")
    }

    private fun loadLastSuccessfulSyncMillis(): Long =
        prefs.getLong(KEY_LAST_SUCCESSFUL_SYNC_MILLIS, 0L)

    fun updatePreferences(newPrefs: WidgetPreferences) {
        _preferencesFlow.value = newPrefs
        prefs.edit().apply {
            putInt(KEY_OPACITY, newPrefs.opacityPercent)
            putString(KEY_COLOR_THEME, newPrefs.colorTheme.id)
            putString(KEY_DENSITY, newPrefs.density.id)
            putInt(KEY_DAYS_HORIZON, newPrefs.daysHorizon)
            putBoolean(KEY_ALL_CALENDARS, newPrefs.isAllCalendarsSelected)
            putStringSet(KEY_SELECTED_CAL_IDS, newPrefs.selectedCalendarIds.map { it.toString() }.toSet())
            putBoolean(KEY_SHOW_LOCATIONS, newPrefs.showLocations)
            putBoolean(KEY_SHOW_DAY_HEADERS, newPrefs.showDayHeaders)
            putBoolean(KEY_SHOW_PAST_EVENTS, newPrefs.showPastEvents)
            putString(KEY_TITLE_LINE_LIMIT, newPrefs.titleLineLimit.id)
            putString(KEY_TIME_DISPLAY_MODE, newPrefs.timeDisplayMode.id)
            putBoolean(KEY_SHOW_CALENDAR_NAME, newPrefs.showCalendarName)
            apply()
        }
        AppLogger.d(
            AppLogger.TAG_PREFS,
            "Updated widget prefs: opacity=${newPrefs.opacityPercent}%, theme=${newPrefs.colorTheme.id}, horizon=${newPrefs.daysHorizon}d, titleLines=${newPrefs.titleLineLimit.id}, timeMode=${newPrefs.timeDisplayMode.id}"
        )
        notifyWidgetsChanged(debounced = true)
    }

    private fun loadPreferences(): WidgetPreferences {
        val opacity = prefs.getInt(KEY_OPACITY, 85)
        val themeId = prefs.getString(KEY_COLOR_THEME, WidgetColorTheme.DYNAMIC.id) ?: WidgetColorTheme.DYNAMIC.id
        val densityId = prefs.getString(KEY_DENSITY, WidgetDensity.REGULAR.id) ?: WidgetDensity.REGULAR.id
        val days = prefs.getInt(KEY_DAYS_HORIZON, 7)
        val allCalendars = prefs.getBoolean(KEY_ALL_CALENDARS, true)
        val calIdStrings = prefs.getStringSet(KEY_SELECTED_CAL_IDS, emptySet()) ?: emptySet()
        val calIds = calIdStrings.mapNotNull { it.toLongOrNull() }.toSet()
        val showLoc = prefs.getBoolean(KEY_SHOW_LOCATIONS, true)
        val showHeaders = prefs.getBoolean(KEY_SHOW_DAY_HEADERS, true)
        val showPast = prefs.getBoolean(KEY_SHOW_PAST_EVENTS, false)
        val titleLineLimitId = prefs.getString(KEY_TITLE_LINE_LIMIT, WidgetTitleLineLimit.FULL.id) ?: WidgetTitleLineLimit.FULL.id
        val timeDisplayModeId = prefs.getString(KEY_TIME_DISPLAY_MODE, WidgetTimeDisplayMode.FULL_RANGE.id) ?: WidgetTimeDisplayMode.FULL_RANGE.id
        val showCalName = prefs.getBoolean(KEY_SHOW_CALENDAR_NAME, true)

        return WidgetPreferences(
            opacityPercent = opacity,
            colorTheme = WidgetColorTheme.fromId(themeId),
            density = WidgetDensity.fromId(densityId),
            daysHorizon = days,
            selectedCalendarIds = calIds,
            isAllCalendarsSelected = allCalendars,
            showLocations = showLoc,
            showDayHeaders = showHeaders,
            showPastEvents = showPast,
            titleLineLimit = WidgetTitleLineLimit.fromId(titleLineLimitId),
            timeDisplayMode = WidgetTimeDisplayMode.fromId(timeDisplayModeId),
            showCalendarName = showCalName
        )
    }

    /**
     * Invalidates Classic RemoteViews widgets and Jetpack Glance widgets.
     * When [debounced] is true (e.g., slider drags), rapid updates are coalesced over 150ms
     * on real devices to prevent broadcast storms.
     */
    fun notifyWidgetsChanged(debounced: Boolean = false) {
        if (debounced && !RuntimeEnvironment.isRunningInTestEnvironment) {
            pendingWidgetNotifyJob?.cancel()
            pendingWidgetNotifyJob = widgetUpdateScope.launch {
                delay(WIDGET_DEBOUNCE_MILLIS)
                dispatchClassicWidgetBroadcast()
                dispatchGlanceWidgetUpdate()
            }
            return
        }

        pendingWidgetNotifyJob?.cancel()
        dispatchClassicWidgetBroadcast()
        if (!RuntimeEnvironment.isRunningInTestEnvironment) {
            widgetUpdateScope.launch {
                dispatchGlanceWidgetUpdate()
            }
        }
    }

    private fun dispatchClassicWidgetBroadcast() {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, CalendarAgendaWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_agenda_list)
                val updateIntent = Intent(context, CalendarAgendaWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(updateIntent)
                AppLogger.d(AppLogger.TAG_PREFS, "Broadcast widget update for ${appWidgetIds.size} widget(s)")
            }
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_PREFS, "Failed to notify home screen widgets of changes", e)
        }
    }

    private suspend fun dispatchGlanceWidgetUpdate() {
        try {
            GlanceWidgetUpdater.updateAllGlanceWidgets(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_PREFS, "Failed refreshing Glance widgets on change", e)
        }
    }

    companion object {
        private const val WIDGET_DEBOUNCE_MILLIS = 150L
        private const val PREFS_NAME = "google_calendar_widget_prefs"
        private const val KEY_OPACITY = "pref_widget_opacity"
        private const val KEY_COLOR_THEME = "pref_color_theme"
        private const val KEY_DENSITY = "pref_density"
        private const val KEY_DAYS_HORIZON = "pref_days_horizon"
        private const val KEY_ALL_CALENDARS = "pref_all_calendars"
        private const val KEY_SELECTED_CAL_IDS = "pref_selected_cal_ids"
        private const val KEY_SHOW_LOCATIONS = "pref_show_locations"
        private const val KEY_SHOW_DAY_HEADERS = "pref_show_day_headers"
        private const val KEY_SHOW_PAST_EVENTS = "pref_show_past_events"
        private const val KEY_TITLE_LINE_LIMIT = "pref_title_line_limit"
        private const val KEY_TIME_DISPLAY_MODE = "pref_time_display_mode"
        private const val KEY_SHOW_CALENDAR_NAME = "pref_show_calendar_name"
        private const val KEY_LAST_SUCCESSFUL_SYNC_MILLIS = "pref_last_successful_sync_millis"

        @Volatile
        private var instance: WidgetPreferencesManager? = null

        fun getInstance(context: Context): WidgetPreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: WidgetPreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
