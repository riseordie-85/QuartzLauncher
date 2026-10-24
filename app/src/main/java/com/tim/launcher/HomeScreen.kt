@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import com.tim.launcher.settings.ButtonEnd
import com.tim.launcher.settings.ButtonIcon
import com.tim.launcher.settings.CoverSide
import com.tim.launcher.settings.DockShape
import com.tim.launcher.settings.DrawerOpen
import com.tim.launcher.settings.GestureAction
import com.tim.launcher.settings.GestureSlot
import com.tim.launcher.settings.PageMode
import com.tim.launcher.settings.S
import com.tim.launcher.settings.Scope
import com.tim.launcher.settings.SettingsActivity
import com.tim.launcher.settings.SettingsStore
import com.tim.launcher.ui.Q

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

    /** Where the "Remove from home" target sits while it's showing. */
    var removeZone: Rect = Rect.Zero

    val active: Boolean get() = app != null

    val overRemove: Boolean
        get() = active && removeZone != Rect.Zero && removeZone.inflate(24f).contains(pointer)

    val hover: SlotRef?
        get() = if (!active || overRemove) null else bounds.entries.firstOrNull { it.value.contains(pointer) }?.key

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
            entry == null -> Unit
            overRemove -> if (!fromDrawer && from != null) vm.remove(from)
            to == null -> Unit
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
    val store = remember { SettingsStore.get(activity) }
    var drawerOpen by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // ---- Settings → home ----
    val scope = when (screen.posture) {
        Posture.PHONE -> Scope.PHONE
        Posture.COVER -> Scope.COVER
        Posture.UNFOLDED -> Scope.INNER
        Posture.TABLET -> Scope.TABLET
    }
    val gridScope = when (screen.posture) {
        Posture.PHONE -> Scope.PHONE
        Posture.TABLET -> Scope.TABLET
        else -> Scope.COVER
    }
    // First run after settings arrived: keep the layout you already had instead of resetting it to defaults
    remember(gridScope) {
        if (store.owner(S.gridCols, gridScope) == null) {
            store.set(S.gridCols, gridScope, if (gridScope == Scope.TABLET) vm.cols * 2 else vm.cols)
            store.set(S.gridRows, gridScope, vm.rows)
        }
        if (store.owner(S.pages, Scope.GLOBAL) == null) store.set(S.pages, value = vm.pages)
        if (store.owner(S.coverSide, Scope.GLOBAL) == null) {
            store.set(S.coverSide, value = if (vm.coverLeft) CoverSide.LEFT else CoverSide.RIGHT)
        }
        if (store.owner(S.pageMode, Scope.GLOBAL) == null) {
            store.set(S.pageMode, value = if (vm.pageMode == "strip") PageMode.STRIP else PageMode.PAIRED)
        }
        true
    }
    val settingCols = store[S.gridCols, gridScope]
    val settingRows = store[S.gridRows, gridScope]
    LaunchedEffect(settingCols, settingRows, gridScope) {
        val c = if (gridScope == Scope.TABLET) (settingCols / 2).coerceAtLeast(3) else settingCols
        vm.setGrid(c, settingRows)
    }
    val settingSide = store[S.coverSide]
    LaunchedEffect(settingSide) {
        val left = settingSide == CoverSide.LEFT
        if (vm.coverLeft != left) vm.setCoverSide(left)
    }
    val settingPages = store[S.pages]
    LaunchedEffect(settingPages) { vm.setPageCount(settingPages) }
    val settingMode = if (store[S.pageMode] == PageMode.STRIP) "strip" else "paired"
    LaunchedEffect(settingMode) { if (vm.pageMode != settingMode) vm.choosePageMode(settingMode) }

    val panelColor = Color(store[S.panelColor])
    val look = HomeLook(
        panel = panelColor.copy(alpha = store[S.panelOpacity].coerceIn(0.05f, 1f)),
        dockShape = if (store[S.dockShape] == DockShape.SQUARE) RoundedCornerShape(0.dp) else RoundedCornerShape(store[S.dockRadius].dp),
        dockBackground = store[S.dockShape] != DockShape.NONE,
        labelsHome = store[S.labelsHome, scope],
        labelsDock = store[S.labelsDock],
        labelsDrawer = store[S.labelsDrawer],
        iconScale = store[S.iconSize, scope],
        drawerBg = panelColor.copy(alpha = maxOf(store[S.panelOpacity], 0.85f)),
        button = if (store[S.drawerOpen] == DrawerOpen.SWIPE) null else store[S.drawerButtonEnd],
        buttonIcon = store[S.drawerButtonIcon],
        openDrawer = { drawerOpen = true },
    )
    val drawerCols = store[S.drawerCols, scope].let {
        if (scope == Scope.INNER && store.isLinked(S.drawerCols, Scope.INNER)) it * 2 else it
    }

    fun run(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.APP_DRAWER, GestureAction.SEARCH -> drawerOpen = true
            GestureAction.NOTIFICATIONS -> expandNotifications(activity)
            GestureAction.SETTINGS -> SettingsActivity.open(activity)
            GestureAction.LOCK -> Toast.makeText(activity, "Lock needs Accessibility access, which comes later", Toast.LENGTH_SHORT).show()
            GestureAction.SWAP_DOCK -> Diag.log("Swap dock side arrives with dock layouts")
        }
    }
    var hintSeen by remember { mutableStateOf(prefs.getBoolean(Prefs.HINT_SEEN, false)) }

    LaunchedEffect(homeTick) {
        drawerOpen = false
        panelOpen = false
        menuOpen = false
    }
    BackHandler {
        drawerOpen = false
        panelOpen = false
        menuOpen = false
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
                        val threshold = 80.dp.toPx()
                        if (!drag.active && !drawerOpen) {
                            if (travel < -threshold) run(store[S.gesture(GestureSlot.SWIPE_UP)])
                            if (travel > threshold) run(store[S.gesture(GestureSlot.SWIPE_DOWN)])
                        }
                        travel = 0f
                    },
                    onDragCancel = { travel = 0f },
                    onVerticalDrag = { _, dy -> travel += dy },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { run(store[S.gesture(GestureSlot.DOUBLE_TAP)]) },
                    onLongPress = {
                        menuOpen = true
                        if (!hintSeen) {
                            hintSeen = true
                            prefs.edit().putBoolean(Prefs.HINT_SEEN, true).apply()
                        }
                    },
                )
            },
    ) {
      CompositionLocalProvider(LocalHomeLook provides look) {
        if (screen.posture.split) {
            SplitLayout(screen, vm, drag)
        } else {
            SingleLayout(screen, vm, drag, showHint = !hintSeen)
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            AppDrawer(vm, drag, drawerCols, onClose = { drawerOpen = false })
        }

        if (drag.active) RemoveZone(drag)

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

        AnimatedVisibility(visible = menuOpen, enter = fadeIn(), exit = fadeOut()) {
            HomeMenu(
                onSettings = {
                    menuOpen = false
                    SettingsActivity.open(activity)
                },
                onSetup = {
                    menuOpen = false
                    SettingsActivity.open(activity, wizard = true)
                },
                onTestPanel = {
                    menuOpen = false
                    panelOpen = true
                },
                onDismiss = { menuOpen = false },
            )
        }

        AnimatedVisibility(visible = panelOpen, enter = fadeIn(), exit = fadeOut()) {
            TestPanel(activity, vm, screen, onClose = { panelOpen = false })
        }
      }
    }
}

/** Phone or Fold cover screen: swipeable pages of one grid, plus the dock. */
@Composable
private fun SingleLayout(screen: ScreenState, vm: HomeViewModel, drag: DragController, showHint: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = SIDE_PAD_DP.dp),
    ) {
        if (showHint) HintLine()
        HomePager(
            count = vm.pages,
            startPage = vm.currentPage,
            onSettled = { vm.currentPage = it },
            screen = screen,
            drag = drag,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            GridArea(Area.COVER, page * vm.perPage, vm.cover, vm.cols, vm.rows, vm, drag, Modifier.fillMaxSize())
        }
        PageDots(vm.pages, vm.currentPage)
        DockPanel {
            DockWithButton(Modifier.fillMaxWidth()) {
                DockRow(Area.DOCK_COVER, vm.dockCover, vm, drag, Modifier.weight(1f))
            }
        }
    }
}

/**
 * Unfolded Fold or tablet. Book orientation: the cover grid doubled horizontally, split at the hinge, in either
 * paired or strip page mode. Rotated (taller than wide): one reflowed grid, iPad style.
 */
@Composable
private fun SplitLayout(screen: ScreenState, vm: HomeViewModel, drag: DragController) {
    if (screen.heightDp > screen.widthDp) {
        ReflowLayout(screen, vm, drag)
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical))
            .padding(horizontal = SIDE_PAD_DP.dp),
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val (leftW, rightW) = halfWidths(screen, maxWidth)
            if (vm.pageMode == "strip") {
                StripPages(screen, vm, drag, leftW)
            } else {
                HomePager(
                    count = vm.pages,
                    startPage = vm.currentPage,
                    onSettled = { vm.currentPage = it },
                    screen = screen,
                    drag = drag,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    Row(Modifier.fillMaxSize()) {
                        HalfGrid(vm.coverLeft, page, coverCaption(screen, vm.coverLeft, page), vm, drag, Modifier.width(leftW).fillMaxHeight())
                        Spacer(Modifier.width(HINGE_GAP_DP.dp))
                        HalfGrid(!vm.coverLeft, page, coverCaption(screen, !vm.coverLeft, page), vm, drag, Modifier.width(rightW).fillMaxHeight())
                    }
                }
            }
        }
        PageDots(vm.pages, vm.currentPage)
        DockPanel {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val (leftW, rightW) = halfWidths(screen, maxWidth)
                Row {
                    DockHalf(
                        if (vm.coverLeft) Area.DOCK_COVER else Area.DOCK_EXTRA,
                        if (vm.coverLeft) vm.dockCover else vm.dockExtra,
                        withButton = vm.coverLeft,
                        vm, drag, Modifier.width(leftW),
                    )
                    Spacer(Modifier.width(HINGE_GAP_DP.dp))
                    DockHalf(
                        if (vm.coverLeft) Area.DOCK_EXTRA else Area.DOCK_COVER,
                        if (vm.coverLeft) vm.dockExtra else vm.dockCover,
                        withButton = !vm.coverLeft,
                        vm, drag, Modifier.width(rightW),
                    )
                }
            }
        }
    }
}

private fun coverCaption(screen: ScreenState, isCover: Boolean, page: Int): String {
    val n = page + 1
    return when {
        screen.posture == Posture.TABLET -> if (isCover) "Phone layout · page $n" else "Other half · page $n"
        isCover -> "Cover · page $n"
        else -> "Inner half · page $n"
    }
}

/**
 * Strip mode: cover pages laid out in one continuous row, two visible at a time when unfolded. A blank page pads
 * the end (cover on the left) or the start (cover on the right) so the page you were on stays on its half.
 */
@Composable
private fun StripPages(screen: ScreenState, vm: HomeViewModel, drag: DragController, halfWidth: Dp) {
    HomePager(
        count = vm.pages + 1,
        startPage = vm.currentPage,
        onSettled = { vm.currentPage = it.coerceIn(0, vm.pages - 1) },
        screen = screen,
        drag = drag,
        modifier = Modifier.fillMaxSize(),
        pageSize = PageSize.Fixed(halfWidth),
        pageSpacing = HINGE_GAP_DP.dp,
    ) { index ->
        val page = if (vm.coverLeft) index else index - 1
        if (page in 0 until vm.pages) {
            Column(Modifier.fillMaxSize()) {
                Caption("Page ${page + 1}")
                GridArea(Area.COVER, page * vm.perPage, vm.cover, vm.cols, vm.rows, vm, drag, Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Caption(if (page < 0) "Start" else "End")
            }
        }
    }
}

/** Rotated unfolded screen: the same apps, in the same order, flowed into one grid sized for the tall screen. */
@Composable
private fun ReflowLayout(screen: ScreenState, vm: HomeViewModel, drag: DragController) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = SIDE_PAD_DP.dp),
    ) {
        Caption(if (vm.pageMode == "strip") "Rotated · reflowed cover page" else "Rotated · cover + inner half, reflowed")
        HomePager(
            count = vm.pages,
            startPage = vm.currentPage,
            onSettled = { vm.currentPage = it },
            screen = screen,
            drag = drag,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val refs = reflowRefs(vm, page)
                val capacity = if (vm.pageMode == "strip") vm.perPage else vm.perPage * 2
                val cols = maxOf(vm.cols, (maxWidth / REFLOW_CELL).toInt())
                val rowsNeeded = (capacity + cols - 1) / cols
                val rowsFit = maxOf(1, (maxHeight / REFLOW_CELL).toInt())
                RefGrid(refs, cols, maxOf(rowsNeeded, rowsFit), vm, drag, Modifier.fillMaxSize())
            }
        }
        PageDots(vm.pages, vm.currentPage)
        DockPanel {
            val dock = buildList {
                val first = if (vm.coverLeft) Area.DOCK_COVER else Area.DOCK_EXTRA
                val second = if (vm.coverLeft) Area.DOCK_EXTRA else Area.DOCK_COVER
                vm.list(first).indices.forEach { add(SlotRef(first, it)) }
                vm.list(second).indices.forEach { add(SlotRef(second, it)) }
            }.filter { vm.keyAt(it) != null }
            DockWithButton(Modifier.fillMaxWidth()) {
                RefRow(dock, vm, drag, Modifier.weight(1f))
            }
        }
    }
}

private val REFLOW_CELL = 92.dp

/** Filled slots of one page in reading order: row by row across both halves (paired) or one cover page (strip). */
private fun reflowRefs(vm: HomeViewModel, page: Int): List<SlotRef> {
    val base = page * vm.perPage
    val refs = mutableListOf<SlotRef>()
    for (r in 0 until vm.rows) {
        val halves = when {
            vm.pageMode == "strip" -> listOf(Area.COVER)
            vm.coverLeft -> listOf(Area.COVER, Area.EXTRA)
            else -> listOf(Area.EXTRA, Area.COVER)
        }
        for (area in halves) {
            for (c in 0 until vm.cols) refs.add(SlotRef(area, base + r * vm.cols + c))
        }
    }
    return refs.filter { vm.keyAt(it) != null }
}

@Composable
private fun RefGrid(refs: List<SlotRef>, cols: Int, rows: Int, vm: HomeViewModel, drag: DragController, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val cellW = maxWidth / cols
        val cellH = maxHeight / rows
        val look = LocalHomeLook.current
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
private fun RefRow(refs: List<SlotRef>, vm: HomeViewModel, drag: DragController, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val count = refs.size.coerceAtLeast(1)
        val cellW = minOf(maxWidth / count, 88.dp)
        val iconSize = minOf(cellW * 0.66f, 56.dp)
        val labels = LocalHomeLook.current.labelsDock
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            refs.forEach { ref ->
                Cell(ref, vm.keyAt(ref), iconSize, labels, vm, drag, Modifier.width(cellW).height(iconSize + if (labels) 36.dp else 20.dp))
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
    startPage: Int,
    onSettled: (Int) -> Unit,
    screen: ScreenState,
    drag: DragController,
    modifier: Modifier,
    pageSize: PageSize = PageSize.Fill,
    pageSpacing: Dp = 0.dp,
    content: @Composable (Int) -> Unit,
) {
    val state = rememberPagerState(initialPage = startPage.coerceIn(0, maxOf(0, count - 1))) { count }
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.collect { onSettled(it) }
    }

    val edgePx = with(LocalDensity.current) { 36.dp.toPx() }
    val edge = when {
        !drag.active -> 0
        drag.pointer.x < edgePx -> -1
        drag.pointer.x > screen.widthPx - edgePx -> 1
        else -> 0
    }
    LaunchedEffect(edge) {
        if (edge == 0) return@LaunchedEffect
        delay(450)
        while (true) {
            val target = (state.currentPage + edge).coerceIn(0, count - 1)
            if (target == state.currentPage) break
            state.animateScrollToPage(target)
            delay(650)
        }
    }

    HorizontalPager(
        state = state,
        modifier = modifier,
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
private fun HalfGrid(isCover: Boolean, page: Int, caption: String, vm: HomeViewModel, drag: DragController, modifier: Modifier) {
    Column(modifier) {
        Caption(caption)
        GridArea(
            area = if (isCover) Area.COVER else Area.EXTRA,
            base = page * vm.perPage,
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
    base: Int,
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
        val look = LocalHomeLook.current
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

/** Shown at the top while dragging. Dropping a home icon here takes it off the home screen (it stays installed). */
@Composable
private fun RemoveZone(drag: DragController) {
    val hovering = drag.overRemove
    val label = if (drag.fromDrawer) "Cancel" else "Remove from home"
    DisposableEffect(Unit) {
        onDispose { drag.removeZone = Rect.Zero }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(top = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .onGloballyPositioned { drag.removeZone = it.boundsInRoot() }
                .clip(RoundedCornerShape(24.dp))
                .background(if (hovering) Color(0xFFD9443B) else Color(0xCC1A1A22))
                .padding(horizontal = 28.dp, vertical = 14.dp),
        ) {
            Text("✕  $label", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DockRow(area: Area, items: List<String?>, vm: HomeViewModel, drag: DragController, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val count = items.size.coerceAtLeast(1)
        val cellW = maxWidth / count
        val iconSize = minOf(cellW * 0.62f, 56.dp)
        val labels = LocalHomeLook.current.labelsDock
        Row {
            items.forEachIndexed { index, key ->
                Cell(SlotRef(area, index), key, iconSize, labels, vm, drag, Modifier.width(cellW).height(iconSize + if (labels) 36.dp else 20.dp))
            }
        }
    }
}

@Composable
private fun DockPanel(content: @Composable () -> Unit) {
    val look = LocalHomeLook.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp)
            .clip(look.dockShape)
            .background(if (look.dockBackground) look.panel else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A dock row with the apps button at the chosen end, when the drawer opens by button. */
@Composable
private fun DockWithButton(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    val look = LocalHomeLook.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (look.button == ButtonEnd.START) DrawerButton()
        content()
        if (look.button == ButtonEnd.END) DrawerButton()
    }
}

@Composable
private fun DockHalf(area: Area, items: List<String?>, withButton: Boolean, vm: HomeViewModel, drag: DragController, modifier: Modifier) {
    if (withButton) {
        DockWithButton(modifier) { DockRow(area, items, vm, drag, Modifier.weight(1f)) }
    } else {
        DockRow(area, items, vm, drag, modifier)
    }
}

@Composable
private fun DrawerButton() {
    val look = LocalHomeLook.current
    Box(
        Modifier
            .padding(horizontal = 8.dp)
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

/** The long-press menu on an empty spot of the home screen. */
@Composable
private fun HomeMenu(onSettings: () -> Unit, onSetup: () -> Unit, onTestPanel: () -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth(0.8f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF0221F2E))
                .padding(vertical = 8.dp),
        ) {
            MenuItem("Quartz settings", Icons.Rounded.Settings, onSettings)
            MenuItem("Run setup again", Icons.Rounded.AutoAwesome, onSetup)
            MenuItem("Test panel", Icons.Rounded.BugReport, onTestPanel)
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Q.Text)
        Spacer(Modifier.width(16.dp))
        Text(text, color = Q.Text, fontSize = 16.sp)
    }
}

/** Pulls down the notification shade, the way most launchers do it. */
@SuppressLint("WrongConstant")
private fun expandNotifications(context: Context) {
    runCatching {
        val service = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
    }.onFailure {
        Diag.log("Couldn't open notifications: ${it.message}")
        Toast.makeText(context, "This phone didn't let Quartz open notifications", Toast.LENGTH_SHORT).show()
    }
}

/** Settings-driven look of the home screen, shared down the tree. */
data class HomeLook(
    val panel: Color = Color.White.copy(alpha = 0.14f),
    val dockShape: Shape = RoundedCornerShape(28.dp),
    val dockBackground: Boolean = true,
    val labelsHome: Boolean = true,
    val labelsDock: Boolean = false,
    val labelsDrawer: Boolean = true,
    val iconScale: Float = 1f,
    val drawerBg: Color = Color(0xEE101018),
    val button: ButtonEnd? = null,
    val buttonIcon: ButtonIcon = ButtonIcon.QUARTZ,
    val openDrawer: () -> Unit = {},
)

val LocalHomeLook = compositionLocalOf { HomeLook() }

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
            .background(LocalHomeLook.current.drawerBg)
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
        showLabel = LocalHomeLook.current.labelsDrawer,
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
