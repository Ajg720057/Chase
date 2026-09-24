package com.chase.workoutlog.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chase.workoutlog.analytics.Consistency
import com.chase.workoutlog.data.WorkoutEntry
import com.chase.workoutlog.ui.components.BarChart
import com.chase.workoutlog.ui.components.ScreenTopBar
import com.chase.workoutlog.ui.components.StatCard
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy")
private val weekLabel = DateTimeFormatter.ofPattern("M/d")

@Composable
fun CalendarScreen(entries: List<WorkoutEntry>) {
    val today = remember { LocalDate.now() }
    var monthOffset by rememberSaveable { mutableStateOf(0L) }
    var selectedEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val month = YearMonth.from(today).plusMonths(monthOffset)
    val selected = LocalDate.ofEpochDay(selectedEpochDay)

    val countByDay = remember(entries) { entries.groupingBy { it.date }.eachCount() }
    val days = countByDay.keys
    val current = remember(days) { Consistency.currentStreak(days, today) }
    val longest = remember(days) { Consistency.longestStreak(days) }
    val thisMonthDays = days.count { YearMonth.from(it) == YearMonth.from(today) }
    val weekly = remember(days) { Consistency.weeklyCounts(days, today, 8) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Consistency")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Current streak", "$current day${if (current == 1) "" else "s"}", Modifier.weight(1f))
                StatCard("Longest streak", "$longest day${if (longest == 1) "" else "s"}", Modifier.weight(1f))
                StatCard("This month", "$thisMonthDays day${if (thisMonthDays == 1) "" else "s"}", Modifier.weight(1f))
            }

            Card {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { monthOffset-- }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                        }
                        Text(
                            month.format(monthTitle),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { monthOffset++ }, enabled = monthOffset < 0) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                        }
                    }
                    MonthGrid(month, today, selected, countByDay) { selectedEpochDay = it.toEpochDay() }
                }
            }

            val dayEntries = entries.filter { it.date == selected }.sortedBy { it.id }
            Text(friendlyDate(selected, today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (dayEntries.isEmpty()) {
                Text(
                    if (selected.isAfter(today)) "That's in the future." else "Rest day — nothing logged.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column {
                    dayEntries.forEach {
                        EntryCard(it, onClick = null, onDelete = null, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }

            Text("Workout days per week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card {
                Column(Modifier.padding(12.dp)) {
                    BarChart(
                        bars = weekly.map { (start, count) -> start.format(weekLabel) to count },
                        maxValue = 7,
                    )
                    Text(
                        "Weeks start on Monday. Last 8 weeks shown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Box(Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    countByDay: Map<LocalDate, Int>,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val leadingBlanks = first.dayOfWeek.value - 1 // Monday = 0
    val cells = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val primary = MaterialTheme.colorScheme.primary

    Row(Modifier.fillMaxWidth()) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { day ->
                Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                    if (day != null) {
                        val count = countByDay[day] ?: 0
                        val isSelected = day == selected
                        val bg = when {
                            count >= 3 -> primary
                            count == 2 -> primary.copy(alpha = 0.75f)
                            count == 1 -> primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(bg)
                                .then(
                                    when {
                                        isSelected -> Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                                        day == today -> Modifier.border(BorderStroke(2.dp, primary), CircleShape)
                                        else -> Modifier
                                    }
                                )
                                .clickable { onSelect(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (count > 0 || day == today) FontWeight.Bold else FontWeight.Normal,
                                color = if (count > 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            repeat(7 - week.size) { Box(Modifier.weight(1f)) }
        }
    }
}
