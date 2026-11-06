package com.tim.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.tim.launcher.wallpaper.WallCache

/** The blurred wallpaper behind every panel on this screen, or null when there's no blur (or Quartz isn't the wallpaper). */
val LocalGlass = staticCompositionLocalOf<WallCache.Glass?> { null }

/**
 * Frosted glass: the part of the blurred wallpaper that sits behind this panel, then [tint] over it. Without blur
 * it's just the tint, and the real wallpaper shows through.
 */
fun Modifier.glass(shape: Shape, tint: Color): Modifier = composed {
    val glass = LocalGlass.current
    val image = remember(glass) { glass?.bitmap?.asImageBitmap() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .clip(shape)
        .drawBehind {
            if (glass != null && image != null) {
                val sx = image.width / glass.screenW.toFloat()
                val sy = image.height / glass.screenH.toFloat()
                withTransform({
                    translate(glass.left - origin.x, glass.top - origin.y)
                    scale(1f / sx, 1f / sy, pivot = Offset.Zero)
                }) {
                    drawImage(image, filterQuality = FilterQuality.Low)
                }
            }
            drawRect(tint)
        }
}

/** Dark text for light glass and wallpapers, white for dark ones. */
object Ink {
    val Dark = Color(0xFF15141C)
    val Light = Color.White

    /** How light a [tint] at its alpha looks over something [behind] bright (0 dark – 1 light). */
    fun brightness(tint: Color, behind: Float): Float = tint.alpha * tint.copy(alpha = 1f).luminance().perceived() + (1 - tint.alpha) * behind

    fun on(brightness: Float): Color = if (brightness > 0.55f) Dark else Light

    // Compose's luminance() is linear light; people judge brightness closer to its square root
    private fun Float.perceived() = kotlin.math.sqrt(this)
}
