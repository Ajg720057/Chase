package com.chase.mealplan.grocery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngredientsTest {
    @Test fun parsesQuantities() {
        assertEquals(Quantity(2.0, "cup"), Ingredients.parseQuantity("2 cups"))
        assertEquals(Quantity(1.5, "tbsp"), Ingredients.parseQuantity("1 1/2 Tbsp"))
        assertEquals(Quantity(1.5, "tbsp"), Ingredients.parseQuantity("1½ tablespoons"))
        assertEquals(Quantity(0.5, "tsp"), Ingredients.parseQuantity("½ t"))
        assertEquals(Quantity(0.25, "lb"), Ingredients.parseQuantity("1/4 lb."))
        assertEquals(Quantity(3.0, ""), Ingredients.parseQuantity("3"))
        assertEquals(Quantity(8.0, "fl oz"), Ingredients.parseQuantity("8 fl oz"))
        assertNull(Ingredients.parseQuantity("a pinch"))
        assertNull(Ingredients.parseQuantity("2-3"))
        assertNull(Ingredients.parseQuantity("2 large"))
    }

    @Test fun parsesPastedLines() {
        assertEquals(IngredientLine("2 cups", "flour, sifted"), Ingredients.parseLine("• 2 cups of flour, sifted"))
        assertEquals(IngredientLine("3", "eggs"), Ingredients.parseLine("3 eggs"))
        assertEquals(IngredientLine("1 1/2 tsp", "salt"), Ingredients.parseLine("1 1/2 tsp salt"))
        assertEquals(IngredientLine("½ cup", "milk"), Ingredients.parseLine("▢ ½ cup milk"))
        assertEquals(IngredientLine("2-3 cloves", "garlic"), Ingredients.parseLine("2-3 cloves garlic"))
        assertEquals(IngredientLine("", "Salt and pepper to taste"), Ingredients.parseLine("Salt and pepper to taste"))
        assertEquals(IngredientLine("8 fl oz", "cream"), Ingredients.parseLine("8 fl oz cream"))
        assertEquals(IngredientLine("1", "(14 oz) can tomatoes"), Ingredients.parseLine("1 (14 oz) can tomatoes"))
    }

    @Test fun formatsNumbers() {
        assertEquals("1 1/2", Ingredients.formatNumber(1.5))
        assertEquals("1/3", Ingredients.formatNumber(1.0 / 3))
        assertEquals("2", Ingredients.formatNumber(2.0))
        assertEquals("1.1", Ingredients.formatNumber(1.1))
        assertEquals("3 cups", Ingredients.formatQuantity(Quantity(3.0, "cup")))
        assertEquals("1 cup", Ingredients.formatQuantity(Quantity(1.0, "cup")))
        assertEquals("2 tbsp", Ingredients.formatQuantity(Quantity(2.0, "tbsp")))
        assertEquals("2 boxes", Ingredients.formatQuantity(Quantity(2.0, "box")))
    }

    @Test fun groupsSimilarNames() {
        assertEquals(Ingredients.key("Eggs"), Ingredients.key(" egg "))
        assertEquals(Ingredients.key("tomatoes"), Ingredients.key("Tomato"))
        assertEquals(Ingredients.key("berries"), Ingredients.key("berry"))
        assertEquals("hummus", Ingredients.key("Hummus"))
    }

    @Test fun aggregatesAcrossMeals() {
        val items = Ingredients.aggregate(
            listOf(
                IngredientUse("1 cup", "rice", "Stir fry"),
                IngredientUse("2 cups", "Rice", "Burrito bowls"),
                IngredientUse("2", "eggs", "Pancakes"),
                IngredientUse("3", "Egg", "Fried rice"),
                IngredientUse("a pinch", "salt", "Pancakes"),
                IngredientUse("1 tsp", "Salt", "Stir fry"),
                IngredientUse("", "Cilantro", "Burrito bowls"),
                IngredientUse("1 cup", "rice", "Stir fry"),
            ),
        )
        assertEquals(4, items.size)
        val rice = items[0]
        assertEquals("Rice", rice.name)
        assertEquals("4 cups", rice.amount)
        assertEquals(listOf("Stir fry", "Burrito bowls"), rice.meals)
        assertEquals("5", items[1].amount)
        assertEquals("1 tsp + a pinch", items[2].amount)
        assertEquals("", items[3].amount)
    }
}
