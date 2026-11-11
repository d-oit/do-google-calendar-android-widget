package com.example.widget

import android.content.Intent
import android.widget.RemoteViewsService

class CalendarAgendaWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return CalendarAgendaViewsFactory(applicationContext, intent)
    }
}
