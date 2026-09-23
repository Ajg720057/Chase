package com.chase.lifeboard.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chase.lifeboard.ui.Navigator
import com.chase.lifeboard.ui.common.BackupMenu
import com.chase.lifeboard.ui.common.EmptyState
import com.chase.lifeboard.ui.common.Format
import com.chase.lifeboard.ui.common.TaskRow
import com.chase.lifeboard.ui.lifeBoardApp
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(navigator: Navigator) {
    val app = lifeBoardApp()
    val vm: TaskListViewModel = viewModel(factory = viewModelFactory { initializer { TaskListViewModel(app) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sortMenu by remember { mutableStateOf(false) }

    val canDrag = sort == TaskSort.MANUAL && filter == TaskFilter.ACTIVE
    // Local copy that moves instantly while dragging; saved when the drag ends.
    var items by remember { mutableStateOf(state?.items.orEmpty()) }
    LaunchedEffect(state) { items = state?.items.orEmpty() }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = items.indexOfFirst { it.task.id == from.key }
        val toIdx = items.indexOfFirst { it.task.id == to.key }
        if (fromIdx >= 0 && toIdx >= 0) {
            items = items.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                actions = {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        TaskSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                leadingIcon = { if (s == sort) Icon(Icons.Filled.Check, null) },
                                onClick = { vm.setSort(s); sortMenu = false },
                            )
                        }
                    }
                    BackupMenu()
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigator.newTask() },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New task") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == TaskFilter.ACTIVE,
                    onClick = { vm.setFilter(TaskFilter.ACTIVE) },
                    label = { Text("To do (${state?.activeCount ?: 0})") },
                )
                FilterChip(
                    selected = filter == TaskFilter.DONE,
                    onClick = { vm.setFilter(TaskFilter.DONE) },
                    label = { Text("Done (${state?.doneCount ?: 0})") },
                )
            }
            if (state != null && items.isEmpty()) {
                EmptyState(
                    if (filter == TaskFilter.ACTIVE) "Nothing to do. Tap “New task” to add one." else "No completed tasks yet.",
                )
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.task.id }) { item ->
                    ReorderableItem(reorderState, key = item.task.id, enabled = canDrag) { isDragging ->
                        TaskRow(
                            task = item.task,
                            subtaskDone = item.subtasksDone,
                            subtaskTotal = item.subtasksTotal,
                            elevated = isDragging,
                            onToggle = { done ->
                                vm.toggle(item.task, done) { next ->
                                    scope.launch { snackbar.showSnackbar("Repeats — next due ${Format.due(next)}") }
                                }
                            },
                            onClick = { navigator.openTask(item.task.id) },
                            trailing = {
                                if (canDrag) {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.draggableHandle(
                                            onDragStopped = { vm.reorder(items.map { it.task.id }) },
                                        ),
                                    ) {
                                        Icon(
                                            Icons.Filled.DragHandle,
                                            contentDescription = "Drag to reorder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
