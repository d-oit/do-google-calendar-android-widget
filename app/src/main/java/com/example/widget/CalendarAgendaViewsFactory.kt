package com.example.widget

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.CalendarApp
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.model.EventEntity
import com.example.data.pref.WidgetDensity
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.repository.CalendarRepository
import com.example.util.AppLogger
import com.example.util.CalendarColorMapper

class CalendarAgendaViewsFactory(
    private val context: Context,
    intent: Intent,
    private val repository: CalendarRepository? = null
) : RemoteViewsService.RemoteViewsFactory {

    @Volatile
    private var events: List<EventEntity> = emptyList()

    private val loadLock = Any()

    override fun onCreate() {
        loadEvents()
    }

    override fun onDataSetChanged() {
        loadEvents()
    }

    private fun loadEvents() {
        synchronized(loadLock) {
            try {
                val prefs = WidgetPreferencesManager.getInstance(context).getPreferences()
                val now = System.currentTimeMillis()
                val loaded = if (repository != null) {
                    repository.getUpcomingEventsSync()
                } else {
                    val appRepo = runCatching { CalendarApp.instance.repository }.getOrNull()
                    if (appRepo != null) {
                        appRepo.getUpcomingEventsSync()
                    } else {
                        val db = AppDatabase.getInstance(context)
                        val threshold = CalendarRepository.getStartOfTodayMillis(now)
                        val raw = db.eventDao().getUpcomingEventsSync(threshold)
                        CalendarRepository.filterEventsForPreferences(raw, prefs, now)
                    }
                }
                events = loaded
                AppLogger.d(AppLogger.TAG_WIDGET, "RemoteViewsFactory loaded ${loaded.size} event(s)")
            } catch (e: Exception) {
                AppLogger.e(AppLogger.TAG_WIDGET, "Failed to load events in CalendarAgendaViewsFactory", e)
                events = emptyList()
            }
        }
    }

    override fun onDestroy() {
        events = emptyList()
    }

    override fun getCount(): Int = events.size

    override fun getViewAt(position: Int): RemoteViews? {
        val snapshot = events
        if (position < 0 || position >= snapshot.size) return null
        val event = snapshot[position]
        val prefs = WidgetPreferencesManager.getInstance(context).getPreferences()
        val theme = WidgetThemeHelper.resolveColors(context, prefs)
        val rv = RemoteViews(context.packageName, R.layout.widget_agenda_item)

        // Day header chip with theme-matched pill background
        if (event.isFirstOfDay && event.dayHeader.isNotEmpty() && prefs.showDayHeaders) {
            rv.setViewVisibility(R.id.widget_item_date_header_container, View.VISIBLE)
            rv.setTextViewText(R.id.widget_item_date_header, event.dayHeader)
            rv.setTextColor(R.id.widget_item_date_header, theme.accentColor)
            rv.setInt(R.id.widget_item_date_header_bg, "setColorFilter", theme.chipBgColor)
            rv.setInt(R.id.widget_item_date_header_bg, "setImageAlpha", theme.chipBgAlpha)
        } else {
            rv.setViewVisibility(R.id.widget_item_date_header_container, View.GONE)
        }

        // Rounded item card surface + border matching theme and opacity
        rv.setInt(R.id.widget_item_bg_fill, "setColorFilter", theme.itemBgColor)
        rv.setInt(R.id.widget_item_bg_fill, "setImageAlpha", theme.itemAlpha)
        rv.setInt(R.id.widget_item_bg_stroke, "setColorFilter", theme.itemBorderColor)
        rv.setInt(R.id.widget_item_bg_stroke, "setImageAlpha", theme.itemBorderAlpha)

        // Compact vs Regular vs Large padding
        val padPx = when (prefs.density) {
            WidgetDensity.COMPACT -> dpToPx(6f)
            WidgetDensity.REGULAR -> dpToPx(8f)
            WidgetDensity.LARGE -> dpToPx(11f)
        }
        rv.setViewPadding(R.id.widget_item_content, padPx, padPx, padPx, padPx)

        // Title and Density Font Scaling
        rv.setTextViewText(R.id.widget_item_title, event.title)
        rv.setTextViewTextSize(R.id.widget_item_title, TypedValue.COMPLEX_UNIT_SP, prefs.density.titleSp)
        rv.setTextColor(R.id.widget_item_title, theme.titleColor)
        rv.setTextColor(R.id.widget_item_location, theme.subtitleColor)
        rv.setTextColor(R.id.widget_item_calendar, theme.subtitleColor)

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

        val eventColorInt = CalendarColorMapper.parseColorInt(
            event.colorHex,
            theme.accentColor
        )
        rv.setTextColor(R.id.widget_item_time, eventColorInt)

        // Location / Conference link (filtered at presentation time)
        if (!event.location.isNullOrBlank() && prefs.showLocations) {
            rv.setViewVisibility(R.id.widget_item_location, View.VISIBLE)
            rv.setTextViewText(R.id.widget_item_location, event.location)
        } else {
            rv.setViewVisibility(R.id.widget_item_location, View.GONE)
        }

        // Rounded accent color stripe
        rv.setInt(R.id.widget_item_color_bar, "setColorFilter", eventColorInt)

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

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        val snapshot = events
        return if (position in snapshot.indices) {
            snapshot[position].id.hashCode().toLong()
        } else {
            position.toLong()
        }
    }

    override fun hasStableIds(): Boolean = true
}
