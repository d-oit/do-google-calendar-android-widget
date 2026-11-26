package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.EventEntity
import com.example.util.AppLogger
import com.example.util.CalendarDateFormatter
import com.example.util.LogSanitizer
import com.example.util.rememberEventColorScheme

@Composable
fun EventDetailDialog(
    event: EventEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {}
) {
    val context = LocalContext.current
    val colorScheme = rememberEventColorScheme(event)
    var confirmingDelete by remember(event.id) { mutableStateOf(false) }

    val extractedLink = remember(event.location) {
        val loc = event.location ?: return@remember null
        Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(loc)?.value
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(colorScheme.primaryAccent, CircleShape)
                )

                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Badges row (Day + Calendar + Recurring)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (event.dayHeader.isNotEmpty() || event.startMillis > 0L) {
                        val localizedDay = remember(event.startMillis, context) {
                            CalendarDateFormatter.formatLocalizedDayHeader(context, event.startMillis)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colorScheme.badgeBackground,
                            border = BorderStroke(0.5.dp, colorScheme.badgeBorder)
                        ) {
                            Text(
                                text = localizedDay,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.badgeText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (event.calendarId.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = event.calendarId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (event.isRecurring) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = stringResource(R.string.detail_recurring_desc),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = stringResource(R.string.detail_recurring_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // Full Date & Time info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = colorScheme.timeIconTint,
                        modifier = Modifier.size(20.dp)
                    )
                    val timeStr = CalendarDateFormatter.formatFullDateTime(
                        startMillis = event.startMillis,
                        endMillis = event.endMillis,
                        isAllDay = event.isAllDay,
                        context = context
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.timeText
                    )
                }

                // Location / Video Link
                if (!event.location.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (extractedLink != null) {
                                    Modifier.clickable {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(extractedLink)).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        } catch (e: ActivityNotFoundException) {
                                            AppLogger.w(
                                                AppLogger.TAG_UI,
                                                "No app available to open link for event id=${event.id} (${LogSanitizer.sanitize(extractedLink)})",
                                                e
                                            )
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.msg_open_link_failed),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (e: SecurityException) {
                                            AppLogger.w(
                                                AppLogger.TAG_UI,
                                                "SecurityException opening link for event id=${event.id} (${LogSanitizer.sanitize(extractedLink)})",
                                                e
                                            )
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.msg_open_link_failed),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                } else Modifier
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (extractedLink != null) colorScheme.primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = event.location,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (extractedLink != null) colorScheme.primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = if (extractedLink != null) TextDecoration.Underline else TextDecoration.None
                        )
                    }
                }

                // Description / Notes
                if (!event.description.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(20.dp)
                        )
                        Text(
                            text = event.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("event_detail_description")
                        )
                    }
                }

                // Action Buttons: Edit + Open in Google Calendar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("edit_event_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_edit), fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            val link = event.htmlLink
                            val openIntent = if (!link.isNullOrBlank() &&
                                (link.startsWith("https://", true) || link.startsWith("http://", true) || link.startsWith("content://com.android.calendar/", true))
                            ) {
                                Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            } else {
                                val builder = Uri.parse("content://com.android.calendar/time").buildUpon()
                                ContentUris.appendId(builder, event.startMillis)
                                Intent(Intent.ACTION_VIEW, builder.build()).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            }
                            try {
                                context.startActivity(openIntent)
                            } catch (e: ActivityNotFoundException) {
                                AppLogger.w(AppLogger.TAG_UI, "No Calendar app available to open event id=${event.id}", e)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.msg_open_calendar_app_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: SecurityException) {
                                AppLogger.w(AppLogger.TAG_UI, "SecurityException opening Calendar app for event id=${event.id}", e)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.msg_open_calendar_app_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("open_in_google_calendar_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Launch,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.detail_action_open_gcal),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                // Inline Delete Confirmation Banner
                if (confirmingDelete) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (event.isRecurring) {
                                    stringResource(R.string.detail_delete_recurring_message)
                                } else {
                                    stringResource(R.string.detail_delete_confirm_message, event.title)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { confirmingDelete = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.action_keep))
                                }
                                Button(
                                    onClick = {
                                        onDelete()
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("confirm_delete_event_btn")
                                ) {
                                    Text(stringResource(R.string.detail_action_confirm_delete))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_event_detail_btn")
            ) {
                Text(stringResource(R.string.action_close))
            }
        },
        dismissButton = {
            if (!confirmingDelete) {
                TextButton(
                    onClick = { confirmingDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("delete_from_detail_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.detail_action_confirm_delete))
                }
            }
        }
    )
}
