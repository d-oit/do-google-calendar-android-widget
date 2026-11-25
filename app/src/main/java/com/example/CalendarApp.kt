package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.repository.CalendarRepository
import com.example.util.AppLogger
import com.example.worker.CalendarSyncScheduler
import kotlinx.coroutines.CoroutineExceptionHandler
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

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        AppLogger.e(AppLogger.TAG_APP, "Uncaught exception in applicationScope", throwable)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLogger.d(AppLogger.TAG_APP, "CalendarApp initialized")

        if (!isRunningInTestEnvironment()) {
            CalendarSyncScheduler.schedulePeriodicSync(this)
            // Synchronize genuine Google Calendar events directly from device accounts
            applicationScope.launch {
                repository.syncEvents(forceRefresh = false)
                    .onFailure { error ->
                        AppLogger.w(AppLogger.TAG_APP, "Initial background sync did not complete: ${error.message}")
                    }
            }
        }
    }

    companion object {
        lateinit var instance: CalendarApp
            private set

        private fun isRunningInTestEnvironment(): Boolean {
            return try {
                Class.forName("org.robolectric.Robolectric") != null
            } catch (_: ClassNotFoundException) {
                false
            }
        }
    }
}
