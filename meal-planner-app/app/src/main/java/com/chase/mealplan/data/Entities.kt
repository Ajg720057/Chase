package com.chase.mealplan.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.chase.mealplan.grocery.IngredientLine
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
)

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
)

data class PlannedMeal(
    @Embedded val entry: PlanEntryEntity,
    @Relation(parentColumn = "mealId", entityColumn = "id") val meal: MealEntity,
)

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
