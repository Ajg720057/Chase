package com.chase.mealplan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MealEntity::class, PlanEntryEntity::class, GroceryItemEntity::class, SectionOverrideEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun planDao(): PlanDao
    abstract fun groceryDao(): GroceryDao
    abstract fun sectionDao(): SectionDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mealplanner.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /** Adds leftovers, which don't go on the grocery list. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `plan_entries` ADD COLUMN `leftover` INTEGER")
            }
        }

        /** Adds who's eating each planned meal. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `plan_entries` ADD COLUMN `eaters` TEXT")
            }
        }

        /** Adds servings, typed-in nutrition and store sections. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `meals` ADD COLUMN `servings` INTEGER")
                db.execSQL("ALTER TABLE `meals` ADD COLUMN `calories` REAL")
                db.execSQL("ALTER TABLE `meals` ADD COLUMN `protein` REAL")
                db.execSQL("ALTER TABLE `meals` ADD COLUMN `carbs` REAL")
                db.execSQL("ALTER TABLE `meals` ADD COLUMN `fat` REAL")
                db.execSQL("ALTER TABLE `plan_entries` ADD COLUMN `servings` INTEGER")
                // Rebuilt rather than altered so the new column needs no SQL default.
                db.execSQL(
                    "CREATE TABLE `grocery_items_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`key` TEXT NOT NULL, `name` TEXT NOT NULL, `amount` TEXT NOT NULL, `meals` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, `checked` INTEGER NOT NULL, `manual` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, `section` TEXT NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO `grocery_items_new` (`id`, `key`, `name`, `amount`, `meals`, `category`, " +
                        "`checked`, `manual`, `sortOrder`, `section`) SELECT `id`, `key`, `name`, `amount`, " +
                        "`meals`, `category`, `checked`, `manual`, `sortOrder`, 'OTHER' FROM `grocery_items`",
                )
                db.execSQL("DROP TABLE `grocery_items`")
                db.execSQL("ALTER TABLE `grocery_items_new` RENAME TO `grocery_items`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `section_overrides` (`key` TEXT NOT NULL, " +
                        "`section` TEXT NOT NULL, PRIMARY KEY(`key`))",
                )
            }
        }
    }
}
