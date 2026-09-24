package com.chase.mealplan.grocery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionsAndNutritionTest {
    private fun section(name: String) = StoreSections.classify(name)

    @Test fun classifiesCommonItems() {
        assertEquals(StoreSection.PRODUCE, section("Yellow onions"))
        assertEquals(StoreSection.PRODUCE, section("Red bell pepper, diced"))
        assertEquals(StoreSection.PRODUCE, section("Butter lettuce"))
        assertEquals(StoreSection.PRODUCE, section("Green beans"))
        assertEquals(StoreSection.MEAT, section("Boneless skinless chicken breasts"))
        assertEquals(StoreSection.MEAT, section("Ground beef (80/20)"))
        assertEquals(StoreSection.DAIRY, section("Shredded cheddar cheese"))
        assertEquals(StoreSection.DAIRY, section("Eggs"))
        assertEquals(StoreSection.DAIRY, section("Sour cream"))
        assertEquals(StoreSection.PANTRY, section("Peanut butter"))
        assertEquals(StoreSection.PANTRY, section("Spaghetti"))
        assertEquals(StoreSection.PANTRY, section("Red wine vinegar"))
        assertEquals(StoreSection.CANNED, section("Chicken broth"))
        assertEquals(StoreSection.CANNED, section("Black beans"))
        assertEquals(StoreSection.CANNED, section("Coconut milk"))
        assertEquals(StoreSection.CANNED, section("Diced tomatoes"))
        assertEquals(StoreSection.CANNED, section("(14 oz) can tomatoes"))
        assertEquals(StoreSection.SPICES, section("Garlic powder"))
        assertEquals(StoreSection.SPICES, section("Black pepper"))
        assertEquals(StoreSection.SPICES, section("Red pepper flakes"))
        assertEquals(StoreSection.SPICES, section("All-purpose flour"))
        assertEquals(StoreSection.BAKERY, section("Hamburger buns"))
        assertEquals(StoreSection.BAKERY, section("Flour tortillas"))
        assertEquals(StoreSection.FROZEN, section("Frozen peas"))
        assertEquals(StoreSection.FROZEN, section("Vanilla ice cream"))
        assertEquals(StoreSection.SNACKS, section("Tortilla chips"))
        assertEquals(StoreSection.DRINKS, section("Sparkling water"))
        assertEquals(StoreSection.HOUSEHOLD, section("Aluminum foil"))
        assertEquals(StoreSection.HOUSEHOLD, section("Paper towels"))
        assertEquals(StoreSection.OTHER, section("Zzyzx"))
        assertEquals(StoreSection.HOUSEHOLD, StoreSections.classify("Zzyzx", isSupply = true))
    }

    @Test fun scalesAmounts() {
        assertEquals("4 cups", Ingredients.scaleAmount("2 cups", 2.0))
        assertEquals("3/4 tsp", Ingredients.scaleAmount("1 1/2 tsp", 0.5))
        assertEquals("4 large", Ingredients.scaleAmount("2 large", 2.0))
        assertEquals("a pinch (×2)", Ingredients.scaleAmount("a pinch", 2.0))
        assertEquals("2-3 cloves (×1.5)", Ingredients.scaleAmount("2-3 cloves", 1.5))
        assertEquals("2 cups", Ingredients.scaleAmount("2 cups", 1.0))
        assertEquals("", Ingredients.scaleAmount("", 3.0))
    }

    @Test fun sizesIngredients() {
        val rice = Nutrition.find("white rice")!!
        assertEquals(185.0, Nutrition.grams("1 cup", "white rice", rice)!!, 0.01)
        val egg = Nutrition.find("eggs")!!
        assertEquals(150.0, Nutrition.grams("3", "eggs", egg)!!, 0.01)
        assertEquals(100.0, Nutrition.grams("2 large", "eggs", egg)!!, 0.01)
        val garlic = Nutrition.find("garlic")!!
        assertEquals(9.0, Nutrition.grams("3 cloves", "garlic", garlic)!!, 0.01)
        val beef = Nutrition.find("ground beef")!!
        assertEquals(453.6, Nutrition.grams("1 lb", "ground beef", beef)!!, 0.01)
        val tomatoes = Nutrition.find("diced tomatoes")!!
        assertEquals(396.9, Nutrition.grams("1", "(14 oz) can diced tomatoes", tomatoes)!!, 0.01)
        val butter = Nutrition.find("butter")!!
        assertEquals(14.1875, Nutrition.grams("1 tbsp", "butter", butter)!!, 0.01)
        assertNull(Nutrition.grams("", "butter", butter))
    }

    @Test fun matchesTheRightFood() {
        assertEquals(Nutrition.find("peanut butter"), Nutrition.find("creamy peanut butter"))
        assertTrue(Nutrition.find("garlic powder")!!.negligible)
        assertTrue(Nutrition.find("salt and pepper to taste")!!.negligible)
        assertEquals(588.0, Nutrition.find("Peanut Butter")!!.per100g.calories, 0.0)
        assertEquals(6.0, Nutrition.find("low sodium chicken broth")!!.per100g.calories, 0.0)
        assertNotNull(Nutrition.find("boneless skinless chicken thighs"))
        assertNull(Nutrition.find("dragon fruit powder of mystery"))
    }

    @Test fun estimatesARecipe() {
        val e = Nutrition.estimate(
            listOf(
                IngredientLine("1 lb", "chicken breast"),
                IngredientLine("1 cup", "white rice"),
                IngredientLine("1 tbsp", "olive oil"),
                IngredientLine("1 tsp", "salt"),
                IngredientLine("2", "mystery sauce thing"),
                IngredientLine("", "cilantro"),
            ),
        )
        // 453.6 g chicken = 544 cal, 185 g rice = 675 cal, 13.5 g oil = 119 cal.
        assertEquals(1339.0, e.total.calories, 3.0)
        assertEquals(115.0, e.total.protein, 2.0)
        assertEquals(5, e.counted)
        assertEquals(listOf("mystery sauce thing"), e.missing)
    }
}
