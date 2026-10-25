package com.tim.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import com.tim.launcher.wallpaper.WallRenderer
import com.tim.launcher.wallpaper.PatternStyle
import com.tim.launcher.settings.IconShadow
import com.tim.launcher.settings.DockEdge
import android.graphics.BlurMaskFilter
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.LinearGradient
import com.tim.launcher.settings.GradientDirection
import android.graphics.RadialGradient
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tim.launcher.settings.IconBackground
import com.tim.launcher.settings.IconShape
import com.tim.launcher.settings.IconStyle
import com.tim.launcher.settings.S
import com.tim.launcher.settings.SettingsStore
import com.tim.launcher.settings.Setting
import com.tim.launcher.settings.orHome
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
        val color2: Int = 0,
        val pattern: PatternStyle = PatternStyle.NONE,
        val patternStrength: Float = 0.3f,
        val edge: DockEdge = DockEdge.NONE,
        val shadow: IconShadow = IconShadow.NONE,
        val patternSize: Float = 0.5f,
        val gradient: GradientDirection = GradientDirection.DIAGONAL,
    ) {
        /** Nothing to change: the app's own icon is used as it is. */
        val plain get() = pack.isEmpty() && shape == IconShape.SYSTEM && style == IconStyle.DEFAULT &&
            background == IconBackground.NONE && edge == DockEdge.NONE && shadow == IconShadow.NONE
        val key get() = "$pack|$shape|$force|$style|$background|$color|$color2|$pattern|$patternStrength|$edge|$shadow|$patternSize|$gradient"

        /** How much bigger than the icon its picture is drawn, to leave room for the shadow. */
        val overscan get() = if (shadow == IconShadow.NONE) 1f else OVERSCAN
    }

    /** Room around an icon for its shadow: the picture is this much bigger than the icon. */
    const val OVERSCAN = 1.25f

    /** Where icon settings are read from: the saved settings, or changes in settings that haven't been applied yet. */
    interface Values {
        fun <T> get(setting: Setting<T>): T

        /** A dock or drawer value, or the home screen's while that place has no value of its own. */
        fun <T> place(own: Setting<T>?, home: Setting<T>): T
    }

    private class Saved(private val store: SettingsStore) : Values {
        override fun <T> get(setting: Setting<T>): T = store[setting]
        override fun <T> place(own: Setting<T>?, home: Setting<T>): T = store.orHome(own, home)
    }

    fun specFor(store: SettingsStore, slot: IconSlot): Spec = specFor(Saved(store), slot)

    fun specFor(v: Values, slot: IconSlot): Spec {
        val match = v.get(S.iconsMatch)
        val pack = when {
            match || slot == IconSlot.HOME -> v.get(S.iconPackHome)
            slot == IconSlot.DOCK -> v.get(S.iconPackDock)
            else -> v.get(S.iconPackDrawer)
        }
        // Dock and drawer have their own shape and style when icons don't match everywhere
        val place = when {
            match || slot == IconSlot.HOME -> null
            slot == IconSlot.DOCK -> S.iconsDock
            else -> S.iconsDrawer
        }
        return Spec(
            pack,
            v.place(place?.shape, S.iconShape),
            v.place(place?.force, S.iconForceShape),
            v.place(place?.style, S.iconStyle),
            v.place(place?.background, S.iconBackground),
            v.place(place?.color, S.iconBgColor),
            v.place(place?.color2, S.iconBgColor2),
            v.place(place?.pattern, S.iconPattern),
            v.place(place?.patternStrength, S.iconPatternStrength),
            v.place(place?.edge, S.iconEdge),
            v.place(place?.shadow, S.iconShadow),
            patternSize = v.place(place?.patternSize, S.iconPatternSize),
            gradient = v.place(place?.gradient, S.iconGradient),
        )
    }

    /**
     * Bumped when newly drawn icons are ready, so icons still waiting for theirs swap in. Only icons that are
     * waiting watch it: an icon already showing its drawing doesn't redraw every time another one is ready
     * (that made scrolling the drawer stutter on slower devices while new icons were being drawn).
     */
    var version by mutableIntStateOf(0)
        private set

    /** Bumped when drawn icons are thrown away (a pack changed or an app updated), so every icon looks again. */
    var epoch by mutableIntStateOf(0)
        private set

    private val specStates = HashMap<IconSlot, State<Spec>>()

    /**
     * The style for [slot], worked out once and shared by every icon in that place (rather than each icon reading
     * every icon setting itself). Follows the settings as they change.
     */
    fun specState(store: SettingsStore, slot: IconSlot): State<Spec> =
        specStates.getOrPut(slot) { derivedStateOf { specFor(store, slot) } }

    // Same size as the apps' own icons, so styled ones are just as sharp
    private const val SIZE = 192
    private val cache = object : LruCache<String, ImageBitmap>(96 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val pending = HashSet<String>()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "quartz-icons").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val main = Handler(Looper.getMainLooper())

    /**
     * Kept for an icon that couldn't be drawn, so it isn't tried again on every redraw. [icon] hands back the
     * caller's own icon for it (not whichever copy happened to be loaded when it failed), so callers can tell.
     */
    private val FAILED = ImageBitmap(1, 1)

    /** The styled icon for [app], or null while it's being drawn (or when there's nothing to change). */
    fun icon(context: Context, app: AppEntry, spec: Spec): ImageBitmap? {
        if (spec.plain) return null
        val key = spec.key + "|" + app.key
        cache.get(key)?.let { return if (it === FAILED) app.icon else it }
        synchronized(pending) {
            if (!pending.add(key)) return null
        }
        val appContext = context.applicationContext
        worker.execute {
            // Already drawn while this waited (by Apply getting everything ready)
            if (cache.get(key) != null) {
                synchronized(pending) { pending.remove(key) }
                refreshSoon()
                return@execute
            }
            val bmp = runCatching { render(appContext, app, spec) }
                .onFailure { Diag.log("Icon for ${app.label} couldn't be drawn: ${it.message}") }
                .getOrNull()
            // Nothing to draw, or it failed: keep the app's own icon, so it isn't tried again on every redraw
            cache.put(key, bmp?.asImageBitmap() ?: FAILED)
            synchronized(pending) { pending.remove(key) }
            if (bmp != null) refreshSoon()
        }
        return null
    }

    /**
     * Draws every app's icon for each of [specs] ahead of time, so the home screen can switch to a new style all at
     * once. Reports progress on the main thread, then [onDone].
     */
    fun prepare(context: Context, apps: List<AppEntry>, specs: List<Spec>, onProgress: (Int, Int) -> Unit, onDone: () -> Unit) {
        val appContext = context.applicationContext
        worker.execute {
            // Whatever happens, say it's done, so the progress popup can't be left up
            try {
                val jobs = specs.filterNot { it.plain }.distinctBy { it.key }
                    .flatMap { spec -> apps.map { spec to it } }
                    .filter { (spec, app) -> cache.get(spec.key + "|" + app.key) == null }
                jobs.forEachIndexed { i, (spec, app) ->
                    val key = spec.key + "|" + app.key
                    if (cache.get(key) == null) {
                        val bmp = runCatching { render(appContext, app, spec) }
                            .onFailure { Diag.log("Icon for ${app.label} couldn't be drawn: ${it.message}") }
                            .getOrNull()
                        cache.put(key, bmp?.asImageBitmap() ?: FAILED)
                    }
                    if (i % 3 == 0 || i == jobs.lastIndex) main.post { onProgress(i + 1, jobs.size) }
                }
            } catch (t: Throwable) {
                Diag.log("Icons couldn't all be drawn ahead: ${t.message}")
            } finally {
                main.post {
                    version++
                    onDone()
                }
            }
        }
    }

    /** An app was updated or removed: redraw everything if it was an icon pack in use, else just its own icons. */
    fun packageChanged(pkg: String?) {
        if (pkg == null) return
        if (IconPacks.isLoaded(pkg)) {
            clear()
        } else {
            // Otherwise the app's own icon may have changed: drop what was drawn from the old one
            cache.snapshot().keys.filter { it.contains("|$pkg/") }.forEach { cache.remove(it) }
            main.post { epoch++ }
        }
    }

    private var refreshQueued = false

    /** Lets icons on screen swap in, once for a batch of newly drawn icons rather than once per icon. */
    private fun refreshSoon() {
        main.post {
            if (refreshQueued) return@post
            refreshQueued = true
            main.postDelayed({
                refreshQueued = false
                version++
            }, 60)
        }
    }

    /** Throws away drawn icons (a pack changed). */
    fun clear() {
        cache.evictAll()
        IconPacks.clear()
        main.post {
            epoch++
            version++
        }
    }

    // ---- Drawing ----

    /** The icon, then its rim of light and its shadow. */
    private fun render(context: Context, app: AppEntry, spec: Spec): Bitmap? {
        // The outline the rim follows: your shape, or the system's mask when the icon is left as the app drew it
        val outline = path(if (spec.shape == IconShape.SYSTEM) IconShape.SQUIRCLE else spec.shape, SIZE.toFloat())
        val icon = drawIcon(context, app, spec, outline) ?: return null
        drawEdge(Canvas(icon), outline, SIZE.toFloat(), spec.edge, isLight(plateColor(spec) ?: 0xFF000000.toInt()))
        if (spec.shadow == IconShadow.NONE) return icon
        return withShadow(icon, spec.shadow)
    }

    private fun drawIcon(context: Context, app: AppEntry, spec: Spec, outline: Path): Bitmap? {
        val density = context.resources.displayMetrics.densityDpi
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        // Quartz Settings isn't a launcher activity, so Android won't resolve it: it wears Quartz's own icon
        val own: Drawable? = if (app.key == StarterApps.QUARTZ_SETTINGS) {
            runCatching { context.packageManager.getApplicationIcon(context.packageName) }.getOrNull()
        } else {
            launcherApps?.resolveActivity(Intent().setComponent(app.component), Process.myUserHandle())?.getIcon(density)
        }
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
            if (plate != null) paintPlate(canvas, shape, spec, s)
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
            // Android's own mask (a circle on many phones), scaled to the bounds just set
            (icon as? AdaptiveIconDrawable)?.let { outline.set(it.iconMask) }
            return out
        }

        // Cut to your shape, on your background if you picked one. The shape goes down first and the icon is drawn
        // into it, so the edge comes out smooth (a clipPath edge is jagged)
        fillShape(canvas, shape, 0xFF000000.toInt())
        val layer = canvas.saveLayer(null, cutPaint)
        val adaptive = own as? AdaptiveIconDrawable
        when {
            packIcon != null -> {
                if (plate != null) fillPlate(canvas, spec, s) else canvas.drawColor(0xFFF4F4F6.toInt())
                drawInset(canvas, packIcon, s, 0.78f)
            }
            adaptive != null -> {
                if (plate != null) fillPlate(canvas, spec, s) else adaptive.background?.let { drawLayer(canvas, it, s, adaptive = true) }
                adaptive.foreground?.let { drawLayer(canvas, it, s, adaptive = true) }
            }
            own != null -> {
                // An old-style icon with its own outline: sit it on a plate so it fills the shape neatly
                if (plate != null) fillPlate(canvas, spec, s) else canvas.drawColor(0xFFF4F4F6.toInt())
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
        IconBackground.COLOR, IconBackground.GRADIENT -> spec.color
    }

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

    /** The background inside the shape: a color or a corner-to-corner gradient, then the pattern, if any. */
    private fun fillPlate(canvas: Canvas, spec: Spec, s: Float) {
        val base = plateColor(spec) ?: return
        if (spec.background == IconBackground.GRADIENT) {
            platePaint.shader = when (spec.gradient) {
                GradientDirection.HORIZONTAL -> LinearGradient(0f, 0f, s, 0f, base, spec.color2, Shader.TileMode.CLAMP)
                GradientDirection.VERTICAL -> LinearGradient(0f, 0f, 0f, s, base, spec.color2, Shader.TileMode.CLAMP)
                GradientDirection.DIAGONAL -> LinearGradient(0f, 0f, s, s, base, spec.color2, Shader.TileMode.CLAMP)
                GradientDirection.RADIAL -> RadialGradient(s / 2f, s / 2f, s * 0.6f, base, spec.color2, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, s, s, platePaint)
            platePaint.shader = null
        } else {
            canvas.drawColor(base)
        }
        if (spec.pattern != PatternStyle.NONE) {
            // Sized as if the icon were a small piece of a screen, so a few facets or dots show across it
            WallRenderer.drawPatternOnly(canvas, s, s * 3.5f, Rect(0, 0, s.toInt(), s.toInt()), spec.pattern, spec.patternStrength, spec.patternSize)
        }
    }

    /** [fillPlate], cut to [shape] with a smooth edge. */
    private fun paintPlate(canvas: Canvas, shape: Path, spec: Spec, s: Float) {
        fillShape(canvas, shape, 0xFF000000.toInt())
        val layer = canvas.saveLayer(null, cutPaint)
        fillPlate(canvas, spec, s)
        canvas.restoreToCount(layer)
    }

    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // Only where the icon already is, so the outer half of the line disappears and the edge stays smooth
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }

    /** The rim around an icon: a hairline, or light catching the edge (brightest top left, a glint bottom right). */
    private fun drawEdge(canvas: Canvas, shape: Path, s: Float, edge: DockEdge, lightPlate: Boolean) {
        when (edge) {
            DockEdge.NONE -> return
            DockEdge.HAIRLINE -> {
                rimPaint.shader = null
                rimPaint.color = if (lightPlate) 0x40000000 else 0x55FFFFFF
                rimPaint.strokeWidth = s * 0.03f
                canvas.drawPath(shape, rimPaint)
            }
            DockEdge.SOFT, DockEdge.BRIGHT -> {
                val k = if (edge == DockEdge.BRIGHT) 1f else 0.6f
                fun w(a: Float) = (((a * k).coerceIn(0f, 1f) * 255).toInt() shl 24) or 0xFFFFFF
                rimPaint.color = 0xFFFFFFFF.toInt()
                rimPaint.shader = LinearGradient(
                    0f, 0f, s, s,
                    intArrayOf(w(0.95f), w(0.32f), w(0.12f), w(0.3f), w(0.75f)),
                    floatArrayOf(0f, 0.3f, 0.5f, 0.75f, 1f),
                    Shader.TileMode.CLAMP,
                )
                rimPaint.strokeWidth = s * if (edge == DockEdge.BRIGHT) 0.05f else 0.04f
                canvas.drawPath(shape, rimPaint)
                rimPaint.shader = null
                if (edge == DockEdge.BRIGHT) {
                    // A soft sheen across the top, as if the icon were a curved piece of glass
                    sheenPaint.shader = LinearGradient(0f, 0f, 0f, s * 0.5f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, s, s * 0.5f, sheenPaint)
                    sheenPaint.shader = null
                }
            }
        }
    }

    /** The icon on a bigger picture with a blurred shadow under it, falling slightly down. */
    private fun withShadow(icon: Bitmap, shadow: IconShadow): Bitmap {
        val big = (SIZE * OVERSCAN).toInt()
        val out = Bitmap.createBitmap(big, big, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val strong = shadow == IconShadow.STRONG
        val blur = SIZE * if (strong) 0.07f else 0.05f
        val offset = IntArray(2)
        val alpha = icon.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) }, offset)
        val margin = (big - SIZE) / 2f
        val drop = SIZE * if (strong) 0.05f else 0.035f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (strong) 0x8C000000.toInt() else 0x59000000 }
        canvas.drawBitmap(alpha, margin + offset[0], margin + offset[1] + drop, paint)
        alpha.recycle()
        canvas.drawBitmap(icon, margin, margin, null)
        icon.recycle()
        return out
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
