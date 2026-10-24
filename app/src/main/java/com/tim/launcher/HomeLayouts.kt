@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.tim.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.settings.ButtonEnd
import com.tim.launcher.settings.ButtonIcon
import com.tim.launcher.settings.DockAlign
import com.tim.launcher.settings.DockLength
import com.tim.launcher.settings.DockPosition
import com.tim.launcher.ui.Q
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import kotlinx.coroutines.flow.drop

/*
 * The layout engine. Every screen is the same frame: your pages, the page dots, and the dock on whichever edge
 * you chose. What changes per screen is how pages are shown:
 *  - phone, cover screen, tablet: one page at a time; if the screen's shape doesn't match the grid's shape
 *    (a tablet held upright, a phone allowed to rotate), the grid flips, iPad style, keeping every slot.
 *  - unfolded: your pages as one strip, two neighbors at a time, split at the hinge.
 *  - unfolded and rotated: one page, reflowed into a grid sized for the tall screen.
 */

private const val HINGE_GAP_DP = 24
private const val SIDE_PAD_DP = 12
private val REFLOW_CELL = 92.dp

@Composable
fun HomeLayout(screen: ScreenState, vm: HomeViewModel, drag: DragController, showHint: Boolean) {
    val look = LocalHomeLook.current
    val unfolded = screen.posture == Posture.UNFOLDED
    val tall = screen.heightDp > screen.widthDp
    val ownGrid = unfolded && !look.carryOver
    val dock = if (unfolded) unfoldedDock(vm) else coverDock(vm)

    HomeFrame(dock, vm, drag) {
        if (showHint) HintLine()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                ownGrid && !tall -> ReflowPages(screen, vm, drag, look.innerCols, look.innerRows)
                unfolded && !tall -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    // A side dock takes room from one half, so then the two pages split what's left evenly;
                    // otherwise they split exactly at the hinge
                    val sideDock = look.dockPosition.let { it == DockPosition.LEFT || it == DockPosition.RIGHT }
                    val half = if (sideDock) (maxWidth - HINGE_GAP_DP.dp) / 2 else halfWidth(screen, maxWidth)
                    StripPages(screen, vm, drag, half)
                }
                unfolded -> ReflowPages(screen, vm, drag)
                else -> SinglePages(screen, vm, drag)
            }
        }
        PageDots(vm.pages, vm.currentPage)
    }
}

// ---- Frame and dock ----

/** Places the content and the dock according to the dock's position for this screen. */
@Composable
private fun HomeFrame(dock: List<SlotRef>, vm: HomeViewModel, drag: DragController, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalHomeLook.current
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = SIDE_PAD_DP.dp),
    ) {
        when (look.dockPosition) {
            DockPosition.BOTTOM -> Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxWidth(), content = content)
                DockBar(dock, vertical = false, vm, drag, Modifier.padding(top = 6.dp, bottom = 10.dp))
            }
            DockPosition.TOP -> Column(Modifier.fillMaxSize()) {
                DockBar(dock, vertical = false, vm, drag, Modifier.padding(top = 6.dp, bottom = 6.dp))
                Column(Modifier.weight(1f).fillMaxWidth(), content = content)
            }
            DockPosition.LEFT -> Row(Modifier.fillMaxSize()) {
                DockBar(dock, vertical = true, vm, drag, Modifier.padding(vertical = 8.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).fillMaxHeight(), content = content)
            }
            DockPosition.RIGHT -> Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight(), content = content)
                Spacer(Modifier.width(8.dp))
                DockBar(dock, vertical = true, vm, drag, Modifier.padding(vertical = 8.dp))
            }
            DockPosition.NONE -> Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxWidth(), content = content)
                // No dock: the apps button (if you use one) sits on the home screen
                if (look.button != null) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        contentAlignment = if (look.button == ButtonEnd.START) Alignment.CenterStart else Alignment.CenterEnd,
                    ) { DrawerButton() }
                }
            }
        }
    }
}

private fun coverDock(vm: HomeViewModel): List<SlotRef> = vm.dockCover.indices.map { SlotRef(Area.DOCK_COVER, it) }

/** The unfolded dock: your folded dock icons first, then the extra unfolded slots after them. */
private fun unfoldedDock(vm: HomeViewModel): List<SlotRef> =
    coverDock(vm) + vm.dockExtra.indices.map { SlotRef(Area.DOCK_EXTRA, it) }

/**
 * The dock. It only holds the icons you've put in it, so it shrinks when it isn't full; while you drag an icon,
 * one empty spot appears at the end to drop into.
 *  - Full: spans the edge, with the icons at the start, middle or end.
 *  - Fit: only as long as its icons, icons centered. A fit side dock sits at the top, middle or bottom.
 * If the icons don't fit, the dock scrolls. The apps button sits at the chosen end.
 */
@Composable
private fun DockBar(slots: List<SlotRef>, vertical: Boolean, vm: HomeViewModel, drag: DragController, modifier: Modifier) {
    val look = LocalHomeLook.current
    val full = look.dockLength == DockLength.FULL
    val button = look.button
    val filled = slots.filter { vm.keyAt(it) != null }
    val dropSpot = if (drag.active) slots.firstOrNull { vm.keyAt(it) == null } else null
    val refs = if (dropSpot != null) filled + dropSpot else filled
    val count = refs.size + if (button != null) 1 else 0
    if (count == 0) return
    val background = Modifier
        .clip(look.dockShape)
        .background(if (look.dockBackground) look.panel else Color.Transparent)

    if (!vertical) {
        BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val cellW = 72.dp
            val scrolls = cellW * count > maxWidth
            val iconSize = 56.dp
            val cellH = iconSize + if (look.labelsDock) 36.dp else 20.dp
            Row(
                (if (full || scrolls) Modifier.fillMaxWidth() else Modifier)
                    .then(background)
                    .then(if (scrolls) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = when {
                    !full || scrolls -> Arrangement.Start
                    look.dockIconAlign == DockAlign.START -> Arrangement.Start
                    look.dockIconAlign == DockAlign.CENTER -> Arrangement.Center
                    else -> Arrangement.End
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (button == ButtonEnd.START) Box(Modifier.width(cellW), contentAlignment = Alignment.Center) { DrawerButton() }
                refs.forEach { ref ->
                    Cell(ref, vm.keyAt(ref), iconSize, look.labelsDock, vm, drag, Modifier.width(cellW).height(cellH))
                }
                if (button == ButtonEnd.END) Box(Modifier.width(cellW), contentAlignment = Alignment.Center) { DrawerButton() }
            }
        }
    } else {
        BoxWithConstraints(
            modifier.fillMaxHeight(),
            contentAlignment = when {
                full -> Alignment.Center
                look.dockAlign == DockAlign.START -> Alignment.TopCenter
                look.dockAlign == DockAlign.CENTER -> Alignment.Center
                else -> Alignment.BottomCenter
            },
        ) {
            val cellH = 80.dp
            val scrolls = cellH * count > maxHeight
            val iconSize = 52.dp
            val cellW = 72.dp
            Column(
                (if (full || scrolls) Modifier.fillMaxHeight() else Modifier)
                    .width(cellW)
                    .then(background)
                    .then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(vertical = 8.dp),
                verticalArrangement = when {
                    !full || scrolls -> Arrangement.Top
                    look.dockIconAlign == DockAlign.START -> Arrangement.Top
                    look.dockIconAlign == DockAlign.CENTER -> Arrangement.Center
                    else -> Arrangement.Bottom
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (button == ButtonEnd.START) Box(Modifier.height(cellH), contentAlignment = Alignment.Center) { DrawerButton() }
                refs.forEach { ref ->
                    Cell(ref, vm.keyAt(ref), iconSize, false, vm, drag, Modifier.width(cellW).height(cellH))
                }
                if (button == ButtonEnd.END) Box(Modifier.height(cellH), contentAlignment = Alignment.Center) { DrawerButton() }
            }
        }
    }
}

@Composable
private fun DrawerButton() {
    val look = LocalHomeLook.current
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Q.Accent)
            .clickable { look.openDrawer() },
        contentAlignment = Alignment.Center,
    ) {
        when (look.buttonIcon) {
            ButtonIcon.QUARTZ -> Text("Q", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            ButtonIcon.GRID -> Icon(Icons.Rounded.Apps, contentDescription = "Apps", tint = Color.White)
            ButtonIcon.DOTS -> Icon(Icons.Rounded.MoreHoriz, contentDescription = "Apps", tint = Color.White)
            ButtonIcon.CIRCLE -> Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = "Apps", tint = Color.White)
        }
    }
}

// ---- Pages ----

/**
 * One page at a time. When the screen's shape and the grid's shape disagree (a 7×5 tablet grid held upright, or a
 * 5×6 phone grid turned sideways), the grid flips to 5×7 or 6×5. Every slot keeps its place in reading order, so
 * nothing moves when you turn it back.
 */
@Composable
private fun SinglePages(screen: ScreenState, vm: HomeViewModel, drag: DragController) {
    val landscapeScreen = screen.widthDp > screen.heightDp
    val landscapeGrid = vm.cols > vm.rows
    val flip = vm.cols != vm.rows && landscapeScreen != landscapeGrid
    val cols = if (flip) vm.rows else vm.cols
    val rows = if (flip) vm.cols else vm.rows
    HomePager(
        count = vm.pages,
        page = vm.currentPage,
        onSettled = { vm.currentPage = it },
        screen = screen,
        drag = drag,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        GridArea(Area.COVER, page * vm.perPage, vm.cover, cols, rows, vm, drag, Modifier.fillMaxSize())
    }
}

/**
 * Unfolded: your pages are one fixed row and the screen shows two neighbors at a time. The cover screen is the left
 * or right page of that pair. At the ends the view stops at the first or last pair.
 */
@Composable
private fun StripPages(screen: ScreenState, vm: HomeViewModel, drag: DragController, halfWidth: Dp) {
    val lastFirst = maxOf(0, vm.pages - 2)
    HomePager(
        count = vm.pages,
        page = (if (vm.coverLeft) vm.currentPage else vm.currentPage - 1).coerceIn(0, lastFirst),
        onSettled = { first ->
            vm.currentPage = (if (vm.coverLeft) first else first + 1).coerceIn(0, vm.pages - 1)
        },
        screen = screen,
        drag = drag,
        modifier = Modifier.fillMaxSize(),
        pageSize = PageSize.Fixed(halfWidth),
        pageSpacing = HINGE_GAP_DP.dp,
        maxPage = lastFirst,
    ) { page ->
        Column(Modifier.fillMaxSize()) {
            Caption(if (page == vm.currentPage) "Page ${page + 1} · cover" else "Page ${page + 1}")
            GridArea(Area.COVER, page * vm.perPage, vm.cover, vm.cols, vm.rows, vm, drag, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/**
 * One page at a time with your apps, in order, flowed into a different grid: one sized for the tall screen when
 * the unfolded phone is turned upright, or the unfolded screen's own grid when the cover doesn't carry over.
 */
@Composable
private fun ReflowPages(screen: ScreenState, vm: HomeViewModel, drag: DragController, gridCols: Int? = null, gridRows: Int? = null) {
    HomePager(
        count = vm.pages,
        page = vm.currentPage,
        onSettled = { vm.currentPage = it },
        screen = screen,
        drag = drag,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val base = page * vm.perPage
            val refs = (0 until vm.perPage).map { SlotRef(Area.COVER, base + it) }.filter { vm.keyAt(it) != null }
            val cols = gridCols ?: maxOf(vm.cols, (maxWidth / REFLOW_CELL).toInt())
            val rows = gridRows ?: maxOf((vm.perPage + cols - 1) / cols, maxOf(1, (maxHeight / REFLOW_CELL).toInt()))
            RefGrid(refs, cols.coerceAtLeast(1), rows.coerceAtLeast(1), vm, drag, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun RefGrid(refs: List<SlotRef>, cols: Int, rows: Int, vm: HomeViewModel, drag: DragController, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val look = LocalHomeLook.current
        val cellW = maxWidth / cols
        val cellH = maxHeight / rows
        val iconSize = minOf(cellW * 0.62f * look.iconScale, cellH * 0.55f, 80.dp)
        Column {
            for (r in 0 until rows) {
                Row {
                    for (c in 0 until cols) {
                        val ref = refs.getOrNull(r * cols + c)
                        if (ref != null) {
                            Cell(ref, vm.keyAt(ref), iconSize, look.labelsHome, vm, drag, Modifier.width(cellW).height(cellH))
                        } else {
                            Spacer(Modifier.width(cellW).height(cellH))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridArea(
    area: Area,
    base: Int,
    items: List<String?>,
    cols: Int,
    rows: Int,
    vm: HomeViewModel,
    drag: DragController,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val look = LocalHomeLook.current
        val cellW = maxWidth / cols
        val cellH = maxHeight / rows
        val iconSize = minOf(cellW * 0.62f * look.iconScale, cellH * 0.55f, 80.dp)
        Column {
            for (r in 0 until rows) {
                Row {
                    for (c in 0 until cols) {
                        val index = base + r * cols + c
                        Cell(SlotRef(area, index), items.getOrNull(index), iconSize, look.labelsHome, vm, drag, Modifier.width(cellW).height(cellH))
                    }
                }
            }
        }
    }
}

/**
 * A horizontal pager for home pages. While an icon is dragged near the left or right edge of the screen, it
 * flips to the neighboring page. All pages stay composed so a drag survives its page sliding away.
 */
@Composable
private fun HomePager(
    count: Int,
    page: Int,
    onSettled: (Int) -> Unit,
    screen: ScreenState,
    drag: DragController,
    modifier: Modifier,
    pageSize: PageSize = PageSize.Fill,
    pageSpacing: Dp = 0.dp,
    maxPage: Int = count - 1,
    content: @Composable (Int) -> Unit,
) {
    val last = maxPage.coerceIn(0, maxOf(0, count - 1))
    val state = rememberPagerState(initialPage = page.coerceIn(0, last)) { count }
    // Report pages you swipe to (not the one we opened on, so unfolding at an edge doesn't change your page)
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.drop(1).collect { onSettled(it.coerceAtMost(last)) }
    }
    // Follow the page from outside: Home, switching cover side, or folding
    LaunchedEffect(page, last) {
        val target = page.coerceIn(0, last)
        if (!state.isScrollInProgress && state.currentPage != target) state.animateScrollToPage(target)
    }

    val edgePx = with(LocalDensity.current) { 36.dp.toPx() }
    val widthPx = screen.widthPx
    val edge by remember(edgePx, widthPx) {
        derivedStateOf {
            when {
                !drag.active -> 0
                drag.pointer.x < edgePx -> -1
                drag.pointer.x > widthPx - edgePx -> 1
                else -> 0
            }
        }
    }
    LaunchedEffect(edge) {
        if (edge == 0) return@LaunchedEffect
        delay(450)
        while (true) {
            val target = (state.currentPage + edge).coerceIn(0, last)
            if (target == state.currentPage) break
            state.animateScrollToPage(target)
            delay(650)
        }
    }

    // Swiping past the first or last page triggers its gesture (see Settings › Gestures)
    val onSwipePast = LocalHomeLook.current.onSwipePast
    val pastModifier = Modifier.pointerInput(count, last) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startPage = state.currentPage
            var dx = 0f
            var multi = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) multi = true
                event.changes.firstOrNull { it.id == down.id }?.let { dx += it.positionChangeIgnoreConsumed().x }
            } while (event.changes.any { it.pressed })
            val threshold = 90.dp.toPx()
            if (!multi && !drag.active && state.currentPage == startPage) {
                if (dx > threshold && startPage == 0) onSwipePast(true)
                if (dx < -threshold && startPage >= last) onSwipePast(false)
            }
        }
    }

    HorizontalPager(
        state = state,
        modifier = modifier.then(pastModifier),
        pageSize = pageSize,
        pageSpacing = pageSpacing,
        beyondViewportPageCount = count,
    ) { index -> content(index) }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    if (count <= 1) return
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (i == current) 8.dp else 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (i == current) 0.95f else 0.45f)),
            )
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        textAlign = TextAlign.Center,
        style = labelStyle(),
    )
}

/** Width of one half of the unfolded screen, split around the hinge (or the middle if none is reported). */
@Composable
private fun halfWidth(screen: ScreenState, available: Dp): Dp {
    val density = LocalDensity.current
    val gap = HINGE_GAP_DP.dp
    val hingeLocal = screen.hingeCenterPx
        ?.let { with(density) { it.toDp() } - SIDE_PAD_DP.dp }
        ?: (available / 2)
    return (hingeLocal - gap / 2).coerceIn(0.dp, available)
}
