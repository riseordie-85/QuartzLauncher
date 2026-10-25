package com.tim.launcher.wizard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tim.launcher.DockStyle
import com.tim.launcher.HomeLook
import com.tim.launcher.Ink
import com.tim.launcher.LocalHomeLook
import com.tim.launcher.WidgetKind
import com.tim.launcher.WidgetView
import com.tim.launcher.settings.DeviceType
import com.tim.launcher.settings.DrawerScroll
import com.tim.launcher.settings.DrawerStyle
import com.tim.launcher.settings.DrawerView
import com.tim.launcher.settings.GestureSlot
import com.tim.launcher.settings.S
import com.tim.launcher.settings.SettingsStore
import com.tim.launcher.ui.Q
import kotlin.math.floor

/*
 * Little moving pictures for setup's questions about the app drawer and widgets: a home screen with an arrow
 * showing the swipe, the drawer as icons or a list, how it scrolls, how it opens, and where a widget will go.
 * They're drawn with simple shapes (not your real apps), so they're quick and the same on every device.
 */

private val Tiles = listOf(
    Color(0xFFE5675B), Color(0xFFF2A541), Color(0xFF5BB974), Color(0xFF4F8EF7),
    Color(0xFF9B72F2), Color(0xFFE56BB0), Color(0xFF3FB6C6), Color(0xFFB0B5C0),
)

/** A small screen the shape of this device (a landscape tablet, or a phone held upright). */
@Composable
private fun MiniScreen(type: DeviceType, height: Dp = 160.dp, content: @Composable BoxScope.() -> Unit) {
    val landscape = type == DeviceType.TABLET
    val width = if (landscape) height * 1.6f else height * 0.52f
    Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width, height)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2B2252), Color(0xFF4A2F6E), Color(0xFF1F3A5F))))
                .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .clipToBounds(),
            content = content,
        )
    }
}

/** A few icons and a dock, standing in for a home screen. */
@Composable
private fun BoxScope.MiniHome(cols: Int) {
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
        repeat(2) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(cols) { c -> Tile(Tiles[(r * cols + c) % Tiles.size], 14.dp) }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.16f)).padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) { repeat(cols) { Tile(Tiles[(it + 3) % Tiles.size], 14.dp) } }
    }
}

@Composable
private fun Tile(color: Color, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(color))
}

/** "Which way should you swipe?": an arrow (and a finger) sweeping across the home screen in that direction. */
@Composable
fun SwipePreview(type: DeviceType, slot: GestureSlot?) {
    val loop = rememberInfiniteTransition(label = "swipe")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "t")
    MiniScreen(type) {
        MiniHome(if (type == DeviceType.TABLET) 6 else 4)
        if (slot == null) return@MiniScreen
        // Moves over the first 70% of the loop, fading in and out at the ends
        val p = (t / 0.7f).coerceIn(0f, 1f)
        val alpha = when {
            t > 0.7f -> 0f
            p < 0.15f -> p / 0.15f
            p > 0.85f -> (1f - p) / 0.15f
            else -> 1f
        }
        val travel = 50.dp
        val (dx, dy, rotation) = when (slot) {
            GestureSlot.SWIPE_UP -> Triple(0.dp, travel * (0.5f - p), 0f)
            GestureSlot.SWIPE_DOWN -> Triple(0.dp, travel * (p - 0.5f), 180f)
            GestureSlot.SWIPE_PAST_LEFT -> Triple(travel * (0.5f - p), 0.dp, 270f)
            else -> Triple(travel * (p - 0.5f), 0.dp, 90f)
        }
        Box(
            Modifier.align(Alignment.Center).offset(dx, dy).alpha(alpha),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Q.Accent.copy(alpha = 0.85f)))
            Icon(
                Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = rotation },
            )
            Icon(
                Icons.Rounded.TouchApp,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp).offset(14.dp, 20.dp),
            )
        }
    }
}

/** A panel standing in for the drawer, holding icons in a grid or names in a list. */
@Composable
private fun DrawerPanel(view: DrawerView, modifier: Modifier, rows: Int = 6, offsetRows: Float = 0f, cols: Int = 4) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE61E1B2E))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(8.dp)
            .clipToBounds(),
    ) {
        Column(Modifier.offset(y = (-offsetRows * 22).dp)) {
            repeat(rows) { r ->
                if (view == DrawerView.ICONS) {
                    Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        // Colors repeat every two rows, so a list scrolling by two rows loops without a jump
                        repeat(cols) { c -> Tile(Tiles[((r % 2) * cols + c) % Tiles.size], 14.dp) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Tiles[r % Tiles.size]))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.height(5.dp).fillMaxWidth(0.4f + (r % 3) * 0.15f).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)))
                    }
                }
            }
        }
    }
}

/** "How should your apps appear?": the drawer as icons or as a list. */
@Composable
fun DrawerViewPreview(type: DeviceType, view: DrawerView) {
    MiniScreen(type) {
        AnimatedContent(view, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "view") { v ->
            DrawerPanel(v, Modifier.fillMaxSize().padding(10.dp), rows = 7, cols = if (type == DeviceType.TABLET) 7 else 4)
        }
    }
}

/** "How should the app list scroll?": rows gliding up forever, or whole pages sliding sideways with dots. */
@Composable
fun ScrollPreview(type: DeviceType, scroll: DrawerScroll) {
    val loop = rememberInfiniteTransition(label = "scroll")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "t")
    val cols = if (type == DeviceType.TABLET) 7 else 4
    MiniScreen(type) {
        if (scroll == DrawerScroll.VERTICAL) {
            // Two rows' worth of travel per loop, wrapping seamlessly
            DrawerPanel(DrawerView.ICONS, Modifier.fillMaxSize().padding(10.dp), rows = 12, offsetRows = t * 2f, cols = cols)
            Icon(
                Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp).size(16.dp).graphicsLayer { rotationZ = 180f },
            )
        } else {
            // Three pages: rest, slide, rest, slide…
            val pages = 3
            val step = t * pages
            val page = floor(step).toInt() % pages
            val local = step - floor(step)
            val slide = if (local < 0.7f) 0f else (local - 0.7f) / 0.3f
            BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
                val w = maxWidth
                Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).offset(x = -(w * (page + slide))).width(w * (pages + 1))) {
                    repeat(pages + 1) { i ->
                        DrawerPanel(DrawerView.ICONS, Modifier.width(w).height(maxHeight - 14.dp).padding(horizontal = 2.dp), rows = 5, cols = cols, offsetRows = (i % pages) * 0.5f)
                    }
                }
                Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    val current = if (slide > 0.5f) (page + 1) % pages else page
                    repeat(pages) { i ->
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == current) 0.95f else 0.35f)))
                    }
                }
            }
        }
    }
}

/** "How should your app drawer open up?": the home screen, and the drawer opening over it in that style. */
@Composable
fun OpenStylePreview(type: DeviceType, style: DrawerStyle) {
    val loop = rememberInfiniteTransition(label = "open")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "t")
    // Opens, stays a moment, closes, and the home screen shows a moment
    val raw = when {
        t < 0.25f -> t / 0.25f
        t < 0.65f -> 1f
        t < 0.85f -> 1f - (t - 0.65f) / 0.2f
        else -> 0f
    }
    val p = raw * raw * (3 - 2 * raw) // eased
    val cols = if (type == DeviceType.TABLET) 6 else 4
    MiniScreen(type) {
        MiniHome(cols)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            when (style) {
                DrawerStyle.FULL -> DrawerPanel(
                    DrawerView.ICONS,
                    Modifier.width(w).height(h - 34.dp).offset(y = (h - 34.dp) * (1f - p)).alpha(p.coerceAtLeast(0.01f)),
                    cols = cols,
                )
                DrawerStyle.FLOATING -> DrawerPanel(
                    DrawerView.ICONS,
                    Modifier.align(Alignment.Center).width(w * 0.72f).height(h * 0.62f)
                        .graphicsLayer { scaleX = 0.85f + 0.15f * p; scaleY = 0.85f + 0.15f * p; alpha = p },
                    cols = if (type == DeviceType.TABLET) 5 else 3,
                )
                DrawerStyle.SIDE -> DrawerPanel(
                    DrawerView.LIST,
                    Modifier.width(w * 0.48f).fillMaxSize().padding(6.dp).offset(x = -(w * 0.5f) * (1f - p)).alpha(p.coerceAtLeast(0.01f)),
                )
                DrawerStyle.BOTTOM -> DrawerPanel(
                    DrawerView.ICONS,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp).width(w * 0.8f).height(h * 0.5f)
                        .offset(y = h * 0.6f * (1f - p)).alpha(p.coerceAtLeast(0.01f)),
                    cols = if (type == DeviceType.TABLET) 5 else 3,
                )
            }
        }
    }
}

/**
 * "Let's add a few widgets": your home page as it'll start, with the widget you picked across the top (the middle
 * three columns on a tablet), the tips card and a row of apps along the bottom.
 */
@Composable
fun HomeWidgetPreview(store: SettingsStore, type: DeviceType, kind: WidgetKind?) {
    val panel = Color(store[S.panelColor]).copy(alpha = store[S.panelOpacity].coerceIn(0.05f, 1f))
    val look = HomeLook(panel = panel, dock = DockStyle.of(store), onPanel = Ink.on(Ink.brightness(panel, 0.3f)))
    val cols = store[S.gridCols, type.primary].coerceAtLeast(3)
    val rows = store[S.gridRows, type.primary].coerceAtLeast(3)
    MiniScreen(type, height = if (type == DeviceType.TABLET) 200.dp else 230.dp) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
            val cw = maxWidth / cols
            val ch = maxHeight / rows
            // Empty cells
            for (r in 0 until rows) for (c in 0 until cols) {
                Box(Modifier.offset(cw * c, ch * r).size(cw, ch), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
                }
            }
            // A row of apps along the bottom
            for (c in 0 until cols) {
                Box(Modifier.offset(cw * c, ch * (rows - 1)).size(cw, ch), contentAlignment = Alignment.Center) {
                    Tile(Tiles[c % Tiles.size], minOf(cw, ch) * 0.55f)
                }
            }
            CompositionLocalProvider(LocalHomeLook provides look) {
                val tablet = cols >= 6
                if (kind != null) {
                    val w = if (tablet) 3 else minOf(kind.w, cols)
                    val h = minOf(if (tablet && kind == WidgetKind.FLIP_WEATHER) 3 else kind.h, rows)
                    WidgetView(kind, Modifier.offset(cw * ((cols - w) / 2), 0.dp).size(cw * w, ch * h).padding(2.dp), sample = true)
                }
                val tw = if (tablet) 3 else minOf(4, cols)
                val th = minOf(2, rows - 1)
                WidgetView(
                    WidgetKind.TIPS,
                    Modifier.offset(cw * ((cols - tw) / 2), ch * (rows - 1 - th)).size(cw * tw, ch * th).padding(2.dp),
                    sample = true,
                )
            }
        }
    }
}
