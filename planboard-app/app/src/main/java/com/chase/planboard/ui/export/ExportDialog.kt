package com.chase.planboard.ui.export

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chase.planboard.data.Periods
import com.chase.planboard.data.Scope
import com.chase.planboard.export.ExportOptions
import com.chase.planboard.export.ItineraryPdf
import com.chase.planboard.ui.common.DatePickerModal
import com.chase.planboard.ui.common.Format
import com.chase.planboard.ui.planBoardApp
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Picks a date range (a day, week, month, or any custom range) and what to include,
 * then saves the plans in that range as a PDF itinerary and offers to open or share it.
 */
@Composable
fun ExportItineraryDialog(initialScope: Scope?, anchor: LocalDate, onDismiss: () -> Unit) {
    val app = planBoardApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // null means a custom range.
    var mode by remember { mutableStateOf(initialScope) }
    var date by remember { mutableStateOf(anchor) }
    var customStart by remember { mutableStateOf(Periods.align(initialScope ?: Scope.WEEK, anchor)) }
    var customEnd by remember { mutableStateOf(Periods.end(initialScope ?: Scope.WEEK, anchor)) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    var includeTodos by remember { mutableStateOf(true) }
    var includeDetails by remember { mutableStateOf(true) }
    var includeNotes by remember { mutableStateOf(false) }
    var includeDone by remember { mutableStateOf(true) }
    var includeEmptyDays by remember { mutableStateOf(true) }

    var saved by remember { mutableStateOf<Uri?>(null) }
    var working by remember { mutableStateOf(false) }

    val m = mode
    val start = if (m != null) Periods.align(m, date) else minOf(customStart, customEnd)
    val end = if (m != null) Periods.end(m, date) else maxOf(customStart, customEnd)
    val options = ExportOptions(
        start = start,
        end = end,
        includeTodos = includeTodos,
        includeDetails = includeDetails,
        includeNotes = includeNotes,
        includeDone = includeDone,
        includeEmptyDays = includeEmptyDays,
    )
    val tooLong = options.dayCount > ExportOptions.MAX_DAYS

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        scope.launch {
            val result = runCatching { app.itinerary.export(options, uri) }
            working = false
            if (result.isSuccess) {
                saved = uri
            } else {
                Toast.makeText(context, "Couldn't create the PDF: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val done = saved
    if (done != null) {
        SavedDialog(uri = done, onDismiss = onDismiss)
        return
    }

    // Stay open while the PDF is being written so the save isn't cut short.
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Export itinerary") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(Scope.DAY, Scope.WEEK, Scope.MONTH).forEach { s ->
                        FilterChip(selected = mode == s, onClick = { mode = s }, label = { Text(s.label) })
                    }
                    FilterChip(
                        selected = mode == null,
                        onClick = {
                            if (m != null) {
                                customStart = start
                                customEnd = end
                            }
                            mode = null
                        },
                        label = { Text("Custom") },
                    )
                }

                if (m != null) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { date = Periods.shift(m, date, -1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                        }
                        Text(
                            when (m) {
                                Scope.DAY -> Format.relativeDay(date)
                                Scope.WEEK -> Format.week(date)
                                Scope.MONTH -> Format.period(Scope.MONTH, date)
                            },
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = { date = Periods.shift(m, date, 1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                        }
                    }
                } else {
                    Row(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { pickStart = true }) { Text("From ${Format.relativeDay(customStart)}") }
                        OutlinedButton(onClick = { pickEnd = true }) { Text("To ${Format.relativeDay(customEnd)}") }
                    }
                }
                Text(
                    if (tooLong) {
                        "That's more than a year. Pick a shorter range."
                    } else {
                        "${ItineraryPdf.rangeLabel(start, end)} · ${options.dayCount} day${if (options.dayCount == 1L) "" else "s"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )

                Option("To-dos", includeTodos) { includeTodos = it }
                Option("Plan details", includeDetails) { includeDetails = it }
                Option("Progress log", includeNotes) { includeNotes = it }
                Option("Finished plans", includeDone) { includeDone = it }
                Option("Days with nothing planned", includeEmptyDays) { includeEmptyDays = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !tooLong && !working,
                onClick = { createLauncher.launch(if (start == end) "itinerary-$start.pdf" else "itinerary-$start-to-$end.pdf") },
            ) { Text(if (working) "Creating…" else "Create PDF") }
        },
        dismissButton = { TextButton(enabled = !working, onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickStart) DatePickerModal(initial = customStart, onPick = { customStart = it }, onDismiss = { pickStart = false })
    if (pickEnd) DatePickerModal(initial = customEnd, onPick = { customEnd = it }, onDismiss = { pickEnd = false })
}

@Composable
private fun Option(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
private fun SavedDialog(uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun launch(intent: Intent, failure: String) {
        runCatching {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(context, failure, Toast.LENGTH_LONG).show() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Itinerary saved") },
        text = { Text("Your PDF is saved where you chose. Open it now or share it.") },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    launch(
                        Intent(Intent.ACTION_SEND)
                            .setType("application/pdf")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        "No app available to share with.",
                    )
                    onDismiss()
                }) { Text("Share") }
                TextButton(onClick = {
                    launch(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/pdf")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        "No PDF viewer found. You can still find the file where you saved it.",
                    )
                    onDismiss()
                }) { Text("Open") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
