package com.tim.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tim.launcher.settings.IconBackground
import com.tim.launcher.settings.IconShape
import com.tim.launcher.settings.IconStyle
import com.tim.launcher.settings.S
import com.tim.launcher.settings.SettingsStore
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Where an icon is shown. Each place can use its own icon pack. */
enum class IconSlot { HOME, DOCK, DRAWER }

/** Which place the icons being drawn belong to; the dock and drawer set this for everything inside them. */
val LocalIconSlot = staticCompositionLocalOf { IconSlot.HOME }

/**
 * Draws app icons the way you've styled them: from an icon pack, cut to a shape, minimal (one-color) or not, on a
 * light, dark or colored background. Icons are drawn in the background and kept, so the home screen never waits;
 * until one is ready the app's own icon shows.
 */
object IconRender {

    data class Spec(
        val pack: String,
        val shape: IconShape,
        val force: Boolean,
        val style: IconStyle,
        val background: IconBackground,
        val color: Int,
    ) {
        /** Nothing to change: the app's own icon is used as it is. */
        val plain get() = pack.isEmpty() && shape == IconShape.SYSTEM && style == IconStyle.DEFAULT && background == IconBackground.NONE
        val key get() = "$pack|$shape|$force|$style|$background|$color"
    }

    fun specFor(store: SettingsStore, slot: IconSlot): Spec {
        val pack = when {
            store[S.iconsMatch] || slot == IconSlot.HOME -> store[S.iconPackHome]
            slot == IconSlot.DOCK -> store[S.iconPackDock]
            else -> store[S.iconPackDrawer]
        }
        return Spec(pack, store[S.iconShape], store[S.iconForceShape], store[S.iconStyle], store[S.iconBackground], store[S.iconBgColor])
    }

    /** Bumped when newly drawn icons are ready, so icons on screen swap in. */
    var version by mutableIntStateOf(0)
        private set

    private const val SIZE = 160
    private val cache = object : LruCache<String, ImageBitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val pending = HashSet<String>()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "quartz-icons").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val main = Handler(Looper.getMainLooper())

    /** The styled icon for [app], or null while it's being drawn (or when there's nothing to change). */
    fun icon(context: Context, app: AppEntry, spec: Spec): ImageBitmap? {
        if (spec.plain) return null
        val key = spec.key + "|" + app.key
        cache.get(key)?.let { return it }
        synchronized(pending) {
            if (!pending.add(key)) return null
        }
        val appContext = context.applicationContext
        worker.execute {
            val bmp = runCatching { render(appContext, app, spec) }
                .onFailure { Diag.log("Icon for ${app.label} couldn't be drawn: ${it.message}") }
                .getOrNull()
            // Nothing to draw, or it failed: keep the app's own icon, so it isn't tried again on every redraw
            cache.put(key, bmp?.asImageBitmap() ?: app.icon)
            synchronized(pending) { pending.remove(key) }
            if (bmp != null) main.post { version++ }
        }
        return null
    }

    /** An app was updated or removed: redraw everything if it was an icon pack in use, else just its own icons. */
    fun packageChanged(pkg: String?) {
        if (pkg == null) return
        if (IconPacks.isLoaded(pkg)) clear()
        // Otherwise the app's own icon may have changed: drop what was drawn from the old one
        else cache.snapshot().keys.filter { it.contains("|$pkg/") }.forEach { cache.remove(it) }
    }

    /** Throws away drawn icons (a pack changed). */
    fun clear() {
        cache.evictAll()
        IconPacks.clear()
        main.post { version++ }
    }

    // ---- Drawing ----

    private fun render(context: Context, app: AppEntry, spec: Spec): Bitmap? {
        val density = context.resources.displayMetrics.densityDpi
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val info = launcherApps?.resolveActivity(Intent().setComponent(app.component), Process.myUserHandle())
        val own: Drawable? = info?.getIcon(density)
        val theme = IconPacks.theme(context, spec.pack)
        val packIcon = theme?.let { IconPacks.iconFor(it, app.component) }
        // Nothing to draw from (the app was just removed): leave its own icon showing
        if (own == null && packIcon == null) return null

        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val s = SIZE.toFloat()
        val shape = path(if (spec.shape == IconShape.SYSTEM) IconShape.SQUIRCLE else spec.shape, s)
        val plate = plateColor(spec)

        if (spec.style == IconStyle.MINIMAL) {
            // One color: Android 13's monochrome icon when the app has one, otherwise a grey version of its icon
            if (plate != null) fillShape(canvas, shape, plate)
            val ink = if (plate != null && isLight(plate)) 0xFF1E1D24.toInt() else 0xFFFFFFFF.toInt()
            val mono = (own as? AdaptiveIconDrawable)?.monochrome?.mutate()
            if (mono != null) {
                mono.colorFilter = PorterDuffColorFilter(ink, PorterDuff.Mode.SRC_IN)
                drawLayer(canvas, mono, s, adaptive = true)
            } else {
                val icon = (packIcon ?: own)?.mutate() ?: return out
                icon.colorFilter = ColorMatrixColorFilter(minimalMatrix(isLight(plate ?: 0xFF000000.toInt())))
                drawInset(canvas, icon, s, if (plate != null) 0.62f else 0.8f)
                icon.colorFilter = null
            }
            return out
        }

        // An icon from the pack, as the pack drew it
        if (packIcon != null && !spec.force && plate == null) {
            packIcon.setBounds(0, 0, SIZE, SIZE)
            packIcon.draw(canvas)
            return out
        }
        // The pack doesn't have this app: dress the app's icon in the pack's frame, if it has one
        if (theme != null && packIcon == null && !spec.force && plate == null && (theme.back.isNotEmpty() || theme.mask != null)) {
            if (own != null) packFrame(canvas, theme, own, app, s)
            return out
        }
        // Left as the app drew it
        if (spec.shape == IconShape.SYSTEM && !spec.force && plate == null) {
            val icon = packIcon ?: own ?: return out
            icon.setBounds(0, 0, SIZE, SIZE)
            icon.draw(canvas)
            return out
        }

        // Cut to your shape, on your background if you picked one. The shape goes down first and the icon is drawn
        // into it, so the edge comes out smooth (a clipPath edge is jagged)
        fillShape(canvas, shape, 0xFF000000.toInt())
        val layer = canvas.saveLayer(null, cutPaint)
        val adaptive = own as? AdaptiveIconDrawable
        when {
            packIcon != null -> {
                fillShape(canvas, shape, plate ?: 0xFFF4F4F6.toInt())
                drawInset(canvas, packIcon, s, 0.78f)
            }
            adaptive != null -> {
                if (plate != null) canvas.drawColor(plate) else adaptive.background?.let { drawLayer(canvas, it, s, adaptive = true) }
                adaptive.foreground?.let { drawLayer(canvas, it, s, adaptive = true) }
            }
            own != null -> {
                // An old-style icon with its own outline: sit it on a plate so it fills the shape neatly
                canvas.drawColor(plate ?: 0xFFF4F4F6.toInt())
                drawInset(canvas, own, s, 0.7f)
            }
        }
        canvas.restoreToCount(layer)
        return out
    }

    private fun plateColor(spec: Spec): Int? = when (spec.background) {
        IconBackground.NONE -> null
        IconBackground.LIGHT -> 0xFFF4F4F6.toInt()
        IconBackground.DARK -> 0xFF1E1D24.toInt()
        IconBackground.COLOR -> spec.color or 0xFF000000.toInt()
    }

    private fun isLight(c: Int): Boolean {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0 > 0.6
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Keeps only what lands inside the shape already drawn underneath. */
    private val cutPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }

    private fun fillShape(canvas: Canvas, shape: Path, color: Int) {
        fill.color = color
        canvas.drawPath(shape, fill)
    }

    /** An adaptive icon layer is drawn half again as big as the icon, so its middle part fills the shape. */
    private fun drawLayer(canvas: Canvas, d: Drawable, s: Float, adaptive: Boolean) {
        val extra = if (adaptive) s * 0.25f else 0f
        d.setBounds((-extra).toInt(), (-extra).toInt(), (s + extra).toInt(), (s + extra).toInt())
        d.draw(canvas)
    }

    private fun drawInset(canvas: Canvas, d: Drawable, s: Float, scale: Float) {
        val inset = s * (1f - scale) / 2f
        d.setBounds(inset.toInt(), inset.toInt(), (s - inset).toInt(), (s - inset).toInt())
        d.draw(canvas)
    }

    /** Grey, then pushed toward white (or dark, on a light background), for the minimal style. */
    private fun minimalMatrix(lightPlate: Boolean): ColorMatrix {
        val m = ColorMatrix().apply { setSaturation(0f) }
        val shift = if (lightPlate) -60f else 60f
        m.postConcat(ColorMatrix(floatArrayOf(1.1f, 0f, 0f, 0f, shift, 0f, 1.1f, 0f, 0f, shift, 0f, 0f, 1.1f, 0f, shift, 0f, 0f, 0f, 1f, 0f)))
        return m
    }

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    /** The classic icon pack treatment: a back plate, the app's icon shrunk, cut by a mask, with a frame on top. */
    private fun packFrame(canvas: Canvas, theme: IconPacks.Theme, own: Drawable, app: AppEntry, s: Float) {
        if (theme.back.isNotEmpty()) {
            val pick = theme.back[Math.floorMod(app.key.hashCode(), theme.back.size)]
            IconPacks.drawable(theme, pick)?.let { it.setBounds(0, 0, SIZE, SIZE); it.draw(canvas) }
        }
        val layer = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val lc = Canvas(layer)
        drawInset(lc, own, s, theme.scale.coerceIn(0.3f, 1f))
        IconPacks.drawable(theme, theme.mask)?.let { mask ->
            val mb = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            mask.setBounds(0, 0, SIZE, SIZE)
            mask.draw(Canvas(mb))
            lc.drawBitmap(mb, 0f, 0f, maskPaint)
            mb.recycle()
        }
        canvas.drawBitmap(layer, 0f, 0f, null)
        layer.recycle()
        IconPacks.drawable(theme, theme.upon)?.let { it.setBounds(0, 0, SIZE, SIZE); it.draw(canvas) }
    }

    // ---- Shapes ----

    /** The outline of an icon shape, [s] pixels square. Shared with the settings preview. */
    fun path(shape: IconShape, s: Float): Path {
        val p = Path()
        val r = RectF(0f, 0f, s, s)
        when (shape) {
            IconShape.CIRCLE -> p.addCircle(s / 2f, s / 2f, s / 2f, Path.Direction.CW)
            IconShape.ROUNDED -> p.addRoundRect(r, s * 0.24f, s * 0.24f, Path.Direction.CW)
            IconShape.SQUARE -> p.addRoundRect(r, s * 0.06f, s * 0.06f, Path.Direction.CW)
            IconShape.TEARDROP -> {
                val big = s * 0.5f
                val small = s * 0.12f
                p.addRoundRect(r, floatArrayOf(big, big, big, big, small, small, big, big), Path.Direction.CW)
            }
            IconShape.HEXAGON -> {
                // A soft hexagon, points at the top and bottom
                for (i in 0 until 6) {
                    val a = PI / 3 * i - PI / 2
                    val x = (s / 2f + s / 2f * cos(a)).toFloat()
                    val y = (s / 2f + s / 2f * sin(a)).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            IconShape.SQUIRCLE, IconShape.SYSTEM -> {
                // A superellipse: between a circle and a rounded square
                val n = 4.0
                val steps = 64
                for (i in 0..steps) {
                    val t = 2 * PI * i / steps
                    val c = cos(t)
                    val si = sin(t)
                    val x = (s / 2f + s / 2f * (abs(c).pow(2.0 / n) * sign(c))).toFloat()
                    val y = (s / 2f + s / 2f * (abs(si).pow(2.0 / n) * sign(si))).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
        }
        return p
    }
}
