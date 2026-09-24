package com.chase.mealplan.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.chase.mealplan.data.GroceryCategory
import com.chase.mealplan.data.GroceryItemEntity
import com.chase.mealplan.data.Week
import com.chase.mealplan.ui.ConfirmDialog
import com.chase.mealplan.ui.MainViewModel
import com.chase.mealplan.ui.WeekSwitcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroceryScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val items by vm.grocery.collectAsState()
    val week by vm.week.collectAsState()
    val builtFrom by vm.groceryWeek.collectAsState()
    var newItem by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val checkedCount = items.count { it.checked }
    val grouped = remember(items) {
        GroceryCategory.entries.mapNotNull { cat ->
            val inCat = items.filter { it.category == cat }
            if (inCat.isEmpty()) null else cat to (inCat.filter { !it.checked } + inCat.filter { it.checked })
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Grocery List") },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { share(context, items, builtFrom) }) { Icon(Icons.Filled.Share, "Share list") }
                    }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Remove checked items") },
                            enabled = checkedCount > 0,
                            onClick = { menuOpen = false; vm.clearChecked() },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear whole list") },
                            enabled = items.isNotEmpty(),
                            onClick = { menuOpen = false; confirmClear = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Build from the meals planned for:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        WeekSwitcher(week, vm::previousWeek, vm::nextWeek)
                        Button(onClick = { vm.buildGroceryList(week) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (builtFrom == week) "Rebuild list for this week" else "Build list for this week")
                        }
                        builtFrom?.let {
                            Text(
                                "Current list is for ${it.label}. Rebuilding keeps items you added and, " +
                                    "for the same week, your check marks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val add = {
                        vm.addGroceryItem(newItem)
                        newItem = ""
                    }
                    OutlinedTextField(
                        value = newItem,
                        onValueChange = { newItem = it },
                        placeholder = { Text("Add something else (e.g. paper towels)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { add() }, enabled = newItem.isNotBlank()) { Icon(Icons.Filled.Add, "Add item") }
                }
            }

            if (items.isEmpty()) {
                item {
                    Text(
                        "Your list is empty. Plan some meals for the week, then tap Build list.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            "$checkedCount of ${items.size} in the cart",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (items.isEmpty()) 0f else checkedCount.toFloat() / items.size },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            grouped.forEach { (category, list) ->
                item(key = "header-${category.name}") {
                    Text(
                        category.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(list, key = { it.id }) { item ->
                    GroceryRow(
                        item = item,
                        onToggle = { vm.setChecked(item, !item.checked) },
                        onDelete = { vm.deleteGroceryItem(item) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear the grocery list?",
            text = "Removes every item, including ones you added yourself.",
            confirm = "Clear",
            onConfirm = { vm.clearGrocery() },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun GroceryRow(
    item: GroceryItemEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
            .alpha(if (item.checked) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.amount.isNotBlank()) {
                    Text(
                        item.amount,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            if (item.meals.isNotBlank()) {
                Text(
                    "for ${item.meals}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (item.manual) {
            Box {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, "Remove ${item.name}") }
            }
        }
    }
}

private fun share(context: android.content.Context, items: List<GroceryItemEntity>, week: Week?) {
    val text = buildString {
        append("Grocery list")
        week?.let { append(" (${it.label})") }
        append("\n")
        GroceryCategory.entries.forEach { cat ->
            val open = items.filter { it.category == cat && !it.checked }
            if (open.isNotEmpty()) {
                append("\n${cat.label}\n")
                open.forEach { i ->
                    append("☐ ${i.name}")
                    if (i.amount.isNotBlank()) append(" — ${i.amount}")
                    append("\n")
                }
            }
        }
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share grocery list"))
}
