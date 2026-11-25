package com.example.widget

import android.content.Context
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
}
