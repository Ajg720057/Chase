package com.chase.workoutlog.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chase.workoutlog.analytics.Format
import com.chase.workoutlog.data.Exercise
import com.chase.workoutlog.data.ExerciseType
import com.chase.workoutlog.data.SetEntry
import com.chase.workoutlog.data.WorkoutEntry
import com.chase.workoutlog.ui.components.NumberField
import com.chase.workoutlog.ui.components.ScreenTopBar
import com.chase.workoutlog.ui.components.Stepper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Text-field state for one set. [edited] marks sets the user changed by hand, which stop auto-filling. */
private data class SetRow(
    val reps: String,
    val weight: String,
    val seconds: String,
    val edited: Boolean = false,
)

private fun SetEntry.toRow() = SetRow(
    reps = if (reps > 0) reps.toString() else "",
    weight = if (weight > 0) Format.number(weight) else "",
    seconds = if (seconds > 0) seconds.toString() else "",
)

private val dateLabel = DateTimeFormatter.ofPattern("EEE, MMM d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWorkoutScreen(
    exercise: Exercise,
    existing: WorkoutEntry?,
    lastTime: WorkoutEntry?,
    onBack: () -> Unit,
    onSave: (WorkoutEntry) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var date by remember { mutableStateOf(existing?.date ?: today) }
    var showDatePicker by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    // Set-based exercises start from what was logged (editing) or from last time, so repeating a workout is one tap.
    val template = existing ?: lastTime
    val rows = remember {
        mutableStateListOf<SetRow>().apply {
            val fromTemplate = template?.sets?.map { it.toRow() }.orEmpty()
            if (fromTemplate.isNotEmpty()) addAll(fromTemplate)
            else repeat(3) {
                add(
                    when (exercise.type) {
                        ExerciseType.TIMED -> SetRow(reps = "", weight = "", seconds = "30")
                        ExerciseType.BODYWEIGHT -> SetRow(reps = "10", weight = "", seconds = "")
                        else -> SetRow(reps = "10", weight = "", seconds = "")
                    }
                )
            }
        }
    }

    val templateDuration = template?.durationSeconds ?: 0
    var hours by remember { mutableStateOf(if (templateDuration >= 3600) (templateDuration / 3600).toString() else "") }
    var minutes by remember { mutableStateOf(if (templateDuration > 0) ((templateDuration % 3600) / 60).toString() else "") }
    var seconds by remember { mutableStateOf(if (templateDuration % 60 > 0) (templateDuration % 60).toString() else "") }
    var distance by remember {
        mutableStateOf(template?.distanceMiles?.takeIf { it > 0 }?.let { Format.number(it) } ?: "")
    }

    val durationSeconds = (hours.toIntOrNull() ?: 0) * 3600 + (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
    val distanceMiles = distance.toDoubleOrNull() ?: 0.0

    val isSetBased = exercise.type != ExerciseType.CARDIO
    val canSave = if (isSetBased) {
        rows.isNotEmpty() && rows.all { r ->
            when (exercise.type) {
                ExerciseType.TIMED -> (r.seconds.toIntOrNull() ?: 0) > 0
                else -> (r.reps.toIntOrNull() ?: 0) > 0
            }
        }
    } else durationSeconds > 0

    fun updateRow(index: Int, change: (SetRow) -> SetRow) {
        val updated = change(rows[index]).copy(edited = true)
        rows[index] = updated
        // Later sets the user hasn't touched follow along, so entering set 1 fills in the rest.
        for (j in index + 1 until rows.size) {
            if (!rows[j].edited) rows[j] = updated.copy(edited = false)
        }
    }

    fun setCount(n: Int) {
        while (rows.size < n) rows.add((rows.lastOrNull() ?: SetRow("10", "", "30")).copy(edited = false))
        while (rows.size > n) rows.removeAt(rows.lastIndex)
    }

    fun buildEntry() = WorkoutEntry(
        id = existing?.id ?: System.currentTimeMillis(),
        exerciseId = exercise.id,
        exerciseName = exercise.name,
        type = exercise.type,
        date = date,
        sets = if (isSetBased) rows.map { r ->
            SetEntry(
                reps = r.reps.toIntOrNull() ?: 0,
                weight = if (exercise.type == ExerciseType.STRENGTH) r.weight.toDoubleOrNull() ?: 0.0 else 0.0,
                seconds = if (exercise.type == ExerciseType.TIMED) r.seconds.toIntOrNull() ?: 0 else 0,
            )
        } else emptyList(),
        durationSeconds = if (isSetBased) 0 else durationSeconds,
        distanceMiles = if (isSetBased) 0.0 else distanceMiles,
        notes = notes.trim(),
    )

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = if (existing != null) "Edit ${exercise.name}" else exercise.name,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { onSave(buildEntry()) },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp)
                        .height(52.dp),
                ) {
                    Text(if (existing != null) "Save changes" else "Save workout", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AssistChip(
                onClick = { showDatePicker = true },
                label = {
                    Text(
                        when (date) {
                            today -> "Today"
                            today.minusDays(1) -> "Yesterday"
                            else -> date.format(dateLabel)
                        }
                    )
                },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
            )

            if (lastTime != null && existing == null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Last time · ${lastTime.date.format(dateLabel)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            Format.summary(lastTime),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            "Pre-filled below — just adjust what changed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            if (isSetBased) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("How many sets?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Stepper(value = rows.size, onValueChange = { setCount(it) })
                }
                if (rows.size > 1) {
                    Text(
                        "Set 1 fills in the rest — change any set that was different.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                rows.forEachIndexed { i, row ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Set ${i + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(52.dp))
                        when (exercise.type) {
                            ExerciseType.STRENGTH -> {
                                NumberField(
                                    value = row.reps,
                                    onValueChange = { v -> updateRow(i) { it.copy(reps = v) } },
                                    label = "Reps",
                                    modifier = Modifier.weight(1f),
                                )
                                Text("×")
                                NumberField(
                                    value = row.weight,
                                    onValueChange = { v -> updateRow(i) { it.copy(weight = v) } },
                                    label = "Weight",
                                    suffix = "lb",
                                    decimal = true,
                                    modifier = Modifier.weight(1.3f),
                                )
                            }
                            ExerciseType.BODYWEIGHT -> NumberField(
                                value = row.reps,
                                onValueChange = { v -> updateRow(i) { it.copy(reps = v) } },
                                label = "Reps",
                                modifier = Modifier.weight(1f),
                            )
                            ExerciseType.TIMED -> NumberField(
                                value = row.seconds,
                                onValueChange = { v -> updateRow(i) { it.copy(seconds = v) } },
                                label = "Hold time",
                                suffix = "sec",
                                modifier = Modifier.weight(1f),
                            )
                            ExerciseType.CARDIO -> Unit
                        }
                    }
                }
            } else {
                Text("How long was it?", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(hours, { hours = it }, "Hours", Modifier.weight(1f))
                    NumberField(minutes, { minutes = it }, "Min", Modifier.weight(1f))
                    NumberField(seconds, { seconds = it }, "Sec", Modifier.weight(1f))
                }
                Text("How far? (optional)", style = MaterialTheme.typography.titleMedium)
                NumberField(
                    value = distance,
                    onValueChange = { distance = it },
                    label = "Distance",
                    suffix = "miles",
                    decimal = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (durationSeconds > 0 && distanceMiles > 0) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pace", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                Format.pace(durationSeconds / distanceMiles),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
    }

    if (showDatePicker) {
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}
