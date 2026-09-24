package com.chase.workoutlog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chase.workoutlog.data.Category
import com.chase.workoutlog.data.Exercise
import com.chase.workoutlog.data.WorkoutEntry
import com.chase.workoutlog.ui.screens.CalendarScreen
import com.chase.workoutlog.ui.screens.ExercisePickerScreen
import com.chase.workoutlog.ui.screens.HistoryScreen
import com.chase.workoutlog.ui.screens.LogWorkoutScreen
import com.chase.workoutlog.ui.screens.ProgressScreen
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    LOG("Log", Icons.Default.Add),
    HISTORY("History", Icons.AutoMirrored.Filled.List),
    CALENDAR("Calendar", Icons.Default.DateRange),
    PROGRESS("Progress", Icons.Default.Star),
}

/** What the log screen is open for: a new entry, or editing [existingId]. */
private data class LogTarget(val exerciseId: String, val existingId: Long? = null)

@Composable
fun WorkoutApp(vm: WorkoutViewModel = viewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.LOG) }
    var logExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var logExistingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var progressExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun openLog(target: LogTarget?) {
        logExerciseId = target?.exerciseId
        logExistingId = target?.existingId
    }

    fun exerciseForEntry(entry: WorkoutEntry): Exercise =
        vm.exercise(entry.exerciseId)
            ?: Exercise(entry.exerciseId, entry.exerciseName, Category.FULL_BODY, entry.type, isCustom = true)

    val existing = logExistingId?.let { id -> data.entries.firstOrNull { it.id == id } }
    val logExercise = logExerciseId?.let { id -> vm.exercise(id) ?: existing?.let(::exerciseForEntry) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (logExercise == null) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = {
                                if (t == Tab.PROGRESS && tab == Tab.PROGRESS) progressExerciseId = null
                                tab = t
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (logExercise != null) {
                BackHandler { openLog(null) }
                key(logExerciseId, logExistingId) {
                    LogWorkoutScreen(
                        exercise = logExercise,
                        existing = existing,
                        lastTime = vm.lastEntryFor(logExercise.id),
                        onBack = { openLog(null) },
                        onSave = { entry ->
                            vm.save(entry) { message -> scope.launch { snackbar.showSnackbar(message) } }
                            openLog(null)
                        },
                    )
                }
                return@Box
            }

            if (tab != Tab.LOG) BackHandler { tab = Tab.LOG }

            when (tab) {
                Tab.LOG -> ExercisePickerScreen(
                    exercises = exercises,
                    entries = data.entries,
                    onPick = { openLog(LogTarget(it.id)) },
                    onCreateCustom = { name, category, type ->
                        val created = vm.addCustomExercise(name, category, type)
                        openLog(LogTarget(created.id))
                    },
                    onDeleteCustom = vm::deleteCustomExercise,
                )
                Tab.HISTORY -> HistoryScreen(
                    entries = data.entries,
                    onEdit = { openLog(LogTarget(it.exerciseId, it.id)) },
                    onDelete = vm::delete,
                )
                Tab.CALENDAR -> CalendarScreen(entries = data.entries)
                Tab.PROGRESS -> ProgressScreen(
                    exercises = exercises,
                    entries = data.entries,
                    selectedExerciseId = progressExerciseId,
                    onSelect = { progressExerciseId = it },
                    onLog = { openLog(LogTarget(it.id)) },
                )
            }
        }
    }
}
