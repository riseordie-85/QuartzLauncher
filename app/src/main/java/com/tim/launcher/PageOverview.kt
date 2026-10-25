package com.tim.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.tim.launcher.ui.Q
import kotlin.math.roundToInt

/**
 * Pinch the home screen to see every page at once. Tap a page to go to it, long-press and drag to move it, tap the
 * house to make it your home page (the one Home brings you back to), or delete a page. Its apps move to your
 * other pages.
 */
@Composable
fun PageOverview(vm: HomeViewModel, onAddPage: () -> Unit, onPagesChanged: () -> Unit, onClose: () -> Unit) {
    var confirmDelete by remember { mutableStateOf<Int?>(null) }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD90B0A12))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(Modifier.fillMaxSize().padding(vertical = 20.dp)) {
            Text(
                "Pages",
                color = Q.Text,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                "Tap to open · hold and drag to move · tap the house to make it your home page",
                color = Q.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 20.dp),
            )

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // Size thumbnails to fit the screen height; the row scrolls sideways if there are many
                val cardW = minOf(maxWidth / 3.4f, (maxHeight - 70.dp) * (vm.cols.toFloat() / vm.rows) / 1.15f).coerceAtLeast(90.dp)
                val gap = 16.dp
                val stepPx = with(LocalDensity.current) { (cardW + gap).toPx() }
                val target = if (dragging >= 0) (dragging + (dragX / stepPx).roundToInt()).coerceIn(0, vm.pages - 1) else -1

                Row(
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState(), enabled = dragging < 0)
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (page in 0 until vm.pages) key(page) {
                        // Pages between the dragged page and its target slide over to make room
                        val shift = when {
                            dragging < 0 || page == dragging -> 0f
                            dragging < target && page in (dragging + 1)..target -> -stepPx
                            dragging > target && page in target until dragging -> stepPx
                            else -> 0f
                        }
                        // Snap (not animate) back when the drag ends: the pages have just been reordered under these slots
                        val animatedShift by animateFloatAsState(shift, if (dragging < 0) snap() else spring(), label = "shift")
                        PageCard(
                            vm = vm,
                            page = page,
                            width = cardW,
                            isHome = page == vm.homePage,
                            isCurrent = page == vm.currentPage,
                            canDelete = vm.pages > 1,
                            modifier = Modifier
                                .zIndex(if (page == dragging) 1f else 0f)
                                .graphicsLayer {
                                    translationX = if (page == dragging) dragX else animatedShift
                                    val s = if (page == dragging) 1.06f else 1f
                                    scaleX = s
                                    scaleY = s
                                }
                                .pointerInput(page, vm.pages, stepPx) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            dragging = page
                                            dragX = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragX += amount.x
                                        },
                                        onDragEnd = {
                                            val to = (dragging + (dragX / stepPx).roundToInt()).coerceIn(0, vm.pages - 1)
                                            vm.movePage(dragging, to)
                                            dragging = -1
                                            dragX = 0f
                                        },
                                        onDragCancel = {
                                            dragging = -1
                                            dragX = 0f
                                        },
                                    )
                                },
                            onOpen = {
                                vm.currentPage = page
                                onClose()
                            },
                            onHome = { vm.setHomePage(page) },
                            onDelete = { confirmDelete = page },
                        )
                    }
                    if (vm.pages < HomeViewModel.MAX_PAGES) {
                        Box(
                            Modifier
                                .width(cardW)
                                .height(cardW * (vm.rows.toFloat() / vm.cols) * 1.15f)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                                .clickable { onAddPage() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = Q.Text, modifier = Modifier.size(32.dp))
                                Text("Add page", color = Q.TextDim, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { page ->
        val count = (page * vm.perPage until (page + 1) * vm.perPage).count { vm.cover.getOrNull(it) != null }
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete page ${page + 1}?") },
            text = {
                Text(
                    if (count == 0) "It's empty." else "Its $count apps and folders move to empty spots on your other pages. Nothing is uninstalled.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePage(page)
                    onPagesChanged()
                    confirmDelete = null
                }) { Text("Delete", color = Q.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PageCard(
    vm: HomeViewModel,
    page: Int,
    width: Dp,
    isHome: Boolean,
    isCurrent: Boolean,
    canDelete: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onHome: () -> Unit,
    onDelete: () -> Unit,
) {
    val cols = vm.cols
    val rows = vm.rows
    val height = width * (rows.toFloat() / cols) * 1.15f
    val cell = width / cols
    val base = page * vm.perPage
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(
                    if (isCurrent) 2.dp else 1.dp,
                    if (isCurrent) Q.Accent else Color.White.copy(alpha = 0.18f),
                    RoundedCornerShape(18.dp),
                )
                .pointerInput(page) { detectTapGestures(onTap = { onOpen() }) },
            contentAlignment = Alignment.Center,
        ) {
            Column {
                for (r in 0 until rows) {
                    Row {
                        for (c in 0 until cols) {
                            val key = vm.cover.getOrNull(base + r * cols + c)
                            Box(Modifier.size(cell, height / rows), contentAlignment = Alignment.Center) {
                                MiniIcon(key, vm, cell * 0.62f)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isHome) Q.Accent else Color.White.copy(alpha = 0.12f))
                    .clickable { onHome() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Home, contentDescription = "Make home page", tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Text("Page ${page + 1}", color = Q.Text, fontSize = 13.sp)
            if (canDelete) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete page", tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

/** A tiny app icon, or a tiny folder tile, for the page thumbnails. */
@Composable
private fun MiniIcon(key: String?, vm: HomeViewModel, size: Dp) {
    if (key == null) return
    val folder = vm.folderFor(key)
    if (folder != null) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Color.White.copy(alpha = 0.3f)),
        )
    } else {
        vm.apps[key]?.let { StyledIcon(it, size) }
    }
}
