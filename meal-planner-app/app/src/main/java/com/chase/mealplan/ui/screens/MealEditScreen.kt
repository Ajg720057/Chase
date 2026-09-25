package com.chase.mealplan.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chase.mealplan.MealPlanApp
import com.chase.mealplan.grocery.IngredientLine
import com.chase.mealplan.grocery.Nutrition
import com.chase.mealplan.ui.ConfirmDialog
import com.chase.mealplan.ui.EatersPicker
import com.chase.mealplan.ui.MealThumb
import com.chase.mealplan.ui.Navigator
import com.chase.mealplan.ui.SectionHeader
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditScreen(vm: MealEditViewModel, navigator: Navigator) {
    val app = LocalContext.current.applicationContext as MealPlanApp
    val allMeals by vm.allMeals.collectAsState()
    var nameFocused by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var pasteOpen by remember { mutableStateOf(false) }

    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) vm.importPhoto(uri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::importPhoto)
    }

    val leave: () -> Unit = {
        if (vm.dirty) {
            confirmDiscard = true
        } else {
            navigator.back()
        }
    }
    BackHandler(onBack = leave)

    val title = when {
        vm.date != null && vm.slot != null -> {
            val day = vm.date.format(DateTimeFormatter.ofPattern("EEEE"))
            if (vm.entryId != null) "Swap $day ${vm.slot.label.lowercase()}" else "$day ${vm.slot.label.lowercase()}"
        }
        vm.isEditingSaved -> "Edit meal"
        else -> "New meal"
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.Filled.Close, "Cancel") } },
                actions = {
                    TextButton(enabled = vm.canSave(), onClick = { vm.save { navigator.back() } }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = vm.name,
                onValueChange = { vm.name = it; vm.touch() },
                label = { Text("Meal name") },
                placeholder = { Text("e.g. Chicken tacos") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { nameFocused = it.isFocused },
            )

            // Saved meals matching what's typed. Tapping one fills in everything.
            val query = vm.name.trim()
            val suggestions = remember(query, allMeals, vm.loadedMealId) {
                allMeals
                    .filter { it.id != vm.loadedMealId }
                    .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
                    .sortedBy { !it.name.startsWith(query, ignoreCase = true) }
                    .take(6)
            }
            val showSuggestions = suggestions.isNotEmpty() && !vm.isEditingSaved &&
                (nameFocused || (query.isEmpty() && vm.loadedMealId == null))
            if (showSuggestions) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(
                        if (query.isEmpty()) "Pick a saved meal" else "Saved meals",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
                    )
                    suggestions.forEach { meal ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.useSaved(meal) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MealThumb(meal, 36.dp, corner = 8.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(meal.name, style = MaterialTheme.typography.bodyLarge)
                                if (meal.ingredients.isNotEmpty()) {
                                    Text(
                                        "${meal.ingredients.size} ingredients",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (vm.loadedMealId != null && !vm.isEditingSaved) {
                Text(
                    "Using your saved recipe. Changes here update it everywhere it's planned.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                Text("Recipe makes", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = vm.servings,
                    onValueChange = { v -> vm.servings = v.filter { it.isDigit() }.take(3); vm.touch() },
                    placeholder = { Text("4") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.width(80.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("servings", style = MaterialTheme.typography.bodyLarge)
            }

            if (vm.date != null && vm.entryId == null && vm.people.size > 1) {
                SectionHeader("Who's eating")
                EatersPicker(vm.people, vm.eaters) { vm.eaters = it; vm.touch() }
            }

            SectionHeader("Photo")
            PhotoPicker(
                photoFile = vm.photo?.let { app.photos.file(it) },
                busy = vm.importingPhoto,
                onTake = {
                    val uri = app.photos.newCameraUri()
                    cameraUri = uri
                    runCatching { takePicture.launch(uri) }
                },
                onChoose = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemove = vm::removePhoto,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Ingredients", Modifier.weight(1f))
                TextButton(onClick = { pasteOpen = true }, modifier = Modifier.padding(top = 12.dp)) {
                    Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Paste a list")
                }
            }
            vm.ingredients.forEach { row ->
                androidx.compose.runtime.key(row.id) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                        OutlinedTextField(
                            value = row.amount,
                            onValueChange = { row.amount = it; vm.touch() },
                            placeholder = { Text("Amount") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.weight(0.36f),
                        )
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(
                            value = row.name,
                            onValueChange = { row.name = it; vm.touch() },
                            placeholder = { Text("Ingredient") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.weight(0.64f),
                        )
                        IconButton(onClick = { vm.removeIngredient(row) }) { Icon(Icons.Filled.Close, "Remove ingredient") }
                    }
                }
            }
            OutlinedButton(onClick = vm::addIngredient) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add ingredient")
            }

            SectionHeader("Supplies")
            Text(
                "Things you need that aren't food, like foil, skewers or paper plates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            vm.supplies.forEach { row ->
                androidx.compose.runtime.key(row.id) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                        OutlinedTextField(
                            value = row.text,
                            onValueChange = { row.text = it; vm.touch() },
                            placeholder = { Text("Supply") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.removeSupply(row) }) { Icon(Icons.Filled.Close, "Remove supply") }
                    }
                }
            }
            OutlinedButton(onClick = vm::addSupply) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add supply")
            }

            SectionHeader("Recipe")
            OutlinedTextField(
                value = vm.recipe,
                onValueChange = { vm.recipe = it; vm.touch() },
                placeholder = { Text("Steps, cook time, notes…") },
                minLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            NutritionEditor(vm)

            Spacer(Modifier.height(20.dp))
            Button(
                enabled = vm.canSave(),
                onClick = { vm.save { navigator.back() } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (vm.date != null) "Save to plan" else "Save meal") }
            if (vm.name.isBlank()) {
                Text(
                    "Give the meal a name to save it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            text = "What you've entered here won't be saved.",
            confirm = "Discard",
            onConfirm = { navigator.back() },
            onDismiss = { confirmDiscard = false },
        )
    }
    if (pasteOpen) {
        PasteIngredientsDialog(
            onAdd = vm::pasteIngredients,
            onDismiss = { pasteOpen = false },
        )
    }
}

@Composable
private fun PhotoPicker(
    photoFile: java.io.File?,
    busy: Boolean,
    onTake: () -> Unit,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        if (photoFile != null || busy) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (photoFile != null) {
                    AsyncImage(
                        model = photoFile,
                        contentDescription = "Meal photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) {
                        IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove photo", tint = Color.White) }
                    }
                }
                if (busy) CircularProgressIndicator()
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onTake, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (photoFile == null) "Take photo" else "Retake")
            }
            OutlinedButton(onClick = onChoose, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Choose")
            }
        }
    }
}

@Composable
private fun PasteIngredientsDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste ingredients") },
        text = {
            Column {
                Text(
                    "One ingredient per line, e.g. copied from a recipe website. Amounts like \"2 cups\" are split out automatically.",
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.Transparent)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("2 cups flour\n1 tsp salt\n3 eggs") },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onAdd(text); onDismiss() }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NutritionEditor(vm: MealEditViewModel) {
    val estimate by remember {
        derivedStateOf {
            Nutrition.estimate(vm.ingredients.map { IngredientLine(it.amount, it.name) })
        }
    }
    val servings = vm.servings.toIntOrNull()?.takeIf { it > 0 }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    SectionHeader("Nutrition per serving")
    when {
        estimate.counted == 0 || estimate.total.calories <= 0.0 ->
            Text("Add ingredients with amounts to get an estimate.", style = MaterialTheme.typography.bodySmall, color = muted)
        servings == null ->
            Text(
                "Whole recipe: ${estimate.total.summary()}. Fill in \"Recipe makes … servings\" to see it per serving.",
                style = MaterialTheme.typography.bodySmall, color = muted,
            )
        else ->
            Text(
                "Estimated from ingredients: ${(estimate.total / servings.toDouble()).summary()}",
                style = MaterialTheme.typography.bodyMedium,
            )
    }
    if (estimate.missing.isNotEmpty()) {
        Text(
            "Not counted: ${estimate.missing.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 2.dp),
        )
    }
    Text(
        "Or type in the numbers from the recipe (they're used instead of the estimate):",
        style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Calories", vm.calories, { vm.calories = it; vm.touch() }, Modifier.weight(1f))
        NumberField("Protein (g)", vm.protein, { vm.protein = it; vm.touch() }, Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Carbs (g)", vm.carbs, { vm.carbs = it; vm.touch() }, Modifier.weight(1f))
        NumberField("Fat (g)", vm.fat, { vm.fat = it; vm.touch() }, Modifier.weight(1f))
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}
