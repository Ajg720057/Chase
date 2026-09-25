package com.chase.mealplan.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Query("SELECT * FROM meals ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals WHERE id = :id")
    fun observe(id: Long): Flow<MealEntity?>

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun get(id: Long): MealEntity?

    @Query("SELECT * FROM meals WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): MealEntity?

    @Query("SELECT * FROM meals")
    suspend fun all(): List<MealEntity>

    @Insert
    suspend fun insert(meal: MealEntity): Long

    @Insert
    suspend fun insertAll(meals: List<MealEntity>)

    @Update
    suspend fun update(meal: MealEntity)

    @Delete
    suspend fun delete(meal: MealEntity)

    @Query("DELETE FROM meals")
    suspend fun deleteAll()
}

@Dao
interface PlanDao {
    @Transaction
    @Query("SELECT * FROM plan_entries WHERE date BETWEEN :start AND :end ORDER BY date, sortOrder")
    fun observeRange(start: String, end: String): Flow<List<PlannedMeal>>

    @Transaction
    @Query("SELECT * FROM plan_entries WHERE date BETWEEN :start AND :end ORDER BY date, sortOrder")
    suspend fun range(start: String, end: String): List<PlannedMeal>

    @Query("SELECT * FROM plan_entries WHERE id = :id")
    fun observe(id: Long): Flow<PlanEntryEntity?>

    @Query("SELECT * FROM plan_entries WHERE id = :id")
    suspend fun get(id: Long): PlanEntryEntity?

    @Query("SELECT * FROM plan_entries")
    suspend fun all(): List<PlanEntryEntity>

    @Insert
    suspend fun insert(entry: PlanEntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<PlanEntryEntity>)

    @Query("UPDATE plan_entries SET leftover = :leftover WHERE id = :id")
    suspend fun setLeftover(id: Long, leftover: Boolean?)

    @Query("UPDATE plan_entries SET eaters = :eaters WHERE id = :id")
    suspend fun setEaters(id: Long, eaters: String?)

    @Query("UPDATE plan_entries SET servings = :servings WHERE id = :id")
    suspend fun setServings(id: Long, servings: Int?)

    @Query("UPDATE plan_entries SET mealId = :mealId WHERE id = :id")
    suspend fun setMeal(id: Long, mealId: Long)

    @Query("DELETE FROM plan_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM plan_entries WHERE date BETWEEN :start AND :end")
    suspend fun deleteRange(start: String, end: String)

    @Query("DELETE FROM plan_entries")
    suspend fun deleteAll()
}

@Dao
interface GroceryDao {
    @Query("SELECT * FROM grocery_items ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<GroceryItemEntity>>

    @Query("SELECT * FROM grocery_items")
    suspend fun all(): List<GroceryItemEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM grocery_items")
    suspend fun maxOrder(): Long

    @Insert
    suspend fun insert(item: GroceryItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<GroceryItemEntity>)

    @Query("UPDATE grocery_items SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("UPDATE grocery_items SET section = :section WHERE `key` = :key")
    suspend fun setSection(key: String, section: com.chase.mealplan.grocery.StoreSection)

    @Query("DELETE FROM grocery_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM grocery_items WHERE checked = 1")
    suspend fun deleteChecked()

    @Query("DELETE FROM grocery_items WHERE manual = 0")
    suspend fun deleteGenerated()

    @Query("DELETE FROM grocery_items")
    suspend fun deleteAll()
}

@Dao
interface SectionDao {
    @Query("SELECT * FROM section_overrides")
    suspend fun all(): List<SectionOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(override: SectionOverrideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(overrides: List<SectionOverrideEntity>)

    @Query("DELETE FROM section_overrides")
    suspend fun deleteAll()
}
