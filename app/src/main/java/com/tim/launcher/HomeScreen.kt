package com.tim.launcher

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

private val PanelShape = RoundedCornerShape(28.dp)
private val DockGlass = Color.White.copy(alpha = 0.14f)
private const val HINGE_GAP_DP = 24
private const val SIDE_PAD_DP = 12

/** Tracks the icon being dragged (from a home slot or from the drawer) and where every slot sits on screen. */
class DragController {
    var source by mutableStateOf<SlotRef?>(null)
        private set
    var app by mutableStateOf<AppEntry?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set

    /** True while an app picked up from the drawer is being placed on the home screen. */
    var fromDrawer by mutableStateOf(false)
        private set

    val bounds = HashMap<SlotRef, Rect>()

    val active: Boolean get() = app != null

    val hover: SlotRef?
        get() = if (!active) null else bounds.entries.firstOrNull { it.value.contains(pointer) }?.key

    fun start(ref: SlotRef, entry: AppEntry, at: Offset) {
        source = ref
        app = entry
        pointer = at
        fromDrawer = false
    }

    fun startFromDrawer(entry: AppEntry, at: Offset) {
        source = null
        app = entry
        pointer = at
        fromDrawer = true
    }

    fun moveBy(delta: Offset) {
        pointer += delta
    }

    fun drop(vm: HomeViewModel) {
        val entry = app
        val from = source
        val to = hover
        when {
            entry == null || to == null -> Unit
            fromDrawer -> vm.place(entry.key, to)
            from != null && from != to -> vm.move(from, to)
        }
        clear()
    }

    fun cancel() = clear()

    private fun clear() {
        source = null
        app = null
        fromDrawer = false
    }
}

@Composable
fun HomeScreen(activity: Activity, homeTick: Int, vm: HomeViewModel = viewModel()) {
    val screen = rememberScreenState(activity)
    val prefs = remember { Prefs.get(activity) }
    val drag = remember { DragController() }
    var drawerOpen by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var hintSeen by remember { mutableStateOf(prefs.getBoolean(Prefs.HINT_SEEN, false)) }

    LaunchedEffect(homeTick) {
        drawerOpen = false
        panelOpen = false
    }
    BackHandler {
        drawerOpen = false
        panelOpen = false
    }

    // Log every posture change and time how long the new layout took to reach the screen.
    LaunchedEffect(screen.posture, screen.widthDp, screen.heightDp) {
        Diag.log("Posture: ${screen.posture.label}, ${screen.widthDp}×${screen.heightDp}dp, display ${screen.displayId}")
        if (drag.active) {
            drag.cancel()
            Diag.log("Drag interrupted by the fold change; icon stayed in its spot")
        }
        val changedAt = Diag.lastConfigAt
        withFrameNanos { }
        val elapsed = SystemClock.uptimeMillis() - changedAt
        if (changedAt != 0L && elapsed < 3000) {
            Diag.lastRelayoutMs = elapsed
            Diag.log("New layout on screen ${elapsed}ms after the config change")
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var travel = 0f
                detectVerticalDragGestures(
                    onDragStart = { travel = 0f },
                    onDragEnd = {
                        if (!drag.active && travel < -80.dp.toPx()) drawerOpen = true
                        travel = 0f
                    },
                    onDragCancel = { travel = 0f },
                    onVerticalDrag = { _, dy -> travel += dy },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    panelOpen = true
                    if (!hintSeen) {
                        hintSeen = true
                        prefs.edit().putBoolean(Prefs.HINT_SEEN, true).apply()
                    }
                })
            },
    ) {
        if (screen.posture.split) {
            SplitLayout(screen, vm, drag)
        } else {
            SingleLayout(vm, drag, showHint = !hintSeen)
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            val drawerCols = if (screen.posture.split) vm.cols * 2 else vm.cols
            AppDrawer(vm, drag, drawerCols, onClose = { drawerOpen = false })
        }

        // The icon under your finger while dragging
        drag.app?.let { app ->
            val sizePx = with(LocalDensity.current) { 64.dp.toPx() }
            AppIcon(
                app = app,
                size = 64.dp,
                showLabel = false,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (drag.pointer.x - sizePx / 2).roundToInt(),
                            (drag.pointer.y - sizePx / 2).roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        scaleX = 1.12f
                        scaleY = 1.12f
                        alpha = 0.92f
                    },
            )
        }

        AnimatedVisibility(visible = panelOpen, enter = fadeIn(), exit = fadeOut()) {
            TestPanel(activity, vm, screen, onClose = { panelOpen = false })
        }
    }
}

/** Phone or Fold cover screen: one grid plus the dock. */
@Composable
private fun SingleLayout(vm: HomeViewModel, drag: DragController, showHint: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = SIDE_PAD_DP.dp),
    ) {
        if (showHint) HintLine()
        GridArea(Area.COVER, vm.cover, vm.cols, vm.rows, vm, drag, Modifier.weight(1f).fillMaxWidth())
        DockPanel {
            DockRow(Area.DOCK_COVER, vm.dockCover, vm, drag, Modifier.fillMaxWidth())
        }
    }
}

/**
 * Unfolded Fold or tablet: the cover grid doubled horizontally. The cover layout lands on the chosen half,
 * split at the real hinge position when the device reports one.
 */
@Composable
private fun SplitLayout(screen: ScreenState, vm: HomeViewModel, drag: DragController) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical))
            .padding(horizontal = SIDE_PAD_DP.dp),
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val (leftW, rightW) = halfWidths(screen, maxWidth)
            Row(Modifier.fillMaxSize()) {
                HalfGrid(isCover = vm.coverLeft, screen, vm, drag, Modifier.width(leftW).fillMaxHeight())
                Spacer(Modifier.width(HINGE_GAP_DP.dp))
                HalfGrid(isCover = !vm.coverLeft, screen, vm, drag, Modifier.width(rightW).fillMaxHeight())
            }
        }
        DockPanel {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val (leftW, rightW) = halfWidths(screen, maxWidth)
                Row {
                    DockRow(
                        if (vm.coverLeft) Area.DOCK_COVER else Area.DOCK_EXTRA,
                        if (vm.coverLeft) vm.dockCover else vm.dockExtra,
                        vm, drag, Modifier.width(leftW),
                    )
                    Spacer(Modifier.width(HINGE_GAP_DP.dp))
                    DockRow(
                        if (vm.coverLeft) Area.DOCK_EXTRA else Area.DOCK_COVER,
                        if (vm.coverLeft) vm.dockExtra else vm.dockCover,
                        vm, drag, Modifier.width(rightW),
                    )
                }
            }
        }
    }
}

/** Widths of the two halves inside the padded area, split around the hinge. */
@Composable
private fun halfWidths(screen: ScreenState, available: Dp): Pair<Dp, Dp> {
    val density = LocalDensity.current
    val gap = HINGE_GAP_DP.dp
    val hingeLocal = screen.hingeCenterPx
        ?.let { with(density) { it.toDp() } - SIDE_PAD_DP.dp }
        ?: (available / 2)
    val left = (hingeLocal - gap / 2).coerceIn(0.dp, available)
    val right = (available - left - gap).coerceAtLeast(0.dp)
    return left to right
}

@Composable
private fun HalfGrid(isCover: Boolean, screen: ScreenState, vm: HomeViewModel, drag: DragController, modifier: Modifier) {
    Column(modifier) {
        val caption = when {
            screen.posture == Posture.TABLET -> if (isCover) "Phone layout half" else "Other half"
            isCover -> "Cover layout"
            else -> "Inner half"
        }
        Text(
            caption,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
            textAlign = TextAlign.Center,
            style = labelStyle(),
        )
        GridArea(
            area = if (isCover) Area.COVER else Area.EXTRA,
            items = if (isCover) vm.cover else vm.extra,
            cols = vm.cols,
            rows = vm.rows,
            vm = vm,
            drag = drag,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun GridArea(
    area: Area,
    items: List<String?>,
    cols: Int,
    rows: Int,
    vm: HomeViewModel,
    drag: DragController,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val cellW = maxWidth / cols
        val cellH = maxHeight / rows
        val iconSize = minOf(cellW * 0.62f, cellH * 0.55f, 72.dp)
        Column {
            for (r in 0 until rows) {
                Row {
                    for (c in 0 until cols) {
                        val index = r * cols + c
                        Cell(SlotRef(area, index), items.getOrNull(index), iconSize, true, vm, drag, Modifier.width(cellW).height(cellH))
                    }
                }
            }
        }
    }
}

@Composable
private fun DockRow(area: Area, items: List<String?>, vm: HomeViewModel, drag: DragController, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val count = items.size.coerceAtLeast(1)
        val cellW = maxWidth / count
        val iconSize = minOf(cellW * 0.62f, 56.dp)
        Row {
            items.forEachIndexed { index, key ->
                Cell(SlotRef(area, index), key, iconSize, false, vm, drag, Modifier.width(cellW).height(iconSize + 20.dp))
            }
        }
    }
}

@Composable
private fun DockPanel(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp)
            .clip(PanelShape)
            .background(DockGlass),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Cell(
    ref: SlotRef,
    key: String?,
    iconSize: Dp,
    showLabel: Boolean,
    vm: HomeViewModel,
    drag: DragController,
    modifier: Modifier,
) {
    val app = key?.let { vm.apps[it] }
    val haptics = LocalHapticFeedback.current

    DisposableEffect(ref) {
        onDispose { drag.bounds.remove(ref) }
    }

    val gestures = if (app == null) Modifier else Modifier
        .pointerInput(ref, key) {
            detectTapGestures(
                onTap = { vm.launch(app, drag.bounds[ref]?.toAndroidRect()) },
                onLongPress = { }, // long press belongs to dragging
            )
        }
        .pointerInput(ref, key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val origin = drag.bounds[ref]?.topLeft ?: Offset.Zero
                    // bounds are measured outside the 2.dp padding; `local` is inside it
                    val pad = 2.dp.toPx()
                    drag.start(ref, app, origin + Offset(pad, pad) + local)
                },
                onDrag = { change, amount ->
                    change.consume()
                    drag.moveBy(amount)
                },
                onDragEnd = { drag.drop(vm) },
                onDragCancel = { drag.cancel() },
            )
        }

    val hovered = drag.hover == ref && drag.source != ref
    Box(
        modifier
            .onGloballyPositioned { drag.bounds[ref] = it.boundsInRoot() }
            .padding(2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.18f) else Color.Transparent)
            .then(gestures),
        contentAlignment = Alignment.Center,
    ) {
        if (app != null) {
            AppIcon(
                app = app,
                size = iconSize,
                showLabel = showLabel,
                modifier = Modifier.alpha(if (drag.source == ref) 0f else 1f),
            )
        }
    }
}

@Composable
fun AppIcon(app: AppEntry, size: Dp, showLabel: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(size))
        if (showLabel) {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = labelStyle(),
                modifier = Modifier.padding(top = 4.dp).widthIn(max = size * 1.6f),
            )
        }
    }
}

private fun labelStyle() = TextStyle(
    shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 1f), blurRadius = 4f),
)

@Composable
private fun HintLine() {
    Text(
        "Phase 0 test · swipe up for apps · long-press empty space for the test panel",
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        style = labelStyle(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}

@Composable
private fun AppDrawer(vm: HomeViewModel, drag: DragController, cols: Int, onClose: () -> Unit) {
    // Once an app is picked up, the drawer stays invisible until it closes (no flash on drop)
    var carried by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (carried || drag.fromDrawer) 0f else 1f }
            .background(Color(0xEE101018))
            // Keep long-presses inside the drawer from opening the test panel underneath
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dy -> if (dy > 12f) onClose() }
                }
                .clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.5f)),
            )
        }
        Text(
            "Apps · ${vm.sortedApps.size}   (long-press and drag to place on home)",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.sortedApps, key = { it.key }) { app ->
                DrawerItem(app, vm, drag, onPickUp = { carried = true }, onClose = onClose)
            }
        }
    }
}

@Composable
private fun DrawerItem(
    app: AppEntry,
    vm: HomeViewModel,
    drag: DragController,
    onPickUp: () -> Unit,
    onClose: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val itemBounds = remember { arrayOf(Rect.Zero) }
    var lifted by remember { mutableStateOf(false) }
    AppIcon(
        app = app,
        size = 52.dp,
        showLabel = true,
        modifier = Modifier
            .onGloballyPositioned { itemBounds[0] = it.boundsInRoot() }
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(app.key) {
                detectTapGestures(
                    onTap = {
                        vm.launch(app, itemBounds[0].toAndroidRect())
                        onClose()
                    },
                    onLongPress = { }, // long press picks the app up instead
                )
            }
            .pointerInput(app.key) {
                // Long-press lifts the app; it only leaves the drawer once the finger actually moves.
                // A long-press without moving (e.g. touching to stop a fast scroll) does nothing.
                val carryAfter = 24.dp.toPx()
                var startAt = Offset.Zero
                var travel = Offset.Zero
                var carrying = false
                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        lifted = true
                        startAt = itemBounds[0].topLeft + local
                        travel = Offset.Zero
                        carrying = false
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        travel += amount
                        if (carrying) {
                            drag.moveBy(amount)
                        } else if (travel.getDistance() > carryAfter) {
                            carrying = true
                            onPickUp()
                            drag.startFromDrawer(app, startAt + travel)
                        }
                    },
                    onDragEnd = {
                        lifted = false
                        if (carrying) {
                            drag.drop(vm)
                            onClose()
                        }
                    },
                    onDragCancel = {
                        lifted = false
                        if (carrying) {
                            drag.cancel()
                            onClose()
                        }
                    },
                )
            }
            .graphicsLayer {
                val scale = if (lifted) 1.08f else 1f
                scaleX = scale
                scaleY = scale
            }
            .padding(vertical = 10.dp),
    )
}
