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
import androidx.compose.runtime.mutableStateMapOf
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

/** A place found by name, ZIP or airport code. */
data class Place(
    val name: String,
    val region: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    val countryCode: String = "",
    /** What else to show in a search list (an airport's full name, a postal code). */
    val note: String = "",
) {
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

    /** Your own weather's key: wherever the settings point (this phone's location or your city). */
    const val ME = "me"

    /** The full weather for every place asked about, by key ([ME] or a saved place's key). */
    val reports = mutableStateMapOf<String, WeatherReport>()

    /** Places being fetched right now. */
    val fetching = mutableStateMapOf<String, Boolean>()

    private val jobs = HashMap<String, Job>()
    private var reportsRead = false

    private fun reportsFile(context: Context) = java.io.File(context.filesDir, "weather_reports.json")

    /** Raw answers per place, kept so the page and widgets show the last weather at once. */
    private val raw = java.util.concurrent.ConcurrentHashMap<String, JSONObject>()

    private fun loadReports(context: Context) {
        if (reportsRead) return
        reportsRead = true
        runCatching {
            val all = JSONObject(reportsFile(context).readText())
            all.keys().forEach { k ->
                val o = all.getJSONObject(k)
                raw[k] = o
                rebuild(k, o)?.let { reports[k] = it }
            }
        }
    }

    private fun rebuild(key: String, o: JSONObject): WeatherReport? = runCatching {
        WeatherText.parse(
            key, o.getString("name"), o.getDouble("lat"), o.getDouble("lon"), o.getLong("at"), o.optString("source"),
            o.getJSONObject("forecast"), o.optJSONObject("air"), o.optJSONArray("alerts"),
        )
    }.onFailure { Diag.log("Weather: couldn't read saved $key: ${it.message}") }.getOrNull()

    private fun saveReports(context: Context) {
        val all = JSONObject()
        // Only places still in use: you, and the places you've saved
        val keep = setOf(ME) + WeatherPlaces.list.map { it.key }
        raw.keys.filter { it !in keep }.forEach { raw.remove(it) }
        reports.keys.filter { it !in keep }.forEach { reports.remove(it) }
        raw.forEach { (k, v) -> all.put(k, v) }
        runCatching { reportsFile(context).writeText(all.toString()) }
    }

    /** The weather at [key], or null before it's been fetched. Call [refresh] to keep it current. */
    fun report(context: Context, key: String): WeatherReport? {
        loadReports(context.applicationContext)
        return reports[key]
    }

    /** Fetches the weather at a saved place if what's kept is old ([force]: anyway). [ME] goes through [refresh]. */
    fun refreshPlace(context: Context, key: String, force: Boolean = false) {
        val app = context.applicationContext
        if (key == ME) {
            refresh(app, force)
            return
        }
        loadReports(app)
        WeatherPlaces.load(app)
        val place = WeatherPlaces.byKey(key) ?: return
        if (jobs[key]?.isActive == true) return
        val kept = reports[key]
        if (!force && kept != null && System.currentTimeMillis() - kept.at < STALE_MS) return
        if (!force && !mayTry(key)) return
        jobs[key] = scope.launch {
            fetching[key] = true
            try {
                val report = withContext(Dispatchers.IO) {
                    fetchReport(app, key, place.title, place.lat, place.lon, "place", place.country)
                }
                reports[key] = report
                saveReports(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Diag.log("Weather for $key: ${e.javaClass.simpleName} ${e.message}")
            } finally {
                fetching.remove(key)
            }
        }
    }

    /**
     * Everything for one place: Open-Meteo's forecast and air quality, and in the US the National Weather Service's
     * warnings. Only the rounded location is sent; nothing identifies you.
     */
    private fun fetchReport(context: Context, key: String, name: String, lat: Double, lon: Double, source: String, country: String): WeatherReport {
        val at = System.currentTimeMillis()
        val base = "https://api.open-meteo.com/v1/forecast?latitude=${fmt(lat)}&longitude=${fmt(lon)}" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max" +
            "&timezone=auto&timeformat=unixtime"
        // Everything, the quarter hours and a trimmed set of hours included
        val full = base +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,weather_code,is_day," +
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m,pressure_msl,visibility,uv_index" +
            "&hourly=temperature_2m,weather_code,is_day,precipitation_probability,pressure_msl" +
            "&minutely_15=precipitation&forecast_minutely_15=8" +
            "&past_hours=3&forecast_hours=24&forecast_days=7"
        // The plainest way to ask for the same (the extras taken from the hours instead), if the first is turned down
        val plain = base +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day," +
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m,pressure_msl" +
            "&hourly=temperature_2m,weather_code,is_day,precipitation_probability,pressure_msl,dew_point_2m,visibility,uv_index" +
            "&forecast_days=7"
        val forecast = try {
            JSONObject(get(full)).also { WeatherText.parse(key, name, lat, lon, at, source, it, null, null) }
        } catch (e: Exception) {
            Diag.log("Weather: the full forecast didn't work (${e.javaClass.simpleName} ${e.message}); asking plainly")
            JSONObject(get(plain))
        }
        val air = runCatching {
            JSONObject(get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${fmt(lat)}&longitude=${fmt(lon)}&current=us_aqi,pm2_5"))
        }.getOrNull()
        // Warnings: the National Weather Service covers the US (its states and territories) only
        val inUs = country.equals("US", true) || (country.isBlank() && lat in 17.0..72.0 && lon in -180.0..-64.0)
        val alerts = if (inUs && SettingsStore.get(context)[S.weatherAlerts]) {
            runCatching {
                JSONObject(
                    get(
                        "https://api.weather.gov/alerts/active?point=${fmt(lat)},${fmt(lon)}",
                        mapOf("Accept" to "application/geo+json", "User-Agent" to "QuartzLauncher (github.com/riseordie-85/QuartzLauncher)"),
                    ),
                ).optJSONArray("features")
            }.getOrNull()
        } else {
            null
        }
        val o = JSONObject().put("name", name).put("lat", lat).put("lon", lon).put("at", at).put("source", source).put("forecast", forecast)
        if (air != null) o.put("air", air)
        if (alerts != null) o.put("alerts", alerts)
        raw[key] = o
        return WeatherText.parse(key, name, lat, lon, at, source, forecast, air, alerts)
    }

    private const val STALE_MS = 25 * 60_000L

    /**
     * When each place was last asked about (main thread only). Every weather widget asks as it comes into view, so
     * while the weather can't be had (offline, no location) it's asked again at most every two minutes, not each time.
     */
    private val tried = HashMap<String, Long>()

    private fun mayTry(key: String): Boolean {
        val now = System.currentTimeMillis()
        if (now - (tried[key] ?: 0L) < 2 * 60_000L) return false
        tried[key] = now
        return true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var cacheRead = false

    /** The last weather fetched, from before Quartz was last closed. */
    fun loadCache(context: Context) {
        if (cacheRead) return
        cacheRead = true
        loadReports(context.applicationContext)
        WeatherPlaces.load(context.applicationContext)
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
        val full = reports[ME]
        val fresh = shown != null && shown.source == sourceKey(store) && System.currentTimeMillis() - shown.at < STALE_MS &&
            full != null && full.source == shown.source
        if (!force && fresh) return
        if (!force && !mayTry(ME)) return
        job = scope.launch {
            loading = true
            fetching[ME] = true
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
                problem = "Couldn't get the weather just now (${e.message?.take(80) ?: e.javaClass.simpleName}). It'll try again soon."
                Diag.log("Weather: ${e.javaClass.simpleName} ${e.message}")
            } finally {
                // A request dropped for a newer one finishes late; it mustn't say the newer one is done
                if (job == null || job === coroutineContext[Job]) {
                    loading = false
                    fetching.remove(ME)
                }
            }
        }
    }

    /** Clears what's shown (the location changed), then fetches for the new place. */
    fun locationChanged(context: Context) {
        // A request still on its way is for the old place: drop it, or it would block this one
        job?.cancel()
        job = null
        now = null
        reports.remove(ME)
        raw.remove(ME)
        problem = null
        Prefs.get(context).edit().remove(Prefs.WEATHER).apply()
        refresh(context, force = true)
    }

    private suspend fun fetch(context: Context, store: SettingsStore): Pair<WeatherNow?, String?> {
        val source = sourceKey(store)
        var issue: String? = null
        var spot: Triple<Double, Double, String>? = null
        // The chosen city's country, unless this phone's own location is used (then it's told by where that is)
        var country = store[S.weatherCountry]
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
                    country = ""
                }
            }
            // Fall back on a city you've chosen before
            if (spot == null) cityOf(store)?.let { spot = it }
        } else {
            spot = cityOf(store)
            if (spot == null) issue = "Choose a city in Settings › Widgets."
        }
        val (lat, lon, name) = spot ?: return null to issue
        val report = fetchReport(context, ME, name, lat, lon, source, country)
        withContext(Dispatchers.Main) {
            // Not if the settings have moved on to another place while this was on its way
            if (report.source == sourceKey(store)) {
                reports[ME] = report
                saveReports(context)
            }
        }
        val weather = report.toNow()
        Diag.log("Weather for $name: ${weather.condition}, ${weather.tempC.roundToInt()}°C")
        return weather to issue
    }

    private fun cityOf(store: SettingsStore): Triple<Double, Double, String>? {
        val lat = store[S.weatherLat].toDoubleOrNull() ?: return null
        val lon = store[S.weatherLon].toDoubleOrNull() ?: return null
        return Triple(lat, lon, store[S.weatherCity].substringBefore(",").ifBlank { "Weather" })
    }

    /**
     * Places matching [query]: a city, a ZIP or postal code, or an airport code. Airports are looked up on the phone;
     * the rest asks Open-Meteo's place search with only what you typed.
     */
    suspend fun search(query: String, context: Context? = null): List<Place> = withContext(Dispatchers.IO) {
        val text = query.trim()
        val airport = if (context != null && text.length == 3 && text.all { it.isLetter() }) Airports.byCode(context, text) else null
        val fromAirport = airport?.let {
            listOf(Place(it.city.ifBlank { it.name }, it.code, it.country, round2(it.lat), round2(it.lon), it.country, it.name))
        } ?: emptyList()
        val q = URLEncoder.encode(text, "UTF-8")
        val lang = Locale.getDefault().language.ifBlank { "en" }
        val found = runCatching {
            val json = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=$lang&format=json"))
            val results = json.optJSONArray("results")
            if (results == null) {
                emptyList()
            } else {
                (0 until results.length()).map { i ->
                    val o = results.getJSONObject(i)
                    val postcodes = o.optJSONArray("postcodes")
                    val zip = if (postcodes != null && (0 until postcodes.length()).any { postcodes.optString(it) == text }) text else ""
                    Place(
                        o.optString("name"), o.optString("admin1"), o.optString("country"),
                        round2(o.getDouble("latitude")), round2(o.getDouble("longitude")),
                        o.optString("country_code"), zip,
                    )
                }
            }
        }.getOrElse { if (fromAirport.isEmpty()) throw it else emptyList() }
        fromAirport + found
    }

    /** Remembers a chosen city and fetches its weather. */
    fun chooseCity(context: Context, place: Place) {
        val store = SettingsStore.get(context)
        store.set(S.weatherCity, value = place.label)
        store.set(S.weatherLat, value = fmt(place.lat))
        store.set(S.weatherLon, value = fmt(place.lon))
        store.set(S.weatherCountry, value = place.countryCode)
        locationChanged(context)
    }

    /**
     * Whether your own weather has nowhere to come from yet: no city chosen, or this phone's location asked for but
     * not allowed (with no city to fall back on).
     */
    fun needsSetup(context: Context): Boolean {
        val store = SettingsStore.get(context)
        val city = store[S.weatherLat].isNotBlank() && store[S.weatherLon].isNotBlank()
        return when (store[S.weatherSource]) {
            WeatherSource.CITY -> !city
            WeatherSource.DEVICE -> !hasLocationPermission(context) && !city
        }
    }

    fun hasLocationPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun format(c: Double, unit: TempUnit): String =
        (if (unit == TempUnit.F) c * 9.0 / 5.0 + 32.0 else c).roundToInt().toString() + "°"

    internal fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "QuartzLauncher")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            if (code !in 200..299) {
                // Open-Meteo says why in its answer ("reason")
                val why = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                    ?.let { runCatching { JSONObject(it).optString("reason") }.getOrNull() ?: it.take(200) }
                error("HTTP $code${if (why.isNullOrBlank()) "" else ": $why"}")
            }
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
