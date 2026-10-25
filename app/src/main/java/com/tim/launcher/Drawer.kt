package com.tim.launcher

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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.settings.DrawerScroll
import com.tim.launcher.settings.DrawerSort
import com.tim.launcher.settings.DrawerStyle
import com.tim.launcher.settings.DrawerView
import com.tim.launcher.settings.Route
import com.tim.launcher.settings.SettingsActivity
import com.tim.launcher.settings.TabMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything about how the drawer looks and behaves, read from settings. */
data class DrawerLook(
    val style: DrawerStyle = DrawerStyle.FULL,
    /** How much of the screen a floating, side or bottom drawer takes, 0.4–1. */
    val size: Float = 0.8f,
    val view: DrawerView = DrawerView.ICONS,
    val scroll: DrawerScroll = DrawerScroll.VERTICAL,
    /** 0 means fit as many as the space allows. */
    val cols: Int = 0,
    val labels: Boolean = true,
    val iconScale: Float = 1f,
    val sort: DrawerSort = DrawerSort.ALPHA,
    val dividers: Boolean = false,
    val tabs: TabMode = TabMode.NONE,
    val searchSettings: Boolean = true,
    val searchContacts: Boolean = false,
    /** Tabs sit on the right (away from a dock on the left). */
    val railOnRight: Boolean = false,
    /** The screen edges this drawer area touches, so only those get system bar padding. */
    val edges: WindowInsetsSides = WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical,
)

private val PanelShape = RoundedCornerShape(28.dp)

/** How big drawer icons are (Settings › Appearance › Icon sizes). */
private val LocalDrawerIconScale = staticCompositionLocalOf { 1f }

/** Clamps a size between [min] and [max], even on a screen smaller than [min]. */
private fun Dp.fit(min: Dp, max: Dp): Dp = coerceIn(minOf(min, max), max)

/**
 * The app drawer. Full screen, a floating box, a side list or a bottom popup. Tabs (yours, or Android's categories)
 * run down a rail on one side, search sits on top, and apps show as a grid, a list or swipeable pages. Long-press an
 * app and drag it out to put it on the home screen or dock.
 */
@Composable
fun AppDrawer(vm: HomeViewModel, drag: DragController, look: DrawerLook, startWithSearch: Boolean, onClose: () -> Unit) {
    val home = LocalHomeLook.current
    val ink = home.onDrawer
    // Once an app is picked up, the drawer stays invisible until it closes (no flash on drop)
    var carried by remember { mutableStateOf(false) }
    val full = look.style == DrawerStyle.FULL

    CompositionLocalProvider(LocalLabelColor provides ink, LocalIconSlot provides IconSlot.DRAWER, LocalDrawerIconScale provides look.iconScale) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (carried || drag.fromDrawer) 0f else 1f }
                // No dimming behind the floating, side and bottom styles; tapping outside still closes them
                // Keyed on the style, so switching it while open (via the + tab button) doesn't keep the old rule
                .pointerInput(full) { detectTapGestures { if (!full) onClose() } },
        ) {
            val size = look.size.coerceIn(0.4f, 1f)
            val margin = 12.dp
            val panel: Modifier = when (look.style) {
                DrawerStyle.FULL -> Modifier.fillMaxSize()
                DrawerStyle.FLOATING -> Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.systemBars.only(look.edges))
                    .padding(margin)
                    .width((maxWidth * size).fit(300.dp, maxWidth))
                    .height((maxHeight * size).fit(360.dp, maxHeight))
                DrawerStyle.SIDE -> Modifier
                    .align(if (look.railOnRight) Alignment.CenterEnd else Alignment.CenterStart)
                    .windowInsetsPadding(WindowInsets.systemBars.only(look.edges))
                    .padding(margin)
                    .width((maxWidth * size * 0.55f).fit(300.dp, maxWidth))
                    .fillMaxHeight()
                DrawerStyle.BOTTOM -> Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.systemBars.only(look.edges))
                    .padding(margin)
                    .width((maxWidth * size).fit(300.dp, minOf(maxWidth, 760.dp)))
                    .height((maxHeight * size * 0.85f).fit(320.dp, maxHeight))
            }
            Box(
                panel
                    .glass(if (full) RectangleShape else PanelShape, home.drawerBg)
                    // Taps inside don't close it, and long-presses don't reach the home screen underneath
                    .pointerInput(Unit) { detectTapGestures { } }
                    .then(if (full) Modifier.windowInsetsPadding(WindowInsets.systemBars.only(look.edges)) else Modifier),
            ) {
                DrawerBody(vm, drag, look, ink, full, startWithSearch, onPickUp = { carried = true }, onClose = onClose)
            }
        }
    }
}

/** What one row of the drawer is: a heading or an app. */
private sealed interface Entry {
    /** [key] is unique even when the same heading shows twice (A to Z can put "#" both before A and after Z). */
    data class Heading(val text: String, val key: String) : Entry
    data class App(val app: AppEntry) : Entry
}

@Composable
private fun DrawerBody(
    vm: HomeViewModel,
    drag: DragController,
    look: DrawerLook,
    ink: Color,
    full: Boolean,
    startWithSearch: Boolean,
    onPickUp: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { DrawerTabs.load(context) }
    val all = vm.sortedApps
    val tabs: List<TabView> = remember(look.tabs, all, DrawerTabs.tabs.toList()) {
        val everything = TabView("all", "All apps", "all", all)
        when (look.tabs) {
            TabMode.NONE -> listOf(everything)
            TabMode.AUTO -> listOf(everything) + AppCategories.tabs(all)
            TabMode.CUSTOM -> listOf(everything) + DrawerTabs.tabs.map { t ->
                TabView(t.id, t.name, t.icon, t.apps.mapNotNull { vm.apps[it] })
            }
        }
    }
    var tabId by remember { mutableStateOf("all") }
    val tab = tabs.firstOrNull { it.id == tabId } ?: tabs.first()
    var query by remember { mutableStateOf("") }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 560.dp
        val showTabs = look.tabs != TabMode.NONE
        Row(Modifier.fillMaxSize()) {
            if (showTabs && !look.railOnRight) TabRail(tabs, tab.id, wide, ink, look.tabs == TabMode.CUSTOM, onClose) { tabId = it; query = "" }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (full) {
                    // Drag the handle down (or tap it) to close
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy > 12f) onClose() } }
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = 0.5f)))
                    }
                }
                SearchField(query, ink, startWithSearch, Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) { query = it }
                if (showTabs && !wide && query.isEmpty()) {
                    Text(
                        tab.name,
                        color = ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (query.isNotBlank()) {
                        SearchResults(vm, drag, look, ink, query, onPickUp, onClose)
                    } else {
                        AppsView(vm, drag, look, ink, tab, onPickUp, onClose)
                    }
                }
            }
            if (showTabs && look.railOnRight) TabRail(tabs, tab.id, wide, ink, look.tabs == TabMode.CUSTOM, onClose) { tabId = it; query = "" }
        }
    }
}

// ---- Tabs ----

private fun tabIcon(key: String): ImageVector =
    if (key == "all") Icons.Rounded.Apps else FolderBadges.firstOrNull { it.first == key }?.second ?: Icons.Rounded.Apps

/**
 * The tabs. On wide screens a sidebar with names; on narrow ones a rail of icons (the drawer shows the tab's name as
 * a heading instead). With your own tabs there's a + at the end to make a new one.
 */
@Composable
private fun TabRail(
    tabs: List<TabView>,
    selected: String,
    wide: Boolean,
    ink: Color,
    canAdd: Boolean,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxHeight()
            .width(if (wide) 176.dp else 60.dp)
            .background(ink.copy(alpha = 0.05f))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { t ->
            val on = t.id == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) ink.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onSelect(t.id) }
                    .padding(horizontal = if (wide) 12.dp else 0.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (wide) Arrangement.Start else Arrangement.Center,
            ) {
                Icon(tabIcon(t.icon), contentDescription = t.name, tint = ink.copy(alpha = if (on) 1f else 0.7f), modifier = Modifier.size(22.dp))
                if (wide) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        t.name,
                        color = ink.copy(alpha = if (on) 1f else 0.75f),
                        fontSize = 15.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (canAdd) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onClose()
                        SettingsActivity.open(context, route = Route.DRAWER)
                    }
                    .padding(horizontal = if (wide) 12.dp else 0.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (wide) Arrangement.Start else Arrangement.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Make a tab", tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                if (wide) {
                    Spacer(Modifier.width(12.dp))
                    Text("New tab", color = ink.copy(alpha = 0.6f), fontSize = 15.sp)
                }
            }
        }
    }
}

// ---- Search ----

@Composable
private fun SearchField(query: String, ink: Color, focus: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(focus) { if (focus) runCatching { requester.requestFocus() } }
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search", color = ink.copy(alpha = 0.5f), fontSize = 16.sp)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = ink, fontSize = 16.sp),
                cursorBrush = SolidColor(ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth().focusRequester(requester),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Clear",
                tint = ink.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onChange("") },
            )
        }
    }
}

@Composable
private fun SearchResults(
    vm: HomeViewModel,
    drag: DragController,
    look: DrawerLook,
    ink: Color,
    query: String,
    onPickUp: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val apps = remember(query, vm.sortedApps) { SearchSources.apps(query, vm.sortedApps) }
    val settings = remember(query, look.searchSettings) { if (look.searchSettings) SearchSources.settings(query) else emptyList() }
    val contacts by produceState(emptyList<SearchSources.Contact>(), query, look.searchContacts) {
        value = if (look.searchContacts) withContext(Dispatchers.IO) { SearchSources.contacts(context, query) } else emptyList()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = columnsFor(look, maxWidth)
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
            if (apps.isEmpty() && settings.isEmpty() && contacts.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Nothing matches “$query”", color = ink.copy(alpha = 0.6f), fontSize = 15.sp, modifier = Modifier.padding(16.dp))
                }
            }
            if (apps.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Heading("Apps", ink) }
                items(apps.size, key = { "a" + apps[it].key }) { i ->
                    DrawerItem(apps[i], vm, drag, list = false, labels = look.labels, onPickUp = onPickUp, onClose = onClose)
                }
            }
            if (settings.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Heading("Settings", ink) }
                settings.forEach { s ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "s" + s.title) {
                        ResultRow(s.icon, s.title, ink) {
                            SearchSources.openSetting(context, s)
                            onClose()
                        }
                    }
                }
            }
            if (contacts.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Heading("Contacts", ink) }
                contacts.forEach { c ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "c${c.id}") {
                        ResultRow(Icons.Rounded.Person, c.name, ink) {
                            SearchSources.openContact(context, c)
                            onClose()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(icon: ImageVector, title: String, ink: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(ink.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, color = ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Heading(text: String, ink: Color) {
    Text(
        text.uppercase(),
        color = ink.copy(alpha = 0.55f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 6.dp),
    )
}

// ---- Apps ----

/** Columns that fit: one per ~88dp, or your own number. */
private fun columnsFor(look: DrawerLook, width: Dp): Int =
    if (look.cols > 0) look.cols else (width / (88.dp * look.iconScale.coerceAtLeast(0.7f))).toInt().coerceIn(2, 12)

private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

/** The tab's apps in the chosen order, with headings between letters (A to Z) or months (newest first). */
private fun entries(apps: List<AppEntry>, look: DrawerLook): List<Entry> {
    val sorted = when (look.sort) {
        DrawerSort.INSTALLED -> apps.sortedByDescending { it.installed }
        else -> apps.sortedBy { it.label.lowercase() }
    }
    if (!look.dividers) return sorted.map { Entry.App(it) }
    val out = ArrayList<Entry>()
    var last: String? = null
    sorted.forEach { app ->
        val head = when (look.sort) {
            DrawerSort.INSTALLED -> if (app.installed > 0) monthFormat.format(Date(app.installed)) else "Earlier"
            else -> app.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"
        }
        if (head != last) {
            out += Entry.Heading(head, "h${out.size}:$head")
            last = head
        }
        out += Entry.App(app)
    }
    return out
}

@Composable
private fun AppsView(
    vm: HomeViewModel,
    drag: DragController,
    look: DrawerLook,
    ink: Color,
    tab: TabView,
    onPickUp: () -> Unit,
    onClose: () -> Unit,
) {
    val list = remember(tab, look.sort, look.dividers) { entries(tab.apps, look) }
    if (tab.apps.isEmpty()) {
        Text(
            if (tab.id == "all") "No apps yet" else "This tab is empty. Add apps to it in Settings › App drawer.",
            color = ink.copy(alpha = 0.6f),
            fontSize = 15.sp,
            modifier = Modifier.padding(20.dp),
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = columnsFor(look, maxWidth)
        when {
            look.view == DrawerView.LIST -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                items(list, key = { e -> if (e is Entry.App) e.app.key else (e as Entry.Heading).key }) { e ->
                    when (e) {
                        is Entry.Heading -> Heading(e.text, ink)
                        is Entry.App -> DrawerItem(e.app, vm, drag, list = true, labels = true, onPickUp = onPickUp, onClose = onClose)
                    }
                }
            }
            look.scroll == DrawerScroll.HORIZONTAL -> {
                // Pages of apps you swipe through sideways; headings don't apply here
                val rows = (maxHeight / (104.dp * look.iconScale)).toInt().coerceAtLeast(1)
                val perPage = (rows * cols).coerceAtLeast(1)
                val apps = list.filterIsInstance<Entry.App>().map { it.app }
                val pageCount = (apps.size + perPage - 1) / perPage
                val pager = rememberPagerState { pageCount.coerceAtLeast(1) }
                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                            apps.drop(page * perPage).take(perPage).chunked(cols).forEach { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    row.forEach { app ->
                                        Box(Modifier.weight(1f)) {
                                            DrawerItem(app, vm, drag, list = false, labels = look.labels, onPickUp = onPickUp, onClose = onClose)
                                        }
                                    }
                                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    if (pageCount > 1) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                            repeat(pageCount) { i ->
                                Box(
                                    Modifier
                                        .padding(horizontal = 4.dp)
                                        .size(if (i == pager.currentPage) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(ink.copy(alpha = if (i == pager.currentPage) 0.9f else 0.4f)),
                                )
                            }
                        }
                    }
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                list.forEach { e ->
                    when (e) {
                        is Entry.Heading -> item(span = { GridItemSpan(maxLineSpan) }, key = e.key) { Heading(e.text, ink) }
                        is Entry.App -> item(key = e.app.key) {
                            DrawerItem(e.app, vm, drag, list = false, labels = look.labels, onPickUp = onPickUp, onClose = onClose)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One app in the drawer: tap to open, long-press and move your finger to carry it to the home screen or dock. A
 * long-press without moving (touching to stop a fast scroll) does nothing.
 */
@Composable
private fun DrawerItem(
    app: AppEntry,
    vm: HomeViewModel,
    drag: DragController,
    list: Boolean,
    labels: Boolean,
    onPickUp: () -> Unit,
    onClose: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val itemBounds = remember { arrayOf(Rect.Zero) }
    var lifted by remember { mutableStateOf(false) }
    val gestures = Modifier
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
                        drag.startFromDrawer(app.key, startAt + travel)
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
            val scale = if (lifted) 1.06f else 1f
            scaleX = scale
            scaleY = scale
        }
    if (list) {
        Row(
            gestures.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(styledIcon(app), contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(14.dp))
            Text(app.label, color = LocalLabelColor.current, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Box(gestures.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            AppIcon(app = app, size = 52.dp * LocalDrawerIconScale.current, showLabel = labels)
        }
    }
}
