package com.chase.planboard.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chase.planboard.data.Periods
import com.chase.planboard.data.PlanEntity
import com.chase.planboard.data.PlanNoteEntity
import com.chase.planboard.data.PlanStatus
import com.chase.planboard.data.Scope
import com.chase.planboard.data.TodoEntity
import com.chase.planboard.data.startDate
import com.chase.planboard.ui.Navigator
import com.chase.planboard.ui.common.ConfirmDialog
import com.chase.planboard.ui.common.DatePickerModal
import com.chase.planboard.ui.common.Format
import com.chase.planboard.ui.common.PlanCard
import com.chase.planboard.ui.common.SectionHeader
import com.chase.planboard.ui.common.TimePickerModal
import com.chase.planboard.ui.planBoardApp
import com.chase.planboard.ui.theme.ScopeColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditScreen(planId: Long, isNew: Boolean, navigator: Navigator) {
    val app = planBoardApp()
    val vm: PlanEditViewModel = viewModel(
        key = "plan-$planId",
        factory = viewModelFactory { initializer { PlanEditViewModel(app, planId, isNew) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var menuOpen by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showDuplicate by remember { mutableStateOf(false) }
    var showRepeat by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<TodoEntity?>(null) }
    var addingTodo by remember { mutableStateOf(false) }
    var newNote by remember { mutableStateOf("") }

    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            val result = snackbar.showSnackbar(m.text, actionLabel = if (m.openPlanId != null) "Open" else null)
            if (result == SnackbarResult.ActionPerformed && m.openPlanId != null) navigator.openPlan(m.openPlanId)
        }
    }

    val leave = {
        vm.onLeave()
        navigator.back()
    }
    BackHandler { leave() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.plan?.let { "${it.scope.label} plan" } ?: "Plan") },
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Duplicate to…") },
                            onClick = { menuOpen = false; showDuplicate = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Repeat…") },
                            onClick = { menuOpen = false; showRepeat = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
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
        val plan = s.plan

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
        ) {
            if (s.path.isNotEmpty()) {
                item(key = "path") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        s.path.forEachIndexed { i, ancestor ->
                            if (i > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                            TextButton(onClick = { navigator.openPlan(ancestor.id) }) {
                                Text(ancestor.title.ifBlank { "Untitled plan" }, maxLines = 1)
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
                    placeholder = { Text("What's the plan?") },
                    textStyle = MaterialTheme.typography.titleLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            item(key = "when") {
                Column {
                    SectionHeader("When")
                    ScopePicker(plan.scope, vm::setScope)
                    Row(
                        Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { showDate = true }) {
                            Icon(Icons.Filled.CalendarToday, null)
                            Spacer(Modifier.width(6.dp))
                            Text(Format.period(plan))
                        }
                        if (plan.scope == Scope.DAY) {
                            val minute = plan.startMinute
                            OutlinedButton(onClick = { showTime = true }) {
                                Icon(Icons.Filled.Schedule, null)
                                Spacer(Modifier.width(6.dp))
                                Text(minute?.let { Format.minuteOfDay(it) } ?: "Add time")
                            }
                            if (minute != null) {
                                IconButton(onClick = { vm.setTime(null) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear time")
                                }
                            }
                        }
                    }
                }
            }
            item(key = "status") {
                Column {
                    SectionHeader("Status")
                    StatusPicker(plan.status, vm::setStatus)
                }
            }
            item(key = "details") {
                Column {
                    SectionHeader("Details")
                    OutlinedTextField(
                        value = details,
                        onValueChange = vm::setDetails,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Goals, ideas, links, anything…") },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            }

            // --- Sub-plans ---
            item(key = "sub-header") {
                val done = s.subPlans.count { it.plan.status == PlanStatus.DONE }
                SectionHeader(if (s.subPlans.isEmpty()) "Break it down" else "Break it down ($done/${s.subPlans.size} done)")
            }
            items(s.subPlans, key = { "sub-${it.plan.id}" }) { item ->
                PlanCard(
                    item = item,
                    subtitle = Format.period(item.plan),
                    onClick = { navigator.openPlan(item.plan.id) },
                    onStatusClick = { vm.cycleSubPlanStatus(item.plan) },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            item(key = "sub-add") {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    childScopes(plan.scope).forEach { child ->
                        OutlinedButton(onClick = { navigator.newPlan(child, childDate(plan), parentId = plan.id) }) {
                            Icon(Icons.Filled.Add, null, tint = ScopeColors.of(child))
                            Spacer(Modifier.width(4.dp))
                            Text(if (plan.scope == Scope.DAY) "Step" else "${child.label} plan")
                        }
                    }
                }
            }

            // --- To-dos ---
            item(key = "todo-header") {
                val done = s.todos.count { it.done }
                SectionHeader(if (s.todos.isEmpty()) "To-dos" else "To-dos ($done/${s.todos.size})")
            }
            item(key = "lifeboard") {
                LifeBoardSwitch(
                    plan = plan,
                    installed = vm.lifeBoardInstalled,
                    available = vm.lifeBoardAvailable,
                    onChange = vm::setLinkToLifeBoard,
                )
            }
            items(s.todos, key = { "todo-${it.id}" }) { todo ->
                TodoRow(
                    todo = todo,
                    canLink = vm.lifeBoardAvailable,
                    onToggle = { vm.setTodoDone(todo, it) },
                    onClick = { editingTodo = todo },
                    onSend = { vm.sendTodo(todo) },
                    onOpen = { vm.openInLifeBoard(todo) },
                    onUnlink = { vm.unlinkTodo(todo) },
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
            item(key = "todo-add") {
                OutlinedButton(onClick = { addingTodo = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add to-do")
                }
            }

            // --- Progress log ---
            item(key = "log-header") { SectionHeader("Progress log") }
            item(key = "log-add") {
                val submit = {
                    vm.addNote(newNote)
                    newNote = ""
                }
                OutlinedTextField(
                    value = newNote,
                    onValueChange = { newNote = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("How's it going? Add an update") },
                    trailingIcon = {
                        if (newNote.isNotBlank()) TextButton(onClick = submit) { Text("Add") }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
            }
            items(s.notes, key = { "note-${it.id}" }) { note ->
                NoteRow(note, onDelete = { vm.deleteNote(note) }, modifier = Modifier.padding(vertical = 3.dp))
            }
            item(key = "bottom") { Spacer(Modifier.padding(bottom = 32.dp)) }
        }

        if (showDate) {
            DatePickerModal(initial = plan.startDate, onPick = vm::setDate, onDismiss = { showDate = false })
        }
        if (showTime) {
            val minute = plan.startMinute ?: (9 * 60)
            TimePickerModal(
                hour = minute / 60,
                minute = minute % 60,
                onPick = { h, m -> vm.setTime(h * 60 + m) },
                onDismiss = { showTime = false },
            )
        }
        if (showDuplicate) {
            DatePickerModal(
                initial = Periods.shift(plan.scope, plan.startDate, 1),
                onPick = vm::duplicate,
                onDismiss = { showDuplicate = false },
            )
        }
        if (showRepeat) {
            RepeatDialog(plan = plan, onConfirm = vm::repeat, onDismiss = { showRepeat = false })
        }
        if (addingTodo || editingTodo != null) {
            TodoDialog(
                todo = editingTodo,
                defaultDue = defaultTodoDue(plan),
                onSave = { t, due ->
                    val editing = editingTodo
                    if (editing == null) vm.addTodo(t, due) else vm.editTodo(editing, t, due)
                },
                onDelete = editingTodo?.let { t -> { vm.deleteTodo(t) } },
                onDismiss = { addingTodo = false; editingTodo = null },
            )
        }
    }

    if (confirmDelete) {
        val linked = state?.todos?.any { it.lifeBoardTaskId != null } == true
        ConfirmDialog(
            title = "Delete plan?",
            text = "This also deletes its sub-plans, to-dos and progress log." +
                if (linked) " To-dos sent to LifeBoard are removed there too." else "",
            confirm = "Delete",
            onConfirm = {
                vm.delete()
                navigator.back()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** What a plan can be broken into: a month into weeks or days, a week into days, a day into steps. */
private fun childScopes(scope: Scope): List<Scope> = when (scope) {
    Scope.MONTH -> listOf(Scope.WEEK, Scope.DAY)
    Scope.WEEK -> listOf(Scope.DAY)
    Scope.DAY -> listOf(Scope.DAY)
}

/** New sub-plans start today if today is inside the parent's period, else at its start. */
private fun childDate(parent: PlanEntity): LocalDate {
    val today = LocalDate.now()
    return if (Periods.contains(parent.scope, parent.startDate, today)) today else parent.startDate
}

/** A to-do added to a day plan with a set time is due then; otherwise it has no date. */
private fun defaultTodoDue(plan: PlanEntity): Long? {
    val minute = plan.startMinute ?: return null
    if (plan.scope != Scope.DAY) return null
    return Format.toMillis(plan.startDate.atTime(minute / 60, minute % 60))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopePicker(selected: Scope, onSelect: (Scope) -> Unit) {
    val options = listOf(Scope.DAY, Scope.WEEK, Scope.MONTH)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, scope ->
            SegmentedButton(
                selected = selected == scope,
                onClick = { onSelect(scope) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(scope.label) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusPicker(selected: PlanStatus, onSelect: (PlanStatus) -> Unit) {
    val options = PlanStatus.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, status ->
            SegmentedButton(
                selected = selected == status,
                onClick = { onSelect(status) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(status.label, maxLines = 1) }
        }
    }
}

@Composable
private fun LifeBoardSwitch(plan: PlanEntity, installed: Boolean, available: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Link, null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Send to-dos to LifeBoard")
            Text(
                when {
                    !installed -> "Install LifeBoard to link this plan's to-dos to your to-do list."
                    !available -> "Update LifeBoard to its latest build to link to-dos."
                    plan.linkToLifeBoard -> "On. New to-dos are added to LifeBoard, and checking one off in either app checks it off in both."
                    else -> "Off. You can still send single to-dos with the link button."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = plan.linkToLifeBoard, onCheckedChange = onChange, enabled = available)
    }
}

@Composable
private fun TodoRow(
    todo: TodoEntity,
    canLink: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onSend: () -> Unit,
    onOpen: () -> Unit,
    onUnlink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var linkMenu by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
        Row(Modifier.clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = todo.done, onCheckedChange = onToggle)
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    todo.title.ifBlank { "Untitled to-do" },
                    textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                    color = if (todo.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(
                    todo.dueAt?.let { Format.due(it) },
                    if (todo.lifeBoardTaskId != null) "In LifeBoard" else null,
                ).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                todo.lifeBoardTaskId != null -> Box {
                    IconButton(onClick = { linkMenu = true }) {
                        Icon(Icons.Filled.Link, contentDescription = "Linked to LifeBoard", tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = linkMenu, onDismissRequest = { linkMenu = false }) {
                        DropdownMenuItem(text = { Text("Open in LifeBoard") }, onClick = { linkMenu = false; onOpen() })
                        DropdownMenuItem(text = { Text("Stop syncing") }, onClick = { linkMenu = false; onUnlink() })
                    }
                }
                canLink -> IconButton(onClick = onSend) {
                    Icon(
                        Icons.Filled.AddLink,
                        contentDescription = "Send to LifeBoard",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: PlanNoteEntity, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    var confirm by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(start = 14.dp, top = 10.dp, bottom = 10.dp)) {
                Text(
                    Format.stamp(note.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(note.text, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { confirm = true }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Delete note",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Delete note?",
            text = note.text,
            confirm = "Delete",
            onConfirm = onDelete,
            onDismiss = { confirm = false },
        )
    }
}
