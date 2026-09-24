package com.chase.mealplan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BreakfastDining
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.chase.mealplan.data.Slot

private val Light = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0B3D0F),
    secondary = Color(0xFFEF6C00),
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = Color(0xFF4A2300),
    tertiary = Color(0xFF00796B),
    background = Color(0xFFF7F8F4),
    surface = Color(0xFFF7F8F4),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEFF2EA),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF0B3D0F),
    primaryContainer = Color(0xFF1E4D22),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFFFB74D),
    secondaryContainer = Color(0xFF5C3300),
    onSecondaryContainer = Color(0xFFFFE0B2),
    tertiary = Color(0xFF4DB6AC),
)

/** Accent color and icon for each meal slot. */
data class SlotStyle(val color: Color, val icon: ImageVector)

fun Slot.style(dark: Boolean): SlotStyle = when (this) {
    Slot.BREAKFAST -> SlotStyle(if (dark) Color(0xFFFFD54F) else Color(0xFFF9A825), Icons.Filled.BreakfastDining)
    Slot.LUNCH -> SlotStyle(if (dark) Color(0xFF81C784) else Color(0xFF43A047), Icons.Filled.LunchDining)
    Slot.DINNER -> SlotStyle(if (dark) Color(0xFF90CAF9) else Color(0xFF1E88E5), Icons.Filled.DinnerDining)
    Slot.SNACK -> SlotStyle(if (dark) Color(0xFFF48FB1) else Color(0xFFD81B60), Icons.Filled.Icecream)
}

@Composable
fun Slot.style(): SlotStyle = style(isSystemInDarkTheme())

@Composable
fun MealPlanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
