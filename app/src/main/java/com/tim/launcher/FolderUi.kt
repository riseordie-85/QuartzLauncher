package com.tim.launcher

import androidx.compose.foundation.Image
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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
import androidx.compose.foundation.layout.offset
import com.tim.launcher.settings.IconBackground
import com.tim.launcher.settings.SettingsStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val slot = LocalIconSlot.current
    val spec by remember(slot) { IconRender.specState(store, slot) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
      Box {
        FolderTile(spec, size) { ink, inset ->
            when (style) {
                FolderIcon.PLAIN -> Icon(Icons.Rounded.Folder, contentDescription = null, tint = ink, modifier = Modifier.size(size * 0.56f))
                FolderIcon.BADGE -> Icon(badgeIcon(folder.badge), contentDescription = null, tint = ink, modifier = Modifier.size(size * 0.52f))
                FolderIcon.GRID2, FolderIcon.GRID3 -> {
                    val n = if (style == FolderIcon.GRID2) 2 else 3
                    FolderGrid(n, folder.items.take(n * n).map { vm.apps[it] }, size * (1f - 2 * inset))
                }
            }
        }
        // The folder's badge adds up its apps'
        BadgeOverlay(
            store,
            folder.items.mapNotNull { vm.apps[it]?.component?.packageName }.distinct(), // two apps from one package count once
            size,
            Modifier.align(Alignment.TopEnd).offset(x = size * 0.08f, y = -size * 0.08f),
        )
      }
        if (showLabel) {
            val labelColor = LocalLabelColor.current
            Text(
                folder.name,
                color = labelColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = labelStyle(labelColor),
                modifier = Modifier.padding(top = 4.dp).width(size * 1.6f),
            )
        }
    }
}

/**
 * A folder's tile, in the icon style of the place it's in (its shape, background, rim and shadow, the same as your
 * app icons there). [content] is drawn on it, given the ink that reads on the tile and how far in to keep things
 * so they stay inside rounder shapes.
 */
@Composable
internal fun FolderTile(spec: IconRender.Spec, size: Dp, content: @Composable (ink: Color, inset: Float) -> Unit) {
    // With no background the tile is a light veil on dark wallpapers and a dark one on light wallpapers
    val label = LocalLabelColor.current
    val light = label.luminance() > 0.5f
    val veil = label.copy(alpha = if (light) 0.24f else 0.12f).toArgb()
    val line = label.copy(alpha = if (light) 0.18f else 0.14f).toArgb()
    @Suppress("UNUSED_VARIABLE") val epoch = IconRender.epoch
    val drawn = IconRender.folderPlate(spec, veil, line)
    if (drawn == null) {
        @Suppress("UNUSED_VARIABLE") val ready = IconRender.version
    }
    // While a new style is being drawn, keep the last one rather than flashing back to the plain tile
    val last = remember { arrayOfNulls<Pair<ImageBitmap, Float>>(1) }
    if (drawn != null) last[0] = drawn to spec.overscan
    val shown = last[0]
    val ink = IconRender.folderInk(spec)?.let { Color(it) } ?: label
    val inset = IconRender.folderInset(spec)
    val outline = remember(spec.key) { FolderOutline(spec) }
    // Frosted glass sits under the tile's see-through tint, like the dock
    if (spec.background == IconBackground.FROSTED) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().glass(outline, Color.Transparent))
            FolderTileFace(size, shown, veil, outline, ink, inset, content)
        }
    } else {
        FolderTileFace(size, shown, veil, outline, ink, inset, content)
    }
}

@Composable
private fun FolderTileFace(
    size: Dp,
    shown: Pair<ImageBitmap, Float>?,
    veil: Int,
    outline: Shape,
    ink: Color,
    inset: Float,
    content: @Composable (ink: Color, inset: Float) -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .drawBehind {
                if (shown == null) {
                    drawRoundRect(Color(veil), cornerRadius = CornerRadius(this.size.width * 0.28f))
                } else {
                    val d = this.size.width * shown.second
                    val o = ((this.size.width - d) / 2f).roundToInt()
                    drawImage(shown.first, dstOffset = IntOffset(o, o), dstSize = IntSize(d.roundToInt(), d.roundToInt()))
                }
            }
            .clip(outline),
        contentAlignment = Alignment.Center,
    ) { content(ink, inset) }
}

/** The tile's outline, so what's on it stays inside. */
private class FolderOutline(private val spec: IconRender.Spec) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = IconRender.folderPath(spec, 100f)
        p.transform(android.graphics.Matrix().apply { setScale(size.width / 100f, size.height / 100f) })
        return Outline.Generic(p.asComposePath())
    }
}

/** [apps] as an [n]×[n] grid of little icons filling [span]. */
@Composable
internal fun FolderGrid(n: Int, apps: List<AppEntry?>, span: Dp, spec: IconRender.Spec? = null) {
    val gap = span * 0.06f
    val mini = (span - gap * (n + 1)) / n
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        for (r in 0 until n) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (c in 0 until n) {
                    val app = apps.getOrNull(r * n + c)
                    if (app != null) StyledIcon(app, mini, spec = spec)
                    else Spacer(Modifier.size(mini))
                }
            }
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
    LaunchedEffect(folder.items, reorder.moving) { if (reorder.moving == null) reorder.order = folder.items }

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
                    .glass(RoundedCornerShape(24.dp), look.drawerBg)
                    .pointerInput(Unit) { detectTapGestures { } } // taps inside don't close it
                CompositionLocalProvider(LocalLabelColor provides look.onDrawer) {
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
            Modifier.size(40.dp).clip(CircleShape).background(LocalLabelColor.current.copy(alpha = 0.12f)).clickable { menu = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.MoreHoriz, contentDescription = "Folder options", tint = LocalLabelColor.current) }
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
                // Asked from inside the dialog: the dialog is its own window, so outside it the field may not exist yet
                LaunchedEffect(Unit) { focus.requestFocus() }
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
    val ink = LocalLabelColor.current
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
                        textStyle = TextStyle(color = ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(Q.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            vm.renameFolder(folder.id, text)
                            editing = false
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ink.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                } else {
                    Text(
                        folder.name,
                        color = ink,
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
                ) { Icon(Icons.Rounded.MoreHoriz, contentDescription = "Folder options", tint = ink) }
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
                            .background(if (on) Q.Accent else ink.copy(alpha = 0.12f))
                            .clickable { vm.setFolderBadge(folder.id, name) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, contentDescription = name, tint = if (on) Color.White else ink, modifier = Modifier.size(22.dp)) }
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
    Layout(content) { measurables, constraints ->
        // Cells shrink if the columns don't fit across the screen
        val w = if (constraints.hasBoundedWidth) minOf(cellW.roundToPx(), constraints.maxWidth / cols) else cellW.roundToPx()
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
    // Real top-left on screen; bounds above is cut down to what's visible when the folder is scrolled
    val origin = remember { arrayOf(Offset.Zero) }
    DisposableEffect(key) {
        onDispose { reorder.itemBounds.remove(key) }
    }
    val lifted = reorder.moving == key
    Box(
        modifier
            .onGloballyPositioned {
                bounds[0] = it.boundsInRoot()
                origin[0] = it.positionInRoot()
                reorder.itemBounds[key] = bounds[0]
            }
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
                        reorder.pointer = origin[0] + local
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
            }
            // After the touch handlers, so the lifted app's bigger size doesn't shrink the drag distances they report
            .graphicsLayer {
                val s = if (lifted) 1.12f else 1f
                scaleX = s
                scaleY = s
                alpha = if (lifted) 0.85f else 1f
            }
            .clip(RoundedCornerShape(14.dp)),
        contentAlignment = if (list) Alignment.CenterStart else Alignment.Center,
    ) {
        if (list) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StyledIcon(app, 40.dp)
                Spacer(Modifier.width(14.dp))
                Text(app.label, color = LocalLabelColor.current, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            AppIcon(app, 52.dp, showLabel = labels)
        }
    }
}
