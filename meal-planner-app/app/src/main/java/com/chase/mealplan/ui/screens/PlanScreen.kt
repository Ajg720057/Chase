package com.chase.mealplan.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Today
import android.Manifest
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.chase.mealplan.pdf.PdfFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.TimePickerDialog
import android.os.Build
import com.chase.mealplan.reminder.Reminders
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalContext
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chase.mealplan.data.PlannedMeal
import com.chase.mealplan.data.Person
import com.chase.mealplan.data.ReminderSettings
import com.chase.mealplan.data.eaterIds
import com.chase.mealplan.data.eatersLabel
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.OutlinedTextField
import com.chase.mealplan.data.Slot
import com.chase.mealplan.data.nutrition
import kotlin.math.roundToInt
import com.chase.mealplan.ui.ConfirmDialog
import com.chase.mealplan.ui.MainViewModel
import com.chase.mealplan.ui.MealThumb
import com.chase.mealplan.ui.Navigator
import com.chase.mealplan.ui.WeekSwitcher
import com.chase.mealplan.ui.theme.style
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(vm: MainViewModel, navigator: Navigator) {
    val week by vm.week.collectAsState()
    val plan by vm.plan.collectAsState()
    val isThisWeek by vm.isThisWeek.collectAsState()
    val startsMonday by vm.weekStartsMonday.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmCopy by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var reminderOpen by remember { mutableStateOf(false) }
    var pdfOpen by remember { mutableStateOf(false) }
    var peopleOpen by remember { mutableStateOf(false) }
    val people by vm.people.collectAsState()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val reminder by vm.reminder.collectAsState()
    val today = LocalDate.now()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(vm::exportBackup)
    }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Meal Plan") },
                actions = {
                    if (!isThisWeek) {
                        IconButton(onClick = vm::thisWeek) { Icon(Icons.Filled.Today, "Go to this week") }
                    }
                    IconButton(onClick = { pdfOpen = true }) { Icon(Icons.Filled.PictureAsPdf, "Weekly menu PDF") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Copy last week's meals") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = { menuOpen = false; confirmCopy = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear this week") },
                            leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) },
                            onClick = { menuOpen = false; confirmClear = true },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Week starts on Monday") },
                            trailingIcon = { Checkbox(checked = startsMonday, onCheckedChange = null) },
                            onClick = { vm.setWeekStartsMonday(!startsMonday) },
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("People")
                                    Text(
                                        people.joinToString(", ") { it.name },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.People, null) },
                            onClick = { menuOpen = false; peopleOpen = true },
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Weekly reminder")
                                    Text(
                                        if (reminder.enabled) reminder.label else "Off",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.Notifications, null) },
                            onClick = { menuOpen = false; reminderOpen = true },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Back up to file…") },
                            leadingIcon = { Icon(Icons.Filled.Save, null) },
                            onClick = { menuOpen = false; exportLauncher.launch("meal-planner-backup-$today.zip") },
                        )
                        DropdownMenuItem(
                            text = { Text("Restore from backup…") },
                            leadingIcon = { Icon(Icons.Filled.Restore, null) },
                            onClick = { menuOpen = false; importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.buildGroceryList(week) { navigator.groceryTab() } },
                icon = { Icon(Icons.Filled.ShoppingCart, null) },
                text = { Text("Make grocery list") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { WeekSwitcher(week, vm::previousWeek, vm::nextWeek) }
            items(week.days, key = { it.toString() }) { day ->
                DayCard(
                    day = day,
                    isToday = day == today,
                    slots = plan[day].orEmpty(),
                    people = people,
                    onOpen = { navigator.meal(it.meal.id, it.entry.id) },
                    onAdd = { slot -> navigator.edit(date = day, slot = slot) },
                )
            }
        }
    }

    if (confirmCopy) {
        ConfirmDialog(
            title = "Copy last week?",
            text = "Every meal planned for the week before ${week.label} is added to the same days this week.",
            confirm = "Copy",
            onConfirm = { vm.copyPreviousWeek() },
            onDismiss = { confirmCopy = false },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear this week?",
            text = "Removes every meal from ${week.label}. Your saved meals and recipes stay in the Meals tab.",
            confirm = "Clear",
            onConfirm = { vm.clearWeek() },
            onDismiss = { confirmClear = false },
        )
    }
    if (peopleOpen) {
        PeopleDialog(current = people, onSave = vm::setPeople, onDismiss = { peopleOpen = false })
    }
    if (pdfOpen) {
        MenuPdfDialog(vm = vm, weekLabel = week.label, onDismiss = { pdfOpen = false })
    }
    if (reminderOpen) {
        ReminderDialog(
            current = reminder,
            onSave = {
                vm.setReminder(it)
                if (it.enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.canNotify(context)) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = { reminderOpen = false },
        )
    }
    restoreUri?.let { uri ->
        ConfirmDialog(
            title = "Restore backup?",
            text = "This replaces all meals, plans and the grocery list on this phone with the ones in the backup.",
            confirm = "Restore",
            onConfirm = { vm.importBackup(uri) },
            onDismiss = { restoreUri = null },
        )
    }
}

@Composable
private fun DayCard(
    day: LocalDate,
    isToday: Boolean,
    slots: Map<Slot, List<PlannedMeal>>,
    people: List<Person>,
    onOpen: (PlannedMeal) -> Unit,
    onAdd: (Slot) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = if (isToday) BorderStroke(2.dp, colors.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    day.format(DateTimeFormatter.ofPattern("EEEE")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    day.format(DateTimeFormatter.ofPattern("MMM d")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                if (isToday) {
                    Spacer(Modifier.width(8.dp))
                    Text("Today", style = MaterialTheme.typography.labelMedium, color = colors.primary, fontWeight = FontWeight.Bold)
                }
                // One serving of each planned meal that has nutrition info.
                val calories = remember(slots) {
                    slots.values.flatten().mapNotNull { it.meal.nutrition().perServing?.calories }
                }
                if (calories.isNotEmpty()) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "≈ ${"%,d".format(calories.sum().roundToInt())} cal",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Slot.entries.forEach { slot ->
                SlotRow(slot, slots[slot].orEmpty(), people, onOpen, onAdd = { onAdd(slot) })
            }
        }
    }
}

@Composable
private fun SlotRow(slot: Slot, meals: List<PlannedMeal>, people: List<Person>, onOpen: (PlannedMeal) -> Unit, onAdd: () -> Unit) {
    val style = slot.style()
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(style.icon, null, tint = style.color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                slot.label,
                style = MaterialTheme.typography.labelLarge,
                color = style.color,
                modifier = Modifier.weight(1f),
            )
            // An empty slot is itself the add button (below), so the small + only
            // shows once something is planned there.
            if (meals.isNotEmpty()) {
                TextButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Text("Add", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (meals.isEmpty()) {
            Text(
                "+ Add ${if (slot == Slot.SNACK) "a snack" else slot.label.lowercase()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAdd)
                    .padding(start = 24.dp, top = 6.dp, bottom = 8.dp),
            )
        }
        meals.forEach { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(p) }
                    .padding(start = 24.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MealThumb(p.meal, 40.dp, corner = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        p.meal.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    eatersLabel(p.entry.eaterIds, people)?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderDialog(current: ReminderSettings, onSave: (ReminderSettings) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weekly reminder") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me to plan next week", modifier = Modifier.weight(1f))
                    Switch(checked = draft.enabled, onCheckedChange = { draft = draft.copy(enabled = it) })
                }
                if (draft.enabled) {
                    Spacer(Modifier.height(8.dp))
                    DayOfWeek.entries.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { d ->
                                FilterChip(
                                    selected = d == draft.day,
                                    onClick = { draft = draft.copy(day = d) },
                                    label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> draft = draft.copy(minuteOfDay = h * 60 + m) },
                            draft.minuteOfDay / 60, draft.minuteOfDay % 60,
                            android.text.format.DateFormat.is24HourFormat(context),
                        ).show()
                    }) {
                        Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(draft.time.format(DateTimeFormatter.ofPattern("h:mm a")))
                    }
                    Text(
                        draft.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private enum class PdfAction { PRINT, SHARE, SAVE }

@Composable
private fun MenuPdfDialog(vm: MainViewModel, weekLabel: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var includeRecipes by remember { mutableStateOf(true) }
    var includePhotos by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var toSave by remember { mutableStateOf<java.io.File?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = toSave
        if (uri != null && file != null) {
            scope.launch {
                val ok = runCatching { withContext(Dispatchers.IO) { PdfFiles.copyTo(context, file, uri) } }.isSuccess
                vm.notify(if (ok) "Menu saved" else "Couldn't save the menu")
            }
        }
        onDismiss()
    }

    fun run(action: PdfAction) {
        busy = true
        scope.launch {
            val file = runCatching { vm.buildMenuPdf(includeRecipes, includePhotos) }.getOrElse {
                vm.notify("Couldn't make the PDF: ${it.message}")
                null
            }
            busy = false
            if (file == null) {
                vm.notify("Nothing is planned for $weekLabel yet")
                onDismiss()
                return@launch
            }
            when (action) {
                PdfAction.PRINT -> { runCatching { PdfFiles.print(context, file, "Weekly menu $weekLabel") }; onDismiss() }
                PdfAction.SHARE -> { runCatching { PdfFiles.share(context, file) }; onDismiss() }
                PdfAction.SAVE -> {
                    toSave = file
                    saveLauncher.launch(file.name)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Weekly menu PDF") },
        text = {
            Column {
                Text(
                    "A printable menu for $weekLabel with every day's meals.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { includeRecipes = !includeRecipes },
                ) {
                    Checkbox(checked = includeRecipes, onCheckedChange = { includeRecipes = it })
                    Text("Add a recipe page for each meal")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = includeRecipes) { includePhotos = !includePhotos },
                ) {
                    Checkbox(checked = includePhotos && includeRecipes, enabled = includeRecipes, onCheckedChange = { includePhotos = it })
                    Text("Include meal photos")
                }
                Spacer(Modifier.height(12.dp))
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Making your menu…")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { run(PdfAction.PRINT) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Print, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Print")
                        }
                        OutlinedButton(onClick = { run(PdfAction.SHARE) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                    OutlinedButton(onClick = { run(PdfAction.SAVE) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save to phone")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PeopleDialog(current: List<Person>, onSave: (List<Person>) -> Unit, onDismiss: () -> Unit) {
    val names = remember { mutableStateListOf<Pair<Int, String>>().apply { addAll(current.map { it.id to it.name }) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("People") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Who eats at your house. On any planned meal you can mark who's having it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                names.forEachIndexed { i, (id, name) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { names[i] = id to it.take(20) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).padding(vertical = 3.dp),
                        )
                        IconButton(enabled = names.size > 1, onClick = { names.removeAt(i) }) {
                            Icon(Icons.Filled.Close, "Remove")
                        }
                    }
                }
                TextButton(onClick = { names.add(((names.maxOfOrNull { it.first } ?: 0) + 1) to "") }) {
                    Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add person")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cleaned = names.map { (id, n) -> Person(id, n.trim()) }.filter { it.name.isNotEmpty() }
                if (cleaned.isNotEmpty()) onSave(cleaned)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
