package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.EventEntity
import com.example.data.pref.WidgetPreferences
import com.example.data.pref.WidgetTitleLineLimit
import com.example.util.CalendarDateFormatter
import com.example.util.rememberEventColorScheme

/**
 * Event item card component that renders an individual event in the agenda list.
 * Applies the color properties read from CalendarContract via [rememberEventColorScheme]
 * across all individual UI elements (accent bar, card surface, border, calendar badge pill,
 * time display, and icons).
 */
@Composable
fun EventItemCard(
    event: EventEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    widgetPreferences: WidgetPreferences = WidgetPreferences()
) {
    val context = LocalContext.current
    // Map event color properties from CalendarContract to UI color scheme
    val colorScheme = rememberEventColorScheme(event)

    Column(modifier = modifier.fillMaxWidth()) {
        // Section header for date if first of day
        if (event.isFirstOfDay && (event.dayHeader.isNotEmpty() || event.startMillis > 0L)) {
            val localizedHeader = CalendarDateFormatter.formatLocalizedDayHeader(context, event.startMillis)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.badgeBackground,
                    border = BorderStroke(0.5.dp, colorScheme.badgeBorder)
                ) {
                    Text(
                        text = localizedHeader,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.badgeText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onClick() }
                .testTag("event_item_card_${event.id}"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.containerBackground
            ),
            border = BorderStroke(1.dp, colorScheme.cardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Color strip indicator derived from CalendarContract event color
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colorScheme.primaryAccent)
                        .testTag("event_color_bar_${event.id}")
                )

                Spacer(modifier = Modifier.width(12.dp))

                val isFullTitle = widgetPreferences.titleLineLimit == WidgetTitleLineLimit.FULL
                val timeStr = CalendarDateFormatter.formatEventTime(
                    startMillis = event.startMillis,
                    endMillis = event.endMillis,
                    isAllDay = event.isAllDay,
                    context = context,
                    timeDisplayMode = widgetPreferences.timeDisplayMode
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.titleColor,
                        maxLines = widgetPreferences.titleLineLimit.maxLines,
                        overflow = if (isFullTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                        softWrap = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("event_title_${event.id}")
                    )

                    if (timeStr.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = colorScheme.timeIconTint,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.timeText,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("event_time_${event.id}")
                            )
                        }
                    }

                    if (widgetPreferences.showCalendarName) {
                        val defaultCalName = stringResource(R.string.title_google_calendar)
                        val calName = event.calendarId.ifBlank { defaultCalName }
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colorScheme.badgeBackground,
                            border = BorderStroke(0.5.dp, colorScheme.badgeBorder),
                            modifier = Modifier.testTag("event_calendar_badge_${event.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(colorScheme.badgeDot, CircleShape)
                                )
                                Text(
                                    text = calName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.badgeText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (!event.location.isNullOrBlank() && widgetPreferences.showLocations) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = colorScheme.locationIconTint,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = event.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.locationIconTint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("event_location_${event.id}")
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_event_btn_${event.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.action_delete_event),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
