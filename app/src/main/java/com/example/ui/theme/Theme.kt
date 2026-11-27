package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val AgendaDarkColorScheme = darkColorScheme(
    primary = GoogleBlueDark,
    onPrimary = Color(0xFF042B60),
    primaryContainer = Color(0xFF1A3A68),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = BlueGrey80,
    onSecondary = Color(0xFF233248),
    secondaryContainer = Color(0xFF2A364B),
    onSecondaryContainer = Color(0xFFD8E4F8),
    tertiary = Color(0xFF81C995),
    onTertiary = Color(0xFF0A3818),
    tertiaryContainer = Color(0xFF1A4B2A),
    onTertiaryContainer = Color(0xFFC8F5D4),
    background = CalendarBackgroundDark,
    onBackground = Color(0xFFE5E8F0),
    surface = CalendarSurfaceDark,
    onSurface = Color(0xFFE5E8F0),
    surfaceVariant = CalendarSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFC1C6D4),
    surfaceContainerLowest = CalendarSurfaceContainerLowestDark,
    surfaceContainerLow = CalendarSurfaceContainerLowDark,
    surfaceContainer = CalendarSurfaceContainerDark,
    surfaceContainerHigh = CalendarSurfaceContainerHighDark,
    surfaceContainerHighest = CalendarSurfaceContainerHighestDark,
    outline = Color(0xFF8B909E),
    outlineVariant = Color(0xFF434752)
)

val AgendaLightColorScheme = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FC),
    onPrimaryContainer = Color(0xFF042B60),
    secondary = BlueGrey40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E4F8),
    onSecondaryContainer = Color(0xFF142B4D),
    tertiary = GoogleGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3F2DC),
    onTertiaryContainer = Color(0xFF0A3818),
    background = CalendarBackgroundLight,
    onBackground = Color(0xFF191C22),
    surface = CalendarSurfaceLight,
    onSurface = Color(0xFF191C22),
    surfaceVariant = CalendarSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF4B5260),
    surfaceContainerLowest = CalendarSurfaceContainerLowestLight,
    surfaceContainerLow = CalendarSurfaceContainerLowLight,
    surfaceContainer = CalendarSurfaceContainerLight,
    surfaceContainerHigh = CalendarSurfaceContainerHighLight,
    surfaceContainerHighest = CalendarSurfaceContainerHighestLight,
    outline = Color(0xFF737A88),
    outlineVariant = Color(0xFFC5CDDC)
)

@Composable
fun AgendaWidgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AgendaDarkColorScheme
        else -> AgendaLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Deprecated(
    message = "Use AgendaWidgetTheme instead",
    replaceWith = ReplaceWith("AgendaWidgetTheme(darkTheme, dynamicColor, content)")
)
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    AgendaWidgetTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}
