package com.chase.mealplan.data

import androidx.room.withTransaction
import com.chase.mealplan.grocery.IngredientLine
import com.chase.mealplan.grocery.IngredientUse
import com.chase.mealplan.grocery.Ingredients
import com.chase.mealplan.grocery.StoreSection
import com.chase.mealplan.grocery.StoreSections
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** What the meal editor hands back when the user taps Save. */
data class MealDraft(
    val name: String,
    val recipe: String,
    val ingredients: List<IngredientLine>,
    val supplies: List<String>,
    val photo: String?,
    val servings: Int? = null,
    /** Typed-in nutrition per serving; all null to estimate from ingredients. */
    val calories: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
) {
    val hasContent: Boolean
        get() = recipe.isNotBlank() || ingredients.isNotEmpty() || supplies.isNotEmpty() || photo != null ||
            servings != null || calories != null
}

class MealRepository(
    private val db: AppDatabase,
    val photos: PhotoStore,
    private val settings: Settings,
) {
    private val meals = db.mealDao()
    private val plan = db.planDao()
    private val grocery = db.groceryDao()
    private val sections = db.sectionDao()

    val allMeals: Flow<List<MealEntity>> = meals.observeAll()
    val groceryItems: Flow<List<GroceryItemEntity>> = grocery.observeAll()

    fun observeMeal(id: Long): Flow<MealEntity?> = meals.observe(id)
    fun observeEntry(id: Long): Flow<PlanEntryEntity?> = plan.observe(id)
    fun observeWeek(week: Week): Flow<List<PlannedMeal>> =
        plan.observeRange(week.start.toString(), week.end.toString())

    suspend fun weekPlan(week: Week): List<PlannedMeal> = plan.range(week.start.toString(), week.end.toString())

    suspend fun getMeal(id: Long) = meals.get(id)

    /**
     * Saves the meal to the library (one meal per name) and, when [date]/[slot] are given,
     * puts it on the plan. [entryId] swaps the meal on an existing plan spot instead.
     * Returns the saved meal's id.
     */
    suspend fun saveMeal(
        mealId: Long?,
        draft: MealDraft,
        date: LocalDate?,
        slot: Slot?,
        entryId: Long?,
    ): Long = db.withTransaction {
        val name = draft.name.trim()
        val clean = draft.copy(
            name = name,
            ingredients = draft.ingredients.filter { it.name.isNotBlank() }
                .map { IngredientLine(it.amount.trim(), it.name.trim()) },
            supplies = draft.supplies.map { it.trim() }.filter { it.isNotEmpty() },
        )
        val current = mealId?.let { meals.get(it) }
        val sameName = meals.findByName(name)?.takeIf { it.id != current?.id }

        val savedId = when {
            // Typed the name of a saved meal but filled nothing in: just use the saved one.
            sameName != null && current == null && !clean.hasContent -> sameName.id
            // Same name as another saved meal: update that one rather than making a duplicate.
            sameName != null && current == null -> {
                if (sameName.photo != clean.photo && clean.photo != null) photos.delete(sameName.photo)
                meals.update(sameName.applying(clean, keepPhoto = clean.photo == null))
                sameName.id
            }
            current != null -> {
                if (current.photo != clean.photo) photos.delete(current.photo)
                meals.update(current.applying(clean, keepPhoto = false))
                current.id
            }
            else -> meals.insert(MealEntity(name = name).applying(clean, keepPhoto = false))
        }

        when {
            entryId != null -> plan.setMeal(entryId, savedId)
            date != null && slot != null -> plan.insert(PlanEntryEntity(date = date.toString(), slot = slot, mealId = savedId))
        }
        savedId
    }

    private fun MealEntity.applying(d: MealDraft, keepPhoto: Boolean) = copy(
        name = d.name, recipe = d.recipe, ingredients = d.ingredients, supplies = d.supplies,
        photo = if (keepPhoto) photo else d.photo, updatedAt = System.currentTimeMillis(),
        servings = d.servings, calories = d.calories, protein = d.protein, carbs = d.carbs, fat = d.fat,
    )

    suspend fun deleteMeal(meal: MealEntity) {
        meals.delete(meal)
        photos.delete(meal.photo)
    }

    suspend fun addToPlan(mealId: Long, date: LocalDate, slot: Slot) {
        plan.insert(PlanEntryEntity(date = date.toString(), slot = slot, mealId = mealId))
    }

    suspend fun removeFromPlan(entryId: Long) = plan.delete(entryId)

    suspend fun setEntryServings(entryId: Long, servings: Int?) = plan.setServings(entryId, servings)

    /** Moves an item to another store section, and remembers that for next time. */
    suspend fun setSection(item: GroceryItemEntity, section: StoreSection) = db.withTransaction {
        sections.put(SectionOverrideEntity(item.key, section))
        grocery.setSection(item.key, section)
    }

    private suspend fun sectionFinder(): (String, Boolean) -> StoreSection {
        val overrides = sections.all().associate { it.key to it.section }
        return { name, isSupply -> overrides[Ingredients.key(name)] ?: StoreSections.classify(name, isSupply) }
    }

    suspend fun clearWeek(week: Week) = plan.deleteRange(week.start.toString(), week.end.toString())

    /** Copies last week's plan onto the same weekdays of [week]. Returns how many were copied. */
    suspend fun copyPreviousWeek(week: Week): Int = db.withTransaction {
        val prev = week.plusWeeks(-1)
        val entries = plan.range(prev.start.toString(), prev.end.toString())
        plan.insertAll(
            entries.map {
                it.entry.copy(id = 0, date = LocalDate.parse(it.entry.date).plusWeeks(1).toString())
            },
        )
        entries.size
    }

    /**
     * Replaces the generated part of the grocery list with everything the week's meals need.
     * Items you added yourself are kept. Rebuilding the same week keeps your check marks;
     * switching to a new week drops everything already checked off.
     */
    suspend fun buildGroceryList(week: Week): Int = db.withTransaction {
        val planned = plan.range(week.start.toString(), week.end.toString())
        val ingredientItems = Ingredients.aggregate(
            planned.flatMap { p ->
                p.meal.ingredients.map { IngredientUse(Ingredients.scaleAmount(it.amount, p.scale), it.name, p.meal.name) }
            },
        )
        val supplyItems = Ingredients.aggregate(
            planned.flatMap { p -> p.meal.supplies.map { IngredientUse("", it, p.meal.name) } },
        )

        val sameWeek = settings.groceryWeek.value == week.start
        val existing = grocery.all()
        val checkedKeys = if (sameWeek) {
            existing.filter { it.checked && !it.manual }.map { it.category to it.key }.toSet()
        } else emptySet()

        grocery.deleteGenerated()
        if (!sameWeek) grocery.deleteChecked()

        var order = 0L
        val sectionOf = sectionFinder()
        fun toEntity(cat: GroceryCategory, item: com.chase.mealplan.grocery.AggregatedItem) = GroceryItemEntity(
            key = item.key, name = item.name, amount = item.amount, meals = item.meals.joinToString(", "),
            category = cat, checked = (cat to item.key) in checkedKeys, sortOrder = order++,
            section = sectionOf(item.name, cat == GroceryCategory.SUPPLY),
        )
        grocery.insertAll(
            ingredientItems.sortedBy { it.name.lowercase() }.map { toEntity(GroceryCategory.INGREDIENT, it) } +
                supplyItems.sortedBy { it.name.lowercase() }.map { toEntity(GroceryCategory.SUPPLY, it) },
        )
        settings.setGroceryWeek(week.start)
        ingredientItems.size + supplyItems.size
    }

    suspend fun addGroceryItem(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val parsed = Ingredients.parseLine(trimmed)
        val itemName = parsed.name.ifBlank { trimmed }.replaceFirstChar { it.uppercase() }
        grocery.insert(
            GroceryItemEntity(
                key = Ingredients.key(itemName), name = itemName, amount = parsed.amount,
                category = GroceryCategory.EXTRA, manual = true, sortOrder = grocery.maxOrder() + 1,
                section = sectionFinder()(itemName, false),
            ),
        )
    }

    suspend fun setChecked(id: Long, checked: Boolean) = grocery.setChecked(id, checked)
    suspend fun deleteGroceryItem(id: Long) = grocery.delete(id)
    suspend fun clearChecked() = grocery.deleteChecked()
    suspend fun clearGrocery() {
        grocery.deleteAll()
        settings.setGroceryWeek(null)
    }

    suspend fun cleanUpPhotos() {
        photos.deleteUnused(meals.all().mapNotNull { it.photo }.toSet())
    }
}
