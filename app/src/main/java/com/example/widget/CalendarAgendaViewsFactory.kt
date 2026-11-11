package com.example.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.CalendarApp
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.model.EventEntity
import com.example.data.pref.WidgetColorTheme
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.repository.CalendarRepository

class CalendarAgendaViewsFactory(
    private val context: Context,
    intent: Intent,
    private val repository: CalendarRepository? = null
) : RemoteViewsService.RemoteViewsFactory {

    private val events = mutableListOf<EventEntity>()

    override fun onCreate() {
        loadEvents()
    }

    override fun onDataSetChanged() {
        loadEvents()
    }

    private fun loadEvents() {
        events.clear()
        try {
            val list = if (repository != null) {
                repository.getUpcomingEventsSync()
            } else {
                try {
                    CalendarApp.instance.repository.getUpcomingEventsSync()
                } catch (e: Throwable) {
                    val db = AppDatabase.getInstance(context)
                    val threshold = System.currentTimeMillis() - (60 * 60 * 1000)
                    db.eventDao().getUpcomingEventsSync(threshold)
                }
            }
            events.addAll(list)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        events.clear()
    }

    override fun getCount(): Int = events.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position < 0 || position >= events.size) return null
        val event = events[position]
        val prefs = WidgetPreferencesManager.getInstance(context).getPreferences()
        val rv = RemoteViews(context.packageName, R.layout.widget_agenda_item)

        // Day header chip
        if (event.isFirstOfDay && event.dayHeader.isNotEmpty() && prefs.showDayHeaders) {
            rv.setViewVisibility(R.id.widget_item_date_header, View.VISIBLE)
            rv.setTextViewText(R.id.widget_item_date_header, event.dayHeader)
            if (prefs.colorTheme == WidgetColorTheme.DARK_SLATE) {
                rv.setTextColor(R.id.widget_item_date_header, Color.parseColor("#90CAF9"))
            } else {
                rv.setTextColor(R.id.widget_item_date_header, Color.parseColor(prefs.colorTheme.primaryHex))
            }
        } else {
            rv.setViewVisibility(R.id.widget_item_date_header, View.GONE)
        }

        // Title and Density Font Scaling
        rv.setTextViewText(R.id.widget_item_title, event.title)
        rv.setTextViewTextSize(R.id.widget_item_title, TypedValue.COMPLEX_UNIT_SP, prefs.density.titleSp)

        if (prefs.colorTheme == WidgetColorTheme.DARK_SLATE) {
            rv.setTextColor(R.id.widget_item_title, Color.parseColor("#FFFFFF"))
            rv.setTextColor(R.id.widget_item_location, Color.parseColor("#B0BEC5"))
            rv.setTextColor(R.id.widget_item_calendar, Color.parseColor("#90CAF9"))
        } else {
            rv.setTextColor(R.id.widget_item_title, Color.parseColor("#1F1F1F"))
            rv.setTextColor(R.id.widget_item_location, Color.parseColor("#5F6368"))
            rv.setTextColor(R.id.widget_item_calendar, Color.parseColor("#5F6368"))
        }

        // Calendar Name badge
        if (event.calendarId.isNotBlank()) {
            rv.setViewVisibility(R.id.widget_item_calendar, View.VISIBLE)
            rv.setTextViewText(R.id.widget_item_calendar, event.calendarId)
        } else {
            rv.setViewVisibility(R.id.widget_item_calendar, View.GONE)
        }

        // Time
        val timeString = CalendarRepository.formatEventTime(
            event.startMillis,
            event.endMillis,
            event.isAllDay
        )
        rv.setTextViewText(R.id.widget_item_time, timeString)
        rv.setTextViewTextSize(R.id.widget_item_time, TypedValue.COMPLEX_UNIT_SP, prefs.density.timeSp)

        val eventColorInt = com.example.util.CalendarColorMapper.parseColorInt(
            event.colorHex,
            com.example.util.CalendarColorMapper.parseColorInt(prefs.colorTheme.primaryHex)
        )

        rv.setTextColor(R.id.widget_item_time, eventColorInt)

        // Location / Conference link
        if (!event.location.isNullOrBlank() && prefs.showLocations) {
            rv.setViewVisibility(R.id.widget_item_location, View.VISIBLE)
            rv.setTextViewText(R.id.widget_item_location, event.location)
        } else {
            rv.setViewVisibility(R.id.widget_item_location, View.GONE)
        }

        // Accent color stripe
        rv.setInt(R.id.widget_item_color_bar, "setBackgroundColor", eventColorInt)

        // Fill-in Intent for list item click
        val fillInIntent = Intent().apply {
            putExtra(CalendarAgendaWidgetProvider.EXTRA_EVENT_ID, event.id)
            putExtra(CalendarAgendaWidgetProvider.EXTRA_EVENT_TITLE, event.title)
            putExtra(CalendarAgendaWidgetProvider.EXTRA_EVENT_LINK, event.htmlLink ?: "")
            putExtra(CalendarAgendaWidgetProvider.EXTRA_START_MILLIS, event.startMillis)
        }
        rv.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)
        rv.setOnClickFillInIntent(R.id.widget_item_card, fillInIntent)

        return rv
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        return if (position in 0 until events.size) {
            events[position].id.hashCode().toLong()
        } else {
            position.toLong()
        }
    }

    override fun hasStableIds(): Boolean = true
}
