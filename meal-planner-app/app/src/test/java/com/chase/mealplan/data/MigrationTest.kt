package com.chase.mealplan.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chase.mealplan.grocery.IngredientLine
import com.chase.mealplan.grocery.StoreSection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Upgrading from the first release must keep every meal, plan entry and grocery item. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    @Test
    fun migratesVersion1Data() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)

        // The version 1 tables exactly as the first release created them.
        object : SQLiteOpenHelper(context, name, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `meals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `recipe` TEXT NOT NULL, `ingredients` TEXT NOT NULL, " +
                        "`supplies` TEXT NOT NULL, `photo` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_name` ON `meals` (`name`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, `slot` TEXT NOT NULL, `mealId` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`mealId`) REFERENCES `meals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_entries_mealId` ON `plan_entries` (`mealId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_entries_date` ON `plan_entries` (`date`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `grocery_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`key` TEXT NOT NULL, `name` TEXT NOT NULL, `amount` TEXT NOT NULL, `meals` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, `checked` INTEGER NOT NULL, `manual` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO meals VALUES (1, 'Tacos', 'Cook it', " +
                        "'[{\"amount\":\"1 lb\",\"name\":\"ground beef\"}]', '[\"foil\"]', NULL, 1, 2)",
                )
                db.execSQL("INSERT INTO plan_entries VALUES (5, '2026-09-25', 'DINNER', 1, 10)")
                db.execSQL("INSERT INTO grocery_items VALUES (7, 'ground beef', 'Ground beef', '1 lb', 'Tacos', 'INGREDIENT', 1, 0, 0)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }.writableDatabase.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            val meal = db.mealDao().get(1)!!
            assertEquals("Tacos", meal.name)
            assertEquals(listOf(IngredientLine("1 lb", "ground beef")), meal.ingredients)
            assertEquals(listOf("foil"), meal.supplies)
            assertNull(meal.servings)
            assertNull(meal.calories)

            val entry = db.planDao().get(5)!!
            assertEquals(Slot.DINNER, entry.slot)
            assertNull(entry.servings)

            val item = db.groceryDao().all().single()
            assertEquals("Ground beef", item.name)
            assertEquals(true, item.checked)
            assertEquals(StoreSection.OTHER, item.section)

            // New columns and table work after the upgrade.
            db.mealDao().update(meal.copy(servings = 4, calories = 500.0))
            assertEquals(4, db.mealDao().get(1)!!.servings)
            db.sectionDao().put(SectionOverrideEntity("ground beef", StoreSection.MEAT))
            db.groceryDao().setSection("ground beef", StoreSection.MEAT)
            assertEquals(StoreSection.MEAT, db.groceryDao().all().single().section)
        }
        db.close()
    }
}
