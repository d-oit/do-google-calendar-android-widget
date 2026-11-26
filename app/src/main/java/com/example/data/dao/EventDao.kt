package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.EventEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for locally caching Google Calendar events in Room.
 * Provides both reactive [Flow] queries for Jetpack Compose UI and synchronous methods
 * tailored for AppWidget [android.widget.RemoteViewsService.RemoteViewsFactory] background queries.
 */
@Dao
interface EventDao {
    /**
     * Reactive stream of upcoming and ongoing events for Jetpack Compose UI.
     * Orders chronologically by start time.
     */
    @Query("SELECT * FROM calendar_events WHERE endMillis >= :nowMillis ORDER BY startMillis ASC")
    fun getUpcomingEventsFlow(nowMillis: Long): Flow<List<EventEntity>>

    /**
     * Horizon-bounded reactive stream of events for Jetpack Compose UI.
     */
    @Query("SELECT * FROM calendar_events WHERE endMillis >= :minEndMillis AND startMillis < :maxStartMillis ORDER BY startMillis ASC")
    fun getUpcomingEventsInRangeFlow(minEndMillis: Long, maxStartMillis: Long): Flow<List<EventEntity>>

    /**
     * Synchronous query of upcoming and ongoing events for widget RemoteViewsFactory.
     * Required because RemoteViewsService operates synchronously on background binder threads.
     */
    @Query("SELECT * FROM calendar_events WHERE endMillis >= :nowMillis ORDER BY startMillis ASC")
    fun getUpcomingEventsSync(nowMillis: Long): List<EventEntity>

    /**
     * Horizon-bounded synchronous query for widget RemoteViewsFactory.
     */
    @Query("SELECT * FROM calendar_events WHERE endMillis >= :minEndMillis AND startMillis < :maxStartMillis ORDER BY startMillis ASC")
    fun getUpcomingEventsInRangeSync(minEndMillis: Long, maxStartMillis: Long): List<EventEntity>

    /**
     * Synchronous query for top N upcoming events for constrained widget layouts.
     */
    @Query("SELECT * FROM calendar_events WHERE endMillis >= :nowMillis ORDER BY startMillis ASC LIMIT :limit")
    fun getUpcomingEventsLimitSync(nowMillis: Long, limit: Int): List<EventEntity>

    /**
     * Reactive stream of all cached events ordered chronologically.
     */
    @Query("SELECT * FROM calendar_events ORDER BY startMillis ASC")
    fun getAllEventsFlow(): Flow<List<EventEntity>>

    /**
     * Search cached events by query string matching title or location.
     */
    @Query("SELECT * FROM calendar_events WHERE title LIKE '%' || :query || '%' OR location LIKE '%' || :query || '%' ORDER BY startMillis ASC")
    fun searchEventsFlow(query: String): Flow<List<EventEntity>>

    /**
     * Get a specific cached event by its unique ID.
     */
    @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: String): EventEntity?

    /**
     * Synchronous lookup for a single event (used by widget fill-in clicks and broadcast receivers).
     */
    @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
    fun getEventByIdSync(id: String): EventEntity?

    /**
     * Reactive observation of a single event for detail dialogs.
     */
    @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
    fun getEventByIdFlow(id: String): Flow<EventEntity?>

    /**
     * Total number of cached events in local storage.
     */
    @Query("SELECT COUNT(*) FROM calendar_events")
    suspend fun getEventCount(): Int

    /**
     * Synchronous event count for widget empty-state detection.
     */
    @Query("SELECT COUNT(*) FROM calendar_events")
    fun getEventCountSync(): Int

    /**
     * Insert or replace multiple calendar events in a single transaction (batch sync).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)

    /**
     * Synchronous insert for widget RemoteViewsFactory background thread execution.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEventsSync(events: List<EventEntity>)

    /**
     * Insert or replace a single calendar event.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)

    /**
     * Update an existing event in local cache.
     */
    @Update
    suspend fun updateEvent(event: EventEntity)

    /**
     * Delete an event by ID.
     */
    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteEventById(id: String)

    /**
     * Prune expired / past events older than [cutoffMillis] to keep local widget cache clean and fast.
     */
    @Query("DELETE FROM calendar_events WHERE endMillis < :cutoffMillis")
    suspend fun deleteEventsOlderThan(cutoffMillis: Long): Int

    /**
     * Clear previously synced device/API events while preserving user-created offline events.
     */
    @Query("DELETE FROM calendar_events WHERE isOfflineCreated = 0")
    suspend fun clearSyncedEvents()

    /**
     * Remove any legacy seeded event rows in a single indexed SQL query.
     */
    @Query("DELETE FROM calendar_events WHERE id LIKE 'seed_%'")
    suspend fun deleteLegacySeedEvents(): Int

    /**
     * Clear all cached events (e.g. on full resync or account sign-out).
     */
    @Query("DELETE FROM calendar_events")
    suspend fun clearAll()

    /**
     * Atomically replaces synced events in a single database transaction so UI and widget
     * observers never see an intermediate empty table during sync.
     */
    @Transaction
    suspend fun replaceSyncedEvents(events: List<EventEntity>) {
        clearSyncedEvents()
        if (events.isNotEmpty()) {
            insertEvents(events)
        }
    }

    /**
     * Atomically replaces all cached events (e.g. when recomputing day headers).
     */
    @Transaction
    suspend fun replaceAllEvents(events: List<EventEntity>) {
        clearAll()
        if (events.isNotEmpty()) {
            insertEvents(events)
        }
    }
}
