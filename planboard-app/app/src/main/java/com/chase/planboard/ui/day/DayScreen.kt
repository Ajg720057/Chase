package com.chase.planboard.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chase.planboard.data.Scope
import com.chase.planboard.ui.BoardViewModel
import com.chase.planboard.ui.DueTodo
import com.chase.planboard.ui.Navigator
import com.chase.planboard.ui.Tab
import com.chase.planboard.ui.common.Format
import com.chase.planboard.ui.common.MainMenu
import com.chase.planboard.ui.common.PeriodSwitcher
import com.chase.planboard.ui.common.SectionHeader
import com.chase.planboard.ui.common.planSection
import com.chase.planboard.ui.common.swipeToShift
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(board: BoardViewModel, navigator: Navigator) {
    val date by board.date.collectAsStateWithLifecycle()
    val state by board.board.collectAsStateWithLifecycle()
    val shift = { n: Long -> board.shift(Scope.DAY, n) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Day") },
                actions = {
                    IconButton(onClick = { board.select(LocalDate.now()) }) {
                        Icon(Icons.Filled.Today, contentDescription = "Today")
                    }
                    MainMenu()
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigator.newPlan(Scope.DAY, date) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Day plan") },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        val relative = Format.relativeDay(date)
        val long = Format.longDay(date)

        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .swipeToShift(shift),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
        ) {
            item(key = "switcher") {
                PeriodSwitcher(
                    title = relative,
                    subtitle = if (relative == long) null else long,
                    onShift = shift,
                )
            }
            planSection(
                key = "day",
                title = "Plans for the day",
                plans = s.plansIn(Scope.DAY, date),
                board = board,
                navigator = navigator,
                emptyText = "Nothing planned yet. Tap “Day plan” to add one.",
                onAdd = { navigator.newPlan(Scope.DAY, date) },
            )

            val due = s.todosDueOn(date)
            if (due.isNotEmpty()) {
                item(key = "due-header") { SectionHeader("To-dos due") }
                items(due, key = { "due-${it.todo.id}" }) { d ->
                    DueTodoRow(
                        d,
                        onToggle = { board.setTodoDone(d.todo, it) },
                        onClick = { d.plan?.let { p -> navigator.openPlan(p.id) } },
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }

            planSection(
                key = "week",
                title = "This week · ${Format.week(date)}",
                plans = s.plansIn(Scope.WEEK, date),
                board = board,
                navigator = navigator,
                emptyText = null,
                onAdd = { navigator.newPlan(Scope.WEEK, date) },
            )
            item(key = "week-open") { OpenLink("Open week") { navigator.show(Tab.WEEK) } }

            planSection(
                key = "month",
                title = "This month · ${Format.month(YearMonth.from(date))}",
                plans = s.plansIn(Scope.MONTH, date),
                board = board,
                navigator = navigator,
                emptyText = null,
                onAdd = { navigator.newPlan(Scope.MONTH, date) },
            )
            item(key = "month-open") { OpenLink("Open month") { navigator.show(Tab.MONTH) } }
        }
    }
}

@Composable
private fun DueTodoRow(d: DueTodo, onToggle: (Boolean) -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
        Row(Modifier.clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = d.todo.done, onCheckedChange = onToggle)
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    d.todo.title.ifBlank { "Untitled to-do" },
                    textDecoration = if (d.todo.done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(
                    d.todo.dueAt?.let { Format.time(it) },
                    d.plan?.let { it.title.ifBlank { "Untitled plan" } },
                ).joinToString(" · ")
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (d.todo.lifeBoardTaskId != null) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = "Linked to LifeBoard",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp).size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun OpenLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    )
}
