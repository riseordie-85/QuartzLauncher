package com.tim.launcher.settings

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tim.launcher.Prefs
import com.tim.launcher.ui.BodyText
import com.tim.launcher.ui.Dropdown
import com.tim.launcher.ui.GlassPanel
import com.tim.launcher.ui.PrimaryButton
import com.tim.launcher.ui.Q
import com.tim.launcher.ui.RowDivider
import com.tim.launcher.ui.ScreenTitle
import com.tim.launcher.ui.SectionLabel
import com.tim.launcher.ui.Segmented
import com.tim.launcher.ui.SettingRow
import com.tim.launcher.ui.SliderRow
import com.tim.launcher.ui.Toggle
import com.tim.launcher.wallpaper.FoldMode
import com.tim.launcher.wallpaper.Frame
import com.tim.launcher.wallpaper.GradientStyle
import com.tim.launcher.wallpaper.PatternStyle
import com.tim.launcher.wallpaper.ScreenKind
import com.tim.launcher.wallpaper.WallCache
import com.tim.launcher.wallpaper.WallConfig
import com.tim.launcher.wallpaper.WallKind
import com.tim.launcher.wallpaper.WallPresets
import com.tim.launcher.wallpaper.WallRenderer
import com.tim.launcher.wallpaper.WallSlot
import com.tim.launcher.wallpaper.WallStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Colors offered for gradients, solids and patterns: darks first, then brights, then lights. */
private val WallColors: List<Int> = listOf(
    0xFF0B0B10, 0xFF1E1B2E, 0xFF14203F, 0xFF14324A, 0xFF0F3B36, 0xFF3A1C5C,
    0xFF4B1D7A, 0xFF2546A8, 0xFF138A8A, 0xFF4E9F3D, 0xFFC9A227, 0xFFE07A2E,
    0xFFB5382A, 0xFF6B2D6E, 0xFFE8EEF5, 0xFFF4F1EA, 0xFFFFFFFF,
).map { it.toInt() }

// ---- Wallpaper screen ----

@Composable
fun WallpaperSettings(ctx: SettingsContext) {
    val context = LocalContext.current
    val foldable = ctx.type == DeviceType.FOLDABLE
    var active by remember { mutableStateOf(WallCache.isActive(context)) }
    LifecycleResumeEffect(Unit) {
        active = WallCache.isActive(context)
        onPauseOrDispose { }
    }
    var mode by remember { mutableStateOf(WallStore.mode(context)) }
    var slot by remember { mutableStateOf(WallSlot.MAIN) }
    val editing = if (foldable && mode == FoldMode.SEPARATE) slot else WallSlot.MAIN
    var cfg by remember(editing) { mutableStateOf(WallStore.load(context, editing)) }
    fun update(c: WallConfig) {
        cfg = c
        WallStore.save(context, editing, c)
    }
    // Only one color editor is open at a time
    var openColor by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(ctx) {
        ScreenTitle("Wallpaper", "Quartz draws it, so the glass behind your dock and drawer matches it exactly")
        Spacer(Modifier.height(16.dp))
        WallpaperPreview(foldable, Modifier.fillMaxWidth())

        if (!active) {
            Spacer(Modifier.height(16.dp))
            UseQuartzWallpaper()
        }

        if (foldable) {
            SectionLabel("Across both screens")
            GlassPanel {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Segmented(mode, FoldMode.entries, { it.label.replace(" · ", "\n") }) {
                        mode = it
                        WallStore.setMode(context, it)
                    }
                    Text(mode.about, color = Q.TextDim, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
                    if (mode == FoldMode.SEPARATE) {
                        Text("Editing", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(top = 16.dp, bottom = 10.dp))
                        Segmented(slot, listOf(WallSlot.COVER, WallSlot.MAIN), { if (it == WallSlot.COVER) "Cover screen" else "Unfolded" }) { slot = it }
                    }
                }
            }
        }

        SectionLabel("Start from")
        PresetRow(selected = cfg) { preset -> update(preset.copy(pattern = cfg.pattern, patternColor = cfg.patternColor, patternStrength = cfg.patternStrength, patternSize = cfg.patternSize, dim = cfg.dim)) }

        SectionLabel("Wallpaper")
        GlassPanel {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Segmented(cfg.kind, WallKind.entries, { it.label }) { update(cfg.copy(kind = it)) }
            }
            when (cfg.kind) {
                WallKind.GRADIENT -> GradientControls(cfg, openColor, { openColor = it }) { update(it) }
                WallKind.SOLID -> ColorEditor("Color", cfg.solid, openColor == "solid", { openColor = if (openColor == "solid") null else "solid" }) {
                    update(cfg.copy(solid = it))
                }
                WallKind.IMAGE -> ImageControls(cfg) { update(it) }
                WallKind.TEST -> BodyText(
                    "The phase 0 test pattern: shows which screen it's on and how the halves line up.",
                    Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                )
            }
        }

        if (cfg.kind != WallKind.TEST) {
            SectionLabel("Pattern")
            GlassPanel {
                SettingRow("Pattern", "Drawn over the wallpaper") {
                    Dropdown(cfg.pattern, PatternStyle.entries, { it.label }) { update(cfg.copy(pattern = it)) }
                }
                if (cfg.pattern != PatternStyle.NONE) {
                    RowDivider()
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        val light = Color(cfg.patternColor).luminance() > 0.5f
                        Segmented(light, listOf(true, false), { if (it) "Light" else "Dark" }) {
                            update(cfg.copy(patternColor = if (it) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()))
                        }
                    }
                    SliderRow("Strength", cfg.patternStrength, 0.03f..0.5f, "${(cfg.patternStrength * 100).roundToInt()}%") {
                        update(cfg.copy(patternStrength = it))
                    }
                    SliderRow("Size", cfg.patternSize, 0f..1f, "${(cfg.patternSize * 100).roundToInt()}%") {
                        update(cfg.copy(patternSize = it))
                    }
                }
            }

            SectionLabel("Readability")
            GlassPanel {
                SliderRow("Darken", cfg.dim, 0f..0.6f, if (cfg.dim < 0.01f) "Off" else "${(cfg.dim * 100).roundToInt()}%") {
                    update(cfg.copy(dim = it))
                }
                BodyText(
                    "Labels and the status bar switch to dark text on light wallpapers on their own.",
                    Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                )
            }
        }
    }
}

/** The button that sets Quartz as the wallpaper (Android asks you to confirm). */
@Composable
fun UseQuartzWallpaper(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    GlassPanel(modifier) {
        Column(Modifier.padding(20.dp)) {
            Text("Quartz isn't your wallpaper yet", color = Q.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "Frosted glass and fold matching need it. Android shows a preview; tap Set wallpaper there.",
                color = Q.TextDim,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
            PrimaryButton("Use Quartz wallpaper") {
                if (!WallCache.openPicker(context)) {
                    Toast.makeText(context, "This phone didn't open the wallpaper picker", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

// ---- Preview ----

/** Sizes of this device's screens in px: cover and unfolded on a foldable, otherwise just the one. */
private fun screenSizes(context: Context, foldable: Boolean): List<Pair<Int, Int>> {
    val prefs = Prefs.get(context)
    if (!foldable) {
        val m = context.resources.displayMetrics
        return listOf(m.widthPixels to m.heightPixels)
    }
    val cw = prefs.getInt(Prefs.COVER_W, 0).takeIf { it > 0 } ?: 1080
    val ch = prefs.getInt(Prefs.COVER_H, 0).takeIf { it > 0 } ?: 2520
    val iw = prefs.getInt(Prefs.INNER_W, 0).takeIf { it > 0 } ?: 2448
    val ih = prefs.getInt(Prefs.INNER_H, 0).takeIf { it > 0 } ?: 1848
    return listOf(cw to ch, iw to ih)
}

/** What the wallpaper looks like on each screen, side by side, redrawn a moment after every change. */
@Composable
fun WallpaperPreview(foldable: Boolean, modifier: Modifier = Modifier, height: Dp = 170.dp) {
    val context = LocalContext.current
    val generation = WallCache.generation
    val sizes = remember(foldable) { screenSizes(context, foldable) }
    val images by produceState<List<ImageBitmap>?>(null, generation, sizes) {
        delay(40) // let a slider settle before drawing
        value = withContext(Dispatchers.Default) {
            sizes.map { (w, h) -> WallCache.preview(context, w, h, longest = 420).asImageBitmap() }
        }
    }
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        sizes.forEachIndexed { i, (w, h) ->
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .height(height)
                    .width(height * (w.toFloat() / h))
                    .clip(shape)
                    .background(Q.Glass)
                    .border(1.dp, Q.Edge, shape),
            ) {
                images?.getOrNull(i)?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
                if (foldable) {
                    Text(
                        if (i == 0) "Cover" else "Unfolded",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** A row of ready-made wallpapers to start from. */
@Composable
fun PresetRow(selected: WallConfig?, onPick: (WallConfig) -> Unit) {
    val thumbs by produceState<List<Bitmap>?>(null) {
        value = withContext(Dispatchers.Default) {
            WallPresets.all.map { p ->
                WallRenderer.renderSmall(300, 400, 0.25f, Frame(p.config, 0.75f, 0f, 1f, ScreenKind.SINGLE, FoldMode.STRETCHED)) { null }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WallPresets.all.forEachIndexed { i, preset ->
            val on = selected != null && selected.kind == preset.config.kind && selected.gradient == preset.config.gradient &&
                selected.colors == preset.config.colors && selected.angle == preset.config.angle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val shape = RoundedCornerShape(12.dp)
                Box(
                    Modifier
                        .size(64.dp, 86.dp)
                        .clip(shape)
                        .background(Q.Glass)
                        .border(if (on) 3.dp else 1.dp, if (on) Color.White else Q.Edge, shape)
                        .clickable { onPick(preset.config) },
                ) {
                    thumbs?.getOrNull(i)?.let {
                        Image(it.asImageBitmap(), contentDescription = preset.name, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    }
                }
                Text(preset.name, color = Q.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ---- Gradient ----

@Composable
private fun GradientControls(cfg: WallConfig, open: String?, onOpen: (String?) -> Unit, update: (WallConfig) -> Unit) {
    fun toggle(id: String) = onOpen(if (open == id) null else id)
    RowDivider()
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Segmented(cfg.gradient, GradientStyle.entries, { it.label }) { update(cfg.copy(gradient = it)) }
    }
    when (cfg.gradient) {
        GradientStyle.LINEAR -> {
            ColorEditor("Start", cfg.color(0), open == "c0", { toggle("c0") }) { update(cfg.withColor(0, it)) }
            SettingRow("Middle color") { Toggle(cfg.linearMiddle) { update(cfg.copy(linearMiddle = it)) } }
            if (cfg.linearMiddle) ColorEditor("Middle", cfg.color(1), open == "c1", { toggle("c1") }) { update(cfg.withColor(1, it)) }
            ColorEditor("End", cfg.color(2), open == "c2", { toggle("c2") }) { update(cfg.withColor(2, it)) }
            SliderRow("Direction", cfg.angle, 0f..360f, "${cfg.angle.roundToInt()}°") { update(cfg.copy(angle = it)) }
        }
        GradientStyle.RADIAL -> {
            ColorEditor("Center", cfg.color(0), open == "c0", { toggle("c0") }) { update(cfg.withColor(0, it)) }
            ColorEditor("Edge", cfg.color(2), open == "c2", { toggle("c2") }) { update(cfg.withColor(2, it)) }
            SliderRow("Size", cfg.radialSize, 0.3f..1.6f, "${(cfg.radialSize * 100).roundToInt()}%") { update(cfg.copy(radialSize = it)) }
            SliderRow("Across", cfg.radialX, 0f..1f, "${(cfg.radialX * 100).roundToInt()}%") { update(cfg.copy(radialX = it)) }
            SliderRow("Up and down", cfg.radialY, 0f..1f, "${(cfg.radialY * 100).roundToInt()}%") { update(cfg.copy(radialY = it)) }
        }
        GradientStyle.CORNERS -> {
            var corner by remember { mutableStateOf(0) }
            CornerPicker(cfg, corner) {
                corner = it
                onOpen("corner")
            }
            val names = listOf("Top left", "Top right", "Bottom left", "Bottom right", "Middle")
            ColorEditor(names[corner], cfg.color(corner), open == "corner", { toggle("corner") }) { update(cfg.withColor(corner, it)) }
        }
    }
}

/** Five dots (four corners and the middle); tap one to change its color. */
@Composable
private fun CornerPicker(cfg: WallConfig, selected: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        val w = 200.dp
        val h = 120.dp
        Box(
            Modifier
                .size(w, h)
                .clip(RoundedCornerShape(14.dp))
                .background(Q.Glass)
                .border(1.dp, Q.Edge, RoundedCornerShape(14.dp)),
        ) {
            val dot = 34.dp
            val spots = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f, 0.5f to 0.5f)
            spots.forEachIndexed { i, (fx, fy) ->
                val on = i == selected
                Box(
                    Modifier
                        .offset(x = (w - dot - 16.dp) * fx + 8.dp, y = (h - dot - 16.dp) * fy + 8.dp)
                        .size(dot)
                        .clip(CircleShape)
                        .background(Color(cfg.color(i)))
                        .border(if (on) 3.dp else 1.dp, if (on) Color.White else Q.Edge, CircleShape)
                        .clickable { onSelect(i) },
                )
            }
        }
    }
}

// ---- Image ----

@Composable
private fun ImageControls(cfg: WallConfig, update: (WallConfig) -> Unit) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            WallCache.importImage(context, uri) { name ->
                busy = false
                if (name != null) {
                    update(cfg.copy(kind = WallKind.IMAGE, image = name, focusX = 0.5f, focusY = 0.5f, zoom = 1f))
                } else {
                    Toast.makeText(context, "Quartz couldn't read that image", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        PrimaryButton(
            when {
                busy -> "Saving…"
                cfg.image == null -> "Choose a photo"
                else -> "Choose another photo"
            },
            enabled = !busy,
        ) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        Text(
            "Android's photo picker shares only the photo you pick. Quartz keeps a copy on this phone; it never leaves it.",
            color = Q.TextDim,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
        )
    }
    if (cfg.image != null) {
        RowDivider()
        SliderRow("Across", cfg.focusX, 0f..1f, "${(cfg.focusX * 100).roundToInt()}%") { update(cfg.copy(focusX = it)) }
        SliderRow("Up and down", cfg.focusY, 0f..1f, "${(cfg.focusY * 100).roundToInt()}%") { update(cfg.copy(focusY = it)) }
        SliderRow("Zoom", cfg.zoom, 1f..3f, "${(cfg.zoom * 100).roundToInt()}%") { update(cfg.copy(zoom = it)) }
    }
}

// ---- Colors ----

/**
 * One color: its name and a swatch. Tap to open a palette plus hue, saturation and brightness sliders for any
 * color in between.
 */
@Composable
fun ColorEditor(label: String, color: Int, open: Boolean, onToggle: () -> Unit, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Q.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(color)).border(1.dp, Q.Edge, CircleShape))
        }
        if (open) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WallColors.forEach { c ->
                    val on = c == color
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (on) 3.dp else 1.dp, if (on) Color.White else Q.Edge, CircleShape)
                            .clickable { onChange(c) },
                    )
                }
            }
            val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) } }
            fun set(i: Int, v: Float) {
                val next = hsv.copyOf()
                next[i] = v
                onChange(android.graphics.Color.HSVToColor(next))
            }
            SliderRow("Hue", hsv[0], 0f..360f, "${hsv[0].roundToInt()}°") { set(0, it) }
            SliderRow("Saturation", hsv[1], 0f..1f, "${(hsv[1] * 100).roundToInt()}%") { set(1, it) }
            SliderRow("Brightness", hsv[2], 0f..1f, "${(hsv[2] * 100).roundToInt()}%") { set(2, it) }
        }
        RowDivider()
    }
}
