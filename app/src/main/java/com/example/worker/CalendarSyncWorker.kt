package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.CalendarApp
import com.example.data.db.AppDatabase
import com.example.data.model.AppError
import com.example.data.model.OperationResult
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.repository.CalendarRepository
import com.example.util.AppLogger
import com.example.widget.GlanceWidgetUpdater
import kotlinx.coroutines.CancellationException

/**
 * Background [CoroutineWorker] responsible for periodically syncing local Room cache
 * with Android's native [android.provider.CalendarContract] and updating all home screen
 * Glance widgets via [androidx.glance.appwidget.GlanceAppWidgetManager].
 */
class CalendarSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, true)
        AppLogger.d(
            AppLogger.TAG_SYNC,
            "CalendarSyncWorker started (attempt=$runAttemptCount, forceRefresh=$forceRefresh)"
        )

        return try {
            val repository = resolveRepository(applicationContext)
            when (val syncResult = repository.syncEvents(forceRefresh = forceRefresh)) {
                is OperationResult.Success -> {
                    val syncTimestamp = System.currentTimeMillis()
                    val glanceCount = GlanceWidgetUpdater.updateAllGlanceWidgets(
                        context = applicationContext,
                        syncTimestampMillis = syncTimestamp
                    )
                    AppLogger.d(
                        AppLogger.TAG_SYNC,
                        "CalendarSyncWorker completed: synced=${syncResult.data} events, glanceUpdated=$glanceCount"
                    )
                    Result.success(
                        workDataOf(
                            KEY_SYNCED_COUNT to syncResult.data,
                            KEY_GLANCE_UPDATED_COUNT to glanceCount,
                            KEY_LAST_SYNC_MILLIS to syncTimestamp
                        )
                    )
                }

                is OperationResult.Failure -> {
                    when (val error = syncResult.error) {
                        is AppError.PermissionDenied -> {
                            AppLogger.w(
                                AppLogger.TAG_SYNC,
                                "CalendarSyncWorker skipped sync due to missing calendar permission"
                            )
                            val glanceCount = GlanceWidgetUpdater.updateAllGlanceWidgets(applicationContext)
                            Result.success(
                                workDataOf(
                                    KEY_SKIPPED_NO_PERMISSION to true,
                                    KEY_GLANCE_UPDATED_COUNT to glanceCount
                                )
                            )
                        }

                        else -> {
                            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                                AppLogger.w(
                                    AppLogger.TAG_SYNC,
                                    "CalendarSyncWorker transient failure (${error.message}); scheduling retry"
                                )
                                Result.retry()
                            } else {
                                val errorCause = when (error) {
                                    is AppError.CalendarQueryFailed -> error.cause
                                    is AppError.EventWriteFailed -> error.cause
                                    is AppError.DatabaseFailed -> error.cause
                                    is AppError.Unexpected -> error.cause
                                    is AppError.PermissionDenied -> null
                                }
                                AppLogger.e(
                                    AppLogger.TAG_SYNC,
                                    "CalendarSyncWorker failed after $MAX_RETRY_ATTEMPTS retries: ${error.message}",
                                    errorCause
                                )
                                Result.failure(workDataOf(KEY_ERROR_MESSAGE to error.message))
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(AppLogger.TAG_SYNC, "Unexpected exception inside CalendarSyncWorker", e)
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Calendar sync error"))
                )
            }
        }
    }

    private fun resolveRepository(context: Context): CalendarRepository {
        repositoryProviderForTesting?.let { provider ->
            return provider(context)
        }
        val appRepo = runCatching { CalendarApp.instance.repository }.getOrNull()
        if (appRepo != null) return appRepo
        return CalendarRepository(
            context.applicationContext,
            AppDatabase.getInstance(context.applicationContext).eventDao(),
            WidgetPreferencesManager.getInstance(context.applicationContext)
        )
    }

    companion object {
        const val KEY_FORCE_REFRESH = "key_force_refresh"
        const val KEY_SYNCED_COUNT = "key_synced_count"
        const val KEY_GLANCE_UPDATED_COUNT = "key_glance_updated_count"
        const val KEY_LAST_SYNC_MILLIS = "key_last_sync_millis"
        const val KEY_SKIPPED_NO_PERMISSION = "key_skipped_no_permission"
        const val KEY_ERROR_MESSAGE = "key_error_message"
        const val MAX_RETRY_ATTEMPTS = 3

        @Volatile
        internal var repositoryProviderForTesting: ((Context) -> CalendarRepository)? = null
    }
}
