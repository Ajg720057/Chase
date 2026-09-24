package com.chase.mealplan.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.chase.mealplan.grocery.IngredientLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Saves everything (meals, photos, plan, grocery list) to one .zip you can keep in
 * Drive or Downloads, and restores from it. Restoring replaces all current data.
 */
class Backup(
    private val context: Context,
    private val db: AppDatabase,
    private val photos: PhotoStore,
    private val settings: Settings,
) {
    private val converters = Converters()

    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("version", 1)
            put("meals", JSONArray(db.mealDao().all().map { it.toJson() }))
            put("plan", JSONArray(db.planDao().all().map { it.toJson() }))
            put("grocery", JSONArray(db.groceryDao().all().map { it.toJson() }))
            put("groceryWeek", settings.groceryWeek.value?.toString() ?: JSONObject.NULL)
        }
        val out = context.contentResolver.openOutputStream(target) ?: error("Could not open file")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(json.toString().toByteArray())
            zip.closeEntry()
            photos.dir.listFiles()?.forEach { f ->
                zip.putNextEntry(ZipEntry("photos/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    suspend fun import(source: Uri) = withContext(Dispatchers.IO) {
        var json: JSONObject? = null
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        val input = context.contentResolver.openInputStream(source) ?: error("Could not open file")
        ZipInputStream(input.buffered()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                when {
                    e.name == "data.json" -> json = JSONObject(zip.readBytes().decodeToString())
                    e.name.startsWith("photos/") && !e.isDirectory -> {
                        File(staging, File(e.name).name).outputStream().use { zip.copyTo(it) }
                    }
                }
                e = zip.nextEntry
            }
        }
        val data = json ?: error("That file isn't a Meal Planner backup")

        db.withTransaction {
            db.groceryDao().deleteAll()
            db.planDao().deleteAll()
            db.mealDao().deleteAll()
            db.mealDao().insertAll(data.getJSONArray("meals").objects().map { it.toMeal() })
            db.planDao().insertAll(data.getJSONArray("plan").objects().map { it.toEntry() })
            db.groceryDao().insertAll(data.optJSONArray("grocery")?.objects()?.map { it.toGrocery() }.orEmpty())
        }
        settings.setGroceryWeek(
            data.optString("groceryWeek").takeIf { it.isNotEmpty() && it != "null" }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
        photos.dir.deleteRecursively()
        photos.dir.mkdirs()
        staging.listFiles()?.forEach { it.copyTo(photos.file(it.name), overwrite = true) }
        staging.deleteRecursively()
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun MealEntity.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("recipe", recipe)
        put("ingredients", JSONArray(converters.ingredientsToJson(ingredients)))
        put("supplies", JSONArray(supplies))
        put("photo", photo ?: JSONObject.NULL)
        put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun JSONObject.toMeal() = MealEntity(
        id = getLong("id"), name = optString("name"), recipe = optString("recipe"),
        ingredients = optJSONArray("ingredients")?.let { converters.ingredientsFromJson(it.toString()) }
            ?: emptyList<IngredientLine>(),
        supplies = optJSONArray("supplies")?.let { converters.stringsFromJson(it.toString()) } ?: emptyList(),
        photo = if (isNull("photo")) null else optString("photo"),
        createdAt = optLong("createdAt"), updatedAt = optLong("updatedAt"),
    )

    private fun PlanEntryEntity.toJson() = JSONObject().apply {
        put("id", id); put("date", date); put("slot", slot.name); put("mealId", mealId); put("sortOrder", sortOrder)
    }

    private fun JSONObject.toEntry() = PlanEntryEntity(
        id = getLong("id"), date = getString("date"),
        slot = runCatching { Slot.valueOf(getString("slot")) }.getOrDefault(Slot.DINNER),
        mealId = getLong("mealId"), sortOrder = optLong("sortOrder"),
    )

    private fun GroceryItemEntity.toJson() = JSONObject().apply {
        put("id", id); put("key", key); put("name", name); put("amount", amount); put("meals", meals)
        put("category", category.name); put("checked", checked); put("manual", manual); put("sortOrder", sortOrder)
    }

    private fun JSONObject.toGrocery() = GroceryItemEntity(
        id = getLong("id"), key = optString("key"), name = optString("name"), amount = optString("amount"),
        meals = optString("meals"),
        category = runCatching { GroceryCategory.valueOf(getString("category")) }.getOrDefault(GroceryCategory.EXTRA),
        checked = optBoolean("checked"), manual = optBoolean("manual"), sortOrder = optLong("sortOrder"),
    )
}
