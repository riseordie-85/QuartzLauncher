package com.tim.launcher.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import com.tim.launcher.Diag
import org.json.JSONObject
import java.io.DataInputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/**
 * Rain radar for the weather page: the past two hours from RainViewer's free radar (personal use; future frames
 * and close zoom ended there in 2026), over map lines drawn on the phone from Natural Earth (public domain), so no
 * map service sees where you're looking. RainViewer sees which map tiles are asked for (a rough area), nothing more.
 */
object Radar {
    /** Zoom level for the radar tiles: RainViewer's closest now (7) is about a metro area across. */
    const val ZOOM = 7
    const val TILE = 256

    /** One radar picture in time: its time (ms) and where its tiles live. */
    data class Frame(val time: Long, val base: String)

    /** The radar's frames, oldest first, every 20 minutes over the last two hours (fewer asks of the service). */
    fun frames(): List<Frame> {
        val json = JSONObject(Weather.get("https://api.rainviewer.com/public/weather-maps.json"))
        val host = json.getString("host")
        val past = json.getJSONObject("radar").getJSONArray("past")
        val all = (0 until past.length()).map { i ->
            val f = past.getJSONObject(i)
            Frame(f.getLong("time") * 1000, host + f.getString("path"))
        }
        // Every other one, always ending on the newest
        return all.reversed().filterIndexed { i, _ -> i % 2 == 0 }.reversed()
    }

    private val tiles = ConcurrentHashMap<String, Bitmap>()

    /** One radar tile (256 px, the "universal blue" colors, smoothed, with snow shown), kept for this session. */
    fun tile(frame: Frame, x: Int, y: Int): Bitmap? {
        val n = 1 shl ZOOM
        val wx = ((x % n) + n) % n
        val key = "${frame.time}/$wx/$y"
        tiles[key]?.let { return it }
        if (y !in 0 until n) return null
        return runCatching {
            val url = java.net.URL("${frame.base}/$TILE/$ZOOM/$wx/$y/2/1_1.png")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "QuartzLauncher")
            try {
                if (conn.responseCode !in 200..299) null else conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                conn.disconnect()
            }
        }.onFailure { Diag.log("Radar tile: ${it.message}") }.getOrNull()?.also { tiles[key] = it }
    }

    // ---- Where on the map ----

    /** Longitude to x on the whole world at [ZOOM], in pixels (Web Mercator, as map tiles use). */
    fun worldX(lon: Double): Double = (lon + 180.0) / 360.0 * TILE * (1 shl ZOOM)

    fun worldY(lat: Double): Double {
        val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        return (1.0 - ln(tan(r) + 1.0 / kotlin.math.cos(r)) / PI) / 2.0 * TILE * (1 shl ZOOM)
    }

    // ---- Map lines, from the app's own copy ----

    /** A line on the map: 0 coast or lake shore, 1 country border, 2 state or province line. */
    class Line(val kind: Int, val lat: FloatArray, val lon: FloatArray, val minLat: Float, val maxLat: Float, val minLon: Float, val maxLon: Float)

    data class City(val name: String, val lat: Double, val lon: Double, val rank: Int)

    @Volatile
    private var lines: List<Line>? = null

    @Volatile
    private var cities: List<City>? = null

    fun lines(context: Context): List<Line> = lines ?: synchronized(this) {
        lines ?: runCatching {
            DataInputStream(context.assets.open("weathermap/lines.bin").buffered()).use { input ->
                val count = input.readInt()
                List(count) {
                    val kind = input.readByte().toInt()
                    val n = input.readInt()
                    val lat = FloatArray(n)
                    val lon = FloatArray(n)
                    for (i in 0 until n) {
                        lat[i] = input.readFloat()
                        lon[i] = input.readFloat()
                    }
                    Line(kind, lat, lon, lat.minOrNull() ?: 0f, lat.maxOrNull() ?: 0f, lon.minOrNull() ?: 0f, lon.maxOrNull() ?: 0f)
                }
            }
        }.onFailure { Diag.log("Map lines: ${it.message}") }.getOrDefault(emptyList()).also { lines = it }
    }

    fun cities(context: Context): List<City> = cities ?: synchronized(this) {
        cities ?: runCatching {
            context.assets.open("weathermap/cities.tsv").bufferedReader().readLines().mapNotNull { line ->
                val f = line.split('\t')
                if (f.size < 4) null else City(f[0], f[1].toDouble(), f[2].toDouble(), f[3].toInt())
            }
        }.getOrDefault(emptyList()).also { cities = it }
    }

    /**
     * The map lines of [kind] inside a view [w] × [h] px whose top left is at world pixel ([left], [top]) at
     * [ZOOM], drawn [scale] times bigger, as one path.
     */
    fun path(context: Context, kind: Int, left: Double, top: Double, w: Float, h: Float, scale: Float): Path {
        val path = Path()
        val world = (TILE * (1 shl ZOOM)).toDouble()
        // The view's corners as latitudes and longitudes, a little wider so lines run off the edge
        val lonA = (left - w / scale * 0.2) / world * 360.0 - 180.0
        val lonB = (left + w / scale * 1.2) / world * 360.0 - 180.0
        fun latAt(y: Double): Double = Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(PI * (1 - 2 * y / world))))
        val latA = latAt(top + h / scale * 1.2)
        val latB = latAt(top - h / scale * 0.2)
        lines(context).forEach { line ->
            if (line.kind != kind || line.maxLon < lonA || line.minLon > lonB || line.maxLat < latA || line.minLat > latB) return@forEach
            for (i in line.lat.indices) {
                val x = ((worldX(line.lon[i].toDouble()) - left) * scale).toFloat()
                val y = ((worldY(line.lat[i].toDouble()) - top) * scale).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        return path
    }
}
