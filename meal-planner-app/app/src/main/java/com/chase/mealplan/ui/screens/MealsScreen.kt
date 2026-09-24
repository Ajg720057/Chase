package com.chase.mealplan.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chase.mealplan.data.MealEntity
import com.chase.mealplan.ui.AddToPlanDialog
import com.chase.mealplan.ui.ConfirmDialog
import com.chase.mealplan.ui.MainViewModel
import com.chase.mealplan.ui.MealRow
import com.chase.mealplan.ui.Navigator

/** Every saved meal, to browse, edit, delete or put on the plan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealsScreen(vm: MainViewModel, navigator: Navigator) {
    val meals by vm.meals.collectAsState()
    val week by vm.week.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var planning by remember { mutableStateOf<MealEntity?>(null) }
    var deleting by remember { mutableStateOf<MealEntity?>(null) }

    val shown = remember(meals, query) {
        val q = query.trim()
        if (q.isEmpty()) meals
        else meals.filter { m ->
            m.name.contains(q, ignoreCase = true) || m.ingredients.any { it.name.contains(q, ignoreCase = true) }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Saved Meals") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigator.edit() },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New meal") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (meals.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search meals or ingredients") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (meals.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No saved meals yet.\n\nEvery meal you add to the plan is saved here automatically, " +
                            "so next time you only need to type its name.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(shown, key = { it.id }) { meal ->
                        MealRow(meal, onClick = { navigator.meal(meal.id) }) {
                            var open by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "Options") }
                                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Add to plan") },
                                        leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
                                        onClick = { open = false; planning = meal },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                        onClick = { open = false; navigator.edit(mealId = meal.id) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                        onClick = { open = false; deleting = meal },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    planning?.let { meal ->
        AddToPlanDialog(
            startWeek = week,
            onPick = { date, slot -> vm.addToPlan(meal.id, date, slot) },
            onDismiss = { planning = null },
        )
    }
    deleting?.let { meal ->
        ConfirmDialog(
            title = "Delete ${meal.name}?",
            text = "The recipe is deleted and the meal is taken off every day it's planned.",
            confirm = "Delete",
            onConfirm = { vm.deleteMeal(meal) },
            onDismiss = { deleting = null },
        )
    }
}
