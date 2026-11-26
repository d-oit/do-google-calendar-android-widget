package com.example.util

import android.content.Context
import android.text.format.DateFormat
import com.example.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Centralized, locale-aware date & time formatting and day-boundary utilities.
 * Respects device locale skeletons and 12h/24h system time preferences.
 */
object CalendarDateFormatter {
    const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    const val CANONICAL_TODAY = "TODAY"
    const val CANONICAL_TOMORROW = "TOMORROW"

    fun getStartOfTodayMillis(referenceMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = referenceMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getHorizonEndMillis(daysHorizon: Int, referenceMillis: Long = System.currentTimeMillis()): Long {
        val safeDays = daysHorizon.coerceIn(1, 60)
        return getStartOfTodayMillis(referenceMillis) + (safeDays.toLong() * DAY_MILLIS)
    }

    fun isSameLocalDay(firstMillis: Long, secondMillis: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = firstMillis }
        val c2 = Calendar.getInstance().apply { timeInMillis = secondMillis }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    fun isToday(millis: Long, referenceMillis: Long = System.currentTimeMillis()): Boolean {
        return isSameLocalDay(millis, referenceMillis)
    }

    fun isTomorrow(millis: Long, referenceMillis: Long = System.currentTimeMillis()): Boolean {
        val tomorrowRef = Calendar.getInstance().apply {
            timeInMillis = referenceMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return isSameLocalDay(millis, tomorrowRef)
    }

    fun formatDayTag(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d%02d%02d", year, month, day)
    }

    /**
     * Canonical day header key stored in Room cache (`TODAY`, `TOMORROW`, or formatted date).
     * UI and widgets should prefer [formatLocalizedDayHeader] for display.
     */
    fun computeCanonicalDayHeader(millis: Long, referenceMillis: Long = System.currentTimeMillis()): String {
        return when {
            isToday(millis, referenceMillis) -> CANONICAL_TODAY
            isTomorrow(millis, referenceMillis) -> CANONICAL_TOMORROW
            else -> {
                val locale = Locale.getDefault()
                val pattern = bestPattern(locale, "EEEMMMd", "EEE, MMM d")
                SimpleDateFormat(pattern, locale).format(Date(millis)).uppercase(locale)
            }
        }
    }

    /**
     * Localized day group header for Compose UI and Home Screen Widgets.
     */
    fun formatLocalizedDayHeader(
        context: Context,
        startMillis: Long,
        referenceMillis: Long = System.currentTimeMillis()
    ): String {
        val locale = Locale.getDefault()
        return when {
            isToday(startMillis, referenceMillis) ->
                context.getString(R.string.day_header_today).uppercase(locale)
            isTomorrow(startMillis, referenceMillis) ->
                context.getString(R.string.day_header_tomorrow).uppercase(locale)
            else -> {
                val pattern = bestPattern(locale, "EEEMMMd", "EEE, MMM d")
                SimpleDateFormat(pattern, locale).format(Date(startMillis)).uppercase(locale)
            }
        }
    }

    fun formatWidgetHeaderDate(isNarrow: Boolean, nowMillis: Long = System.currentTimeMillis()): String {
        val locale = Locale.getDefault()
        val pattern = if (isNarrow) {
            bestPattern(locale, "EEEMMMd", "EEE, MMM d")
        } else {
            bestPattern(locale, "EEEEMMMd", "EEEE, MMM d")
        }
        return SimpleDateFormat(pattern, locale).format(Date(nowMillis))
    }

    fun formatFullDate(millis: Long): String {
        val locale = Locale.getDefault()
        val pattern = bestPattern(locale, "EEEMMMdyyyy", "EEE, MMM d, yyyy")
        return SimpleDateFormat(pattern, locale).format(Date(millis))
    }

    fun formatTime(millis: Long, is24Hour: Boolean = false): String {
        val locale = Locale.getDefault()
        val skeleton = if (is24Hour) "Hm" else "hm"
        val fallback = if (is24Hour) "HH:mm" else "h:mm a"
        val pattern = bestPattern(locale, skeleton, fallback)
        return SimpleDateFormat(pattern, locale).format(Date(millis))
    }

    fun formatTime(context: Context, millis: Long): String {
        val is24Hour = runCatching { DateFormat.is24HourFormat(context) }.getOrDefault(false)
        return formatTime(millis, is24Hour)
    }

    fun formatEventTime(
        startMillis: Long,
        endMillis: Long,
        isAllDay: Boolean,
        context: Context? = null
    ): String {
        if (isAllDay) {
            return context?.getString(R.string.event_all_day_label) ?: "All day"
        }
        val is24Hour = context?.let { runCatching { DateFormat.is24HourFormat(it) }.getOrDefault(false) } ?: false
        val startStr = formatTime(startMillis, is24Hour)
        val endStr = formatTime(endMillis, is24Hour)
        return "$startStr – $endStr"
    }

    fun formatFullDateTime(
        startMillis: Long,
        endMillis: Long,
        isAllDay: Boolean,
        context: Context? = null
    ): String {
        val dateStr = formatFullDate(startMillis)
        val timeStr = formatEventTime(startMillis, endMillis, isAllDay, context)
        return "$dateStr • $timeStr"
    }

    fun formatLastSyncIndicator(
        context: Context,
        lastSyncMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        compact: Boolean = false
    ): String {
        if (lastSyncMillis <= 0L) {
            return context.getString(R.string.widget_sync_never)
        }
        val elapsedMillis = (nowMillis - lastSyncMillis).coerceAtLeast(0L)
        if (elapsedMillis < 60_000L && !compact) {
            return context.getString(R.string.widget_sync_just_now)
        }
        val is24Hour = runCatching { DateFormat.is24HourFormat(context) }.getOrDefault(false)
        val locale = Locale.getDefault()
        val formattedTime = if (isSameLocalDay(nowMillis, lastSyncMillis)) {
            formatTime(lastSyncMillis, is24Hour)
        } else {
            val skeleton = if (is24Hour) "MMMdHm" else "MMMdhm"
            val fallback = if (is24Hour) "MMM d, HH:mm" else "MMM d, h:mm a"
            SimpleDateFormat(bestPattern(locale, skeleton, fallback), locale).format(Date(lastSyncMillis))
        }
        return if (compact) {
            formattedTime
        } else {
            context.getString(R.string.widget_sync_time_prefix, formattedTime)
        }
    }

    fun utcAllDayToLocalMillis(utcMillis: Long): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        return Calendar.getInstance().apply {
            set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun localMillisToUtcAllDay(localMillis: Long): Long {
        val localCal = Calendar.getInstance().apply {
            timeInMillis = localMillis
        }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(
                localCal.get(Calendar.YEAR),
                localCal.get(Calendar.MONTH),
                localCal.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun bestPattern(locale: Locale, skeleton: String, fallback: String): String {
        return try {
            val resolved = DateFormat.getBestDateTimePattern(locale, skeleton)
            if (resolved.isNullOrBlank()) fallback else resolved
        } catch (_: RuntimeException) {
            fallback
        }
    }
}
