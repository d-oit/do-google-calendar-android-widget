package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.pref.WidgetColorTheme
import com.example.data.pref.WidgetDensity
import com.example.data.pref.WidgetPreferences
import com.example.data.pref.WidgetTimeDisplayMode
import com.example.data.pref.WidgetTitleLineLimit
import com.example.data.repository.CalendarAccountInfo
import com.example.util.CalendarColorMapper
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WidgetCustomizerControls(
    preferences: WidgetPreferences,
    availableCalendars: List<CalendarAccountInfo>,
    onUpdatePreferences: (WidgetPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("widget_customizer_controls_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: Appearance (Background Opacity + Color Theme)
        CustomizerSectionCard(
            title = stringResource(R.string.widget_customizer_section_appearance),
            icon = Icons.Default.Palette,
            testTagName = "customizer_card_appearance"
        ) {
            // 1a. Smooth M3 Background Opacity Slider (continuous track — no discrete dots)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.widget_customizer_opacity_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    val opacityQualifier = when {
                        preferences.opacityPercent == 0 -> stringResource(R.string.widget_opacity_clear)
                        preferences.opacityPercent <= 25 -> stringResource(R.string.widget_opacity_glass)
                        preferences.opacityPercent <= 55 -> stringResource(R.string.widget_opacity_translucent)
                        preferences.opacityPercent <= 85 -> stringResource(R.string.widget_opacity_frosted)
                        else -> stringResource(R.string.widget_opacity_solid)
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "${stringResource(R.string.widget_customizer_opacity_value, preferences.opacityPercent)} · $opacityQualifier",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Slider(
                    value = preferences.opacityPercent.toFloat(),
                    onValueChange = { value ->
                        onUpdatePreferences(preferences.copy(opacityPercent = value.roundToInt().coerceIn(0, 100)))
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opacity_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 1b. Color Theme Selector (Multi-tone Material You swatch + Preset Swatches)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.widget_customizer_theme_label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WidgetColorTheme.values().forEach { theme ->
                        val isSelected = preferences.colorTheme == theme
                        ColorThemeOptionPill(
                            theme = theme,
                            isSelected = isSelected,
                            onClick = { onUpdatePreferences(preferences.copy(colorTheme = theme)) }
                        )
                    }
                }
            }
        }

        // Card 2: Typography & Layout (M3 Single-Choice Segmented Buttons)
        CustomizerSectionCard(
            title = stringResource(R.string.widget_customizer_section_typography),
            icon = Icons.Default.FormatSize,
            testTagName = "customizer_card_typography"
        ) {
            // 2a. Text Density
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.widget_customizer_density_label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val densities = WidgetDensity.values()
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    densities.forEachIndexed { index, density ->
                        val isSelected = preferences.density == density
                        SegmentedButton(
                            selected = isSelected,
                            onClick = { onUpdatePreferences(preferences.copy(density = density)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = densities.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                activeBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("density_chip_${density.id}")
                        ) {
                            Text(
                                text = stringResource(density.labelResId),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // 2b. Event Title Lines
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Title,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.widget_customizer_title_lines_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                val titleLimits = WidgetTitleLineLimit.values()
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    titleLimits.forEachIndexed { index, lineLimit ->
                        val isSelected = preferences.titleLineLimit == lineLimit
                        SegmentedButton(
                            selected = isSelected,
                            onClick = { onUpdatePreferences(preferences.copy(titleLineLimit = lineLimit)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = titleLimits.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                activeBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("title_lines_chip_${lineLimit.id}")
                        ) {
                            Text(
                                text = stringResource(lineLimit.labelResId),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // 2c. Event Time Display
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.widget_customizer_time_display_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                val timeModes = WidgetTimeDisplayMode.values()
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    timeModes.forEachIndexed { index, timeMode ->
                        val isSelected = preferences.timeDisplayMode == timeMode
                        SegmentedButton(
                            selected = isSelected,
                            onClick = { onUpdatePreferences(preferences.copy(timeDisplayMode = timeMode)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = timeModes.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                activeBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("time_display_chip_${timeMode.id}")
                        ) {
                            Text(
                                text = stringResource(timeMode.labelResId),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Card 3: Schedule & Calendars
        CustomizerSectionCard(
            title = stringResource(R.string.widget_customizer_section_schedule),
            icon = Icons.Default.DateRange,
            testTagName = "customizer_card_schedule"
        ) {
            // 3a. Upcoming Horizon
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.widget_customizer_horizon_label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("days_chip_$days")
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 3b. Multi-Calendar Selection Filter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.widget_customizer_calendars_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (preferences.isAllCalendarsSelected) {
                                stringResource(R.string.widget_customizer_calendars_all)
                            } else {
                                stringResource(R.string.widget_customizer_custom_filter)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Switch(
                        checked = preferences.isAllCalendarsSelected,
                        onCheckedChange = { checked ->
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

                AnimatedVisibility(visible = !preferences.isAllCalendarsSelected) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (availableCalendars.isEmpty()) {
                            Text(
                                text = stringResource(R.string.widget_customizer_no_cals_permission),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(4.dp)
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
                                        .clip(RoundedCornerShape(10.dp))
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
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
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
        }

        // Card 4: Display Options (Clean M3 Switch Rows)
        CustomizerSectionCard(
            title = stringResource(R.string.widget_customizer_section_display),
            icon = Icons.Default.Tune,
            testTagName = "customizer_card_display"
        ) {
            CustomizerSwitchRow(
                label = stringResource(R.string.widget_customizer_show_calendar_name),
                checked = preferences.showCalendarName,
                onCheckedChange = { onUpdatePreferences(preferences.copy(showCalendarName = it)) },
                switchTestTag = "show_calendar_name_switch"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            CustomizerSwitchRow(
                label = stringResource(R.string.widget_customizer_show_locations),
                checked = preferences.showLocations,
                onCheckedChange = { onUpdatePreferences(preferences.copy(showLocations = it)) },
                switchTestTag = "show_locations_switch"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            CustomizerSwitchRow(
                label = stringResource(R.string.widget_customizer_show_day_headers),
                checked = preferences.showDayHeaders,
                onCheckedChange = { onUpdatePreferences(preferences.copy(showDayHeaders = it)) },
                switchTestTag = "show_headers_switch"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            CustomizerSwitchRow(
                label = stringResource(R.string.widget_customizer_show_past),
                checked = preferences.showPastEvents,
                onCheckedChange = { onUpdatePreferences(preferences.copy(showPastEvents = it)) },
                switchTestTag = "show_past_switch"
            )
        }
    }
}

@Composable
private fun CustomizerSectionCard(
    title: String,
    icon: ImageVector,
    testTagName: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTagName),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun ColorThemeOptionPill(
    theme: WidgetColorTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }
    val labelColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("theme_chip_${theme.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (theme == WidgetColorTheme.DYNAMIC) {
                MaterialYouMultiToneSwatch(
                    primary = MaterialTheme.colorScheme.primary,
                    secondary = MaterialTheme.colorScheme.tertiary,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                val baseColor = Color(android.graphics.Color.parseColor(theme.primaryHex))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(baseColor)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Text(
                text = stringResource(theme.labelResId),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = labelColor
            )

            if (isSelected && theme == WidgetColorTheme.DYNAMIC) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * Distinct 4-quadrant wallpaper-inspired Material You swatch derived from the active
 * M3 dynamic color scheme roles (primary, tertiary, primaryContainer, secondary).
 */
@Composable
private fun MaterialYouMultiToneSwatch(
    primary: Color,
    secondary: Color,
    container: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val ringColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, ringColor, CircleShape)
    ) {
        val halfW = size.width / 2f
        val halfH = size.height / 2f
        drawRect(color = primary, topLeft = Offset.Zero, size = Size(halfW, halfH))
        drawRect(color = secondary, topLeft = Offset(halfW, 0f), size = Size(halfW, halfH))
        drawRect(color = container, topLeft = Offset(0f, halfH), size = Size(halfW, halfH))
        drawRect(color = accent, topLeft = Offset(halfW, halfH), size = Size(halfW, halfH))
    }
}

@Composable
private fun CustomizerSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(switchTestTag)
        )
    }
}
