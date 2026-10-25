package com.tim.launcher.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tim.launcher.Diag
import com.tim.launcher.Prefs
import com.tim.launcher.settings.S
import com.tim.launcher.settings.SettingsStore
import com.tim.launcher.settings.TempUnit
import com.tim.launcher.settings.WeatherSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** The weather right now where you are, in °C (shown in your unit). */
data class WeatherNow(
    val place: String,
    val tempC: Double,
    val highC: Double,
    val lowC: Double,
    /** WMO weather code, as Open-Meteo reports it. */
    val code: Int,
    val day: Boolean,
    /** When it was fetched (ms). */
    val at: Long,
    /** Which location it's for, so changing city doesn't keep showing the old one. */
    val source: String,
) {
    val condition: String get() = WeatherCodes.describe(code)
    val icon: String get() = WeatherCodes.icon(code, day)
}

/** A place found by name. */
data class Place(val name: String, val region: String, val country: String, val lat: Double, val lon: Double) {
    val label: String get() = listOf(name, region, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

/**
 * Weather for the widgets, from Open-Meteo (free, no account or key, no tracking). Only asked while a weather widget
 * is on your home screen, at most every half hour, and only with a rough location (rounded to about 1 km): a city
 * you chose, or this phone's coarse location if you'd rather. The last result is kept so widgets show it at once.
 */
object Weather {
    var now by mutableStateOf<WeatherNow?>(null)
        private set

    /** What's stopping the weather from updating, in words, or null when it's fine. */
    var problem by mutableStateOf<String?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    private const val STALE_MS = 25 * 60_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var cacheRead = false

    /** The last weather fetched, from before Quartz was last closed. */
    fun loadCache(context: Context) {
        if (cacheRead) return
        cacheRead = true
        val text = Prefs.get(context).getString(Prefs.WEATHER, null) ?: return
        now = runCatching {
            val o = JSONObject(text)
            WeatherNow(
                o.getString("place"), o.getDouble("t"), o.getDouble("hi"), o.getDouble("lo"), o.getInt("code"),
                o.getBoolean("day"), o.getLong("at"), o.optString("source"),
            )
        }.getOrNull()
    }

    private fun saveCache(context: Context, w: WeatherNow) {
        val o = JSONObject()
            .put("place", w.place).put("t", w.tempC).put("hi", w.highC).put("lo", w.lowC).put("code", w.code)
            .put("day", w.day).put("at", w.at).put("source", w.source)
        Prefs.get(context).edit().putString(Prefs.WEATHER, o.toString()).apply()
    }

    /** Which location the settings point at right now. */
    private fun sourceKey(store: SettingsStore): String = when (store[S.weatherSource]) {
        WeatherSource.DEVICE -> "device"
        WeatherSource.CITY -> "city:${store[S.weatherLat]},${store[S.weatherLon]}"
    }

    /** Fetches new weather if what's shown is old or for another place ([force]: fetch anyway). */
    fun refresh(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        loadCache(app)
        if (job?.isActive == true) return
        val store = SettingsStore.get(app)
        val shown = now
        if (!force && shown != null && shown.source == sourceKey(store) && System.currentTimeMillis() - shown.at < STALE_MS) return
        job = scope.launch {
            loading = true
            try {
                val (weather, issue) = withContext(Dispatchers.IO) { fetch(app, store) }
                // Settings may have moved on to another place while this was on its way
                if (weather == null || weather.source == sourceKey(store)) {
                    problem = issue
                    if (weather != null) {
                        now = weather
                        saveCache(app, weather)
                    }
                }
            } catch (e: CancellationException) {
                throw e // replaced by a newer request: not a failure
            } catch (e: Exception) {
                problem = "Couldn't reach the weather service. It'll try again soon."
                Diag.log("Weather: ${e.javaClass.simpleName} ${e.message}")
            } finally {
                // A request dropped for a newer one finishes late; it mustn't say the newer one is done
                if (job == null || job === coroutineContext[Job]) loading = false
            }
        }
    }

    /** Clears what's shown (the location changed), then fetches for the new place. */
    fun locationChanged(context: Context) {
        // A request still on its way is for the old place: drop it, or it would block this one
        job?.cancel()
        job = null
        now = null
        problem = null
        Prefs.get(context).edit().remove(Prefs.WEATHER).apply()
        refresh(context, force = true)
    }

    private suspend fun fetch(context: Context, store: SettingsStore): Pair<WeatherNow?, String?> {
        val source = sourceKey(store)
        var issue: String? = null
        var spot: Triple<Double, Double, String>? = null
        if (store[S.weatherSource] == WeatherSource.DEVICE) {
            if (!hasLocationPermission(context)) {
                issue = "Location is off for Quartz. Allow it in Settings › Widgets, or choose a city."
            } else {
                val loc = deviceLocation(context)
                if (loc == null) {
                    issue = "This phone didn't share a location just now."
                } else {
                    val lat = round2(loc.latitude)
                    val lon = round2(loc.longitude)
                    spot = Triple(lat, lon, placeName(context, lat, lon) ?: "My location")
                }
            }
            // Fall back on a city you've chosen before
            if (spot == null) cityOf(store)?.let { spot = it }
        } else {
            spot = cityOf(store)
            if (spot == null) issue = "Choose a city in Settings › Widgets."
        }
        val (lat, lon, name) = spot ?: return null to issue
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${fmt(lat)}&longitude=${fmt(lon)}" +
            "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=1"
        val json = JSONObject(get(url))
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val weather = WeatherNow(
            place = name,
            tempC = current.getDouble("temperature_2m"),
            highC = daily.getJSONArray("temperature_2m_max").getDouble(0),
            lowC = daily.getJSONArray("temperature_2m_min").getDouble(0),
            code = current.getInt("weather_code"),
            day = current.optInt("is_day", 1) == 1,
            at = System.currentTimeMillis(),
            source = source,
        )
        Diag.log("Weather for $name: ${weather.condition}, ${weather.tempC.roundToInt()}°C")
        return weather to issue
    }

    private fun cityOf(store: SettingsStore): Triple<Double, Double, String>? {
        val lat = store[S.weatherLat].toDoubleOrNull() ?: return null
        val lon = store[S.weatherLon].toDoubleOrNull() ?: return null
        return Triple(lat, lon, store[S.weatherCity].substringBefore(",").ifBlank { "Weather" })
    }

    /** Places matching [query], for choosing a city. Asks Open-Meteo's place search with only what you typed. */
    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val lang = Locale.getDefault().language.ifBlank { "en" }
        val json = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=$lang&format=json"))
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        (0 until results.length()).map { i ->
            val o = results.getJSONObject(i)
            Place(
                o.optString("name"), o.optString("admin1"), o.optString("country"),
                round2(o.getDouble("latitude")), round2(o.getDouble("longitude")),
            )
        }
    }

    /** Remembers a chosen city and fetches its weather. */
    fun chooseCity(context: Context, place: Place) {
        val store = SettingsStore.get(context)
        store.set(S.weatherCity, value = place.label)
        store.set(S.weatherLat, value = fmt(place.lat))
        store.set(S.weatherLon, value = fmt(place.lon))
        locationChanged(context)
    }

    fun hasLocationPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun format(c: Double, unit: TempUnit): String =
        (if (unit == TempUnit.F) c * 9.0 / 5.0 + 32.0 else c).roundToInt().toString() + "°"

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "QuartzLauncher")
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun round2(v: Double) = (v * 100.0).roundToInt() / 100.0
    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)

    /** A recent coarse location, or a fresh one (waiting up to 15 seconds). */
    @SuppressLint("MissingPermission") // checked by the caller
    private suspend fun deviceLocation(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val enabled = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        val recent = enabled.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (recent != null && System.currentTimeMillis() - recent.time < 2 * 60 * 60_000L) return recent
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { it in enabled } ?: return recent
        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
        return fresh ?: recent
    }

    /** The town at a rounded location, from Android's own place lookup, or null. */
    private suspend fun placeName(context: Context, lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        return withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine<String?> { cont ->
                runCatching {
                    Geocoder(context).getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            val a = addresses.firstOrNull()
                            if (cont.isActive) cont.resume(a?.locality ?: a?.subAdminArea ?: a?.adminArea)
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
    }
}

/** What Open-Meteo's weather codes (WMO) mean, and which Meteocons icon shows each. */
object WeatherCodes {
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71, 73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80, 81 -> "Showers"
        82 -> "Heavy showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorms"
        96, 99 -> "Storms and hail"
        else -> "Weather"
    }

    /** The icon's file name in assets/weather. */
    fun icon(code: Int, day: Boolean): String {
        val dn = if (day) "day" else "night"
        return when (code) {
            0 -> "clear-$dn"
            1 -> "mostly-clear-$dn"
            2 -> "partly-cloudy-$dn"
            3 -> "overcast-$dn"
            45, 48 -> "fog-$dn"
            51, 53, 55 -> "drizzle"
            56, 57, 66, 67 -> "sleet"
            61, 63 -> "rain"
            65, 82 -> "extreme-rain"
            71, 73, 77 -> "snow"
            75 -> "extreme-snow"
            80, 81 -> "partly-cloudy-$dn-rain"
            85, 86 -> "partly-cloudy-$dn-snow"
            95 -> "thunderstorms-$dn-rain"
            96, 99 -> "thunderstorms-hail"
            else -> "not-available"
        }
    }
}
