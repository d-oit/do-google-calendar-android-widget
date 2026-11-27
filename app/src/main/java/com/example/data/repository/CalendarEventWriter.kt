package com.example.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.example.util.AppLogger
import com.example.util.CalendarColorMapper
import com.example.util.CalendarDateFormatter
import java.util.TimeZone

/**
 * Encapsulates write operations against Android's native [CalendarContract.Events]
 * and fallback local calendar provisioning.
 */
internal class CalendarEventWriter(
    private val context: Context
) {
    fun findPrimaryCalendarId(): Long? {
        try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.IS_PRIMARY
            )
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val typeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)

                var googleCalId: Long? = null
                var fallbackId: Long? = null

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    if (fallbackId == null) fallbackId = id
                    val acctType = if (typeIdx != -1) cursor.getString(typeIdx) ?: "" else ""
                    if (googleCalId == null && acctType == "com.google") {
                        googleCalId = id
                    }
                }
                return googleCalId ?: fallbackId
            }
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_REPO, "Error finding primary calendar ID", e)
        }
        return null
    }

    fun ensureLocalCalendarExists(preferredAccountName: String?): Long? {
        val existingId = findPrimaryCalendarId()
        if (existingId != null) return existingId

        return try {
            val targetAccount = preferredAccountName?.ifBlank { null } ?: "Local Calendar"
            val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, targetAccount)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build()

            val values = ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, targetAccount)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, "Google Calendar")
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Google Calendar")
                put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF1A73E8.toInt())
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, targetAccount)
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            }

            val resultUri = context.contentResolver.insert(uri, values)
            resultUri?.let { ContentUris.parseId(it) }
        } catch (e: Exception) {
            AppLogger.e(AppLogger.TAG_REPO, "Failed to create fallback local calendar", e)
            null
        }
    }

    fun buildEventContentValues(
        title: String,
        location: String?,
        description: String?,
        startMillis: Long,
        endMillis: Long,
        isAllDay: Boolean,
        colorHex: String,
        calendarId: Long
    ): ContentValues {
        val dtStart = if (isAllDay) CalendarDateFormatter.localMillisToUtcAllDay(startMillis) else startMillis
        val dtEnd = if (isAllDay) {
            val utcEnd = CalendarDateFormatter.localMillisToUtcAllDay(endMillis)
            if (utcEnd <= dtStart) dtStart + CalendarDateFormatter.DAY_MILLIS else utcEnd
        } else {
            if (endMillis <= startMillis) startMillis + 3600000L else endMillis
        }
        val colorInt = CalendarColorMapper.parseColorInt(colorHex)

        return ContentValues().apply {
            put(CalendarContract.Events.DTSTART, dtStart)
            put(CalendarContract.Events.DTEND, dtEnd)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description?.trim()?.ifEmpty { null } ?: "Created via d.o.it Agenda Widget")
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (isAllDay) "UTC" else TimeZone.getDefault().id)
            put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
            put(CalendarContract.Events.EVENT_COLOR, colorInt)
            if (!location.isNullOrBlank()) {
                put(CalendarContract.Events.EVENT_LOCATION, location.trim())
            } else {
                putNull(CalendarContract.Events.EVENT_LOCATION)
            }
        }
    }

    fun insertEvent(
        title: String,
        location: String?,
        description: String?,
        startMillis: Long,
        endMillis: Long,
        isAllDay: Boolean,
        colorHex: String,
        targetCalendarId: Long?,
        preferredAccountName: String?
    ): Pair<Long, Long>? {
        val calendarId = targetCalendarId ?: findPrimaryCalendarId() ?: ensureLocalCalendarExists(preferredAccountName) ?: return null
        return try {
            val values = buildEventContentValues(
                title = title,
                location = location,
                description = description,
                startMillis = startMillis,
                endMillis = endMillis,
                isAllDay = isAllDay,
                colorHex = colorHex,
                calendarId = calendarId
            )
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val insertedId = uri?.let { ContentUris.parseId(it) } ?: return null
            insertedId to calendarId
        } catch (e: SecurityException) {
            AppLogger.w(AppLogger.TAG_REPO, "WRITE_CALENDAR denied writing event to provider", e)
            null
        } catch (e: Exception) {
            AppLogger.e(AppLogger.TAG_REPO, "Failed inserting event into CalendarContract", e)
            null
        }
    }

    fun updateEvent(
        parentEventId: Long,
        title: String,
        location: String?,
        description: String?,
        startMillis: Long,
        endMillis: Long,
        isAllDay: Boolean,
        colorHex: String,
        calendarId: Long
    ) {
        try {
            val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, parentEventId)
            val values = buildEventContentValues(
                title = title,
                location = location,
                description = description,
                startMillis = startMillis,
                endMillis = endMillis,
                isAllDay = isAllDay,
                colorHex = colorHex,
                calendarId = if (calendarId > 0L) calendarId else 1L
            )
            context.contentResolver.update(updateUri, values, null, null)
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_REPO, "Failed updating event id=$parentEventId in CalendarContract", e)
        }
    }

    fun deleteEvent(parentEventId: Long) {
        try {
            val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, parentEventId)
            context.contentResolver.delete(deleteUri, null, null)
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_REPO, "Failed deleting event id=$parentEventId from CalendarContract", e)
        }
    }
}
