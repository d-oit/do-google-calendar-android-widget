package com.example.data.pref

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.example.R
import com.example.util.AppLogger
import com.example.widget.CalendarAgendaWidgetProvider
import com.example.widget.GlanceWidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class WidgetColorTheme(val id: String, val displayName: String, val primaryHex: String) {
    DYNAMIC("dynamic", "Material You", "#1A73E8"),
    CLASSIC_BLUE("classic_blue", "Google Blue", "#1A73E8"),
    DARK_SLATE("dark_slate", "Midnight Dark", "#1E1E1E"),
    EMERALD("emerald", "Emerald Green", "#137333"),
    SUNSET("sunset", "Sunset Amber", "#E65100");

    companion object {
        fun fromId(id: String): WidgetColorTheme =
            values().firstOrNull { it.id == id } ?: DYNAMIC
    }
}

enum class WidgetDensity(val id: String, val displayName: String, val titleSp: Float, val timeSp: Float) {
    COMPACT("compact", "Compact", 12f, 10f),
    REGULAR("regular", "Regular", 14f, 12f),
    LARGE("large", "Large", 16f, 13f);

    companion object {
        fun fromId(id: String): WidgetDensity =
            values().firstOrNull { it.id == id } ?: REGULAR
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
    val showPastEvents: Boolean = false
)

class WidgetPreferencesManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val widgetUpdateExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        AppLogger.w(AppLogger.TAG_PREFS, "Uncaught exception updating Glance widgets", throwable)
    }
    private val widgetUpdateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + widgetUpdateExceptionHandler)

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
            apply()
        }
        AppLogger.d(
            AppLogger.TAG_PREFS,
            "Updated widget prefs: opacity=${newPrefs.opacityPercent}%, theme=${newPrefs.colorTheme.id}, horizon=${newPrefs.daysHorizon}d"
        )
        notifyWidgetsChanged()
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

        return WidgetPreferences(
            opacityPercent = opacity,
            colorTheme = WidgetColorTheme.fromId(themeId),
            density = WidgetDensity.fromId(densityId),
            daysHorizon = days,
            selectedCalendarIds = calIds,
            isAllCalendarsSelected = allCalendars,
            showLocations = showLoc,
            showDayHeaders = showHeaders,
            showPastEvents = showPast
        )
    }

    fun notifyWidgetsChanged() {
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

        if (!isRunningInTestEnvironment()) {
            widgetUpdateScope.launch {
                try {
                    GlanceWidgetUpdater.updateAllGlanceWidgets(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(AppLogger.TAG_PREFS, "Failed refreshing Glance widgets on change", e)
                }
            }
        }
    }

    private fun isRunningInTestEnvironment(): Boolean {
        return try {
            Class.forName("org.robolectric.Robolectric") != null
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    companion object {
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
