package com.chase.workoutlog.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chase.workoutlog.analytics.Format
import com.chase.workoutlog.data.WorkoutEntry
import com.chase.workoutlog.ui.components.EmptyState
import com.chase.workoutlog.ui.components.ScreenTopBar
import com.chase.workoutlog.ui.components.SectionHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayHeader = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")

fun friendlyDate(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(dayHeader)
}

@Composable
fun HistoryScreen(
    entries: List<WorkoutEntry>,
    onEdit: (WorkoutEntry) -> Unit,
    onDelete: (WorkoutEntry) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<WorkoutEntry?>(null) }
    val byDay = remember(entries) {
        entries.sortedWith(compareByDescending<WorkoutEntry> { it.date }.thenByDescending { it.id })
            .groupBy { it.date }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("History")
        if (entries.isEmpty()) {
            EmptyState(
                "No workouts yet",
                "Head to the Log tab, pick an exercise and save it — it'll show up here.",
                Modifier.fillMaxWidth(),
            )
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            byDay.forEach { (day, dayEntries) ->
                item(key = "day_$day") {
                    SectionHeader("${friendlyDate(day)} · ${dayEntries.size} exercise${if (dayEntries.size == 1) "" else "s"}")
                }
                items(dayEntries, key = { it.id }) { entry ->
                    EntryCard(entry, onClick = { onEdit(entry) }, onDelete = { pendingDelete = entry })
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this workout?") },
            text = { Text("${entry.exerciseName} on ${friendlyDate(entry.date)} — ${Format.summary(entry)}") },
            confirmButton = {
                TextButton(onClick = { onDelete(entry); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun EntryCard(
    entry: WorkoutEntry,
    onClick: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
) {
    Card(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.exerciseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(Format.summary(entry), style = MaterialTheme.typography.bodyMedium)
                if (entry.notes.isNotBlank()) {
                    Text(
                        entry.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
        }
    }
}
