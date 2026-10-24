package com.tim.launcher.wallpaper

import android.app.WallpaperColors
import android.content.SharedPreferences
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.tim.launcher.Diag
import com.tim.launcher.Prefs

/**
 * The Quartz wallpaper. It draws whatever you've set up in Quartz settings (gradient, color, image, pattern) and
 * knows which screen it's on, so a folding phone's cover and unfolded screens line up the way you chose.
 */
class QuartzWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = QuartzEngine()

    private inner class QuartzEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val id = ++engineCount
        private var w = 0
        private var h = 0
        private val prefs by lazy { Prefs.get(this@QuartzWallpaperService) }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(false)
            prefs.registerOnSharedPreferenceChangeListener(this)
            Diag.log("Wallpaper #$id started${if (isPreview) " (preview)" else ""}")
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            Diag.log("Wallpaper #$id stopped")
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            val resized = width != w || height != h
            w = width
            h = height
            val kind = WallRenderer.kindFor(this@QuartzWallpaperService, w, h)
            if (!isPreview) WallCache.rememberSize(this@QuartzWallpaperService, kind, w, h)
            Diag.log("Wallpaper #$id: ${kind.label} ${width}×$height")
            draw()
            if (resized) notifyColorsChanged()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) draw()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            when (key) {
                Prefs.WP_MAIN, Prefs.WP_COVER, Prefs.WP_MODE -> {
                    draw()
                    notifyColorsChanged()
                }
                Prefs.COVER_SIDE, Prefs.FOLDABLE, Prefs.INNER_W, Prefs.INNER_H -> draw()
            }
        }

        /** Colors for Android's own theming (Material You) and its light or dark status bar text. */
        override fun onComputeColors(): WallpaperColors? {
            if (w == 0 || h == 0) return null
            return runCatching {
                val frame = WallRenderer.frameFor(this@QuartzWallpaperService, w, h)
                val small = WallRenderer.renderSmall(w, h, 96f / maxOf(w, h), frame) { WallCache.image(this@QuartzWallpaperService, it) }
                WallpaperColors.fromBitmap(small).also { small.recycle() }
            }.getOrNull()
        }

        private fun draw() {
            if (w == 0 || h == 0) return
            val holder = surfaceHolder
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull()
                ?: runCatching { holder.lockCanvas() }.getOrNull()
                ?: return
            try {
                val frame = WallRenderer.frameFor(this@QuartzWallpaperService, w, h)
                WallRenderer.draw(canvas, w, h, frame) { WallCache.image(this@QuartzWallpaperService, it) }
            } catch (e: Exception) {
                Diag.log("Wallpaper #$id couldn't draw: ${e.message}")
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }

    companion object {
        private var engineCount = 0
    }
}
