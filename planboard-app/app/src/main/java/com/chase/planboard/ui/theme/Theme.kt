package com.chase.planboard.ui.theme

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
import com.chase.planboard.data.PlanStatus
import com.chase.planboard.data.Scope

private val Light = lightColorScheme(
    primary = Color(0xFF00796B),
    secondary = Color(0xFF4DB6AC),
    tertiary = Color(0xFF5E35B1),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF80CBC4),
    secondary = Color(0xFFB2DFDB),
    tertiary = Color(0xFFB39DDB),
)

/** Stripe colors that tell month, week and day plans apart at a glance. */
object ScopeColors {
    val month = Color(0xFF7E57C2)
    val week = Color(0xFF1E88E5)
    val day = Color(0xFF00897B)
    fun of(scope: Scope): Color = when (scope) {
        Scope.MONTH -> month
        Scope.WEEK -> week
        Scope.DAY -> day
    }
}

object StatusColors {
    val inProgress = Color(0xFFFB8C00)
    val done = Color(0xFF43A047)
    fun of(status: PlanStatus): Color? = when (status) {
        PlanStatus.IN_PROGRESS -> inProgress
        PlanStatus.DONE -> done
        PlanStatus.PLANNED -> null
    }
}

@Composable
fun PlanBoardTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
