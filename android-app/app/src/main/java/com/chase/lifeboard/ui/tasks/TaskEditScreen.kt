package com.chase.lifeboard.ui.tasks

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chase.lifeboard.alarm.Notifications
import com.chase.lifeboard.data.Recurrence
import com.chase.lifeboard.ui.Navigator
import com.chase.lifeboard.ui.common.ConfirmDialog
import com.chase.lifeboard.ui.common.DatePickerModal
import com.chase.lifeboard.ui.common.Format
import com.chase.lifeboard.ui.common.PriorityChips
import com.chase.lifeboard.ui.common.SectionHeader
import com.chase.lifeboard.ui.common.TaskRow
import com.chase.lifeboard.ui.common.TimePickerModal
import com.chase.lifeboard.ui.lifeBoardApp
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(taskId: Long, isNew: Boolean, navigator: Navigator) {
    val app = lifeBoardApp()
    val context = LocalContext.current
    val vm: TaskEditViewModel = viewModel(
        key = "task-$taskId",
        factory = viewModelFactory { initializer { TaskEditViewModel(app, taskId, isNew) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showRepeat by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var exactAlarmPrompt by remember { mutableStateOf(false) }
    var newSubtask by remember { mutableStateOf("") }

    val leave = {
        vm.onLeave()
        navigator.back()
    }
    BackHandler { leave() }

    var subtasks by remember { mutableStateOf(state?.subtasks.orEmpty()) }
    LaunchedEffect(state?.subtasks) { subtasks = state?.subtasks.orEmpty() }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = subtasks.indexOfFirst { it.task.id == from.key }
        val toIdx = subtasks.indexOfFirst { it.task.id == to.key }
        if (fromIdx >= 0 && toIdx >= 0) {
            subtasks = subtasks.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state?.task?.parentId != null) "Subtask" else "Task") },
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        val task = s.task
        val due = task.dueAt?.let { Format.toDateTime(it) }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            if (s.path.isNotEmpty()) {
                item(key = "path") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        s.path.forEachIndexed { i, ancestor ->
                            if (i > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                            TextButton(onClick = { navigator.openTask(ancestor.id) }) {
                                Text(ancestor.title.ifBlank { "Untitled" }, maxLines = 1)
                            }
                        }
                    }
                }
            }
            item(key = "title") {
                OutlinedTextField(
                    value = title,
                    onValueChange = vm::setTitle,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("What needs doing?") },
                    textStyle = MaterialTheme.typography.titleLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            item(key = "priority") {
                Column {
                    SectionHeader("Priority")
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        PriorityChips(task.priority, vm::setPriority)
                    }
                }
            }
            item(key = "when") {
                Column {
                    SectionHeader("When")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showDate = true }) {
                            Icon(Icons.Filled.CalendarToday, null)
                            Spacer(Modifier.width(6.dp))
                            Text(due?.let { Format.relativeDay(it.toLocalDate()) } ?: "Add date")
                        }
                        if (due != null) {
                            OutlinedButton(onClick = { showTime = true }) {
                                Icon(Icons.Filled.Schedule, null)
                                Spacer(Modifier.width(6.dp))
                                Text(Format.time(task.dueAt!!))
                            }
                            IconButton(onClick = { vm.setDue(null) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear date")
                            }
                        }
                    }
                    if (due != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Alarm, null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Alarm")
                                Text(
                                    if (task.alarmEnabled) "Rings at ${Format.due(task.dueAt!!)}" else "Off",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = task.alarmEnabled,
                                onCheckedChange = { on ->
                                    vm.setAlarm(on)
                                    if (on) {
                                        if (!Notifications.canPost(context)) {
                                            scope.launch {
                                                snackbar.showSnackbar("Notifications are off for LifeBoard — turn them on in Settings so alarms can ring.")
                                            }
                                        } else if (!app.alarmScheduler.canScheduleExact()) {
                                            exactAlarmPrompt = true
                                        }
                                        if (task.dueAt!! < System.currentTimeMillis()) {
                                            scope.launch { snackbar.showSnackbar("That time has already passed — pick a later time.") }
                                        }
                                    }
                                },
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Repeat, null)
                            Spacer(Modifier.width(12.dp))
                            Box(Modifier.weight(1f)) {
                                AssistChip(onClick = { showRepeat = true }, label = { Text(task.recurrence.label) })
                                DropdownMenu(expanded = showRepeat, onDismissRequest = { showRepeat = false }) {
                                    Recurrence.entries.forEach { r ->
                                        DropdownMenuItem(
                                            text = { Text(r.label) },
                                            onClick = { vm.setRecurrence(r); showRepeat = false },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "notes") {
                Column {
                    SectionHeader("Notes")
                    OutlinedTextField(
                        value = notes,
                        onValueChange = vm::setNotes,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Details, links, anything…") },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            }
            item(key = "subtasks-header") {
                val done = subtasks.count { it.task.completed }
                SectionHeader(if (subtasks.isEmpty()) "Subtasks" else "Subtasks ($done/${subtasks.size})")
            }
            items(subtasks, key = { it.task.id }) { item ->
                ReorderableItem(reorderState, key = item.task.id) { isDragging ->
                    TaskRow(
                        task = item.task,
                        subtaskDone = item.subtasksDone,
                        subtaskTotal = item.subtasksTotal,
                        elevated = isDragging,
                        modifier = Modifier.padding(vertical = 4.dp),
                        onToggle = { done ->
                            vm.toggle(item.task, done) { next ->
                                scope.launch { snackbar.showSnackbar("Repeats — next due ${Format.due(next)}") }
                            }
                        },
                        onClick = { navigator.openTask(item.task.id) },
                        trailing = {
                            IconButton(
                                onClick = {},
                                modifier = Modifier.draggableHandle(
                                    onDragStopped = { vm.reorderSubtasks(subtasks.map { it.task.id }) },
                                ),
                            ) {
                                Icon(
                                    Icons.Filled.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
            item(key = "add-subtask") {
                val submit = {
                    vm.addSubtask(newSubtask)
                    newSubtask = ""
                }
                OutlinedTextField(
                    value = newSubtask,
                    onValueChange = { newSubtask = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    placeholder = { Text("Add a subtask") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Add, null) },
                    trailingIcon = {
                        if (newSubtask.isNotBlank()) {
                            TextButton(onClick = submit) { Text("Add") }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Text(
                    "Tap a subtask to give it its own date, alarm, priority or subtasks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 32.dp),
                )
            }
        }

        if (showDate) {
            DatePickerModal(
                initial = due?.toLocalDate() ?: LocalDate.now(),
                onPick = { date ->
                    val time = due?.toLocalTime() ?: LocalTime.of(9, 0)
                    vm.setDue(Format.toMillis(date.atTime(time)))
                },
                onDismiss = { showDate = false },
            )
        }
        if (showTime && due != null) {
            TimePickerModal(
                hour = due.hour,
                minute = due.minute,
                onPick = { h, m -> vm.setDue(Format.toMillis(due.toLocalDate().atTime(h, m))) },
                onDismiss = { showTime = false },
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete task?",
            text = "This also deletes all of its subtasks.",
            confirm = "Delete",
            onConfirm = {
                vm.delete()
                navigator.back()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (exactAlarmPrompt && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        AlertDialog(
            onDismissRequest = { exactAlarmPrompt = false },
            title = { Text("Allow exact alarms") },
            text = { Text("To ring at exactly the time you set, LifeBoard needs the “Alarms & reminders” permission.") },
            confirmButton = {
                TextButton(onClick = {
                    exactAlarmPrompt = false
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { exactAlarmPrompt = false }) { Text("Not now") } },
        )
    }
}
