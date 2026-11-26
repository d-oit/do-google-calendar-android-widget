package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.pref.WidgetColorTheme
import com.example.data.pref.WidgetDensity
import com.example.data.pref.WidgetPreferences
import com.example.data.repository.CalendarAccountInfo
import com.example.util.CalendarColorMapper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetCustomizerControls(
    preferences: WidgetPreferences,
    availableCalendars: List<CalendarAccountInfo>,
    onUpdatePreferences: (WidgetPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("widget_customizer_controls_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.widget_customizer_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.widget_customizer_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. Background Opacity Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.widget_customizer_opacity_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    val opacityQualifier = when {
                        preferences.opacityPercent == 0 -> stringResource(R.string.widget_opacity_clear)
                        preferences.opacityPercent <= 25 -> stringResource(R.string.widget_opacity_glass)
                        preferences.opacityPercent <= 55 -> stringResource(R.string.widget_opacity_translucent)
                        preferences.opacityPercent <= 85 -> stringResource(R.string.widget_opacity_frosted)
                        else -> stringResource(R.string.widget_opacity_solid)
                    }
                    Text(
                        text = "${stringResource(R.string.widget_customizer_opacity_value, preferences.opacityPercent)} ($opacityQualifier)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = preferences.opacityPercent.toFloat(),
                    onValueChange = { onUpdatePreferences(preferences.copy(opacityPercent = it.toInt())) },
                    valueRange = 0f..100f,
                    steps = 19,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opacity_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // 2. Color Themes
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.widget_customizer_theme_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WidgetColorTheme.values().forEach { theme ->
                        val isSelected = preferences.colorTheme == theme
                        FilterChip(
                            selected = isSelected,
                            onClick = { onUpdatePreferences(preferences.copy(colorTheme = theme)) },
                            label = { Text(stringResource(theme.labelResId)) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(Color(android.graphics.Color.parseColor(theme.primaryHex)), CircleShape)
                                )
                            },
                            modifier = Modifier.testTag("theme_chip_${theme.id}")
                        )
                    }
                }
            }

            // 3. Typography Scale & Density
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FormatSize,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.widget_customizer_density_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WidgetDensity.values().forEach { density ->
                        val isSelected = preferences.density == density
                        FilterChip(
                            selected = isSelected,
                            onClick = { onUpdatePreferences(preferences.copy(density = density)) },
                            label = { Text(stringResource(density.labelResId)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("density_chip_${density.id}")
                        )
                    }
                }
            }

            // 4. Days Ahead Horizon
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.widget_customizer_horizon_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(1, 2, 3, 7, 14, 30).forEach { days ->
                        val isSelected = preferences.daysHorizon == days
                        val label = if (days == 1) {
                            stringResource(R.string.filter_today)
                        } else {
                            stringResource(R.string.widget_customizer_horizon_days, days)
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { onUpdatePreferences(preferences.copy(daysHorizon = days)) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("days_chip_$days")
                        )
                    }
                }
            }

            // 5. Multi-Calendar Selection Filter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.widget_customizer_calendars_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (preferences.isAllCalendarsSelected) {
                                stringResource(R.string.widget_customizer_calendars_all)
                            } else {
                                stringResource(R.string.widget_customizer_custom_filter)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = preferences.isAllCalendarsSelected,
                            onCheckedChange = { checked ->
                                // When switching to Custom Filter for the first time, pre-select all discovered calendars
                                // if selectedCalendarIds is empty so the user can uncheck specific ones easily.
                                val updatedSet = if (!checked && preferences.selectedCalendarIds.isEmpty() && availableCalendars.isNotEmpty()) {
                                    availableCalendars.map { it.id }.toSet()
                                } else {
                                    preferences.selectedCalendarIds
                                }
                                onUpdatePreferences(
                                    preferences.copy(
                                        isAllCalendarsSelected = checked,
                                        selectedCalendarIds = updatedSet
                                    )
                                )
                            },
                            modifier = Modifier.testTag("all_calendars_switch")
                        )
                    }
                }

                AnimatedVisibility(visible = !preferences.isAllCalendarsSelected) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (availableCalendars.isEmpty()) {
                            Text(
                                text = stringResource(R.string.widget_customizer_no_cals_permission),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            if (preferences.selectedCalendarIds.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.widget_customizer_no_cals_selected),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            availableCalendars.forEach { cal ->
                                val isChecked = preferences.selectedCalendarIds.contains(cal.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val newSet = if (isChecked) {
                                                preferences.selectedCalendarIds - cal.id
                                            } else {
                                                preferences.selectedCalendarIds + cal.id
                                            }
                                            onUpdatePreferences(preferences.copy(selectedCalendarIds = newSet))
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .testTag("calendar_filter_item_${cal.id}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            val newSet = if (checked) {
                                                preferences.selectedCalendarIds + cal.id
                                            } else {
                                                preferences.selectedCalendarIds - cal.id
                                            }
                                            onUpdatePreferences(preferences.copy(selectedCalendarIds = newSet))
                                        }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(CalendarColorMapper.parseColorHex(cal.colorHex), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cal.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = cal.accountName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. Display Toggles
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.widget_customizer_display_toggles),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.widget_customizer_show_locations),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = preferences.showLocations,
                        onCheckedChange = { onUpdatePreferences(preferences.copy(showLocations = it)) },
                        modifier = Modifier.testTag("show_locations_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.widget_customizer_show_day_headers),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = preferences.showDayHeaders,
                        onCheckedChange = { onUpdatePreferences(preferences.copy(showDayHeaders = it)) },
                        modifier = Modifier.testTag("show_headers_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.widget_customizer_show_past),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = preferences.showPastEvents,
                        onCheckedChange = { onUpdatePreferences(preferences.copy(showPastEvents = it)) },
                        modifier = Modifier.testTag("show_past_switch")
                    )
                }
            }
        }
    }
}
