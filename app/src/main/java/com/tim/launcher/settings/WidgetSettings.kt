package com.tim.launcher.settings

import android.Manifest
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.DockStyle
import com.tim.launcher.HomeLook
import com.tim.launcher.Ink
import com.tim.launcher.LocalHomeLook
import com.tim.launcher.WidgetKind
import com.tim.launcher.WidgetView
import com.tim.launcher.ui.BodyText
import com.tim.launcher.ui.GlassPanel
import com.tim.launcher.ui.PrimaryButton
import com.tim.launcher.ui.Q
import com.tim.launcher.ui.RowDivider
import com.tim.launcher.ui.ScreenTitle
import com.tim.launcher.ui.SectionLabel
import com.tim.launcher.ui.Segmented
import com.tim.launcher.ui.SettingRow
import com.tim.launcher.ui.SwatchRow
import com.tim.launcher.ui.Tabs
import com.tim.launcher.ui.Toggle
import com.tim.launcher.weather.Place
import com.tim.launcher.weather.Weather
import kotlinx.coroutines.launch

/** Colors that suit the flip cards (the first is the pink from Tim's mockups). */
private val WidgetColors: List<Int> = listOf(
    0xFFBF5AA6.toInt(), // orchid
    0xFF8C6CFF.toInt(), // Quartz violet
    0xFF3F7FE0.toInt(), // blue
    0xFF1E9C8F.toInt(), // teal
    0xFFE0763F.toInt(), // amber
    0xFF3A3846.toInt(), // graphite
)

/** Settings › Widgets: how the clocks and weather look, and where the weather is for. */
@Composable
fun WidgetSettings(ctx: SettingsContext) {
    val store = ctx.store
    val context = LocalContext.current
    remember { Weather.loadCache(context); true }
    var colorOpen by remember { mutableStateOf(false) }
    var previewKind by remember { mutableStateOf(WidgetKind.FLIP_WEATHER) }

    ScreenScaffold(ctx, pinned = {
        Tabs(previewKind, WidgetKind.entries, { shortName(it) }, Modifier.padding(top = 4.dp)) { previewKind = it }
        WidgetPreview(store, previewKind)
    }) {
        ScreenTitle("Widgets", "Clocks and weather for your home screen")
        GlassPanel(Modifier.padding(top = 16.dp)) {
            Column(Modifier.padding(20.dp)) {
                BodyText(
                    "To add one, long-press an empty spot on your home screen and tap Widgets. Hold a widget to move it, " +
                        "or hold and let go to resize it, change its style or remove it.",
                )
            }
        }

        SectionLabel("Clock")
        GlassPanel {
            SettingRow("24-hour time") { Toggle(store[S.clock24h]) { store.set(S.clock24h, value = it) } }
            RowDivider()
            SettingRow("Motion", "Flip cards turn over and weather icons move. Off, they just change.") {
                Toggle(store[S.widgetMotion]) { store.set(S.widgetMotion, value = it) }
            }
        }

        SectionLabel("Color")
        GlassPanel {
            Text(
                "The flip clock's cards and the clock's date",
                color = Q.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
            )
            SwatchRow(store[S.widgetColor], WidgetColors) { store.set(S.widgetColor, value = it) }
            RowDivider()
            ColorEditor("Any color", store[S.widgetColor], colorOpen, { colorOpen = !colorOpen }) { store.set(S.widgetColor, value = it) }
            Text(
                "The panel behind each widget matches your dock: its glass, gradient, pattern and edge light (Dock › Style).",
                color = Q.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
            )
        }

        SectionLabel("Weather")
        WeatherControls(store)

        SectionLabel("About the weather")
        GlassPanel {
            Column(Modifier.padding(20.dp)) {
                BodyText(
                    "Weather data by Open-Meteo.com: free, no account, no ads or tracking. Quartz only asks while a weather " +
                        "widget is on your home screen (about every half hour), or when you tap Update. It sends a rough " +
                        "location only, rounded to about 1 km. Nothing else about you goes with it.",
                )
                Spacer(Modifier.height(8.dp))
                BodyText("Weather icons: Meteocons by Bas Milius (MIT). Typeface: Outfit (SIL Open Font License).")
            }
        }
    }
}

private fun shortName(kind: WidgetKind) = when (kind) {
    WidgetKind.CLOCK -> "Clock"
    WidgetKind.FLIP -> "Flip"
    WidgetKind.FLIP_WEATHER -> "Flip + weather"
    WidgetKind.WEATHER -> "Weather"
}

/** A widget as it'll look on your home screen, with your glass, dock style and colors. */
@Composable
internal fun WidgetPreview(store: SettingsStore, kind: WidgetKind) {
    val panelColor = Color(store[S.panelColor])
    val panel = panelColor.copy(alpha = store[S.panelOpacity].coerceIn(0.05f, 1f))
    val look = HomeLook(panel = panel, dock = DockStyle.of(store), onPanel = Ink.on(Ink.brightness(panel, 0.3f)))
    CompositionLocalProvider(LocalHomeLook provides look) {
        Box(Modifier.fillMaxWidth().height(170.dp).padding(top = 18.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
            WidgetView(kind, Modifier.fillMaxSize(), sample = true)
        }
    }
}

/**
 * Where the weather is for (a city you search for, or this phone's location) and °F or °C. Shared with setup.
 */
@Composable
fun WeatherControls(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchNote by remember { mutableStateOf<String?>(null) }
    var allowed by remember { mutableStateOf(Weather.hasLocationPermission(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (granted) {
            store.set(S.weatherSource, value = WeatherSource.DEVICE)
            Weather.locationChanged(context)
        } else {
            toast(context, "Location wasn't allowed. You can choose a city instead.")
        }
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        searching = true
        searchNote = null
        scope.launch {
            runCatching { Weather.search(q) }
                .onSuccess {
                    results = it
                    if (it.isEmpty()) searchNote = "No places found for \"$q\""
                }
                .onFailure { searchNote = "Couldn't search just now. Check your connection." }
            searching = false
        }
    }

    val source = store[S.weatherSource]
    GlassPanel {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Where", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
            Segmented(source, WeatherSource.entries, { it.label }) { chosen ->
                if (chosen == WeatherSource.DEVICE && !Weather.hasLocationPermission(context)) {
                    ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                } else if (chosen != source) {
                    store.set(S.weatherSource, value = chosen)
                    Weather.locationChanged(context)
                }
            }
        }
        RowDivider()
        if (source == WeatherSource.CITY) {
            val city = store[S.weatherCity]
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(if (city.isBlank()) "No city chosen yet" else city, color = Q.Text, fontSize = 16.sp)
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(60) },
                        singleLine = true,
                        placeholder = { Text("Search for a city") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    PrimaryButton(if (searching) "…" else "Search", enabled = query.isNotBlank() && !searching) { search() }
                }
                searchNote?.let { Text(it, color = Q.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
            results.forEach { place ->
                RowDivider()
                SettingRow(place.name, listOf(place.region, place.country).filter { it.isNotBlank() }.distinct().joinToString(", "), onClick = {
                    Weather.chooseCity(context, place)
                    results = emptyList()
                    query = ""
                })
            }
        } else {
            SettingRow(
                "Location access",
                if (allowed) "Rough location only, rounded to about 1 km. If the phone can't say where it is, your city is used." else "Not allowed yet. Tap to allow.",
                onClick = { if (!allowed) ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
            ) { Text(if (allowed) "On" else "Off", color = Q.TextDim, fontSize = 14.sp) }
        }
        RowDivider()
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Units", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
            Segmented(store[S.weatherUnits], TempUnit.entries, { it.label }) { store.set(S.weatherUnits, value = it) }
        }
        RowDivider()
        val now = Weather.now
        SettingRow(
            now?.let { "${it.place}: ${it.condition.lowercase()}, ${Weather.format(it.tempC, store[S.weatherUnits])}" } ?: "No weather yet",
            Weather.problem ?: now?.let { "Updated " + ago(it.at) } ?: "Choose where, then add a weather widget",
            onClick = { Weather.refresh(context, force = true) },
        ) { Text(if (Weather.loading) "Updating…" else "Update", color = Q.Accent, fontSize = 14.sp) }
    }
}

private fun ago(at: Long): String =
    if (System.currentTimeMillis() - at < 60_000L) "just now"
    else DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()
