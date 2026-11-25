package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.EventEntity
import com.example.data.repository.CalendarAccountInfo
import com.example.util.CalendarColorMapper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEventDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        location: String?,
        description: String?,
        startMillis: Long,
        endMillis: Long,
        isAllDay: Boolean,
        colorHex: String,
        targetCalendarId: Long?
    ) -> Unit,
    initialEvent: EventEntity? = null,
    availableCalendars: List<CalendarAccountInfo> = emptyList()
) {
    val writableCalendars = remember(availableCalendars) {
        availableCalendars.filter { it.isWritable }
    }

    val initialCal = remember(initialEvent) {
        Calendar.getInstance().apply {
            if (initialEvent != null) {
                timeInMillis = initialEvent.startMillis
            } else {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    }

    val initialEndCal = remember(initialEvent) {
        Calendar.getInstance().apply {
            if (initialEvent != null) {
                timeInMillis = initialEvent.endMillis
            } else {
                timeInMillis = initialCal.timeInMillis + 3600000L
            }
        }
    }

    var title by remember(initialEvent) { mutableStateOf(initialEvent?.title ?: "") }
    var location by remember(initialEvent) { mutableStateOf(initialEvent?.location ?: "") }
    var description by remember(initialEvent) { mutableStateOf(initialEvent?.description ?: "") }
    var isAllDay by remember(initialEvent) { mutableStateOf(initialEvent?.isAllDay ?: false) }

    var selectedYear by remember(initialEvent) { mutableIntStateOf(initialCal.get(Calendar.YEAR)) }
    var selectedMonth by remember(initialEvent) { mutableIntStateOf(initialCal.get(Calendar.MONTH)) }
    var selectedDayOfMonth by remember(initialEvent) { mutableIntStateOf(initialCal.get(Calendar.DAY_OF_MONTH)) }

    var startHour by remember(initialEvent) { mutableIntStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var startMinute by remember(initialEvent) { mutableIntStateOf(initialCal.get(Calendar.MINUTE)) }
    var endHour by remember(initialEvent) { mutableIntStateOf(initialEndCal.get(Calendar.HOUR_OF_DAY)) }
    var endMinute by remember(initialEvent) { mutableIntStateOf(initialEndCal.get(Calendar.MINUTE)) }

    var selectedColor by remember(initialEvent) {
        mutableStateOf(initialEvent?.colorHex ?: CalendarColorMapper.DEFAULT_COLOR_HEX)
    }

    val defaultCalId = remember(initialEvent, writableCalendars) {
        initialEvent?.sourceCalendarId?.takeIf { id -> writableCalendars.any { it.id == id } }
            ?: writableCalendars.firstOrNull { it.isPrimary }?.id
            ?: writableCalendars.firstOrNull()?.id
    }
    var selectedCalendarId by remember(initialEvent, writableCalendars) {
        mutableStateOf<Long?>(defaultCalId)
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showTitleError by remember { mutableStateOf(false) }

    val colorPalette = remember {
        listOf(
            "#1A73E8" to "Blue",
            "#33B679" to "Sage",
            "#8E24AA" to "Grape",
            "#E67C73" to "Flamingo",
            "#F6BF26" to "Banana",
            "#F4511E" to "Tangerine",
            "#039BE5" to "Peacock",
            "#0B8043" to "Basil",
            "#D50000" to "Tomato"
        )
    }

    val formattedDate = remember(selectedYear, selectedMonth, selectedDayOfMonth) {
        val c = Calendar.getInstance().apply {
            set(selectedYear, selectedMonth, selectedDayOfMonth, 0, 0, 0)
        }
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(c.time)
    }

    val formattedStartTime = remember(startHour, startMinute) {
        formatHourMinute(startHour, startMinute)
    }

    val formattedEndTime = remember(endHour, endMinute) {
        formatHourMinute(endHour, endMinute)
    }

    val currentDurationMinutes = remember(startHour, startMinute, endHour, endMinute) {
        ((endHour * 60 + endMinute) - (startHour * 60 + startMinute)).coerceAtLeast(15)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialEvent == null) "Add Event to Agenda" else "Edit Event",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) showTitleError = false
                    },
                    label = { Text("Event Title") },
                    placeholder = { Text("e.g., Team Sync, Doctor Visit") },
                    isError = showTitleError,
                    supportingText = if (showTitleError) {
                        { Text("Please enter an event title") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("event_title_input")
                )

                // All-Day Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All-day event",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = isAllDay,
                        onCheckedChange = { isAllDay = it },
                        modifier = Modifier.testTag("event_all_day_switch")
                    )
                }

                // Date Picker Trigger
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("event_date_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedDate,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Change", style = MaterialTheme.typography.labelMedium)
                }

                if (!isAllDay) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showStartTimePicker = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("event_start_time_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(formattedStartTime, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { showEndTimePicker = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("event_end_time_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(formattedEndTime, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Quick duration chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(30 to "30m", 60 to "1h", 90 to "1.5h", 120 to "2h").forEach { (mins, label) ->
                            FilterChip(
                                selected = currentDurationMinutes == mins,
                                onClick = {
                                    val totalEndMins = (startHour * 60 + startMinute + mins).coerceAtMost(23 * 60 + 59)
                                    endHour = totalEndMins / 60
                                    endMinute = totalEndMins % 60
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("duration_chip_$mins")
                            )
                        }
                    }
                }

                // Target Calendar Selector
                if (writableCalendars.isNotEmpty()) {
                    Text(
                        text = "Calendar",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        writableCalendars.forEach { cal ->
                            val isSelected = selectedCalendarId == cal.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCalendarId = cal.id },
                                label = { Text(cal.displayName) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(CalendarColorMapper.parseColorHex(cal.colorHex), CircleShape)
                                    )
                                },
                                modifier = Modifier.testTag("target_calendar_chip_${cal.id}")
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location or Video Link (Optional)") },
                    placeholder = { Text("e.g., Room 3A, https://meet.google.com/...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("event_location_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes / Description (Optional)") },
                    placeholder = { Text("Agenda items, links, or notes") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("event_description_input")
                )

                Text(
                    text = "Event Color",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    colorPalette.forEach { (hex, label) ->
                        val isSelected = selectedColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { selectedColor = hex }
                                .semantics { contentDescription = "$label color" }
                                .testTag("color_swatch_$label"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(CalendarColorMapper.parseColorHex(hex))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        showTitleError = true
                        return@Button
                    }

                    val startCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, selectedYear)
                        set(Calendar.MONTH, selectedMonth)
                        set(Calendar.DAY_OF_MONTH, selectedDayOfMonth)
                        set(Calendar.HOUR_OF_DAY, if (isAllDay) 0 else startHour)
                        set(Calendar.MINUTE, if (isAllDay) 0 else startMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val startMillis = startCal.timeInMillis

                    val endMillis = if (isAllDay) {
                        startMillis + 24L * 60L * 60L * 1000L
                    } else {
                        val endCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, selectedYear)
                            set(Calendar.MONTH, selectedMonth)
                            set(Calendar.DAY_OF_MONTH, selectedDayOfMonth)
                            set(Calendar.HOUR_OF_DAY, endHour)
                            set(Calendar.MINUTE, endMinute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        if (endCal.timeInMillis <= startMillis) {
                            startMillis + 3600000L
                        } else {
                            endCal.timeInMillis
                        }
                    }

                    onConfirm(
                        title.trim(),
                        location.trim().ifEmpty { null },
                        description.trim().ifEmpty { null },
                        startMillis,
                        endMillis,
                        isAllDay,
                        selectedColor,
                        selectedCalendarId
                    )
                },
                modifier = Modifier.testTag("save_event_button")
            ) {
                Text(if (initialEvent == null) "Add to Schedule" else "Save Changes")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_event_button")
            ) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        val initialUtcDateMillis = remember(selectedYear, selectedMonth, selectedDayOfMonth) {
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(selectedYear, selectedMonth, selectedDayOfMonth, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcDateMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = utcMillis
                            }
                            selectedYear = utcCal.get(Calendar.YEAR)
                            selectedMonth = utcCal.get(Calendar.MONTH)
                            selectedDayOfMonth = utcCal.get(Calendar.DAY_OF_MONTH)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = startHour,
            initialMinute = startMinute
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text("Select Start Time", fontWeight = FontWeight.Bold) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val prevDuration = currentDurationMinutes
                        startHour = timeState.hour
                        startMinute = timeState.minute
                        val newEndMins = (startHour * 60 + startMinute + prevDuration).coerceAtMost(23 * 60 + 59)
                        endHour = newEndMins / 60
                        endMinute = newEndMins % 60
                        showStartTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEndTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = endHour,
            initialMinute = endMinute
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text("Select End Time", fontWeight = FontWeight.Bold) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startTotal = startHour * 60 + startMinute
                        val pickedTotal = timeState.hour * 60 + timeState.minute
                        if (pickedTotal <= startTotal) {
                            val clamped = (startTotal + 30).coerceAtMost(23 * 60 + 59)
                            endHour = clamped / 60
                            endMinute = clamped % 60
                        } else {
                            endHour = timeState.hour
                            endMinute = timeState.minute
                        }
                        showEndTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatHourMinute(hour: Int, minute: Int): String {
    val c = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(c.timeInMillis))
}
