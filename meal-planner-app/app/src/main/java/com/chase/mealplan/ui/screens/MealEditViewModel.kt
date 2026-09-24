package com.chase.mealplan.ui.screens

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chase.mealplan.MealPlanApp
import com.chase.mealplan.data.MealDraft
import com.chase.mealplan.data.MealEntity
import com.chase.mealplan.data.Slot
import com.chase.mealplan.grocery.IngredientLine
import com.chase.mealplan.grocery.Ingredients
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class IngredientRow(val id: Long, amount: String = "", name: String = "") {
    var amount by mutableStateOf(amount)
    var name by mutableStateOf(name)
}

class SupplyRow(val id: Long, text: String = "") {
    var text by mutableStateOf(text)
}

/**
 * Editor for one meal. [mealId] edits a saved meal; [date]+[slot] adds the result to the plan;
 * [entryId] swaps the meal on an existing plan spot.
 */
class MealEditViewModel(
    private val app: MealPlanApp,
    mealId: Long?,
    val date: LocalDate?,
    val slot: Slot?,
    val entryId: Long?,
) : ViewModel() {
    private val repo = app.repo
    private var nextRowId = 0L

    /** The saved meal being edited, or the one picked from suggestions. Null for a brand-new meal. */
    var loadedMealId by mutableStateOf(mealId)
        private set
    var name by mutableStateOf("")
    var recipe by mutableStateOf("")
    var photo by mutableStateOf<String?>(null)
        private set
    val ingredients = mutableStateListOf<IngredientRow>()
    val supplies = mutableStateListOf<SupplyRow>()

    var dirty by mutableStateOf(false)
        private set
    var importingPhoto by mutableStateOf(false)
        private set

    val allMeals: StateFlow<List<MealEntity>> = repo.allMeals
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isEditingSaved get() = loadedMealId != null && date == null && entryId == null

    init {
        if (mealId != null) {
            viewModelScope.launch { repo.getMeal(mealId)?.let(::fill) }
        } else {
            ingredients += IngredientRow(nextRowId++)
        }
    }

    fun touch() { dirty = true }

    /** Name of the meal picked from suggestions; renaming it makes a new meal instead. */
    private var pickedName: String? = null

    /** Fills the form from a saved meal (from the suggestions list). */
    fun useSaved(meal: MealEntity) {
        fill(meal)
        pickedName = meal.name
        dirty = true
    }

    private fun fill(meal: MealEntity) {
        loadedMealId = meal.id
        name = meal.name
        recipe = meal.recipe
        photo = meal.photo
        ingredients.clear()
        meal.ingredients.forEach { ingredients += IngredientRow(nextRowId++, it.amount, it.name) }
        if (ingredients.isEmpty()) ingredients += IngredientRow(nextRowId++)
        supplies.clear()
        meal.supplies.forEach { supplies += SupplyRow(nextRowId++, it) }
    }

    fun addIngredient() { ingredients += IngredientRow(nextRowId++); dirty = true }
    fun removeIngredient(row: IngredientRow) { ingredients.remove(row); dirty = true }

    /** Adds one ingredient per pasted line, splitting "2 cups flour" into amount and name. */
    fun pasteIngredients(text: String) {
        val lines = text.lines().map { Ingredients.parseLine(it) }.filter { it.name.isNotBlank() }
        if (lines.isEmpty()) return
        ingredients.removeAll { it.name.isBlank() && it.amount.isBlank() }
        lines.forEach { ingredients += IngredientRow(nextRowId++, it.amount, it.name) }
        dirty = true
    }

    fun addSupply() { supplies += SupplyRow(nextRowId++); dirty = true }
    fun removeSupply(row: SupplyRow) { supplies.remove(row); dirty = true }

    fun importPhoto(uri: Uri) {
        viewModelScope.launch {
            importingPhoto = true
            val stored = app.photos.import(uri)
            importingPhoto = false
            if (stored != null) {
                photo = stored
                dirty = true
            }
        }
    }

    fun removePhoto() { photo = null; dirty = true }

    fun canSave() = name.isNotBlank() && !importingPhoto

    fun save(onSaved: () -> Unit) {
        val draft = MealDraft(
            name = name,
            recipe = recipe.trim(),
            ingredients = ingredients.map { IngredientLine(it.amount, it.name) },
            supplies = supplies.map { it.text },
            photo = photo,
        )
        val picked = pickedName
        val mealId = if (picked != null && !picked.equals(name.trim(), ignoreCase = true)) null else loadedMealId
        // Runs in the app scope so it finishes even though the screen closes right away.
        app.appScope.launch { repo.saveMeal(mealId, draft, date, slot, entryId) }
        onSaved()
    }
}
