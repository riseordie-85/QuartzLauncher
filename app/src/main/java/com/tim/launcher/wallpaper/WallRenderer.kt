package com.tim.launcher.wallpaper

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.tim.launcher.Prefs
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class ScreenKind(val label: String) { SINGLE("Single screen"), COVER("Cover screen"), INNER("Unfolded screen") }

/**
 * What one screen shows: [config], drawn on a virtual canvas [aspect] wide (1 tall), of which the part from [from]
 * to [to] (0–1, across) belongs to this screen. A cover screen in Shared · cropped shows its half of the unfolded
 * screen's canvas; every other screen gets the whole of a canvas shaped like itself.
 */
data class Frame(
    val config: WallConfig,
    val aspect: Float,
    val from: Float,
    val to: Float,
    val kind: ScreenKind,
    val mode: FoldMode,
)

/**
 * Draws Quartz wallpapers. The wallpaper service, the launcher's frosted glass and the settings previews all draw
 * through here, so the glass behind the dock lines up exactly with the wallpaper around it.
 */
object WallRenderer {

    fun isFoldable(context: Context): Boolean =
        Prefs.get(context).getBoolean(Prefs.FOLDABLE, false) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)

    fun kindFor(context: Context, w: Int, h: Int): ScreenKind = when {
        !isFoldable(context) -> ScreenKind.SINGLE
        // Short side over long side, so a cover screen turned sideways is still the cover (and doesn't get saved
        // as the unfolded screen's size)
        min(w, h).toFloat() / max(w, h).coerceAtLeast(1) < 0.7f -> ScreenKind.COVER
        else -> ScreenKind.INNER
    }

    /** The unfolded screen's shape, as last seen (the Fold's inner screen until then). */
    fun innerAspect(context: Context): Float {
        val prefs = Prefs.get(context)
        val w = prefs.getInt(Prefs.INNER_W, 0)
        val h = prefs.getInt(Prefs.INNER_H, 0)
        return if (w > 0 && h > 0) w.toFloat() / h else 2448f / 1848f
    }

    fun coverAspect(context: Context): Float {
        val prefs = Prefs.get(context)
        val w = prefs.getInt(Prefs.COVER_W, 0)
        val h = prefs.getInt(Prefs.COVER_H, 0)
        return if (w > 0 && h > 0) w.toFloat() / h else 1080f / 2520f
    }

    fun frameFor(context: Context, w: Int, h: Int): Frame {
        val kind = kindFor(context, w, h)
        val mode = WallStore.mode(context)
        val aspect = w.toFloat() / h.coerceAtLeast(1)
        val coverLeft = Prefs.get(context).getString(Prefs.COVER_SIDE, "left") != "right"
        return when {
            kind == ScreenKind.COVER && mode == FoldMode.SEPARATE ->
                Frame(WallStore.load(context, WallSlot.COVER), aspect, 0f, 1f, kind, mode)
            kind == ScreenKind.COVER && mode == FoldMode.MATCHED ->
                Frame(
                    WallStore.load(context, WallSlot.MAIN), innerAspect(context),
                    if (coverLeft) 0f else 0.5f, if (coverLeft) 0.5f else 1f, kind, mode,
                )
            else -> Frame(WallStore.load(context, WallSlot.MAIN), aspect, 0f, 1f, kind, mode)
        }
    }

    // ---- Drawing ----

    private class Viewport(val width: Float, val height: Float, val dx: Float, val dy: Float)

    /** Fits this screen's part of the virtual canvas to the screen, cropping (never stretching) to fill it. */
    private fun viewport(w: Int, h: Int, f: Frame): Viewport {
        val screen = w.toFloat() / h
        val regionW = (f.to - f.from) * f.aspect
        val visW: Float
        val visH: Float
        if (regionW > screen) {
            visH = 1f
            visW = screen
        } else {
            visW = regionW
            visH = regionW / screen
        }
        val left = f.from * f.aspect + (regionW - visW) / 2f
        val top = (1f - visH) / 2f
        val scale = h / visH
        return Viewport(f.aspect * scale, scale, -left * scale, -top * scale)
    }

    private val smooth = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    /**
     * Draws [frame] on a [w]×[h] screen. [image] loads a saved image by name (null while it isn't available).
     * One drawing at a time: the wallpaper, the glass and the previews draw on different threads with shared paints.
     */
    @Synchronized
    fun draw(canvas: Canvas, w: Int, h: Int, frame: Frame, image: (String) -> Bitmap?) {
        if (w <= 0 || h <= 0) return
        val c = frame.config
        val vp = viewport(w, h, frame)
        canvas.save()
        canvas.clipRect(0, 0, w, h)
        canvas.translate(vp.dx, vp.dy)
        val width = vp.width
        val height = vp.height
        when (c.kind) {
            WallKind.SOLID -> canvas.drawColor(c.solid)
            WallKind.GRADIENT -> drawGradient(canvas, width, height, c)
            WallKind.IMAGE -> {
                val bmp = c.image?.let(image)
                if (bmp != null) drawImage(canvas, width, height, c, bmp) else canvas.drawColor(c.solid)
            }
            WallKind.TEST -> drawTest(canvas, width, height, frame)
        }
        if (c.kind != WallKind.TEST && c.pattern != PatternStyle.NONE) {
            if (c.pattern.faceted) {
                val clip = canvas.clipBounds.takeUnless { it.isEmpty } ?: Rect(0, 0, width.toInt(), height.toInt())
                Facets.draw(canvas, width, height, clip, c)
            } else {
                drawPattern(canvas, width, height, c)
            }
        }
        canvas.restore()
        if (c.dim > 0.005f) canvas.drawColor(Color.argb((c.dim.coerceIn(0f, 0.8f) * 255).toInt(), 0, 0, 0))
        if (c.kind == WallKind.TEST) drawTestInfo(canvas, w, h, frame)
    }

    /**
     * Just a pattern, in [style], over whatever is already on [canvas] (the dock uses this). [w]×[h] is the screen,
     * so the pattern's size and placement match the wallpaper's; [clip] is the part to draw.
     */
    @Synchronized
    fun drawPatternOnly(canvas: Canvas, w: Float, h: Float, clip: Rect, style: PatternStyle, strength: Float) {
        val c = WallConfig(pattern = style, patternStrength = strength, patternSize = 0.3f, patternColor = Color.WHITE)
        if (style.faceted) Facets.draw(canvas, w, h, clip, c) else drawPattern(canvas, w, h, c)
    }

    /** A small copy of a screen's wallpaper ([scale] of full size), for glass, previews and colors. */
    fun renderSmall(w: Int, h: Int, scale: Float, frame: Frame, image: (String) -> Bitmap?): Bitmap {
        val bw = max(1, (w * scale).toInt())
        val bh = max(1, (h * scale).toInt())
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(bw.toFloat() / w, bh.toFloat() / h)
        draw(canvas, w, h, frame, image)
        return bmp
    }

    private fun drawGradient(canvas: Canvas, w: Float, h: Float, c: WallConfig) {
        when (c.gradient) {
            GradientStyle.LINEAR -> {
                val a = Math.toRadians(c.angle.toDouble())
                val dx = cos(a).toFloat()
                val dy = sin(a).toFloat()
                val half = (abs(w * dx) + abs(h * dy)) / 2f
                val cx = w / 2f
                val cy = h / 2f
                val colors = if (c.linearMiddle) intArrayOf(c.color(0), c.color(1), c.color(2)) else intArrayOf(c.color(0), c.color(2))
                smooth.shader = LinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half, colors, null, Shader.TileMode.CLAMP)
            }
            GradientStyle.RADIAL -> {
                val r = max(1f, c.radialSize.coerceIn(0.1f, 2f) * max(w, h))
                smooth.shader = RadialGradient(c.radialX * w, c.radialY * h, r, intArrayOf(c.color(0), c.color(2)), null, Shader.TileMode.CLAMP)
            }
            GradientStyle.CORNERS -> {
                canvas.drawBitmap(cornerBitmap(c), null, RectF(0f, 0f, w, h), smooth)
                return
            }
        }
        canvas.drawRect(0f, 0f, w, h, smooth)
        smooth.shader = null
    }

    private var cornerKey: List<Int>? = null
    private var cornerBmp: Bitmap? = null

    /**
     * Four corner colors blended across, with the middle color glowing out from the center. Worked out on a small
     * grid and scaled up smoothly, which is plenty for something this soft (and works on every kind of canvas).
     */
    @Synchronized
    private fun cornerBitmap(c: WallConfig): Bitmap {
        val key = (0 until 5).map { c.color(it) }
        cornerBmp?.let { if (key == cornerKey) return it }
        val n = 96
        val px = IntArray(n * n)
        fun ch(color: Int, shift: Int) = ((color shr shift) and 0xFF).toFloat()
        for (y in 0 until n) {
            val v = y / (n - 1f)
            for (x in 0 until n) {
                val u = x / (n - 1f)
                val d = hypot(u - 0.5f, v - 0.5f)
                var m = (1f - d / 0.6f).coerceIn(0f, 1f)
                m = m * m * (3f - 2f * m) * 0.9f
                var out = 0xFF shl 24
                for (shift in intArrayOf(16, 8, 0)) {
                    val top = ch(key[0], shift) * (1 - u) + ch(key[1], shift) * u
                    val bottom = ch(key[2], shift) * (1 - u) + ch(key[3], shift) * u
                    val base = top * (1 - v) + bottom * v
                    val value = base * (1 - m) + ch(key[4], shift) * m
                    out = out or (value.toInt().coerceIn(0, 255) shl shift)
                }
                px[y * n + x] = out
            }
        }
        val bmp = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        cornerKey = key
        cornerBmp = bmp
        return bmp
    }

    private fun drawImage(canvas: Canvas, w: Float, h: Float, c: WallConfig, bmp: Bitmap) {
        val s = max(w / bmp.width, h / bmp.height) * c.zoom.coerceIn(1f, 4f)
        val dw = bmp.width * s
        val dh = bmp.height * s
        val left = (w - dw) * c.focusX.coerceIn(0f, 1f)
        val top = (h - dh) * c.focusY.coerceIn(0f, 1f)
        canvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), smooth)
    }

    private val pat = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun drawPattern(canvas: Canvas, w: Float, h: Float, c: WallConfig) {
        val cell = h * (0.025f + c.patternSize.coerceIn(0f, 1f) * 0.075f)
        if (cell < 2f) return
        val alpha = (c.patternStrength.coerceIn(0f, 1f) * 255).toInt()
        pat.color = (c.patternColor and 0x00FFFFFF) or (alpha shl 24)
        pat.shader = null
        // Only the part that's on screen
        val clip = canvas.clipBounds.takeUnless { it.isEmpty } ?: Rect(0, 0, w.toInt(), h.toInt())
        val x0 = floor(clip.left / cell).toInt() - 1
        val x1 = ceil(clip.right / cell).toInt() + 1
        val y0 = floor(clip.top / cell).toInt() - 1
        val y1 = ceil(clip.bottom / cell).toInt() + 1
        when (c.pattern) {
            PatternStyle.DOTS -> {
                pat.style = Paint.Style.FILL
                for (r in y0..y1) for (col in x0..x1) {
                    val shift = if (r % 2 == 0) 0f else cell / 2f
                    canvas.drawCircle(col * cell + shift + cell / 2f, r * cell + cell / 2f, cell * 0.13f, pat)
                }
            }
            PatternStyle.GRID -> {
                pat.style = Paint.Style.STROKE
                pat.strokeWidth = max(1f, cell * 0.04f)
                for (col in x0..x1) canvas.drawLine(col * cell, clip.top.toFloat(), col * cell, clip.bottom.toFloat(), pat)
                for (r in y0..y1) canvas.drawLine(clip.left.toFloat(), r * cell, clip.right.toFloat(), r * cell, pat)
            }
            PatternStyle.STRIPES -> {
                pat.style = Paint.Style.STROKE
                pat.strokeWidth = cell * 0.3f
                // 45° lines: x - y = k·cell
                val k0 = floor((clip.left - clip.bottom) / cell).toInt() - 1
                val k1 = ceil((clip.right - clip.top) / cell).toInt() + 1
                for (k in k0..k1) {
                    val b = k * cell
                    canvas.drawLine(clip.top + b, clip.top.toFloat(), clip.bottom + b, clip.bottom.toFloat(), pat)
                }
            }
            PatternStyle.CHECKS -> {
                pat.style = Paint.Style.FILL
                for (r in y0..y1) for (col in x0..x1) {
                    if ((r + col) % 2 == 0) canvas.drawRect(col * cell, r * cell, (col + 1) * cell, (r + 1) * cell, pat)
                }
            }
            PatternStyle.WAVES -> {
                pat.style = Paint.Style.STROKE
                pat.strokeWidth = max(1f, cell * 0.07f)
                val path = Path()
                val amp = cell * 0.22f
                val step = cell / 8f
                for (r in y0..y1) {
                    path.reset()
                    val base = r * cell + cell / 2f
                    var x = clip.left - cell
                    path.moveTo(x, base + amp * sin(x / cell * PI).toFloat())
                    while (x < clip.right + cell) {
                        x += step
                        path.lineTo(x, base + amp * sin(x / cell * PI).toFloat())
                    }
                    canvas.drawPath(path, pat)
                }
            }
            // None, and the faceted ones (drawn by Facets)
            else -> Unit
        }
    }

    // ---- Test pattern (kept from phase 0, for checking how screens line up) ----

    private val testLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
    private val testHinge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        pathEffect = DashPathEffect(floatArrayOf(24f, 16f), 0f)
    }
    private val bigText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        setShadowLayer(10f, 0f, 3f, Color.argb(140, 0, 0, 0))
    }
    private val smallText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.MONOSPACE
    }
    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }

    private fun drawTest(canvas: Canvas, w: Float, h: Float, frame: Frame) {
        val alt = frame.kind == ScreenKind.COVER && frame.mode == FoldMode.SEPARATE
        val colors = if (alt) {
            intArrayOf(Color.parseColor("#0F3D3E"), Color.parseColor("#1D6A5E"), Color.parseColor("#7A8F2E"), Color.parseColor("#C9A227"))
        } else {
            intArrayOf(
                Color.parseColor("#4B1D7A"), Color.parseColor("#2546A8"), Color.parseColor("#138A8A"),
                Color.parseColor("#4E9F3D"), Color.parseColor("#E07A2E"),
            )
        }
        smooth.shader = LinearGradient(0f, 0f, w, h, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, smooth)
        smooth.shader = null
        testLine.strokeWidth = max(1f, h / 900f)
        for (i in 0..8) canvas.drawLine(w * i / 8f, 0f, w * i / 8f, h, testLine)
        for (i in 1..9) canvas.drawLine(0f, h * i / 10f, w, h * i / 10f, testLine)
        if (!alt && frame.kind != ScreenKind.SINGLE) {
            testHinge.strokeWidth = max(2f, h / 370f)
            canvas.drawLine(w / 2f, 0f, w / 2f, h, testHinge)
        }
        bigText.textSize = min(w / 2f, h) * 0.11f
        canvas.drawText(if (alt) "COVER" else "LEFT HALF", w * 0.25f, h * 0.47f, bigText)
        canvas.drawText(if (alt) "WALLPAPER" else "RIGHT HALF", w * 0.75f, h * 0.47f, bigText)
    }

    private fun drawTestInfo(canvas: Canvas, w: Int, h: Int, frame: Frame) {
        val lines = listOf("Quartz test wallpaper", "${frame.kind.label} · ${w}×$h px", frame.mode.label)
        smallText.textSize = max(10f, min(w, h) * 0.034f)
        val pad = smallText.textSize * 0.8f
        val lineH = smallText.textSize * 1.35f
        val boxW = lines.maxOf { smallText.measureText(it) } + pad * 2
        val top = h * 0.07f
        val left = (w - boxW) / 2f
        canvas.drawRoundRect(RectF(left, top, left + boxW, top + lineH * lines.size + pad * 1.4f), pad, pad, box)
        lines.forEachIndexed { i, text -> canvas.drawText(text, left + pad, top + pad + smallText.textSize + i * lineH, smallText) }
    }

    // ---- Blur and brightness ----

    /** Blurs [bmp] in place: three box-blur passes each way, which looks like a soft gaussian. */
    fun blur(bmp: Bitmap, radius: Int) {
        if (radius < 1) return
        val w = bmp.width
        val h = bmp.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        bmp.getPixels(a, 0, w, 0, 0, w, h)
        val r = radius.coerceAtMost(max(1, min(w, h) / 2))
        repeat(3) {
            boxPass(a, b, w, h, r, horizontal = true)
            boxPass(b, a, w, h, r, horizontal = false)
        }
        bmp.setPixels(a, 0, w, 0, 0, w, h)
    }

    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val k = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + k else k * w + line
            }
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = src[at(i)]
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (i in 0 until len) {
                dst[at(i)] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val out = src[at(i - r)]
                val inn = src[at(i + r + 1)]
                sr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                sg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                sb += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }

    /** Average brightness (0 dark – 1 light) of part of [bmp], as a person would judge it. */
    fun brightness(bmp: Bitmap, left: Float = 0f, top: Float = 0f, right: Float = 1f, bottom: Float = 1f): Float {
        val x0 = (left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val x1 = (right * bmp.width).toInt().coerceIn(x0 + 1, bmp.width)
        val y0 = (top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        val y1 = (bottom * bmp.height).toInt().coerceIn(y0 + 1, bmp.height)
        val stepX = max(1, (x1 - x0) / 48)
        val stepY = max(1, (y1 - y0) / 48)
        var sum = 0.0
        var n = 0
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                val p = bmp.getPixel(x, y)
                sum += (0.2126 * ((p shr 16) and 0xFF) + 0.7152 * ((p shr 8) and 0xFF) + 0.0722 * (p and 0xFF)) / 255.0
                n++
                x += stepX
            }
            y += stepY
        }
        return if (n == 0) 0.3f else (sum / n).toFloat()
    }
}
