package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.EventEntity
import com.example.data.pref.WidgetDensity
import com.example.data.pref.WidgetPreferences
import com.example.data.repository.CalendarRepository
import com.example.util.CalendarColorMapper
import com.example.widget.WidgetThemeHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    lastSyncTime: Long = 0L
) {
    val context = LocalContext.current
    val resolvedTheme = remember(preferences, context) {
        WidgetThemeHelper.resolveColors(context, preferences)
    }
    val previewEvents = remember(events, preferences) {
        CalendarRepository.filterEventsForPreferences(events, preferences)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("widget_preview_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = "Widget Preview",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Live Home Screen Widget",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${preferences.opacityPercent}% • ${preferences.density.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Simulated Android Wallpaper Backdrop to demonstrate real transparency
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1E3C72),
                                Color(0xFF2A5298),
                                Color(0xFF7928CA),
                                Color(0xFFFF0080)
                            )
                        )
                    )
                    .padding(8.dp)
            ) {
                val rootAlpha = resolvedTheme.rootAlpha / 255f
                val rootBgColor = Color(resolvedTheme.rootBgColor).copy(alpha = rootAlpha)
                val rootBorderColor = Color(resolvedTheme.rootBorderColor).copy(alpha = resolvedTheme.rootBorderAlpha / 255f)
                val accentColor = Color(resolvedTheme.accentColor)
                val chipBgColor = Color(resolvedTheme.chipBgColor).copy(alpha = resolvedTheme.chipBgAlpha / 255f)
                val titleColor = if (preferences.opacityPercent == 0) Color.White else Color(resolvedTheme.titleColor)
                val subtitleColor = if (preferences.opacityPercent == 0) Color.White.copy(alpha = 0.85f) else Color(resolvedTheme.subtitleColor)
                val dividerColor = Color(resolvedTheme.dividerColor)

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
                                contentDescription = "Calendar",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            val dateFormat = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }
                            val todayString = dateFormat.format(Date())
                            val syncStatusLabel = remember(lastSyncTime, isSyncing, context) {
                                if (isSyncing) {
                                    context.getString(com.example.R.string.widget_sync_in_progress)
                                } else {
                                    CalendarRepository.formatLastSyncIndicator(
                                        context = context,
                                        lastSyncMillis = lastSyncTime
                                    )
                                }
                            }
                            val syncDotColor = when {
                                isSyncing -> accentColor
                                lastSyncTime > 0L -> if (resolvedTheme.isDark) Color(0xFF81C995) else Color(0xFF137333)
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
                                        contentDescription = "Add Event",
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
                                                    com.example.R.string.widget_sync_tooltip_at,
                                                    CalendarRepository.formatLastSyncIndicator(
                                                        context = context,
                                                        lastSyncMillis = lastSyncTime,
                                                        compact = true
                                                    )
                                                )
                                            } else {
                                                context.getString(com.example.R.string.widget_sync_tooltip_never)
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
                                            androidx.compose.material3.CircularProgressIndicator(
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

                        // Widget Agenda List
                        if (previewEvents.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 148.dp)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            Color(resolvedTheme.itemBorderColor).copy(alpha = 0.6f)
                                        ),
                                        modifier = Modifier
                                            .size(width = 112.dp, height = 84.dp)
                                            .testTag("widget_preview_empty_illustration")
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.img_empty_calendar),
                                            contentDescription = stringResource(R.string.empty_calendar_illustration_desc),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Text(
                                        text = "No upcoming events",
                                        color = subtitleColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            val itemPad = when (preferences.density) {
                                WidgetDensity.COMPACT -> 6.dp
                                WidgetDensity.REGULAR -> 8.dp
                                WidgetDensity.LARGE -> 11.dp
                            }
                            val itemBgColor = Color(resolvedTheme.itemBgColor).copy(alpha = resolvedTheme.itemAlpha / 255f)
                            val itemBorderColor = Color(resolvedTheme.itemBorderColor).copy(alpha = resolvedTheme.itemBorderAlpha / 255f)

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(previewEvents, key = { it.id }) { event ->
                                    Column {
                                        if (event.isFirstOfDay && event.dayHeader.isNotEmpty() && preferences.showDayHeaders) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = chipBgColor,
                                                modifier = Modifier.padding(bottom = 4.dp, top = 2.dp)
                                            ) {
                                                Text(
                                                    text = event.dayHeader,
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
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(itemBgColor)
                                                .border(1.dp, itemBorderColor, RoundedCornerShape(14.dp))
                                                .padding(itemPad),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val barColor = CalendarColorMapper.parseColorHex(
                                                event.colorHex,
                                                preferences.colorTheme.primaryHex
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .height(28.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(barColor)
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = event.title,
                                                    color = titleColor,
                                                    fontSize = preferences.density.titleSp.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (event.calendarId.isNotBlank()) {
                                                    Text(
                                                        text = event.calendarId,
                                                        color = subtitleColor,
                                                        fontSize = (preferences.density.timeSp - 2).coerceAtLeast(9f).sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (!event.location.isNullOrBlank() && preferences.showLocations) {
                                                    Text(
                                                        text = event.location,
                                                        color = subtitleColor,
                                                        fontSize = (preferences.density.timeSp - 1).coerceAtLeast(9f).sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            val timeStr = CalendarRepository.formatEventTime(
                                                event.startMillis,
                                                event.endMillis,
                                                event.isAllDay
                                            )

                                            Text(
                                                text = timeStr,
                                                color = barColor,
                                                fontSize = preferences.density.timeSp.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pin to Home Screen Button
            Button(
                onClick = onPinWidget,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("pin_widget_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Widget to Home Screen",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
