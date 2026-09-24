package com.chase.mealplan.grocery

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** One ingredient line on a recipe: "2 cups" + "flour". */
data class IngredientLine(val amount: String = "", val name: String = "")

/** An ingredient needed by a planned meal, as fed into the grocery list builder. */
data class IngredientUse(val amount: String, val name: String, val meal: String)

/** One row of the finished grocery list. */
data class AggregatedItem(val key: String, val name: String, val amount: String, val meals: List<String>)

/** A parsed amount such as 1.5 cups. [unit] is normalized ("tbsp", "cup", "" for a plain count). */
data class Quantity(val value: Double, val unit: String)

object Ingredients {
    private val unicodeFractions = mapOf(
        '½' to 0.5, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '¼' to 0.25, '¾' to 0.75,
        '⅕' to 0.2, '⅖' to 0.4, '⅗' to 0.6, '⅘' to 0.8, '⅙' to 1.0 / 6, '⅚' to 5.0 / 6,
        '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875,
    )

    /** Spoken/written unit -> normalized unit. Word units get an "s" when there's more than one. */
    private val units = buildMap {
        fun add(norm: String, vararg forms: String) { put(norm, norm); forms.forEach { put(it, norm) } }
        add("tsp", "t", "teaspoon", "teaspoons", "tsps")
        add("tbsp", "T", "tbs", "tbl", "tablespoon", "tablespoons", "tbsps")
        add("cup", "c", "cups")
        add("oz", "ounce", "ounces")
        add("fl oz")
        add("lb", "lbs", "pound", "pounds")
        add("g", "gram", "grams", "gr")
        add("kg", "kilogram", "kilograms", "kgs")
        add("ml", "milliliter", "milliliters", "millilitre", "millilitres", "mls")
        add("l", "liter", "liters", "litre", "litres")
        add("pint", "pints", "pt")
        add("quart", "quarts", "qt")
        add("gallon", "gallons", "gal")
        add("can", "cans")
        add("jar", "jars")
        add("bottle", "bottles")
        add("box", "boxes")
        add("bag", "bags")
        add("package", "packages", "pkg", "pkgs", "pack", "packs", "packet", "packets")
        add("clove", "cloves")
        add("slice", "slices")
        add("stick", "sticks")
        add("head", "heads")
        add("bunch", "bunches")
        add("sprig", "sprigs")
        add("stalk", "stalks")
        add("pinch", "pinches")
        add("dash", "dashes")
        add("dozen", "dozens")
        add("piece", "pieces", "pc", "pcs")
        add("fillet", "fillets")
        add("loaf", "loaves")
    }
    private val abbreviations = setOf("tsp", "tbsp", "oz", "fl oz", "lb", "g", "kg", "ml", "l", "pt", "qt")
    private val pluralOverrides = mapOf("box" to "boxes", "bunch" to "bunches", "pinch" to "pinches", "dash" to "dashes", "loaf" to "loaves", "dozen" to "dozen")

    private fun normalizeUnit(word: String): String? {
        val w = word.trim().trimEnd('.')
        // "T" is tablespoon and "t" is teaspoon, so only these two are case-sensitive.
        if (w == "T" || w == "t") return units[w]
        return units[w.lowercase()]
    }

    /** Reads a leading number: "2", "1.5", "1/2", "1 1/2", "1½", "½". Returns value and chars consumed. */
    private fun readNumber(s: String): Pair<Double, Int>? {
        var i = 0
        while (i < s.length && s[i] == ' ') i++
        val start = i
        var whole: Double? = null
        val numStart = i
        while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
        if (i > numStart) {
            val text = s.substring(numStart, i)
            whole = text.toDoubleOrNull() ?: return null
            // "1/2" style fraction right after the digits.
            if (i < s.length && s[i] == '/') {
                var j = i + 1
                val denStart = j
                while (j < s.length && s[j].isDigit()) j++
                val den = s.substring(denStart, j).toDoubleOrNull()
                if (den != null && den != 0.0) return whole / den to j
                return null
            }
        }
        // Mixed number: "1 1/2" or "1 ½" or "1½".
        var k = i
        while (k < s.length && s[k] == ' ') k++
        if (k < s.length && unicodeFractions.containsKey(s[k])) {
            return (whole ?: 0.0) + unicodeFractions.getValue(s[k]) to k + 1
        }
        if (whole != null && k > i) {
            val m = Regex("^(\\d+)/(\\d+)").find(s.substring(k))
            if (m != null) {
                val den = m.groupValues[2].toDouble()
                if (den != 0.0) return whole + m.groupValues[1].toDouble() / den to k + m.value.length
            }
        }
        return if (whole != null && i > start) whole to i else null
    }

    /** Parses an amount like "2 cups" or "1 1/2". Returns null for things like "a pinch" or "2-3". */
    fun parseQuantity(amount: String): Quantity? {
        val s = amount.trim()
        if (s.isEmpty()) return null
        val (value, used) = readNumber(s) ?: return null
        val rest = s.substring(used).trim()
        if (rest.isEmpty()) return Quantity(value, "")
        val unit = normalizeUnit(rest) ?: return null
        return Quantity(value, unit)
    }

    /** Splits a pasted recipe line like "• 2 cups of flour, sifted" into amount and name. */
    fun parseLine(raw: String): IngredientLine {
        val s = raw.trim().trimStart('-', '*', '•', '▢', '☐', '□', '·', '–', ' ', '\t').trim()
        if (s.isEmpty()) return IngredientLine()
        val num = readNumber(s)
        if (num == null) return IngredientLine("", s)
        var amountEnd = num.second
        // Allow ranges such as "2-3" or "2 to 3" to stay in the amount.
        Regex("^\\s*(-|–|to)\\s*\\d+").find(s.substring(amountEnd))?.let { amountEnd += it.value.length }
        var rest = s.substring(amountEnd)
        // Optional unit word, with "fl oz" as the only two-word unit.
        val words = rest.trimStart().split(Regex("\\s+"), limit = 3)
        val twoWord = if (words.size >= 2) "${words[0]} ${words[1]}" else null
        val unitWords = when {
            twoWord != null && normalizeUnit(twoWord) != null -> 2
            words.isNotEmpty() && words[0].isNotEmpty() && normalizeUnit(words[0]) != null && words.size > 1 -> 1
            else -> 0
        }
        if (unitWords > 0) {
            val lead = rest.length - rest.trimStart().length
            var consumed = lead
            var remaining = rest.trimStart()
            repeat(unitWords) {
                val w = remaining.substringBefore(' ')
                consumed += w.length
                remaining = remaining.substring(w.length)
                val spaces = remaining.length - remaining.trimStart().length
                consumed += spaces
                remaining = remaining.trimStart()
            }
            amountEnd += consumed
            rest = s.substring(amountEnd)
        }
        val amount = s.substring(0, amountEnd).trim()
        var name = rest.trim()
        if (name.startsWith("of ", ignoreCase = true)) name = name.substring(3).trim()
        return IngredientLine(amount, name)
    }

    /** Groups "Eggs", "egg" and " eggs " together. */
    fun key(name: String): String {
        var k = name.trim().lowercase().replace(Regex("\\s+"), " ")
        k = when {
            k.endsWith("ies") && k.length > 4 -> k.dropLast(3) + "y"
            k.endsWith("oes") && k.length > 4 -> k.dropLast(2)
            k.endsWith("s") && !k.endsWith("ss") && !k.endsWith("us") && k.length > 3 -> k.dropLast(1)
            else -> k
        }
        return k
    }

    fun formatNumber(v: Double): String {
        val whole = floor(v + 1e-9).toInt()
        val frac = v - whole
        if (abs(frac) < 0.02) return whole.toString()
        val fractions = listOf(
            0.125 to "1/8", 0.25 to "1/4", 1.0 / 3 to "1/3", 0.375 to "3/8", 0.5 to "1/2",
            0.625 to "5/8", 2.0 / 3 to "2/3", 0.75 to "3/4", 0.875 to "7/8",
        )
        val match = fractions.firstOrNull { abs(it.first - frac) < 0.02 }
        if (match != null) return if (whole == 0) match.second else "$whole ${match.second}"
        if (frac > 0.98) return (whole + 1).toString()
        val rounded = (v * 100).roundToInt() / 100.0
        return rounded.toString().trimEnd('0').trimEnd('.')
    }

    fun formatQuantity(q: Quantity): String {
        val n = formatNumber(q.value)
        if (q.unit.isEmpty()) return n
        val unit = if (q.value > 1.0 + 1e-9 && q.unit !in abbreviations) {
            pluralOverrides[q.unit] ?: (q.unit + "s")
        } else q.unit
        return "$n $unit"
    }

    /**
     * Merges ingredient uses into one line per item. Numeric amounts with the same unit are
     * added up ("1 cup" + "2 cups" = "3 cups"); anything else is listed alongside ("3 cups + a pinch").
     */
    fun aggregate(uses: List<IngredientUse>): List<AggregatedItem> {
        val groups = LinkedHashMap<String, MutableList<IngredientUse>>()
        for (u in uses) {
            if (u.name.isBlank()) continue
            groups.getOrPut(key(u.name)) { mutableListOf() } += u
        }
        return groups.map { (key, list) ->
            val sums = LinkedHashMap<String, Double>()
            val texts = LinkedHashSet<String>()
            for (u in list) {
                val a = u.amount.trim()
                if (a.isEmpty()) continue
                val q = parseQuantity(a)
                if (q != null) sums[q.unit] = (sums[q.unit] ?: 0.0) + q.value else texts += a
            }
            val parts = sums.map { (unit, v) -> formatQuantity(Quantity(v, unit)) } + texts
            AggregatedItem(
                key = key,
                name = list.first().name.trim().replaceFirstChar { it.uppercase() },
                amount = parts.joinToString(" + "),
                meals = list.map { it.meal }.distinct(),
            )
        }
    }
}
