package com.example.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.util.AppLogger
import java.util.concurrent.TimeUnit

/**
 * Schedules and manages background [CalendarSyncWorker] tasks using [WorkManager].
 */
object CalendarSyncScheduler {
    const val UNIQUE_PERIODIC_SYNC_WORK = "periodic_calendar_sync"
    const val UNIQUE_IMMEDIATE_SYNC_WORK = "immediate_calendar_sync"
    const val TAG_CALENDAR_SYNC = "tag_calendar_sync"
    const val DEFAULT_SYNC_INTERVAL_MINUTES = 30L
    const val MIN_PERIODIC_INTERVAL_MINUTES = PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS / 60_000L

    fun schedulePeriodicSync(
        context: Context,
        repeatIntervalMinutes: Long = DEFAULT_SYNC_INTERVAL_MINUTES,
        existingPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
    ) {
        val safeIntervalMinutes = repeatIntervalMinutes.coerceAtLeast(MIN_PERIODIC_INTERVAL_MINUTES)
        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
            safeIntervalMinutes,
            TimeUnit.MINUTES
        )
            .setInputData(workDataOf(CalendarSyncWorker.KEY_FORCE_REFRESH to true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.SECONDS)
            .addTag(TAG_CALENDAR_SYNC)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_SYNC_WORK,
            existingPolicy,
            request
        )
        AppLogger.d(
            AppLogger.TAG_SYNC,
            "Scheduled periodic CalendarSyncWorker every ${safeIntervalMinutes}m (policy=$existingPolicy)"
        )
    }

    fun enqueueImmediateSync(
        context: Context,
        forceRefresh: Boolean = true
    ) {
        val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
            .setInputData(workDataOf(CalendarSyncWorker.KEY_FORCE_REFRESH to forceRefresh))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10L, TimeUnit.SECONDS)
            .addTag(TAG_CALENDAR_SYNC)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_IMMEDIATE_SYNC_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
        AppLogger.d(AppLogger.TAG_SYNC, "Enqueued immediate CalendarSyncWorker (forceRefresh=$forceRefresh)")
    }

    fun cancelPeriodicSync(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_PERIODIC_SYNC_WORK)
        AppLogger.d(AppLogger.TAG_SYNC, "Cancelled periodic CalendarSyncWorker")
    }
}
