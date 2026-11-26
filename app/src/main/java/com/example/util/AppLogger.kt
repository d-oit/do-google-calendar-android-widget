package com.example.util

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.BuildConfig
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Scrubs personally identifiable information (email addresses, URL paths/query parameters,
 * and credential-like tokens) from logs and exported diagnostic reports.
 */
object LogSanitizer {
    private val EMAIL_REGEX = Regex("""([A-Za-z0-9._%+-])([A-Za-z0-9._%+-]*)(@[A-Za-z0-9.-]+\.[A-Za-z]{2,})""")
    private val URL_REGEX = Regex("""(https?://)([^/\s]+)([^\s]*)""", RegexOption.IGNORE_CASE)
    private val BEARER_HEADER_REGEX = Regex("""(?i)\b(bearer)\s+[A-Za-z0-9._~+/=-]{8,}""")
    private val SECRET_KV_REGEX = Regex("""(?i)\b(token|api[_-]?key|secret)\s*[:=]\s*[^\s,;]+""")

    fun redactEmail(email: String): String {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return trimmed
        val atIdx = trimmed.indexOf('@')
        if (atIdx <= 0) return trimmed
        val local = trimmed.substring(0, atIdx)
        val domain = trimmed.substring(atIdx)
        return "${local.first()}***$domain"
    }

    fun sanitize(input: String): String {
        if (input.isEmpty()) return input
        var sanitized = EMAIL_REGEX.replace(input) { match ->
            val firstChar = match.groupValues[1]
            val domain = match.groupValues[3]
            "$firstChar***$domain"
        }
        sanitized = URL_REGEX.replace(sanitized) { match ->
            val scheme = match.groupValues[1]
            val host = match.groupValues[2]
            "$scheme$host/[redacted]"
        }
        sanitized = BEARER_HEADER_REGEX.replace(sanitized) { match ->
            "${match.groupValues[1]} [redacted]"
        }
        sanitized = SECRET_KV_REGEX.replace(sanitized) { match ->
            "${match.groupValues[1]}=[redacted]"
        }
        return sanitized
    }
}

/**
 * Lightweight structured logging wrapper around [android.util.Log] with:
 * - [BuildConfig.DEBUG] gating for debug/info logs and stack trace verbosity
 * - Automatic PII sanitization via [LogSanitizer]
 * - Bounded thread-safe in-memory ring buffer for Sync Diagnostics inspection
 * - Pure JVM unit-test safety when [android.util.Log] is not shadowed
 */
object AppLogger {
    const val TAG_APP = "AgendaApp"
    const val TAG_REPO = "CalendarRepo"
    const val TAG_VM = "CalendarVM"
    const val TAG_WIDGET = "AgendaWidget"
    const val TAG_COLOR = "CalendarColor"
    const val TAG_PREFS = "WidgetPrefs"
    const val TAG_UI = "AgendaUI"
    const val TAG_SYNC = "CalendarSync"
    const val TAG_UPDATE = "AppUpdate"

    private const val MAX_RING_BUFFER_SIZE = 120

    enum class Level(val shortCode: String) {
        DEBUG("D"),
        INFO("I"),
        WARN("W"),
        ERROR("E")
    }

    data class LogEntry(
        val timestampMillis: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val errorType: String? = null
    ) {
        fun formatLine(): String {
            val ts = LOG_TIME_FORMAT.get()?.format(Date(timestampMillis)) ?: timestampMillis.toString()
            val errSuffix = errorType?.let { " [$it]" } ?: ""
            return "$ts ${level.shortCode}/$tag: $message$errSuffix"
        }
    }

    private val LOG_TIME_FORMAT = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    private val ringBufferLock = Any()
    private val ringBuffer = ArrayDeque<LogEntry>(MAX_RING_BUFFER_SIZE)

    @Volatile
    @VisibleForTesting
    var testLogSink: ((level: Level, tag: String, message: String, throwable: Throwable?) -> Unit)? = null

    fun getRecentLogs(limit: Int = MAX_RING_BUFFER_SIZE): List<LogEntry> {
        synchronized(ringBufferLock) {
            if (ringBuffer.isEmpty()) return emptyList()
            val snapshot = ringBuffer.toList()
            return if (limit >= snapshot.size) snapshot else snapshot.takeLast(limit.coerceAtLeast(0))
        }
    }

    fun formatRecentLogs(limit: Int = 40): List<String> {
        return getRecentLogs(limit).map { it.formatLine() }
    }

    fun clearRecentLogs() {
        synchronized(ringBufferLock) {
            ringBuffer.clear()
        }
    }

    private fun recordEntry(level: Level, tag: String, sanitizedMessage: String, throwable: Throwable?) {
        synchronized(ringBufferLock) {
            if (ringBuffer.size >= MAX_RING_BUFFER_SIZE) {
                ringBuffer.removeFirst()
            }
            ringBuffer.addLast(
                LogEntry(
                    timestampMillis = System.currentTimeMillis(),
                    level = level,
                    tag = tag,
                    message = sanitizedMessage,
                    errorType = throwable?.javaClass?.simpleName
                )
            )
        }
    }

    fun d(tag: String, message: String) {
        val safeMessage = LogSanitizer.sanitize(message)
        recordEntry(Level.DEBUG, tag, safeMessage, null)
        testLogSink?.invoke(Level.DEBUG, tag, safeMessage, null)
        if (!BuildConfig.DEBUG) return
        try {
            Log.d(tag, safeMessage)
        } catch (_: RuntimeException) {
            // Pure JVM unit test without Android Log shadow
        }
    }

    fun i(tag: String, message: String) {
        val safeMessage = LogSanitizer.sanitize(message)
        recordEntry(Level.INFO, tag, safeMessage, null)
        testLogSink?.invoke(Level.INFO, tag, safeMessage, null)
        if (!BuildConfig.DEBUG) return
        try {
            Log.i(tag, safeMessage)
        } catch (_: RuntimeException) {
            // Pure JVM unit test without Android Log shadow
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val safeMessage = LogSanitizer.sanitize(message)
        recordEntry(Level.WARN, tag, safeMessage, throwable)
        testLogSink?.invoke(Level.WARN, tag, safeMessage, throwable)
        try {
            if (throwable != null && BuildConfig.DEBUG) {
                Log.w(tag, safeMessage, throwable)
            } else if (throwable != null) {
                Log.w(tag, "$safeMessage (${throwable.javaClass.simpleName})")
            } else {
                Log.w(tag, safeMessage)
            }
        } catch (_: RuntimeException) {
            System.err.println("W/$tag: $safeMessage${throwable?.let { " (${it.javaClass.simpleName})" } ?: ""}")
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val safeMessage = LogSanitizer.sanitize(message)
        recordEntry(Level.ERROR, tag, safeMessage, throwable)
        testLogSink?.invoke(Level.ERROR, tag, safeMessage, throwable)
        try {
            if (throwable != null && BuildConfig.DEBUG) {
                Log.e(tag, safeMessage, throwable)
            } else if (throwable != null) {
                Log.e(tag, "$safeMessage (${throwable.javaClass.simpleName})")
            } else {
                Log.e(tag, safeMessage)
            }
        } catch (_: RuntimeException) {
            System.err.println("E/$tag: $safeMessage${throwable?.let { " (${it.javaClass.simpleName})" } ?: ""}")
        }
    }
}
