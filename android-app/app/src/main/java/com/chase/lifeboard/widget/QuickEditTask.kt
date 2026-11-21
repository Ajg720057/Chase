package com.chase.lifeboard.widget

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.chase.lifeboard.MainActivity
import com.chase.lifeboard.alarm.Notifications
import com.chase.lifeboard.data.Recurrence
import com.chase.lifeboard.data.TaskEntity
import com.chase.lifeboard.ui.common.DatePickerModal
import com.chase.lifeboard.ui.common.Format
import com.chase.lifeboard.ui.common.PriorityChips
import com.chase.lifeboard.ui.common.TimePickerModal
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Quick edit of a task's title, priority, date/time, alarm and repeat from the
 * widget. Notes and subtasks stay in the full editor ("Open in app").
 */
@Composable
internal fun QuickAddActivity.EditTaskForm(taskId: Long) {
    val app = lifeBoardApp
    var task by remember { mutableStateOf<TaskEntity?>(null) }
    var missing by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var alarm by remember { mutableStateOf(false) }
    var recurrence by remember { mutableStateOf(Recurrence.NONE) }

    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showRepeat by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) {
        val t = app.tasks.get(taskId)
        if (t == null) {
            missing = true
            return@LaunchedEffect
        }
        task = t
        title = t.title
        priority = t.priority
        t.dueAt?.let {
            val dt = Format.toDateTime(it)
            date = dt.toLocalDate()
            time = dt.toLocalTime()
        }
        alarm = t.alarmEnabled
        recurrence = t.recurrence
    }

    if (missing) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("This task no longer exists.", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { finish() }) { Text("Close") }
        }
        return
    }
    if (task == null) return

    fun save(openApp: Boolean) {
        lifecycleScope.launch {
            // Re-read so anything changed elsewhere (notes, done state) isn't overwritten.
            val fresh = app.tasks.get(taskId)
            if (fresh != null) {
                val d = date
                val dueAt = d?.let { Format.toMillis(it.atTime(time)) }
                app.tasks.update(
                    fresh.copy(
                        title = title.trim(),
                        priority = priority,
                        dueAt = dueAt,
                        alarmEnabled = alarm && dueAt != null,
                        recurrence = if (dueAt != null) recurrence else Recurrence.NONE,
                    ),
                )
                if (alarm && dueAt != null && !Notifications.canPost(this@EditTaskForm)) {
                    Toast.makeText(
                        this@EditTaskForm,
                        "Notifications are off for LifeBoard, so the alarm can't ring",
                        Toast.LENGTH_LONG,
                    ).show()
                } else if (!openApp) {
                    Toast.makeText(this@EditTaskForm, "Task updated", Toast.LENGTH_SHORT).show()
                }
            }
            if (openApp) startActivity(MainActivity.openTaskIntent(this@EditTaskForm, taskId))
            finish()
        }
    }

    val today = LocalDate.now()
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Edit task", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text("Task title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            PriorityChips(priority) { priority = it }
        }

        // Date: one-tap shortcuts plus a full picker.
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Today" to today, "Tomorrow" to today.plusDays(1), "Next week" to today.plusWeeks(1))
                .forEach { (label, d) ->
                    FilterChip(selected = date == d, onClick = { date = d }, label = { Text(label) })
                }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showDate = true }) {
                Icon(Icons.Filled.Event, null)
                Spacer(Modifier.width(6.dp))
                Text(date?.let { Format.relativeDay(it) } ?: "Pick date")
            }
            if (date != null) {
                OutlinedButton(onClick = { showTime = true }) {
                    Icon(Icons.Filled.Schedule, null)
                    Spacer(Modifier.width(6.dp))
                    Text(Format.time(Format.toMillis(date!!.atTime(time))))
                }
                IconButton(onClick = { date = null; alarm = false; recurrence = Recurrence.NONE }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear date")
                }
            }
        }

        if (date != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Alarm, null)
                Spacer(Modifier.width(12.dp))
                Text("Alarm", Modifier.weight(1f))
                Switch(checked = alarm, onCheckedChange = { alarm = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Repeat, null)
                Spacer(Modifier.width(12.dp))
                Box {
                    AssistChip(onClick = { showRepeat = true }, label = { Text(recurrence.label) })
                    DropdownMenu(expanded = showRepeat, onDismissRequest = { showRepeat = false }) {
                        Recurrence.entries.forEach { r ->
                            DropdownMenuItem(text = { Text(r.label) }, onClick = { recurrence = r; showRepeat = false })
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { save(openApp = true) }) { Text("Open in app") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { finish() }) { Text("Cancel") }
            Button(onClick = { save(openApp = false) }) { Text("Save") }
        }
    }

    if (showDate) {
        DatePickerModal(
            initial = date ?: today,
            onPick = { date = it },
            onDismiss = { showDate = false },
        )
    }
    if (showTime) {
        TimePickerModal(
            hour = time.hour,
            minute = time.minute,
            onPick = { h, m -> time = LocalTime.of(h, m) },
            onDismiss = { showTime = false },
        )
    }
}
