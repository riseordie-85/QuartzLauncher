package com.tim.launcher.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.ui.Q
import com.tim.launcher.wallpaper.PatternGroup
import com.tim.launcher.wallpaper.PatternStyle
import com.tim.launcher.wallpaper.WallRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Little pictures of each pattern, drawn once and kept for the life of the app. */
private object PatternThumbs {
    private const val PX = 120
    private val made = ConcurrentHashMap<PatternStyle, ImageBitmap>()

    fun get(style: PatternStyle): ImageBitmap = made.getOrPut(style) {
        val bmp = Bitmap.createBitmap(PX, PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, PX.toFloat(), PX.toFloat(), 0xFF3A3360.toInt(), 0xFF1D2A4A.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, PX.toFloat(), PX.toFloat(), bg)
        if (style != PatternStyle.NONE) {
            // The middle of a small imaginary screen, so patterns that start from the middle (rings) show whole
            val screen = 380f
            val off = ((screen - PX) / 2f).toInt()
            canvas.save()
            canvas.translate(-off.toFloat(), -off.toFloat())
            val clip = Rect(off, off, off + PX, off + PX)
            canvas.clipRect(clip)
            WallRenderer.drawPatternOnly(canvas, screen, screen, clip, style, if (style.faceted) 0.45f else 0.6f, 0.5f, 0.5f)
            canvas.restore()
        }
        bmp.asImageBitmap()
    }
}

/**
 * The pattern choice: a row showing the one picked, which opens to every pattern as a little picture, grouped
 * (crystal, dots, lines, grids, shapes, curves). [crystal] false leaves the crystal ones out.
 */
@Composable
fun PatternPicker(title: String, about: String?, selected: PatternStyle, crystal: Boolean = true, onPick: (PatternStyle) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val styles = remember(crystal) { PatternStyle.entries.filter { crystal || !it.faceted } }
    val thumbs by produceState<Map<PatternStyle, ImageBitmap>>(emptyMap(), styles) {
        value = withContext(Dispatchers.Default) { styles.associateWith { PatternThumbs.get(it) } }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Q.Text, fontSize = 16.sp)
                Text(
                    if (selected == PatternStyle.NONE) about ?: "None" else selected.label,
                    color = Q.TextDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Thumb(thumbs[selected], selected = false, size = 40)
        }
        if (open) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                val cols = ((maxWidth.value + 8f) / 82f).toInt().coerceAtLeast(3)
                Column {
                    Swatches(listOf(PatternStyle.NONE), cols, thumbs, selected) { onPick(it); open = false }
                    PatternGroup.entries.forEach { group ->
                        val inGroup = styles.filter { it.group == group }
                        if (inGroup.isEmpty()) return@forEach
                        Text(group.label, color = Q.TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 6.dp))
                        Swatches(inGroup, cols, thumbs, selected) { onPick(it); open = false }
                    }
                    Spacer(Modifier.size(10.dp))
                }
            }
        }
    }
}

@Composable
private fun Swatches(styles: List<PatternStyle>, cols: Int, thumbs: Map<PatternStyle, ImageBitmap>, selected: PatternStyle, onPick: (PatternStyle) -> Unit) {
    styles.chunked(cols).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { style ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onPick(style) }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Thumb(thumbs[style], selected = style == selected, size = 58)
                    Text(
                        style.label,
                        color = if (style == selected) Q.Text else Q.TextDim,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Thumb(image: ImageBitmap?, selected: Boolean, size: Int) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(size.dp)
            .clip(shape)
            .background(Color(0xFF2A2F45))
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Q.Edge, shape),
    ) {
        if (image != null) Image(image, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

/** How heavy a pattern's lines and dots are, for the patterns that have lines and dots. */
@Composable
fun PatternWeightSlider(style: PatternStyle, weight: Float, onChange: (Float) -> Unit) {
    if (style == PatternStyle.NONE || !style.weighted) return
    com.tim.launcher.ui.SliderRow("Line weight", weight, 0f..1f, when {
        weight < 0.2f -> "Fine"
        weight > 0.8f -> "Bold"
        else -> "${(weight * 100).toInt()}%"
    }) { onChange(it) }
}
