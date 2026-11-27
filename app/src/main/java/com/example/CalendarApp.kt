package com.example

import android.app.Application
import android.content.res.Configuration
import com.example.data.db.AppDatabase
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.repository.CalendarRepository
import com.example.util.AppLogger
import com.example.util.RuntimeEnvironment
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

    @Volatile
    private var lastNightMode: Int = Configuration.UI_MODE_NIGHT_UNDEFINED

    override fun onCreate() {
        super.onCreate()
        instance = this
        lastNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        AppLogger.d(AppLogger.TAG_APP, "CalendarApp initialized")

        if (!RuntimeEnvironment.isRunningInTestEnvironment) {
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (newNightMode != lastNightMode) {
            lastNightMode = newNightMode
            AppLogger.d(AppLogger.TAG_WIDGET, "System night mode changed ($newNightMode); refreshing widgets")
            preferencesManager.notifyWidgetsChanged(debounced = false)
        }
    }

    companion object {
        lateinit var instance: CalendarApp
            private set
    }
}
