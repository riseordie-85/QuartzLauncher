package com.tim.launcher.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.tim.launcher.Diag
import com.tim.launcher.Prefs
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Wallpaper memory shared by everything in the Quartz process: decoded images and small blurred copies for the
 * glass. Samsung starts a new wallpaper engine on every fold and unfold, so keeping these here (not in the engine)
 * lets each new engine draw straight away.
 */
object WallCache {

    /** Bumped whenever a new blurred copy is ready or the wallpaper changes, so the home screen redraws its glass. */
    var generation by mutableIntStateOf(0)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "quartz-wallpaper").apply { priority = Thread.NORM_PRIORITY - 1 } }

    // ---- Images ----

    /** Longest side a saved wallpaper image keeps; plenty for a 2448 px screen, and about 20 MB in memory. */
    const val MAX_IMAGE = 2560

    private val images = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** A saved wallpaper image, decoded once and then shared. Null if the file is missing. */
    fun image(context: Context, name: String): Bitmap? {
        images.get(name)?.let { return it }
        synchronized(images) {
            images.get(name)?.let { return it }
            val file = WallStore.imageFile(context, name)
            if (!file.exists()) return null
            val bmp = runCatching {
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            }.getOrNull() ?: return null
            images.put(name, bmp)
            return bmp
        }
    }

    /**
     * Copies a picked photo into Quartz's private folder, scaled down to [MAX_IMAGE] and turned upright. It never
     * leaves the phone. Calls back on the main thread with the new file's name, or null if it couldn't be read.
     */
    fun importImage(context: Context, uri: Uri, done: (String?) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val name = runCatching {
                val source = ImageDecoder.createSource(app.contentResolver, uri)
                val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val longest = max(info.size.width, info.size.height)
                    if (longest > MAX_IMAGE) {
                        val s = MAX_IMAGE.toFloat() / longest
                        decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                val name = "img_${System.currentTimeMillis()}.jpg"
                FileOutputStream(WallStore.imageFile(app, name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                images.put(name, if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false) ?: bmp)
                Diag.log("Wallpaper image saved: ${bmp.width}×${bmp.height}")
                name
            }.onFailure { Diag.log("Couldn't read that image: ${it.message}") }.getOrNull()
            main.post { done(name) }
        }
    }

    // ---- Glass ----

    /** A small copy of one screen's wallpaper, blurred for glass, and how bright it is (0 dark – 1 light). */
    /**
     * A blurred copy of what's behind the panels: [bitmap] covers a [screenW]×[screenH] area whose top left sits at
     * [left], [top] in the window (the whole screen, for the home screen; just the sample strip, for a settings
     * preview).
     */
    class Glass(
        val bitmap: Bitmap,
        val screenW: Int,
        val screenH: Int,
        val brightness: Float,
        val blurred: Boolean,
        val left: Float = 0f,
        val top: Float = 0f,
    )

    private val glass = LinkedHashMap<String, Glass>()
    private val latest = HashMap<String, Glass>()
    private val pending = HashSet<String>()
    private val wanted = HashMap<String, String>()

    /**
     * The wallpaper of a [w]×[h] screen, small and blurred by [blur] (0–1). If it isn't ready yet it's made in the
     * background (and [generation] changes when it's done); meanwhile the last one made for this screen size is
     * returned, so the glass never flashes empty while you change settings.
     */
    fun glass(context: Context, w: Int, h: Int, blur: Float): Glass? {
        if (w <= 0 || h <= 0) return null
        val app = context.applicationContext
        val frame = WallRenderer.frameFor(app, w, h)
        val level = (blur.coerceIn(0f, 1f) * 50).roundToInt()
        val size = "${w}x$h"
        val key = "$size|$level|${frame.config.toJson()}|${frame.mode}|${frame.from}|${frame.aspect}"
        synchronized(this) {
            glass[key]?.let { return it }
            wanted[size] = key
            if (pending.add(key)) {
                worker.execute {
                    // Dragging a slider asks for a new copy on every step: skip the ones that are already out of date
                    val stale = synchronized(this) { wanted[size] != key }
                    val made = if (stale) {
                        null
                    } else {
                        runCatching { make(app, frame, w, h, level) }
                            .onFailure { Diag.log("Glass couldn't be made: ${it.message}") }
                            .getOrNull()
                    }
                    synchronized(this) {
                        pending.remove(key)
                        if (made != null) {
                            glass[key] = made
                            latest[size] = made
                            while (glass.size > 6) glass.remove(glass.keys.first())
                        }
                    }
                    // Only when there's something new: a failed copy that bumped this would be asked for again
                    // straight away, over and over
                    if (made != null) main.post { generation++ }
                }
            }
            return latest[size]
        }
    }

    private fun make(context: Context, frame: Frame, w: Int, h: Int, level: Int): Glass {
        val bmp = WallRenderer.renderSmall(w, h, 0.25f, frame) { image(context, it) }
        val brightness = WallRenderer.brightness(bmp)
        // Level 0 still makes a small copy: the home screen uses its brightness to pick label colors
        if (level > 0) WallRenderer.blur(bmp, 2 + level * 22 / 50)
        return Glass(bmp, w, h, brightness, blurred = level > 0)
    }

    /** Call after saving a wallpaper, so the home screen and glass pick it up. */
    fun changed() {
        main.post { generation++ }
    }

    /** A small, unblurred preview of what a [w]×[h] screen shows, for settings. Slow: call it off the main thread. */
    fun preview(context: Context, w: Int, h: Int, longest: Int = 360): Bitmap {
        val app = context.applicationContext
        val frame = WallRenderer.frameFor(app, w, h)
        val scale = longest.toFloat() / max(w, h)
        return WallRenderer.renderSmall(w, h, scale, frame) { image(app, it) }
    }

    // ---- Is Quartz the wallpaper? ----

    fun isActive(context: Context): Boolean = runCatching {
        WallpaperManager.getInstance(context).wallpaperInfo?.packageName == context.packageName
    }.getOrDefault(false)

    /** Opens Android's live wallpaper preview for Quartz, where you tap "Set wallpaper". */
    fun openPicker(context: Context): Boolean {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, QuartzWallpaperService::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }
            .onFailure { Diag.log("Live wallpaper picker didn't open: ${it.message}") }
            .isSuccess
    }

    /** Remembers each screen's size the first time the wallpaper sees it, so the cover can match the unfolded one. */
    fun rememberSize(context: Context, kind: ScreenKind, w: Int, h: Int) {
        val (kw, kh) = when (kind) {
            ScreenKind.INNER -> Prefs.INNER_W to Prefs.INNER_H
            ScreenKind.COVER -> Prefs.COVER_W to Prefs.COVER_H
            ScreenKind.SINGLE -> return
        }
        // Saved in one orientation (unfolded: wide; cover: tall), so turning the phone doesn't move the cover's half
        val long = maxOf(w, h)
        val short = minOf(w, h)
        val (sw, sh) = if (kind == ScreenKind.INNER) long to short else short to long
        val prefs = Prefs.get(context)
        if (prefs.getInt(kw, 0) != sw || prefs.getInt(kh, 0) != sh) {
            prefs.edit().putInt(kw, sw).putInt(kh, sh).apply()
            // The cover's half of the unfolded wallpaper just changed shape: redo its glass and previews too
            changed()
        }
    }
}
