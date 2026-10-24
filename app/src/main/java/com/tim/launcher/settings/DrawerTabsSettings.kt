package com.tim.launcher.settings

import android.Manifest
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
private fun loadApps(context: Context): List<AppEntry> {
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
    val apps by produceState<List<AppEntry>?>(null) { value = withContext(Dispatchers.IO) { loadApps(context) } }
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
            DrawerTabs.tabs.forEach { t ->
                RowDivider()
                NavRow(t.name, "${t.apps.size} apps", FolderBadges.firstOrNull { it.first == t.icon }?.second) {
                    EditingTab.id = t.id
                    ctx.go(Route.DRAWER_TAB)
                }
            }
            RowDivider()
            NavRow("New tab", "Name it, pick an icon, then add apps", Icons.Rounded.Add) { naming = true }
        }
        RowDivider()
        BodyText(
            "Tabs sit down the side of the drawer: names on wide screens, icons on narrow ones. They stay on the side away from the dock.",
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }

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
