package com.chase.babylayers.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chase.babylayers.logic.Conditions
import com.chase.babylayers.logic.Note
import com.chase.babylayers.logic.OutdoorAdvice
import com.chase.babylayers.logic.Outing
import com.chase.babylayers.logic.Recommender
import com.chase.babylayers.logic.Severity
import com.chase.babylayers.logic.SleepAdvice
import com.chase.babylayers.logic.TempUnit
import com.chase.babylayers.logic.fToC
import com.chase.babylayers.logic.formatAge
import com.chase.babylayers.logic.formatTemp
import com.chase.babylayers.logic.weatherDescription
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyLayersApp(vm: MainViewModel) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Baby Layers 👶") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            BabyCard(vm)
            LocationCard(vm)

            val conditions = vm.conditions
            if (conditions != null) {
                OutdoorCard(Recommender.outdoor(vm.ageMonths, conditions, vm.outing), vm)
            } else {
                InfoCard(
                    "Set your location (or type in the temperature) to see what your baby should wear outside.",
                )
            }

            SleepCard(Recommender.sleep(vm.ageMonths, vm.roomTempC), vm)
            CheckCard()
            Text(
                "General guidance only, not medical advice. Every baby is different — trust how your baby feels, " +
                    "and talk to your doctor or health visitor if you're worried.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Text(
                "Weather data by Open-Meteo.com",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(text, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun BabyCard(vm: MainViewModel) {
    SectionCard("Your baby") {
        Text("Age: ${formatAge(vm.ageMonths)}", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = vm.ageMonths.toFloat(),
            onValueChange = { vm.updateAge(it.roundToInt()) },
            valueRange = 0f..48f,
            steps = 47,
        )
        Text("Going out by", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Outing.entries.forEach { o ->
                FilterChip(
                    selected = vm.outing == o,
                    onClick = { vm.updateOuting(o) },
                    label = { Text(o.label) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Units", style = MaterialTheme.typography.labelLarge)
            TempUnit.entries.forEach { u ->
                FilterChip(
                    selected = vm.unit == u,
                    onClick = { vm.updateUnit(u) },
                    label = { Text(u.symbol) },
                )
            }
        }
    }
}

@Composable
private fun LocationCard(vm: MainViewModel) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) vm.useDeviceLocation() else vm.locationPermissionDenied()
    }

    fun requestLocation() {
        val granted = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) {
            vm.useDeviceLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            )
        }
    }

    SectionCard("Where are you?") {
        Button(onClick = { requestLocation() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.LocationOn, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Use my location")
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Or search a city") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                vm.search(query)
            }),
            trailingIcon = {
                IconButton(onClick = {
                    focus.clearFocus()
                    vm.search(query)
                }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
            },
        )
        vm.searchResults.forEach { p ->
            Text(
                p.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        query = ""
                        vm.choosePlace(p)
                    }
                    .padding(vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            HorizontalDivider()
        }

        vm.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        val place = vm.place
        val weather = vm.weather
        if (place != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📍 ${place.name}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.refreshWeather() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh weather")
                }
            }
        }
        if (weather != null && vm.manualTempC == null) {
            WeatherSummary(weather.current, vm.unit, weather.todayMinC, weather.todayMaxC)
        }

        HorizontalDivider()
        Text("No signal? Enter the outdoor temperature yourself:", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                label = { Text("Temp (${vm.unit.symbol})") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
            FilledTonalButton(onClick = {
                focus.clearFocus()
                manual.replace(',', '.').toDoubleOrNull()?.let {
                    vm.updateManualTemp(if (vm.unit == TempUnit.FAHRENHEIT) fToC(it) else it)
                }
            }) { Text("Use") }
        }
        if (vm.manualTempC != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Using ${formatTemp(vm.manualTempC!!, vm.unit)} (entered manually)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = {
                    manual = ""
                    vm.updateManualTemp(null)
                }) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun WeatherSummary(c: Conditions, unit: TempUnit, minC: Double?, maxC: Double?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatTemp(c.tempC, unit), fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(
                "Feels like ${formatTemp(c.feelsLikeC, unit)}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            listOfNotNull(
                weatherDescription(c.weatherCode),
                "wind ${c.windKph.roundToInt()} km/h",
                if (c.isDay) "UV ${c.uvIndex.roundToInt()}" else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (minC != null && maxC != null) {
            Text(
                "Today: low ${formatTemp(minC, unit)}, high ${formatTemp(maxC, unit)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OutdoorCard(advice: OutdoorAdvice, vm: MainViewModel) {
    SectionCard("Going outside now") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${advice.layers}",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    if (advice.layers == 1) "layer" else "layers",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "${advice.band.emoji} ${advice.band.label} · ${formatAge(vm.ageMonths)} · ${vm.outing.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text("Dress from the skin out:", style = MaterialTheme.typography.labelLarge)
        advice.clothing.forEachIndexed { i, item -> Bullet("${i + 1}.", item) }

        if (advice.accessories.isNotEmpty()) {
            Text("Also:", style = MaterialTheme.typography.labelLarge)
            advice.accessories.forEach { Bullet("•", it) }
        }
        advice.notes.forEach { NoteRow(it) }
    }
}

@Composable
private fun SleepCard(advice: SleepAdvice, vm: MainViewModel) {
    SectionCard("Sleep & naps (indoors)") {
        Text("Nursery room temperature", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val step = if (vm.unit == TempUnit.FAHRENHEIT) 5.0 / 9.0 else 1.0
            OutlinedButton(onClick = { vm.updateRoomTemp(vm.roomTempC - step) }) { Text("−") }
            Text(formatTemp(vm.roomTempC, vm.unit), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { vm.updateRoomTemp(vm.roomTempC + step) }) { Text("+") }
        }
        vm.weather?.tonightMinC?.let {
            Text(
                "Outside tonight: down to ${formatTemp(it, vm.unit)} — rooms without heating or A/C will drift toward this.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    "${formatTog(advice.tog)} TOG",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(advice.sleepBag, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        Text("Under the sleep bag:", style = MaterialTheme.typography.labelLarge)
        advice.clothing.forEach { Bullet("•", it) }
        advice.notes.forEach { NoteRow(it) }
    }
}

private fun formatTog(tog: Double): String =
    if (tog == tog.roundToInt().toDouble()) "${tog.roundToInt()}.0" else tog.toString()

@Composable
private fun CheckCard() {
    SectionCard("Is baby too hot or too cold?") {
        Bullet("✋", "Feel the back of the neck or chest — it should be warm and dry, not sweaty or cold. Hands and feet are naturally cooler, so don't go by those.")
        Bullet("🥵", "Too hot: sweaty or damp hair, flushed red cheeks, heat rash, fast breathing, restless. Remove a layer.")
        Bullet("🥶", "Too cold: cool chest or tummy, pale or mottled skin, fussy, unusually quiet. Add a layer or go inside.")
        Bullet("🏠", "Take off hats and extra layers as soon as you come indoors or get into a warm car or shop, even if baby is asleep.")
        NoteRow(
            Note(
                "Seek medical help urgently if baby is under 3 months with a temperature of 38°C (100.4°F) or higher, or is floppy, hard to wake, or has cold, blue or mottled skin.",
                Severity.DANGER,
            ),
        )
    }
}

@Composable
private fun Bullet(marker: String, text: String) {
    Row {
        Text(marker, modifier = Modifier.width(28.dp), style = MaterialTheme.typography.bodyLarge)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NoteRow(note: Note) {
    val (bg, fg, icon) = when (note.severity) {
        Severity.DANGER -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "⚠️")
        Severity.CAUTION -> Triple(Color(0xFFFFF3CD), Color(0xFF5C4400), "💡")
        Severity.INFO -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "ℹ️")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(icon, modifier = Modifier.width(28.dp))
        Text(note.text, color = fg, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(2.dp))
}
