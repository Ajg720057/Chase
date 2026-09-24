package com.chase.workoutlog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

/** A text field that only accepts numbers; [decimal] allows one decimal point. */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    suffix: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val cleaned = raw.filter { it.isDigit() || (decimal && it == '.') }
            val ok = cleaned.count { it == '.' } <= 1 && cleaned.length <= 7
            if (ok) onValueChange(cleaned)
        },
        label = { Text(label) },
        suffix = if (suffix != null) { { Text(suffix) } } else null,
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier,
    )
}

/** Big "− 3 +" control for picking a small count such as number of sets. */
@Composable
fun Stepper(value: Int, onValueChange: (Int) -> Unit, min: Int = 1, max: Int = 20) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { if (value > min) onValueChange(value - 1) }, enabled = value > min) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        FilledTonalIconButton(onClick = { if (value < max) onValueChange(value + 1) }, enabled = value < max) {
            Icon(Icons.Default.Add, contentDescription = "Add one")
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    valueColor: Color = Color.Unspecified,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Small colored dot used as a legend/indicator. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(10.dp)) { drawCircle(color) }
}
