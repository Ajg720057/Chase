package com.chase.planboard.ui.common

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chase.planboard.data.PlanStatus
import com.chase.planboard.ui.PlanSummary
import com.chase.planboard.ui.theme.ScopeColors
import com.chase.planboard.ui.theme.StatusColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * One plan: a stripe colored by scope, a status button (tap to move it along),
 * the title, and small badges for time, to-do progress, sub-plans and LifeBoard link.
 */
@Composable
fun PlanCard(
    item: PlanSummary,
    onClick: () -> Unit,
    onStatusClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val plan = item.plan
    val done = plan.status == PlanStatus.DONE
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(ScopeColors.of(plan.scope)),
            )
            StatusButton(plan.status, onStatusClick)
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = 10.dp, bottom = 10.dp, end = 12.dp),
            ) {
                Text(
                    plan.title.ifBlank { "Untitled plan" },
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(subtitle, item.parentTitle?.let { "Part of $it" }).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val start = plan.startMinute
                if (start != null || item.todosTotal > 0 || item.subPlans > 0 || plan.linkToLifeBoard) {
                    Row(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (start != null) Badge(Icons.Filled.Schedule, Format.minuteOfDay(start))
                        if (item.todosTotal > 0) Badge(Icons.Filled.Checklist, "${item.todosDone}/${item.todosTotal}")
                        if (item.subPlans > 0) Badge(Icons.Filled.AccountTree, item.subPlans.toString())
                        if (plan.linkToLifeBoard) Badge(Icons.Filled.Link, "LifeBoard")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusButton(status: PlanStatus, onClick: () -> Unit) {
    val icon = when (status) {
        PlanStatus.PLANNED -> Icons.Filled.RadioButtonUnchecked
        PlanStatus.IN_PROGRESS -> Icons.Filled.Timelapse
        PlanStatus.DONE -> Icons.Filled.CheckCircle
    }
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = "Status: ${status.label}. Tap to change.",
            tint = StatusColors.of(status) ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Badge(icon: ImageVector, text: String?, tint: Color? = null) {
    val color = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        if (text != null) {
            Spacer(Modifier.width(3.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

/** "‹  September 2026  ›" header; arrows or a sideways swipe move to the previous/next period. */
@Composable
fun PeriodSwitcher(
    title: String,
    subtitle: String?,
    onShift: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.swipeToShift(onShift), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        IconButton(onClick = { onShift(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
        }
    }
}

/** Swiping right goes back one period, swiping left goes forward. */
fun Modifier.swipeToShift(onShift: (Long) -> Unit): Modifier = pointerInput(onShift) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = { if (total > 120) onShift(-1) else if (total < -120) onShift(1) },
        onHorizontalDrag = { _, amount -> total += amount },
    )
}

/** Material date picker; works in LocalDate so callers never touch its UTC millis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerModal(hour: Int, minute: Int, onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.hour, state.minute); onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
