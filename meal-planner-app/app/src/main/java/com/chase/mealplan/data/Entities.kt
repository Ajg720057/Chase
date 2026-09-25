package com.chase.mealplan.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.chase.mealplan.grocery.IngredientLine
import com.chase.mealplan.grocery.NutritionFacts
import com.chase.mealplan.grocery.StoreSection
import org.json.JSONArray
import org.json.JSONObject

enum class Slot(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snacks"),
}

/** A saved meal. Reused every time it's planned, so editing it updates every day it's on. */
@Entity(tableName = "meals", indices = [Index("name")])
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val recipe: String = "",
    val ingredients: List<IngredientLine> = emptyList(),
    val supplies: List<String> = emptyList(),
    /** File name inside the app's photos folder. */
    val photo: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** How many servings the recipe as written makes. */
    val servings: Int? = null,
    /** Nutrition per serving typed in by hand (e.g. from the recipe website). */
    val calories: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
)

// Kept outside the entity classes so Room doesn't treat them as columns.
val MealEntity.enteredNutrition: NutritionFacts?
    get() = calories?.let { NutritionFacts(it, protein ?: 0.0, carbs ?: 0.0, fat ?: 0.0) }

/** A meal placed on a day in a slot. [date] is ISO yyyy-MM-dd so it sorts as text. */
@Entity(
    tableName = "plan_entries",
    foreignKeys = [
        ForeignKey(
            entity = MealEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mealId"), Index("date")],
)
data class PlanEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val slot: Slot,
    val mealId: Long,
    val sortOrder: Long = System.currentTimeMillis(),
    /** Servings being made that day, when different from what the recipe makes. */
    val servings: Int? = null,
    /** Who's eating it, as person ids like ",1,2,". Null means everyone. */
    val eaters: String? = null,
    /** Leftovers from another day: shown on the plan but left off the grocery list. */
    val leftover: Boolean? = null,
)

val PlanEntryEntity.isLeftover: Boolean get() = leftover == true

/** "Leftovers · Me", "Wife", or null for a normal meal for everyone. */
fun entryNote(entry: PlanEntryEntity, people: List<Person>): String? =
    listOfNotNull(if (entry.isLeftover) "Leftovers" else null, eatersLabel(entry.eaterIds, people))
        .joinToString(" · ").ifEmpty { null }

/** The ids of the people eating this, or null for everyone. */
val PlanEntryEntity.eaterIds: Set<Int>?
    get() = eaters?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet()?.takeIf { it.isNotEmpty() }

/** Stores a set of person ids; null or empty means everyone. */
fun eatersColumn(ids: Set<Int>?): String? =
    ids?.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(",", prefix = ",", postfix = ",")

data class PlannedMeal(
    @Embedded val entry: PlanEntryEntity,
    @Relation(parentColumn = "mealId", entityColumn = "id") val meal: MealEntity,
)

/** How much to multiply the recipe by for this day. */
val PlannedMeal.scale: Double
    get() {
        val made = entry.servings ?: return 1.0
        val base = meal.servings ?: return 1.0
        return made.toDouble() / base
    }

enum class GroceryCategory(val label: String) {
    INGREDIENT("Ingredients"),
    SUPPLY("Supplies"),
    EXTRA("Extras"),
}

@Entity(tableName = "grocery_items")
data class GroceryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Normalized name, used to keep check marks when the list is rebuilt. */
    val key: String,
    val name: String,
    val amount: String = "",
    /** Which meals need it, e.g. "Tacos, Chili". */
    val meals: String = "",
    val category: GroceryCategory,
    val checked: Boolean = false,
    /** Added by hand rather than generated from the plan. Kept when the list is rebuilt. */
    val manual: Boolean = false,
    val sortOrder: Long = 0,
    val section: StoreSection = StoreSection.OTHER,
)

/** A store section you picked for an item, remembered for next time. */
@Entity(tableName = "section_overrides")
data class SectionOverrideEntity(
    @PrimaryKey val key: String,
    val section: StoreSection,
)

class Converters {
    @TypeConverter
    fun ingredientsToJson(list: List<IngredientLine>): String =
        JSONArray(list.map { JSONObject().put("amount", it.amount).put("name", it.name) }).toString()

    @TypeConverter
    fun ingredientsFromJson(json: String): List<IngredientLine> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            IngredientLine(o.optString("amount"), o.optString("name"))
        }
    }.getOrDefault(emptyList())

    @TypeConverter
    fun stringsToJson(list: List<String>): String = JSONArray(list).toString()

    @TypeConverter
    fun stringsFromJson(json: String): List<String> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())
}

/** Nutrition for one serving of a meal, and whether it was estimated or typed in. */
data class MealNutrition(
    /** Null when estimated but the recipe's serving count isn't set. */
    val perServing: NutritionFacts?,
    val estimated: Boolean,
    val estimate: com.chase.mealplan.grocery.NutritionEstimate?,
)

fun MealEntity.nutrition(): MealNutrition {
    enteredNutrition?.let { return MealNutrition(it, estimated = false, estimate = null) }
    val est = com.chase.mealplan.grocery.Nutrition.estimate(ingredients)
    val per = if (est.counted > 0 && est.total.calories > 0) servings?.takeIf { it > 0 }?.let { est.total / it.toDouble() } else null
    return MealNutrition(per, estimated = true, estimate = est)
}
