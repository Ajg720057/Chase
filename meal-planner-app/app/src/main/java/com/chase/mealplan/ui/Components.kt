package com.chase.mealplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chase.mealplan.MealPlanApp
import com.chase.mealplan.data.MealEntity
import com.chase.mealplan.data.Slot
import com.chase.mealplan.data.Week
import com.chase.mealplan.ui.theme.style
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The meal's photo, or a colored tile with its first letter when there's no photo. */
@Composable
fun MealThumb(meal: MealEntity, size: Dp, modifier: Modifier = Modifier, corner: Dp = 10.dp) {
    val app = LocalContext.current.applicationContext as MealPlanApp
    val shape = RoundedCornerShape(corner)
    if (meal.photo != null) {
        AsyncImage(
            model = app.photos.file(meal.photo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                meal.name.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = if (size < 48.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun WeekSwitcher(
    week: Week,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous week") }
        Text(
            week.label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next week") }
    }
}

/** Pick a day (in any week) and a slot to put a saved meal on. */
@Composable
fun AddToPlanDialog(
    startWeek: Week,
    onPick: (LocalDate, Slot) -> Unit,
    onDismiss: () -> Unit,
) {
    var week by remember { mutableStateOf(startWeek) }
    var day by remember { mutableStateOf<LocalDate?>(null) }
    var slot by remember { mutableStateOf(Slot.DINNER) }
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEE d") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to plan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                WeekSwitcher(week, { week = week.plusWeeks(-1); day = null }, { week = week.plusWeeks(1); day = null })
                week.days.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { d ->
                            FilterChip(
                                selected = d == day,
                                onClick = { day = d },
                                label = { Text(d.format(dayFmt)) },
                            )
                        }
                    }
                }
                Text("Meal", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                Slot.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { s ->
                            FilterChip(
                                selected = s == slot,
                                onClick = { slot = s },
                                label = { Text(s.label) },
                                leadingIcon = { Icon(s.style().icon, null, Modifier.size(18.dp), tint = s.style().color) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = day != null, onClick = { day?.let { onPick(it, slot) }; onDismiss() }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A tappable row used in lists of meals. */
@Composable
fun MealRow(meal: MealEntity, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MealThumb(meal, 52.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(meal.name, style = MaterialTheme.typography.titleMedium)
            val bits = buildList {
                if (meal.ingredients.isNotEmpty()) add("${meal.ingredients.size} ingredient${if (meal.ingredients.size == 1) "" else "s"}")
                if (meal.supplies.isNotEmpty()) add("${meal.supplies.size} suppl${if (meal.supplies.size == 1) "y" else "ies"}")
                if (meal.recipe.isNotBlank()) add("recipe")
            }
            if (bits.isNotEmpty()) {
                Text(
                    bits.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}
