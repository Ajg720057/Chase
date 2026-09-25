package com.chase.mealplan.importer

import com.chase.mealplan.grocery.IngredientLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeParserTest {
    // The shape Allrecipes and many big sites use: a top-level array with the Recipe in it.
    private val arrayPage = """
        <html><head>
        <script type="application/ld+json">{"@type":"Organization","name":"Some Site"}</script>
        <script id="schema" type="application/ld+json">
        [{"@context":"http://schema.org","@type":["Recipe","NewsArticle"],
          "name":"World&#39;s Best Lasagna",
          "image":{"@type":"ImageObject","url":"https://img.example.com/lasagna.jpg"},
          "recipeYield":["12","1 (9x13-inch) lasagna"],
          "totalTime":"PT3H15M",
          "recipeIngredient":["1 pound sweet Italian sausage","3/4 pound lean ground beef",
             "1 (28 ounce) can crushed tomatoes","2 tablespoons white sugar","1 ½ teaspoons dried basil leaves",
             "salt to taste"],
          "recipeInstructions":[{"@type":"HowToStep","text":"Cook sausage, beef &amp; onion until browned."},
             {"@type":"HowToStep","text":"Stir in &quot;crushed&quot; tomatoes.\n"}],
          "nutrition":{"@type":"NutritionInformation","calories":"448 kcal","proteinContent":"29.7 g",
             "carbohydrateContent":"36 g","fatContent":"21.3 g"}}]
        </script></head><body>...</body></html>
    """.trimIndent()

    // The shape WordPress recipe plugins use: an @graph with sections of steps.
    private val graphPage = """
        <script type='application/ld+json' class='yoast-schema-graph'>
        {"@context":"https://schema.org","@graph":[
          {"@type":"WebPage","name":"Blog post"},
          {"@type":"Recipe","name":"Sheet Pan Chicken <b>Fajitas</b>",
           "image":["https://blog.example.com/fajitas-1.jpg","https://blog.example.com/fajitas-2.jpg"],
           "recipeYield":4,
           "recipeIngredient":["1.5 lbs chicken breasts, sliced","2 bell peppers","8 flour tortillas"],
           "recipeInstructions":[
             {"@type":"HowToSection","name":"Prep","itemListElement":[
               {"@type":"HowToStep","text":"Heat oven to 425&deg;F."},
               {"@type":"HowToStep","text":"Slice everything."}]},
             {"@type":"HowToSection","name":"Cook","itemListElement":[
               {"@type":"HowToStep","text":"Roast 20 minutes."}]}]}
        ]}
        </script>
    """.trimIndent()

    @Test fun readsTopLevelArrayRecipe() {
        val r = RecipeParser.parse(arrayPage, "https://www.example.com/lasagna")!!
        assertEquals("World's Best Lasagna", r.name)
        assertEquals(12, r.servings)
        assertEquals("https://img.example.com/lasagna.jpg", r.imageUrl)
        assertEquals(6, r.ingredients.size)
        assertEquals(IngredientLine("1 pound", "sweet Italian sausage"), r.ingredients[0])
        assertEquals(IngredientLine("1 ½ teaspoons", "dried basil leaves"), r.ingredients[4])
        assertEquals(IngredientLine("", "salt to taste"), r.ingredients[5])
        assertTrue(r.instructions.startsWith("1. Cook sausage, beef & onion until browned.\n2. Stir in \"crushed\" tomatoes."))
        assertTrue(r.instructions.contains("Total time: 3 hr 15 min"))
        assertTrue(r.instructions.contains("From: https://www.example.com/lasagna"))
        assertEquals(448.0, r.calories!!, 0.0)
        assertEquals(29.7, r.protein!!, 0.0)
        assertEquals(36.0, r.carbs!!, 0.0)
        assertEquals(21.3, r.fat!!, 0.0)
    }

    @Test fun readsGraphRecipeWithSections() {
        val r = RecipeParser.parse(graphPage)!!
        assertEquals("Sheet Pan Chicken Fajitas", r.name)
        assertEquals(4, r.servings)
        assertEquals("https://blog.example.com/fajitas-1.jpg", r.imageUrl)
        assertEquals(IngredientLine("1.5 lbs", "chicken breasts, sliced"), r.ingredients[0])
        assertEquals("PREP\n1. Heat oven to 425°F.\n2. Slice everything.\n\nCOOK\n3. Roast 20 minutes.", r.instructions)
        assertNull(r.calories)
    }

    @Test fun returnsNullWithoutRecipe() {
        assertNull(RecipeParser.parse("<html><script type=\"application/ld+json\">{\"@type\":\"Article\"}</script></html>"))
        assertNull(RecipeParser.parse("<html>no data</html>"))
    }

    @Test fun readsDurations() {
        assertEquals("45 min", RecipeParser.duration("PT45M"))
        assertEquals("1 hr", RecipeParser.duration("PT1H"))
        assertNull(RecipeParser.duration(""))
    }
}
