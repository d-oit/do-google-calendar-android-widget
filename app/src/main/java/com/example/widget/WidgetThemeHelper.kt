package com.example.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.pref.WidgetColorTheme
import com.example.data.pref.WidgetPreferences
import com.example.util.AppLogger

/**
 * Resolved color palette for both the Home Screen Widget (RemoteViews) and
 * the in-app Live Widget Preview, ensuring 1:1 visual parity across all themes,
 * Android 12+ Material You dynamic colors, and opacity levels (0%–100%).
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

    fun resolveColors(context: Context, prefs: WidgetPreferences): ResolvedWidgetTheme {
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val rootAlpha = ((prefs.opacityPercent / 100f) * 255f).toInt().coerceIn(0, 255)
        val rootBorderAlpha = if (prefs.opacityPercent == 0) 0 else ((rootAlpha * 0.35f).toInt().coerceIn(28, 120))
        val itemAlpha = when {
            prefs.opacityPercent == 0 -> 0
            prefs.opacityPercent < 30 -> (rootAlpha + 35).coerceAtMost(110)
            else -> rootAlpha
        }
        val itemBorderAlpha = if (prefs.opacityPercent == 0) 0 else 160

        return when (prefs.colorTheme) {
            WidgetColorTheme.DARK_SLATE -> ResolvedWidgetTheme(
                isDark = true,
                rootBgColor = Color.parseColor("#1E1E1E"),
                rootAlpha = rootAlpha,
                rootBorderColor = Color.parseColor("#4A4D52"),
                rootBorderAlpha = rootBorderAlpha,
                itemBgColor = Color.parseColor("#2B2D30"),
                itemAlpha = itemAlpha,
                itemBorderColor = Color.parseColor("#3D4045"),
                itemBorderAlpha = itemBorderAlpha,
                accentColor = Color.parseColor("#90CAF9"),
                chipBgColor = Color.parseColor("#2A3B4D"),
                chipBgAlpha = 235,
                titleColor = Color.WHITE,
                subtitleColor = Color.parseColor("#B0BEC5"),
                dividerColor = Color.argb(140, 80, 84, 90)
            )

            WidgetColorTheme.CLASSIC_BLUE -> ResolvedWidgetTheme(
                isDark = false,
                rootBgColor = Color.parseColor("#F4F8FE"),
                rootAlpha = rootAlpha,
                rootBorderColor = Color.parseColor("#D2E3FC"),
                rootBorderAlpha = rootBorderAlpha,
                itemBgColor = Color.WHITE,
                itemAlpha = itemAlpha,
                itemBorderColor = Color.parseColor("#E3EAF4"),
                itemBorderAlpha = itemBorderAlpha,
                accentColor = Color.parseColor("#1A73E8"),
                chipBgColor = Color.parseColor("#E8F0FE"),
                chipBgAlpha = 240,
                titleColor = Color.parseColor("#1F1F1F"),
                subtitleColor = Color.parseColor("#5F6368"),
                dividerColor = Color.argb(180, 218, 224, 232)
            )

            WidgetColorTheme.EMERALD -> ResolvedWidgetTheme(
                isDark = false,
                rootBgColor = Color.parseColor("#EFFAF3"),
                rootAlpha = rootAlpha,
                rootBorderColor = Color.parseColor("#B7E4C7"),
                rootBorderAlpha = rootBorderAlpha,
                itemBgColor = Color.WHITE,
                itemAlpha = itemAlpha,
                itemBorderColor = Color.parseColor("#D8F3DC"),
                itemBorderAlpha = itemBorderAlpha,
                accentColor = Color.parseColor("#137333"),
                chipBgColor = Color.parseColor("#D8F3DC"),
                chipBgAlpha = 240,
                titleColor = Color.parseColor("#112619"),
                subtitleColor = Color.parseColor("#476351"),
                dividerColor = Color.argb(180, 196, 228, 207)
            )

            WidgetColorTheme.SUNSET -> ResolvedWidgetTheme(
                isDark = false,
                rootBgColor = Color.parseColor("#FFF6EE"),
                rootAlpha = rootAlpha,
                rootBorderColor = Color.parseColor("#FFD2B3"),
                rootBorderAlpha = rootBorderAlpha,
                itemBgColor = Color.WHITE,
                itemAlpha = itemAlpha,
                itemBorderColor = Color.parseColor("#FFE4CE"),
                itemBorderAlpha = itemBorderAlpha,
                accentColor = Color.parseColor("#E65100"),
                chipBgColor = Color.parseColor("#FFE5D0"),
                chipBgAlpha = 240,
                titleColor = Color.parseColor("#2B170A"),
                subtitleColor = Color.parseColor("#6E4B3A"),
                dividerColor = Color.argb(180, 244, 210, 184)
            )

            WidgetColorTheme.DYNAMIC -> resolveDynamicColors(
                context = context,
                systemDark = systemDark,
                rootAlpha = rootAlpha,
                rootBorderAlpha = rootBorderAlpha,
                itemAlpha = itemAlpha,
                itemBorderAlpha = itemBorderAlpha
            )
        }
    }

    private fun resolveDynamicColors(
        context: Context,
        systemDark: Boolean,
        rootAlpha: Int,
        rootBorderAlpha: Int,
        itemAlpha: Int,
        itemBorderAlpha: Int
    ): ResolvedWidgetTheme {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (systemDark) {
                    val rootBg = ContextCompat.getColor(context, android.R.color.system_neutral1_900)
                    val itemBg = ContextCompat.getColor(context, android.R.color.system_neutral1_800)
                    val accent = ContextCompat.getColor(context, android.R.color.system_accent1_200)
                    val chipBg = ContextCompat.getColor(context, android.R.color.system_accent1_800)
                    val border = ContextCompat.getColor(context, android.R.color.system_neutral2_700)
                    return ResolvedWidgetTheme(
                        isDark = true,
                        rootBgColor = stripAlpha(rootBg),
                        rootAlpha = rootAlpha,
                        rootBorderColor = stripAlpha(border),
                        rootBorderAlpha = rootBorderAlpha,
                        itemBgColor = stripAlpha(itemBg),
                        itemAlpha = itemAlpha,
                        itemBorderColor = stripAlpha(border),
                        itemBorderAlpha = itemBorderAlpha,
                        accentColor = stripAlpha(accent),
                        chipBgColor = stripAlpha(chipBg),
                        chipBgAlpha = 235,
                        titleColor = Color.parseColor("#F1F3F4"),
                        subtitleColor = Color.parseColor("#C4C7C5"),
                        dividerColor = Color.argb(140, 95, 99, 104)
                    )
                } else {
                    val rootBg = ContextCompat.getColor(context, android.R.color.system_neutral1_50)
                    val itemBg = ContextCompat.getColor(context, android.R.color.system_neutral1_0)
                    val accent = ContextCompat.getColor(context, android.R.color.system_accent1_600)
                    val chipBg = ContextCompat.getColor(context, android.R.color.system_accent1_100)
                    val border = ContextCompat.getColor(context, android.R.color.system_neutral2_200)
                    return ResolvedWidgetTheme(
                        isDark = false,
                        rootBgColor = stripAlpha(rootBg),
                        rootAlpha = rootAlpha,
                        rootBorderColor = stripAlpha(border),
                        rootBorderAlpha = rootBorderAlpha,
                        itemBgColor = stripAlpha(itemBg),
                        itemAlpha = itemAlpha,
                        itemBorderColor = stripAlpha(border),
                        itemBorderAlpha = itemBorderAlpha,
                        accentColor = stripAlpha(accent),
                        chipBgColor = stripAlpha(chipBg),
                        chipBgAlpha = 240,
                        titleColor = Color.parseColor("#1F1F1F"),
                        subtitleColor = Color.parseColor("#5F6368"),
                        dividerColor = Color.argb(180, 218, 224, 232)
                    )
                }
            } catch (e: Exception) {
                AppLogger.w(AppLogger.TAG_WIDGET, "Failed to resolve Android 12+ dynamic system colors, falling back", e)
            }
        }

        return if (systemDark) {
            ResolvedWidgetTheme(
                isDark = true,
                rootBgColor = Color.parseColor("#1C1E24"),
                rootAlpha = rootAlpha,
                rootBorderColor = Color.parseColor("#3E434E"),
                rootBorderAlpha = rootBorderAlpha,
                itemBgColor = Color.parseColor("#282B33"),
                itemAlpha = itemAlpha,
                itemBorderColor = Color.parseColor("#3B3F4A"),
                itemBorderAlpha = itemBorderAlpha,
                accentColor = Color.parseColor("#A8C7FA"),
                chipBgColor = Color.parseColor("#2A3B54"),
                chipBgAlpha = 235,
                titleColor = Color.parseColor("#F1F3F4"),
                subtitleColor = Color.parseColor("#C4C7C5"),
                dividerColor = Color.argb(140, 80, 84, 90)
            )
        } else {
            ResolvedWidgetTheme(
                isDark = false,
                rootBgColor = Color.parseColor("#F8FAFD"),
                rootAlpha = rootAlpha,
                rootBorderColor = Color.parseColor("#D3E3FD"),
                rootBorderAlpha = rootBorderAlpha,
                itemBgColor = Color.WHITE,
                itemAlpha = itemAlpha,
                itemBorderColor = Color.parseColor("#ECEFF1"),
                itemBorderAlpha = itemBorderAlpha,
                accentColor = Color.parseColor("#1A73E8"),
                chipBgColor = Color.parseColor("#E8F0FE"),
                chipBgAlpha = 240,
                titleColor = Color.parseColor("#1F1F1F"),
                subtitleColor = Color.parseColor("#5F6368"),
                dividerColor = Color.argb(180, 224, 227, 231)
            )
        }
    }

    private fun stripAlpha(color: Int): Int {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
    }
}
