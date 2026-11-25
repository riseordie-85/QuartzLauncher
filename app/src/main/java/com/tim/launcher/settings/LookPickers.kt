package com.tim.launcher.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.AppEntry
import com.tim.launcher.DockStyle
import com.tim.launcher.IconRender
import com.tim.launcher.IconSlot
import com.tim.launcher.Ink
import com.tim.launcher.LocalGlass
import com.tim.launcher.StyledIcon
import com.tim.launcher.dockSurface
import com.tim.launcher.ui.Q
import com.tim.launcher.wallpaper.WallCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A few of your own apps to show presets with, read once. */
@Composable
internal fun rememberSampleApps(count: Int = 5): List<AppEntry> {
    val context = LocalContext.current
    val apps by produceState(emptyList<AppEntry>(), count) { value = withContext(Dispatchers.IO) { sampleApps(context, count) } }
    return apps
}

/** The sample scene (sharp, blurred) at a size, made once and kept: every preset picture shares them. */
private object Scenes {
    private val made = HashMap<Long, Pair<Bitmap, Bitmap>>()

    fun get(w: Int, h: Int): Pair<Bitmap, Bitmap> = synchronized(this) {
        made.getOrPut((w.toLong() shl 32) or h.toLong()) { PreviewScene.make(w, h) }
    }
}

/**
 * A small picture over the sample scene, with frosted glass behind anything inside that wants it (the dock, frosted
 * icons), lined up with the scene wherever the picture is on screen.
 */
@Composable
internal fun SceneBox(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    var at by remember { mutableStateOf<Pair<Offset, IntSize>?>(null) }
    // (Not named "size": that would hide the draw scope's own size in drawBehind below)
    val boxSize = at?.second
    val scene by produceState<Pair<Bitmap, Bitmap>?>(null, boxSize) {
        val sz = boxSize ?: return@produceState
        // A third of full size is plenty for a picture that's mostly behind glass
        value = withContext(Dispatchers.Default) { Scenes.get((sz.width / 3).coerceAtLeast(1), (sz.height / 3).coerceAtLeast(1)) }
    }
    val sharp = remember(scene) { scene?.first?.asImageBitmap() }
    val glass = scene?.let { pics -> at?.let { (o, sz) -> WallCache.Glass(pics.second, sz.width, sz.height, 0.4f, blurred = true, left = o.x, top = o.y) } }
    Box(
        modifier
            .onGloballyPositioned { at = it.positionInWindow() to it.size }
            .drawBehind {
                if (sharp != null) {
                    drawImage(sharp, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Low)
                } else {
                    drawRect(Brush.linearGradient(listOf(Color(0xFF3B2A7A), Color(0xFF244F9E), Color(0xFF14808A), Color(0xFF4E9F3D))))
                }
            },
    ) {
        CompositionLocalProvider(LocalGlass provides glass) { content() }
    }
}

/** One choice in a row of presets: its picture, outlined when it's the one you have, and its name. */
@Composable
private fun PresetChoice(name: String, selected: Boolean, onPick: () -> Unit, picture: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.width(118.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SceneBox(
            Modifier
                .size(118.dp, 72.dp)
                .clip(shape)
                .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Q.Edge, shape)
                .clickable(onClick = onPick),
            content = picture,
        )
        Text(
            name,
            color = if (selected) Q.Text else Q.TextDim,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun PresetStrip(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** A small dock in [look]'s style, along the bottom of a preset picture. */
@Composable
internal fun BoxScope.MiniDock(look: IconRender.Values, apps: List<AppEntry>, icon: Int = 20, count: Int = 4, spec: IconRender.Spec? = null) {
    val style = DockStyle.of(look)
    val panel = Color(look.get(S.panelColor)).copy(alpha = look.get(S.panelOpacity).coerceIn(0.05f, 1f))
    val ink = Ink.on(Ink.brightness(panel, 0.35f))
    val depth = (icon + 12).dp + style.padding.dp * 2 + style.waveRoom + style.outerRoom
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = if (style.floating || !style.background) 7.dp else 0.dp)
            .padding(bottom = if (style.floating || !style.background) 7.dp else 0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .dockSurface(style, panel, DockPosition.BOTTOM, ink, style.slant(depth))
                .padding(horizontal = style.endRoom(depth))
                .padding(top = style.waveRoom, bottom = style.outerRoom)
                .padding(vertical = 6.dp + style.padding.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(count) { i ->
                val app = apps.getOrNull(i)
                when {
                    app != null && spec != null -> StyledIcon(app, icon.dp, spec = spec)
                    app != null -> Image(app.icon, contentDescription = null, modifier = Modifier.size(icon.dp))
                    else -> Box(Modifier.size(icon.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
                }
            }
        }
    }
}

/**
 * Ready-made docks to start from, each drawn with the real dock's drawing code. Picking one sets the dock's shape,
 * fill, pattern, edge and line, and the glass it shares with the drawer and folders; everything stays adjustable.
 */
@Composable
fun DockPresetRow(store: SettingsStore) {
    val apps = rememberSampleApps()
    PresetStrip {
        DockPresets.all.forEach { preset ->
            PresetChoice(preset.name, DockPresets.matches(store, preset), { store.applyLook(DockPresets.keys, preset.look) }) {
                MiniDock(preset.look, apps)
            }
        }
    }
}

/**
 * Ready-made icon styles to start from, drawn on your own apps. Picking one goes into the changes waiting for Apply,
 * for every icon ("Match everywhere" on). Your icon pack stays.
 */
@Composable
fun IconPresetRow(draft: IconDraft) {
    val apps = rememberSampleApps()
    PresetStrip {
        IconPresets.all.forEach { preset ->
            // The preset, with your icon pack
            val values = remember(preset, draft.get(S.iconPackHome), draft.get(S.iconForceShape)) {
                val pack = draft.get(S.iconPackHome)
                val force = draft.get(S.iconForceShape)
                object : IconRender.Values {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T> get(setting: Setting<T>): T = when (setting) {
                        S.iconPackHome -> pack as T
                        S.iconForceShape -> force as T
                        S.iconsMatch -> true as T
                        else -> preset.look.get(setting)
                    }
                    override fun <T> place(own: Setting<T>?, home: Setting<T>): T = get(home)
                }
            }
            val spec = remember(values) { IconRender.specFor(values, IconSlot.HOME) }
            PresetChoice(preset.name, IconPresets.matches(draft, preset), { IconPresets.pick(draft, preset) }) {
                Row(
                    Modifier.align(Alignment.Center).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { i ->
                        val app = apps.getOrNull(i)
                        if (app != null) StyledIcon(app, 30.dp, spec = spec)
                        else Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)))
                    }
                }
            }
        }
    }
}

/**
 * Gradients to start from, matching the wallpaper presets. [from] and [to] are the gradient you have now (only
 * their colors count: picking a gradient keeps how see-through each end is).
 */
@Composable
fun GradientPresetRow(from: Int, to: Int, onPick: (from: Int, to: Int) -> Unit) {
    fun rgb(c: Int) = c and 0x00FFFFFF
    // An end that's almost clear (frosted glass's faint tint) comes back solid, or the gradient wouldn't show
    fun keep(old: Int, c: Int) = (if ((old ushr 24) < 0x40) 0xFF000000.toInt() else old and 0xFF000000.toInt()) or rgb(c)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GradientPresets.all.forEach { g ->
            val on = rgb(from) == rgb(g.from) && rgb(to) == rgb(g.to)
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(g.from), Color(g.to))))
                        .border(if (on) 3.dp else 1.dp, if (on) Color.White else Q.Edge, CircleShape)
                        .clickable {
                            onPick(keep(from, g.from), keep(to, g.to))
                        },
                )
                Text(
                    g.name,
                    color = if (on) Q.Text else Q.TextDim,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
