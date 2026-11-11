package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.repository.CalendarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CalendarApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferencesManager: WidgetPreferencesManager by lazy { WidgetPreferencesManager.getInstance(this) }
    val repository: CalendarRepository by lazy {
        CalendarRepository(this, database.eventDao(), preferencesManager)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (!isRunningInTestEnvironment()) {
            // Synchronize genuine Google Calendar events directly from device accounts
            applicationScope.launch {
                repository.syncEvents(forceRefresh = false)
            }
        }
    }

    companion object {
        lateinit var instance: CalendarApp
            private set

        private fun isRunningInTestEnvironment(): Boolean {
            return try {
                Class.forName("org.robolectric.Robolectric") != null
            } catch (_: Throwable) {
                false
            }
        }
    }
}
