package com.chase.workoutlog.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E5EFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF00174F),
    secondary = Color(0xFF00A67E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F2E3),
    onSecondaryContainer = Color(0xFF002117),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB5C4FF),
    onPrimary = Color(0xFF00297A),
    primaryContainer = Color(0xFF0040B0),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFF7FD9B9),
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF00513D),
    onSecondaryContainer = Color(0xFFC8F2E3),
)

/** Green for improvements, red for regressions; readable in light and dark. */
object Trend {
    @Composable
    fun up(): Color = if (isSystemInDarkTheme()) Color(0xFF6DDC9C) else Color(0xFF16803C)

    @Composable
    fun down(): Color = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Color(0xFFC62828)
}

@Composable
fun WorkoutLogTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
