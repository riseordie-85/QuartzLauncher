package com.tim.launcher

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.settings.Route
import com.tim.launcher.settings.SettingsActivity
import com.tim.launcher.ui.Q
import com.tim.launcher.weather.Weather
import kotlin.math.roundToInt

/** Room around a widget inside its cells, so it lines up with the icons beside it. */
val WIDGET_PAD = 6.dp

/** A widget's block of cells: top-left column and row, width and height. */
private data class Block(val c: Int, val r: Int, val w: Int, val h: Int)

/**
 * The widgets on one page of the grid, each drawn over the cells it covers. [base] is the page's first slot;
 * [cols] is the page's column count as drawn (the same as the saved grid here).
 */
@Composable
internal fun WidgetLayer(base: Int, items: List<String?>, cols: Int, cellW: Dp, cellH: Dp, vm: HomeViewModel, drag: DragController) {
    val seen = HashSet<String>()
    for (i in base until base + vm.perPage) {
        val k = items.getOrNull(i) ?: continue
        if (!isWidgetKey(k) || !seen.add(k)) continue
        val widget = vm.widgetFor(k) ?: continue
        key(k) {
            WidgetOnGrid(widget, i, Block((i - base) % cols, (i - base) / cols, widget.w, widget.h), cellW, cellH, vm, drag)
        }
    }
}

/**
 * A widget on the grid. Tap it to open the clock (or set up weather); hold and drag to move it; hold and let go for
 * its menu. While it's being resized it shows handles on each edge that snap to the grid.
 */
@Composable
private fun WidgetOnGrid(widget: HomeWidget, anchor: Int, block: Block, cellW: Dp, cellH: Dp, vm: HomeViewModel, drag: DragController) {
    val key = WIDGET_PREFIX + widget.id
    val ref = SlotRef(Area.COVER, anchor)
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val resizing = drag.resizing == widget.id
    var edit by remember(widget, anchor, resizing) { mutableStateOf(block) }
    val shown = if (resizing) edit else block
    var origin by remember { mutableStateOf(Offset.Zero) }
    val carried = drag.active && drag.key == key

    // Keyed on the kind too: changing its style must change what a tap does
    val gestures = if (resizing) Modifier else Modifier
        .pointerInput(widget.id, widget.kind, anchor) {
            detectTapGestures(
                onTap = { widgetTap(context, widget.kind) },
                onLongPress = { }, // long press belongs to moving it
            )
        }
        .pointerInput(widget.id, anchor) {
            var moved = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    moved = 0f
                    drag.startWidget(ref, key, origin + local, grab = local, size = Size(size.width.toFloat(), size.height.toFloat()))
                },
                onDrag = { change, amount ->
                    change.consume()
                    moved += amount.getDistance()
                    drag.moveBy(amount)
                },
                onDragEnd = {
                    // Held without moving: the widget's menu
                    if (moved < viewConfiguration.touchSlop * 2) {
                        drag.cancel()
                        drag.widgetMenu = widget.id
                    } else {
                        drag.drop(vm)
                    }
                },
                onDragCancel = { drag.cancel() },
            )
        }

    Box(
        Modifier
            .offset(cellW * shown.c, cellH * shown.r)
            .width(cellW * shown.w)
            .height(cellH * shown.h)
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .alpha(if (carried) 0f else 1f)
            .then(gestures),
    ) {
        WidgetView(widget.kind, Modifier.fillMaxSize().padding(WIDGET_PAD))
        if (resizing) {
            val density = LocalDensity.current
            val cw = with(density) { cellW.toPx() }
            val ch = with(density) { cellH.toPx() }
            ResizeFrame(
                start = block,
                cols = vm.cols,
                rows = vm.rows,
                minW = minOf(widget.kind.minW, vm.cols),
                minH = minOf(widget.kind.minH, vm.rows),
                cellW = cw,
                cellH = ch,
                onChange = { edit = it },
                onDone = { b ->
                    val page = anchor / vm.perPage
                    val ok = vm.resizeWidget(widget.id, page * vm.perPage + b.r * vm.cols + b.c, b.w, b.h)
                    if (!ok) edit = block
                },
            )
        }
    }
}

/**
 * The outline and four handles shown while resizing. Dragging a handle moves that edge a whole cell at a time,
 * never smaller than the widget's smallest size or off the page.
 */
@Composable
private fun BoxScope.ResizeFrame(
    start: Block,
    cols: Int,
    rows: Int,
    minW: Int,
    minH: Int,
    cellW: Float,
    cellH: Float,
    onChange: (Block) -> Unit,
    onDone: (Block) -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .matchParentSize()
            .padding(2.dp)
            .border(2.dp, Q.Accent, shape)
            .background(Q.Accent.copy(alpha = 0.08f), shape),
    )
    // Each handle works from the block as it was when the drag began
    @Composable
    fun Handle(alignment: Alignment, resize: (Block, Float, Float) -> Block) {
        var current by remember(start) { mutableStateOf(start) }
        Box(
            Modifier
                .align(alignment)
                .size(44.dp)
                .pointerInput(start) {
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f; current = start },
                        onDrag = { change, amount ->
                            change.consume()
                            dx += amount.x
                            dy += amount.y
                            val next = resize(start, dx, dy)
                            if (next != current) {
                                current = next
                                onChange(next)
                            }
                        },
                        onDragEnd = { onDone(current) },
                        onDragCancel = { onChange(start) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White).border(3.dp, Q.Accent, CircleShape))
        }
    }
    // A widget narrowed by a smaller grid can be under its smallest size: it can't shrink further, but never
    // gets an empty range (coerceIn throws when min > max)
    Handle(Alignment.CenterEnd) { b, dx, _ -> b.copy(w = (b.w + (dx / cellW).roundToInt()).coerceIn(minOf(minW, b.w), cols - b.c)) }
    Handle(Alignment.BottomCenter) { b, _, dy -> b.copy(h = (b.h + (dy / cellH).roundToInt()).coerceIn(minOf(minH, b.h), rows - b.r)) }
    Handle(Alignment.CenterStart) { b, dx, _ ->
        val d = (dx / cellW).roundToInt().coerceIn(-b.c, maxOf(0, b.w - minW))
        b.copy(c = b.c + d, w = b.w - d)
    }
    Handle(Alignment.TopCenter) { b, _, dy ->
        val d = (dy / cellH).roundToInt().coerceIn(-b.r, maxOf(0, b.h - minH))
        b.copy(r = b.r + d, h = b.h - d)
    }
}

/**
 * A widget on a reflowed page (the unfolded screen turned upright, or a grid turned sideways): widgets sit above
 * the icons, full width. Tap works as usual; hold for its menu (moving and resizing happen on the normal grid).
 */
@Composable
internal fun WidgetOnFlow(widget: HomeWidget, width: Dp, height: Dp, drag: DragController) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .width(width)
            .height(height)
            .pointerInput(widget.id, widget.kind) {
                detectTapGestures(
                    onTap = { widgetTap(context, widget.kind) },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        drag.widgetMenu = widget.id
                    },
                )
            },
    ) {
        WidgetView(widget.kind, Modifier.fillMaxSize().padding(WIDGET_PAD))
    }
}

/** Tapping a widget: the clock opens your alarms; weather that isn't set up yet opens its settings. */
private fun widgetTap(context: Context, kind: WidgetKind) {
    if (kind.weather && Weather.now == null) {
        SettingsActivity.open(context, route = Route.WIDGETS)
        return
    }
    if (kind == WidgetKind.WEATHER) {
        Weather.refresh(context, force = true)
        Toast.makeText(context, "Updating the weather", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Diag.log("No clock app to open: ${it.message}") }
}

/**
 * While a widget is carried: an outline where it would land, and the widget itself under your finger.
 */
@Composable
internal fun CarriedWidget(widget: HomeWidget, vm: HomeViewModel, drag: DragController) {
    val density = LocalDensity.current
    val spot = drag.widgetDrop(vm)
    val cell = spot?.let { drag.bounds[SlotRef(Area.COVER, it)] }
    if (cell != null && !drag.overRemove) {
        val shape = RoundedCornerShape(26.dp)
        Box(
            Modifier
                .offset { IntOffset(cell.left.roundToInt(), cell.top.roundToInt()) }
                .size(with(density) { (cell.width * widget.w).toDp() }, with(density) { (cell.height * widget.h).toDp() })
                .padding(WIDGET_PAD)
                .background(Q.Accent.copy(alpha = 0.14f), shape)
                .border(2.dp, Q.Accent.copy(alpha = 0.9f), shape),
        )
    }
    val size = drag.widgetSize
    WidgetView(
        widget.kind,
        Modifier
            .offset {
                IntOffset(
                    (drag.pointer.x - drag.widgetGrab.x).roundToInt(),
                    (drag.pointer.y - drag.widgetGrab.y).roundToInt(),
                )
            }
            .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
            .padding(WIDGET_PAD)
            .graphicsLayer {
                scaleX = 1.04f
                scaleY = 1.04f
                alpha = if (drag.overRemove) 0.55f else 0.92f
            },
    )
}

/** Shown at the top while resizing a widget. */
@Composable
internal fun ResizeBar(onDone: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars).padding(top = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xE61A1A22))
                .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Drag the edges to resize", color = Color.White, fontSize = 15.sp)
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Q.Accent)
                    .clickable { onDone() }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) { Text("Done", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

/** A held widget's menu: resize, change style, its settings, or remove it. */
@Composable
internal fun WidgetMenu(
    widget: HomeWidget,
    canResize: Boolean,
    onResize: () -> Unit,
    onKind: (WidgetKind) -> Unit,
    onSettings: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var styles by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.82f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF0221F2E))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(vertical = 8.dp),
        ) {
            Text(
                widget.kind.label,
                color = Q.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp),
            )
            if (canResize) MenuRow("Resize", Icons.Rounded.AspectRatio, onClick = onResize)
            MenuRow("Change style", Icons.Rounded.Palette) { styles = !styles }
            if (styles) {
                WidgetKind.entries.filter { it != widget.kind }.forEach { kind ->
                    Text(
                        kind.label,
                        color = Q.Text,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onKind(kind) }
                            .padding(start = 60.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
            MenuRow("Widget settings", Icons.Rounded.Settings, onClick = onSettings)
            MenuRow("Remove", Icons.Rounded.DeleteOutline, color = Q.Danger, onClick = onRemove)
        }
    }
}

@Composable
private fun MenuRow(text: String, icon: ImageVector, color: Color = Q.Text, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(text, color = color, fontSize = 16.sp)
    }
}

/** "Add a widget": a live preview of each, tap one to add it to the page you're on. */
@Composable
internal fun WidgetPicker(onPick: (WidgetKind) -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xF0221F2E))
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Widgets, contentDescription = null, tint = Q.Accent)
                Spacer(Modifier.width(10.dp))
                Text("Add a widget", color = Q.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "It goes on this page wherever there's room. Hold it afterwards to move it, resize it or change its style.",
                color = Q.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            WidgetKind.entries.forEach { kind ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { onPick(kind) }
                        .padding(14.dp),
                ) {
                    val ratio = when (kind) {
                        WidgetKind.CLOCK -> 2.6f
                        WidgetKind.FLIP -> 2.07f
                        WidgetKind.FLIP_WEATHER -> 1.89f
                        WidgetKind.WEATHER -> 4.16f
                    }
                    // Room above for the parts that rise out of the card
                    Spacer(Modifier.height(if (kind == WidgetKind.WEATHER) 14.dp else 4.dp))
                    WidgetView(kind, Modifier.fillMaxWidth().aspectRatio(ratio), sample = true)
                    Spacer(Modifier.height(if (kind.weather) 18.dp else 8.dp))
                    Text(kind.label, color = Q.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(kind.about, color = Q.TextDim, fontSize = 13.sp)
                }
            }
        }
    }
}
