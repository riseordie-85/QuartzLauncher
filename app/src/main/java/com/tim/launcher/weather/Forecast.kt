package com.tim.launcher.weather

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.tim.launcher.Prefs
import com.tim.launcher.settings.TempUnit
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/** One hour of the forecast. [time] is in ms. */
data class Hour(val time: Long, val tempC: Double, val code: Int, val day: Boolean, val pop: Int) {
    val icon: String get() = WeatherCodes.icon(code, day)
}

/** One day of the forecast. [date] is the place's midnight, in ms. */
data class DayForecast(
    val date: Long,
    val code: Int,
    val highC: Double,
    val lowC: Double,
    val pop: Int,
    val sunrise: Long,
    val sunset: Long,
    val uvMax: Double,
) {
    val icon: String get() = WeatherCodes.icon(code, true)
    val condition: String get() = WeatherCodes.describe(code)
}

/** A weather warning from the US National Weather Service. */
data class Alert(val event: String, val headline: String, val severity: String, val description: String, val ends: Long)

/** Everything about the weather at one place: now, the next hours and days, the air, any warnings. */
class WeatherReport(
    /** [Weather.ME], or a saved place's key. */
    val key: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    /** When it was fetched (ms). */
    val at: Long,
    val timezone: String,
    val elevationM: Double,
    val tempC: Double,
    val feelsC: Double,
    val code: Int,
    val day: Boolean,
    val humidity: Int,
    val dewC: Double,
    val windKmh: Double,
    val windDeg: Double,
    val gustKmh: Double,
    val pressureHpa: Double,
    /** Over the last three hours: 1 rising, -1 falling, 0 steady. */
    val pressureTrend: Int,
    val visibilityM: Double,
    val uv: Double,
    /** From this hour on, about a day of them. */
    val hours: List<Hour>,
    /** From today, a week of them. */
    val days: List<DayForecast>,
    /** Rain or snow (mm) in each quarter hour of the next two hours: (time ms, amount). */
    val quarters: List<Pair<Long, Double>>,
    val aqi: Int?,
    val pm25: Double?,
    val alerts: List<Alert>,
    /** Where it came from (the "me" place records which location setting), to tell when it's out of date. */
    val source: String,
) {
    /** The hours from now on (a report kept from earlier has some behind it). */
    val upcoming: List<Hour> get() {
        val now = System.currentTimeMillis()
        return hours.filter { it.time > now - 3_600_000L }.ifEmpty { hours.takeLast(1) }
    }

    /** The quarter hours from now on. */
    val upcomingQuarters: List<Pair<Long, Double>> get() {
        val now = System.currentTimeMillis()
        return quarters.filter { it.first > now - 15 * 60_000L }
    }

    val highC: Double get() = days.firstOrNull()?.highC ?: tempC
    val lowC: Double get() = days.firstOrNull()?.lowC ?: tempC

    /** "Windy" when the wind is the news; otherwise what the sky's doing. */
    val condition: String get() = if (windKmh >= 40 && code <= 3) "Windy" else WeatherCodes.describe(code)
    val icon: String get() = if (windKmh >= 40 && code <= 3) "wind" else WeatherCodes.icon(code, day)

    fun toNow(): WeatherNow = WeatherNow(name, tempC, highC, lowC, code, day, at, source)
}

/** Turning a report into words and numbers in your units. */
object WeatherText {
    fun temp(c: Double, unit: TempUnit) = Weather.format(c, unit)

    /** The bare number, no degree sign (for tight spots). */
    fun number(c: Double, unit: TempUnit) = (if (unit == TempUnit.F) c * 9.0 / 5.0 + 32.0 else c).roundToInt().toString()

    fun speed(kmh: Double, unit: TempUnit) = if (unit == TempUnit.F) "${(kmh / 1.609).roundToInt()} mph" else "${kmh.roundToInt()} km/h"

    fun distance(m: Double, unit: TempUnit): String =
        if (unit == TempUnit.F) String.format(Locale.getDefault(), "%.1f mi", m / 1609.34) else String.format(Locale.getDefault(), "%.1f km", m / 1000.0)

    fun elevation(m: Double, unit: TempUnit): String =
        if (unit == TempUnit.F) "%,d ft".format((m * 3.281).roundToInt()) else "%,d m".format(m.roundToInt())

    fun compass(deg: Double): String = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((deg % 360) + 360) % 360 / 45.0).roundToInt() % 8]

    fun uvLabel(uv: Double) = when {
        uv < 3 -> "Low"
        uv < 6 -> "Moderate"
        uv < 8 -> "High"
        uv < 11 -> "Very high"
        else -> "Extreme"
    }

    fun aqiLabel(aqi: Int) = when {
        aqi <= 50 -> "Good"
        aqi <= 100 -> "Moderate"
        aqi <= 150 -> "Unhealthy for some"
        aqi <= 200 -> "Unhealthy"
        aqi <= 300 -> "Very unhealthy"
        else -> "Hazardous"
    }

    fun aqiNote(aqi: Int) = when {
        aqi <= 50 -> "Clean outdoor air"
        aqi <= 100 -> "Fine for most people"
        aqi <= 150 -> "Sensitive groups take it easy outside"
        else -> "Limit time outside"
    }

    fun trend(t: Int) = when (t) {
        1 -> "Rising"
        -1 -> "Falling"
        else -> "Steady"
    }

    /** A clock time at the place, like "4:38 PM" (or 16:38). */
    fun clock(ms: Long, tz: String, h24: Boolean): String =
        SimpleDateFormat(if (h24) "HH:mm" else "h:mm a", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone(tz) }.format(Date(ms))

    /** An hour at the place, like "11 PM" (or 23:00). */
    fun hour(ms: Long, tz: String, h24: Boolean): String =
        SimpleDateFormat(if (h24) "HH:00" else "h a", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone(tz) }.format(Date(ms))

    /** A day's short name at the place ("Thu"), or "Today". */
    fun dayName(ms: Long, tz: String, today: Boolean = false): String =
        if (today) "Today" else SimpleDateFormat("EEE", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone(tz) }.format(Date(ms)).trimEnd('.')

    /** How far ahead of or behind this phone a place's clock is, in words. */
    fun timeDifference(tz: String): String {
        val now = System.currentTimeMillis()
        val diff = TimeZone.getTimeZone(tz).getOffset(now) - TimeZone.getDefault().getOffset(now)
        if (diff == 0) return "Same time"
        val hours = abs(diff) / 3_600_000.0
        val text = if (hours % 1.0 == 0.0) hours.toInt().toString() else String.format(Locale.getDefault(), "%.1f", hours)
        return "$text hr${if (hours == 1.0) "" else "s"} ${if (diff > 0) "ahead" else "behind"}"
    }

    /**
     * A sentence about what's coming, from the forecast itself (no service writes these for free): rain or snow
     * starting or stopping in the next hours, else how it stays, then tomorrow.
     */
    fun summary(r: WeatherReport, unit: TempUnit, h24: Boolean): String {
        fun wet(code: Int) = code >= 51
        fun kind(code: Int) = when {
            code in 71..77 || code in 85..86 -> "Snow"
            code >= 95 -> "Storms"
            code in 51..57 -> "Drizzle"
            else -> "Rain"
        }
        val parts = ArrayList<String>()
        val soon = r.upcomingQuarters.indexOfFirst { it.second >= 0.1 }
        val nowWet = wet(r.code)
        if (!nowWet && soon in 1..7) {
            parts += "${kind(r.upcoming.firstOrNull { wet(it.code) }?.code ?: 61)} likely in about ${soon * 15} minutes."
        } else if (nowWet) {
            val stop = r.upcoming.drop(1).take(12).firstOrNull { !wet(it.code) && it.pop < 40 }
            parts += if (stop != null) "${kind(r.code)} tapering off around ${hour(stop.time, r.timezone, h24)}." else "${kind(r.code)} carrying on for a while."
        } else {
            val start = r.upcoming.drop(1).take(12).firstOrNull { wet(it.code) && it.pop >= 50 }
            parts += if (start != null) {
                "${kind(start.code)} likely from around ${hour(start.time, r.timezone, h24)}."
            } else {
                "${WeatherCodes.describe(r.code)} for the next few hours."
            }
        }
        r.days.getOrNull(1)?.let { t -> parts += "Tomorrow: ${t.condition.lowercase()}, high ${temp(t.highC, unit)}." }
        return parts.joinToString(" ")
    }

    fun parse(
        key: String,
        name: String,
        lat: Double,
        lon: Double,
        at: Long,
        source: String,
        forecast: JSONObject,
        air: JSONObject?,
        alerts: JSONArray?,
    ): WeatherReport {
        val c = forecast.getJSONObject("current")
        val h = forecast.getJSONObject("hourly")
        val d = forecast.getJSONObject("daily")
        val nowSec = c.getLong("time")

        val hTime = h.getJSONArray("time")
        val hTemp = h.getJSONArray("temperature_2m")
        val hCode = h.getJSONArray("weather_code")
        val hDay = h.getJSONArray("is_day")
        val hPop = h.optJSONArray("precipitation_probability")
        val hPressure = h.optJSONArray("pressure_msl")
        val all = (0 until hTime.length()).map { i ->
            Hour(hTime.getLong(i) * 1000, hTemp.optDouble(i, 0.0), hCode.optInt(i, 0), hDay.optInt(i, 1) == 1, hPop?.optInt(i, 0) ?: 0)
        }
        // This hour onwards
        val startIndex = (0 until hTime.length()).lastOrNull { hTime.getLong(it) <= nowSec } ?: 0
        val hours = all.drop(startIndex).take(24)
        // Pressure now against three hours ago
        val trend = if (hPressure != null && startIndex >= 3) {
            val diff = hPressure.optDouble(startIndex, 0.0) - hPressure.optDouble(startIndex - 3, 0.0)
            if (diff > 1.0) 1 else if (diff < -1.0) -1 else 0
        } else {
            0
        }

        val dTime = d.getJSONArray("time")
        val days = (0 until dTime.length()).map { i ->
            DayForecast(
                dTime.getLong(i) * 1000,
                d.getJSONArray("weather_code").optInt(i, 0),
                d.getJSONArray("temperature_2m_max").optDouble(i, 0.0),
                d.getJSONArray("temperature_2m_min").optDouble(i, 0.0),
                d.optJSONArray("precipitation_probability_max")?.optInt(i, 0) ?: 0,
                (d.optJSONArray("sunrise")?.optLong(i, 0L) ?: 0L) * 1000,
                (d.optJSONArray("sunset")?.optLong(i, 0L) ?: 0L) * 1000,
                d.optJSONArray("uv_index_max")?.optDouble(i, 0.0) ?: 0.0,
            )
        }

        val quarters = forecast.optJSONObject("minutely_15")?.let { m ->
            val t = m.getJSONArray("time")
            val p = m.getJSONArray("precipitation")
            (0 until t.length()).map { t.getLong(it) * 1000 to p.optDouble(it, 0.0) }
        } ?: emptyList()

        val aq = air?.optJSONObject("current")
        val warnings = alerts?.let { list ->
            (0 until list.length()).mapNotNull { i ->
                val p = list.getJSONObject(i).optJSONObject("properties") ?: return@mapNotNull null
                // The weather service sends null for what it doesn't know (often "ends"), and optString turns that into "null"
                fun text(k: String) = if (p.isNull(k)) "" else p.optString(k)
                Alert(
                    text("event"),
                    text("headline"),
                    text("severity"),
                    text("description").replace(Regex("\\s*\\n\\s*"), " ").trim(),
                    parseIso(text("ends").ifBlank { text("expires") }),
                )
            }
        } ?: emptyList()

        return WeatherReport(
            key = key,
            name = name,
            lat = lat,
            lon = lon,
            at = at,
            timezone = forecast.optString("timezone", TimeZone.getDefault().id),
            elevationM = forecast.optDouble("elevation", 0.0),
            tempC = c.optDouble("temperature_2m", 0.0),
            feelsC = c.optDouble("apparent_temperature", c.optDouble("temperature_2m", 0.0)),
            code = c.optInt("weather_code", 0),
            day = c.optInt("is_day", 1) == 1,
            humidity = c.optInt("relative_humidity_2m", 0),
            dewC = c.optDouble("dew_point_2m", 0.0),
            windKmh = c.optDouble("wind_speed_10m", 0.0),
            windDeg = c.optDouble("wind_direction_10m", 0.0),
            gustKmh = c.optDouble("wind_gusts_10m", 0.0),
            pressureHpa = c.optDouble("pressure_msl", 0.0),
            pressureTrend = trend,
            visibilityM = c.optDouble("visibility", 0.0),
            uv = c.optDouble("uv_index", 0.0),
            hours = hours,
            days = days,
            quarters = quarters,
            aqi = aq?.takeIf { it.has("us_aqi") && !it.isNull("us_aqi") }?.optInt("us_aqi"),
            pm25 = aq?.takeIf { it.has("pm2_5") && !it.isNull("pm2_5") }?.optDouble("pm2_5"),
            alerts = warnings,
            source = source,
        )
    }

    private fun parseIso(s: String): Long = runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrDefault(0L)
}

/** A place you've saved for weather, with your own name for it ("Mom & Dad"). */
data class SavedPlace(val id: String, val nickname: String, val label: String, val lat: Double, val lon: Double, val country: String) {
    val key: String get() = "place:$id"

    /** The name shown: yours, or the town's. */
    val title: String get() = nickname.ifBlank { label.substringBefore(",") }
}

/** Your saved places, kept on this phone. */
object WeatherPlaces {
    val list = mutableStateListOf<SavedPlace>()
    private var read = false

    fun load(context: Context) {
        if (read) return
        read = true
        val text = Prefs.get(context).getString(Prefs.WEATHER_PLACES, null) ?: return
        runCatching {
            val a = JSONArray(text)
            list.clear()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                list += SavedPlace(o.getString("id"), o.optString("nick"), o.getString("label"), o.getDouble("lat"), o.getDouble("lon"), o.optString("country"))
            }
        }
    }

    private fun save(context: Context) {
        val a = JSONArray()
        list.forEach { p ->
            a.put(JSONObject().put("id", p.id).put("nick", p.nickname).put("label", p.label).put("lat", p.lat).put("lon", p.lon).put("country", p.country))
        }
        Prefs.get(context).edit().putString(Prefs.WEATHER_PLACES, a.toString()).apply()
    }

    fun add(context: Context, place: Place): SavedPlace {
        load(context)
        list.firstOrNull { abs(it.lat - place.lat) < 0.01 && abs(it.lon - place.lon) < 0.01 }?.let { return it }
        val saved = SavedPlace(System.currentTimeMillis().toString(36), "", place.label, place.lat, place.lon, place.countryCode)
        list += saved
        save(context)
        return saved
    }

    fun rename(context: Context, id: String, nickname: String) {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        list[i] = list[i].copy(nickname = nickname.trim().take(40))
        save(context)
    }

    fun remove(context: Context, id: String) {
        list.removeAll { it.id == id }
        save(context)
    }

    fun move(context: Context, id: String, by: Int) {
        val i = list.indexOfFirst { it.id == id }
        val j = i + by
        if (i < 0 || j !in list.indices) return
        val p = list.removeAt(i)
        list.add(j, p)
        save(context)
    }

    fun byKey(key: String): SavedPlace? = list.firstOrNull { it.key == key }
}

/** Airports by code (DEN, AUS), from a public list kept in the app: searching them never leaves the phone. */
object Airports {
    data class Airport(val code: String, val lat: Double, val lon: Double, val name: String, val city: String, val country: String)

    @Volatile
    private var all: List<Airport>? = null

    fun load(context: Context): List<Airport> = all ?: synchronized(this) {
        all ?: runCatching {
            context.assets.open("weathermap/airports.tsv").bufferedReader().readLines().mapNotNull { line ->
                val f = line.split('\t')
                if (f.size < 6) null else Airport(f[0], f[1].toDouble(), f[2].toDouble(), f[3], f[4], f[5])
            }
        }.getOrDefault(emptyList()).also { all = it }
    }

    fun byCode(context: Context, code: String): Airport? = load(context).firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }

    /** The nearest big airport's code, if one is within about 120 km. */
    fun nearest(context: Context, lat: Double, lon: Double): String? {
        val cos = kotlin.math.cos(Math.toRadians(lat))
        val best = load(context).minByOrNull { a ->
            val dy = a.lat - lat
            val dx = (a.lon - lon) * cos
            dx * dx + dy * dy
        } ?: return null
        val dy = best.lat - lat
        val dx = (best.lon - lon) * cos
        return if (kotlin.math.sqrt(dx * dx + dy * dy) * 111.0 <= 120.0) best.code else null
    }
}
