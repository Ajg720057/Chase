package com.chase.mealplan.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import com.chase.mealplan.data.Person
import com.chase.mealplan.data.PhotoStore
import com.chase.mealplan.data.entryNote
import com.chase.mealplan.data.PlannedMeal
import com.chase.mealplan.data.Slot
import com.chase.mealplan.data.Week
import com.chase.mealplan.data.nutrition
import com.chase.mealplan.ui.theme.style
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws a printable weekly menu: one page with the week at a glance, then (optionally)
 * a page per meal with its photo, ingredients, supplies and recipe. US Letter, portrait.
 */
class MenuPdf(private val photos: PhotoStore) {
    private val pageW = 612
    private val pageH = 792
    private val margin = 40f

    private val ink = 0xFF1B1F1C.toInt()
    private val muted = 0xFF5F6B62.toInt()
    private val rule = 0xFFD5DBD3.toInt()
    private val green = 0xFF2E7D32.toInt()

    private fun paint(size: Float, bold: Boolean = false, color: Int = ink) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun write(
        week: Week,
        planned: List<PlannedMeal>,
        includeRecipes: Boolean,
        includePhotos: Boolean,
        out: File,
        people: List<Person> = emptyList(),
    ) {
        val doc = PdfDocument()
        try {
            var page: PdfDocument.Page? = null
            var number = 0
            render(
                week, planned, includeRecipes, includePhotos, people,
                object : PageSink {
                    override fun start(width: Int, height: Int): Canvas {
                        number++
                        return doc.startPage(PdfDocument.PageInfo.Builder(width, height, number).create())
                            .also { page = it }.canvas
                    }

                    override fun finish() {
                        page?.let(doc::finishPage)
                        page = null
                    }
                },
            )
            out.parentFile?.mkdirs()
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** Where pages are drawn: a PDF file in the app, plain bitmaps in tests. */
    interface PageSink {
        fun start(width: Int, height: Int): Canvas
        fun finish()
    }

    /** Draws every page into [sink]. Returns how many pages there were. */
    fun render(
        week: Week,
        planned: List<PlannedMeal>,
        includeRecipes: Boolean,
        includePhotos: Boolean,
        people: List<Person>,
        sink: PageSink,
    ): Int {
        val pages = Pages(sink)
        pages.newPage()
        drawMenu(pages.canvas, week, planned, people)
        if (includeRecipes) {
            // Each meal once, in the order it first appears in the week.
            val order = planned.sortedWith(compareBy({ it.entry.date }, { it.entry.slot.ordinal }, { it.entry.sortOrder }))
            order.distinctBy { it.meal.id }.forEach { p ->
                pages.newPage()
                drawRecipe(pages, p, order.filter { it.meal.id == p.meal.id }, includePhotos)
            }
        }
        pages.finish()
        return pages.count
    }

    // ---- Page 1: the week at a glance ----

    private fun drawMenu(c: Canvas, week: Week, planned: List<PlannedMeal>, people: List<Person>) {
        var y = margin
        y = drawText(c, "Weekly Menu", paint(26f, bold = true, color = green), margin, y, pageW - 2 * margin)
        y = drawText(c, week.label + ", " + week.end.year, paint(13f, color = muted), margin, y + 2, pageW - 2 * margin)
        y += 16

        val dayColW = 78f
        val colW = (pageW - 2 * margin - dayColW) / Slot.entries.size
        val headerH = 24f
        val rowH = (pageH - margin - y - headerH) / 7f
        val byDay = planned.groupBy { LocalDate.parse(it.entry.date) }

        // Slot headings on a light tint of each slot's color.
        Slot.entries.forEachIndexed { i, slot ->
            val x = margin + dayColW + i * colW
            val color = slot.style(dark = false).color.toArgb()
            c.drawRect(RectF(x, y, x + colW, y + headerH), Paint().apply { this.color = color; alpha = 45 })
            drawText(c, slot.label, paint(11f, bold = true), x + 6, y + 5, colW - 12)
        }
        val tableTop = y
        y += headerH

        val dayName = DateTimeFormatter.ofPattern("EEEE")
        val dayDate = DateTimeFormatter.ofPattern("MMM d")
        week.days.forEachIndexed { row, day ->
            val top = y + row * rowH
            if (row % 2 == 1) {
                c.drawRect(RectF(margin, top, pageW - margin, top + rowH), Paint().apply { color = 0xFFF4F6F2.toInt() })
            }
            val meals = byDay[day].orEmpty()
            var dy = drawText(c, day.format(dayName), paint(11f, bold = true), margin + 6, top + 6, dayColW - 10)
            dy = drawText(c, day.format(dayDate), paint(9.5f, color = muted), margin + 6, dy + 1, dayColW - 10)
            val calories = meals.mapNotNull { it.meal.nutrition().perServing?.calories }
            if (calories.isNotEmpty()) {
                drawText(c, "≈ ${"%,d".format(calories.sum().roundToInt())} cal", paint(8.5f, color = muted), margin + 6, dy + 3, dayColW - 10)
            }
            Slot.entries.forEachIndexed { i, slot ->
                val x = margin + dayColW + i * colW
                val names = meals.filter { it.entry.slot == slot }.joinToString("\n") { p ->
                    "• " + p.meal.name + (entryNote(p.entry, people)?.let { " ($it)" } ?: "")
                }
                if (names.isNotEmpty()) drawClipped(c, names, paint(10f), x + 6, top + 6, colW - 12, rowH - 10)
            }
        }

        // Grid lines.
        val line = Paint().apply { color = rule; strokeWidth = 0.8f }
        val bottom = y + 7 * rowH
        for (r in 0..7) c.drawLine(margin, y + r * rowH, pageW - margin, y + r * rowH, line)
        c.drawLine(margin, tableTop, pageW - margin, tableTop, line)
        c.drawLine(margin, tableTop, margin, bottom, line)
        for (i in 0..Slot.entries.size) {
            val x = margin + dayColW + i * colW
            c.drawLine(x, tableTop, x, bottom, line)
        }
    }

    // ---- One page (or more) per meal ----

    private fun drawRecipe(pages: Pages, p: PlannedMeal, uses: List<PlannedMeal>, includePhotos: Boolean) {
        val meal = p.meal
        pages.text(meal.name, paint(22f, bold = true, color = green), after = 4f)

        val short = DateTimeFormatter.ofPattern("EEE")
        pages.text(
            "On the menu: " + uses.joinToString(" · ") {
                "${LocalDate.parse(it.entry.date).format(short)} ${it.entry.slot.label.lowercase()}"
            },
            paint(10.5f, color = muted),
        )
        val facts = buildList {
            meal.servings?.let { add("Makes $it servings") }
            val n = meal.nutrition()
            n.perServing?.let {
                add("${it.calories.roundToInt()} cal, ${it.protein.roundToInt()} g protein per serving" + if (n.estimated) " (est.)" else "")
            }
        }
        if (facts.isNotEmpty()) pages.text(facts.joinToString(" · "), paint(10.5f, color = muted))
        pages.y += 10

        if (includePhotos && meal.photo != null) {
            loadPhoto(photos.file(meal.photo))?.let { bmp ->
                val maxW = pageW - 2 * margin
                val maxH = 220f
                val scale = min(maxW / bmp.width, maxH / bmp.height)
                val w = bmp.width * scale
                val h = bmp.height * scale
                pages.ensure(h)
                pages.canvas.drawBitmap(bmp, null, RectF(margin, pages.y, margin + w, pages.y + h), Paint(Paint.FILTER_BITMAP_FLAG))
                pages.y += h + 14
            }
        }

        if (meal.ingredients.isNotEmpty()) {
            heading(pages, "Ingredients")
            val scaledDays = uses.filter { it.entry.servings != null && meal.servings != null && it.entry.servings != meal.servings }
            if (scaledDays.isNotEmpty()) {
                pages.text("Amounts are for the recipe's ${meal.servings} servings.", paint(9.5f, color = muted), after = 3f)
            }
            meal.ingredients.forEach { line ->
                val text = if (line.amount.isBlank()) line.name else "${line.amount}  ${line.name}"
                pages.text("•  $text", paint(11f), indent = 6f, after = 2f)
            }
            pages.y += 8
        }
        if (meal.supplies.isNotEmpty()) {
            heading(pages, "Supplies")
            meal.supplies.forEach { pages.text("•  $it", paint(11f), indent = 6f, after = 2f) }
            pages.y += 8
        }
        if (meal.recipe.isNotBlank()) {
            heading(pages, "Recipe")
            meal.recipe.lines().forEach { para ->
                if (para.isBlank()) pages.y += 6 else pages.text(para, paint(11f), after = 3f)
            }
        }
    }

    private fun heading(pages: Pages, text: String) {
        pages.ensure(40f) // keep a heading with at least a couple of lines after it
        pages.text(text, paint(13f, bold = true, color = green), after = 4f)
    }

    private fun loadPhoto(file: File): Bitmap? = runCatching {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 900) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    // ---- Text helpers ----

    private fun layout(text: String, paint: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()

    /** Draws wrapped text and returns the y just below it. */
    private fun drawText(c: Canvas, text: String, paint: TextPaint, x: Float, y: Float, width: Float): Float {
        val l = layout(text, paint, width)
        c.save()
        c.translate(x, y)
        l.draw(c)
        c.restore()
        return y + l.height
    }

    /** Draws as many whole lines as fit in [maxH], ending with "…" if some were cut. */
    private fun drawClipped(c: Canvas, text: String, paint: TextPaint, x: Float, y: Float, width: Float, maxH: Float) {
        val l = layout(text, paint, width)
        var last = l.lineCount - 1
        while (last >= 0 && l.getLineBottom(last) > maxH) last--
        if (last < 0) return
        c.save()
        c.translate(x, y)
        c.clipRect(0f, 0f, width, l.getLineBottom(last).toFloat())
        l.draw(c)
        c.restore()
        if (last < l.lineCount - 1) {
            drawText(c, "…", paint, x + width - 10, y + l.getLineTop(last), 12f)
        }
    }

    /** Flows text down pages, starting a new page when one fills up. */
    private inner class Pages(private val sink: PageSink) {
        private var current: Canvas? = null
        var count = 0
            private set
        var y = margin
        val canvas: Canvas get() = current!!

        fun newPage() {
            if (current != null) sink.finish()
            current = sink.start(pageW, pageH)
            count++
            y = margin
        }

        fun finish() {
            if (current != null) sink.finish()
            current = null
        }

        fun ensure(space: Float) {
            if (y + space > pageH - margin) newPage()
        }

        fun text(s: String, paint: TextPaint, indent: Float = 0f, after: Float = 0f) {
            val width = pageW - 2 * margin - indent
            val l = layout(s, paint, width)
            for (i in 0 until l.lineCount) {
                val top = l.getLineTop(i).toFloat()
                val bottom = l.getLineBottom(i).toFloat()
                ensure(bottom - top)
                canvas.save()
                canvas.translate(margin + indent, y - top)
                canvas.clipRect(0f, top, width, bottom)
                l.draw(canvas)
                canvas.restore()
                y += bottom - top
            }
            y += after
        }
    }
}
