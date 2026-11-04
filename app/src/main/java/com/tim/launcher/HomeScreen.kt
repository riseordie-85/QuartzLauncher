@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.tim.launcher

import android.app.Activity
import android.os.SystemClock
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.RectangleShape
import com.tim.launcher.wallpaper.WallCache
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.DpSize
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
import android.content.pm.ActivityInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tim.launcher.settings.DockAlign
import com.tim.launcher.settings.DockLength
import com.tim.launcher.settings.DockPosition
import kotlinx.coroutines.flow.drop
import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
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
import com.tim.launcher.settings.dockPositionOn
import com.tim.launcher.settings.migrateDockShape
import com.tim.launcher.settings.DrawerOpen
import com.tim.launcher.settings.GestureAction
import com.tim.launcher.settings.GestureSlot
import com.tim.launcher.settings.S
import com.tim.launcher.settings.Scope
import com.tim.launcher.settings.SettingsActivity
import com.tim.launcher.settings.SettingsStore
import com.tim.launcher.settings.DeviceType
import com.tim.launcher.settings.dockCounts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.tim.launcher.ui.Q
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.tim.launcher.settings.FolderIcon
import com.tim.launcher.settings.Route
import com.tim.launcher.weather.Weather
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch


/** Where the dragged icon is over another one: its middle (to make a folder), or the side before or after it. */
enum class DropZone { CENTER, BEFORE, AFTER }

/**
 * Tracks the app or folder being dragged (from a home slot, the drawer, or out of a folder) and where every slot
 * sits on screen. Holding it over the middle of another icon for a moment arms [folderTarget]: dropping then makes
 * a folder. Pausing anywhere else shows [plan]: the icons slide over to make room, and dropping keeps them there.
 */
class DragController {
    var source by mutableStateOf<SlotRef?>(null)
        private set
    /** The app or folder key being carried. */
    var key by mutableStateOf<String?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set

    /** True while an app picked up from the drawer is being placed on the home screen. */
    var fromDrawer by mutableStateOf(false)
        private set

    /** The folder an app is being dragged out of. */
    var fromFolder by mutableStateOf<String?>(null)
        private set

    /** The icon held over long enough that dropping makes (or adds to) a folder. */
    var folderTarget by mutableStateOf<SlotRef?>(null)

    /** The icons slid over to make room where the dragged icon would land (shown live while dragging). */
    var plan by mutableStateOf<DropPlan?>(null)

    /** True while the pages are shown reflowed (unfolded upright). Icons don't slide there; the drop still works. */
    var reflow = false

    /** True when the dock runs down the side of the screen, so "before" and "after" are above and below. */
    var dockVertical = false

    /** The slot each icon was last drawn in, so an icon that moves to a new slot can glide over from there. */
    val lastSlot = HashMap<String, SlotRef>()

    /** For a widget being carried: where it was grabbed (from its top-left corner) and its size, in px. */
    var widgetGrab = Offset.Zero
        private set
    var widgetSize = Size.Zero
        private set

    /** How wide the screen is (px), so a carried widget only lands on a page you can see. */
    var screenWidth = 0f

    /** The widget being resized (handles on its edges), and the widget whose menu is open. */
    var resizing by mutableStateOf<String?>(null)
    var widgetMenu by mutableStateOf<String?>(null)

    /** When the last drag ended; icons still glide for a moment after (dropping, or springing back). */
    var endedAt = 0L
        private set

    /** What that drag carried. */
    var lastKey: String? = null
        private set

    /** What a slot shows right now: the slid-over contents while a plan is showing, otherwise the saved layout. */
    fun shown(vm: HomeViewModel, ref: SlotRef): String? {
        val p = plan
        return if (p != null && !reflow && p.area == ref.area && ref.index in p.items.indices) p.items[ref.index] else vm.keyAt(ref)
    }

    /** The whole page or dock list as it shows right now. */
    fun shownList(vm: HomeViewModel, area: Area): List<String?> {
        val p = plan
        return if (p != null && !reflow && p.area == area) p.items else vm.list(area)
    }

    /** The slot where the dragged icon would land; it's drawn empty. */
    fun isGap(ref: SlotRef): Boolean = plan?.let { !reflow && it.area == ref.area && it.at == ref.index } == true

    fun zone(ref: SlotRef): DropZone {
        val b = bounds[ref] ?: return DropZone.CENTER
        if (b.width <= 0f || b.height <= 0f) return DropZone.CENTER
        val fx = (pointer.x - b.left) / b.width
        val fy = (pointer.y - b.top) / b.height
        val dock = ref.area == Area.DOCK_COVER || ref.area == Area.DOCK_EXTRA
        val along = if (dock && dockVertical) fy else fx
        val across = if (dock && dockVertical) fx else fy
        return when {
            along in 0.28f..0.72f && across in 0.15f..0.8f -> DropZone.CENTER
            along < 0.5f -> DropZone.BEFORE
            else -> DropZone.AFTER
        }
    }

    /** What dropping on [ref] would do. Null when nothing should happen (an app back onto its own folder). */
    fun planFor(vm: HomeViewModel, ref: SlotRef, zone: DropZone): DropPlan? {
        val carried = key ?: return null
        val folder = fromFolder
        if (folder != null && shown(vm, ref) == FOLDER_PREFIX + folder) return null
        if (ref.area != Area.DOCK_COVER && ref.area != Area.DOCK_EXTRA) return vm.planGrid(carried, source, ref)
        // The dock is packed, so count the icons drawn before the drop point (not the gap or the icon being moved)
        val drawn = bounds.keys.filter { it.area == ref.area }.sortedBy { it.index }
        val gap = plan?.takeIf { !reflow && it.area == ref.area }?.at
        val from = source
        val skip: (SlotRef) -> Boolean = { r -> if (gap != null) r.index == gap else r == from }
        val idx = drawn.indexOf(ref).coerceAtLeast(0)
        val dropPoint = idx + if (zone == DropZone.AFTER) 1 else 0
        val position = drawn.take(dropPoint).count { !skip(it) && shown(vm, it) != null }
        return vm.planDock(carried, source, ref.area, position)
    }

    val bounds = HashMap<SlotRef, Rect>()

    /** Where the "Remove from home" and "Uninstall" targets sit while they're showing. */
    var removeZone: Rect = Rect.Zero
    var uninstallZone: Rect = Rect.Zero

    /** An app held and let go without moving: its menu (and the slot it's in, or null from the drawer). */
    var appMenu by mutableStateOf<Pair<String, SlotRef?>?>(null)

    val active: Boolean get() = key != null

    val overRemove: Boolean
        get() = active && removeZone != Rect.Zero && removeZone.inflate(24f).contains(pointer)

    val overUninstall: Boolean
        get() = active && uninstallZone != Rect.Zero && uninstallZone.inflate(24f).contains(pointer)

    val hover: SlotRef?
        get() = if (!active || overRemove || overUninstall) null else bounds.entries.firstOrNull { it.value.contains(pointer) }?.key

    fun start(ref: SlotRef, key: String, at: Offset) {
        begin(key, at)
        source = ref
    }

    fun startWidget(ref: SlotRef, key: String, at: Offset, grab: Offset, size: Size, snapshot: ImageBitmap? = null) {
        begin(key, at)
        source = ref
        widgetGrab = grab
        widgetSize = size
        widgetSnapshot = snapshot
    }

    /** A picture of an app's widget being carried (the live widget can only be shown in one place). */
    var widgetSnapshot: ImageBitmap? = null
        private set

    /** The size of a home screen cell, for working out how many cells an app's widget needs. */
    var cellSize = DpSize(80.dp, 100.dp)

    /**
     * For a carried widget: the slot its top-left cell would land on (the cell under its top-left corner, pulled in
     * so the whole widget stays on the page).
     */
    fun widgetDrop(vm: HomeViewModel): Int? {
        val id = key?.takeIf { isWidgetKey(it) }?.removePrefix(WIDGET_PREFIX) ?: return null
        val perPage = vm.perPage
        if (perPage <= 0) return null
        // Only cells on screen: every page stays composed, so the pages off to the sides have cells too
        val wide = screenWidth
        val cells = bounds.entries.filter {
            it.key.area == Area.COVER && it.value.width > 0f && (wide <= 0f || it.value.center.x in 0f..wide)
        }
        // The page under your finger is where it lands. Going by the top-left corner alone, a widget held by its
        // right side near the screen's left edge would land on the page off screen.
        val under = cells.minByOrNull { (it.value.center - pointer).getDistanceSquared() } ?: return null
        val page = under.key.index / perPage
        val onPage = cells.filter { it.key.index / perPage == page }
        val first = onPage.first().value
        val corner = pointer - widgetGrab + Offset(first.width / 2f, first.height / 2f)
        val nearest = onPage.minByOrNull { (it.value.center - corner).getDistanceSquared() }?.key ?: return null
        return vm.clampWidgetSpot(id, nearest.index)
    }

    fun startFromDrawer(key: String, at: Offset) {
        begin(key, at)
        fromDrawer = true
    }

    fun startFromFolder(folderId: String, key: String, at: Offset) {
        begin(key, at)
        fromFolder = folderId
    }

    private fun begin(key: String, at: Offset) {
        clear()
        this.key = key
        pointer = at
    }

    fun moveBy(delta: Offset) {
        pointer += delta
    }

    fun drop(vm: HomeViewModel) {
        val carried = key
        if (carried != null && isWidgetKey(carried)) {
            val id = carried.removePrefix(WIDGET_PREFIX)
            if (overRemove) vm.removeWidget(id) else widgetDrop(vm)?.let { vm.moveWidget(id, it) }
            clear()
            return
        }
        val from = source
        val to = hover
        val folder = fromFolder
        val armed = folderTarget
        val showing = plan?.takeIf { !reflow }
        // The dropped icon appears where you let go, so it shouldn't glide over from the slot it was picked up from
        if (carried != null) lastSlot.remove(carried)
        when {
            carried == null -> Unit
            // Dropped on Uninstall: Android asks to confirm; the icon stays put meanwhile
            overUninstall -> vm.uninstall(carried)
            overRemove -> when {
                from != null -> vm.remove(from)
                folder != null -> vm.takeOutOfFolder(folder, carried)
                else -> Unit
            }
            armed != null && to != null && armed == to ->
                if (showing != null) vm.folderAfterPlan(showing, to, carried, from, folder)
                else vm.makeFolder(to, carried, from, folder)
            to != null -> planFor(vm, to, zone(to))?.let { vm.dropInto(it, carried, from, folder) }
            // Let go just off the icons (between cells, past the dock's end): keep what's showing
            showing != null -> vm.dropInto(showing, carried, from, folder)
        }
        clear()
    }

    fun cancel() = clear()

    private fun clear() {
        if (key != null) {
            endedAt = SystemClock.uptimeMillis()
            lastKey = key
        }
        plan = null
        source = null
        key = null
        fromDrawer = false
        fromFolder = null
        folderTarget = null
        widgetSnapshot = null
    }
}

@Composable
fun HomeScreen(activity: Activity, homeTick: Int, vm: HomeViewModel = viewModel()) {
    val screen = rememberScreenState(activity)
    val prefs = remember { Prefs.get(activity) }
    val drag = remember { DragController() }
    SideEffect { drag.screenWidth = screen.widthPx.toFloat() }
    val store = remember { SettingsStore.get(activity).also { it.migrateDockShape() } }
    var drawerOpen by remember { mutableStateOf(false) }
    // Opened by the Search gesture: the drawer starts with the keyboard up
    var drawerSearch by remember { mutableStateOf(false) }
    // Where the dock is on screen, so an open drawer can leave it visible
    var dockRect by remember { mutableStateOf(Rect.Zero) }
    var panelOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<Pair<String, SlotRef>?>(null) }
    var lockPrompt by remember { mutableStateOf(false) }
    var overviewOpen by remember { mutableStateOf(false) }
    var widgetPicker by remember { mutableStateOf(false) }
    // Set while two fingers are down, so a two-finger gesture doesn't also count as a one-finger swipe
    var multiTouchUntil by remember { mutableStateOf(0L) }

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
            store.set(S.gridCols, gridScope, vm.cols)
            store.set(S.gridRows, gridScope, vm.rows)
        }
        if (store.owner(S.pages, Scope.GLOBAL) == null) store.set(S.pages, value = vm.pages)
        if (store.owner(S.coverSide, Scope.GLOBAL) == null) {
            store.set(S.coverSide, value = if (vm.coverLeft) CoverSide.LEFT else CoverSide.RIGHT)
        }
        true
    }
    val settingCols = store[S.gridCols, gridScope]
    val settingRows = store[S.gridRows, gridScope]
    LaunchedEffect(settingCols, settingRows, gridScope) {
        vm.setGrid(settingCols, settingRows)
    }
    val settingSide = store[S.coverSide]
    LaunchedEffect(settingSide) {
        val left = settingSide == CoverSide.LEFT
        if (vm.coverLeft != left) vm.setCoverSide(left)
    }
    val settingPages = store[S.pages]
    LaunchedEffect(settingPages) { vm.setPageCount(settingPages) }

    // Phones and the cover screen stay upright unless you allow rotation. The unfolded screen and tablets always
    // follow the device (Android requires it on large screens), and their grids reflow instead.
    val rotate = store[S.rotateHome]
    LaunchedEffect(screen.posture, rotate) {
        val upright = !rotate && (screen.posture == Posture.PHONE || screen.posture == Posture.COVER)
        activity.requestedOrientation =
            if (upright) ActivityInfo.SCREEN_ORIENTATION_NOSENSOR else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // Pick up a layout imported from another device while Settings was open
    // (then re-apply this screen's grid setting, in case the file's grid differs and the setting itself didn't change)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.reloadIfChanged()
        vm.setGrid(store[S.gridCols, gridScope], store[S.gridRows, gridScope])
        vm.addPendingWidgets()
    }

    // Weather for weather widgets: only while one is on your pages and the home screen is showing, every half hour
    val needsWeather = vm.widgets.values.any { it.kind.weather }
    val weatherScope = rememberCoroutineScope()
    LifecycleResumeEffect(needsWeather) {
        val job = if (needsWeather) {
            weatherScope.launch {
                while (true) {
                    Weather.refresh(activity)
                    delay(30 * 60_000L)
                }
            }
        } else {
            null
        }
        onPauseOrDispose { job?.cancel() }
    }

    // Declared before `look`, whose onSwipePast calls it (local functions aren't visible before their declaration)
    fun run(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.APP_DRAWER -> {
                drawerSearch = false
                drawerOpen = true
            }
            GestureAction.SEARCH -> {
                drawerSearch = true
                drawerOpen = true
            }
            GestureAction.NOTIFICATIONS -> expandNotifications(activity)
            GestureAction.SETTINGS -> SettingsActivity.open(activity)
            GestureAction.LOCK -> if (!LockService.lock()) lockPrompt = true
            GestureAction.SWAP_DOCK -> store.set(S.dockSwapped, value = !store[S.dockSwapped])
            GestureAction.OVERVIEW -> overviewOpen = true
        }
    }

    // The wallpaper behind everything: blurred for glass, and how bright it is so text stays readable
    var quartzWallpaper by remember { mutableStateOf(WallCache.isActive(activity)) }
    LifecycleResumeEffect(Unit) {
        quartzWallpaper = WallCache.isActive(activity)
        onPauseOrDispose { }
    }
    val blur = store[S.panelBlur]
    val wallGen = WallCache.generation
    val wall = remember(wallGen, quartzWallpaper, screen.widthPx, screen.heightPx, blur, vm.coverLeft) {
        if (quartzWallpaper) WallCache.glass(activity, screen.widthPx, screen.heightPx, blur) else null
    }
    val glass = wall?.takeIf { it.blurred && blur > 0.02f }
    val wallBrightness = wall?.brightness ?: 0.3f

    val panelColor = Color(store[S.panelColor])
    val opacity = store[S.panelOpacity].coerceIn(0.05f, 1f)
    val panel = panelColor.copy(alpha = opacity)
    // Without blur the drawer needs to be nearly solid to hide the icons under it; with blur the glass does that
    val drawerBg = panelColor.copy(alpha = if (glass != null) opacity else maxOf(opacity, 0.85f))
    val homeLabels = if (quartzWallpaper && wallBrightness > 0.62f) Ink.Dark else Ink.Light
    val look = HomeLook(
        panel = panel,
        dock = DockStyle.of(store),
        swapHandle = store[S.swapHandle] && store.dockPositionOn(scope) != DockPosition.NONE,
        dockIconScale = store[S.dockIconSize],
        swapDock = { store.set(S.dockSwapped, value = !store[S.dockSwapped]) },
        labelsHome = store[S.labelsHome, scope],
        labelsDock = store[S.labelsDock],
        labelsDrawer = store[S.labelsDrawer],
        iconScale = store[S.iconSize, scope],
        drawerBg = drawerBg,
        onPanel = if (!store[S.dockBackground]) homeLabels else Ink.on(Ink.brightness(panel, wallBrightness)),
        onDrawer = Ink.on(Ink.brightness(drawerBg, if (glass != null) wallBrightness else 0.3f)),
        onHome = homeLabels,
        button = if (store[S.drawerOpen] == DrawerOpen.SWIPE) null else store[S.drawerButtonEnd],
        buttonIcon = store[S.drawerButtonIcon],
        openDrawer = {
            drawerSearch = false
            drawerOpen = true
        },
        onDockBounds = { dockRect = it },
        carryOver = store[S.carryOver],
        innerCols = store[S.gridCols, Scope.INNER],
        innerRows = store[S.gridRows, Scope.INNER],
        dockPosition = store.dockPositionOn(scope),
        dockLength = store[S.dockLength, scope],
        dockAlign = store[S.dockAlign, scope],
        dockIconAlign = store[S.dockIconAlign, scope],
        folderIcon = store[S.folderIcon],
        openFolder = { id, ref -> openFolder = id to ref },
        onSwipePast = { first ->
            run(store[S.gesture(if (first) GestureSlot.SWIPE_PAST_LEFT else GestureSlot.SWIPE_PAST_RIGHT)])
        },
    )
    // Status and navigation bar icons follow what's behind them: dark on light glass or wallpaper
    val view = LocalView.current
    val darkBars = (if (drawerOpen) look.onDrawer else homeLabels) == Ink.Dark
    SideEffect {
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = darkBars
            isAppearanceLightNavigationBars = darkBars
        }
    }

    val deviceType = when {
        screen.foldable -> DeviceType.FOLDABLE
        screen.posture == Posture.TABLET -> DeviceType.TABLET
        else -> DeviceType.PHONE
    }
    val (dockFolded, dockUnfolded) = store.dockCounts(deviceType)
    LaunchedEffect(dockFolded, dockUnfolded) {
        val folded = if (dockFolded == 0) HomeViewModel.UNLIMITED_DOCK else dockFolded
        val extra = when {
            deviceType != DeviceType.FOLDABLE || dockFolded == 0 -> 0
            dockUnfolded == 0 -> HomeViewModel.UNLIMITED_DOCK
            else -> dockUnfolded - dockFolded
        }
        vm.setDockSizes(folded, extra)
    }
    val drawerCols = store[S.drawerCols, scope].let {
        if (scope == Scope.INNER && store.isLinked(S.drawerCols, Scope.INNER)) it * 2 else it
    }

    var hintSeen by remember { mutableStateOf(prefs.getBoolean(Prefs.HINT_SEEN, false)) }

    LaunchedEffect(homeTick) {
        // Pressing Home while already home, with nothing open, returns to your home page
        if (homeTick > 0 && !drawerOpen && !panelOpen && !menuOpen && !overviewOpen && !widgetPicker && drag.widgetMenu == null && drag.resizing == null) vm.goHome()
        drawerOpen = false
        panelOpen = false
        menuOpen = false
        openFolder = null
        overviewOpen = false
        widgetPicker = false
        drag.widgetMenu = null
        drag.appMenu = null
        drag.resizing = null
    }
    BackHandler {
        drawerOpen = false
        panelOpen = false
        menuOpen = false
        openFolder = null
        overviewOpen = false
        widgetPicker = false
        drag.widgetMenu = null
        drag.appMenu = null
        drag.resizing = null
    }

    // Hold a dragged icon over the middle of another for a moment to make a folder. Pause anywhere else and the
    // icons slide over to make room for it.
    val haptics = LocalHapticFeedback.current
    val hoverAt by remember { derivedStateOf { drag.hover?.let { it to drag.zone(it) } } }
    val overRemove by remember { derivedStateOf { drag.overRemove || drag.overUninstall } }
    LaunchedEffect(hoverAt, drag.key, overRemove) {
        drag.folderTarget = null
        val carried = drag.key ?: return@LaunchedEffect
        if (overRemove) {
            drag.plan = null
            return@LaunchedEffect
        }
        // A widget being moved doesn't make folders; what it pushes aside is worked out below
        if (isWidgetKey(carried)) return@LaunchedEffect
        val (target, zone) = hoverAt ?: return@LaunchedEffect
        val existing = drag.shown(vm, target)
        val canFolder = existing != null && existing != carried && !isFolderKey(carried) && !isWidgetKey(existing) &&
            !drag.isGap(target) && !(drag.fromFolder != null && vm.folderFor(existing)?.id == drag.fromFolder)
        if (zone == DropZone.CENTER && canFolder) {
            delay(550)
            drag.folderTarget = target
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        } else if (!drag.reflow) {
            delay(180)
            drag.plan = drag.planFor(vm, target, zone)
        }
    }

    // Carrying a widget: pause over a spot and everything in the way (icons, other widgets) moves aside to show
    // where it would all go; dropping keeps it like that
    val widgetSpot by remember { derivedStateOf { if (isWidgetKey(drag.key) && !drag.overRemove) drag.widgetDrop(vm) else null } }
    LaunchedEffect(widgetSpot, drag.key) {
        val carried = drag.key?.takeIf { isWidgetKey(it) } ?: return@LaunchedEffect
        val spot = widgetSpot
        if (spot == null) {
            drag.plan = null
            return@LaunchedEffect
        }
        delay(150)
        // Let go (or picked up something else) while waiting: what's showing is already right
        if (drag.key != carried) return@LaunchedEffect
        drag.plan = vm.planWidgetMove(carried.removePrefix(WIDGET_PREFIX), spot)
    }
    // Done resizing: the page shows what was saved again
    LaunchedEffect(drag.resizing) {
        if (drag.resizing == null && !drag.active) drag.plan = null
    }

    // Log every posture change and time how long the new layout took to reach the screen.
    LaunchedEffect(screen.posture, screen.widthDp, screen.heightDp) {
        Diag.log("Posture: ${screen.posture.label}, ${screen.widthDp}×${screen.heightDp}dp, display ${screen.displayId}")
        openFolder = null
        overviewOpen = false
        drag.resizing = null
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
                        if (!drag.active && !drawerOpen && openFolder == null && !overviewOpen && SystemClock.uptimeMillis() > multiTouchUntil) {
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
                // Two-finger gestures: pinch, and two-finger swipe down. Watches without taking touches from anything.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var startDist = -1f
                    var startCenter = Offset.Zero
                    var lastDist = 0f
                    var lastCenter = Offset.Zero
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.filter { it.pressed }
                        if (down.size >= 2) {
                            multiTouchUntil = SystemClock.uptimeMillis() + 400
                            val a = down[0].position
                            val b = down[1].position
                            val dist = (a - b).getDistance()
                            val center = (a + b) / 2f
                            if (startDist < 0f) {
                                startDist = dist
                                startCenter = center
                            }
                            lastDist = dist
                            lastCenter = center
                        }
                    } while (event.changes.any { it.pressed })
                    if (startDist > 0f && !drag.active && !drawerOpen && openFolder == null && !overviewOpen) {
                        val dy = lastCenter.y - startCenter.y
                        when {
                            lastDist < startDist * 0.65f -> run(store[S.gesture(GestureSlot.PINCH)])
                            dy > 100.dp.toPx() && lastDist > startDist * 0.7f -> run(store[S.gesture(GestureSlot.TWO_FINGER_DOWN)])
                        }
                    }
                }
            }
            .onRightClick(behind = true) {
                if (!drawerOpen && openFolder == null && !overviewOpen && !menuOpen && !widgetPicker && drag.widgetMenu == null && drag.appMenu == null) {
                    drag.resizing = null
                    menuOpen = true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { drag.resizing = null },
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
      CompositionLocalProvider(LocalHomeLook provides look, LocalGlass provides glass, LocalLabelColor provides homeLabels) {
        // Covered by the drawer or the page overview: the widgets underneath hold still
        CompositionLocalProvider(LocalWidgetsPaused provides (drawerOpen || overviewOpen)) {
            HomeLayout(screen, vm, drag, showHint = !hintSeen)
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            // The drawer leaves the dock showing (unless you'd rather hide it), so it only covers the rest
            val density = LocalDensity.current
            val position = look.dockPosition
            val keepDock = !store[S.drawerHideDock, scope] && position != DockPosition.NONE && dockRect != Rect.Zero
            val gap = with(density) {
                when {
                    !keepDock -> PaddingValues(0.dp)
                    position == DockPosition.BOTTOM -> PaddingValues(bottom = (screen.heightPx - dockRect.top).coerceAtLeast(0f).toDp())
                    position == DockPosition.TOP -> PaddingValues(top = dockRect.bottom.coerceAtLeast(0f).toDp())
                    position == DockPosition.LEFT -> PaddingValues(start = dockRect.right.coerceAtLeast(0f).toDp())
                    else -> PaddingValues(end = (screen.widthPx - dockRect.left).coerceAtLeast(0f).toDp())
                }
            }
            // Only the screen edges the drawer touches need room for the status and navigation bars
            val edges = if (!keepDock) {
                WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical
            } else {
                when (position) {
                    DockPosition.BOTTOM -> WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                    DockPosition.TOP -> WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    DockPosition.LEFT -> WindowInsetsSides.Vertical + WindowInsetsSides.Right
                    else -> WindowInsetsSides.Vertical + WindowInsetsSides.Left
                }
            }
            val drawerLook = DrawerLook(
                style = store[S.drawerStyle, scope],
                size = store[S.drawerSize, scope],
                view = store[S.drawerView],
                scroll = store[S.drawerScroll],
                cols = if (store[S.drawerColsAuto]) 0 else drawerCols.coerceAtLeast(1),
                labels = store[S.labelsDrawer],
                iconScale = store[S.drawerIconSize],
                sort = store[S.drawerSort],
                dividers = store[S.drawerDividers],
                tabs = store[S.drawerTabMode],
                searchSettings = store[S.searchSettings],
                searchContacts = store[S.searchContacts],
                railOnRight = position == DockPosition.LEFT,
                letters = store[S.drawerLetters],
                remember = store[S.drawerRemember],
                edges = edges,
            )
            Box(Modifier.fillMaxSize().padding(gap)) {
                AppDrawer(vm, drag, drawerLook, startWithSearch = drawerSearch, onClose = { drawerOpen = false })
            }
        }

        openFolder?.let { (id, ref) ->
            val folder = vm.folders[id]
            if (folder == null) {
                LaunchedEffect(id) { openFolder = null }
            } else {
                val anchor = drag.bounds[ref]
                val w = screen.widthPx.toFloat()
                val h = screen.heightPx.toFloat()
                // On the unfolded screen a folder opens within its own half
                val region = if (screen.posture == Posture.UNFOLDED && screen.widthDp > screen.heightDp && anchor != null) {
                    val hinge = (screen.hingeCenterPx ?: (screen.widthPx / 2)).toFloat()
                    if (anchor.center.x < hinge) Rect(0f, 0f, hinge, h) else Rect(hinge, 0f, w, h)
                } else {
                    Rect(0f, 0f, w, h)
                }
                FolderOverlay(
                    folder = folder,
                    vm = vm,
                    drag = drag,
                    anchor = anchor,
                    region = region,
                    style = store[S.folderOpen],
                    view = store[S.folderView],
                    columns = store[S.folderCols],
                    rowDirection = store[S.folderRow],
                    onClose = { openFolder = null },
                )
            }
        }

        // Asked once per pick-up, not on every frame of the drag
        val carriedKey = drag.key
        val canUninstallCarried = remember(carriedKey) { vm.canUninstall(carriedKey) }
        if (drag.active) RemoveZone(drag, canUninstall = canUninstallCarried)

        // A widget being moved: where it would land, and the widget under your finger
        drag.key?.let { carried -> vm.widgetFor(carried)?.let { CarriedWidget(it, vm, drag) } }

        // The icon under your finger while dragging
        drag.key?.takeIf { !isWidgetKey(it) }?.let { carried ->
            val sizePx = with(LocalDensity.current) { 64.dp.toPx() }
            ItemIcon(
                key = carried,
                vm = vm,
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
                onWallpaper = {
                    menuOpen = false
                    SettingsActivity.open(activity, route = Route.WALLPAPER)
                },
                onPages = {
                    menuOpen = false
                    overviewOpen = true
                },
                onWidgets = {
                    menuOpen = false
                    widgetPicker = true
                },
                onDismiss = { menuOpen = false },
            )
        }

        AnimatedVisibility(visible = widgetPicker, enter = fadeIn(), exit = fadeOut()) {
            WidgetPicker(
                onPick = { kind ->
                    if (vm.addWidget(kind, vm.currentPage)) {
                        widgetPicker = false
                        if (kind.weather) Weather.refresh(activity)
                    } else {
                        Toast.makeText(activity, "No room on this page for the ${kind.label.lowercase()}. Make some space, or try another page.", Toast.LENGTH_LONG).show()
                    }
                },
                onPickApp = { info ->
                    val cell = drag.cellSize
                    val spans = AppWidgets.spans(activity, info, cell.width.value, cell.height.value, vm.cols, vm.rows)
                    val page = vm.currentPage
                    if (vm.findSpace(page, spans.w, spans.h, spans.minW, spans.minH) == null) {
                        Toast.makeText(activity, "No room on this page for that widget. Make some space, or try another page.", Toast.LENGTH_LONG).show()
                    } else {
                        widgetPicker = false
                        AppWidgets.add(activity, info) { home, id ->
                            // The home screen may have been recreated while Android's screens showed: use the one now
                            val now = (home as? ComponentActivity)?.let { ViewModelProvider(it).get(HomeViewModel::class.java) } ?: vm
                            now.addAppWidget(page, id, info.provider.flattenToString(), spans)
                        }
                    }
                },
                sizeOf = { info ->
                    val cell = drag.cellSize
                    AppWidgets.spans(activity, info, cell.width.value, cell.height.value, vm.cols, vm.rows)
                },
                onDismiss = { widgetPicker = false },
            )
        }

        drag.widgetMenu?.let { id ->
            val widget = vm.widgets[id]
            if (widget == null) {
                LaunchedEffect(id) { drag.widgetMenu = null }
            } else {
                WidgetMenu(
                    widget = widget,
                    canResize = !drag.reflow,
                    onResize = {
                        drag.widgetMenu = null
                        drag.resizing = id
                    },
                    onKind = { kind ->
                        if (vm.changeWidgetKind(id, kind)) {
                            drag.widgetMenu = null
                            if (kind.weather) Weather.refresh(activity)
                        }
                    },
                    onSettings = {
                        drag.widgetMenu = null
                        SettingsActivity.open(activity, route = Route.WIDGETS)
                    },
                    onRemove = {
                        drag.widgetMenu = null
                        vm.removeWidget(id)
                    },
                    onDismiss = { drag.widgetMenu = null },
                    onReconfigure = {
                        drag.widgetMenu = null
                        AppWidgets.reconfigure(activity, widget.appWidgetId)
                    },
                    onGlass = { vm.setWidgetGlass(id, it) },
                )
            }
        }

        drag.appMenu?.let { (key, ref) ->
            val app = vm.apps[key]
            if (app == null) {
                LaunchedEffect(key) { drag.appMenu = null }
            } else {
                AppMenu(
                    app = app,
                    canUninstall = vm.canUninstall(key),
                    onHome = ref != null,
                    onInfo = {
                        drag.appMenu = null
                        vm.appInfo(app)
                    },
                    onUninstall = {
                        drag.appMenu = null
                        vm.uninstall(key)
                    },
                    onRemove = {
                        drag.appMenu = null
                        // Only if it's still there (the layout may have changed while the menu was open)
                        ref?.takeIf { vm.keyAt(it) == key }?.let { vm.remove(it) }
                    },
                    onAddToHome = {
                        drag.appMenu = null
                        vm.addToHome(key)
                    },
                    onDismiss = { drag.appMenu = null },
                )
            }
        }

        if (drag.resizing != null) {
            if (drag.resizing?.let { vm.widgets[it] } == null) LaunchedEffect(Unit) { drag.resizing = null }
            ResizeBar(onDone = { drag.resizing = null })
        }

        AnimatedVisibility(visible = overviewOpen, enter = fadeIn() + scaleIn(initialScale = 1.08f), exit = fadeOut() + scaleOut(targetScale = 1.08f)) {
            PageOverview(
                vm = vm,
                onAddPage = { store.set(S.pages, value = (vm.pages + 1).coerceAtMost(HomeViewModel.MAX_PAGES)) },
                onPagesChanged = { store.set(S.pages, value = vm.pages) },
                onClose = { overviewOpen = false },
            )
        }

        AnimatedVisibility(visible = panelOpen, enter = fadeIn(), exit = fadeOut()) {
            TestPanel(activity, vm, screen, onClose = { panelOpen = false })
        }

        if (lockPrompt) {
            AlertDialog(
                onDismissRequest = { lockPrompt = false },
                title = { Text("Turn on Quartz Lock") },
                text = {
                    Text(
                        "To lock your screen with a gesture, Android needs you to turn on Quartz Lock in " +
                            "Accessibility settings. It can only lock the screen: it can't read your screen or " +
                            "anything you type.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        lockPrompt = false
                        LockService.openSettings(activity)
                    }) { Text("Open settings") }
                },
                dismissButton = { TextButton(onClick = { lockPrompt = false }) { Text("Not now") } },
            )
        }
      }
    }
}

/** Shown at the top while dragging. Dropping a home icon here takes it off the home screen (it stays installed). */
@Composable
private fun RemoveZone(drag: DragController, canUninstall: Boolean) {
    val label = if (drag.fromDrawer) "Cancel" else "Remove from home"
    DisposableEffect(Unit) {
        onDispose {
            drag.removeZone = Rect.Zero
            drag.uninstallZone = Rect.Zero
        }
    }
    if (!canUninstall) {
        SideEffect { drag.uninstallZone = Rect.Zero }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        Box(
            Modifier
                .onGloballyPositioned { drag.removeZone = it.boundsInRoot() }
                .clip(RoundedCornerShape(24.dp))
                .background(if (drag.overRemove) Color(0xFFD9443B) else Color(0xCC1A1A22))
                .padding(horizontal = 28.dp, vertical = 14.dp),
        ) {
            Text("✕  $label", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        if (canUninstall) {
            Box(
                Modifier
                    .onGloballyPositioned { drag.uninstallZone = it.boundsInRoot() }
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (drag.overUninstall) Color(0xFFD9443B) else Color(0xCC1A1A22))
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            ) {
                Text("🗑  Uninstall", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** The long-press menu on an empty spot of the home screen. */
@Composable
private fun HomeMenu(
    onSettings: () -> Unit,
    onSetup: () -> Unit,
    onWallpaper: () -> Unit,
    onPages: () -> Unit,
    onWidgets: () -> Unit,
    onDismiss: () -> Unit,
) {
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
            MenuItem("Wallpaper", Icons.Rounded.Wallpaper, onWallpaper)
            MenuItem("Widgets", Icons.Rounded.Widgets, onWidgets)
            MenuItem("Quartz settings", Icons.Rounded.Settings, onSettings)
            MenuItem("Run setup again", Icons.Rounded.AutoAwesome, onSetup)
            MenuItem("Pages", Icons.Rounded.GridView, onPages)
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
    val dock: DockStyle = DockStyle(),
    val swapHandle: Boolean = false,
    val dockIconScale: Float = 1f,
    val swapDock: () -> Unit = {},
    /** Where the dock sits on screen, reported by the dock itself. */
    val onDockBounds: (Rect) -> Unit = {},
    val labelsHome: Boolean = true,
    val labelsDock: Boolean = false,
    val labelsDrawer: Boolean = true,
    val iconScale: Float = 1f,
    val drawerBg: Color = Color(0xEE101018),
    /** Text and icon color on the dock, the drawer and folders, and on the wallpaper. */
    val onPanel: Color = Color.White,
    val onDrawer: Color = Color.White,
    val onHome: Color = Color.White,
    val button: ButtonEnd? = null,
    val buttonIcon: ButtonIcon = ButtonIcon.QUARTZ,
    val openDrawer: () -> Unit = {},
    val carryOver: Boolean = true,
    val innerCols: Int = 8,
    val innerRows: Int = 6,
    val dockPosition: DockPosition = DockPosition.BOTTOM,
    val dockLength: DockLength = DockLength.FULL,
    val dockAlign: DockAlign = DockAlign.END,
    val dockIconAlign: DockAlign = DockAlign.CENTER,
    val folderIcon: FolderIcon = FolderIcon.GRID2,
    val openFolder: (String, SlotRef) -> Unit = { _, _ -> },
    /** Swiping past the first (true) or last (false) page. */
    val onSwipePast: (Boolean) -> Unit = {},
)

val LocalHomeLook = compositionLocalOf { HomeLook() }

/** The color of icon labels where they're drawn: white on dark glass and wallpapers, dark on light ones. */
val LocalLabelColor = compositionLocalOf { Color.White }

@Composable
internal fun Cell(
    ref: SlotRef,
    key: String?,
    iconSize: Dp,
    showLabel: Boolean,
    vm: HomeViewModel,
    drag: DragController,
    modifier: Modifier,
) {
    val app = key?.let { vm.apps[it] }
    val folder = vm.folderFor(key)
    val haptics = LocalHapticFeedback.current
    val look = LocalHomeLook.current

    DisposableEffect(ref) {
        onDispose { drag.bounds.remove(ref) }
    }

    // Slide in from wherever this icon was last drawn, when the icons make room for a drag (or spring back after)
    val glide = remember(ref) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val fromSlot = remember(ref, key) { key?.let { drag.lastSlot[it] } }
    LaunchedEffect(ref, key) {
        val here = drag.bounds[ref]?.topLeft
        val from = fromSlot?.takeIf { it != ref }?.let { drag.bounds[it]?.topLeft }
        // Not the icon being carried: it's hidden in its gap and shows up there when you let go
        // (or a widget being resized pushes it aside)
        val recent = key != drag.key && (drag.active || drag.resizing != null || SystemClock.uptimeMillis() - drag.endedAt < 600)
        if (from != null && here != null && from != here && recent) {
            glide.snapTo(from - here)
            glide.animateTo(Offset.Zero, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        } else {
            glide.snapTo(Offset.Zero)
        }
    }
    SideEffect {
        if (key != null) drag.lastSlot[key] = ref
    }

    // The icon being dragged keeps its gesture even when the icons sliding over put something else in its slot
    val held = drag.source == ref && drag.key != null
    val gestureKey = if (held) drag.key else key
    val gestureApp = gestureKey?.let { vm.apps[it] }
    val gestureFolder = vm.folderFor(gestureKey)
    val gestures = if (gestureKey == null || (gestureApp == null && gestureFolder == null)) Modifier else Modifier
        .pointerInput(ref, gestureKey) {
            detectTapGestures(
                onTap = {
                    if (gestureFolder != null) look.openFolder(gestureFolder.id, ref)
                    else if (gestureApp != null) vm.launch(gestureApp, drag.bounds[ref]?.toAndroidRect())
                },
                onLongPress = { }, // long press belongs to dragging
            )
        }
        .pointerInput(ref, gestureKey) {
            var moved = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    moved = 0f
                    val origin = drag.bounds[ref]?.topLeft ?: Offset.Zero
                    // bounds are measured outside the 2.dp padding; `local` is inside it
                    val pad = 2.dp.toPx()
                    drag.start(ref, gestureKey, origin + Offset(pad, pad) + local)
                },
                onDrag = { change, amount ->
                    change.consume()
                    moved += amount.getDistance()
                    drag.moveBy(amount)
                },
                onDragEnd = {
                    // Held and let go without moving: an app's menu (app info, uninstall, remove)
                    if (moved < viewConfiguration.touchSlop * 2 && gestureApp != null) {
                        drag.cancel()
                        drag.appMenu = gestureApp.key to ref
                    } else {
                        drag.drop(vm)
                    }
                },
                onDragCancel = { drag.cancel() },
            )
        }

    val hovered by remember(ref) { derivedStateOf { drag.hover == ref && drag.source != ref && drag.plan == null && !isWidgetKey(drag.key) } }
    val folderArmed = drag.folderTarget == ref
    Box(
        modifier
            .onGloballyPositioned { drag.bounds[ref] = it.boundsInRoot() }
            .padding(2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    folderArmed -> Q.Accent.copy(alpha = 0.45f)
                    hovered -> Color.White.copy(alpha = 0.18f)
                    else -> Color.Transparent
                },
            )
            // A right click opens an app's menu at once (a folder just opens)
            .onRightClick(enabled = app != null || folder != null) {
                if (folder != null) look.openFolder(folder.id, ref) else if (app != null) drag.appMenu = app.key to ref
            }
            .then(gestures),
        contentAlignment = Alignment.Center,
    ) {
        if (key != null && (app != null || folder != null)) {
            ItemIcon(
                key = key,
                vm = vm,
                size = iconSize,
                showLabel = showLabel,
                modifier = Modifier
                    .alpha(if (drag.active && key == drag.key && (drag.source == ref || drag.isGap(ref))) 0f else 1f)
                    .graphicsLayer {
                        val s = if (folderArmed) 1.12f else 1f
                        scaleX = s
                        scaleY = s
                        translationX = glide.value.x
                        translationY = glide.value.y
                    },
            )
        }
    }
}

@Composable
fun AppIcon(app: AppEntry, size: Dp, showLabel: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            StyledIcon(app, size, contentDescription = app.label)
            BadgeOverlay(store, listOf(app.component.packageName), size, Modifier.align(Alignment.TopEnd).offset(x = size * 0.08f, y = -size * 0.08f))
        }
        if (showLabel) {
            val color = LocalLabelColor.current
            Text(
                text = app.label,
                color = color,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = labelStyle(color),
                modifier = Modifier.padding(top = 4.dp).widthIn(max = size * 1.6f),
            )
        }
    }
}

/**
 * An app's icon as you've styled it for where it's shown, [size] across. A shadow is drawn on a slightly bigger
 * picture, which spills past [size] so the icon itself stays the same size.
 */
@Composable
fun StyledIcon(
    app: AppEntry,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    /** A style to show instead of the saved one (the settings preview). */
    spec: IconRender.Spec? = null,
) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val slot = LocalIconSlot.current
    val shared = remember(slot) { IconRender.specState(store, slot) }
    val s = spec ?: shared.value
    @Suppress("UNUSED_VARIABLE") val epoch = IconRender.epoch // drawn icons were thrown away: look again
    val drawn = IconRender.icon(context, app, s)
    // Still being drawn: redraw when new icons are ready (an icon already drawn doesn't need to)
    if (drawn == null && !s.plain) {
        @Suppress("UNUSED_VARIABLE") val ready = IconRender.version
    }
    // While a new style is being drawn, keep showing the last one rather than flashing back to the plain icon
    val last = remember(app.key) { arrayOfNulls<Pair<ImageBitmap, Float>>(1) }
    val shown: Pair<ImageBitmap, Float> = when {
        drawn != null -> drawn to (if (drawn !== app.icon) s.overscan else 1f)
        s.plain -> app.icon to 1f
        else -> last[0] ?: (app.icon to 1f)
    }
    if (drawn != null || s.plain) last[0] = shown
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Image(shown.first, contentDescription = contentDescription, modifier = Modifier.requiredSize(size * shown.second))
    }
}

/** Light labels get a soft dark shadow to lift them off busy wallpapers; dark labels get a faint light one. */
internal fun labelStyle(color: Color = Color.White) = TextStyle(
    shadow = if (color.luminance() > 0.5f) {
        Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 1f), blurRadius = 4f)
    } else {
        Shadow(color = Color.White.copy(alpha = 0.35f), offset = Offset(0f, 1f), blurRadius = 3f)
    },
)

@Composable
internal fun HintLine() {
    Text(
        "Phase 0 test · swipe up for apps · long-press empty space for the test panel",
        color = LocalLabelColor.current.copy(alpha = 0.75f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        style = labelStyle(LocalLabelColor.current),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}
