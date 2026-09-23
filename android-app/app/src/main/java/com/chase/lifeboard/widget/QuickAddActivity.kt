package com.chase.lifeboard.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.chase.lifeboard.LifeBoardApp
import com.chase.lifeboard.MainActivity
import com.chase.lifeboard.data.Moods
import com.chase.lifeboard.data.Priority
import com.chase.lifeboard.ui.common.PriorityChips
import com.chase.lifeboard.ui.theme.LifeBoardTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * A small popup over the home screen for adding a task or journal entry
 * straight from the widget, without opening the full app.
 */
class QuickAddActivity : ComponentActivity() {
    private val app get() = application as LifeBoardApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_TASK
        setContent {
            LifeBoardTheme {
                Dialog(onDismissRequest = { finish() }) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                        if (mode == MODE_JOURNAL) JournalForm() else TaskForm()
                    }
                }
            }
        }
    }

    @Composable
    private fun TaskForm() {
        var title by remember { mutableStateOf("") }
        var priority by remember { mutableIntStateOf(Priority.NONE) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }

        fun save(openEditor: Boolean) {
            lifecycleScope.launch {
                if (title.isBlank() && !openEditor) return@launch
                val id = app.tasks.create(title = title.trim())
                if (priority != Priority.NONE) {
                    app.tasks.get(id)?.let { app.tasks.update(it.copy(priority = priority)) }
                }
                if (openEditor) {
                    startActivity(MainActivity.openTaskIntent(this@QuickAddActivity, id, isNew = title.isBlank()))
                } else {
                    Toast.makeText(this@QuickAddActivity, "Task added", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        }

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("New task", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("What needs doing?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { save(openEditor = false) }),
            )
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                PriorityChips(priority) { priority = it }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { save(openEditor = true) }) { Text("Date, alarm, subtasks…") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { save(openEditor = false) }, enabled = title.isNotBlank()) { Text("Add") }
            }
        }
    }

    @Composable
    private fun JournalForm() {
        var body by remember { mutableStateOf("") }
        var mood by remember { mutableStateOf<Int?>(null) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }

        fun save(openEditor: Boolean) {
            lifecycleScope.launch {
                val empty = body.isBlank() && mood == null
                if (empty && !openEditor) return@launch
                val id = app.journal.create(LocalDate.now().toEpochDay())
                app.journal.get(id)?.let { app.journal.update(it.copy(body = body.trim(), mood = mood)) }
                if (openEditor) {
                    startActivity(MainActivity.openEntryIntent(this@QuickAddActivity, id, isNew = empty))
                } else {
                    Toast.makeText(this@QuickAddActivity, "Journal entry saved", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        }

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Journal — today", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Moods.all.forEach { (value, emoji) ->
                    val selected = mood == value
                    Text(
                        emoji,
                        fontSize = 28.sp,
                        modifier = Modifier
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                MaterialTheme.shapes.medium,
                            )
                            .clickable { mood = if (selected) null else value }
                            .padding(6.dp),
                    )
                }
            }
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = { Text("What's on your mind?") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { save(openEditor = true) }) { Text("Add photos, title…") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { save(openEditor = false) }, enabled = body.isNotBlank() || mood != null) { Text("Save") }
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_TASK = "task"
        const val MODE_JOURNAL = "journal"

        fun intent(context: Context, mode: String): Intent =
            Intent(context, QuickAddActivity::class.java)
                .setAction("com.chase.lifeboard.QUICK_ADD_$mode")
                .putExtra(EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
