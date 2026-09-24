package com.chase.planboard.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chase.planboard.data.Periods
import com.chase.planboard.data.PlanEntity
import com.chase.planboard.data.Scope
import com.chase.planboard.data.TodoEntity
import com.chase.planboard.data.startDate
import com.chase.planboard.ui.common.DatePickerModal
import com.chase.planboard.ui.common.Format
import com.chase.planboard.ui.common.TimePickerModal
import java.time.LocalDate
import java.time.LocalTime

/** Adds a to-do (when [todo] is null) or edits one: title plus an optional due date and time. */
@Composable
fun TodoDialog(
    todo: TodoEntity?,
    defaultDue: Long?,
    onSave: (title: String, dueAt: Long?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(todo?.title.orEmpty()) }
    var due by remember { mutableStateOf(if (todo != null) todo.dueAt else defaultDue) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (todo == null) runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (todo == null) "New to-do" else "Edit to-do") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text("What needs doing?") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                val dt = due?.let { Format.toDateTime(it) }
                Row(
                    Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedButton(onClick = { pickDate = true }) {
                        Icon(Icons.Filled.CalendarToday, null)
                        Spacer(Modifier.width(6.dp))
                        Text(dt?.let { Format.relativeDay(it.toLocalDate()) } ?: "Due date")
                    }
                    if (dt != null) {
                        OutlinedButton(onClick = { pickTime = true }) {
                            Icon(Icons.Filled.Schedule, null)
                            Spacer(Modifier.width(6.dp))
                            Text(Format.time(due!!))
                        }
                        IconButton(onClick = { due = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear due date")
                        }
                    }
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = { onDelete(); onDismiss() },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Delete to-do", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title, due); onDismiss() },
            ) { Text(if (todo == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickDate) {
        val current = due?.let { Format.toDateTime(it) }
        DatePickerModal(
            initial = current?.toLocalDate() ?: LocalDate.now(),
            onPick = { date -> due = Format.toMillis(date.atTime(current?.toLocalTime() ?: LocalTime.of(9, 0))) },
            onDismiss = { pickDate = false },
        )
    }
    if (pickTime && due != null) {
        val current = Format.toDateTime(due!!)
        TimePickerModal(
            hour = current.hour,
            minute = current.minute,
            onPick = { h, m -> due = Format.toMillis(current.toLocalDate().atTime(h, m)) },
            onDismiss = { pickTime = false },
        )
    }
}

/** Makes copies of a plan every day, week or month, e.g. "every week, 4 times". */
@Composable
fun RepeatDialog(plan: PlanEntity, onConfirm: (Scope, Int) -> Unit, onDismiss: () -> Unit) {
    // A plan can repeat at its own size or larger: a day plan daily/weekly/monthly, a month plan only monthly.
    val units = Scope.entries.filter { it.ordinal >= plan.scope.ordinal }
    var unit by remember { mutableStateOf(plan.scope) }
    var times by remember { mutableIntStateOf(4) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeat plan") },
        text = {
            Column {
                Text("Make copies, with their sub-plans and to-dos:", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    units.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = {
                                Text(
                                    when (u) {
                                        Scope.DAY -> "Daily"
                                        Scope.WEEK -> "Weekly"
                                        Scope.MONTH -> "Monthly"
                                    },
                                )
                            },
                        )
                    }
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (times > 1) times-- }) { Icon(Icons.Filled.Remove, contentDescription = "Fewer") }
                    Text(
                        if (times == 1) "1 time" else "$times times",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = { if (times < 52) times++ }) { Icon(Icons.Filled.Add, contentDescription = "More") }
                }
                val first = Periods.shift(unit, plan.startDate, 1)
                val last = Periods.shift(unit, plan.startDate, times.toLong())
                Text(
                    if (times == 1) {
                        "Copy: ${Format.period(plan.scope, first)}"
                    } else {
                        "From ${Format.period(plan.scope, first)} to ${Format.period(plan.scope, last)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(unit, times); onDismiss() }) { Text("Repeat") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
