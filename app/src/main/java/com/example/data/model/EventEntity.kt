package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room database entity representing a locally cached Google Calendar event.
 * Optimized for offline viewing on the home screen widget and in-app agenda.
 *
 * Indexed on [startMillis], [endMillis], and [calendarId] for high-performance range queries
 * and quick filtering during widget RemoteViews updates.
 */
@Entity(
    tableName = "calendar_events",
    indices = [
        Index(value = ["startMillis"]),
        Index(value = ["endMillis"]),
        Index(value = ["calendarId"])
    ]
)
data class EventEntity(
    @PrimaryKey
    val id: String,
    val calendarId: String = "primary",
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean = false,
    val colorHex: String = "#1A73E8",
    val htmlLink: String? = null,
    val dayHeader: String = "",
    val isFirstOfDay: Boolean = false,
    val isOfflineCreated: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Checks if the event is currently active / ongoing at [now].
     */
    fun isOngoing(now: Long = System.currentTimeMillis()): Boolean =
        now in startMillis..endMillis

    /**
     * Checks if the event has already concluded before [now].
     */
    fun isPast(now: Long = System.currentTimeMillis()): Boolean =
        endMillis < now
}
