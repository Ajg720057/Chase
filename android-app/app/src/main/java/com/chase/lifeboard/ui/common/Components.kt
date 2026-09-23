package com.chase.lifeboard.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chase.lifeboard.data.Priority
import com.chase.lifeboard.data.Recurrence
import com.chase.lifeboard.data.TaskEntity
import com.chase.lifeboard.ui.theme.PriorityColors
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * One task line: priority stripe, checkbox, title and a row of small badges
 * (due date, alarm, repeat, subtask progress). [trailing] is used for drag handles.
 */
@Composable
fun TaskRow(
    task: TaskEntity,
    subtaskDone: Int,
    subtaskTotal: Int,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showCheckbox: Boolean = true,
    elevated: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (elevated) 6.dp else 1.dp,
        shadowElevation = if (elevated) 8.dp else 0.dp,
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(PriorityColors.of(task.priority) ?: Color.Transparent),
            )
            if (showCheckbox) {
                Checkbox(checked = task.completed, onCheckedChange = onToggle)
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    task.title.ifBlank { "Untitled task" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (task.priority == Priority.HIGH && !task.completed) FontWeight.SemiBold else null,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val due = task.dueAt
                if (due != null || subtaskTotal > 0 || task.recurrence != Recurrence.NONE) {
                    Row(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (due != null) {
                            val overdue = !task.completed && due < System.currentTimeMillis()
                            Badge(
                                if (task.alarmEnabled) Icons.Filled.Alarm else Icons.Filled.Schedule,
                                Format.due(due),
                                if (overdue) MaterialTheme.colorScheme.error else null,
                            )
                        }
                        if (task.recurrence != Recurrence.NONE) Badge(Icons.Filled.Repeat, null)
                        if (subtaskTotal > 0) Badge(Icons.AutoMirrored.Filled.List, "$subtaskDone/$subtaskTotal")
                    }
                }
            }
            trailing()
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, text: String?, tint: Color? = null) {
    val color = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        if (text != null) {
            Spacer(Modifier.width(3.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
fun PriorityChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Priority.NONE, Priority.LOW, Priority.MEDIUM, Priority.HIGH).forEach { p ->
            val color = PriorityColors.of(p)
            FilterChip(
                selected = selected == p,
                onClick = { onSelect(p) },
                label = { Text(Priority.label(p)) },
                leadingIcon = if (color != null) {
                    { Box(Modifier.size(10.dp).background(color, CircleShape)) }
                } else {
                    null
                },
                colors = if (color != null) {
                    FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.22f))
                } else {
                    FilterChipDefaults.filterChipColors()
                },
            )
        }
    }
}

/** Material date picker; works in LocalDate so callers never touch its UTC millis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerModal(hour: Int, minute: Int, onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.hour, state.minute); onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

