package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.util.AppLogger
import com.example.worker.CalendarSyncScheduler

class CalendarGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarGlanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AppLogger.d(AppLogger.TAG_WIDGET, "Glance agenda widget enabled; ensuring periodic calendar sync")
        CalendarSyncScheduler.schedulePeriodicSync(context.applicationContext)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_PROVIDER_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> {
                AppLogger.d(
                    AppLogger.TAG_WIDGET,
                    "Glance widget receiver observed ${intent.action}; scheduling immediate refresh"
                )
                CalendarSyncScheduler.enqueueImmediateSync(
                    context = context.applicationContext,
                    forceRefresh = false
                )
            }
        }
    }
}
