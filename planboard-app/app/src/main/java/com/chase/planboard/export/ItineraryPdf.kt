package com.chase.planboard.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.chase.planboard.data.PlanStatus
import com.chase.planboard.data.Scope
import com.chase.planboard.data.TodoEntity
import com.chase.planboard.data.durationMinutes
import com.chase.planboard.data.endsNextDay
import com.chase.planboard.ui.common.Format
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Draws an [Itinerary] as a printable, US Letter PDF with page numbers. */
object ItineraryPdf {
    private const val PAGE_W = 612 // 8.5in at 72pt/in
    private const val PAGE_H = 792 // 11in
    private const val MARGIN = 48f
    private const val TIME_COL = 70f

    private val dayHeading = DateTimeFormatter.ofPattern("EEEE, MMMM d")
    private val longDate = DateTimeFormatter.ofPattern("MMMM d, yyyy")
    private val shortDate = DateTimeFormatter.ofPattern("MMM d")

    private val INK = Color.rgb(33, 33, 33)
    private val MUTED = Color.rgb(110, 110, 110)
    private val RULE = Color.rgb(210, 210, 210)
    private val ACCENT = Color.rgb(0, 121, 107)

    fun rangeLabel(start: LocalDate, end: LocalDate): String = when {
        start == end -> start.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
        start.year == end.year -> "${start.format(shortDate)} – ${end.format(longDate)}"
        else -> "${start.format(longDate)} – ${end.format(longDate)}"
    }

    fun write(itinerary: Itinerary, out: OutputStream) {
        val doc = PdfDocument()
        try {
            val w = PageWriter(doc, footer = "PlanBoard itinerary · ${rangeLabel(itinerary.start, itinerary.end)}")

            w.text("Itinerary", paint(22f, bold = true))
            w.text(rangeLabel(itinerary.start, itinerary.end), paint(13f, color = ACCENT), after = 2f)
            w.text("Created ${LocalDate.now().format(longDate)}", paint(9f, color = MUTED), after = 8f)
            w.rule()

            if (itinerary.isEmpty) {
                w.text("Nothing is planned for these dates.", paint(11f, color = MUTED), after = 8f)
            }
            if (itinerary.months.isNotEmpty()) {
                w.heading("Month goals")
                itinerary.months.forEach { w.plan(it, Format.period(it.plan)) }
            }
            if (itinerary.weeks.isNotEmpty()) {
                w.heading("Week plans")
                itinerary.weeks.forEach { w.plan(it, Format.period(it.plan)) }
            }
            if (itinerary.days.isNotEmpty()) {
                w.heading("Day by day")
                itinerary.days.forEach { day ->
                    w.dayHeader(day.date.format(dayHeading))
                    if (day.plans.isEmpty()) {
                        w.text("Nothing planned", paint(10f, color = MUTED, italic = true), indent = TIME_COL, after = 6f)
                    }
                    day.plans.forEach { p ->
                        val start = p.plan.startMinute
                        val end = p.plan.endMinute
                        w.plan(
                            p,
                            subtitle = p.plan.durationMinutes?.let { d ->
                                Format.duration(d) + if (p.plan.endsNextDay) ", ends next day" else ""
                            },
                            timeLabel = when {
                                start == null -> "Any time"
                                end == null -> Format.minuteOfDay(start)
                                else -> "${Format.minuteOfDay(start)}\n– ${Format.minuteOfDay(end)}"
                            },
                        )
                    }
                }
            }
            w.finish()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun paint(size: Float, bold: Boolean = false, italic: Boolean = false, color: Int = INK) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(
                Typeface.DEFAULT,
                when {
                    bold && italic -> Typeface.BOLD_ITALIC
                    bold -> Typeface.BOLD
                    italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                },
            )
        }

    private fun scopeColor(scope: Scope) = when (scope) {
        Scope.MONTH -> Color.rgb(0x7E, 0x57, 0xC2)
        Scope.WEEK -> Color.rgb(0x1E, 0x88, 0xE5)
        Scope.DAY -> Color.rgb(0x00, 0x89, 0x7B)
    }

    /** Keeps a y cursor, starts new pages as needed, and stamps a footer on each page. */
    private class PageWriter(private val doc: PdfDocument, private val footer: String) {
        private val left = MARGIN
        private val width = PAGE_W - 2 * MARGIN
        private val bottom = PAGE_H - MARGIN - 18f
        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page
        private val canvas: Canvas get() = page.canvas
        private var y = 0f

        init {
            newPage()
        }

        private fun newPage() {
            if (pageNumber > 0) finishPage()
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            y = MARGIN
        }

        private fun finishPage() {
            val p = paint(8f, color = MUTED)
            canvas.drawText(footer, left, PAGE_H - MARGIN + 4f, p)
            val num = "Page $pageNumber"
            canvas.drawText(num, left + width - p.measureText(num), PAGE_H - MARGIN + 4f, p)
            doc.finishPage(page)
        }

        fun finish() = finishPage()

        /** Starts a new page unless [height] more points fit on this one. */
        fun ensure(height: Float) {
            if (y + height > bottom && y > MARGIN) newPage()
        }

        private fun layout(text: String, paint: TextPaint, w: Float): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, w.toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .build()

        private fun draw(l: StaticLayout, x: Float, top: Float) {
            canvas.save()
            canvas.translate(x, top)
            l.draw(canvas)
            canvas.restore()
        }

        fun text(s: String, paint: TextPaint, indent: Float = 0f, after: Float = 0f) {
            val l = layout(s, paint, width - indent)
            ensure(l.height.toFloat())
            draw(l, left + indent, y)
            y += l.height + after
        }

        fun rule() {
            ensure(10f)
            y += 4f
            canvas.drawLine(left, y, left + width, y, Paint().apply { color = RULE; strokeWidth = 0.8f })
            y += 8f
        }

        fun heading(s: String) {
            ensure(70f) // keep a heading with at least the start of what follows
            y += 10f
            text(s.uppercase(), paint(11f, bold = true, color = ACCENT), after = 2f)
            rule()
        }

        fun dayHeader(s: String) {
            ensure(56f)
            y += 6f
            text(s, paint(13f, bold = true), after = 4f)
        }

        /** One plan: time column (for days), colored marker, title, status line, details, to-dos and notes. */
        fun plan(item: ItineraryPlan, subtitle: String? = null, timeLabel: String? = null) {
            val p = item.plan
            val indent = if (timeLabel != null) TIME_COL else 0f
            val bodyX = left + indent + 12f
            val bodyW = width - indent - 12f

            val titlePaint = paint(12f, bold = true, color = if (p.status == PlanStatus.DONE) MUTED else INK)
            val title = layout(p.title.ifBlank { "Untitled plan" }, titlePaint, bodyW)
            ensure(title.height + 30f)

            val blockTop = y
            val timeLayout = timeLabel?.let { layout(it, paint(10f, bold = true, color = MUTED), TIME_COL - 8f) }
            timeLayout?.let { draw(it, left, y + 1f) }
            canvas.drawRoundRect(
                left + indent, y + 3f, left + indent + 5f, y + 3f + title.height - 4f, 2f, 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scopeColor(p.scope) },
            )
            draw(title, bodyX, y)
            y += title.height

            val meta = listOfNotNull(
                subtitle,
                p.status.takeIf { it != PlanStatus.PLANNED }?.label,
                item.parentTitle?.let { "Part of $it" },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) block(meta, paint(9f, color = MUTED), bodyX, bodyW)
            if (p.details.isNotBlank()) {
                y += 2f
                block(p.details.trim(), paint(10f), bodyX, bodyW)
            }
            if (item.todos.isNotEmpty()) {
                y += 3f
                item.todos.forEach { todo(it, bodyX, bodyW) }
            }
            if (item.notes.isNotEmpty()) {
                y += 3f
                item.notes.forEach { n ->
                    block("${Format.toDate(n.createdAt).format(shortDate)}: ${n.text}", paint(9f, italic = true, color = MUTED), bodyX, bodyW)
                }
            }
            // Don't let a two-line time ("9:00 AM – 10:30 AM") run into the next plan.
            if (timeLayout != null && y < blockTop + timeLayout.height + 1f && y > blockTop) {
                y = blockTop + timeLayout.height + 1f
            }
            y += 10f
        }

        private fun block(s: String, paint: TextPaint, x: Float, w: Float) {
            val l = layout(s, paint, w)
            ensure(l.height.toFloat())
            draw(l, x, y)
            y += l.height + 1f
        }

        private fun todo(t: TodoEntity, x: Float, w: Float) {
            val label = buildString {
                append(t.title.ifBlank { "Untitled to-do" })
                t.dueAt?.let { append("  —  ").append(Format.due(it)) }
            }
            val l = layout(label, paint(10f, color = if (t.done) MUTED else INK), w - 16f)
            ensure(l.height.toFloat() + 2f)
            val box = 8f
            val top = y + 3f
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.9f
                color = MUTED
            }
            canvas.drawRect(x, top, x + box, top + box, stroke)
            if (t.done) {
                val check = Path().apply {
                    moveTo(x + 1.5f, top + 4.2f)
                    lineTo(x + 3.5f, top + 6.5f)
                    lineTo(x + 7f, top + 1.5f)
                }
                canvas.drawPath(check, stroke.apply { color = ACCENT; strokeWidth = 1.4f })
            }
            draw(l, x + 16f, y)
            y += l.height + 2f
        }
    }
}
