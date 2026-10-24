package com.tim.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.settings.FolderIcon
import com.tim.launcher.settings.FolderOpen
import com.tim.launcher.settings.FolderView
import com.tim.launcher.settings.RowDirection
import com.tim.launcher.ui.Q
import kotlin.math.roundToInt

/** Badge icons a folder can wear with the "folder with badge" style. */
val FolderBadges: List<Pair<String, ImageVector>> = listOf(
    "folder" to Icons.Rounded.Folder,
    "games" to Icons.Rounded.SportsEsports,
    "work" to Icons.Rounded.Work,
    "social" to Icons.Rounded.Forum,
    "music" to Icons.Rounded.MusicNote,
    "photo" to Icons.Rounded.PhotoCamera,
    "video" to Icons.Rounded.Movie,
    "shopping" to Icons.Rounded.ShoppingBag,
    "money" to Icons.Rounded.AccountBalance,
    "tools" to Icons.Rounded.Build,
    "travel" to Icons.Rounded.Flight,
    "health" to Icons.Rounded.FavoriteBorder,
    "school" to Icons.Rounded.School,
    "news" to Icons.Rounded.Description,
    "home" to Icons.Rounded.Home,
    "star" to Icons.Rounded.Star,
)

private fun badgeIcon(name: String): ImageVector = FolderBadges.firstOrNull { it.first == name }?.second ?: Icons.Rounded.Folder

/** An app or a folder, whichever [key] is. */
@Composable
fun ItemIcon(key: String, vm: HomeViewModel, size: Dp, showLabel: Boolean, modifier: Modifier = Modifier) {
    val folder = vm.folderFor(key)
    if (folder != null) {
        FolderIconView(folder, vm, size, showLabel, modifier)
    } else {
        vm.apps[key]?.let { AppIcon(it, size, showLabel, modifier) }
    }
}

/** A folder's icon: plain, with a badge, or a 2×2 / 3×3 preview of the apps inside. */
@Composable
fun FolderIconView(folder: Folder, vm: HomeViewModel, size: Dp, showLabel: Boolean, modifier: Modifier = Modifier) {
    val style = LocalHomeLook.current.folderIcon
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
                .background(Color.White.copy(alpha = 0.24f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(size * 0.28f)),
            contentAlignment = Alignment.Center,
        ) {
            when (style) {
                FolderIcon.PLAIN -> Icon(Icons.Rounded.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
                FolderIcon.BADGE -> Icon(badgeIcon(folder.badge), contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.52f))
                FolderIcon.GRID2, FolderIcon.GRID3 -> {
                    val n = if (style == FolderIcon.GRID2) 2 else 3
                    val gap = size * 0.06f
                    val mini = (size - gap * (n + 1)) / n
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        for (r in 0 until n) {
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                for (c in 0 until n) {
                                    val app = folder.items.getOrNull(r * n + c)?.let { vm.apps[it] }
                                    if (app != null) Image(app.icon, contentDescription = null, modifier = Modifier.size(mini))
                                    else Spacer(Modifier.size(mini))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showLabel) {
            Text(
                folder.name,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = labelStyle(),
                modifier = Modifier.padding(top = 4.dp).width(size * 1.6f),
            )
        }
    }
}

/**
 * An open folder. Full screen, a floating box, a popup above or below the folder, or an expanding row. Popups stay
 * inside [region] (the half of the unfolded screen the folder is on). Long-press an app and drag it out to put it
 * on the home screen or dock.
 */
@Composable
fun FolderOverlay(
    folder: Folder,
    vm: HomeViewModel,
    drag: DragController,
    anchor: Rect?,
    region: Rect,
    style: FolderOpen,
    view: FolderView,
    columns: Int,
    rowDirection: RowDirection,
    onClose: () -> Unit,
) {
    val look = LocalHomeLook.current
    var carried by remember { mutableStateOf(false) }
    val margin = 12.dp
    val reorder = remember(folder.id) { FolderReorder(folder.items) }
    LaunchedEffect(folder.items) { if (reorder.moving == null) reorder.order = folder.items }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (carried) 0f else 1f }
            .background(if (style == FolderOpen.FULL) Color(0xB3000000) else Color(0x40000000))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
    ) {
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                val panelMods = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(look.drawerBg)
                    .pointerInput(Unit) { detectTapGestures { } } // taps inside don't close it
                when {
                    style == FolderOpen.ROW -> FolderRow(folder, vm, drag, reorder, rowDirection, panelMods, { carried = true }, onClose)
                    else -> FolderPanel(
                        folder, vm, drag, reorder,
                        full = style == FolderOpen.FULL,
                        view = view,
                        columns = columns,
                        modifier = if (style == FolderOpen.FULL) panelMods.windowInsetsPadding(WindowInsets.systemBars) else panelMods,
                        onPickUp = { carried = true },
                        onClose = onClose,
                    )
                }
            },
        ) { measurables, constraints ->
            val m = margin.roundToPx()
            val regionW = region.width.roundToInt()
            val regionH = region.height.roundToInt()
            val panelConstraints = if (style == FolderOpen.FULL) {
                Constraints.fixed(constraints.maxWidth, constraints.maxHeight)
            } else {
                Constraints(maxWidth = (regionW - 2 * m).coerceAtLeast(0), maxHeight = (regionH - 2 * m).coerceAtLeast(0))
            }
            val panel = measurables.first().measure(panelConstraints)
            layout(constraints.maxWidth, constraints.maxHeight) {
                val a = anchor ?: Rect.Zero
                val minX = region.left.roundToInt() + m
                val maxX = (region.right.roundToInt() - m - panel.width).coerceAtLeast(minX)
                val minY = region.top.roundToInt() + m
                val maxY = (region.bottom.roundToInt() - m - panel.height).coerceAtLeast(minY)
                val (x, y) = when {
                    style == FolderOpen.FULL -> 0 to 0
                    style == FolderOpen.CENTER || anchor == null ->
                        (region.center.x - panel.width / 2f).roundToInt().coerceIn(minX, maxX) to
                            (region.center.y - panel.height / 2f).roundToInt().coerceIn(minY, maxY)
                    style == FolderOpen.ROW && rowDirection == RowDirection.HORIZONTAL -> {
                        // A strip beside the folder: to its right if there's room, else to its left
                        val gap = m / 2
                        val right = a.right.roundToInt() + gap
                        val px = if (right + panel.width <= region.right - m) right else a.left.roundToInt() - gap - panel.width
                        px.coerceIn(minX, maxX) to (a.center.y - panel.height / 2f).roundToInt().coerceIn(minY, maxY)
                    }
                    else -> {
                        // Above the folder when it's in the lower half, below it otherwise
                        val above = a.center.y > region.center.y
                        val py = if (above) a.top.roundToInt() - m / 2 - panel.height else a.bottom.roundToInt() + m / 2
                        (a.center.x - panel.width / 2f).roundToInt().coerceIn(minX, maxX) to py.coerceIn(minY, maxY)
                    }
                }
                panel.place(x, y)
            }
        }
    }
}

@Composable
private fun FolderPanel(
    folder: Folder,
    vm: HomeViewModel,
    drag: DragController,
    reorder: FolderReorder,
    full: Boolean,
    view: FolderView,
    columns: Int,
    modifier: Modifier,
    onPickUp: () -> Unit,
    onClose: () -> Unit,
) {
    val cols = columns.coerceIn(2, 6).coerceAtMost(folder.items.size.coerceAtLeast(2))
    val cell = 84.dp
    Column(
        modifier
            .then(if (full) Modifier.fillMaxSize() else if (view == FolderView.GRID) Modifier.width(cell * cols + 32.dp) else Modifier.width(280.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FolderHeader(folder, vm, onClose)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .onGloballyPositioned { reorder.area = it.boundsInRoot() }
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (view == FolderView.GRID) {
                FolderGrid(cols, cell, cell + 8.dp) {
                    reorder.order.forEach { k ->
                        key(k) { FolderItem(folder.id, k, vm, drag, reorder, Modifier, list = false, onPickUp, onClose) }
                    }
                }
            } else {
                reorder.order.forEach { k ->
                    key(k) { FolderItem(folder.id, k, vm, drag, reorder, Modifier.fillMaxWidth().height(56.dp), list = true, onPickUp, onClose) }
                }
            }
        }
    }
}

/** The expanding row: just the apps, in a strip beside or above/below the folder. */
@Composable
private fun FolderRow(
    folder: Folder,
    vm: HomeViewModel,
    drag: DragController,
    reorder: FolderReorder,
    direction: RowDirection,
    modifier: Modifier,
    onPickUp: () -> Unit,
    onClose: () -> Unit,
) {
    val cell = 72.dp
    if (direction == RowDirection.HORIZONTAL) {
        Row(
            modifier.onGloballyPositioned { reorder.area = it.boundsInRoot() }.horizontalScroll(rememberScrollState()).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            reorder.order.forEach { k ->
                key(k) { FolderItem(folder.id, k, vm, drag, reorder, Modifier.size(cell), list = false, onPickUp, onClose, labels = false) }
            }
            FolderRowOptions(folder, vm, onClose)
        }
    } else {
        Column(
            modifier.onGloballyPositioned { reorder.area = it.boundsInRoot() }.verticalScroll(rememberScrollState()).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            reorder.order.forEach { k ->
                key(k) { FolderItem(folder.id, k, vm, drag, reorder, Modifier.size(cell), list = false, onPickUp, onClose, labels = false) }
            }
            FolderRowOptions(folder, vm, onClose)
        }
    }
}

/**
 * The expanding row has no room for a header, so its last spot is a small options button: Rename and Badge open a
 * dialog, Remove folder puts the apps back on the screen.
 */
@Composable
private fun FolderRowOptions(folder: Folder, vm: HomeViewModel, onClose: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var badges by remember { mutableStateOf(false) }
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { menu = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.MoreHoriz, contentDescription = "Folder options", tint = Color.White) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
            DropdownMenuItem(text = { Text("Badge") }, onClick = { menu = false; badges = true })
            DropdownMenuItem(text = { Text("Remove folder", color = Q.Danger) }, onClick = {
                menu = false
                vm.dissolveFolder(folder.id)
                onClose()
            })
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(TextFieldValue(folder.name, TextRange(0, folder.name.length))) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        val done = {
            vm.renameFolder(folder.id, text.text)
            renaming = false
        }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename folder") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = if (it.text.length <= 30) it else it.copy(text = it.text.take(30)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { done() }),
                    modifier = Modifier.focusRequester(focus),
                )
            },
            confirmButton = { TextButton(onClick = { done() }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }

    if (badges) {
        AlertDialog(
            onDismissRequest = { badges = false },
            title = { Text("Folder badge") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FolderBadges.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (name, icon) ->
                                val on = name == folder.badge
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(if (on) Q.Accent else Color.White.copy(alpha = 0.12f))
                                        .clickable { vm.setFolderBadge(folder.id, name) },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(icon, contentDescription = name, tint = Color.White, modifier = Modifier.size(22.dp)) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { badges = false }) { Text("Done") } },
        )
    }
}

/** Folder name (tap to rename) and a menu for the badge and removing the folder. */
@Composable
private fun FolderHeader(folder: Folder, vm: HomeViewModel, onClose: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(folder.id) { mutableStateOf(folder.name) }
    var menu by remember { mutableStateOf(false) }
    var badges by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(36.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (editing) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it.take(30) },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(Q.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            vm.renameFolder(folder.id, text)
                            editing = false
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                } else {
                    Text(
                        folder.name,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { text = folder.name; editing = true },
                    )
                }
            }
            Box {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.MoreHoriz, contentDescription = "Folder options", tint = Color.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; text = folder.name; editing = true })
                    DropdownMenuItem(text = { Text("Badge") }, onClick = { menu = false; badges = !badges })
                    DropdownMenuItem(text = { Text("Remove folder", color = Q.Danger) }, onClick = {
                        menu = false
                        vm.dissolveFolder(folder.id)
                        onClose()
                    })
                }
            }
        }
        if (badges) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FolderBadges.forEach { (name, icon) ->
                    val on = name == folder.badge
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (on) Q.Accent else Color.White.copy(alpha = 0.12f))
                            .clickable { vm.setFolderBadge(folder.id, name) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, contentDescription = name, tint = Color.White, modifier = Modifier.size(22.dp)) }
                }
            }
        }
    }
}

/**
 * Moving apps around inside an open folder. Drag an app over another to swap places; drag it past the edge of the
 * folder and it's handed off to the home screen, where you can drop it anywhere.
 */
class FolderReorder(initial: List<String>) {
    var order by mutableStateOf(initial)
    var moving by mutableStateOf<String?>(null)
    var pointer = Offset.Zero
    var handedOff = false
    /** The app we last swapped with. Skipped until the pointer leaves it, so stale bounds can't swap it straight back. */
    var lastOver: String? = null
    /** The apps area of the open folder, in screen coordinates. */
    var area: Rect = Rect.Zero
    val itemBounds = HashMap<String, Rect>()
}

/** A grid where every app is a direct child, so an app keeps its identity (and its drag) as it moves between rows. */
@Composable
private fun FolderGrid(cols: Int, cellW: Dp, cellH: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, _ ->
        val w = cellW.roundToPx()
        val h = cellH.roundToPx()
        val placeables = measurables.map { it.measure(Constraints.fixed(w, h)) }
        val rows = ((placeables.size + cols - 1) / cols).coerceAtLeast(1)
        layout(w * cols, h * rows) {
            placeables.forEachIndexed { i, p -> p.place((i % cols) * w, (i / cols) * h) }
        }
    }
}

@Composable
private fun FolderItem(
    folderId: String,
    key: String,
    vm: HomeViewModel,
    drag: DragController,
    reorder: FolderReorder,
    modifier: Modifier,
    list: Boolean,
    onPickUp: () -> Unit,
    onClose: () -> Unit,
    labels: Boolean = true,
) {
    val app = vm.apps[key] ?: return
    val haptics = LocalHapticFeedback.current
    val bounds = remember { arrayOf(Rect.Zero) }
    DisposableEffect(key) {
        onDispose { reorder.itemBounds.remove(key) }
    }
    val lifted = reorder.moving == key
    Box(
        modifier
            .onGloballyPositioned {
                bounds[0] = it.boundsInRoot()
                reorder.itemBounds[key] = bounds[0]
            }
            .graphicsLayer {
                val s = if (lifted) 1.12f else 1f
                scaleX = s
                scaleY = s
                alpha = if (lifted) 0.85f else 1f
            }
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(key) {
                detectTapGestures(
                    onTap = {
                        vm.launch(app, bounds[0].toAndroidRect())
                        onClose()
                    },
                    onLongPress = { },
                )
            }
            .pointerInput(key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        reorder.moving = key
                        reorder.handedOff = false
                        reorder.lastOver = null
                        reorder.pointer = bounds[0].topLeft + local
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        if (reorder.handedOff) {
                            drag.moveBy(amount)
                        } else {
                            reorder.pointer += amount
                            if (reorder.area != Rect.Zero && !reorder.area.contains(reorder.pointer)) {
                                // Out of the folder: keep the new order, then carry the app to the home screen
                                vm.reorderFolder(folderId, reorder.order)
                                reorder.handedOff = true
                                onPickUp()
                                drag.startFromFolder(folderId, key, reorder.pointer)
                            } else {
                                val over = reorder.itemBounds.entries
                                    .firstOrNull { it.key != key && it.value.contains(reorder.pointer) }?.key
                                if (over != reorder.lastOver) reorder.lastOver = null
                                if (over != null && over != reorder.lastOver) {
                                    reorder.lastOver = over
                                    val next = reorder.order.toMutableList()
                                    val to = next.indexOf(over)
                                    next.remove(key)
                                    next.add(to, key)
                                    reorder.order = next
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        if (reorder.handedOff) {
                            drag.drop(vm)
                            onClose()
                        } else {
                            vm.reorderFolder(folderId, reorder.order)
                        }
                        reorder.moving = null
                    },
                    onDragCancel = {
                        if (reorder.handedOff) {
                            drag.cancel()
                            onClose()
                        } else {
                            vm.reorderFolder(folderId, reorder.order)
                        }
                        reorder.moving = null
                    },
                )
            },
        contentAlignment = if (list) Alignment.CenterStart else Alignment.Center,
    ) {
        if (list) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(14.dp))
                Text(app.label, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            AppIcon(app, 52.dp, showLabel = labels)
        }
    }
}
