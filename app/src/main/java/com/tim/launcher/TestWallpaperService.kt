package com.tim.launcher

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 0 test wallpaper. Draws one "wide" test image (LEFT HALF | hinge | RIGHT HALF) and labels which screen,
 * size and display it is drawing on, so we can see how the Fold hands wallpaper surfaces to each screen.
 */
class TestWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = TestEngine()

    private inner class TestEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val id = ++engineCount
        private var w = 0
        private var h = 0
        private val prefs by lazy { Prefs.get(this@TestWallpaperService) }

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 255, 255, 255)
            strokeWidth = 2f
        }
        private val hinge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            strokeWidth = 5f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(24f, 16f), 0f)
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

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(false)
            prefs.registerOnSharedPreferenceChangeListener(this)
            Diag.log("Wallpaper #$id created${if (isPreview) " (preview)" else ""} on display ${displayId()}")
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            Diag.log("Wallpaper #$id destroyed")
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width
            h = height
            Diag.log("Wallpaper #$id surface ${width}×$height on display ${displayId()}")
            draw()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) draw()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key == Prefs.WP_MODE || key == Prefs.COVER_SIDE || key == Prefs.FOLDABLE) draw()
        }

        private fun displayId(): Int = runCatching { displayContext?.display?.displayId }.getOrNull() ?: -1

        private fun draw() {
            if (w == 0 || h == 0) return
            val holder = surfaceHolder
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull()
                ?: runCatching { holder.lockCanvas() }.getOrNull()
                ?: return
            try {
                paint(canvas)
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun paint(canvas: Canvas) {
            val mode = prefs.getString(Prefs.WP_MODE, "matched") ?: "matched"
            val coverLeft = prefs.getString(Prefs.COVER_SIDE, "left") != "right"
            val foldable = prefs.getBoolean(Prefs.FOLDABLE, false) ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
            val narrow = w.toFloat() / h < 0.7f
            val screenName = when {
                foldable && narrow -> "Cover screen"
                foldable -> "Inner screen"
                else -> "Single screen"
            }

            val showing = when (mode) {
                "separate" -> {
                    if (foldable && narrow) {
                        drawSolid(canvas, Color.parseColor("#3A2A5C"), "COVER\nWALLPAPER")
                    } else {
                        drawWide(canvas, 0f, 1f, alt = true)
                    }
                    "its own wallpaper"
                }
                "independent" -> {
                    drawWide(canvas, 0f, 1f, alt = false)
                    "the whole image"
                }
                else -> if (foldable && narrow) {
                    if (coverLeft) drawWide(canvas, 0f, 0.5f, alt = false) else drawWide(canvas, 0.5f, 1f, alt = false)
                    if (coverLeft) "the left half" else "the right half"
                } else {
                    drawWide(canvas, 0f, 1f, alt = false)
                    "the whole image"
                }
            }

            drawInfo(
                canvas,
                listOf(
                    "Quartz test wallpaper #$id",
                    "$screenName · ${w}×$h px",
                    "Display ${displayId()} · ${modeLabel(mode)}",
                    "Showing $showing",
                ),
            )
        }

        private fun modeLabel(mode: String) = when (mode) {
            "independent" -> "Shared · stretched"
            "separate" -> "Independent"
            else -> "Shared · cropped"
        }

        /** Draws the part [from]..[to] (0–1) of one wide test image across the whole surface. */
        private fun drawWide(canvas: Canvas, from: Float, to: Float, alt: Boolean) {
            val span = to - from
            fun x(f: Float) = (f - from) / span * w

            val colors = if (alt) {
                intArrayOf(
                    Color.parseColor("#0F3D3E"), Color.parseColor("#1D6A5E"),
                    Color.parseColor("#7A8F2E"), Color.parseColor("#C9A227"),
                )
            } else {
                intArrayOf(
                    Color.parseColor("#4B1D7A"), Color.parseColor("#2546A8"), Color.parseColor("#138A8A"),
                    Color.parseColor("#4E9F3D"), Color.parseColor("#E07A2E"),
                )
            }
            fill.shader = LinearGradient(x(0f), 0f, x(1f), h.toFloat(), colors, null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
            fill.shader = null

            // Grid lines every 1/8 of the wide image, so halves line up visibly across fold states
            for (i in 0..8) {
                val f = i / 8f
                if (f in from..to) canvas.drawLine(x(f), 0f, x(f), h.toFloat(), line)
            }
            val rowStep = h / 10f
            for (i in 1..9) canvas.drawLine(0f, i * rowStep, w.toFloat(), i * rowStep, line)

            if (0.5f in from..to) {
                canvas.drawLine(x(0.5f), 0f, x(0.5f), h.toFloat(), hinge)
            }

            bigText.textSize = min(w, h) * if (span < 1f) 0.11f else 0.075f
            val labelY = h * 0.47f
            if (0.25f in from..to) canvas.drawText(if (alt) "INNER" else "LEFT HALF", x(0.25f), labelY, bigText)
            if (0.75f in from..to) canvas.drawText(if (alt) "WALLPAPER" else "RIGHT HALF", x(0.75f), labelY, bigText)
        }

        private fun drawSolid(canvas: Canvas, color: Int, label: String) {
            canvas.drawColor(color)
            bigText.textSize = min(w, h) * 0.11f
            val lines = label.split("\n")
            lines.forEachIndexed { i, text ->
                canvas.drawText(text, w / 2f, h * 0.45f + i * bigText.textSize * 1.15f, bigText)
            }
        }

        private fun drawInfo(canvas: Canvas, lines: List<String>) {
            smallText.textSize = max(28f, min(w, h) * 0.034f)
            val pad = smallText.textSize * 0.8f
            val lineH = smallText.textSize * 1.35f
            val boxW = lines.maxOf { smallText.measureText(it) } + pad * 2
            val top = h * 0.07f
            val left = (w - boxW) / 2f
            canvas.drawRoundRect(RectF(left, top, left + boxW, top + lineH * lines.size + pad * 1.4f), pad, pad, box)
            lines.forEachIndexed { i, text ->
                canvas.drawText(text, left + pad, top + pad + smallText.textSize + i * lineH, smallText)
            }
        }
    }

    companion object {
        private var engineCount = 0
    }
}
