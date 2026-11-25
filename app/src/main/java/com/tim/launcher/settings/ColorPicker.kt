package com.tim.launcher.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.ui.Q
import com.tim.launcher.wallpaper.WallKind
import com.tim.launcher.wallpaper.WallSlot
import com.tim.launcher.wallpaper.WallStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Pick any color: a square for how vivid and how bright, a bar for the hue, and a box to type a code like #efefef.
 * [hsv] is the color as hue (0–360), saturation and brightness (0–1); [onHsv] gets each change.
 */
@Composable
internal fun HsvPicker(hsv: FloatArray, onHsv: (FloatArray) -> Unit) {
    val current by rememberUpdatedState(hsv)
    val send by rememberUpdatedState(onHsv)
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)))
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        // How vivid (left to right) and how bright (bottom to top)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(14.dp))
                .drawBehind {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                }
                .pointerInput(Unit) {
                    fun at(x: Float, y: Float) {
                        val s = (x / size.width).coerceIn(0f, 1f)
                        val v = 1f - (y / size.height).coerceIn(0f, 1f)
                        send(floatArrayOf(current[0], s, v))
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        at(down.position.x, down.position.y)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            at(change.position.x, change.position.y)
                        }
                    }
                },
        ) {
            val density = LocalDensity.current
            val w = with(density) { maxWidth.toPx() }
            val h = with(density) { maxHeight.toPx() }
            val r = with(density) { 11.dp.toPx() }
            Thumb(
                Color(android.graphics.Color.HSVToColor(hsv)),
                Modifier.offset { IntOffset((hsv[1] * w - r).roundToInt(), ((1f - hsv[2]) * h - r).roundToInt()) },
            )
        }
        Spacer(Modifier.height(14.dp))
        // Hue
        val rainbow = remember { (0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) } }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.horizontalGradient(rainbow))
                .pointerInput(Unit) {
                    fun at(x: Float) = send(floatArrayOf((x / size.width).coerceIn(0f, 1f) * 360f, current[1], current[2]))
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        at(down.position.x)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            at(change.position.x)
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val density = LocalDensity.current
            val w = with(density) { maxWidth.toPx() }
            val r = with(density) { 11.dp.toPx() }
            Thumb(hueColor, Modifier.offset { IntOffset((hsv[0] / 360f * w - r).roundToInt().coerceIn(0, (w - 2 * r).roundToInt()), 0) })
        }
    }
}

@Composable
private fun Thumb(color: Color, modifier: Modifier) {
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(color)
            .border(3.dp, Color.White, CircleShape),
    )
}

/**
 * A color code to read or type: #RRGGBB, or #AARRGGBB when the color can be see-through ([alpha]). The color
 * changes as soon as a whole code is typed; anything else is left alone until it is.
 */
@Composable
internal fun HexField(color: Int, alpha: Boolean, onChange: (Int) -> Unit) {
    fun code(c: Int) = if (alpha) "#%08X".format(c) else "#%06X".format(c and 0xFFFFFF)
    var text by remember { mutableStateOf(code(color)) }
    var editing by remember { mutableStateOf(false) }
    // Follows the color while you pick it some other way, but not while you're typing
    LaunchedEffect(color) { if (!editing) text = code(color) }
    val focus = LocalFocusManager.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(color)).border(1.dp, Q.Edge, RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                val clean = "#" + typed.trim().removePrefix("#").filter { it.isLetterOrDigit() }.take(if (alpha) 8 else 6).uppercase()
                text = clean
                parseHex(clean, alpha, color)?.let(onChange)
            },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = Q.Text),
            label = { Text(if (alpha) "Color code (#RRGGBB or #AARRGGBB)" else "Color code (#RRGGBB)") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.weight(1f).onFocusChanged {
                editing = it.isFocused
                if (!it.isFocused) text = code(color)
            },
        )
    }
}

/**
 * A typed code as a color: #RGB, #RRGGBB, or (with [alpha]) #AARRGGBB. Six digits keep [current]'s see-through.
 */
internal fun parseHex(text: String, alpha: Boolean, current: Int): Int? {
    val h = text.removePrefix("#")
    if (h.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
    val keep = current and 0xFF000000.toInt()
    return when (h.length) {
        3 -> keep or (h.map { "$it$it" }.joinToString("").toLong(16).toInt())
        6 -> (if (alpha) keep else 0xFF000000.toInt()) or h.toLong(16).toInt()
        8 -> if (alpha) h.toLong(16).toInt() else 0xFF000000.toInt() or (h.toLong(16).toInt() and 0xFFFFFF)
        else -> null
    }
}

/** A row of round swatches to tap. */
@Composable
internal fun SwatchStrip(label: String?, colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    if (colors.isEmpty()) return
    if (label != null) Text(label, color = Q.TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { c ->
            val on = c == selected
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (on) 3.dp else 1.dp, if (on) Color.White else Q.Edge, CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

/** Colors from your wallpaper, to match things to it: its gradient or color, or the main colors of your photo. */
@Composable
internal fun rememberWallpaperColors(): List<Int> {
    val context = LocalContext.current
    val colors by produceState(emptyList<Int>()) { value = withContext(Dispatchers.IO) { wallpaperColors(context) } }
    return colors
}

private fun wallpaperColors(context: Context): List<Int> = runCatching {
    val wall = WallStore.load(context, WallSlot.MAIN)
    val found = when (wall.kind) {
        WallKind.SOLID -> listOf(wall.solid)
        WallKind.IMAGE -> wall.image?.let { photoColors(WallStore.imageFile(context, it).path) }.orEmpty()
        else -> (0 until 5).map { wall.color(it) }
    }
    val withPattern = if (wall.pattern != com.tim.launcher.wallpaper.PatternStyle.NONE) found + wall.patternColor else found
    withPattern.map { it or 0xFF000000.toInt() }.distinct().take(8)
}.getOrDefault(emptyList())

/**
 * The main colors of a photo: it's read tiny, its pixels sorted into coarse color buckets, and the busiest
 * buckets' average colors kept (darkest to lightest), skipping near-duplicates.
 */
private fun photoColors(path: String): List<Int> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 48 && bounds.outHeight / (sample * 2) >= 48) sample *= 2
    val bmp: Bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return emptyList()
    val small = Bitmap.createScaledBitmap(bmp, 48, 48, true)
    if (small !== bmp) bmp.recycle()
    val pixels = IntArray(48 * 48)
    small.getPixels(pixels, 0, 48, 0, 0, 48, 48)
    small.recycle()
    // 4 levels per channel: 64 buckets
    val count = IntArray(64)
    val sum = Array(64) { LongArray(3) }
    pixels.forEach { p ->
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val k = (r / 64) * 16 + (g / 64) * 4 + (b / 64)
        count[k]++
        sum[k][0] += r.toLong(); sum[k][1] += g.toLong(); sum[k][2] += b.toLong()
    }
    val picks = ArrayList<Int>()
    (0 until 64).sortedByDescending { count[it] }.filter { count[it] > 0 }.forEach { k ->
        if (picks.size >= 6) return@forEach
        val n = count[k]
        val c = android.graphics.Color.rgb((sum[k][0] / n).toInt(), (sum[k][1] / n).toInt(), (sum[k][2] / n).toInt())
        val close = picks.any { o ->
            val dr = ((o shr 16) and 0xFF) - ((c shr 16) and 0xFF)
            val dg = ((o shr 8) and 0xFF) - ((c shr 8) and 0xFF)
            val db = (o and 0xFF) - (c and 0xFF)
            dr * dr + dg * dg + db * db < 40 * 40
        }
        if (!close) picks += c
    }
    return picks.sortedBy { c ->
        0.2126 * ((c shr 16) and 0xFF) + 0.7152 * ((c shr 8) and 0xFF) + 0.0722 * (c and 0xFF)
    }
}
