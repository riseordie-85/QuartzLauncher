package com.tim.launcher.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.tim.launcher.wallpaper.WallRenderer
import kotlin.random.Random

/**
 * A made-up wallpaper for settings previews, busy enough that frosted glass shows: a sky gradient, a low sun,
 * soft circles of light, fine streaks and a line of hills. Not your wallpaper, just something with detail.
 */
internal object PreviewScene {

    /** The scene, [w]×[h] pixels, and the same scene blurred the way the home screen blurs its wallpaper. */
    fun make(w: Int, h: Int): Pair<Bitmap, Bitmap> {
        val sharp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        draw(Canvas(sharp), w.toFloat(), h.toFloat())
        val blurred = sharp.copy(Bitmap.Config.ARGB_8888, true)
        // About the strength of the home screen's glass at its usual setting, for a picture this size
        WallRenderer.blur(blurred, (h * 0.09f).toInt().coerceIn(3, 14))
        return sharp to blurred
    }

    private fun draw(c: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

        // Sky
        paint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(0xFF3B2A7A.toInt(), 0xFF244F9E.toInt(), 0xFF14808A.toInt(), 0xFF4E9F3D.toInt()),
            null, Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w, h, paint)

        // A low sun with a glow
        val sx = w * 0.72f
        val sy = h * 0.42f
        paint.shader = RadialGradient(sx, sy, h * 0.7f, intArrayOf(0x88FFD27A.toInt(), 0x00FFD27A), null, Shader.TileMode.CLAMP)
        c.drawCircle(sx, sy, h * 0.7f, paint)
        paint.shader = null
        paint.color = 0xFFFFD98A.toInt()
        c.drawCircle(sx, sy, h * 0.2f, paint)

        // Soft circles of light, the same every time
        val rnd = Random(7)
        val tints = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFF9BD2.toInt(), 0xFF7FE8FF.toInt(), 0xFFFFE08A.toInt())
        repeat(18) {
            val r = h * (0.06f + rnd.nextFloat() * 0.2f)
            paint.color = tints[it % tints.size]
            paint.alpha = 50 + rnd.nextInt(90)
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.8f, r, paint)
        }

        // Fine diagonal streaks
        paint.color = 0x2EFFFFFF
        paint.strokeWidth = (h * 0.018f).coerceAtLeast(1f)
        val step = h * 0.22f
        var x = -h
        while (x < w + h) {
            c.drawLine(x, h, x + h * 0.9f, 0f, paint)
            x += step
        }

        // Two lines of hills along the bottom
        fun hills(base: Float, height: Float, waves: Float, phase: Float, color: Int) {
            val p = Path().apply {
                moveTo(0f, h)
                var px = 0f
                while (px <= w) {
                    val y = base - height * (0.5f + 0.5f * kotlin.math.sin((px / w) * waves * 6.283f + phase))
                    lineTo(px, y)
                    px += w / 60f
                }
                lineTo(w, h)
                close()
            }
            paint.color = color
            c.drawPath(p, paint)
        }
        hills(h * 0.88f, h * 0.3f, 2.3f, 0.6f, 0xCC1B2A4A.toInt())
        hills(h * 1.0f, h * 0.26f, 3.1f, 2.1f, 0xFF101828.toInt())
    }
}
