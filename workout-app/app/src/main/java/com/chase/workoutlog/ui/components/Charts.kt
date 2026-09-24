package com.chase.workoutlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.chase.workoutlog.analytics.DataPoint
import java.time.format.DateTimeFormatter

private val shortDate = DateTimeFormatter.ofPattern("MMM d")

/** A simple time-scaled line chart with min/max labels on the left and first/last dates underneath. */
@Composable
fun LineChart(
    points: List<DataPoint>,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.merge(TextStyle(color = labelColor))

    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        if (points.isEmpty()) return@Canvas
        val minV = points.minOf { it.value }
        val maxV = points.maxOf { it.value }
        val span = (maxV - minV).takeIf { it > 0 } ?: (maxV.takeIf { it > 0 } ?: 1.0) * 0.2
        val lo = minV - span * 0.15
        val hi = maxV + span * 0.15

        val maxLabel = measurer.measure(formatValue(maxV), labelStyle)
        val minLabel = measurer.measure(formatValue(minV), labelStyle)
        val leftPad = maxOf(maxLabel.size.width, minLabel.size.width) + 12f
        val bottomPad = maxLabel.size.height + 10f
        val chartW = size.width - leftPad - 8f
        val chartH = size.height - bottomPad - 8f

        val firstDay = points.first().date.toEpochDay()
        val lastDay = points.last().date.toEpochDay()
        val daySpan = (lastDay - firstDay).toFloat()

        fun x(i: Int): Float {
            if (points.size == 1) return leftPad + chartW / 2
            val frac = if (daySpan > 0) (points[i].date.toEpochDay() - firstDay) / daySpan
            else i.toFloat() / (points.size - 1)
            return leftPad + frac * chartW
        }
        fun y(v: Double): Float = (8f + chartH * (1 - ((v - lo) / (hi - lo)))).toFloat()

        // Grid lines at the max and min values, plus the midpoint.
        for (v in listOf(maxV, (maxV + minV) / 2, minV)) {
            drawLine(gridColor, Offset(leftPad, y(v)), Offset(leftPad + chartW, y(v)), strokeWidth = 1f)
        }
        drawText(maxLabel, topLeft = Offset(0f, y(maxV) - maxLabel.size.height / 2))
        if (maxV != minV) drawText(minLabel, topLeft = Offset(0f, y(minV) - minLabel.size.height / 2))

        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(x(i), y(p.value)) else path.lineTo(x(i), y(p.value))
        }
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        points.forEachIndexed { i, p ->
            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x(i), y(p.value)))
        }

        val firstLabel = measurer.measure(points.first().date.format(shortDate), labelStyle)
        drawText(firstLabel, topLeft = Offset(leftPad, size.height - firstLabel.size.height))
        if (points.size > 1) {
            val lastLabel = measurer.measure(points.last().date.format(shortDate), labelStyle)
            drawText(lastLabel, topLeft = Offset(leftPad + chartW - lastLabel.size.width, size.height - lastLabel.size.height))
        }
    }
}

/** Vertical bars with the count on top and a label underneath. */
@Composable
fun BarChart(bars: List<Pair<String, Int>>, maxValue: Int, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { (label, count) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                val frac = if (maxValue > 0) count.toFloat() / maxValue else 0f
                Box(
                    Modifier
                        .width(22.dp)
                        .height((100 * frac).coerceAtLeast(4f).dp)
                        .background(if (count > 0) barColor else emptyColor, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                )
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
