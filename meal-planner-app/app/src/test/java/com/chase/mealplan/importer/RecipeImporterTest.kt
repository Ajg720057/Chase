package com.chase.mealplan.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeImporterTest {
    @Test fun findsTheAddressInSharedText() {
        // What Chrome and other apps put in a share.
        assertEquals(
            "https://www.allrecipes.com/recipe/23600/worlds-best-lasagna/",
            RecipeImporter.normalize("https://www.allrecipes.com/recipe/23600/worlds-best-lasagna/"),
        )
        assertEquals(
            "https://cooking.example.com/r/123?utm_source=share",
            RecipeImporter.normalize("Check out this recipe! https://cooking.example.com/r/123?utm_source=share."),
        )
        assertEquals("https://example.com/soup", RecipeImporter.normalize("  example.com/soup \n"))
        assertNull(RecipeImporter.normalize("chicken soup"))
        assertNull(RecipeImporter.normalize(""))
    }
}
