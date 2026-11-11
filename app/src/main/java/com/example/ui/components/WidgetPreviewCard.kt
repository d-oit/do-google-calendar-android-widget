package com.example.ui.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EventEntity
import com.example.data.pref.WidgetColorTheme
import com.example.data.pref.WidgetPreferences
import com.example.data.repository.CalendarRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WidgetPreviewCard(
    events: List<EventEntity>,
    preferences: WidgetPreferences,
    isSyncing: Boolean,
    onRefresh: () -> Unit,
    onPinWidget: () -> Unit,
    onAddEvent: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                        text = "${preferences.opacityPercent}% Opacity • ${preferences.density.displayName}",
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
                // Widget Container with dynamic user opacity and theme
                val isDark = preferences.colorTheme == WidgetColorTheme.DARK_SLATE
                val alphaFloat = (preferences.opacityPercent / 100f).coerceIn(0.05f, 1f)
                val baseBgColor = if (isDark) {
                    Color(0xFF1E1E1E).copy(alpha = alphaFloat)
                } else {
                    Color(0xFFF8FAFD).copy(alpha = alphaFloat)
                }

                val titleColor = if (isDark) Color.White else Color(0xFF1F1F1F)
                val subtitleColor = if (isDark) Color(0xFFB0BEC5) else Color(0xFF5F6368)
                val accentColor = Color(android.graphics.Color.parseColor(preferences.colorTheme.primaryHex))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(baseBgColor)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
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

                            val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
                            val todayString = dateFormat.format(Date())

                            Text(
                                text = todayString,
                                color = titleColor,
                                fontSize = preferences.density.titleSp.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Add Event icon on widget
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.15f))
                                    .clickable { onAddEvent() }
                                    .testTag("widget_preview_add_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Event",
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Refresh button on widget
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.15f))
                                    .clickable { onRefresh() }
                                    .testTag("widget_preview_refresh_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Widget",
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(if (isDark) Color(0xFF333333) else Color(0xFFE0E3E7))
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Widget Agenda List
                        if (events.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = subtitleColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "No upcoming events",
                                        color = subtitleColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(events.take(preferences.daysHorizon * 2), key = { it.id }) { event ->
                                    Column {
                                        if (event.isFirstOfDay && event.dayHeader.isNotEmpty() && preferences.showDayHeaders) {
                                             Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = accentColor.copy(alpha = 0.15f),
                                                modifier = Modifier.padding(bottom = 3.dp, top = 2.dp)
                                            ) {
                                                Text(
                                                    text = event.dayHeader,
                                                    color = accentColor,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        // Item row inside widget
                                        val itemBgColor = if (isDark) {
                                            Color(0xFF2C2C2C).copy(alpha = alphaFloat)
                                        } else {
                                            Color.White.copy(alpha = alphaFloat)
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(itemBgColor)
                                                .border(
                                                    1.dp,
                                                    if (isDark) Color(0xFF3D3D3D) else Color(0xFFECEFF1),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .padding(horizontal = 10.dp, vertical = if (preferences.density.id == "compact") 6.dp else 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val barColor = com.example.util.CalendarColorMapper.parseColorHex(
                                                event.colorHex,
                                                preferences.colorTheme.primaryHex
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .height(26.dp)
                                                    .clip(RoundedCornerShape(2.dp))
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
                                                        color = barColor,
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
