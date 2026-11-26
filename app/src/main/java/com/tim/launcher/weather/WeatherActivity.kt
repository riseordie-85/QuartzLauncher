package com.tim.launcher.weather

import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.WeatherIcon
import com.tim.launcher.settings.S
import com.tim.launcher.settings.SettingsStore
import com.tim.launcher.settings.TempUnit
import com.tim.launcher.ui.Q
import com.tim.launcher.ui.QuartzTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Quartz's weather page: everything about the weather at one of your places, opened from a weather widget. */
class WeatherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius((48 * resources.displayMetrics.density).toInt())
        }
        val start = intent.getStringExtra(EXTRA_PLACE) ?: Weather.ME
        setContent {
            QuartzTheme {
                BackHandler { finish() }
                Box(Modifier.fillMaxSize().background(Q.Scrim)) {
                    WeatherPage(start) { finish() }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_PLACE = "place"

        /** Opens the weather page at [key] ([Weather.ME] or a saved place's key). */
        fun open(context: Context, key: String = Weather.ME) {
            context.startActivity(
                Intent(context, WeatherActivity::class.java).putExtra(EXTRA_PLACE, key).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private val Card = RoundedCornerShape(22.dp)

@Composable
private fun WeatherPage(startKey: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    remember { Weather.loadCache(context); true }
    var key by remember { mutableStateOf(if (startKey == Weather.ME || WeatherPlaces.byKey(startKey) != null) startKey else Weather.ME) }
    val unit = store[S.weatherUnits]
    val h24 = store[S.clock24h]
    val accent = Color(store[S.widgetAccent]).let { if (it.alpha < 0.5f) Q.Accent else it.copy(alpha = 1f) }
    val report = Weather.reports[key]
    val busy = Weather.fetching[key] == true
    LaunchedEffect(key) { Weather.refreshPlace(context, key) }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        // Top: back, the units, refresh
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClose)
            Spacer(Modifier.weight(1f))
            Row(Modifier.clip(CircleShape).background(Q.Glass)) {
                TempUnit.entries.forEach { u ->
                    Text(
                        u.label,
                        color = if (u == unit) Q.Text else Q.TextDim,
                        fontWeight = if (u == unit) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (u == unit) Q.GlassStrong else Color.Transparent)
                            .clickable { store.set(S.weatherUnits, value = u) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            RoundButton(Icons.Rounded.Refresh, if (busy) "Updating" else "Update", { Weather.refreshPlace(context, key, force = true) })
        }

        // Your places, to switch between
        PlaceChips(key) { key = it }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                if (report == null) {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                busy -> "Getting the weather…"
                                key == Weather.ME && Weather.problem != null -> Weather.problem!!
                                else -> "No weather yet. Tap update to try again."
                            },
                            color = Q.TextDim,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    Current(report, key, unit, h24, accent)
                    report.alerts.forEach { AlertCard(it, h24, report.timezone) }
                    Section { Text(WeatherText.summary(report, unit, h24), color = Q.Text, fontSize = 16.sp, lineHeight = 22.sp) }
                    RainSoon(report, accent, h24)
                    if (store[S.weatherRadar]) RadarCard(report, accent)
                    Hourly(report, unit, h24)
                    Daily(report, unit, accent)
                    Details(report, unit, h24, accent)
                    Text(
                        "Updated ${ago(report.at)} • Open-Meteo" +
                            (if (report.alerts.isNotEmpty() || report.lat in 17.0..72.0 && report.lon < -64.0) ", NWS" else "") +
                            (if (store[S.weatherRadar]) ", RainViewer" else ""),
                        color = Q.TextFaint,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                Places(key, onPick = { key = it }, onRemoved = { if (it == key) key = Weather.ME })
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = Q.Text)
    }
}

/** A rounded glass card for a part of the page. */
@Composable
private fun Section(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(Card)
            .background(Q.Glass)
            .border(1.dp, Q.Edge, Card)
            .padding(16.dp),
    ) {
        if (title != null) Text(title, color = Q.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
        content()
    }
}

/** My weather, then your saved places, as a row of chips. */
@Composable
private fun PlaceChips(key: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    remember { WeatherPlaces.load(context); true }
    val places = listOf(Weather.ME to "My weather") + WeatherPlaces.list.map { it.key to it.title }
    if (places.size < 2) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        places.forEach { (k, title) ->
            Text(
                title,
                color = if (k == key) Q.Text else Q.TextDim,
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (k == key) Q.GlassStrong else Q.Glass)
                    .clickable { onPick(k) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Where, what time it is there, and the weather now. */
@Composable
private fun Current(r: WeatherReport, key: String, unit: TempUnit, h24: Boolean, accent: Color) {
    val context = LocalContext.current
    val place = WeatherPlaces.byKey(key)
    // The airport list is read on first use: not on the main thread
    val airport by produceState<String?>(null, r.lat, r.lon) {
        value = withContext(Dispatchers.Default) { Airports.nearest(context, r.lat, r.lon) }
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
        val nickname = place?.nickname?.ifBlank { null } ?: if (key == Weather.ME) "My weather" else null
        nickname?.let { Text(it, color = Q.TextDim, fontSize = 14.sp) }
        Text(place?.label ?: r.name, color = Q.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        val bits = listOfNotNull(
            WeatherText.clock(System.currentTimeMillis(), r.timezone, h24),
            if (key == Weather.ME) "Your weather" else WeatherText.timeDifference(r.timezone),
            airport,
            WeatherText.elevation(r.elevationM, unit),
        )
        Text(bits.joinToString(" • "), color = Q.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(WeatherText.temp(r.tempC, unit), color = accent, fontSize = 76.sp, fontWeight = FontWeight.Bold, lineHeight = 80.sp)
                Text(r.condition, color = Q.Text, fontSize = 20.sp)
                Text(
                    "H: ${WeatherText.temp(r.highC, unit)} • L: ${WeatherText.temp(r.lowC, unit)} • Feels like ${WeatherText.temp(r.feelsC, unit)}",
                    color = Q.TextDim,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            WeatherIcon(r.icon, true, Modifier.size(132.dp))
        }
    }
}

/** A weather warning, amber or red by how serious, opening to its details. */
@Composable
private fun AlertCard(a: Alert, h24: Boolean, tz: String) {
    var open by remember { mutableStateOf(false) }
    val color = if (a.severity.equals("Extreme", true) || a.severity.equals("Severe", true)) Color(0xFFE5534B) else Color(0xFFF2A93B)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(Card)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.6f), Card)
            .clickable { open = !open }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                a.event + if (a.ends > 0) " until ${WeatherText.clock(a.ends, tz, h24)}" else "",
                color = Q.Text,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Q.TextDim)
        }
        if (open) {
            Text(a.description.ifBlank { a.headline }, color = Q.TextDim, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
            Text("From the National Weather Service", color = Q.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Rain or snow in each quarter hour of the next two hours, as bars. */
@Composable
private fun RainSoon(r: WeatherReport, accent: Color, h24: Boolean) {
    val quarters = r.upcomingQuarters
    if (quarters.isEmpty()) return
    val max = quarters.maxOf { it.second }
    Section("Next 2 hours") {
        if (max < 0.1) {
            Text("No rain or snow expected", color = Q.Text, fontSize = 15.sp)
            return@Section
        }
        val first = quarters.indexOfFirst { it.second >= 0.1 }
        Text(
            if (first <= 0) "Coming down now" else "Starting in about ${first * 15} minutes",
            color = Q.Text,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val n = quarters.size
            val gap = 6.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            // Heavy rain (4 mm in a quarter hour) fills the bar
            quarters.forEachIndexed { i, (_, mm) ->
                val f = (mm / 4.0).coerceIn(0.0, 1.0).toFloat()
                val bh = if (mm >= 0.1) (size.height * f).coerceAtLeast(4.dp.toPx()) else 2.dp.toPx()
                drawRoundRect(
                    color = if (mm >= 0.1) accent else Q.Edge,
                    topLeft = Offset(i * (bw + gap), size.height - bh),
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("Now", color = Q.TextFaint, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("+1 hr", color = Q.TextFaint, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("+2 hrs", color = Q.TextFaint, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}

/** The past two hours of radar around the place, over map lines drawn here; play it as a loop or slide through. */
@Composable
private fun RadarCard(r: WeatherReport, accent: Color) {
    val context = LocalContext.current
    val frames by produceState<List<Radar.Frame>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { Radar.frames() }.getOrDefault(emptyList()) }
    }
    var index by remember(frames) { mutableIntStateOf((frames?.size ?: 1) - 1) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(playing, frames) {
        val list = frames ?: return@LaunchedEffect
        while (playing && list.isNotEmpty()) {
            delay(if (index == list.lastIndex) 1400 else 600)
            index = (index + 1) % list.size
        }
    }
    Section("Radar") {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF14131C))) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val density = androidx.compose.ui.platform.LocalDensity.current.density
            // The tiles are drawn at their own size in dp, so a view is a few hundred km across on a phone
            val scale = density * 1.0f
            val cx = Radar.worldX(r.lon)
            val cy = Radar.worldY(r.lat)
            val left = cx - wPx / 2 / scale
            val top = cy - hPx / 2 / scale
            val frame = frames?.getOrNull(index)
            // The tiles this view needs, for the frame showing
            val tileXs = (kotlin.math.floor(left / Radar.TILE).toInt())..(kotlin.math.floor((left + wPx / scale) / Radar.TILE).toInt())
            val tileYs = (kotlin.math.floor(top / Radar.TILE).toInt())..(kotlin.math.floor((top + hPx / scale) / Radar.TILE).toInt())
            // Keyed on the tiles too: another place (or size) needs other tiles for the same frame
            val pictures by produceState<Map<Pair<Int, Int>, android.graphics.Bitmap>>(emptyMap(), frame, tileXs, tileYs) {
                val f = frame ?: return@produceState
                value = withContext(Dispatchers.IO) {
                    buildMap { for (x in tileXs) for (y in tileYs) Radar.tile(f, x, y)?.let { put(x to y, it) } }
                }
            }
            // Load every frame's tiles once in the background, so playing doesn't wait
            LaunchedEffect(frames, tileXs, tileYs) {
                val list = frames ?: return@LaunchedEffect
                withContext(Dispatchers.IO) { list.forEach { f -> for (x in tileXs) for (y in tileYs) Radar.tile(f, x, y) } }
            }
            // Reading the map lines and building the paths takes a moment: not on the main thread
            val paths by produceState<Map<Int, android.graphics.Path>>(emptyMap(), r.lat, r.lon, wPx, hPx) {
                value = withContext(Dispatchers.Default) { (0..2).associateWith { Radar.path(context, it, left, top, wPx, hPx, scale) } }
            }
            val cities by produceState<List<Radar.City>>(emptyList()) {
                value = withContext(Dispatchers.IO) { Radar.cities(context) }
            }
            Canvas(Modifier.fillMaxSize()) {
                pictures.forEach { (xy, bmp) ->
                    val dx = ((xy.first * Radar.TILE - left) * scale).toFloat()
                    val dy = ((xy.second * Radar.TILE - top) * scale).toFloat()
                    drawImage(
                        bmp.asImageBitmap(),
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bmp.width, bmp.height),
                        dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
                        dstSize = IntSize((Radar.TILE * scale).roundToInt(), (Radar.TILE * scale).roundToInt()),
                        alpha = 0.85f,
                    )
                }
                drawIntoCanvas { c ->
                    val native = c.nativeCanvas
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE }
                    listOf(2 to (0x40FFFFFF to 1f), 1 to (0x8CFFFFFF.toInt() to 1.5f), 0 to (0x8CFFFFFF.toInt() to 1.2f)).forEach { (kind, look) ->
                        paint.color = look.first
                        paint.strokeWidth = look.second * density
                        paths[kind]?.let { native.drawPath(it, paint) }
                    }
                    // Big towns nearby, by name
                    val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xB3FFFFFF.toInt()
                        textSize = 11f * density
                    }
                    cities.forEach { city ->
                        if (city.rank > 7) return@forEach
                        val x = ((Radar.worldX(city.lon) - left) * scale).toFloat()
                        val y = ((Radar.worldY(city.lat) - top) * scale).toFloat()
                        if (x in 0f..size.width && y in 0f..size.height) {
                            native.drawCircle(x, y, 2f * density, text)
                            native.drawText(city.name, x + 5 * density, y - 4 * density, text)
                        }
                    }
                }
                // You are here
                drawCircle(Color.White, 7.dp.toPx(), Offset(size.width / 2, size.height / 2))
                drawCircle(accent, 5.dp.toPx(), Offset(size.width / 2, size.height / 2))
            }
            if (frames?.isEmpty() == true) {
                Text("Radar isn't available right now", color = Q.TextDim, modifier = Modifier.align(Alignment.Center))
            }
        }
        val list = frames.orEmpty()
        if (list.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play") { playing = !playing }
                Slider(
                    value = index.toFloat(),
                    onValueChange = {
                        playing = false
                        index = it.roundToInt().coerceIn(0, list.lastIndex)
                    },
                    valueRange = 0f..list.lastIndex.toFloat().coerceAtLeast(1f),
                    steps = (list.size - 2).coerceAtLeast(0),
                    modifier = Modifier.weight(1f),
                )
                val minutes = ((System.currentTimeMillis() - list[index.coerceIn(0, list.lastIndex)].time) / 60_000).toInt()
                Text(if (index == list.lastIndex) "Now" else "-$minutes min", color = Q.Text, fontSize = 13.sp, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
            }
            Text("The past 2 hours. Radar by RainViewer; map lines by Natural Earth.", color = Q.TextFaint, fontSize = 12.sp)
        }
    }
}

/** The next day, hour by hour. */
@Composable
private fun Hourly(r: WeatherReport, unit: TempUnit, h24: Boolean) {
    Section("Hourly") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            r.upcoming.forEachIndexed { i, h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (i == 0) "Now" else WeatherText.hour(h.time, r.timezone, h24), color = Q.TextDim, fontSize = 13.sp)
                    WeatherIcon(h.icon, false, Modifier.size(40.dp))
                    Text(if (h.pop >= 10) "${h.pop}%" else " ", color = Color(0xFF7FB8FF), fontSize = 12.sp)
                    Text(WeatherText.temp(h.tempC, unit), color = Q.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** The week: each day's sky, its chance of rain or snow, and its low to high on the week's scale. */
@Composable
private fun Daily(r: WeatherReport, unit: TempUnit, accent: Color) {
    if (r.days.isEmpty()) return
    val lo = r.days.minOf { it.lowC }
    val hi = r.days.maxOf { it.highC }
    Section("7 days") {
        r.days.forEachIndexed { i, d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(WeatherText.dayName(d.date, r.timezone, today = i == 0), color = Q.Text, fontSize = 15.sp, modifier = Modifier.width(56.dp))
                WeatherIcon(d.icon, false, Modifier.size(34.dp))
                Text(if (d.pop >= 10) "${d.pop}%" else "", color = Color(0xFF7FB8FF), fontSize = 12.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                Text(WeatherText.temp(d.lowC, unit), color = Q.TextDim, fontSize = 15.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                Canvas(Modifier.weight(1f).height(8.dp).padding(horizontal = 10.dp)) {
                    val span = (hi - lo).coerceAtLeast(1.0)
                    val a = ((d.lowC - lo) / span).toFloat()
                    val b = ((d.highC - lo) / span).toFloat()
                    drawRoundRect(Q.Glass, cornerRadius = CornerRadius(size.height / 2))
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(Color(0xFF7FB8FF), accent), startX = a * size.width, endX = b * size.width),
                        topLeft = Offset(a * size.width, 0f),
                        size = Size(((b - a) * size.width).coerceAtLeast(size.height), size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                }
                Text(WeatherText.temp(d.highC, unit), color = Q.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(44.dp))
            }
        }
    }
}

/** Wind, UV, air, humidity, visibility, pressure and the sun, as tiles. */
@Composable
private fun Details(r: WeatherReport, unit: TempUnit, h24: Boolean, accent: Color) {
    val today = r.days.firstOrNull()
    val tiles = buildList<@Composable () -> Unit> {
        add {
            Tile("Wind", "wind") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(WeatherText.speed(r.windKmh, unit), color = Q.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    // The arrow points where the wind is blowing to
                    Icon(Icons.Rounded.Navigation, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp).rotate((r.windDeg + 180).toFloat()))
                    Text(WeatherText.compass(r.windDeg), color = Q.TextDim, fontSize = 15.sp)
                }
                Text("Gusts up to ${WeatherText.speed(r.gustKmh, unit)}", color = Q.TextDim, fontSize = 13.sp)
            }
        }
        add {
            Tile("UV index", "uv-index") {
                Text("${r.uv.roundToInt()} ${WeatherText.uvLabel(r.uv)}", color = Q.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("Today's peak ${(today?.uvMax ?: r.uv).roundToInt()}", color = Q.TextDim, fontSize = 13.sp)
            }
        }
        r.aqi?.let { aqi ->
            add {
                Tile("Air quality", "smoke-particles") {
                    Text("$aqi ${WeatherText.aqiLabel(aqi)}", color = Q.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(WeatherText.aqiNote(aqi) + (r.pm25?.let { " • PM2.5 ${it.roundToInt()}" } ?: ""), color = Q.TextDim, fontSize = 13.sp)
                }
            }
        }
        add {
            Tile("Humidity", "humidity") {
                Text("${r.humidity}%", color = Q.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("Dew point ${WeatherText.temp(r.dewC, unit)}", color = Q.TextDim, fontSize = 13.sp)
            }
        }
        add {
            Tile("Visibility", "horizon") {
                Text(WeatherText.distance(r.visibilityM, unit), color = Q.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(if (r.visibilityM >= 10_000) "Clear view" else if (r.visibilityM >= 3_000) "A little hazy" else "Low", color = Q.TextDim, fontSize = 13.sp)
            }
        }
        add {
            Tile("Pressure", "barometer") {
                Text("${r.pressureHpa.roundToInt()} hPa", color = Q.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(WeatherText.trend(r.pressureTrend), color = Q.TextDim, fontSize = 13.sp)
            }
        }
        if (today != null && today.sunrise > 0) {
            add {
                Tile("Sunrise", "sunrise") {
                    Text(WeatherText.clock(today.sunrise, r.timezone, h24), color = Q.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    val minutes = ((today.sunset - today.sunrise) / 60_000).toInt()
                    Text("${minutes / 60} h ${minutes % 60} min of daylight", color = Q.TextDim, fontSize = 13.sp)
                }
            }
            add {
                Tile("Sunset", "sunset") {
                    Text(WeatherText.clock(today.sunset, r.timezone, h24), color = Q.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    val tomorrow = r.days.getOrNull(1)
                    Text(tomorrow?.let { "Tomorrow ${WeatherText.clock(it.sunset, r.timezone, h24)}" } ?: "", color = Q.TextDim, fontSize = 13.sp)
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = if (maxWidth > 560.dp) 3 else 2
        Column {
            tiles.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tile -> Box(Modifier.weight(1f)) { tile() } }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Tile(title: String, icon: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .heightIn(min = 112.dp)
            .clip(Card)
            .background(Q.Glass)
            .border(1.dp, Q.Edge, Card)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(icon, false, Modifier.size(26.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, color = Q.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** Your saved places: open, rename ("Mom & Dad"), reorder, remove, or add one by city, ZIP or airport code. */
@Composable
private fun Places(current: String, onPick: (String) -> Unit, onRemoved: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<SavedPlace?>(null) }
    val unit = SettingsStore.get(context)[S.weatherUnits]

    Section("Saved places") {
        WeatherPlaces.list.forEachIndexed { i, p ->
            val r = Weather.reports[p.key]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (p.key == current) Q.GlassStrong else Color.Transparent)
                    .clickable { onPick(p.key) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.title, color = Q.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            if (p.nickname.isNotBlank()) p.label else null,
                            r?.let { WeatherText.clock(System.currentTimeMillis(), it.timezone, SettingsStore.get(context)[S.clock24h]) },
                        ).joinToString(" • "),
                        color = Q.TextDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (r != null) {
                    WeatherIcon(r.icon, false, Modifier.size(30.dp))
                    Text(WeatherText.temp(r.tempC, unit), color = Q.Text, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 6.dp))
                }
                SmallButton(Icons.Rounded.KeyboardArrowUp, "Move up", enabled = i > 0) { WeatherPlaces.move(context, p.id, -1) }
                SmallButton(Icons.Rounded.KeyboardArrowDown, "Move down", enabled = i < WeatherPlaces.list.lastIndex) { WeatherPlaces.move(context, p.id, 1) }
                SmallButton(Icons.Rounded.Edit, "Rename") { renaming = p }
                SmallButton(Icons.Rounded.Close, "Remove") {
                    WeatherPlaces.remove(context, p.id)
                    onRemoved(p.key)
                }
            }
            // Keep its weather coming while it's listed
            LaunchedEffect(p.key) { Weather.refreshPlace(context, p.key) }
        }
        if (WeatherPlaces.list.isEmpty()) {
            Text("Save a city, ZIP or airport to check it here or put it on a widget.", color = Q.TextDim, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (!adding) {
            Row(
                Modifier.clip(CircleShape).background(Q.GlassStrong).clickable { adding = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Q.Text, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add a place", color = Q.Text, fontSize = 14.sp)
            }
        } else {
            fun search() {
                if (query.isBlank()) return
                searching = true
                message = null
                scope.launch {
                    results = runCatching { Weather.search(query, context) }.getOrElse {
                        message = "Couldn't search just now"
                        emptyList()
                    }
                    if (results.isEmpty() && message == null) message = "Nothing found for \"$query\""
                    searching = false
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("City, ZIP or airport code") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                trailingIcon = { TextButton(onClick = { search() }) { Text(if (searching) "…" else "Search") } },
                modifier = Modifier.fillMaxWidth(),
            )
            message?.let { Text(it, color = Q.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            results.forEach { place ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            val saved = WeatherPlaces.add(context, place)
                            adding = false
                            query = ""
                            results = emptyList()
                            onPick(saved.key)
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                ) {
                    Text(place.label, color = Q.Text, fontSize = 15.sp)
                    if (place.note.isNotBlank()) Text(place.note, color = Q.TextDim, fontSize = 12.sp)
                }
            }
            TextButton(onClick = {
                adding = false
                results = emptyList()
                message = null
            }) { Text("Cancel") }
        }
    }

    renaming?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.nickname) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Name this place") },
            text = {
                Column {
                    Text("A name of your own, like \"Mom & Dad\" or \"Weekend cabin\". Leave it empty to use ${p.label.substringBefore(",")}.", color = Q.TextDim, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    WeatherPlaces.rename(context, p.id, name)
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SmallButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) Q.TextDim else Q.Edge, modifier = Modifier.size(20.dp))
    }
}

private fun ago(at: Long): String {
    val minutes = ((System.currentTimeMillis() - at) / 60_000).toInt()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} hr ago"
    }
}
