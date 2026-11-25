package com.example.util

import android.util.Log
import com.example.BuildConfig

/**
 * Lightweight structured logging wrapper around [android.util.Log].
 *
 * - Debug and info logs are gated behind [BuildConfig.DEBUG].
 * - Warnings and errors always log with standardized component tags.
 * - Safe in pure JVM unit tests where [android.util.Log] may not be mocked.
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

    enum class Level {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    @Volatile
    var testLogSink: ((level: Level, tag: String, message: String, throwable: Throwable?) -> Unit)? = null

    fun d(tag: String, message: String) {
        testLogSink?.invoke(Level.DEBUG, tag, message, null)
        if (!BuildConfig.DEBUG) return
        try {
            Log.d(tag, message)
        } catch (_: RuntimeException) {
            // Pure JVM unit test without Android Log shadow
        }
    }

    fun i(tag: String, message: String) {
        testLogSink?.invoke(Level.INFO, tag, message, null)
        if (!BuildConfig.DEBUG) return
        try {
            Log.i(tag, message)
        } catch (_: RuntimeException) {
            // Pure JVM unit test without Android Log shadow
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        testLogSink?.invoke(Level.WARN, tag, message, throwable)
        try {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        } catch (_: RuntimeException) {
            System.err.println("W/$tag: $message${throwable?.let { " (${it.message})" } ?: ""}")
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        testLogSink?.invoke(Level.ERROR, tag, message, throwable)
        try {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        } catch (_: RuntimeException) {
            System.err.println("E/$tag: $message${throwable?.let { " (${it.message})" } ?: ""}")
        }
    }
}
