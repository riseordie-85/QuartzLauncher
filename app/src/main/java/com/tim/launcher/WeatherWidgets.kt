package com.tim.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tim.launcher.settings.TempUnit
import com.tim.launcher.weather.DayForecast
import com.tim.launcher.weather.Hour
import com.tim.launcher.weather.Weather
import com.tim.launcher.weather.WeatherPlaces
import com.tim.launcher.weather.WeatherReport
import com.tim.launcher.weather.WeatherText
import org.json.JSONArray
import org.json.JSONObject

/**
 * The weather widgets. Each fills its cell on your widget panel: temperatures in the accent, words in the panel's
 * text color, light and bold weights of the widget font. [big] lets the icon spill past the panel's edge.
 */
@Composable
internal fun WeatherWidget(
    kind: WidgetKind,
    r: WeatherReport?,
    r2: WeatherReport?,
    name: String,
    name2: String,
    now: Long,
    w: Dp,
    h: Dp,
    ink: Color,
    paper: Color,
    accent: Color,
    motion: Boolean,
    unit: TempUnit,
    big: Boolean,
    hours: String,
    minutes: String,
) {
    val light = FontWeight.Light
    val bold = FontWeight.Bold
    val dim = ink.copy(alpha = ink.alpha * 0.8f)
    fun t(c: Double?) = c?.let { WeatherText.temp(it, unit) } ?: "--°"
    val icon = r?.icon ?: "not-available"
    val loHi = if (r != null) "${t(r.lowC)}/${t(r.highC)}" else "--/--"
    // Plan units for a size, like the other widgets
    fun plan(pw: Float, ph: Float): (Float) -> Dp {
        val s = minOf(w.value / pw, h.value / ph)
        return { v -> (v * s).dp }
    }
    // An icon centered at (cx, cy), spilling past the panel when it's big
    @Composable
    fun Spill(cx: Dp, cy: Dp, size: Dp) = WeatherIcon(icon, motion, Modifier.at(cx - size / 2, cy - size / 2, size, size))

    when (kind) {
        // ---- One day ----

        WidgetKind.W_TEMP -> {
            val u = plan(190f, 135f)
            WidgetCard(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(horizontal = u(10f)), contentAlignment = Alignment.Center) { WText(t(r?.tempC), u(84f), accent, bold) }
            }
        }

        WidgetKind.W_ICON -> {
            val u = plan(190f, 135f)
            WidgetCard(Modifier.fillMaxSize())
            Spill(w / 2, h / 2, if (big) u(170f) else u(118f))
        }

        WidgetKind.W_HILO -> {
            val u = plan(190f, 135f)
            WidgetCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    WText(t(r?.lowC), u(48f), ink, light, line = 0.95f)
                    WText(t(r?.highC), u(48f), ink, light, line = 0.95f)
                }
            }
        }

        WidgetKind.W_TALL -> {
            val u = plan(190f, 290f)
            WidgetCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(bottom = u(18f)), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    WText(t(r?.tempC), u(66f), accent, bold)
                    WText(loHi, u(30f), ink, light)
                }
            }
            Spill(w / 2, h * 0.32f, if (big) u(190f) else u(130f))
        }

        WidgetKind.W_SMALL -> {
            val u = plan(400f, 180f)
            WidgetCard(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().padding(end = u(24f)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.End,
                ) {
                    WText(t(r?.tempC), u(92f), accent, bold, line = 0.95f)
                    WText(loHi, u(44f), ink, light)
                }
            }
            Spill(u(105f), h / 2, if (big) u(200f) else u(130f))
        }

        WidgetKind.W_CARD -> {
            val u = plan(400f, 400f)
            WidgetCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().fillMaxHeight(0.26f).padding(horizontal = u(22f)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WText(loHi, u(40f), ink, light, Modifier.weight(1f))
                        WText(t(r?.tempC), u(78f), accent, bold)
                    }
                    Box(Modifier.fillMaxWidth().weight(1f).background(paper), contentAlignment = Alignment.Center) {
                        WeatherIcon(icon, motion, Modifier.size(u(250f)))
                    }
                }
            }
        }

        WidgetKind.W_BIG -> {
            val u = plan(400f, 400f)
            WidgetCard(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(bottom = u(24f), start = u(16f), end = u(16f)), contentAlignment = Alignment.BottomCenter) {
                    val text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = light, color = ink)) {
                            append("L${r?.let { WeatherText.number(it.lowC, unit) } ?: "--"} H${r?.let { WeatherText.number(it.highC, unit) } ?: "--"} ")
                        }
                        withStyle(SpanStyle(fontWeight = bold, color = accent)) { append("C${r?.let { WeatherText.number(it.tempC, unit) } ?: "--"}") }
                    }
                    WText(text, u(48f), ink, light)
                }
            }
            Spill(w / 2, h * 0.42f, if (big) u(330f) else u(270f))
        }

        WidgetKind.W_FLIP_SMALL -> {
            val u = Plan(w, h, 400f, 400f)
            WidgetCard(Modifier.fillMaxSize())
            val cardW = u(165f)
            val cardH = u(185f)
            FlipCard(hours, paper, motion, Modifier.at(w / 2 - u(8f) - cardW, u.top + u(22f), cardW, cardH))
            FlipCard(minutes, paper, motion, Modifier.at(w / 2 + u(8f), u.top + u(22f), cardW, cardH))
            Slot(u(26f), u.top + u(250f), u(120f), u(130f), Alignment.CenterStart) {
                Column {
                    WText(r?.let { WeatherText.number(it.lowC, unit) } ?: "--", u(42f), ink, light, line = 0.95f)
                    WText(r?.let { WeatherText.number(it.highC, unit) } ?: "--", u(42f), ink, light, line = 0.95f)
                }
            }
            Spill(w / 2, u.top + u(240f), if (big) u(220f) else u(170f))
            Slot(w - u(170f), u.top + u(250f), u(150f), u(130f), Alignment.CenterEnd) {
                WText(r?.let { WeatherText.number(it.tempC, unit) } ?: "--", u(100f), ink, light)
            }
        }

        WidgetKind.WEATHER -> {
            val u = plan(826f, 135f)
            WidgetCard(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxSize().padding(start = u(150f), end = u(26f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WText(name, u(48f), ink, bold, Modifier.weight(1f))
                    Column(Modifier.padding(horizontal = u(16f))) {
                        WText(t(r?.lowC), u(36f), ink, light, line = 0.95f)
                        WText(t(r?.highC), u(36f), ink, light, line = 0.95f)
                    }
                    WText(t(r?.tempC), u(104f), accent, bold)
                }
            }
            Spill(u(80f), h / 2, if (big) u(190f) else u(110f))
        }

        WidgetKind.W_WIND -> {
            val u = plan(400f, 180f)
            WidgetCard(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize().padding(horizontal = u(24f)), verticalAlignment = Alignment.CenterVertically) {
                    // The arrow points where the wind is blowing to
                    Icon(
                        Icons.Rounded.Navigation,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(u(100f)).rotate(((r?.windDeg ?: 0.0) + 180).toFloat()),
                    )
                    Spacer(Modifier.width(u(16f)))
                    Column(Modifier.weight(1f)) {
                        WText(r?.let { WeatherText.speed(it.windKmh, unit) } ?: "--", u(58f), ink, bold)
                        WText(r?.let { "${WeatherText.compass(it.windDeg)} • gusts ${WeatherText.speed(it.gustKmh, unit)}" } ?: "Wind", u(28f), ink, light)
                    }
                }
            }
        }

        WidgetKind.W_SUN -> {
            val u = plan(400f, 180f)
            val today = r?.days?.firstOrNull()
            val h24 = false
            WidgetCard(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize().padding(horizontal = u(14f)), verticalAlignment = Alignment.CenterVertically) {
                    listOf("sunrise" to today?.sunrise, "sunset" to today?.sunset).forEach { (what, at) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            WeatherIcon(what, motion, Modifier.size(u(84f)))
                            WText(if (at != null && at > 0 && r != null) WeatherText.clock(at, r.timezone, h24) else "--", u(38f), ink, bold)
                        }
                    }
                }
            }
        }

        WidgetKind.W_DETAILS -> {
            val u = plan(400f, 400f)
            val cells = listOf(
                "Feels like" to t(r?.feelsC),
                "Wind" to (r?.let { "${WeatherText.speed(it.windKmh, unit).substringBefore(' ')} ${WeatherText.compass(it.windDeg)}" } ?: "--"),
                "Humidity" to (r?.let { "${it.humidity}%" } ?: "--"),
                (if (r?.aqi != null) "Air" else "UV") to (r?.let { it.aqi?.toString() ?: it.uv.toInt().toString() } ?: "--"),
            )
            WidgetCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(u(18f))) {
                    cells.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            row.forEach { (label, value) ->
                                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                                    WText(label, u(28f), dim, light)
                                    WText(value, u(56f), if (label == "Feels like") accent else ink, bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- Forecasts ----

        WidgetKind.W_FORECAST_ROW -> {
            val u = plan(826f, 135f)
            WidgetCard(Modifier.fillMaxSize()) {
                DaysRow(r, Modifier.fillMaxSize().padding(horizontal = u(16f), vertical = u(8f)), u(28f), u(52f), u(30f), ink, motion, unit, 5)
            }
        }

        WidgetKind.W_HOURLY -> {
            val u = plan(826f, 135f)
            WidgetCard(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize().padding(horizontal = u(16f), vertical = u(8f)), verticalAlignment = Alignment.CenterVertically) {
                    (r?.upcoming ?: emptyList<Hour>()).take(6).forEachIndexed { i, hr ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            WText(if (i == 0) "Now" else WeatherText.hour(hr.time, r!!.timezone, false), u(26f), ink, light)
                            WeatherIcon(hr.icon, motion, Modifier.size(u(52f)))
                            WText(t(hr.tempC), u(30f), ink, bold)
                        }
                    }
                }
            }
        }

        WidgetKind.W_FLIP_FORECAST -> {
            val u = Plan(w, h, 1000f, 680f)
            WidgetCard(Modifier.fillMaxSize())
            val cardW = u(210f)
            val cardH = u(225f)
            FlipCard(hours, paper, motion, Modifier.at(w - u(50f) - cardW * 2 - u(24f), u.top + u(32f), cardW, cardH))
            FlipCard(minutes, paper, motion, Modifier.at(w - u(50f) - cardW, u.top + u(32f), cardW, cardH))
            Spill(u(240f), u.top + u(190f), if (big) u(380f) else u(310f))
            val d = DateParts(now)
            Slot(w / 2, u.top + u(272f), w / 2 - u(50f), u(120f), Alignment.TopEnd) {
                Column(horizontalAlignment = Alignment.End) {
                    WText("${d.weekdayShort.uppercase()} ${d.numeric}", u(52f), ink, bold, line = 1.05f)
                    WText(name.uppercase(), u(52f), ink, bold, line = 1.05f)
                }
            }
            Slot(u(40f), u.top + u(410f), w - u(80f), u(250f), Alignment.Center) {
                DaysRow(r, Modifier.fillMaxSize(), u(52f), u(96f), u(50f), ink, motion, unit, 5)
            }
        }

        WidgetKind.W_FORECAST -> {
            val u = plan(1000f, 1000f)
            WidgetCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(horizontal = u(34f), vertical = u(26f))) {
                    Row(Modifier.fillMaxWidth().height(u(320f))) {
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                WText(t(r?.tempC), u(104f), accent, bold)
                                Spacer(Modifier.width(u(40f)))
                                WText(loHi, u(50f), ink, bold, Modifier.padding(bottom = u(14f)))
                            }
                            WText(name, u(52f), ink, bold)
                            WText(r?.condition ?: "Weather", u(52f), ink, bold)
                        }
                        WeatherIcon(icon, motion, Modifier.size(u(320f)))
                    }
                    val days = r?.days?.drop(1)?.take(5) ?: emptyList()
                    days.forEachIndexed { i, day ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(u(4f)).background(ink.copy(alpha = ink.alpha * 0.6f)))
                        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            WText(WeatherText.dayName(day.date, r!!.timezone).uppercase(), u(86f), ink, bold, Modifier.width(u(270f)))
                            WeatherIcon(day.icon, motion, Modifier.size(u(80f)))
                            WText(day.condition, u(40f), ink, bold, Modifier.weight(1f).padding(horizontal = u(24f)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                WText(t(day.highC), u(50f), ink, bold, line = 0.95f)
                                WText(t(day.lowC), u(50f), ink, light, line = 0.95f)
                            }
                        }
                    }
                }
            }
        }

        // ---- Two places ----

        WidgetKind.W_DUAL -> {
            val u = plan(826f, 135f)
            WidgetCard(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    listOf(r to name, r2 to name2).forEachIndexed { i, (report, title) ->
                        if (i > 0) Box(Modifier.width(u(3f)).fillMaxHeight(0.6f).background(ink.copy(alpha = ink.alpha * 0.5f)))
                        Row(Modifier.weight(1f).padding(horizontal = u(18f)), verticalAlignment = Alignment.CenterVertically) {
                            WeatherIcon(report?.icon ?: "not-available", motion, Modifier.size(u(92f)))
                            Column(Modifier.weight(1f).padding(horizontal = u(10f))) {
                                WText(title, u(34f), ink, bold)
                                WText(report?.let { "${t(it.lowC)}/${t(it.highC)}" } ?: "Pick a place", u(26f), ink, light)
                            }
                            WText(t(report?.tempC), u(70f), accent, bold)
                        }
                    }
                }
            }
        }

        WidgetKind.W_DUAL_TALL -> {
            val u = plan(1000f, 680f)
            WidgetCard(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize().padding(vertical = u(26f))) {
                    listOf(r to name, r2 to name2).forEachIndexed { i, (report, title) ->
                        if (i > 0) Box(Modifier.width(u(4f)).fillMaxHeight().background(ink.copy(alpha = ink.alpha * 0.5f)))
                        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = u(30f))) {
                            WText(title, u(50f), ink, bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WeatherIcon(report?.icon ?: "not-available", motion, Modifier.size(u(170f)))
                                Column(Modifier.padding(start = u(16f))) {
                                    WText(t(report?.tempC), u(104f), accent, bold)
                                    WText(report?.let { "${t(it.lowC)}/${t(it.highC)}" } ?: "Pick a place", u(38f), ink, light)
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            DaysRow(report, Modifier.fillMaxWidth().height(u(200f)), u(34f), u(70f), u(36f), ink, motion, unit, 3)
                        }
                    }
                }
            }
        }

        else -> Unit
    }
}

/** The next [count] days (from tomorrow): name, icon, high. */
@Composable
private fun DaysRow(r: WeatherReport?, modifier: Modifier, nameSize: Dp, iconSize: Dp, tempSize: Dp, ink: Color, motion: Boolean, unit: TempUnit, count: Int) {
    val days: List<DayForecast> = r?.days?.drop(1)?.take(count) ?: emptyList()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        days.forEach { d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                WText(WeatherText.dayName(d.date, r!!.timezone).uppercase(), nameSize, ink, FontWeight.Bold)
                WeatherIcon(d.icon, motion, Modifier.size(iconSize))
                WText(WeatherText.number(d.highC, unit), tempSize, ink, FontWeight.Bold)
            }
        }
        if (days.isEmpty()) WText("Forecast coming", nameSize, ink, FontWeight.Light, Modifier.weight(1f))
    }
}

/** What a place is called on a widget: your name for it, its town, or "Pick a place". */
internal fun weatherName(key: String, report: WeatherReport?): String = when {
    key == Weather.ME || key.isEmpty() -> report?.name ?: Weather.now?.place ?: "Weather"
    else -> WeatherPlaces.byKey(key)?.title ?: report?.name ?: "Pick a place"
}

/** Made-up weather for the widget pictures in pickers and setup. */
internal val SampleReport: WeatherReport by lazy {
    val day = 86_400_000L
    val start = System.currentTimeMillis() / day * day
    val forecast = JSONObject()
        .put("timezone", java.util.TimeZone.getDefault().id)
        .put("elevation", 1609.0)
        .put(
            "current",
            JSONObject().put("time", System.currentTimeMillis() / 1000).put("temperature_2m", 7.0).put("apparent_temperature", 4.0)
                .put("weather_code", 2).put("is_day", 1).put("relative_humidity_2m", 41).put("wind_speed_10m", 18.0)
                .put("wind_direction_10m", 300.0).put("wind_gusts_10m", 32.0).put("uv_index", 3.0),
        )
        .put(
            "hourly",
            JSONObject()
                .put("time", JSONArray((0 until 8).map { (System.currentTimeMillis() / 1000) + it * 3600 }))
                .put("temperature_2m", JSONArray(listOf(7.0, 8.0, 8.5, 7.5, 6.0, 4.0, 3.0, 2.0)))
                .put("weather_code", JSONArray(listOf(2, 2, 3, 3, 61, 61, 3, 1)))
                .put("is_day", JSONArray(listOf(1, 1, 1, 1, 0, 0, 0, 0))),
        )
        .put(
            "daily",
            JSONObject()
                .put("time", JSONArray((0 until 7).map { (start + it * day) / 1000 }))
                .put("weather_code", JSONArray(listOf(2, 0, 61, 3, 71, 0, 1)))
                .put("temperature_2m_max", JSONArray(listOf(8.0, 11.0, 6.0, 4.0, -1.0, 5.0, 9.0)))
                .put("temperature_2m_min", JSONArray(listOf(-2.0, 0.0, 1.0, -3.0, -8.0, -5.0, -1.0)))
                .put("sunrise", JSONArray((0 until 7).map { (start + it * day + 7 * 3_600_000L) / 1000 }))
                .put("sunset", JSONArray((0 until 7).map { (start + it * day + 17 * 3_600_000L) / 1000 })),
        )
    WeatherText.parse(Weather.ME, "Denver", 39.74, -104.99, System.currentTimeMillis(), "", forecast, null, null)
}
