package com.chase.mealplan.pdf

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.chase.mealplan.data.MealEntity
import com.chase.mealplan.data.Person
import com.chase.mealplan.data.PhotoStore
import com.chase.mealplan.data.PlanEntryEntity
import com.chase.mealplan.data.PlannedMeal
import com.chase.mealplan.data.Slot
import com.chase.mealplan.data.Week
import com.chase.mealplan.grocery.IngredientLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenuPdfTest {
    @Test
    fun writesMenuWithRecipePages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val photos = PhotoStore(context)
        val photo = "test.jpg"
        photos.file(photo).outputStream().use {
            Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, it)
        }
        val week = Week(LocalDate.of(2026, 9, 27))
        val tacos = MealEntity(
            id = 1, name = "Chicken tacos with a very long name that has to wrap inside its cell",
            recipe = List(80) { "Step ${it + 1}: do the next thing carefully and keep stirring." }.joinToString("\n"),
            ingredients = listOf(IngredientLine("1 lb", "chicken breast"), IngredientLine("8", "corn tortillas")),
            supplies = listOf("Foil"), photo = photo, servings = 4,
        )
        val oats = MealEntity(id = 2, name = "Overnight oats", ingredients = listOf(IngredientLine("1 cup", "oats")))
        val planned = listOf(
            PlannedMeal(PlanEntryEntity(1, "2026-09-27", Slot.DINNER, 1, servings = 8), tacos),
            PlannedMeal(PlanEntryEntity(2, "2026-09-30", Slot.LUNCH, 1), tacos),
            PlannedMeal(PlanEntryEntity(3, "2026-09-28", Slot.BREAKFAST, 2, eaters = ",1,"), oats),
        ) + List(12) { PlannedMeal(PlanEntryEntity(10L + it, "2026-09-29", Slot.SNACK, 2), oats) }

        // Robolectric can't make real PDF files, so draw the same pages onto bitmaps.
        val bitmaps = mutableListOf<Bitmap>()
        val count = MenuPdf(photos).render(
            week, planned, includeRecipes = true, includePhotos = true,
            people = listOf(Person(1, "Me"), Person(2, "Wife")),
            sink = object : MenuPdf.PageSink {
                override fun start(width: Int, height: Int): Canvas {
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    bitmaps += bmp
                    return Canvas(bmp)
                }

                override fun finish() = Unit
            },
        )
        // Menu page + tacos (the long recipe spills onto a second page) + oats.
        assertTrue("expected at least 4 pages, got $count", count >= 4)
        assertEquals(count, bitmaps.size)
        // Something was actually drawn on each page.
        bitmaps.forEach { bmp ->
            val inked = (0 until bmp.height step 8).any { y ->
                (0 until bmp.width step 8).any { x -> bmp.getPixel(x, y) != Color.WHITE }
            }
            assertTrue(inked)
        }
    }
}
