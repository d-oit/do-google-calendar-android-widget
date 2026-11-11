package com.example.util

import android.content.ContentResolver
import android.database.Cursor
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.example.data.model.EventEntity
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Data class representing the raw color properties read from Android's CalendarContract.
 * Encompasses event-specific colors, calendar-level colors, display colors, and color keys.
 */
data class CalendarEventColorProperties(
    val eventColor: Int? = null,
    val calendarColor: Int? = null,
    val displayColor: Int? = null,
    val eventColorKey: String? = null,
    val calendarColorKey: String? = null,
    val calendarDisplayName: String? = null
)

/**
 * UI Color Scheme mapped from a calendar event's color.
 * Tailored for applying harmonious, high-contrast, accessible colors
 * across all individual UI elements in the event list (cards, badges, stripes, times, icons).
 */
data class EventUiColorScheme(
    val rawHex: String,
    val primaryAccent: Color,
    val containerBackground: Color,
    val cardBorder: Color,
    val badgeBackground: Color,
    val badgeBorder: Color,
    val badgeText: Color,
    val badgeDot: Color,
    val timeText: Color,
    val timeIconTint: Color,
    val locationIconTint: Color,
    val titleColor: Color,
    val onAccentColor: Color,
    val isDarkColor: Boolean
)

/**
 * Mapping utility that reads calendar event color properties from CalendarContract
 * and maps them to semantic UI elements in the event list and widgets.
 *
 * Implements:
 * 1. Extraction of EVENT_COLOR, CALENDAR_COLOR, DISPLAY_COLOR, and EVENT_COLOR_KEY.
 * 2. Lookup against CalendarContract.Colors table or standard Google Calendar event palettes.
 * 3. Heuristic calendar category color mapping.
 * 4. Production of complete [EventUiColorScheme] for Compose event list items.
 */
object CalendarColorMapper {

    const val DEFAULT_COLOR_HEX = "#1A73E8"
    val DEFAULT_COLOR_INT = AndroidColor.parseColor(DEFAULT_COLOR_HEX)

    /**
     * Official Google Calendar 11 standard event palette colors (keys "1" through "11").
     */
    val GOOGLE_EVENT_COLOR_MAP: Map<String, String> = mapOf(
        "1" to "#7986CB",  // Lavender
        "2" to "#33B679",  // Sage
        "3" to "#8E24AA",  // Grape
        "4" to "#E67C73",  // Flamingo
        "5" to "#F6BF26",  // Banana
        "6" to "#F4511E",  // Tangerine
        "7" to "#039BE5",  // Peacock
        "8" to "#616161",  // Graphite
        "9" to "#3F51B5",  // Blueberry
        "10" to "#0B8043", // Basil
        "11" to "#D50000"  // Tomato
    )

    /**
     * Standard Google Calendar sub-calendar palette colors.
     */
    private val DEFAULT_CALENDAR_PALETTE = listOf(
        "#1A73E8", // Blue
        "#33B679", // Sage
        "#8E24AA", // Grape
        "#E67C73", // Flamingo
        "#F6BF26", // Banana
        "#F4511E", // Tangerine
        "#039BE5", // Peacock
        "#3F51B5", // Blueberry
        "#0B8043", // Basil
        "#D50000", // Tomato
        "#7986CB"  // Lavender
    )

    // In-memory cache for CalendarContract.Colors lookups (colorKey -> ARGB int)
    private val colorTableCache = ConcurrentHashMap<String, Int>()
    @Volatile
    private var isColorTableLoaded = false

    /**
     * Resolves the final hex color for an event given its CalendarContract properties.
     * Evaluates in strict priority order:
     * 1. eventColor (explicit event-level color chosen by user)
     * 2. eventColorKey (color key mapped via CalendarContract.Colors or Google palette)
     * 3. displayColor (CalendarContract.EventsColumns.DISPLAY_COLOR)
     * 4. calendarColor (parent calendar color)
     * 5. calendarColorKey (calendar color key)
     * 6. calendarDisplayName (semantic category match or hash)
     */
    fun resolveEventColor(
        properties: CalendarEventColorProperties,
        contentResolver: ContentResolver? = null
    ): String {
        // 1. Direct eventColor if non-zero
        val eventColor = properties.eventColor
        if (eventColor != null && eventColor != 0) {
            return colorIntToHex(eventColor)
        }

        // 2. Event color key lookup
        val eventKey = properties.eventColorKey
        if (!eventKey.isNullOrBlank()) {
            val fromTable = getColorFromTable(eventKey, contentResolver)
            if (fromTable != null && fromTable != 0) {
                return colorIntToHex(fromTable)
            }
            val fromGoogleMap = GOOGLE_EVENT_COLOR_MAP[eventKey]
            if (fromGoogleMap != null) {
                return fromGoogleMap
            }
        }

        // 3. DISPLAY_COLOR if present and non-zero
        val displayColor = properties.displayColor
        if (displayColor != null && displayColor != 0) {
            return colorIntToHex(displayColor)
        }

        // 4. CALENDAR_COLOR if present and non-zero
        val calColor = properties.calendarColor
        if (calColor != null && calColor != 0) {
            return colorIntToHex(calColor)
        }

        // 5. Calendar color key lookup
        val calKey = properties.calendarColorKey
        if (!calKey.isNullOrBlank()) {
            val fromTable = getColorFromTable(calKey, contentResolver)
            if (fromTable != null && fromTable != 0) {
                return colorIntToHex(fromTable)
            }
        }

        // 6. Semantic category matching or hashing by calendar name
        val calName = properties.calendarDisplayName ?: ""
        return resolveCalendarColor(calName, 0, null, contentResolver)
    }

    /**
     * Resolves color properties from an active ContentResolver Cursor.
     */
    fun resolveFromCursor(
        cursor: Cursor,
        eventColorCol: Int = -1,
        calColorCol: Int = -1,
        displayColorCol: Int = -1,
        eventColorKeyCol: Int = -1,
        calNameCol: Int = -1,
        contentResolver: ContentResolver? = null
    ): String {
        val eventColor = if (eventColorCol != -1 && !cursor.isNull(eventColorCol)) cursor.getInt(eventColorCol) else null
        val calColor = if (calColorCol != -1 && !cursor.isNull(calColorCol)) cursor.getInt(calColorCol) else null
        val displayColor = if (displayColorCol != -1 && !cursor.isNull(displayColorCol)) cursor.getInt(displayColorCol) else null
        val eventColorKey = if (eventColorKeyCol != -1 && !cursor.isNull(eventColorKeyCol)) cursor.getString(eventColorKeyCol) else null
        val calName = if (calNameCol != -1 && !cursor.isNull(calNameCol)) cursor.getString(calNameCol) else null

        return resolveEventColor(
            CalendarEventColorProperties(
                eventColor = eventColor,
                calendarColor = calColor,
                displayColor = displayColor,
                eventColorKey = eventColorKey,
                calendarDisplayName = calName
            ),
            contentResolver
        )
    }

    /**
     * Maps calendar display name and raw color int to a hex color string.
     */
    fun resolveCalendarColor(
        calName: String?,
        rawColorInt: Int?,
        colorKey: String? = null,
        contentResolver: ContentResolver? = null
    ): String {
        if (rawColorInt != null && rawColorInt != 0) {
            return colorIntToHex(rawColorInt)
        }
        if (!colorKey.isNullOrBlank()) {
            val fromTable = getColorFromTable(colorKey, contentResolver)
            if (fromTable != null && fromTable != 0) {
                return colorIntToHex(fromTable)
            }
        }
        val safeName = (calName ?: "").trim()
        val lower = safeName.lowercase(Locale.getDefault())

        return when {
            lower.contains("work") || lower.contains("office") || lower.contains("corp") -> "#1A73E8"
            lower.contains("personal") || lower.contains("home") || lower.contains("life") -> "#33B679"
            lower.contains("family") || lower.contains("kids") -> "#8E24AA"
            lower.contains("holiday") || lower.contains("vacation") -> "#0B8043"
            lower.contains("birthday") || lower.contains("anniversary") -> "#F6BF26"
            lower.contains("task") || lower.contains("reminder") || lower.contains("todo") -> "#039BE5"
            lower.contains("health") || lower.contains("fitness") || lower.contains("gym") -> "#E67C73"
            safeName.isNotEmpty() -> {
                val idx = kotlin.math.abs(safeName.hashCode()) % DEFAULT_CALENDAR_PALETTE.size
                DEFAULT_CALENDAR_PALETTE[idx]
            }
            else -> DEFAULT_COLOR_HEX
        }
    }

    /**
     * Converts a 32-bit ARGB or RGB color integer into a `#RRGGBB` hex string.
     */
    fun colorIntToHex(colorInt: Int): String {
        return String.format("#%06X", 0xFFFFFF and colorInt)
    }

    /**
     * Safely parses a hex color string into a Jetpack Compose [Color].
     */
    fun parseColorHex(hex: String?, fallbackHex: String = DEFAULT_COLOR_HEX): Color {
        return try {
            val cleanHex = sanitizeHex(hex, fallbackHex)
            Color(AndroidColor.parseColor(cleanHex))
        } catch (_: Throwable) {
            try {
                Color(AndroidColor.parseColor(fallbackHex))
            } catch (_: Throwable) {
                Color(0xFF1A73E8)
            }
        }
    }

    /**
     * Safely parses a hex color string into an Android [Int] color for RemoteViews.
     */
    fun parseColorInt(hex: String?, fallbackColorInt: Int = DEFAULT_COLOR_INT): Int {
        return try {
            val cleanHex = sanitizeHex(hex, DEFAULT_COLOR_HEX)
            AndroidColor.parseColor(cleanHex)
        } catch (_: Throwable) {
            fallbackColorInt
        }
    }

    private fun sanitizeHex(hex: String?, fallback: String): String {
        if (hex.isNullOrBlank()) return fallback
        val trimmed = hex.trim()
        val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
        return if (withHash.length == 7 || withHash.length == 9) withHash else fallback
    }

    /**
     * Queries Android's CalendarContract.Colors table to cache color keys to ARGB integers.
     */
    fun loadColorsFromProvider(contentResolver: ContentResolver?): Map<String, Int> {
        if (contentResolver == null) return colorTableCache
        if (isColorTableLoaded) return colorTableCache

        try {
            val projection = arrayOf(
                CalendarContract.Colors.COLOR_KEY,
                CalendarContract.Colors.COLOR
            )
            contentResolver.query(
                CalendarContract.Colors.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val keyIdx = cursor.getColumnIndex(CalendarContract.Colors.COLOR_KEY)
                val colorIdx = cursor.getColumnIndex(CalendarContract.Colors.COLOR)

                while (cursor.moveToNext()) {
                    val key = if (keyIdx != -1) cursor.getString(keyIdx) else null
                    val color = if (colorIdx != -1) cursor.getInt(colorIdx) else 0
                    if (!key.isNullOrBlank() && color != 0) {
                        colorTableCache[key] = color
                    }
                }
                isColorTableLoaded = true
            }
        } catch (_: Throwable) {
            // Colors table query may fail if permission is missing or table is unpopulated
        }
        return colorTableCache
    }

    private fun getColorFromTable(key: String, contentResolver: ContentResolver?): Int? {
        colorTableCache[key]?.let { return it }
        if (!isColorTableLoaded && contentResolver != null) {
            loadColorsFromProvider(contentResolver)
            return colorTableCache[key]
        }
        return null
    }

    /**
     * Computes the relative luminance of a color according to ITU-R BT.709.
     * Returns a float in [0.0, 1.0] where >= 0.5 is considered light/bright.
     */
    fun calculateLuminance(color: Color): Float {
        return (0.299f * color.red) + (0.587f * color.green) + (0.114f * color.blue)
    }

    /**
     * Builds a comprehensive [EventUiColorScheme] that coordinates the styling of all UI
     * elements in an event item card (accent stripe, container background, badge, time, text).
     *
     * Ensures accessible contrast ratios across both light and dark themes.
     */
    fun getUiColorScheme(colorHex: String?, isDarkTheme: Boolean = false): EventUiColorScheme {
        val safeHex = sanitizeHex(colorHex, DEFAULT_COLOR_HEX)
        val accent = parseColorHex(safeHex)
        val luminance = calculateLuminance(accent)
        val isDark = luminance < 0.5f

        val containerBackground = if (isDarkTheme) {
            // Subtle tinted dark surface
            Color(0xFF1E2024).blendWith(accent, 0.08f)
        } else {
            // Subtle tinted light surface
            Color.White.blendWith(accent, 0.04f)
        }

        val cardBorder = if (isDarkTheme) {
            accent.copy(alpha = 0.35f)
        } else {
            accent.copy(alpha = 0.24f)
        }

        val badgeBackground = accent.copy(alpha = if (isDarkTheme) 0.22f else 0.12f)
        val badgeBorder = accent.copy(alpha = if (isDarkTheme) 0.45f else 0.35f)

        // Badge and Time text: ensure sufficient contrast against container
        val badgeText = if (isDarkTheme) {
            if (luminance < 0.35f) accent.lighten(0.35f) else accent
        } else {
            if (luminance > 0.65f) accent.darken(0.30f) else accent
        }

        val timeText = if (isDarkTheme) {
            if (luminance < 0.35f) accent.lighten(0.30f) else accent
        } else {
            if (luminance > 0.65f) accent.darken(0.25f) else accent
        }

        val titleColor = if (isDarkTheme) Color(0xFFF1F3F4) else Color(0xFF1F1F1F)
        val locationIconTint = if (isDarkTheme) Color(0xFF9AA0A6) else Color(0xFF5F6368)
        val onAccentColor = if (luminance > 0.5f) Color(0xFF202124) else Color.White

        return EventUiColorScheme(
            rawHex = safeHex,
            primaryAccent = accent,
            containerBackground = containerBackground,
            cardBorder = cardBorder,
            badgeBackground = badgeBackground,
            badgeBorder = badgeBorder,
            badgeText = badgeText,
            badgeDot = accent,
            timeText = timeText,
            timeIconTint = accent,
            locationIconTint = locationIconTint,
            titleColor = titleColor,
            onAccentColor = onAccentColor,
            isDarkColor = isDark
        )
    }

    /**
     * Blends this color with an overlay color using linear interpolation.
     */
    private fun Color.blendWith(overlay: Color, ratio: Float): Color {
        val r = (red * (1f - ratio)) + (overlay.red * ratio)
        val g = (green * (1f - ratio)) + (overlay.green * ratio)
        val b = (blue * (1f - ratio)) + (overlay.blue * ratio)
        return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), 1f)
    }

    /**
     * Lightens the color by increasing RGB values towards 1.0f.
     */
    private fun Color.lighten(fraction: Float): Color {
        val r = red + (1f - red) * fraction
        val g = green + (1f - green) * fraction
        val b = blue + (1f - blue) * fraction
        return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), alpha)
    }

    /**
     * Darkens the color by scaling RGB values down towards 0.0f.
     */
    private fun Color.darken(fraction: Float): Color {
        val factor = (1f - fraction).coerceAtLeast(0f)
        return Color(red * factor, green * factor, blue * factor, alpha)
    }
}

/**
 * Convenience Composable function that retrieves and remembers the [EventUiColorScheme]
 * for a given [EventEntity] based on current system theme.
 */
@Composable
fun rememberEventColorScheme(event: EventEntity): EventUiColorScheme {
    val isDark = isSystemInDarkTheme()
    return remember(event.colorHex, isDark) {
        CalendarColorMapper.getUiColorScheme(event.colorHex, isDark)
    }
}
