package com.tim.launcher.settings

import android.Manifest
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
    0xFFF1EDE6.toInt(), // paper
)

/** Settings › Widgets: how the clocks and weather look, and where the weather is for. */
@Composable
fun WidgetSettings(ctx: SettingsContext) {
    val store = ctx.store
    val context = LocalContext.current
    remember { Weather.loadCache(context); true }
    var colorOpen by remember { mutableStateOf<String?>(null) }
    // Opened from a widget: that one first in the preview, as it's set up (its place, big icon). Settings keeps it up
    // to date as its own settings change here
    val mine = ctx.widget?.takeIf { it.kind != WidgetKind.ANDROID }
    fun change(f: (com.tim.launcher.HomeWidget) -> com.tim.launcher.HomeWidget) {
        val w = mine ?: return
        ctx.onWidget(f(w))
        com.tim.launcher.WidgetBus.change(w.id, f)
    }
    var previewKind by remember { mutableStateOf(mine?.kind ?: WidgetKind.FLIP_WEATHER) }
    // Opened from a widget: only the settings that widget uses. From Settings: all of them
    val k = mine?.kind
    val showTime = k == null || k in WidgetKind.times
    val flips = k == null || k in WidgetKind.flips
    val moves = k == null || k in WidgetKind.flips || k in WidgetKind.moving
    val showCards = k == null || k in WidgetKind.cards
    val showAccent = k == null || k in WidgetKind.accents
    val showWeather = k == null || k.weather

    ScreenScaffold(ctx, pinned = {
        // From a widget, just that widget; otherwise a few to look at
        if (mine == null) Tabs(previewKind, WidgetKind.forSetup, { shortName(it) }, Modifier.padding(top = 4.dp)) { previewKind = it }
        WidgetPreview(store, previewKind, mine?.takeIf { it.kind == previewKind })
    }) {
        if (k != null) {
            ScreenTitle(k.label)
            mine?.let { w -> if (k != WidgetKind.TIPS) ThisWidget(w) { change(it) } }
            BodyText(
                "The rest are shared by every widget that uses them.",
                Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
            )
        } else {
            ScreenTitle("Widgets", "Clocks, dates and weather for your home screen")
            GlassPanel(Modifier.padding(top = 16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    BodyText(
                        "To add one, long-press an empty spot on your home screen and tap Widgets. Hold a widget to move it, " +
                            "or hold and let go to resize it, change its style, its place or what tapping it opens, or remove it.",
                    )
                }
            }
        }

        if (showTime || moves) {
            SectionLabel(if (showTime) "Time" else "Motion")
            GlassPanel {
                if (showTime) SettingRow("24-hour time") { Toggle(store[S.clock24h]) { store.set(S.clock24h, value = it) } }
                if (showTime && moves) RowDivider()
                if (moves) {
                    val about = when {
                        k == null -> "Flip cards turn over and weather icons move. Off, they just change."
                        k in WidgetKind.flips && k.weather -> "The cards turn over and the weather icon moves. Off, they just change."
                        k in WidgetKind.flips -> "The cards turn over. Off, they just change."
                        else -> "The weather icons move. Off, they hold still."
                    }
                    SettingRow("Motion", about) { Toggle(store[S.widgetMotion]) { store.set(S.widgetMotion, value = it) } }
                }
            }
        }

        if (showCards) SectionLabel(if (flips && k != null) "Flip cards" else "Cards")
        if (showCards) GlassPanel {
            Text(
                when {
                    k == null -> "The flip clock's cards, and the paper parts of the calendar, month, week and weather card widgets. Text on them turns dark on a light color."
                    k in WidgetKind.flips -> "The flip clock's cards. The numbers turn dark on a light color."
                    else -> "The paper parts of this widget. Text on it turns dark on a light color."
                },
                color = Q.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
            )
            SwatchRow(store[S.widgetColor], WidgetColors) { store.set(S.widgetColor, value = it) }
            RowDivider()
            ColorEditor("Any color", store[S.widgetColor], colorOpen == "cards", { colorOpen = if (colorOpen == "cards") null else "cards" }) {
                store.set(S.widgetColor, value = it)
            }
        }

        if (showAccent) SectionLabel("Accent")
        if (showAccent) GlassPanel {
            Text(
                when {
                    k == null -> "The clock's date, the picked-out parts of the date widgets, temperatures on the weather widgets, and the tips card's buttons"
                    k == WidgetKind.CLOCK -> "The date under the time"
                    k == WidgetKind.TIPS -> "The buttons"
                    k.weather -> "The temperature, and other bits picked out"
                    else -> "The picked-out parts of the date"
                },
                color = Q.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
            )
            SwatchRow(store[S.widgetAccent], WidgetColors) { store.set(S.widgetAccent, value = it) }
            RowDivider()
            ColorEditor("Any color", store[S.widgetAccent], colorOpen == "accent", { colorOpen = if (colorOpen == "accent") null else "accent" }) {
                store.set(S.widgetAccent, value = it)
            }
            Text(
                "The panel behind each widget matches your dock: its glass, gradient, pattern and edge light (Dock › Style).",
                color = Q.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
            )
        }

        // Every widget's numbers and words
        if (k != WidgetKind.TIPS) {
            SectionLabel("Font")
            GlassPanel {
                SettingRow("Widget font", com.tim.launcher.Fonts.nameOf(store[S.fontWidgets]), onClick = { ctx.go(Route.FONTS) })
            }
            if (!showCards && !showAccent) {
                BodyText("Its panel matches your dock (Dock › Style).", Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }

        if (showWeather) SectionLabel("Weather")
        if (showWeather) WeatherControls(store)

        if (showWeather) SectionLabel("About the weather")
        if (showWeather) GlassPanel {
            Column(Modifier.padding(20.dp)) {
                BodyText(
                    "Weather data by Open-Meteo.com: free, no account, no ads or tracking. Quartz only asks while a weather " +
                        "widget is on your home screen (about every half hour), or when you tap Update. It sends a rough " +
                        "location only, rounded to about 1 km. Nothing else about you goes with it. With this phone's location, " +
                        "Android's own place lookup (on most phones, Google's) also gets that rounded spot to name your town.",
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
    WidgetKind.ANDROID -> "App"
    WidgetKind.TIPS -> "Tips"
    else -> kind.label
}

/** This one widget's own settings: its place(s), big icon, and what tapping it opens. */
@Composable
private fun ThisWidget(widget: com.tim.launcher.HomeWidget, onChange: ((com.tim.launcher.HomeWidget) -> com.tim.launcher.HomeWidget) -> Unit) {
    val context = LocalContext.current
    val kind = widget.kind
    var open by remember { mutableStateOf<String?>(null) }
    remember { com.tim.launcher.weather.WeatherPlaces.load(context); true }
    val places = com.tim.launcher.weather.WeatherPlaces.list
    fun placeName(key: String) = if (key.isEmpty()) "My weather" else places.firstOrNull { it.key == key }?.title ?: "A place that's gone"
    val apps by androidx.compose.runtime.produceState(emptyList<com.tim.launcher.AppEntry>()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            sampleApps(context, Int.MAX_VALUE).sortedBy { it.label.lowercase() }
        }
    }

    SectionLabel("This widget")
    GlassPanel {
        if (kind.weather) {
            val dual = kind in WidgetKind.duals
            listOf(false to widget.place).let { if (dual) it + (true to widget.place2) else it }.forEach { (second, key) ->
                val id = if (second) "place2" else "place"
                SettingRow(
                    if (dual) (if (second) "Second place" else "First place") else "Place",
                    if (second && key.isEmpty()) "Pick one" else placeName(key),
                    onClick = { open = if (open == id) null else id },
                )
                if (open == id) {
                    Column(Modifier.padding(bottom = 6.dp)) {
                        if (!second) Choice("My weather", null, key.isEmpty()) { onChange { it.copy(place = "") }; open = null }
                        places.forEach { p ->
                            Choice(p.title, null, key == p.key) {
                                onChange { if (second) it.copy(place2 = p.key) else it.copy(place = p.key) }
                                open = null
                            }
                        }
                        Choice("Add a place…", null, false) { com.tim.launcher.weather.WeatherActivity.open(context, key.ifEmpty { Weather.ME }) }
                    }
                }
                RowDivider()
            }
            if (kind in WidgetKind.bigIcons) {
                SettingRow("Big icon", "The icon spills past the panel's edge") {
                    Toggle(widget.bigIcon) { big -> onChange { it.copy(bigIcon = big) } }
                }
                RowDivider()
            }
        }
        val opens = when (widget.opens) {
            "" -> com.tim.launcher.usualTap(kind)
            com.tim.launcher.OPENS_NOTHING -> "Nothing"
            com.tim.launcher.StarterApps.QUARTZ_SETTINGS -> "Quartz settings"
            else -> apps.firstOrNull { it.key == widget.opens }?.label ?: "Another app"
        }
        SettingRow("Tapping it opens", opens, onClick = { open = if (open == "opens") null else "opens" })
        if (open == "opens") {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(bottom = 6.dp)) {
                Choice(com.tim.launcher.usualTap(kind), null, widget.opens == "") { onChange { it.copy(opens = "") }; open = null }
                Choice("Nothing", null, widget.opens == com.tim.launcher.OPENS_NOTHING) {
                    onChange { it.copy(opens = com.tim.launcher.OPENS_NOTHING) }
                    open = null
                }
                Choice("Quartz settings", null, widget.opens == com.tim.launcher.StarterApps.QUARTZ_SETTINGS) {
                    onChange { it.copy(opens = com.tim.launcher.StarterApps.QUARTZ_SETTINGS) }
                    open = null
                }
                apps.forEach { app ->
                    Choice(app.label, app.icon, widget.opens == app.key) {
                        onChange { it.copy(opens = app.key) }
                        open = null
                    }
                }
            }
        }
    }
}

/** One choice in a list that opened under a row. */
@Composable
private fun Choice(label: String, icon: androidx.compose.ui.graphics.ImageBitmap?, chosen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 32.dp, end = 20.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            androidx.compose.foundation.Image(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(label, color = if (chosen) Q.Text else Q.TextDim, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (chosen) Box(Modifier.size(10.dp).background(Q.Accent, androidx.compose.foundation.shape.CircleShape))
    }
}

/** A widget as it'll look on your home screen, with your glass, dock style and colors. */
@Composable
internal fun WidgetPreview(store: SettingsStore, kind: WidgetKind, widget: com.tim.launcher.HomeWidget? = null) {
    val panelColor = Color(store[S.panelColor])
    val panel = panelColor.copy(alpha = store[S.panelOpacity].coerceIn(0.05f, 1f))
    val look = HomeLook(panel = panel, dock = DockStyle.of(store), onPanel = Ink.on(Ink.brightness(panel, 0.3f)))
    CompositionLocalProvider(LocalHomeLook provides look) {
        Box(Modifier.fillMaxWidth().height(170.dp).padding(top = 18.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
            // In about its own shape on the home screen (a 1 × 1 isn't stretched across the screen)
            val ratio = kind.w * 1.5f / kind.h
            WidgetView(
                kind,
                // (Not fillMaxHeight first: that pins the height, so a wide widget couldn't shrink to the screen's width)
                Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true),
                sample = widget == null,
                place = widget?.place ?: "",
                place2 = widget?.place2 ?: "",
                bigIcon = widget?.bigIcon ?: false,
            )
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
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Icons", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
            Segmented(store[S.weatherIcons], WeatherIconStyle.entries, { it.label }) { store.set(S.weatherIcons, value = it) }
        }
        RowDivider()
        SettingRow("Weather warnings", "US only, from the National Weather Service, on the weather page") {
            com.tim.launcher.ui.Toggle(store[S.weatherAlerts]) { store.set(S.weatherAlerts, value = it) }
        }
        RowDivider()
        SettingRow("Rain radar", "The past 2 hours on the weather page, from RainViewer, which sees the rough area shown") {
            com.tim.launcher.ui.Toggle(store[S.weatherRadar]) { store.set(S.weatherRadar, value = it) }
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
