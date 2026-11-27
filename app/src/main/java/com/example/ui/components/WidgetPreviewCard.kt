package com.example.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.EventEntity
import com.example.data.pref.WidgetDensity
import com.example.data.pref.WidgetPreferences
import com.example.data.pref.WidgetTitleLineLimit
import com.example.data.repository.CalendarRepository
import com.example.util.CalendarColorMapper
import com.example.util.CalendarDateFormatter
import com.example.widget.WidgetThemeHelper

/**
 * Switchable abstract wallpaper backdrops rendered strictly behind the live widget card
 * so users can verify widget contrast and translucency across 0%–100% opacity.
 */
enum class PreviewWallpaperPreset(
    val id: String,
    @param:StringRes val labelResId: Int,
    val isLightBackdrop: Boolean,
    val swatchPrimary: Color,
    val swatchSecondary: Color
) {
    DARK(
        id = "dark",
        labelResId = R.string.widget_wallpaper_dark,
        isLightBackdrop = false,
        swatchPrimary = Color(0xFF141B30),
        swatchSecondary = Color(0xFF3E2D6D)
    ),
    LIGHT(
        id = "light",
        labelResId = R.string.widget_wallpaper_light,
        isLightBackdrop = true,
        swatchPrimary = Color(0xFFE7EFFC),
        swatchSecondary = Color(0xFFD6E4FA)
    ),
    VIBRANT(
        id = "vibrant",
        labelResId = R.string.widget_wallpaper_vibrant,
        isLightBackdrop = false,
        swatchPrimary = Color(0xFF2855C8),
        swatchSecondary = Color(0xFFD93F87)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPreviewCard(
    events: List<EventEntity>,
    preferences: WidgetPreferences,
    isSyncing: Boolean,
    onRefresh: () -> Unit,
    onPinWidget: () -> Unit,
    onAddEvent: () -> Unit,
    modifier: Modifier = Modifier,
    lastSyncTime: Long = 0L,
    onOpenCalendar: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val defaultWallpaper = remember(isSystemDark) {
        if (isSystemDark) PreviewWallpaperPreset.DARK else PreviewWallpaperPreset.LIGHT
    }
    var selectedWallpaper by rememberSaveable { mutableStateOf(defaultWallpaper) }

    val resolvedTheme = remember(preferences, context, isSystemDark) {
        WidgetThemeHelper.resolveColors(context, preferences, forceDark = isSystemDark)
    }
    val previewEvents = remember(events, preferences) {
        CalendarRepository.filterEventsForPreferences(events, preferences)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("widget_preview_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Minimal section header + wallpaper contrast preset selector (no heavy outer frame)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.widget_preview_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${stringResource(R.string.widget_customizer_opacity_value, preferences.opacityPercent)} • ${stringResource(preferences.density.labelResId)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Compact Wallpaper Backdrop Switcher (Dark | Light | Vibrant)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.testTag("wallpaper_preset_bar")
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreviewWallpaperPreset.values().forEach { preset ->
                        val isSelected = selectedWallpaper == preset
                        val pillContainer by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            },
                            animationSpec = tween(180),
                            label = "wallpaper_pill_bg"
                        )
                        val pillTextColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(180),
                            label = "wallpaper_pill_fg"
                        )
                        Box(
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(pillContainer)
                                .clickable { selectedWallpaper = preset }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("wallpaper_preset_${preset.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(preset.swatchPrimary, preset.swatchSecondary)
                                            )
                                        )
                                        .border(
                                            width = 0.75.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = stringResource(preset.labelResId),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = pillTextColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Frameless Wallpaper Backdrop Stage (only behind the widget itself — zero phone-in-phone chrome)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(28.dp)
                )
                .testTag("widget_wallpaper_stage")
        ) {
            AbstractWallpaperCanvas(
                preset = selectedWallpaper,
                modifier = Modifier.matchParentSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                val rootAlpha = resolvedTheme.rootAlpha / 255f
                val rootBgColor = Color(resolvedTheme.rootBgColor).copy(alpha = rootAlpha)
                val rootBorderColor = Color(resolvedTheme.rootBorderColor).copy(
                    alpha = resolvedTheme.rootBorderAlpha / 255f
                )
                val isClearOverLight = preferences.opacityPercent <= 15 && selectedWallpaper.isLightBackdrop
                val isClearOverDark = preferences.opacityPercent <= 15 && !selectedWallpaper.isLightBackdrop

                val accentColor = when {
                    isClearOverLight -> Color(0xFF1558B0)
                    isClearOverDark -> Color(0xFFA8C7FA)
                    else -> Color(resolvedTheme.accentColor)
                }
                val chipBgColor = when {
                    isClearOverLight -> Color(0xFF1558B0).copy(alpha = 0.14f)
                    isClearOverDark -> Color.White.copy(alpha = 0.18f)
                    else -> Color(resolvedTheme.chipBgColor).copy(alpha = resolvedTheme.chipBgAlpha / 255f)
                }
                val titleColor = when {
                    isClearOverLight -> Color(0xFF111318)
                    isClearOverDark -> Color.White
                    else -> Color(resolvedTheme.titleColor)
                }
                val subtitleColor = when {
                    isClearOverLight -> Color(0xFF333945)
                    isClearOverDark -> Color.White.copy(alpha = 0.86f)
                    else -> Color(resolvedTheme.subtitleColor)
                }
                val dividerColor = when {
                    isClearOverLight -> Color(0xFF111318).copy(alpha = 0.15f)
                    isClearOverDark -> Color.White.copy(alpha = 0.22f)
                    else -> Color(resolvedTheme.dividerColor)
                }

                // Actual Live Widget Surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(rootBgColor)
                        .border(1.dp, rootBorderColor, RoundedCornerShape(24.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        // Widget Top Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = stringResource(R.string.title_google_calendar),
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            val todayString = CalendarDateFormatter.formatWidgetHeaderDate(isNarrow = false)
                            val syncStatusLabel = remember(lastSyncTime, isSyncing, context) {
                                if (isSyncing) {
                                    context.getString(R.string.widget_sync_in_progress)
                                } else {
                                    CalendarDateFormatter.formatLastSyncIndicator(
                                        context = context,
                                        lastSyncMillis = lastSyncTime
                                    )
                                }
                            }
                            val syncDotColor = when {
                                isSyncing -> accentColor
                                lastSyncTime > 0L -> if (resolvedTheme.isDark || isClearOverDark) Color(0xFF81C995) else Color(0xFF137333)
                                else -> subtitleColor
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = todayString,
                                    color = titleColor,
                                    fontSize = preferences.density.titleSp.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.testTag("widget_preview_sync_status")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(syncDotColor)
                                    )
                                    Text(
                                        text = syncStatusLabel,
                                        color = subtitleColor,
                                        fontSize = (preferences.density.timeSp - 2f).coerceIn(9f, 11f).sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("widget_preview_sync_time_text")
                                    )
                                }
                            }

                            // Add Event icon (48dp touch target around 32dp pill)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { onAddEvent() }
                                    .testTag("widget_preview_add_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(chipBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringResource(R.string.action_add_event),
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Refresh button (48dp touch target around 32dp pill) with sync time tooltip
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = {
                                    PlainTooltip {
                                        Text(
                                            text = if (lastSyncTime > 0L) {
                                                context.getString(
                                                    R.string.widget_sync_tooltip_at,
                                                    CalendarRepository.formatLastSyncIndicator(
                                                        context = context,
                                                        lastSyncMillis = lastSyncTime,
                                                        compact = true
                                                    )
                                                )
                                            } else {
                                                context.getString(R.string.widget_sync_tooltip_never)
                                            }
                                        )
                                    }
                                },
                                state = rememberTooltipState()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = !isSyncing) { onRefresh() }
                                        .testTag("widget_preview_refresh_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(chipBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSyncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .testTag("widget_preview_refresh_progress"),
                                                color = accentColor,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = syncStatusLabel,
                                                tint = accentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(dividerColor)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Widget Agenda List or Smart Empty State
                        if (previewEvents.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 140.dp)
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                                    .testTag("widget_preview_empty_state"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(chipBgColor)
                                        .testTag("widget_preview_empty_illustration"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EventAvailable,
                                        contentDescription = stringResource(R.string.widget_no_events),
                                        tint = accentColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.widget_no_events),
                                    color = titleColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = chipBgColor,
                                        modifier = Modifier
                                            .heightIn(min = 36.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onAddEvent() }
                                            .testTag("widget_preview_empty_add_action")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.widget_empty_action_add),
                                                color = accentColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    if (onOpenCalendar != null) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color.Transparent,
                                            border = BorderStroke(1.dp, dividerColor),
                                            modifier = Modifier
                                                .heightIn(min = 36.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { onOpenCalendar() }
                                                .testTag("widget_preview_empty_calendar_action")
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.widget_empty_action_calendar),
                                                    color = subtitleColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val itemPad = when (preferences.density) {
                                WidgetDensity.COMPACT -> 7.dp
                                WidgetDensity.REGULAR -> 9.dp
                                WidgetDensity.LARGE -> 11.dp
                            }
                            val itemBgColor = Color(resolvedTheme.itemBgColor).copy(
                                alpha = resolvedTheme.itemAlpha / 255f
                            )
                            val itemBorderColor = Color(resolvedTheme.itemBorderColor).copy(
                                alpha = resolvedTheme.itemBorderAlpha / 255f
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(previewEvents, key = { it.id }) { event ->
                                    Column {
                                        if (event.isFirstOfDay && (event.dayHeader.isNotEmpty() || event.startMillis > 0L) && preferences.showDayHeaders) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = chipBgColor,
                                                modifier = Modifier.padding(bottom = 4.dp, top = 2.dp)
                                            ) {
                                                Text(
                                                    text = CalendarDateFormatter.formatLocalizedDayHeader(context, event.startMillis),
                                                    color = accentColor,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(IntrinsicSize.Min)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(itemBgColor)
                                                .border(1.dp, itemBorderColor, RoundedCornerShape(14.dp))
                                                .padding(itemPad),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            val barColor = Color(
                                                WidgetThemeHelper.resolveEventBarColor(event.colorHex, resolvedTheme)
                                            )
                                            val timeColor = when {
                                                isClearOverDark -> Color(
                                                    WidgetThemeHelper.ensureReadableAccentColor(
                                                        rawAccentColor = CalendarColorMapper.parseColorInt(
                                                            event.colorHex,
                                                            resolvedTheme.accentColor
                                                        ),
                                                        backgroundColor = android.graphics.Color.parseColor("#1B2B4B"),
                                                        isDarkSurface = true
                                                    )
                                                )
                                                isClearOverLight -> Color(
                                                    WidgetThemeHelper.ensureReadableAccentColor(
                                                        rawAccentColor = CalendarColorMapper.parseColorInt(
                                                            event.colorHex,
                                                            resolvedTheme.accentColor
                                                        ),
                                                        backgroundColor = android.graphics.Color.parseColor("#E7EFFC"),
                                                        isDarkSurface = false
                                                    )
                                                )
                                                else -> Color(
                                                    WidgetThemeHelper.resolveEventTimeColor(event.colorHex, resolvedTheme)
                                                )
                                            }

                                            // Left: Vertical rounded calendar color bar
                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .fillMaxHeight()
                                                    .heightIn(min = 28.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(barColor)
                                            )

                                            Spacer(modifier = Modifier.width(9.dp))

                                            val timeStr = CalendarDateFormatter.formatEventTime(
                                                startMillis = event.startMillis,
                                                endMillis = event.endMillis,
                                                isAllDay = event.isAllDay,
                                                context = context,
                                                timeDisplayMode = preferences.timeDisplayMode
                                            )
                                            val timingCue = CalendarDateFormatter.formatEventTimingCue(
                                                context = context,
                                                startMillis = event.startMillis,
                                                endMillis = event.endMillis,
                                                isAllDay = event.isAllDay
                                            )
                                            val isFullTitle = preferences.titleLineLimit == WidgetTitleLineLimit.FULL

                                            // Middle: Title + compact time + optional calendar/location
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = event.title,
                                                    color = titleColor,
                                                    fontSize = preferences.density.titleSp.sp,
                                                    lineHeight = (preferences.density.titleSp + 4f).sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = preferences.titleLineLimit.maxLines,
                                                    overflow = if (isFullTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                                                    softWrap = true,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("widget_preview_event_title_${event.id}")
                                                )
                                                if (timeStr.isNotEmpty()) {
                                                    Text(
                                                        text = timeStr,
                                                        color = timeColor,
                                                        fontSize = preferences.density.timeSp.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.testTag("widget_preview_event_time_${event.id}")
                                                    )
                                                }
                                                if (preferences.showCalendarName && event.calendarId.isNotBlank()) {
                                                    Text(
                                                        text = event.calendarId,
                                                        color = subtitleColor,
                                                        fontSize = (preferences.density.timeSp - 2).coerceAtLeast(9f).sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.testTag("widget_preview_event_calendar_${event.id}")
                                                    )
                                                }
                                                if (!event.location.isNullOrBlank() && preferences.showLocations) {
                                                    Text(
                                                        text = event.location,
                                                        color = subtitleColor,
                                                        fontSize = (preferences.density.timeSp - 1).coerceAtLeast(9f).sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.testTag("widget_preview_event_location_${event.id}")
                                                    )
                                                }
                                            }

                                            // Right: Subtle live status cue pill (NOW / In Xm)
                                            if (timingCue != null) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (timingCue.isLive) accentColor else chipBgColor,
                                                    modifier = Modifier.testTag("widget_preview_event_cue_${event.id}")
                                                ) {
                                                    Text(
                                                        text = timingCue.label,
                                                        color = if (timingCue.isLive) {
                                                            if (resolvedTheme.isDark) Color(0xFF0A192F) else Color.White
                                                        } else {
                                                            accentColor
                                                        },
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Compact secondary tonal CTA (keeps visual focus on live preview & reserves + FAB for new events)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onPinWidget,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("pin_widget_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.widget_customizer_pin_compact),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Procedural abstract Material You wallpaper artwork drawn via Canvas so the backdrop
 * scales cleanly across phones, foldables, and tablets with zero APK bitmap bloat.
 */
@Composable
private fun AbstractWallpaperCanvas(
    preset: PreviewWallpaperPreset,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (preset) {
            PreviewWallpaperPreset.DARK -> {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0B1020),
                            Color(0xFF151E38),
                            Color(0xFF231942),
                            Color(0xFF161328)
                        ),
                        start = Offset.Zero,
                        end = Offset(w, h)
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF4B6BFB).copy(alpha = 0.42f), Color.Transparent),
                        center = Offset(w * 0.18f, h * 0.22f),
                        radius = w * 0.58f
                    ),
                    radius = w * 0.58f,
                    center = Offset(w * 0.18f, h * 0.22f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF9C4DF4).copy(alpha = 0.36f), Color.Transparent),
                        center = Offset(w * 0.85f, h * 0.78f),
                        radius = w * 0.55f
                    ),
                    radius = w * 0.55f,
                    center = Offset(w * 0.85f, h * 0.78f)
                )
                val wave = Path().apply {
                    moveTo(0f, h * 0.68f)
                    cubicTo(w * 0.28f, h * 0.42f, w * 0.66f, h * 0.92f, w, h * 0.54f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    path = wave,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF31447C).copy(alpha = 0.35f),
                            Color(0xFF5D2D7A).copy(alpha = 0.28f)
                        )
                    )
                )
            }

            PreviewWallpaperPreset.LIGHT -> {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFF3F6FC),
                            Color(0xFFE3ECFA),
                            Color(0xFFECE6F8),
                            Color(0xFFE1F2EE)
                        ),
                        start = Offset.Zero,
                        end = Offset(w, h)
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF8AB4F8).copy(alpha = 0.42f), Color.Transparent),
                        center = Offset(w * 0.15f, h * 0.2f),
                        radius = w * 0.55f
                    ),
                    radius = w * 0.55f,
                    center = Offset(w * 0.15f, h * 0.2f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFF4B8CE).copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(w * 0.86f, h * 0.76f),
                        radius = w * 0.52f
                    ),
                    radius = w * 0.52f,
                    center = Offset(w * 0.86f, h * 0.76f)
                )
                val silkWave = Path().apply {
                    moveTo(0f, h * 0.62f)
                    cubicTo(w * 0.32f, h * 0.36f, w * 0.68f, h * 0.88f, w, h * 0.48f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    path = silkWave,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFBBD3FA).copy(alpha = 0.42f),
                            Color(0xFFD6C7F7).copy(alpha = 0.35f)
                        )
                    )
                )
            }

            PreviewWallpaperPreset.VIBRANT -> {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1A3A8F),
                            Color(0xFF4F32B8),
                            Color(0xFFAA2B89),
                            Color(0xFFE35D5B)
                        ),
                        start = Offset.Zero,
                        end = Offset(w, h)
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(w * 0.2f, h * 0.18f),
                        radius = w * 0.56f
                    ),
                    radius = w * 0.56f,
                    center = Offset(w * 0.2f, h * 0.18f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFB74D).copy(alpha = 0.48f), Color.Transparent),
                        center = Offset(w * 0.86f, h * 0.82f),
                        radius = w * 0.52f
                    ),
                    radius = w * 0.52f,
                    center = Offset(w * 0.86f, h * 0.82f)
                )
                val auroraRibbon = Path().apply {
                    moveTo(0f, h * 0.55f)
                    cubicTo(w * 0.3f, h * 0.24f, w * 0.65f, h * 0.85f, w, h * 0.4f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    path = auroraRibbon,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF7C3AED).copy(alpha = 0.36f),
                            Color(0xFFF43F5E).copy(alpha = 0.32f)
                        )
                    )
                )
            }
        }
    }
}
