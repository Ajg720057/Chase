package com.chase.mealplan.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.chase.mealplan.MealPlanApp
import com.chase.mealplan.data.MealEntity
import com.chase.mealplan.data.nutrition
import com.chase.mealplan.grocery.Ingredients
import com.chase.mealplan.grocery.NutritionFacts
import kotlin.math.roundToInt
import com.chase.mealplan.ui.AddToPlanDialog
import com.chase.mealplan.ui.ConfirmDialog
import com.chase.mealplan.ui.MainViewModel
import com.chase.mealplan.ui.Navigator
import com.chase.mealplan.ui.SectionHeader
import com.chase.mealplan.ui.theme.style
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Shows a meal: photo, ingredients, supplies and recipe. Opened from the plan or the Meals tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(vm: MainViewModel, mealId: Long, entryId: Long?, navigator: Navigator) {
    val app = LocalContext.current.applicationContext as MealPlanApp
    val mealFlow = remember(mealId) { app.repo.observeMeal(mealId) }
    val entryFlow = remember(entryId) { entryId?.let { app.repo.observeEntry(it) } }
    // Starts as "loading" so a deleted meal can be told apart from one not read yet.
    val meal by mealFlow.collectAsState(initial = LOADING)
    val entry = entryFlow?.collectAsState(initial = null)?.value
    val week by vm.week.collectAsState()
    var confirmRemove by remember { mutableStateOf(false) }
    var addToPlan by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf(false) }

    LaunchedEffect(meal) {
        if (meal == null) navigator.back()
    }
    val m = meal?.takeIf { it !== LOADING } ?: return

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(m.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navigator.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { navigator.edit(mealId = m.id) }) { Icon(Icons.Filled.Edit, "Edit meal") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (m.photo != null) {
                AsyncImage(
                    model = app.photos.file(m.photo),
                    contentDescription = "Photo of ${m.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { viewPhoto = true },
                )
                Spacer(Modifier.height(12.dp))
            }

            if (entry != null) {
                val style = entry.slot.style()
                val date = LocalDate.parse(entry.date)
                AssistChip(
                    onClick = {},
                    label = { Text("${date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))} · ${entry.slot.label}") },
                    leadingIcon = { Icon(style.icon, null, tint = style.color, modifier = Modifier.size(18.dp)) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { navigator.edit(date = date, slot = entry.slot, entryId = entry.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.SwapHoriz, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Swap meal")
                    }
                    OutlinedButton(onClick = { confirmRemove = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.EventBusy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove")
                    }
                }
            }
            OutlinedButton(onClick = { addToPlan = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (entry != null) "Also add to another day" else "Add to plan")
            }

            // Servings: how many the recipe makes, and on a planned day how many you're making.
            val made = entry?.servings ?: m.servings
            val scale = if (entry?.servings != null && m.servings != null) entry.servings.toDouble() / m.servings else 1.0
            if (m.servings == null) {
                Muted("Tip: set how many servings the recipe makes (✎) to scale it up or down.")
            } else if (entry != null && made != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Making", style = MaterialTheme.typography.bodyLarge)
                    IconButton(
                        enabled = made > 1,
                        onClick = { vm.setEntryServings(entry.id, (made - 1).takeIf { it != m.servings }) },
                    ) { Icon(Icons.Filled.RemoveCircleOutline, "Fewer servings") }
                    Text("$made", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { vm.setEntryServings(entry.id, (made + 1).takeIf { it != m.servings }) }) {
                        Icon(Icons.Filled.AddCircleOutline, "More servings")
                    }
                    Text("servings", style = MaterialTheme.typography.bodyLarge)
                }
                Muted(
                    if (scale == 1.0) "The recipe makes ${m.servings}."
                    else "Amounts below and on the grocery list are scaled from the recipe's ${m.servings} servings.",
                )
            } else {
                Muted("Makes ${m.servings} servings.")
            }

            SectionHeader("Ingredients")
            if (m.ingredients.isEmpty()) {
                Muted("No ingredients yet. Tap ✎ to add them.")
            } else {
                m.ingredients.forEach { line ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        Bullet()
                        if (line.amount.isNotBlank()) {
                            Text(Ingredients.scaleAmount(line.amount, scale), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(line.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            SectionHeader("Supplies")
            if (m.supplies.isEmpty()) {
                Muted("No supplies listed.")
            } else {
                m.supplies.forEach { s ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        Bullet()
                        Text(s, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            SectionHeader("Recipe")
            if (m.recipe.isBlank()) {
                Muted("No recipe yet.")
            } else {
                SelectionContainer {
                    Text(m.recipe, style = MaterialTheme.typography.bodyLarge)
                }
            }

            SectionHeader("Nutrition per serving")
            val nutrition = remember(m) { m.nutrition() }
            val per = nutrition.perServing
            when {
                per != null -> {
                    NutritionTiles(per)
                    Muted(
                        if (nutrition.estimated) "Estimated from the ingredients. Rough guide only."
                        else "From the numbers you entered.",
                    )
                }
                (nutrition.estimate?.total?.calories ?: 0.0) > 0.0 ->
                    Muted("Whole recipe: ${nutrition.estimate!!.total.summary()}. Set the servings (✎) to see it per serving.")
                else -> Muted("Add ingredients with amounts, or type in the numbers from the recipe (✎).")
            }
            nutrition.estimate?.missing?.takeIf { it.isNotEmpty() }?.let {
                Muted("Not counted: ${it.joinToString(", ")}")
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmRemove && entry != null) {
        ConfirmDialog(
            title = "Remove from this day?",
            text = "${m.name} stays saved in your Meals tab so you can plan it again.",
            confirm = "Remove",
            onConfirm = {
                vm.removeFromPlan(entry.id)
                navigator.back()
            },
            onDismiss = { confirmRemove = false },
        )
    }
    if (addToPlan) {
        AddToPlanDialog(
            startWeek = week,
            onPick = { date, slot -> vm.addToPlan(m.id, date, slot) },
            onDismiss = { addToPlan = false },
        )
    }
    if (viewPhoto && m.photo != null) {
        Dialog(onDismissRequest = { viewPhoto = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().clickable { viewPhoto = false }, contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = app.photos.file(m.photo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private val LOADING = MealEntity(id = -1, name = "")

@Composable
private fun NutritionTiles(n: NutritionFacts) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        listOf(
            "Calories" to "${n.calories.roundToInt()}",
            "Protein" to "${n.protein.roundToInt()} g",
            "Carbs" to "${n.carbs.roundToInt()} g",
            "Fat" to "${n.fat.roundToInt()} g",
        ).forEach { (label, value) ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun Bullet() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 9.dp, end = 10.dp).size(6.dp),
    ) {}
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
