package com.tim.launcher.settings

import android.Manifest
import android.content.ComponentName
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Star
import com.tim.launcher.StarterApps
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.tim.launcher.AppCategories
import com.tim.launcher.AppEntry
import com.tim.launcher.DrawerTab
import com.tim.launcher.DrawerTabs
import com.tim.launcher.FolderBadges
import com.tim.launcher.SearchSources
import com.tim.launcher.ui.BodyText
import com.tim.launcher.ui.GhostButton
import com.tim.launcher.ui.GlassPanel
import com.tim.launcher.ui.NavRow
import com.tim.launcher.ui.Q
import com.tim.launcher.ui.RowDivider
import com.tim.launcher.ui.ScreenTitle
import com.tim.launcher.ui.SectionLabel
import com.tim.launcher.ui.Segmented
import com.tim.launcher.ui.SettingRow
import com.tim.launcher.ui.Toggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which tab the tab editor is showing (set just before opening it). */
object EditingTab {
    var id: String? = null
}

/** Your apps, for picking what goes in a tab. Read on the phone from Android's app list. */
internal fun loadAppEntries(context: Context): List<AppEntry> {
    val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
    val density = context.resources.displayMetrics.densityDpi
    return launcherApps.getActivityList(null, Process.myUserHandle())
        .filter { it.applicationInfo.packageName != context.packageName }
        .mapNotNull { info ->
            runCatching {
                AppEntry(
                    key = info.componentName.flattenToString(),
                    label = info.label.toString(),
                    icon = info.getIcon(density).toBitmap(96, 96).asImageBitmap(),
                    component = info.componentName,
                    category = info.applicationInfo.category,
                    installed = info.firstInstallTime,
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
private fun rememberApps(): List<AppEntry>? {
    val context = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) { value = withContext(Dispatchers.IO) { loadAppEntries(context) } }
    return apps
}

// ---- Drawer settings: tabs ----

@Composable
fun DrawerTabsSection(ctx: SettingsContext) {
    val store = ctx.store
    val context = LocalContext.current
    LaunchedEffect(Unit) { DrawerTabs.load(context) }
    var naming by remember { mutableStateOf(false) }

    SectionLabel("Tabs")
    GlassPanel {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Segmented(store[S.drawerTabMode], TabMode.entries, { it.label }) { store.set(S.drawerTabMode, value = it) }
            Text(
                when (store[S.drawerTabMode]) {
                    TabMode.NONE -> "One list of all your apps."
                    TabMode.CUSTOM -> "Tabs you make, with apps you choose. Quartz suggests apps from what's on this phone; you approve each one."
                    TabMode.AUTO -> "Tabs from the categories apps give themselves on Android: Games, Social, Music and so on."
                },
                color = Q.TextDim,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (store[S.drawerTabMode] == TabMode.CUSTOM) {
            val list = DrawerTabs.tabs.toList()
            list.forEachIndexed { i, t ->
                RowDivider()
                TabListRow(
                    t,
                    first = i == 0,
                    last = i == list.lastIndex,
                    onMove = { DrawerTabs.move(context, t.id, it) },
                ) {
                    EditingTab.id = t.id
                    ctx.go(Route.DRAWER_TAB)
                }
            }
            RowDivider()
            NavRow("New tab", "Name it, pick an icon, then add apps", Icons.Rounded.Add) { naming = true }
        }
        RowDivider()
        BodyText(
            "Tabs sit down the side of the drawer: names on wide screens, icons on narrow ones. They stay on the side away from " +
                "the dock." + if (store[S.drawerTabMode] == TabMode.CUSTOM) " Use the arrows to change their order; All apps stays first." else "",
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }

    if (store[S.drawerTabMode] != TabMode.NONE) DrawerShortcutsSection()

    if (naming) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("New tab") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, singleLine = true, placeholder = { Text("Games, Work, Quartz…") })
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    naming = false
                    val cat = AppCategories.forName(name)
                    val icon = when (cat) {
                        android.content.pm.ApplicationInfo.CATEGORY_GAME -> "games"
                        android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "music"
                        android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "video"
                        android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> "photo"
                        android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "social"
                        android.content.pm.ApplicationInfo.CATEGORY_NEWS -> "news"
                        android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "travel"
                        android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "work"
                        else -> "star"
                    }
                    EditingTab.id = DrawerTabs.add(context, name, icon)
                    ctx.go(Route.DRAWER_TAB)
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
}

/** One of your tabs in the list: tap to edit it, arrows to move it up or down. */
@Composable
private fun TabListRow(tab: DrawerTab, first: Boolean, last: Boolean, onMove: (Int) -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Q.AccentSoft), contentAlignment = Alignment.Center) {
            Icon(FolderBadges.firstOrNull { it.first == tab.icon }?.second ?: Icons.Rounded.Star, contentDescription = null, tint = Q.Text, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(tab.name, color = Q.Text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${tab.apps.size} apps", color = Q.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        MoveButtons(first, last, onMove)
    }
}

/** Up and down arrows; the one that can't move further is faded out. */
@Composable
private fun MoveButtons(first: Boolean, last: Boolean, onMove: (Int) -> Unit) {
    listOf(Triple(Icons.Rounded.KeyboardArrowUp, "Move up", -1), Triple(Icons.Rounded.KeyboardArrowDown, "Move down", 1)).forEach { (icon, label, by) ->
        val can = if (by < 0) !first else !last
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(if (can) Modifier.clickable { onMove(by) } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (can) Q.Text else Q.TextFaint.copy(alpha = 0.35f), modifier = Modifier.size(24.dp))
        }
    }
}

/** Quartz Settings, as a shortcut you can pin (it isn't in Android's app list). */
private fun quartzSettingsEntry(context: Context): AppEntry? = runCatching {
    AppEntry(
        key = StarterApps.QUARTZ_SETTINGS,
        label = "Quartz Settings",
        icon = context.packageManager.getApplicationIcon(context.packageName).toBitmap(96, 96).asImageBitmap(),
        component = ComponentName(context, SettingsActivity::class.java),
    )
}.getOrNull()

// ---- Drawer settings: shortcuts under the tabs ----

@Composable
private fun DrawerShortcutsSection() {
    val context = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { listOfNotNull(quartzSettingsEntry(context)) + loadAppEntries(context) }
    }
    var picking by remember { mutableStateOf(false) }

    SectionLabel("Shortcuts")
    GlassPanel {
        BodyText(
            "Apps pinned under the tabs, one tap away whichever tab is showing. Handy for Settings, a calculator, or " +
                "anything you open all the time.",
            Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
        )
        val byKey = apps?.associateBy { it.key }.orEmpty()
        val pinned = DrawerTabs.shortcuts.toList()
        pinned.forEachIndexed { i, key ->
            val app = byKey[key]
            RowDivider()
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (app != null) Image(app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                else Spacer(Modifier.size(36.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    app?.label ?: if (apps == null) "…" else "App not installed",
                    color = if (app != null) Q.Text else Q.TextDim,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                MoveButtons(first = i == 0, last = i == pinned.lastIndex) { DrawerTabs.moveShortcut(context, key, it) }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { DrawerTabs.removeShortcut(context, key) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = Q.Text, modifier = Modifier.size(20.dp)) }
            }
        }
        RowDivider()
        NavRow("Add a shortcut", "Pick an app to pin under the tabs", Icons.Rounded.Add) { picking = true }
    }

    if (picking) {
        var filter by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Add a shortcut") },
            text = {
                Column {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        singleLine = true,
                        placeholder = { Text("Find an app") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val list = apps
                    if (list == null) {
                        Text("Reading your apps…", modifier = Modifier.padding(top = 16.dp))
                    } else {
                        val choices = list.filter {
                            it.key !in DrawerTabs.shortcuts && (filter.isBlank() || it.label.contains(filter.trim(), ignoreCase = true))
                        }
                        LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = 380.dp)) {
                            items(choices, key = { it.key }) { app ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            DrawerTabs.addShortcut(context, app.key)
                                            picking = false
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Image(app.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(app.label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}

// ---- Drawer settings: search ----

@Composable
fun DrawerSearchSection(ctx: SettingsContext) {
    val store = ctx.store
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        store.set(S.searchContacts, value = granted)
    }
    SectionLabel("Search")
    GlassPanel {
        SettingRow("Apps", "Always searched") { Text("On", color = Q.TextDim, fontSize = 14.sp) }
        RowDivider()
        SettingRow("Android settings", "Wi‑Fi, Bluetooth, battery and other settings pages") {
            Toggle(store[S.searchSettings]) { store.set(S.searchSettings, value = it) }
        }
        RowDivider()
        SettingRow("Contacts", "Read from this phone only, when you search. Android asks first.") {
            Toggle(store[S.searchContacts] && SearchSources.canReadContacts(context)) { on ->
                if (!on) store.set(S.searchContacts, value = false)
                else if (SearchSources.canReadContacts(context)) store.set(S.searchContacts, value = true)
                else ask.launch(Manifest.permission.READ_CONTACTS)
            }
        }
        RowDivider()
        SettingRow("Files", "Not offered: Android would have to let Quartz see all your files", enabled = false)
    }
}

// ---- Tab editor ----

@Composable
fun TabEditor(ctx: SettingsContext) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { DrawerTabs.load(context) }
    val tab = DrawerTabs.find(EditingTab.id)
    val apps = rememberApps()
    var filter by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(ctx) {
        if (tab == null) {
            ScreenTitle("Tab")
            BodyText("This tab was removed.", Modifier.padding(20.dp))
            return@ScreenScaffold
        }
        fun save(t: DrawerTab) = DrawerTabs.update(context, t)

        ScreenTitle(tab.name, "${tab.apps.size} apps")

        SectionLabel("Name and icon")
        GlassPanel {
            Column(Modifier.padding(20.dp)) {
                var name by remember(tab.id) { mutableStateOf(tab.name) }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(24)
                        if (name.isNotBlank()) save(tab.copy(name = name))
                    },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FolderBadges.forEach { (key, icon) ->
                        val on = key == tab.icon
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (on) Q.Accent else Q.Glass)
                                .border(1.dp, Q.Edge, CircleShape)
                                .clickable { save(tab.copy(icon = key)) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, contentDescription = key, tint = Color.White, modifier = Modifier.size(22.dp)) }
                    }
                }
            }
        }

        if (apps == null) {
            BodyText("Reading your apps…", Modifier.padding(20.dp))
            return@ScreenScaffold
        }
        val byKey = apps.associateBy { it.key }
        val members = tab.apps.mapNotNull { byKey[it] }
        val suggestions = AppCategories.suggest(tab.name, apps, tab.apps.toSet())

        if (suggestions.isNotEmpty()) {
            SectionLabel("Suggested")
            GlassPanel {
                BodyText(
                    "From the apps on this phone that look like they belong. Add the ones you want.",
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
                )
                suggestions.forEach { app ->
                    AppRow(app, actionAdd = true) { save(tab.copy(apps = tab.apps + app.key)) }
                }
                RowDivider()
                SettingRow("Add all ${suggestions.size}", onClick = { save(tab.copy(apps = tab.apps + suggestions.map { it.key })) })
            }
        }

        SectionLabel("In this tab")
        GlassPanel {
            if (members.isEmpty()) BodyText("No apps yet.", Modifier.padding(20.dp))
            members.forEach { app ->
                AppRow(app, actionAdd = false) { save(tab.copy(apps = tab.apps - app.key)) }
            }
        }

        SectionLabel("Add apps")
        GlassPanel {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                singleLine = true,
                placeholder = { Text("Find an app") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            val others = apps.filter { it.key !in tab.apps && (filter.isBlank() || it.label.contains(filter.trim(), ignoreCase = true)) }
            others.forEach { app ->
                AppRow(app, actionAdd = true) { save(tab.copy(apps = tab.apps + app.key)) }
            }
        }

        SectionLabel("This tab")
        GlassPanel {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Move up") { DrawerTabs.move(context, tab.id, -1) }
                GhostButton("Move down") { DrawerTabs.move(context, tab.id, 1) }
            }
            RowDivider()
            SettingRow("Delete tab", "The apps stay installed and in All apps", onClick = { confirmDelete = true })
        }
    }

    if (confirmDelete && tab != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${tab.name}”?") },
            text = { Text("Only the tab goes. Its apps stay installed and in All apps.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    DrawerTabs.delete(context, tab.id)
                    ctx.back()
                }) { Text("Delete", color = Q.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AppRow(app: AppEntry, actionAdd: Boolean, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onAction).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(14.dp))
        Text(app.label, color = Q.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(if (actionAdd) Q.AccentSoft else Q.Glass),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (actionAdd) Icons.Rounded.Add else Icons.Rounded.Close,
                contentDescription = if (actionAdd) "Add" else "Remove",
                tint = Q.Text,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
