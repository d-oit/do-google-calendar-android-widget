package com.example

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasText
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.example.data.dao.EventDao
import com.example.data.db.AppDatabase
import com.example.data.model.AppError
import com.example.data.model.EventEntity
import com.example.data.pref.WidgetPreferences
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.repository.CalendarRepository
import com.example.widget.CalendarAgendaWidgetProvider
import com.example.widget.CalendarGlanceWidgetContent
import com.example.widget.GlanceWidgetUpdater
import com.example.widget.WidgetThemeHelper
import com.example.worker.CalendarSyncScheduler
import com.example.worker.CalendarSyncWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarWorkManagerGlanceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var eventDao: EventDao
    private lateinit var prefsManager: WidgetPreferencesManager
    private lateinit var repository: CalendarRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val wmConfig = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, wmConfig)

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        eventDao = database.eventDao()
        prefsManager = WidgetPreferencesManager.getInstance(context)
        prefsManager.updatePreferences(WidgetPreferences())
        repository = CalendarRepository(context, eventDao, prefsManager)
        CalendarSyncWorker.repositoryProviderForTesting = { repository }
    }

    @After
    fun tearDown() {
        CalendarSyncWorker.repositoryProviderForTesting = null
        GlanceWidgetUpdater.testUpdateObserver = null
        repository.setSyncErrorForTesting(null)
        database.close()
    }

    @Test
    fun `CUJ_23_scheduler_enqueues_periodic_and_immediate_work_requests`() {
        val workManager = WorkManager.getInstance(context)
        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)

        CalendarSyncScheduler.schedulePeriodicSync(context, repeatIntervalMinutes = 10L)

        val periodicInfos = workManager.getWorkInfosForUniqueWork(
            CalendarSyncScheduler.UNIQUE_PERIODIC_SYNC_WORK
        ).get(5, TimeUnit.SECONDS)

        assertEquals(1, periodicInfos.size)
        val periodicInfo = periodicInfos.first()
        assertEquals(WorkInfo.State.ENQUEUED, periodicInfo.state)
        assertTrue(periodicInfo.tags.contains(CalendarSyncScheduler.TAG_CALENDAR_SYNC))
        // Interval clamped to WorkManager minimum 15 minutes
        assertTrue(periodicInfo.periodicityInfo != null)
        assertEquals(
            15L * 60_000L,
            periodicInfo.periodicityInfo!!.repeatIntervalMillis
        )

        testDriver?.setPeriodDelayMet(periodicInfo.id)

        CalendarSyncScheduler.enqueueImmediateSync(context, forceRefresh = true)
        val immediateInfos = workManager.getWorkInfosForUniqueWork(
            CalendarSyncScheduler.UNIQUE_IMMEDIATE_SYNC_WORK
        ).get(5, TimeUnit.SECONDS)
        assertEquals(1, immediateInfos.size)
        assertTrue(immediateInfos.first().tags.contains(CalendarSyncScheduler.TAG_CALENDAR_SYNC))

        CalendarSyncScheduler.cancelPeriodicSync(context)
        val afterCancel = workManager.getWorkInfosForUniqueWork(
            CalendarSyncScheduler.UNIQUE_PERIODIC_SYNC_WORK
        ).get(5, TimeUnit.SECONDS)
        assertEquals(WorkInfo.State.CANCELLED, afterCancel.first().state)
    }

    @Test
    fun `CUJ_24_worker_syncs_calendar_and_triggers_glance_widget_manager`() = runBlocking {
        repository.updatePermissionState(explicitGrant = true)
        var observerCalled = false
        var observedTimestamp = 0L
        GlanceWidgetUpdater.testUpdateObserver = { _, ts ->
            observerCalled = true
            observedTimestamp = ts
        }

        val initialSyncTime = repository.lastSyncTime.value
        val worker = TestListenableWorkerBuilder<CalendarSyncWorker>(context)
            .setInputData(workDataOf(CalendarSyncWorker.KEY_FORCE_REFRESH to true))
            .build()

        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertTrue(output.getLong(CalendarSyncWorker.KEY_LAST_SYNC_MILLIS, 0L) > 0L)
        assertTrue(repository.lastSyncTime.value >= initialSyncTime)
        assertTrue(observerCalled)
        assertTrue(observedTimestamp > 0L)
    }

    @Test
    fun `CUJ_25_worker_handles_missing_permission_and_retries_transient_failures`() = runBlocking {
        // 1. Missing permission completes gracefully without battery-wasting retries
        repository.updatePermissionState(explicitGrant = false)
        var glanceRefreshed = false
        GlanceWidgetUpdater.testUpdateObserver = { _, _ -> glanceRefreshed = true }

        val permWorker = TestListenableWorkerBuilder<CalendarSyncWorker>(context).build()
        val permResult = permWorker.doWork()
        assertTrue(permResult is ListenableWorker.Result.Success)
        val permOutput = (permResult as ListenableWorker.Result.Success).outputData
        assertTrue(permOutput.getBoolean(CalendarSyncWorker.KEY_SKIPPED_NO_PERMISSION, false))
        assertTrue(glanceRefreshed)

        // 2. Transient CalendarContract failure retries while below MAX_RETRY_ATTEMPTS
        repository.updatePermissionState(explicitGrant = true)
        repository.setSyncErrorForTesting(AppError.CalendarQueryFailed("Temporary provider outage"))

        val retryWorker = TestListenableWorkerBuilder<CalendarSyncWorker>(context)
            .setRunAttemptCount(0)
            .build()
        assertEquals(ListenableWorker.Result.retry(), retryWorker.doWork())

        // 3. Exhausted retries fail cleanly with error message
        val failedWorker = TestListenableWorkerBuilder<CalendarSyncWorker>(context)
            .setRunAttemptCount(CalendarSyncWorker.MAX_RETRY_ATTEMPTS)
            .build()
        val failResult = failedWorker.doWork()
        assertTrue(failResult is ListenableWorker.Result.Failure)
        val errMsg = (failResult as ListenableWorker.Result.Failure).outputData
            .getString(CalendarSyncWorker.KEY_ERROR_MESSAGE)
        assertEquals("Temporary provider outage", errMsg)
    }

    @Test
    fun `CUJ_26_glance_widget_content_renders_empty_and_populated_events`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 260.dp))

        val prefs = WidgetPreferences()
        val theme = WidgetThemeHelper.resolveColors(context, prefs)

        provideComposable {
            CalendarGlanceWidgetContent(
                events = emptyList(),
                prefs = prefs,
                theme = theme,
                hasPermission = true,
                syncRevision = 1,
                lastSyncMillis = 0L
            )
        }

        onNode(hasTestTag("glance_empty_text"))
            .assertHasText(context.getString(R.string.widget_no_events))
        onNode(hasTestTag("glance_empty_illustration")).assertExists()
        onNode(hasTestTag("glance_sync_time_text"))
            .assertHasText(context.getString(R.string.widget_sync_never))
        onNode(hasTestTag("glance_refresh_button")).assertExists()
        onNode(hasTestTag("glance_add_event_button")).assertExists()

        val now = System.currentTimeMillis()
        val sampleEvent = EventEntity(
            id = "glance_evt_1",
            calendarId = "Work",
            sourceCalendarId = 1L,
            title = "Quarterly Planning",
            location = "Room 4B",
            startMillis = now + 3600_000L,
            endMillis = now + 7200_000L,
            colorHex = "#1A73E8",
            dayHeader = "TODAY",
            isFirstOfDay = true
        )

        provideComposable {
            CalendarGlanceWidgetContent(
                events = listOf(sampleEvent),
                prefs = prefs,
                theme = theme,
                hasPermission = true,
                syncRevision = 2,
                lastSyncMillis = now
            )
        }

        onNode(hasText("Quarterly Planning")).assertExists()
        onNode(hasText("TODAY")).assertExists()
        onNode(hasTestTag("glance_agenda_list")).assertExists()
        onNode(hasTestTag("glance_sync_time_text"))
            .assertHasText(context.getString(R.string.widget_sync_just_now))
    }

    @Test
    fun `CUJ_27_widget_header_last_sync_indicator_and_persistence`() = runBlocking<Unit> {
        prefsManager.setLastSuccessfulSyncMillis(0L)
        assertEquals(0L, prefsManager.getLastSuccessfulSyncMillis())

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val shadowAppWidgetManager = org.robolectric.Shadows.shadowOf(appWidgetManager)
        val widgetId = shadowAppWidgetManager.createWidget(
            CalendarAgendaWidgetProvider::class.java,
            R.layout.widget_calendar_agenda
        )

        // 1. Before any sync succeeds, header shows "Not synced"
        CalendarAgendaWidgetProvider.updateAppWidget(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = widgetId,
            isSyncing = false
        )
        val beforeSyncView = shadowAppWidgetManager.getViewFor(widgetId)
        val syncTextBefore = beforeSyncView.findViewById<TextView>(R.id.widget_sync_time_text)
        assertNotNull(syncTextBefore)
        assertEquals(context.getString(R.string.widget_sync_never), syncTextBefore.text.toString())

        // 2. Active sync shows "Syncing..." indicator in header
        CalendarAgendaWidgetProvider.updateAppWidget(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = widgetId,
            isSyncing = true
        )
        val syncingView = shadowAppWidgetManager.getViewFor(widgetId)
        assertEquals(
            context.getString(R.string.widget_sync_in_progress),
            syncingView.findViewById<TextView>(R.id.widget_sync_time_text).text.toString()
        )

        // 3. Successful CalendarContract sync persists timestamp and updates header + tooltip
        repository.updatePermissionState(explicitGrant = true)
        val syncResult = repository.syncEvents(forceRefresh = true)
        assertTrue(syncResult is com.example.data.model.OperationResult.Success)
        val persistedTimestamp = prefsManager.getLastSuccessfulSyncMillis()
        assertTrue(persistedTimestamp > 0L)

        CalendarAgendaWidgetProvider.updateAppWidget(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = widgetId,
            isSyncing = false
        )
        val afterSyncView = shadowAppWidgetManager.getViewFor(widgetId)
        val syncTextAfter = afterSyncView.findViewById<TextView>(R.id.widget_sync_time_text).text.toString()
        assertEquals(context.getString(R.string.widget_sync_just_now), syncTextAfter)
        val refreshContentDesc = afterSyncView.findViewById<android.view.View>(R.id.widget_refresh_btn)
            .contentDescription?.toString() ?: ""
        assertTrue(refreshContentDesc.contains(syncTextAfter))

        // 4. Failed sync preserves the previous successful sync timestamp
        repository.setSyncErrorForTesting(AppError.CalendarQueryFailed("Outage"))
        repository.syncEvents(forceRefresh = true)
        assertEquals(persistedTimestamp, prefsManager.getLastSuccessfulSyncMillis())
    }
}
