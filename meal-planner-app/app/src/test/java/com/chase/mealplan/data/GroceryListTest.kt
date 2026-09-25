package com.chase.mealplan.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chase.mealplan.grocery.IngredientLine
import com.chase.mealplan.grocery.StoreSection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GroceryListTest {
    @Test
    fun leftoversAndServingsShapeTheList() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = MealRepository(db, PhotoStore(context), Settings(context))
        val week = Week(LocalDate.of(2026, 9, 27))

        val chili = repo.saveMeal(
            null,
            MealDraft(
                name = "Chili", recipe = "", photo = null, servings = 4,
                ingredients = listOf(IngredientLine("1 lb", "ground beef"), IngredientLine("1 can", "black beans")),
                supplies = emptyList(),
            ),
            LocalDate.of(2026, 9, 27), Slot.DINNER, null,
        )
        // The other two servings, eaten two days later.
        repo.addToPlan(chili, LocalDate.of(2026, 9, 29), Slot.LUNCH, leftover = true)

        repo.buildGroceryList(week)
        var items = db.groceryDao().all().associateBy { it.name }
        assertEquals("1 lb", items.getValue("Ground beef").amount)
        assertEquals(StoreSection.MEAT, items.getValue("Ground beef").section)
        assertEquals("1 can", items.getValue("Black beans").amount)

        // Making 8 servings instead of 4 doubles the shopping.
        val dinner = repo.weekPlan(week).single { !it.entry.isLeftover }
        repo.setEntryServings(dinner.entry.id, 8)
        repo.buildGroceryList(week)
        items = db.groceryDao().all().associateBy { it.name }
        assertEquals("2 lb", items.getValue("Ground beef").amount)
        assertEquals("2 cans", items.getValue("Black beans").amount)
        db.close()
    }
}
