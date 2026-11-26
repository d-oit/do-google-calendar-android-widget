package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.sqlite.SQLiteException
import android.provider.CalendarContract
import com.example.data.model.EventEntity
import com.example.util.AppLogger
import com.example.util.CalendarColorMapper
import com.example.util.CalendarDateFormatter
import com.example.util.CalendarEventColorProperties

/**
 * Encapsulates read queries against Android's native [CalendarContract] provider
 * (`Calendars`, `Instances`, and fallback `Events`) with extended-to-core projection fallback.
 */
internal class CalendarProviderDataSource(
    private val context: Context,
    private val onQueryError: (String) -> Unit
) {

    fun queryAvailableCalendars(): List<CalendarAccountInfo> {
        val list = mutableListOf<CalendarAccountInfo>()
        try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.CALENDAR_COLOR_KEY,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.SYNC_EVENTS
            )

            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val typeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val displayIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val primaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                val ownerIdx = cursor.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT)
                val colorIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
                val colorKeyIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR_KEY)
                val accessIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
                val visibleIdx = cursor.getColumnIndex(CalendarContract.Calendars.VISIBLE)
                val syncEventsIdx = cursor.getColumnIndex(CalendarContract.Calendars.SYNC_EVENTS)

                while (cursor.moveToNext()) {
                    val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                    val accountName = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else ""
                    val accountType = if (typeIdx != -1) cursor.getString(typeIdx) ?: "" else ""
                    val displayName = if (displayIdx != -1) cursor.getString(displayIdx) ?: accountName else accountName
                    val isPrimary = if (primaryIdx != -1) cursor.getInt(primaryIdx) == 1 else false
                    val owner = if (ownerIdx != -1) cursor.getString(ownerIdx) ?: "" else ""
                    val color = if (colorIdx != -1) cursor.getInt(colorIdx) else 0
                    val colorKey = if (colorKeyIdx != -1) cursor.getString(colorKeyIdx) else null
                    val accessLevel = if (accessIdx != -1) cursor.getInt(accessIdx) else CalendarContract.Calendars.CAL_ACCESS_OWNER
                    val isWritable = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                    val isVisible = if (visibleIdx != -1 && !cursor.isNull(visibleIdx)) cursor.getInt(visibleIdx) != 0 else true
                    val isSyncEventsEnabled = if (syncEventsIdx != -1 && !cursor.isNull(syncEventsIdx)) cursor.getInt(syncEventsIdx) != 0 else true
                    val hex = CalendarColorMapper.resolveCalendarColor(displayName, color, colorKey, context.contentResolver)

                    list.add(
                        CalendarAccountInfo(
                            id = id,
                            accountName = accountName.ifBlank { owner },
                            displayName = displayName,
                            accountType = accountType,
                            isPrimary = isPrimary,
                            color = color,
                            colorHex = hex,
                            isWritable = isWritable,
                            isVisible = isVisible,
                            isSyncEventsEnabled = isSyncEventsEnabled
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            onQueryError("Permission denied querying calendars: ${e.message}")
            AppLogger.w(AppLogger.TAG_REPO, "Permission denied querying CalendarContract.Calendars", e)
        } catch (e: Exception) {
            onQueryError("Calendars query failed: ${e.message ?: e.javaClass.simpleName}")
            AppLogger.e(AppLogger.TAG_REPO, "Error querying CalendarContract.Calendars", e)
        }
        return list
    }

    fun queryInstances(
        startMillis: Long,
        endMillis: Long,
        calendarIds: Set<Long>? = null
    ): List<EventEntity> {
        val events = mutableListOf<EventEntity>()
        if (calendarIds != null && calendarIds.isEmpty()) return events

        try {
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startMillis)
            ContentUris.appendId(builder, endMillis)
            val uri = builder.build()

            val fullProjection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.EVENT_COLOR,
                CalendarContract.Instances.CALENDAR_COLOR,
                CalendarContract.Instances.EVENT_COLOR_KEY,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.RRULE,
                CalendarContract.Instances.EVENT_TIMEZONE
            )

            val coreProjection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_COLOR,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.RRULE
            )

            var selection: String? = null
            var selectionArgs: Array<String>? = null

            if (!calendarIds.isNullOrEmpty()) {
                val placeholders = calendarIds.joinToString(",") { "?" }
                selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
                selectionArgs = calendarIds.map { it.toString() }.toTypedArray()
            }

            val cursor = try {
                context.contentResolver.query(
                    uri,
                    fullProjection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Instances.BEGIN} ASC"
                )
            } catch (e: IllegalArgumentException) {
                AppLogger.w(AppLogger.TAG_REPO, "Extended Instances projection rejected; retrying with core projection", e)
                context.contentResolver.query(
                    uri,
                    coreProjection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Instances.BEGIN} ASC"
                )
            } catch (e: SQLiteException) {
                AppLogger.w(AppLogger.TAG_REPO, "SQLiteException on extended Instances projection; retrying with core projection", e)
                context.contentResolver.query(
                    uri,
                    coreProjection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Instances.BEGIN} ASC"
                )
            }

            cursor?.use { c ->
                val idIndex = c.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIndex = c.getColumnIndex(CalendarContract.Instances.TITLE)
                val descIndex = c.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val locIndex = c.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val beginIndex = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIndex = c.getColumnIndex(CalendarContract.Instances.END)
                val allDayIndex = c.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val displayColorIndex = c.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)
                val eventColorIndex = c.getColumnIndex(CalendarContract.Instances.EVENT_COLOR)
                val calColorIndex = c.getColumnIndex(CalendarContract.Instances.CALENDAR_COLOR)
                val eventColorKeyIndex = c.getColumnIndex(CalendarContract.Instances.EVENT_COLOR_KEY)
                val calNameIndex = c.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                val calIdIndex = c.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
                val rruleIndex = c.getColumnIndex(CalendarContract.Instances.RRULE)

                while (c.moveToNext()) {
                    val eventIdLong = if (idIndex != -1 && !c.isNull(idIndex)) c.getLong(idIndex) else null
                    val eventIdStr = eventIdLong?.toString() ?: ""
                    val title = if (titleIndex != -1) c.getString(titleIndex) ?: "(No title)" else "(No title)"
                    val desc = if (descIndex != -1) c.getString(descIndex) else null
                    val loc = if (locIndex != -1) c.getString(locIndex) else null
                    val rawBegin = if (beginIndex != -1) c.getLong(beginIndex) else 0L
                    val rawEnd = if (endIndex != -1) c.getLong(endIndex) else rawBegin + 3600000L
                    val allDay = if (allDayIndex != -1) c.getInt(allDayIndex) == 1 else false
                    val begin = if (allDay) CalendarDateFormatter.utcAllDayToLocalMillis(rawBegin) else rawBegin
                    val end = if (allDay) CalendarDateFormatter.utcAllDayToLocalMillis(rawEnd) else rawEnd
                    val displayColor = if (displayColorIndex != -1 && !c.isNull(displayColorIndex)) c.getInt(displayColorIndex) else null
                    val eventColor = if (eventColorIndex != -1 && !c.isNull(eventColorIndex)) c.getInt(eventColorIndex) else null
                    val calColor = if (calColorIndex != -1 && !c.isNull(calColorIndex)) c.getInt(calColorIndex) else null
                    val eventColorKey = if (eventColorKeyIndex != -1 && !c.isNull(eventColorKeyIndex)) c.getString(eventColorKeyIndex) else null
                    val calName = if (calNameIndex != -1) c.getString(calNameIndex) ?: "Google Calendar" else "Google Calendar"
                    val sourceCalId = if (calIdIndex != -1 && !c.isNull(calIdIndex)) c.getLong(calIdIndex) else -1L
                    val rrule = if (rruleIndex != -1) c.getString(rruleIndex) else null

                    val colorHex = CalendarColorMapper.resolveEventColor(
                        CalendarEventColorProperties(
                            eventColor = eventColor,
                            calendarColor = calColor,
                            displayColor = displayColor,
                            eventColorKey = eventColorKey,
                            calendarDisplayName = calName
                        ),
                        contentResolver = context.contentResolver
                    )

                    events.add(
                        EventEntity(
                            id = "dev_${eventIdStr}_$begin",
                            calendarId = calName,
                            sourceCalendarId = sourceCalId,
                            parentEventId = eventIdLong,
                            title = title,
                            description = desc,
                            location = loc,
                            startMillis = begin,
                            endMillis = if (end <= begin) begin + 3600000L else end,
                            isAllDay = allDay,
                            isRecurring = !rrule.isNullOrBlank(),
                            colorHex = colorHex,
                            htmlLink = if (eventIdLong != null) "content://com.android.calendar/events/$eventIdLong" else null,
                            dayHeader = CalendarDateFormatter.computeCanonicalDayHeader(begin),
                            isFirstOfDay = false
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            onQueryError("Permission denied querying Instances: ${e.message}")
            AppLogger.w(AppLogger.TAG_REPO, "Permission denied querying CalendarContract.Instances", e)
        } catch (e: Exception) {
            onQueryError("Instances query failed: ${e.message ?: e.javaClass.simpleName}")
            AppLogger.e(AppLogger.TAG_REPO, "Error querying CalendarContract.Instances", e)
        }
        return events
    }

    fun fetchDirectEvents(
        startMillis: Long,
        endMillis: Long,
        calendarIds: Set<Long>? = null
    ): List<EventEntity> {
        val events = mutableListOf<EventEntity>()
        if (calendarIds != null && calendarIds.isEmpty()) return events
        try {
            val fullProjection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.DISPLAY_COLOR,
                CalendarContract.Events.EVENT_COLOR,
                CalendarContract.Events.CALENDAR_COLOR,
                CalendarContract.Events.EVENT_COLOR_KEY,
                CalendarContract.Events.CALENDAR_DISPLAY_NAME,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.RRULE
            )

            val coreProjection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.CALENDAR_COLOR,
                CalendarContract.Events.CALENDAR_DISPLAY_NAME,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.RRULE
            )

            val selectionBuilder = StringBuilder(
                "${CalendarContract.Events.DELETED} = 0 AND ${CalendarContract.Events.DTSTART} <= ? AND (${CalendarContract.Events.DTEND} >= ? OR (${CalendarContract.Events.DTEND} IS NULL AND ${CalendarContract.Events.DTSTART} >= ?))"
            )
            val argsList = mutableListOf(endMillis.toString(), startMillis.toString(), startMillis.toString())

            if (!calendarIds.isNullOrEmpty()) {
                val placeholders = calendarIds.joinToString(",") { "?" }
                selectionBuilder.append(" AND ${CalendarContract.Events.CALENDAR_ID} IN ($placeholders)")
                calendarIds.forEach { argsList.add(it.toString()) }
            }

            val cursor = try {
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    fullProjection,
                    selectionBuilder.toString(),
                    argsList.toTypedArray(),
                    "${CalendarContract.Events.DTSTART} ASC"
                )
            } catch (e: IllegalArgumentException) {
                AppLogger.w(AppLogger.TAG_REPO, "Extended Events projection rejected; retrying with core projection", e)
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    coreProjection,
                    selectionBuilder.toString(),
                    argsList.toTypedArray(),
                    "${CalendarContract.Events.DTSTART} ASC"
                )
            } catch (e: SQLiteException) {
                AppLogger.w(AppLogger.TAG_REPO, "SQLiteException on extended Events projection; retrying with core projection", e)
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    coreProjection,
                    selectionBuilder.toString(),
                    argsList.toTypedArray(),
                    "${CalendarContract.Events.DTSTART} ASC"
                )
            }

            cursor?.use { c ->
                val idIdx = c.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
                val descIdx = c.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val locIdx = c.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val startIdx = c.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = c.getColumnIndex(CalendarContract.Events.DTEND)
                val allDayIdx = c.getColumnIndex(CalendarContract.Events.ALL_DAY)
                val displayColorIdx = c.getColumnIndex(CalendarContract.Events.DISPLAY_COLOR)
                val eventColorIdx = c.getColumnIndex(CalendarContract.Events.EVENT_COLOR)
                val calColorIdx = c.getColumnIndex(CalendarContract.Events.CALENDAR_COLOR)
                val eventColorKeyIdx = c.getColumnIndex(CalendarContract.Events.EVENT_COLOR_KEY)
                val calNameIdx = c.getColumnIndex(CalendarContract.Events.CALENDAR_DISPLAY_NAME)
                val calIdIdx = c.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
                val rruleIdx = c.getColumnIndex(CalendarContract.Events.RRULE)

                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val title = if (titleIdx != -1) c.getString(titleIdx) ?: "(No title)" else "(No title)"
                    val desc = if (descIdx != -1) c.getString(descIdx) else null
                    val loc = if (locIdx != -1) c.getString(locIdx) else null
                    val rawStart = if (startIdx != -1) c.getLong(startIdx) else 0L
                    val rawEnd = if (endIdx != -1 && !c.isNull(endIdx)) c.getLong(endIdx) else rawStart + 3600000L
                    val allDay = if (allDayIdx != -1) c.getInt(allDayIdx) == 1 else false
                    val start = if (allDay) CalendarDateFormatter.utcAllDayToLocalMillis(rawStart) else rawStart
                    val end = if (allDay) CalendarDateFormatter.utcAllDayToLocalMillis(rawEnd) else rawEnd
                    val displayColor = if (displayColorIdx != -1 && !c.isNull(displayColorIdx)) c.getInt(displayColorIdx) else null
                    val eventColor = if (eventColorIdx != -1 && !c.isNull(eventColorIdx)) c.getInt(eventColorIdx) else null
                    val calColor = if (calColorIdx != -1 && !c.isNull(calColorIdx)) c.getInt(calColorIdx) else null
                    val eventColorKey = if (eventColorKeyIdx != -1 && !c.isNull(eventColorKeyIdx)) c.getString(eventColorKeyIdx) else null
                    val calName = if (calNameIdx != -1) c.getString(calNameIdx) ?: "Google Calendar" else "Google Calendar"
                    val sourceCalId = if (calIdIdx != -1 && !c.isNull(calIdIdx)) c.getLong(calIdIdx) else -1L
                    val rrule = if (rruleIdx != -1) c.getString(rruleIdx) else null

                    val colorHex = CalendarColorMapper.resolveEventColor(
                        CalendarEventColorProperties(
                            eventColor = eventColor,
                            calendarColor = calColor,
                            displayColor = displayColor,
                            eventColorKey = eventColorKey,
                            calendarDisplayName = calName
                        ),
                        contentResolver = context.contentResolver
                    )

                    events.add(
                        EventEntity(
                            id = "dev_${id}_$start",
                            calendarId = calName,
                            sourceCalendarId = sourceCalId,
                            parentEventId = id,
                            title = title,
                            description = desc,
                            location = loc,
                            startMillis = start,
                            endMillis = if (end <= start) start + 3600000L else end,
                            isAllDay = allDay,
                            isRecurring = !rrule.isNullOrBlank(),
                            colorHex = colorHex,
                            htmlLink = "content://com.android.calendar/events/$id",
                            dayHeader = CalendarDateFormatter.computeCanonicalDayHeader(start),
                            isFirstOfDay = false
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            onQueryError("Permission denied querying Events: ${e.message}")
            AppLogger.w(AppLogger.TAG_REPO, "Permission denied querying CalendarContract.Events", e)
        } catch (e: Exception) {
            onQueryError("Events query failed: ${e.message ?: e.javaClass.simpleName}")
            AppLogger.e(AppLogger.TAG_REPO, "Error querying CalendarContract.Events", e)
        }
        return events
    }
}
