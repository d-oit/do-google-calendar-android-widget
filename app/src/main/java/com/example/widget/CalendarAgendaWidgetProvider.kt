package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.RemoteViews
import android.widget.Toast
import com.example.CalendarApp
import com.example.MainActivity
import com.example.R
import com.example.data.pref.WidgetColorTheme
import com.example.data.pref.WidgetPreferencesManager
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
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_REFRESH_WIDGET -> {
                Toast.makeText(context, "Refreshing calendar agenda...", Toast.LENGTH_SHORT).show()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        CalendarApp.instance.repository.syncEvents(forceRefresh = true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            ACTION_ITEM_CLICK -> {
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
                val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE)
                val eventLink = intent.getStringExtra(EXTRA_EVENT_LINK)
                val startMillis = intent.getLongExtra(EXTRA_START_MILLIS, 0L)

                // Validate and sanitize URL strictly for security
                val isSafeWebUrl = !eventLink.isNullOrBlank() &&
                    (eventLink.startsWith("https://", ignoreCase = true) || eventLink.startsWith("http://", ignoreCase = true))

                val openIntent = if (isSafeWebUrl) {
                    Intent(Intent.ACTION_VIEW, Uri.parse(eventLink)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                } else if (startMillis > 0) {
                    val builder = Uri.parse("content://com.android.calendar/time").buildUpon()
                    android.content.ContentUris.appendId(builder, startMillis)
                    Intent(Intent.ACTION_VIEW).apply {
                        data = builder.build()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                } else {
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(EXTRA_EVENT_ID, eventId)
                        putExtra(EXTRA_EVENT_TITLE, eventTitle)
                    }
                }

                try {
                    context.startActivity(openIntent)
                } catch (e: Exception) {
                    try {
                        val fallbackIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(EXTRA_EVENT_ID, eventId)
                            putExtra(EXTRA_EVENT_TITLE, eventTitle)
                        }
                        context.startActivity(fallbackIntent)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
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

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_calendar_agenda)
            val prefs = WidgetPreferencesManager.getInstance(context).getPreferences()

            // Custom opacity and theme background
            val alpha = ((prefs.opacityPercent / 100f) * 255).toInt().coerceIn(0, 255)
            val isDark = prefs.colorTheme == WidgetColorTheme.DARK_SLATE
            val bgColor = if (isDark) {
                Color.argb(alpha, 30, 30, 30)
            } else {
                Color.argb(alpha, 248, 250, 253)
            }
            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)

            // Current date title formatted
            val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
            val dateTitle = dateFormat.format(Date())
            views.setTextViewText(R.id.widget_date_title, dateTitle)

            if (isDark) {
                views.setTextColor(R.id.widget_date_title, Color.WHITE)
                views.setTextColor(R.id.widget_empty_text, Color.parseColor("#B0BEC5"))
            } else {
                views.setTextColor(R.id.widget_date_title, Color.parseColor("#1F1F1F"))
                views.setTextColor(R.id.widget_empty_text, Color.parseColor("#5F6368"))
            }

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

            // Refresh button PendingIntent
            val refreshIntent = Intent(context, CalendarAgendaWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_empty_view, refreshPendingIntent)

            // Add event shortcut PendingIntent
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
