package com.tim.launcher.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.ui.BodyText
import com.tim.launcher.ui.Bullet
import com.tim.launcher.ui.DevicePreview
import com.tim.launcher.ui.DockSample
import com.tim.launcher.ui.Dropdown
import com.tim.launcher.ui.GlassPanel
import com.tim.launcher.ui.LinkState
import com.tim.launcher.ui.NavRow
import com.tim.launcher.ui.PanelColors
import com.tim.launcher.ui.Q
import com.tim.launcher.ui.RowDivider
import com.tim.launcher.ui.ScreenTitle
import com.tim.launcher.ui.SectionLabel
import com.tim.launcher.ui.Segmented
import com.tim.launcher.ui.SettingRow
import com.tim.launcher.ui.SliderRow
import com.tim.launcher.ui.Stepper
import com.tim.launcher.ui.SwatchRow
import com.tim.launcher.ui.Toggle
import com.tim.launcher.ui.previewLook
import kotlin.math.roundToInt

// ---- Shared pieces ----

@Composable
private fun ScreenScaffold(ctx: SettingsContext, showBack: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { ctx.back() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Q.Text)
                }
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), content = content)
        }
    }
}

/** The mini screens at the top of per-screen sections: tap one to choose which screen you're editing. */
@Composable
private fun ScreenPicker(ctx: SettingsContext) {
    if (ctx.type.screens.size < 2) return
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        DevicePreview(
            look = previewLook(ctx.store, ctx.type),
            height = 120.dp,
            selected = ctx.editing,
            onSelect = ctx.onEditing,
        )
        Text(
            "Editing the ${ctx.editing.label.lowercase()} · tap a screen to switch",
            color = Q.TextFaint,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun sharedWith(scope: Scope) = when (scope) {
    Scope.COVER, Scope.INNER -> "the other screen"
    else -> "other devices"
}

/** The link chip for a per-screen setting on the screen being edited. */
private fun SettingsContext.link(setting: Setting<*>): LinkState? {
    if (!setting.perScreen) return null
    val linked = store.isLinked(setting, editing)
    return LinkState(linked, sharedWith(editing)) {
        if (linked) store.unlink(setting, editing) else store.link(setting, editing)
    }
}

// ---- Main ----

@Composable
fun MainSettings(ctx: SettingsContext) {
    val store = ctx.store
    ScreenScaffold(ctx, showBack = true) {
        ScreenTitle("Settings", "${ctx.type.label} · everything stays on this device")

        // Screens diagram
        GlassPanel(Modifier.padding(top = 16.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DevicePreview(
                    look = previewLook(store, ctx.type),
                    height = 150.dp,
                    selected = ctx.editing,
                    onSelect = ctx.onEditing,
                )
                if (ctx.type.screens.size > 1) {
                    val split = S.perScreenSettings.count { s -> ctx.type.screens.any { !store.isLinked(s, it) } }
                    val synced = S.perScreenSettings.size - split
                    Text(
                        "$synced settings synced · $split set per screen",
                        color = Q.TextDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text("Tap a screen to choose which one you're editing", color = Q.TextFaint, fontSize = 12.sp)
                }
            }
        }

        SectionLabel("Layout")
        GlassPanel {
            NavRow("Home screen", "Grid, pages, labels", Icons.Rounded.GridView) { ctx.go(Route.HOME) }
            RowDivider()
            NavRow("Dock", "Position, style, icons", Icons.Rounded.Dock) { ctx.go(Route.DOCK) }
            RowDivider()
            NavRow("App drawer", "Opening, view, sorting", Icons.Rounded.Apps) { ctx.go(Route.DRAWER) }
        }
        SectionLabel("Look and behavior")
        GlassPanel {
            NavRow("Appearance", "Color, glass, icons, badges", Icons.Rounded.Palette) { ctx.go(Route.APPEARANCE) }
            RowDivider()
            NavRow("Gestures", "What each swipe and tap does", Icons.Rounded.Swipe) { ctx.go(Route.GESTURES) }
        }
        SectionLabel("Your data")
        GlassPanel {
            NavRow("Privacy & backup", "Permissions, export, import", Icons.Rounded.Shield) { ctx.go(Route.PRIVACY) }
            RowDivider()
            NavRow("Restart setup", "Walk through the setup questions again", Icons.Rounded.AutoAwesome) { ctx.go(Route.WIZARD) }
            RowDivider()
            NavRow("About", null, Icons.Rounded.Info) { ctx.go(Route.ABOUT) }
        }
    }
}

// ---- Home screen ----

@Composable
fun HomeSettings(ctx: SettingsContext) {
    val store = ctx.store
    val scope = ctx.editing
    val derived = scope == Scope.INNER && store[S.carryOver]
    ScreenScaffold(ctx) {
        ScreenTitle("Home screen")
        ScreenPicker(ctx)

        SectionLabel("Grid")
        GlassPanel {
            val cols = if (derived) store[S.gridCols, Scope.COVER] * 2 else store[S.gridCols, scope]
            val rows = if (derived) store[S.gridRows, Scope.COVER] else store[S.gridRows, scope]
            SettingRow(
                "Columns",
                if (derived) "Doubled from the cover screen" else null,
                link = if (derived) null else ctx.link(S.gridCols),
                enabled = !derived,
            ) { Stepper(cols, 3..if (derived) 16 else if (scope == Scope.TABLET || scope == Scope.INNER) 10 else 8, enabled = !derived) { store.set(S.gridCols, scope, it) } }
            RowDivider()
            SettingRow(
                "Rows",
                if (derived) "Same as the cover screen" else null,
                link = if (derived) null else ctx.link(S.gridRows),
                enabled = !derived,
            ) { Stepper(rows, 3..10, enabled = !derived) { store.set(S.gridRows, scope, it) } }
            if (scope == Scope.TABLET) {
                RowDivider()
                SettingRow("Held upright", "The grid flips to ${store[S.gridRows, scope]} × ${store[S.gridCols, scope]} and your apps keep their order", enabled = false)
            }
            if (scope == Scope.INNER && !store[S.carryOver]) {
                RowDivider()
                SettingRow("Unfolded", "Shows one page at a time, with your apps flowed into this grid", enabled = false)
            }
            RowDivider()
            SettingRow("Pages") { Stepper(store[S.pages], 1..5) { store.set(S.pages, value = it) } }
        }

        if (ctx.type == DeviceType.FOLDABLE) {
            SectionLabel("Folding")
            GlassPanel {
                SettingRow("Cover screen carries over", "Your cover layout becomes one half of the unfolded screen") {
                    Toggle(store[S.carryOver]) { store.set(S.carryOver, value = it) }
                }
                if (store[S.carryOver]) {
                    RowDivider()
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text("It lands on the", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                        Segmented(store[S.coverSide], CoverSide.entries, { it.label }) { store.set(S.coverSide, value = it) }
                    }
                }
            }
        }

        SectionLabel("Icons")
        GlassPanel {
            SettingRow("App names", link = ctx.link(S.labelsHome)) {
                Toggle(store[S.labelsHome, scope]) { store.set(S.labelsHome, scope, it) }
            }
            RowDivider()
            val size = store[S.iconSize, scope]
            SliderRow("Icon size", size, 0.7f..1.3f, "${(size * 100).roundToInt()}%", link = ctx.link(S.iconSize)) {
                store.set(S.iconSize, scope, (it * 20).roundToInt() / 20f)
            }
        }

        if (ctx.type != DeviceType.TABLET) {
            SectionLabel("Rotation")
            GlassPanel {
                SettingRow(
                    "Rotate the home screen",
                    if (ctx.type == DeviceType.FOLDABLE) "The cover screen stays upright when off. The unfolded screen always rotates (Android requires it) and reflows your apps."
                    else "Stays upright when off. When on, your grid flips sideways in landscape.",
                ) { Toggle(store[S.rotateHome]) { store.set(S.rotateHome, value = it) } }
            }
        }

        SectionLabel("Folders")
        GlassPanel {
            SettingRow("Make a folder", "Drag an app onto another and hold for a moment", enabled = false)
            RowDivider()
            SettingRow("Folder icon") {
                Dropdown(store[S.folderIcon], FolderIcon.entries, { it.label }) { store.set(S.folderIcon, value = it) }
            }
            RowDivider()
            SettingRow("Opens as") {
                Dropdown(store[S.folderOpen], FolderOpen.entries, { it.label }) { store.set(S.folderOpen, value = it) }
            }
            when (store[S.folderOpen]) {
                FolderOpen.ROW -> {
                    RowDivider()
                    SettingRow("Row direction") {
                        Dropdown(store[S.folderRow], RowDirection.entries, { it.label }) { store.set(S.folderRow, value = it) }
                    }
                }
                else -> {
                    RowDivider()
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text("Show apps as", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                        Segmented(store[S.folderView], FolderView.entries, { it.label }) { store.set(S.folderView, value = it) }
                    }
                    if (store[S.folderView] == FolderView.GRID) {
                        RowDivider()
                        SettingRow("Grid size", "Columns in an open folder; sets how wide it is") {
                            Stepper(store[S.folderCols], 2..6) { store.set(S.folderCols, value = it) }
                        }
                    }
                }
            }
            if (ctx.type == DeviceType.FOLDABLE) {
                RowDivider()
                SettingRow("Unfolded", "Folders open within the half of the screen they're on", enabled = false)
            }
        }
    }
}

// ---- Dock ----

@Composable
fun DockSettings(ctx: SettingsContext) {
    val store = ctx.store
    val scope = ctx.editing
    val position = store[S.dockPosition, scope]
    ScreenScaffold(ctx) {
        ScreenTitle("Dock")
        ScreenPicker(ctx)

        SectionLabel("Placement")
        GlassPanel {
            SettingRow("Position", link = ctx.link(S.dockPosition)) {
                Dropdown(position, DockPosition.entries, { it.label }) { store.set(S.dockPosition, scope, it) }
            }
            if (position != DockPosition.NONE) {
                RowDivider()
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("Length", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                    Segmented(store[S.dockLength, scope], DockLength.entries, { if (it == DockLength.FULL) "Full" else "Fit to icons" }) {
                        store.set(S.dockLength, scope, it)
                    }
                }
                val vertical = position == DockPosition.LEFT || position == DockPosition.RIGHT
                if (store[S.dockLength, scope] == DockLength.FULL) {
                    RowDivider()
                    SettingRow("Icons sit at the", link = ctx.link(S.dockIconAlign)) {
                        Dropdown(store[S.dockIconAlign, scope], DockAlign.entries, {
                            when (it) {
                                DockAlign.START -> if (vertical) "Top" else "Left"
                                DockAlign.CENTER -> if (vertical) "Middle" else "Center"
                                DockAlign.END -> if (vertical) "Bottom" else "Right"
                            }
                        }) { store.set(S.dockIconAlign, scope, it) }
                    }
                }
                if (vertical && store[S.dockLength, scope] == DockLength.FIT) {
                    RowDivider()
                    SettingRow("Sits at the", "Bottom keeps it near your thumb", link = ctx.link(S.dockAlign)) {
                        Dropdown(store[S.dockAlign, scope], DockAlign.entries, {
                            when (it) { DockAlign.START -> "Top"; DockAlign.CENTER -> "Middle"; DockAlign.END -> "Bottom" }
                        }) { store.set(S.dockAlign, scope, it) }
                    }
                }
                RowDivider()
                val (folded, unfolded) = store.dockCounts(ctx.type)
                val foldable = ctx.type == DeviceType.FOLDABLE
                SettingRow(if (foldable) "Icons when folded" else "Icons", if (folded == 0) "The dock scrolls when it's full" else null) {
                    Dropdown(folded, listOf(4, 5, 6, 7, 8, 0), { if (it == 0) "Unlimited" else "$it" }) {
                        store.set(S.dockIcons, ctx.type.primary, it)
                    }
                }
                if (foldable) {
                    RowDivider()
                    SettingRow(
                        "Icons when unfolded",
                        if (folded == 0) "Unlimited, like folded" else "Extra icons go after your folded ones",
                        enabled = folded != 0,
                    ) {
                        Dropdown(unfolded, (folded..16).toList() + 0, { if (it == 0) "Unlimited" else "$it" }, enabled = folded != 0) {
                            store.setAt(S.dockIcons, Scope.INNER, it)
                        }
                    }
                }
            }
            if (ctx.type == DeviceType.FOLDABLE) {
                RowDivider()
                val same = store.isLinked(S.dockPosition, Scope.COVER) && store.isLinked(S.dockPosition, Scope.INNER)
                SettingRow("Same side folded and unfolded", "On the outer edge, never at the hinge") {
                    Toggle(same) { on ->
                        if (on) store.linkAll(S.dockPosition, ctx.type.screens) else store.unlink(S.dockPosition, Scope.INNER)
                    }
                }
            }
            RowDivider()
            SettingRow("Swap handle", "A small handle that flips the dock to the other side. Stays where you leave it.") {
                Toggle(store[S.swapHandle]) { store.set(S.swapHandle, value = it) }
            }
        }

        SectionLabel("Style")
        GlassPanel {
            DockSample(store, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Background", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                Segmented(store[S.dockShape], DockShape.entries, { if (it == DockShape.NONE) "None" else it.label }) {
                    store.set(S.dockShape, value = it)
                }
            }
            if (store[S.dockShape] == DockShape.ROUNDED) {
                RowDivider()
                val r = store[S.dockRadius]
                SliderRow("Roundness", r.toFloat(), 4f..40f, "$r") { store.set(S.dockRadius, value = it.roundToInt()) }
            }
            RowDivider()
            SettingRow("App names") { Toggle(store[S.labelsDock]) { store.set(S.labelsDock, value = it) } }
            RowDivider()
            SettingRow("Color and glass", "Shared with the app drawer", onClick = { ctx.go(Route.APPEARANCE) })
        }
    }
}

// ---- App drawer ----

@Composable
fun DrawerSettings(ctx: SettingsContext) {
    val store = ctx.store
    val scope = ctx.editing
    val open = store[S.drawerOpen]
    var conflict by remember { mutableStateOf<Pair<GestureSlot, GestureAction>?>(null) }
    val swipeSlots = listOf(GestureSlot.SWIPE_UP, GestureSlot.SWIPE_DOWN, GestureSlot.SWIPE_PAST_LEFT, GestureSlot.SWIPE_PAST_RIGHT)
    val currentSlot = swipeSlots.firstOrNull { store[S.gesture(it)] == GestureAction.APP_DRAWER }

    fun moveDrawerTo(slot: GestureSlot) {
        swipeSlots.forEach { if (it != slot && store[S.gesture(it)] == GestureAction.APP_DRAWER) store.set(S.gesture(it), value = GestureAction.NONE) }
        store.set(S.gesture(slot), value = GestureAction.APP_DRAWER)
    }

    ScreenScaffold(ctx) {
        ScreenTitle("App drawer")
        ScreenPicker(ctx)

        SectionLabel("Opening")
        GlassPanel {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Open with", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                Segmented(open, DrawerOpen.entries, { if (it == DrawerOpen.BOTH) "Both" else it.label }) {
                    store.set(S.drawerOpen, value = it)
                    if (it != DrawerOpen.BUTTON && currentSlot == null) moveDrawerTo(GestureSlot.SWIPE_UP)
                }
            }
            if (open != DrawerOpen.BUTTON) {
                RowDivider()
                SettingRow("Swipe", "Left and right open it when you swipe past your first or last page") {
                    Dropdown(currentSlot ?: GestureSlot.SWIPE_UP, swipeSlots, {
                        when (it) {
                            GestureSlot.SWIPE_UP -> "Up"
                            GestureSlot.SWIPE_DOWN -> "Down"
                            GestureSlot.SWIPE_PAST_LEFT -> "Left"
                            else -> "Right"
                        }
                    }) { slot ->
                        val existing = store[S.gesture(slot)]
                        if (existing != GestureAction.NONE && existing != GestureAction.APP_DRAWER) conflict = slot to existing
                        else moveDrawerTo(slot)
                    }
                }
            }
            if (open != DrawerOpen.SWIPE) {
                RowDivider()
                val dock = store[S.dockPosition, scope]
                val vertical = dock == DockPosition.LEFT || dock == DockPosition.RIGHT
                SettingRow(
                    "Button",
                    if (dock == DockPosition.NONE) "With no dock, the button sits on the home screen" else null,
                ) {
                    Dropdown(store[S.drawerButtonEnd], ButtonEnd.entries, {
                        when {
                            vertical && it == ButtonEnd.START -> "Top of dock"
                            vertical -> "Bottom of dock"
                            it == ButtonEnd.START -> "Left of dock"
                            else -> "Right of dock"
                        }
                    }) { store.set(S.drawerButtonEnd, value = it) }
                }
                RowDivider()
                SettingRow("Button icon") {
                    Dropdown(store[S.drawerButtonIcon], ButtonIcon.entries, { it.label }) { store.set(S.drawerButtonIcon, value = it) }
                }
            }
        }

        SectionLabel("Apps")
        GlassPanel {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Show apps as", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                Segmented(store[S.drawerView], DrawerView.entries, { it.label }) { store.set(S.drawerView, value = it) }
            }
            if (store[S.drawerView] == DrawerView.ICONS) {
                RowDivider()
                SettingRow("Scroll") {
                    Dropdown(store[S.drawerScroll], DrawerScroll.entries, { it.label }) { store.set(S.drawerScroll, value = it) }
                }
                RowDivider()
                val doubled = scope == Scope.INNER && store.isLinked(S.drawerCols, Scope.INNER)
                val cols = store[S.drawerCols, scope]
                SettingRow("Columns", if (doubled) "Doubled when unfolded (${cols * 2})" else null, link = ctx.link(S.drawerCols)) {
                    Stepper(cols, 3..8) { store.set(S.drawerCols, scope, it) }
                }
            }
            RowDivider()
            SettingRow("Style", link = ctx.link(S.drawerStyle)) {
                Dropdown(store[S.drawerStyle, scope], DrawerStyle.entries, { it.label }) { store.set(S.drawerStyle, scope, it) }
            }
            RowDivider()
            SettingRow("Sort") {
                Dropdown(store[S.drawerSort], DrawerSort.entries, { it.label }) { store.set(S.drawerSort, value = it) }
            }
            RowDivider()
            SettingRow("App names") { Toggle(store[S.labelsDrawer]) { store.set(S.labelsDrawer, value = it) } }
            RowDivider()
            SettingRow("Custom tabs", "Your own groups, with on-device suggestions (phase 6)", enabled = false)
        }
    }

    conflict?.let { (slot, action) ->
        AlertDialog(
            onDismissRequest = { conflict = null },
            title = { Text("${slot.label} is taken") },
            text = { Text("${slot.label} currently does “${action.label.lowercase()}”. Use it to open apps instead? You can give that action another gesture in Gestures.") },
            confirmButton = {
                TextButton(onClick = {
                    moveDrawerTo(slot)
                    conflict = null
                }) { Text("Use for apps") }
            },
            dismissButton = { TextButton(onClick = { conflict = null }) { Text("Keep it") } },
        )
    }
}

// ---- Appearance ----

@Composable
fun AppearanceSettings(ctx: SettingsContext) {
    val store = ctx.store
    ScreenScaffold(ctx) {
        ScreenTitle("Appearance", "Dock and app drawer share one glass style")
        Box(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            DevicePreview(previewLook(store, ctx.type), height = 130.dp)
        }

        SectionLabel("Glass")
        GlassPanel {
            DockSample(store, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
            Text("Color", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(start = 20.dp, top = 14.dp))
            SwatchRow(store[S.panelColor], PanelColors) { store.set(S.panelColor, value = it) }
            RowDivider()
            val opacity = store[S.panelOpacity]
            SliderRow("See-through", 1f - opacity, 0f..0.95f, "${((1f - opacity) * 100).roundToInt()}%") {
                store.set(S.panelOpacity, value = 1f - it)
            }
            RowDivider()
            val blur = store[S.panelBlur]
            SliderRow("Blur", blur, 0f..1f, if (blur < 0.05f) "Off" else "${(blur * 100).roundToInt()}%") {
                store.set(S.panelBlur, value = it)
            }
        }

        SectionLabel("Icons")
        GlassPanel {
            SettingRow("Match everywhere", "Dock, home screen and app drawer use the same style") {
                Toggle(store[S.iconsMatch]) { store.set(S.iconsMatch, value = it) }
            }
            RowDivider()
            SettingRow("Icon packs", "Up to three at once: home, dock, drawer (phase 7)", enabled = false)
            RowDivider()
            SettingRow("Shape", "For icons a pack doesn't cover") {
                Dropdown(store[S.iconShape], IconShape.entries, { it.label }) { store.set(S.iconShape, value = it) }
            }
            RowDivider()
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Style", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                Segmented(store[S.iconStyle], IconStyle.entries, { it.label }) { store.set(S.iconStyle, value = it) }
            }
            RowDivider()
            SettingRow("Background") {
                Dropdown(store[S.iconBackground], IconBackground.entries, { it.label }) { store.set(S.iconBackground, value = it) }
            }
        }

        SectionLabel("Notification badges")
        GlassPanel {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Segmented(store[S.badgeStyle], BadgeStyle.entries, { it.label }) { store.set(S.badgeStyle, value = it) }
            }
            RowDivider()
            SettingRow("First color change at") {
                Stepper(store[S.badgeThreshold1], 2..98, format = { "$it+" }) { store.set(S.badgeThreshold1, value = it) }
            }
            RowDivider()
            SettingRow("Second color change at") {
                Stepper(store[S.badgeThreshold2], 3..99, format = { "$it+" }) { store.set(S.badgeThreshold2, value = it) }
            }
            RowDivider()
            SettingRow("Badges need notification access", "You'll be asked when badges arrive (phase 7)", enabled = false)
        }

        SectionLabel("Clock")
        GlassPanel {
            SettingRow("24-hour time") { Toggle(store[S.clock24h]) { store.set(S.clock24h, value = it) } }
        }

        SectionLabel("Coming later")
        GlassPanel {
            SettingRow("Wallpaper", "Gradients, patterns and fold-aware images (phase 4)", enabled = false)
        }
    }
}

// ---- Gestures ----

@Composable
fun GestureSettings(ctx: SettingsContext) {
    val store = ctx.store
    ScreenScaffold(ctx) {
        ScreenTitle("Gestures", "Each gesture does one thing. Changing one here never changes another.")
        SectionLabel("On the home screen")
        GlassPanel {
            GestureSlot.entries.forEachIndexed { i, slot ->
                if (i > 0) RowDivider()
                val action = store[S.gesture(slot)]
                SettingRow(
                    slot.label,
                    if (action == GestureAction.LOCK) {
                        if (com.tim.launcher.LockService.isOn) "Quartz Lock is on" else "Needs Quartz Lock turned on in Accessibility settings; you'll be asked the first time"
                    } else null,
                ) {
                    Dropdown(action, GestureAction.entries, { it.label }) { store.set(S.gesture(slot), value = it) }
                }
            }
        }
        BodyText(
            "Swiping left and right moves between pages, so those gestures only trigger past your first or last page.",
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

// ---- Privacy & backup ----

@Composable
fun PrivacySettings(ctx: SettingsContext) {
    val context = LocalContext.current
    val store = ctx.store
    var confirmReset by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(Profile.export(context).toByteArray()) }
        }.onSuccess { toast(context, "Settings exported") }.onFailure { toast(context, "Export failed: ${it.message}") }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Empty file")
            Profile.import(context, text)
        }.onSuccess { toast(context, "Imported $it settings") }.onFailure { toast(context, "Import failed: ${it.message}") }
    }

    ScreenScaffold(ctx) {
        ScreenTitle("Privacy & backup")
        GlassPanel(Modifier.padding(top = 16.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Quartz doesn't track you", color = Q.Text, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                Bullet("No ads, no analytics, no accounts.")
                Bullet("Your layout and settings never leave this device unless you export them.")
                Bullet("The internet is only used for things you choose, like weather or importing an icon pack.")
                Bullet("If Quartz crashes, the log stays here. You decide whether to share it.")
            }
        }

        SectionLabel("Permissions")
        GlassPanel {
            val isHome = isDefaultHome(context)
            SettingRow(
                "Default home app",
                if (isHome) "Quartz is your home screen" else "Quartz isn't your home screen yet",
                onClick = { openHomeSettings(context) },
            ) { StatusText(if (isHome) "On" else "Off") }
            RowDivider()
            SettingRow("Internet", "Weather and things you import only") { StatusText("Allowed") }
            RowDivider()
            SettingRow("Notification access", "For badges. Not used yet") { StatusText("Not used") }
            RowDivider()
            SettingRow("Location", "Weather can use a typed city instead") { StatusText("Not asked") }
            RowDivider()
            SettingRow(
                "Accessibility (Quartz Lock)",
                "Only used to lock the screen from a gesture. It can't see your screen or what you type.",
                onClick = { com.tim.launcher.LockService.openSettings(context) },
            ) { StatusText(if (com.tim.launcher.LockService.isOn) "On" else "Off") }
        }

        SectionLabel("Backup")
        GlassPanel {
            SettingRow("Export profile", "Your settings and home screen layout, as a file for another device", onClick = {
                exporter.launch("quartz-settings.json")
            })
            RowDivider()
            SettingRow("Import profile", "Load settings and layout from another device", onClick = {
                importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            })
            RowDivider()
            SettingRow("Reset all settings", "Back to the defaults. Your home screen layout isn't touched.", onClick = { confirmReset = true })
        }
        BodyText("Importing a profile replaces this device's layout. Apps it doesn't have leave their spots empty.", Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all settings?") },
            text = { Text("Every setting goes back to its default. To run setup again, long-press the home screen and choose Run setup again.") },
            confirmButton = {
                TextButton(onClick = {
                    store.resetAll()
                    confirmReset = false
                    toast(context, "Settings reset")
                }) { Text("Reset", color = Q.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text, color = Q.TextDim, fontSize = 14.sp)
}

// ---- About ----

@Composable
fun AboutSettings(ctx: SettingsContext) {
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    ScreenScaffold(ctx) {
        ScreenTitle("About", "Quartz Launcher $version")
        GlassPanel(Modifier.padding(top = 16.dp)) {
            Column(Modifier.padding(20.dp)) {
                BodyText("Part of the Quartz family, alongside Quartz Gallery, Notes and Editor. Built to feel the same on every phone, folding phone and tablet you own.")
            }
        }
        SectionLabel("Support")
        GlassPanel {
            SettingRow("Support Quartz on Ko-fi", "Link coming before release", enabled = false)
            RowDivider()
            SettingRow("Charities we support", "Coming before release", enabled = false)
        }
    }
}

// ---- Helpers ----

fun isDefaultHome(context: Context): Boolean {
    val roles = context.getSystemService(RoleManager::class.java) ?: return false
    return runCatching { roles.isRoleAvailable(RoleManager.ROLE_HOME) && roles.isRoleHeld(RoleManager.ROLE_HOME) }.getOrDefault(false)
}

fun openHomeSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { toast(context, "Couldn't open home settings") }
}

fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
