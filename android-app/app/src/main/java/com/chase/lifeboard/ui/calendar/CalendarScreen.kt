package com.chase.lifeboard.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chase.lifeboard.data.Moods
import com.chase.lifeboard.ui.Navigator
import com.chase.lifeboard.ui.common.BackupMenu
import com.chase.lifeboard.ui.common.EmptyState
import com.chase.lifeboard.ui.common.Format
import com.chase.lifeboard.ui.common.SectionHeader
import com.chase.lifeboard.ui.common.TaskRow
import com.chase.lifeboard.ui.journal.JournalEntryCard
import com.chase.lifeboard.ui.lifeBoardApp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(navigator: Navigator) {
    val app = lifeBoardApp()
    val vm: CalendarViewModel = viewModel(factory = viewModelFactory { initializer { CalendarViewModel(app) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                actions = {
                    IconButton(onClick = { vm.select(LocalDate.now()) }) {
                        Icon(Icons.Filled.Today, contentDescription = "Today")
                    }
                    BackupMenu()
                },
            )
        },
    ) { padding ->
        if (s == null) return@Scaffold
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        ) {
            item(key = "grid") {
                MonthGrid(
                    month = s.month,
                    selected = s.selected,
                    marks = s.marks,
                    onSelect = vm::select,
                    onShift = vm::shiftMonth,
                )
            }
            item(key = "day-header") {
                Column(Modifier.padding(top = 12.dp)) {
                    Text(Format.longDay(s.selected), style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            navigator.newTask(dueAt = Format.toMillis(s.selected.atTime(LocalTime.of(9, 0))))
                        }) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Task")
                        }
                        OutlinedButton(onClick = { navigator.newEntry(s.selected) }) {
                            Icon(Icons.Filled.EditNote, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Journal")
                        }
                    }
                }
            }
            item(key = "tasks-header") { SectionHeader("Tasks") }
            if (s.dayTasks.isEmpty()) item(key = "no-tasks") { EmptyState("No tasks due") }
            items(s.dayTasks, key = { "t-${it.task.id}" }) { dt ->
                TaskRow(
                    task = dt.task,
                    subtaskDone = 0,
                    subtaskTotal = 0,
                    subtitle = listOfNotNull(
                        dt.parentTitle?.let { "Subtask of $it" },
                        if (dt.isProjected) "Repeats — upcoming" else null,
                    ).joinToString(" · ").ifBlank { null },
                    showCheckbox = !dt.isProjected,
                    onToggle = { vm.toggle(dt.task, it) },
                    onClick = { navigator.openTask(dt.task.id) },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            item(key = "journal-header") { SectionHeader("Journal") }
            if (s.dayEntries.isEmpty()) item(key = "no-entries") { EmptyState("No journal entries") }
            items(s.dayEntries, key = { "j-${it.entry.id}" }) { e ->
                JournalEntryCard(e, onClick = { navigator.openEntry(e.entry.id) }, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    marks: Map<LocalDate, DayMarks>,
    onSelect: (LocalDate) -> Unit,
    onShift: (Long) -> Unit,
) {
    val firstDayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val weekdays = remember(firstDayOfWeek) { (0L until 7L).map { firstDayOfWeek.plus(it) } }
    val first = month.atDay(1)
    val lead = ((first.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
    val gridStart = first.minusDays(lead.toLong())
    val weeks = ((lead + month.lengthOfMonth()) + 6) / 7
    val today = LocalDate.now()
    val title = month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

    Column(
        Modifier.pointerInput(month) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    if (total > 120) onShift(-1) else if (total < -120) onShift(1)
                },
                onHorizontalDrag = { _, amount -> total += amount },
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onShift(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { d: DayOfWeek ->
                Text(
                    d.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val date = gridStart.plusDays((w * 7 + i).toLong())
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        isSelected = date == selected,
                        marks = marks[date],
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(MaterialTheme.colorScheme.primary)
            Text(" Tasks   ", style = MaterialTheme.typography.labelSmall)
            Dot(MaterialTheme.colorScheme.error)
            Text(" Overdue   ", style = MaterialTheme.typography.labelSmall)
            Dot(MaterialTheme.colorScheme.tertiary)
            Text(" Journal", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    marks: DayMarks?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .aspectRatio(0.85f)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (isSelected) colors.primaryContainer else colors.surface)
            .then(if (isToday) Modifier.border(1.5.dp, colors.primary, MaterialTheme.shapes.small) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfMonth.toString(),
            modifier = Modifier.padding(top = 4.dp),
            fontWeight = if (isToday) FontWeight.Bold else null,
            color = when {
                isSelected -> colors.onPrimaryContainer
                inMonth -> colors.onSurface
                else -> colors.onSurface.copy(alpha = 0.35f)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        val mood = Moods.emoji(marks?.mood)
        if (mood != null) {
            Text(mood, fontSize = 11.sp, lineHeight = 12.sp)
        } else {
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (marks != null && marks.tasks > 0) Dot(if (marks.overdue) colors.error else colors.primary)
            if (marks != null && marks.journal > 0) Dot(colors.tertiary)
        }
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(6.dp).background(color, CircleShape))
}
