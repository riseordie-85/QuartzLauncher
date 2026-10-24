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
import android.content.pm.ActivityInfo
import androidx.lifecycle.Lifecycle
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
import com.tim.launcher.settings.S
import com.tim.launcher.settings.Scope
import com.tim.launcher.settings.SettingsActivity
import com.tim.launcher.settings.SettingsStore
import com.tim.launcher.settings.DeviceType
import com.tim.launcher.settings.dockCounts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.tim.launcher.ui.Q


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
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.reloadIfChanged() }

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
        carryOver = store[S.carryOver],
        innerCols = store[S.gridCols, Scope.INNER],
        innerRows = store[S.gridRows, Scope.INNER],
        dockPosition = store[S.dockPosition, scope],
        dockLength = store[S.dockLength, scope],
        dockAlign = store[S.dockAlign, scope],
    )
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
        // Pressing Home while already home, with nothing open, returns to your home page
        if (homeTick > 0 && !drawerOpen && !panelOpen && !menuOpen) vm.goHome()
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
        HomeLayout(screen, vm, drag, showHint = !hintSeen)

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            AppDrawer(vm, drag, drawerCols.coerceAtLeast(1), onClose = { drawerOpen = false })
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
    val carryOver: Boolean = true,
    val innerCols: Int = 8,
    val innerRows: Int = 6,
    val dockPosition: DockPosition = DockPosition.BOTTOM,
    val dockLength: DockLength = DockLength.FULL,
    val dockAlign: DockAlign = DockAlign.END,
)

val LocalHomeLook = compositionLocalOf { HomeLook() }

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

internal fun labelStyle() = TextStyle(
    shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 1f), blurRadius = 4f),
)

@Composable
internal fun HintLine() {
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
