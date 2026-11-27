package com.example.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.example.data.pref.WidgetColorTheme
import com.example.data.pref.WidgetPreferences
import com.example.ui.theme.AgendaDarkColorScheme
import com.example.ui.theme.AgendaLightColorScheme
import com.example.util.AppLogger
import com.example.util.CalendarColorMapper

/**
 * Material 3 light and dark [ColorScheme] pair backing both Jetpack Glance's
 * system-aware [androidx.glance.material3.ColorProviders] and Classic RemoteViews widgets.
 */
data class WidgetMaterial3ColorSchemes(
    val light: ColorScheme,
    val dark: ColorScheme
)

/**
 * Resolved color palette for the Home Screen Widgets (RemoteViews & Glance) and
 * the in-app Live Widget Preview, ensuring 1:1 visual parity across Material 3
 * light/dark schemes, Android 12+ Material You dynamic colors, and opacity levels (0%–100%).
 */
data class ResolvedWidgetTheme(
    val isDark: Boolean,
    val rootBgColor: Int,
    val rootAlpha: Int,
    val rootBorderColor: Int,
    val rootBorderAlpha: Int,
    val itemBgColor: Int,
    val itemAlpha: Int,
    val itemBorderColor: Int,
    val itemBorderAlpha: Int,
    val accentColor: Int,
    val chipBgColor: Int,
    val chipBgAlpha: Int,
    val titleColor: Int,
    val subtitleColor: Int,
    val dividerColor: Int
)

object WidgetThemeHelper {

    private const val MIN_TITLE_CONTRAST = 7.0
    private const val MIN_SUBTITLE_CONTRAST = 4.5
    private const val MIN_ACCENT_TEXT_CONTRAST = 4.5
    private const val MIN_ACCENT_BAR_CONTRAST = 3.0

    private val ClassicBlueLightScheme = lightColorScheme(
        primary = Color(0xFF1A73E8),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE8F0FE),
        onPrimaryContainer = Color(0xFF042B60),
        background = Color(0xFFF4F8FE),
        onBackground = Color(0xFF1F1F1F),
        surface = Color(0xFFF4F8FE),
        onSurface = Color(0xFF1F1F1F),
        surfaceVariant = Color.White,
        onSurfaceVariant = Color(0xFF5F6368),
        outlineVariant = Color(0xFFD2E3FC)
    )

    private val ClassicBlueDarkScheme = darkColorScheme(
        primary = Color(0xFF8AB4F8),
        onPrimary = Color(0xFF062E6F),
        primaryContainer = Color(0xFF1D3557),
        onPrimaryContainer = Color(0xFFD2E3FC),
        background = Color(0xFF111622),
        onBackground = Color(0xFFF1F4FA),
        surface = Color(0xFF111622),
        onSurface = Color(0xFFF1F4FA),
        surfaceVariant = Color(0xFF1C2434),
        onSurfaceVariant = Color(0xFFC2CDDF),
        outlineVariant = Color(0xFF33415A)
    )

    private val EmeraldLightScheme = lightColorScheme(
        primary = Color(0xFF137333),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD8F3DC),
        onPrimaryContainer = Color(0xFF0A3D1D),
        background = Color(0xFFEFFAF3),
        onBackground = Color(0xFF112619),
        surface = Color(0xFFEFFAF3),
        onSurface = Color(0xFF112619),
        surfaceVariant = Color.White,
        onSurfaceVariant = Color(0xFF476351),
        outlineVariant = Color(0xFFB7E4C7)
    )

    private val EmeraldDarkScheme = darkColorScheme(
        primary = Color(0xFF81C995),
        onPrimary = Color(0xFF06381A),
        primaryContainer = Color(0xFF183D27),
        onPrimaryContainer = Color(0xFFC8F5D4),
        background = Color(0xFF101B14),
        onBackground = Color(0xFFEDF7F0),
        surface = Color(0xFF101B14),
        onSurface = Color(0xFFEDF7F0),
        surfaceVariant = Color(0xFF1B2B21),
        onSurfaceVariant = Color(0xFFB8D1C1),
        outlineVariant = Color(0xFF2E4938)
    )

    private val SunsetLightScheme = lightColorScheme(
        primary = Color(0xFFE65100),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE5D0),
        onPrimaryContainer = Color(0xFF4E1B00),
        background = Color(0xFFFFF6EE),
        onBackground = Color(0xFF2B170A),
        surface = Color(0xFFFFF6EE),
        onSurface = Color(0xFF2B170A),
        surfaceVariant = Color.White,
        onSurfaceVariant = Color(0xFF6E4B3A),
        outlineVariant = Color(0xFFFFD2B3)
    )

    private val SunsetDarkScheme = darkColorScheme(
        primary = Color(0xFFFFB77C),
        onPrimary = Color(0xFF4D2100),
        primaryContainer = Color(0xFF512810),
        onPrimaryContainer = Color(0xFFFFE1C6),
        background = Color(0xFF1F1510),
        onBackground = Color(0xFFFDF3EB),
        surface = Color(0xFF1F1510),
        onSurface = Color(0xFFFDF3EB),
        surfaceVariant = Color(0xFF2D2019),
        onSurfaceVariant = Color(0xFFD9C0B2),
        outlineVariant = Color(0xFF533B2E)
    )

    private val DarkSlateScheme = darkColorScheme(
        primary = Color(0xFF90CAF9),
        onPrimary = Color(0xFF093154),
        primaryContainer = Color(0xFF24374A),
        onPrimaryContainer = Color(0xFFD8ECFF),
        background = Color(0xFF181A1E),
        onBackground = Color(0xFFF8F9FA),
        surface = Color(0xFF181A1E),
        onSurface = Color(0xFFF8F9FA),
        surfaceVariant = Color(0xFF25282F),
        onSurfaceVariant = Color(0xFFC2C9D2),
        outlineVariant = Color(0xFF424752)
    )

    /**
     * Returns whether the Android configuration currently requests system Night/Dark mode.
     */
    fun isSystemDarkMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Resolves a Material 3 light/dark [ColorScheme] pair for the requested [colorTheme].
     *
     * - [WidgetColorTheme.DYNAMIC] uses Android 12+ Material You dynamic light/dark schemes when
     *   available, falling back to [AgendaLightColorScheme] / [AgendaDarkColorScheme].
     * - [WidgetColorTheme.CLASSIC_BLUE], [WidgetColorTheme.EMERALD], and [WidgetColorTheme.SUNSET]
     *   all provide dedicated Material 3 light and dark schemes so the widget automatically adapts
     *   when Android switches between Light and Dark mode.
     * - [WidgetColorTheme.DARK_SLATE] uses its high-contrast dark Material 3 scheme regardless of
     *   system night mode.
     */
    fun resolveMaterial3ColorSchemes(
        context: Context,
        colorTheme: WidgetColorTheme
    ): WidgetMaterial3ColorSchemes {
        return when (colorTheme) {
            WidgetColorTheme.DARK_SLATE -> WidgetMaterial3ColorSchemes(
                light = DarkSlateScheme,
                dark = DarkSlateScheme
            )
            WidgetColorTheme.CLASSIC_BLUE -> WidgetMaterial3ColorSchemes(
                light = ClassicBlueLightScheme,
                dark = ClassicBlueDarkScheme
            )
            WidgetColorTheme.EMERALD -> WidgetMaterial3ColorSchemes(
                light = EmeraldLightScheme,
                dark = EmeraldDarkScheme
            )
            WidgetColorTheme.SUNSET -> WidgetMaterial3ColorSchemes(
                light = SunsetLightScheme,
                dark = SunsetDarkScheme
            )
            WidgetColorTheme.DYNAMIC -> resolveDynamicMaterial3Schemes(context)
        }
    }

    private fun resolveDynamicMaterial3Schemes(context: Context): WidgetMaterial3ColorSchemes {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return WidgetMaterial3ColorSchemes(
                    light = dynamicLightColorScheme(context),
                    dark = dynamicDarkColorScheme(context)
                )
            } catch (e: Exception) {
                AppLogger.w(
                    AppLogger.TAG_WIDGET,
                    "Failed to resolve Android 12+ Material 3 dynamic color schemes, using fallback",
                    e
                )
            }
        }
        return WidgetMaterial3ColorSchemes(
            light = AgendaLightColorScheme,
            dark = AgendaDarkColorScheme
        )
    }

    /**
     * Resolves concrete ARGB colors for Classic RemoteViews widgets, Glance widgets,
     * and the Live Widget Preview using Material 3 color schemes and system dark mode awareness.
     *
     * @param forceDark Optional dark-mode override (e.g., for Glance day/night dual providers
     *   or Compose `isSystemInDarkTheme()` reactivity). Defaults to [isSystemDarkMode].
     */
    fun resolveColors(
        context: Context,
        prefs: WidgetPreferences,
        forceDark: Boolean? = null
    ): ResolvedWidgetTheme {
        val systemDark = forceDark ?: isSystemDarkMode(context)
        val effectiveDark = prefs.colorTheme == WidgetColorTheme.DARK_SLATE || systemDark
        val schemes = resolveMaterial3ColorSchemes(context, prefs.colorTheme)
        val scheme = if (effectiveDark) schemes.dark else schemes.light

        val rootAlpha = ((prefs.opacityPercent / 100f) * 255f).toInt().coerceIn(0, 255)
        val rootBorderAlpha = if (prefs.opacityPercent == 0) 0 else ((rootAlpha * 0.42f).toInt().coerceIn(32, 130))
        val itemAlpha = when {
            prefs.opacityPercent == 0 -> 0
            prefs.opacityPercent < 30 -> (rootAlpha + 35).coerceAtMost(115)
            else -> rootAlpha
        }
        val itemBorderAlpha = if (prefs.opacityPercent == 0) 0 else 165

        val rootBgColor = stripAlpha(scheme.surface.toArgb())
        val rawItemBg = stripAlpha(
            if (effectiveDark) {
                scheme.surfaceContainerHigh.takeIf { it != Color.Unspecified }?.toArgb()
                    ?: scheme.surfaceVariant.toArgb()
            } else {
                // Keep event cards crisp and elevated on light Material 3 surfaces
                if (prefs.colorTheme == WidgetColorTheme.DYNAMIC) {
                    scheme.surfaceContainerLowest.takeIf { it != Color.Unspecified }?.toArgb()
                        ?: AndroidColor.WHITE
                } else {
                    scheme.surfaceVariant.toArgb()
                }
            }
        )
        // Guarantee subtle card separation from the root container in dark mode
        val itemBgColor = if (effectiveDark && rawItemBg == rootBgColor) {
            ColorUtils.blendARGB(rootBgColor, AndroidColor.WHITE, 0.08f)
        } else {
            rawItemBg
        }

        val borderColor = stripAlpha(scheme.outlineVariant.toArgb())
        val chipBgColor = stripAlpha(scheme.primaryContainer.toArgb())
        val chipBgAlpha = if (effectiveDark) 235 else 240

        val rawAccent = stripAlpha(scheme.primary.toArgb())
        val accentColor = ensureReadableAccentColor(
            rawAccentColor = rawAccent,
            backgroundColor = rootBgColor,
            isDarkSurface = effectiveDark,
            minContrast = MIN_ACCENT_TEXT_CONTRAST
        )

        // Ensure event titles and subtitles maintain high contrast against dark/light card surfaces
        val rawTitleColor = stripAlpha(scheme.onSurface.toArgb())
        val rawSubtitleColor = stripAlpha(scheme.onSurfaceVariant.toArgb())
        val titleColor = if (prefs.opacityPercent == 0 && effectiveDark) {
            AndroidColor.WHITE
        } else {
            ensureReadableTextColor(
                preferredColor = rawTitleColor,
                backgroundColor = itemBgColor,
                isDarkSurface = effectiveDark,
                minContrast = MIN_TITLE_CONTRAST
            )
        }
        val subtitleColor = if (prefs.opacityPercent == 0 && effectiveDark) {
            AndroidColor.parseColor("#E1E4E8")
        } else {
            ensureReadableTextColor(
                preferredColor = rawSubtitleColor,
                backgroundColor = itemBgColor,
                isDarkSurface = effectiveDark,
                minContrast = MIN_SUBTITLE_CONTRAST
            )
        }

        val dividerAlpha = if (effectiveDark) 150 else 180
        val dividerColor = ColorUtils.setAlphaComponent(borderColor, dividerAlpha)

        return ResolvedWidgetTheme(
            isDark = effectiveDark,
            rootBgColor = rootBgColor,
            rootAlpha = rootAlpha,
            rootBorderColor = borderColor,
            rootBorderAlpha = rootBorderAlpha,
            itemBgColor = itemBgColor,
            itemAlpha = itemAlpha,
            itemBorderColor = borderColor,
            itemBorderAlpha = itemBorderAlpha,
            accentColor = accentColor,
            chipBgColor = chipBgColor,
            chipBgAlpha = chipBgAlpha,
            titleColor = titleColor,
            subtitleColor = subtitleColor,
            dividerColor = dividerColor
        )
    }

    /**
     * Resolves an event's accent color for timestamp text so that even dark calendar colors
     * (such as Graphite, Blueberry, or Basil) remain comfortably readable (>= 4.5:1 WCAG AA)
     * against dark widget card backgrounds.
     */
    fun resolveEventTimeColor(eventColorHex: String?, theme: ResolvedWidgetTheme): Int {
        val rawEventColor = CalendarColorMapper.parseColorInt(eventColorHex, theme.accentColor)
        return ensureReadableAccentColor(
            rawAccentColor = rawEventColor,
            backgroundColor = theme.itemBgColor,
            isDarkSurface = theme.isDark,
            minContrast = MIN_ACCENT_TEXT_CONTRAST
        )
    }

    /**
     * Resolves an event's vertical accent stripe color so dark event colors remain clearly
     * distinguishable against dark widget card backgrounds.
     */
    fun resolveEventBarColor(eventColorHex: String?, theme: ResolvedWidgetTheme): Int {
        val rawEventColor = CalendarColorMapper.parseColorInt(eventColorHex, theme.accentColor)
        return ensureReadableAccentColor(
            rawAccentColor = rawEventColor,
            backgroundColor = theme.itemBgColor,
            isDarkSurface = theme.isDark,
            minContrast = MIN_ACCENT_BAR_CONTRAST
        )
    }

    /**
     * Ensures [preferredColor] achieves at least [minContrast] against [backgroundColor],
     * stepping toward pure white on dark surfaces or deep charcoal on light surfaces if needed.
     */
    fun ensureReadableTextColor(
        preferredColor: Int,
        backgroundColor: Int,
        isDarkSurface: Boolean,
        minContrast: Double = MIN_TITLE_CONTRAST
    ): Int {
        val opaqueBg = stripAlpha(backgroundColor)
        val opaqueFg = stripAlpha(preferredColor)
        if (ColorUtils.calculateContrast(opaqueFg, opaqueBg) >= minContrast) {
            return opaqueFg
        }

        val targetInk = if (isDarkSurface) AndroidColor.WHITE else AndroidColor.parseColor("#111315")
        var step = 0.2f
        while (step <= 1.0f) {
            val candidate = ColorUtils.blendARGB(opaqueFg, targetInk, step)
            if (ColorUtils.calculateContrast(candidate, opaqueBg) >= minContrast) {
                return candidate
            }
            step += 0.2f
        }
        return targetInk
    }

    /**
     * Preserves the hue of [rawAccentColor] while lifting luminance on dark surfaces (or deepening
     * it on light surfaces) until contrast against [backgroundColor] reaches [minContrast].
     */
    fun ensureReadableAccentColor(
        rawAccentColor: Int,
        backgroundColor: Int,
        isDarkSurface: Boolean,
        minContrast: Double = MIN_ACCENT_TEXT_CONTRAST
    ): Int {
        val opaqueBg = stripAlpha(backgroundColor)
        val opaqueAccent = stripAlpha(rawAccentColor)
        if (ColorUtils.calculateContrast(opaqueAccent, opaqueBg) >= minContrast) {
            return opaqueAccent
        }

        val target = if (isDarkSurface) AndroidColor.WHITE else AndroidColor.parseColor("#121212")
        var step = 0.15f
        while (step <= 0.95f) {
            val candidate = ColorUtils.blendARGB(opaqueAccent, target, step)
            if (ColorUtils.calculateContrast(candidate, opaqueBg) >= minContrast) {
                return candidate
            }
            step += 0.1f
        }
        return target
    }

    private fun stripAlpha(color: Int): Int {
        return AndroidColor.rgb(
            AndroidColor.red(color),
            AndroidColor.green(color),
            AndroidColor.blue(color)
        )
    }
}
