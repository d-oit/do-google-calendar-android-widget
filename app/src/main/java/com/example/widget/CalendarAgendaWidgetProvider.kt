package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.example.CalendarApp
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.repository.CalendarRepository
import com.example.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CalendarAgendaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        AppLogger.d(AppLogger.TAG_WIDGET, "onUpdate for ${appWidgetIds.size} widget(s)")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        AppLogger.d(AppLogger.TAG_WIDGET, "onAppWidgetOptionsChanged for widget=$appWidgetId")
        updateAppWidget(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        AppLogger.d(AppLogger.TAG_WIDGET, "onReceive action=$action")

        when (action) {
            ACTION_REFRESH_WIDGET -> {
                val repo = resolveRepository(context)
                if (!repo.checkCalendarPermission()) {
                    AppLogger.w(AppLogger.TAG_WIDGET, "Widget manual refresh aborted: READ_CALENDAR missing")
                    Toast.makeText(
                        context,
                        context.getString(R.string.widget_permission_required_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshWidgetLayouts(context, isSyncing = false)
                    return
                }
                isWidgetSyncing = true
                refreshWidgetLayouts(context, isSyncing = true)
                Toast.makeText(
                    context,
                    context.getString(R.string.widget_refreshing_toast),
                    Toast.LENGTH_SHORT
                ).show()
                syncAndRefreshAllWidgets(context, forceRefresh = true, repositoryOverride = repo)
            }
            Intent.ACTION_PROVIDER_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> {
                syncAndRefreshAllWidgets(context, forceRefresh = false)
            }
            ACTION_ITEM_CLICK -> handleItemClick(context, intent)
        }
    }

    private fun handleItemClick(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE)
        val eventLink = intent.getStringExtra(EXTRA_EVENT_LINK)
        val startMillis = intent.getLongExtra(EXTRA_START_MILLIS, 0L)

        val isSafeWebUrl = !eventLink.isNullOrBlank() &&
            (eventLink.startsWith("https://", ignoreCase = true) || eventLink.startsWith("http://", ignoreCase = true))
        val isCalendarContentUri = !eventLink.isNullOrBlank() &&
            eventLink.startsWith("content://com.android.calendar/", ignoreCase = true)

        val openIntent = when {
            isSafeWebUrl || isCalendarContentUri -> {
                Intent(Intent.ACTION_VIEW, Uri.parse(eventLink)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            startMillis > 0 -> {
                val builder = Uri.parse("content://com.android.calendar/time").buildUpon()
                ContentUris.appendId(builder, startMillis)
                Intent(Intent.ACTION_VIEW).apply {
                    data = builder.build()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            else -> {
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_EVENT_ID, eventId)
                    putExtra(EXTRA_EVENT_TITLE, eventTitle)
                }
            }
        }

        try {
            context.startActivity(openIntent)
        } catch (e: ActivityNotFoundException) {
            AppLogger.w(AppLogger.TAG_WIDGET, "No external handler for widget item click, falling back to MainActivity", e)
            openFallbackActivity(context, eventId, eventTitle)
        } catch (e: SecurityException) {
            AppLogger.w(AppLogger.TAG_WIDGET, "SecurityException opening widget item URI, falling back to MainActivity", e)
            openFallbackActivity(context, eventId, eventTitle)
        }
    }

    private fun openFallbackActivity(context: Context, eventId: String?, eventTitle: String?) {
        try {
            val fallbackIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_EVENT_ID, eventId)
                putExtra(EXTRA_EVENT_TITLE, eventTitle)
            }
            context.startActivity(fallbackIntent)
        } catch (ex: Exception) {
            AppLogger.e(AppLogger.TAG_WIDGET, "Failed to launch fallback MainActivity from widget click", ex)
        }
    }

    private fun syncAndRefreshAllWidgets(
        context: Context,
        forceRefresh: Boolean,
        repositoryOverride: CalendarRepository? = null
    ) {
        val pendingResult = try {
            goAsync()
        } catch (e: IllegalStateException) {
            AppLogger.d(AppLogger.TAG_WIDGET, "goAsync unavailable in current context: ${e.message}")
            null
        }
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            AppLogger.e(AppLogger.TAG_WIDGET, "Unhandled coroutine error during widget sync", throwable)
            isWidgetSyncing = false
            refreshWidgetLayouts(context, isSyncing = false)
            pendingResult?.finish()
        }
        CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
            try {
                performForceSync(
                    context = context,
                    forceRefresh = forceRefresh,
                    repositoryOverride = repositoryOverride
                )
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.widget.ACTION_REFRESH_WIDGET"
        const val ACTION_ITEM_CLICK = "com.example.widget.ACTION_ITEM_CLICK"
        const val EXTRA_EVENT_ID = "EXTRA_EVENT_ID"
        const val EXTRA_EVENT_TITLE = "EXTRA_EVENT_TITLE"
        const val EXTRA_EVENT_LINK = "EXTRA_EVENT_LINK"
        const val EXTRA_START_MILLIS = "EXTRA_START_MILLIS"

        @Volatile
        internal var isWidgetSyncing: Boolean = false

        private val narrowDateFormat = ThreadLocal.withInitial {
            SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        }
        private val wideDateFormat = ThreadLocal.withInitial {
            SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
        }

        internal fun resolveRepository(context: Context): CalendarRepository {
            val appRepo = runCatching { CalendarApp.instance.repository }.getOrNull()
            if (appRepo != null) return appRepo
            return CalendarRepository(
                context.applicationContext,
                AppDatabase.getInstance(context.applicationContext).eventDao(),
                WidgetPreferencesManager.getInstance(context.applicationContext)
            )
        }

        internal suspend fun performForceSync(
            context: Context,
            forceRefresh: Boolean = true,
            repositoryOverride: CalendarRepository? = null
        ) {
            isWidgetSyncing = true
            try {
                val repo = repositoryOverride ?: resolveRepository(context)
                repo.syncEvents(forceRefresh = forceRefresh)
                    .onSuccess { count ->
                        AppLogger.d(AppLogger.TAG_WIDGET, "Widget force sync succeeded with $count event(s)")
                    }
                    .onFailure { error ->
                        AppLogger.w(AppLogger.TAG_WIDGET, "Widget force sync failed: ${error.message}")
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(AppLogger.TAG_WIDGET, "Exception in performForceSync", e)
            } finally {
                isWidgetSyncing = false
                refreshWidgetLayouts(context, isSyncing = false)
            }
        }

        internal fun refreshWidgetLayouts(context: Context, isSyncing: Boolean) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, CalendarAgendaWidgetProvider::class.java)
                )
                for (id in ids) {
                    updateAppWidget(context, manager, id, isSyncing = isSyncing)
                }
            } catch (e: Exception) {
                AppLogger.w(AppLogger.TAG_WIDGET, "Failed to refresh widget layouts", e)
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            options: Bundle? = null,
            isSyncing: Boolean? = null
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_calendar_agenda)
            val prefsManager = WidgetPreferencesManager.getInstance(context)
            val prefs = prefsManager.getPreferences()
            val theme = WidgetThemeHelper.resolveColors(context, prefs)
            val syncing = isSyncing ?: isWidgetSyncing
            val lastSyncMillis = prefsManager.getLastSuccessfulSyncMillis()

            // Apply rounded background fill & border stroke while preserving 24dp corners
            views.setInt(R.id.widget_bg_fill, "setColorFilter", theme.rootBgColor)
            views.setInt(R.id.widget_bg_fill, "setImageAlpha", theme.rootAlpha)
            views.setInt(R.id.widget_bg_stroke, "setColorFilter", theme.rootBorderColor)
            views.setInt(R.id.widget_bg_stroke, "setImageAlpha", theme.rootBorderAlpha)

            // Adapt header padding/icon visibility for compact widget widths
            val resolvedOptions = options ?: try {
                appWidgetManager.getAppWidgetOptions(appWidgetId)
            } catch (e: Exception) {
                AppLogger.d(AppLogger.TAG_WIDGET, "Could not read widget options for id=$appWidgetId: ${e.message}")
                null
            }
            val minWidthDp = resolvedOptions?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) ?: 250
            val isNarrow = minWidthDp in 1..180
            views.setViewVisibility(R.id.widget_app_icon, if (isNarrow) View.GONE else View.VISIBLE)

            val dateFormatter = if (isNarrow) narrowDateFormat.get() else wideDateFormat.get()
            val dateTitle = dateFormatter?.format(Date()) ?: ""
            views.setTextViewText(R.id.widget_date_title, dateTitle)
            views.setTextViewTextSize(R.id.widget_date_title, TypedValue.COMPLEX_UNIT_SP, prefs.density.titleSp)

            // Bind last successful CalendarContract sync indicator in widget header
            val syncLabel = if (syncing) {
                context.getString(R.string.widget_sync_in_progress)
            } else {
                CalendarRepository.formatLastSyncIndicator(
                    context = context,
                    lastSyncMillis = lastSyncMillis,
                    compact = isNarrow
                )
            }
            val syncDotColor = when {
                syncing -> theme.accentColor
                lastSyncMillis > 0L -> if (theme.isDark) 0xFF81C995.toInt() else 0xFF137333.toInt()
                else -> theme.subtitleColor
            }
            val syncTextSizeSp = (prefs.density.timeSp - 2f).coerceIn(9f, 11f)
            views.setTextViewText(R.id.widget_sync_time_text, syncLabel)
            views.setTextViewTextSize(R.id.widget_sync_time_text, TypedValue.COMPLEX_UNIT_SP, syncTextSizeSp)
            views.setTextColor(R.id.widget_sync_time_text, theme.subtitleColor)
            views.setInt(R.id.widget_sync_dot, "setColorFilter", syncDotColor)
            val syncTooltip = if (lastSyncMillis > 0L) {
                val fullTime = CalendarRepository.formatLastSyncIndicator(context, lastSyncMillis, compact = false)
                "${context.getString(R.string.widget_refresh_desc)} ($fullTime)"
            } else {
                "${context.getString(R.string.widget_refresh_desc)} (${context.getString(R.string.widget_sync_tooltip_never)})"
            }
            views.setContentDescription(R.id.widget_refresh_btn, syncTooltip)

            // Theme-matched colors across header, icons, divider, and empty state
            views.setTextColor(R.id.widget_date_title, theme.titleColor)
            views.setTextColor(R.id.widget_empty_text, theme.subtitleColor)
            views.setInt(R.id.widget_app_icon, "setColorFilter", theme.accentColor)
            views.setInt(R.id.widget_empty_icon, "setColorFilter", theme.accentColor)
            views.setInt(R.id.widget_add_btn_bg, "setColorFilter", theme.chipBgColor)
            views.setInt(R.id.widget_add_btn_bg, "setImageAlpha", theme.chipBgAlpha)
            views.setInt(R.id.widget_add_btn, "setColorFilter", theme.accentColor)
            views.setInt(R.id.widget_refresh_btn_bg, "setColorFilter", theme.chipBgColor)
            views.setInt(R.id.widget_refresh_btn_bg, "setImageAlpha", theme.chipBgAlpha)
            views.setInt(R.id.widget_refresh_btn, "setColorFilter", theme.accentColor)
            views.setInt(R.id.widget_divider, "setBackgroundColor", theme.dividerColor)

            // Toggle manual refresh icon vs active syncing ProgressBar in header
            views.setViewVisibility(R.id.widget_refresh_btn, if (syncing) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_refresh_progress, if (syncing) View.VISIBLE else View.GONE)

            // Bind RemoteViews collection service
            val serviceIntent = Intent(context, CalendarAgendaWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("content://com.example.widget/agenda/$appWidgetId")
            }
            views.setRemoteAdapter(R.id.widget_agenda_list, serviceIntent)
            views.setEmptyView(R.id.widget_agenda_list, R.id.widget_empty_view)

            // Pending intent template for collection item click (tap-to-open).
            val itemClickIntent = Intent(context, CalendarAgendaWidgetProvider::class.java).apply {
                action = ACTION_ITEM_CLICK
            }
            val itemClickPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                itemClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_agenda_list, itemClickPendingIntent)

            // Manual sync/refresh button PendingIntent (48dp container + icon + empty state)
            val refreshIntent = Intent(context, CalendarAgendaWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_btn_container, refreshPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_empty_view, refreshPendingIntent)

            // Add event shortcut PendingIntent (48dp container + icon)
            val addEventIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("OPEN_ADD_EVENT", true)
            }
            val addEventPendingIntent = PendingIntent.getActivity(
                context,
                2,
                addEventIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_add_btn_container, addEventPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_add_btn, addEventPendingIntent)

            // Header title/icon click -> open companion app
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                3,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_date_title, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_app_icon, openAppPendingIntent)

            // Push widget layout update first, then notify list view data
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_agenda_list)
        }
    }
}
