package com.chase.workoutlog.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.chase.workoutlog.analytics.Format
import com.chase.workoutlog.data.Category
import com.chase.workoutlog.data.Exercise
import com.chase.workoutlog.data.ExerciseType
import com.chase.workoutlog.data.WorkoutEntry
import com.chase.workoutlog.ui.components.ScreenTopBar
import com.chase.workoutlog.ui.components.SectionHeader

@Composable
fun ExercisePickerScreen(
    exercises: List<Exercise>,
    entries: List<WorkoutEntry>,
    onPick: (Exercise) -> Unit,
    onCreateCustom: (name: String, Category, ExerciseType) -> Unit,
    onDeleteCustom: (Exercise) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var customDialogName by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Exercise?>(null) }

    val lastByExercise = remember(entries) {
        entries.groupBy { it.exerciseId }
            .mapValues { (_, list) -> list.maxWith(compareBy({ it.date }, { it.id })) }
    }
    val recent = remember(entries, exercises) {
        lastByExercise.values.sortedWith(compareByDescending<WorkoutEntry> { it.date }.thenByDescending { it.id })
            .mapNotNull { e -> exercises.firstOrNull { it.id == e.exerciseId } }
            .take(5)
    }
    val filtered = exercises.filter {
        (category == null || it.category == category) &&
            (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true))
    }
    val browsing = query.isBlank() && category == null

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Log a workout")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search exercises") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear search") }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
            }
            items(Category.entries) { c ->
                FilterChip(
                    selected = category == c,
                    onClick = { category = if (category == c) null else c },
                    label = { Text(c.label) },
                )
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (browsing && recent.isNotEmpty()) {
                item { SectionHeader("Recent") }
                items(recent, key = { "recent_" + it.id }) { ex ->
                    ExerciseRow(ex, lastByExercise[ex.id], onPick, onDelete = null)
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            }

            if (browsing) {
                Category.entries.forEach { c ->
                    val inCategory = filtered.filter { it.category == c }
                    if (inCategory.isNotEmpty()) {
                        item(key = "header_" + c.name) { SectionHeader(c.label) }
                        items(inCategory, key = { it.id }) { ex ->
                            ExerciseRow(ex, lastByExercise[ex.id], onPick, onDelete = { pendingDelete = it })
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { ex ->
                    ExerciseRow(ex, lastByExercise[ex.id], onPick, onDelete = { pendingDelete = it })
                }
            }

            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = { customDialogName = query.trim() }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(
                            if (filtered.isEmpty() && query.isNotBlank()) "  Add \"${query.trim()}\" as an exercise"
                            else "  Add a custom exercise"
                        )
                    }
                }
            }
        }
    }

    customDialogName?.let { initial ->
        CustomExerciseDialog(
            initialName = initial,
            initialCategory = category,
            onDismiss = { customDialogName = null },
            onCreate = { name, cat, type ->
                customDialogName = null
                query = ""
                onCreateCustom(name, cat, type)
            },
        )
    }

    pendingDelete?.let { ex ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${ex.name}?") },
            text = { Text("It will be removed from the exercise list. Workouts you already logged stay in your history.") },
            confirmButton = {
                TextButton(onClick = { onDeleteCustom(ex); pendingDelete = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ExerciseRow(
    exercise: Exercise,
    last: WorkoutEntry?,
    onPick: (Exercise) -> Unit,
    onDelete: ((Exercise) -> Unit)?,
) {
    ListItem(
        modifier = Modifier.clickable { onPick(exercise) },
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        exercise.name.first().uppercase(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        headlineContent = { Text(exercise.name) },
        supportingContent = {
            Text(
                if (last != null) "Last: ${Format.summary(last)}" else exercise.type.description,
                maxLines = 1,
            )
        },
        trailingContent = if (exercise.isCustom && onDelete != null) {
            {
                IconButton(onClick = { onDelete(exercise) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove custom exercise")
                }
            }
        } else null,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomExerciseDialog(
    initialName: String,
    initialCategory: Category?,
    onDismiss: () -> Unit,
    onCreate: (String, Category, ExerciseType) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var type by remember {
        mutableStateOf(if (initialCategory == Category.CARDIO) ExerciseType.CARDIO else ExerciseType.STRENGTH)
    }
    var category by remember { mutableStateOf(initialCategory ?: Category.FULL_BODY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New exercise") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("What do you track?", style = MaterialTheme.typography.titleSmall)
                ExerciseType.entries.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { type = t },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = type == t, onClick = { type = t })
                        Column {
                            Text(t.label)
                            Text(
                                t.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text("Category", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, category, type) }, enabled = name.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
