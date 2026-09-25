package com.chase.mealplan.importer

import com.chase.mealplan.grocery.IngredientLine
import com.chase.mealplan.grocery.Ingredients
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** What could be read from a recipe web page. Anything missing is null or empty. */
data class ImportedRecipe(
    val name: String?,
    val servings: Int?,
    val ingredients: List<IngredientLine>,
    val instructions: String,
    val imageUrl: String?,
    val calories: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
)

/**
 * Reads the schema.org Recipe data most recipe sites embed in their pages
 * (a <script type="application/ld+json"> block), which search engines use too.
 */
object RecipeParser {
    private val scriptRegex = Regex(
        "<script[^>]*type\\s*=\\s*[\"']?application/ld\\+json[\"']?[^>]*>(.*?)</script>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** Null when the page has no recipe data. */
    fun parse(html: String, pageUrl: String? = null): ImportedRecipe? {
        for (m in scriptRegex.findAll(html)) {
            val raw = m.groupValues[1].trim()
                .removePrefix("<!--").removeSuffix("-->")
                .removePrefix("//<![CDATA[").removeSuffix("//]]>").trim()
            val json = runCatching { JSONTokener(raw).nextValue() }.getOrNull() ?: continue
            val recipe = findRecipe(json) ?: continue
            return read(recipe, pageUrl)
        }
        return null
    }

    private fun isRecipe(o: JSONObject): Boolean = when (val t = o.opt("@type")) {
        is String -> t.equals("Recipe", ignoreCase = true)
        is JSONArray -> (0 until t.length()).any { t.optString(it).equals("Recipe", ignoreCase = true) }
        else -> false
    }

    /** The Recipe object, which may sit inside an array or an "@graph". */
    private fun findRecipe(node: Any?, depth: Int = 0): JSONObject? {
        if (depth > 6) return null
        return when (node) {
            is JSONObject -> {
                if (isRecipe(node)) return node
                for (key in listOf("@graph", "mainEntity", "mainEntityOfPage", "itemListElement")) {
                    findRecipe(node.opt(key), depth + 1)?.let { return it }
                }
                null
            }
            is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { findRecipe(node.opt(it), depth + 1) }
            else -> null
        }
    }

    private fun read(r: JSONObject, pageUrl: String?): ImportedRecipe {
        val nutrition = r.optJSONObject("nutrition")
        val steps = instructions(r.opt("recipeInstructions"))
        val extras = buildList {
            duration(r.optString("totalTime"))?.let { add("Total time: $it") }
            if (!pageUrl.isNullOrBlank()) add("From: $pageUrl")
        }
        val text = listOf(steps, extras.joinToString("\n")).filter { it.isNotBlank() }.joinToString("\n\n")
        return ImportedRecipe(
            name = r.optString("name").let(::clean).ifBlank { null },
            servings = servings(r.opt("recipeYield")),
            ingredients = strings(r.opt("recipeIngredient") ?: r.opt("ingredients"))
                .map { Ingredients.parseLine(clean(it)) }
                .filter { it.name.isNotBlank() },
            instructions = text,
            imageUrl = image(r.opt("image")),
            calories = number(nutrition?.opt("calories")),
            protein = number(nutrition?.opt("proteinContent")),
            carbs = number(nutrition?.opt("carbohydrateContent")),
            fat = number(nutrition?.opt("fatContent")),
        )
    }

    private fun strings(node: Any?): List<String> = when (node) {
        is String -> listOf(node)
        is JSONArray -> (0 until node.length()).flatMap { strings(node.opt(it)) }
        is JSONObject -> listOfNotNull(node.optString("text").ifBlank { node.optString("name") }.ifBlank { null })
        else -> emptyList()
    }

    /** Numbered steps. Sections ("For the sauce") become headings. */
    private fun instructions(node: Any?): String {
        val out = mutableListOf<String>()
        var n = 0
        fun walk(x: Any?) {
            when (x) {
                is String -> clean(x).split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
                    .forEach { out += "${++n}. $it" }
                is JSONArray -> (0 until x.length()).forEach { walk(x.opt(it)) }
                is JSONObject -> {
                    val type = x.optString("@type")
                    if (type.equals("HowToSection", ignoreCase = true) || x.has("itemListElement")) {
                        clean(x.optString("name")).takeIf { it.isNotBlank() }?.let {
                            if (out.isNotEmpty()) out += ""
                            out += it.uppercase()
                        }
                        walk(x.opt("itemListElement"))
                    } else {
                        val t = clean(x.optString("text").ifBlank { x.optString("name") })
                        if (t.isNotBlank()) out += "${++n}. $t"
                    }
                }
            }
        }
        walk(node)
        return out.joinToString("\n")
    }

    private fun servings(node: Any?): Int? {
        val text = when (node) {
            is Number -> node.toString()
            is JSONArray -> (0 until node.length()).map { node.opt(it).toString() }.firstOrNull { Regex("\\d").containsMatchIn(it) }
            else -> node?.toString()
        } ?: return null
        return Regex("\\d+").find(text)?.value?.toIntOrNull()?.takeIf { it in 1..100 }
    }

    private fun image(node: Any?): String? = when (node) {
        is String -> node.ifBlank { null }
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { image(node.opt(it)) }
        is JSONObject -> node.optString("url").ifBlank { node.optString("contentUrl") }.ifBlank { null }
        else -> null
    }

    private fun number(node: Any?): Double? = when (node) {
        is Number -> node.toDouble()
        is String -> Regex("\\d+(\\.\\d+)?").find(node.replace(",", ""))?.value?.toDoubleOrNull()
        else -> null
    }

    /** "PT1H15M" -> "1 hr 15 min" */
    fun duration(iso: String): String? {
        val m = Regex("P(?:\\d+D)?T?(?:(\\d+)H)?(?:(\\d+)M)?", RegexOption.IGNORE_CASE).find(iso.trim()) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: 0
        val min = m.groupValues[2].toIntOrNull() ?: 0
        if (h == 0 && min == 0) return null
        return listOfNotNull(if (h > 0) "$h hr" else null, if (min > 0) "$min min" else null).joinToString(" ")
    }

    private val entities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "frac12" to "½", "frac14" to "¼", "frac34" to "¾", "deg" to "°", "rsquo" to "'", "lsquo" to "'",
        "rdquo" to "\"", "ldquo" to "\"", "ndash" to "–", "mdash" to "—", "hellip" to "…", "eacute" to "é",
    )

    /** Strips tags and decodes HTML entities like &amp; and &#39;. */
    fun clean(s: String): String {
        var t = s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
        // Twice, for sites that double-encode ("&amp;#39;").
        repeat(2) {
            t = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);").replace(t) { m ->
                val e = m.groupValues[1]
                when {
                    e.startsWith("#x") || e.startsWith("#X") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                    e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                    else -> entities[e.lowercase()]
                } ?: m.value
            }
        }
        return t.lines().joinToString("\n") { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }.trim()
    }
}
