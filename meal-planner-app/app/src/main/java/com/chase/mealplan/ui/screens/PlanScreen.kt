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
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
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
import com.chase.mealplan.data.Slot
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
            }
            Spacer(Modifier.height(4.dp))
            Slot.entries.forEach { slot ->
                SlotRow(slot, slots[slot].orEmpty(), onOpen, onAdd = { onAdd(slot) })
            }
        }
    }
}

@Composable
private fun SlotRow(slot: Slot, meals: List<PlannedMeal>, onOpen: (PlannedMeal) -> Unit, onAdd: () -> Unit) {
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
                Text(
                    p.meal.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
