package com.chase.workoutlog.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chase.workoutlog.analytics.Analytics
import com.chase.workoutlog.analytics.Consistency
import com.chase.workoutlog.analytics.Format
import com.chase.workoutlog.analytics.Metric
import com.chase.workoutlog.analytics.Progress
import com.chase.workoutlog.data.Category
import com.chase.workoutlog.data.Exercise
import com.chase.workoutlog.data.WorkoutEntry
import com.chase.workoutlog.ui.components.EmptyState
import com.chase.workoutlog.ui.components.LineChart
import com.chase.workoutlog.ui.components.ScreenTopBar
import com.chase.workoutlog.ui.components.SectionHeader
import com.chase.workoutlog.ui.components.StatCard
import com.chase.workoutlog.ui.theme.Trend
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val shortDate = DateTimeFormatter.ofPattern("MMM d")

@Composable
private fun trendColor(percent: Double): Color = when {
    percent > 0.05 -> Trend.up()
    percent < -0.05 -> Trend.down()
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun arrow(percent: Double) = when {
    percent > 0.05 -> "▲ "
    percent < -0.05 -> "▼ "
    else -> ""
}

@Composable
fun ProgressScreen(
    exercises: List<Exercise>,
    entries: List<WorkoutEntry>,
    selectedExerciseId: String?,
    onSelect: (String?) -> Unit,
    onLog: (Exercise) -> Unit,
) {
    val byExercise = remember(entries) {
        entries.groupBy { it.exerciseId }
            .mapValues { (_, list) -> list.sortedWith(compareBy({ it.date }, { it.id })) }
    }
    fun exerciseFor(id: String): Exercise =
        exercises.firstOrNull { it.id == id }
            ?: byExercise[id]!!.last().let { Exercise(id, it.exerciseName, Category.FULL_BODY, it.type, isCustom = true) }

    val selectedEntries = selectedExerciseId?.let { byExercise[it] }
    if (selectedExerciseId != null && selectedEntries != null) {
        BackHandler { onSelect(null) }
        ExerciseProgressDetail(exerciseFor(selectedExerciseId), selectedEntries, onBack = { onSelect(null) }, onLog = onLog)
        return
    }

    val today = remember { LocalDate.now() }
    val days = remember(entries) { entries.map { it.date }.toSet() }
    val weekStart = Consistency.weekStart(today)
    val thisWeek = days.count { !it.isBefore(weekStart) && !it.isAfter(today) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Progress")
        if (entries.isEmpty()) {
            EmptyState(
                "Nothing to chart yet",
                "Log the same exercise a couple of times and you'll see how much you've improved here — like \"12% more weight\" or \"8% faster\".",
                Modifier.fillMaxWidth(),
            )
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Workout days", days.size.toString(), Modifier.weight(1f))
                    StatCard("This week", thisWeek.toString(), Modifier.weight(1f))
                    StatCard("Exercises", byExercise.size.toString(), Modifier.weight(1f))
                }
            }
            item { SectionHeader("Your exercises", Modifier.padding(top = 8.dp)) }
            val ordered = byExercise.entries.sortedWith(
                compareByDescending<Map.Entry<String, List<WorkoutEntry>>> { it.value.last().date }
                    .thenByDescending { it.value.last().id }
            )
            items(ordered, key = { it.key }) { (id, list) ->
                val exercise = exerciseFor(id)
                val metric = Analytics.primaryMetric(exercise.type, list)
                val progress = Analytics.progress(list, metric)
                ListItem(
                    modifier = Modifier.clickable { onSelect(id) },
                    headlineContent = { Text(exercise.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        val sessions = progress?.sessions ?: list.size
                        Text(
                            "$sessions session${if (sessions == 1) "" else "s"}" +
                                (progress?.let { " · Best ${Format.metric(metric, it.best.value)}" } ?: "")
                        )
                    },
                    trailingContent = {
                        if (progress != null && progress.sessions >= 2) {
                            Text(
                                arrow(progress.percentSinceStart) + Analytics.describe(metric, progress.percentSinceStart),
                                color = trendColor(progress.percentSinceStart),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Text(
                                "Log again to compare",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ExerciseProgressDetail(
    exercise: Exercise,
    entries: List<WorkoutEntry>,
    onBack: () -> Unit,
    onLog: (Exercise) -> Unit,
) {
    val metrics = Analytics.metricsFor(exercise.type)
    var metricName by rememberSaveable(exercise.id) {
        mutableStateOf(Analytics.primaryMetric(exercise.type, entries).name)
    }
    val metric = Metric.valueOf(metricName)
    val points = remember(entries, metric) { Analytics.series(entries, metric) }
    val progress = remember(entries, metric) { Analytics.progress(entries, metric) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            exercise.name,
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(metrics) { m ->
                        FilterChip(selected = m == metric, onClick = { metricName = m.name }, label = { Text(m.label) })
                    }
                }
            }
            if (progress == null) {
                item {
                    EmptyState(
                        "No ${metric.label.lowercase()} data",
                        if (metric == Metric.PACE) "Add a distance when you log to track your pace."
                        else "None of your logged sessions include this.",
                        Modifier.fillMaxWidth(),
                    )
                }
            } else {
                item { HeadlineCard(progress) }
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(metric.label, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            LineChart(points, formatValue = { Format.metric(metric, it) })
                            if (metric.lowerIsBetter) {
                                Text(
                                    "Lower is faster.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("First", Format.metric(metric, progress.first.value), Modifier.weight(1f), progress.first.date.format(shortDate))
                        StatCard("Latest", Format.metric(metric, progress.latest.value), Modifier.weight(1f), progress.latest.date.format(shortDate))
                        StatCard("Best", Format.metric(metric, progress.best.value), Modifier.weight(1f), progress.best.date.format(shortDate))
                    }
                }
            }
            item {
                Button(onClick = { onLog(exercise) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Log ${exercise.name}")
                }
            }
            item { SectionHeader("Sessions") }
            items(entries.reversed(), key = { it.id }) { entry ->
                Column {
                    Text(
                        friendlyDate(entry.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp),
                    )
                    EntryCard(entry, onClick = null, onDelete = null)
                }
            }
        }
    }
}

@Composable
private fun HeadlineCard(progress: Progress) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (progress.sessions < 2) {
                Text("1 session logged", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Log this again to see how much you've improved.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Text("Since ${progress.first.date.format(shortDate)}", style = MaterialTheme.typography.labelMedium)
            Text(
                arrow(progress.percentSinceStart) + Analytics.describe(progress.metric, progress.percentSinceStart),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = trendColor(progress.percentSinceStart),
            )
            progress.percentVsPrevious?.let { p ->
                Text(
                    "vs. last session: " + arrow(p) + Analytics.describe(progress.metric, p),
                    style = MaterialTheme.typography.bodyMedium,
                    color = trendColor(p),
                )
            }
            Text(
                "${progress.sessions} sessions tracked",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
