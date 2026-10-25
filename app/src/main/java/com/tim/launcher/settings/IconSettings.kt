package com.tim.launcher.settings

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Spacer
import com.tim.launcher.ui.SliderRow
import com.tim.launcher.ui.Tabs
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tim.launcher.AppEntry
import com.tim.launcher.Badges
import com.tim.launcher.IconPacks
import com.tim.launcher.IconRender
import com.tim.launcher.IconSlot
import com.tim.launcher.LocalIconSlot
import com.tim.launcher.Prefs
import com.tim.launcher.styledIcon
import com.tim.launcher.ui.BodyText
import com.tim.launcher.ui.Dropdown
import com.tim.launcher.ui.GlassPanel
import com.tim.launcher.ui.Q
import com.tim.launcher.ui.RowDivider
import com.tim.launcher.ui.SectionLabel
import com.tim.launcher.ui.Segmented
import com.tim.launcher.ui.SettingRow
import com.tim.launcher.ui.Stepper
import com.tim.launcher.ui.Toggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A few of your own apps (your dock's first), to preview icon styles on. */
private fun sampleApps(context: Context): List<AppEntry> {
    val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
    val density = context.resources.displayMetrics.densityDpi
    val all = launcherApps.getActivityList(null, Process.myUserHandle())
        .filter { it.applicationInfo.packageName != context.packageName }
    val dock = Prefs.get(context).getString(Prefs.L_DOCK_COVER, null)?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()
    val picked = (dock.mapNotNull { k -> all.firstOrNull { it.componentName.flattenToString() == k } } + all).distinct().take(5)
    return picked.mapNotNull { info ->
        runCatching {
            AppEntry(
                key = info.componentName.flattenToString(),
                label = info.label.toString(),
                icon = info.getIcon(density).toBitmap(144, 144).asImageBitmap(),
                component = info.componentName,
                category = info.applicationInfo.category,
            )
        }.getOrNull()
    }
}

/** Your apps drawn with the current icon style for [slot], over a wallpaper strip. */
@Composable
private fun IconPreview(slot: IconSlot) {
    val context = LocalContext.current
    val apps by produceState(emptyList<AppEntry>()) { value = withContext(Dispatchers.IO) { sampleApps(context) } }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .height(92.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3B2A7A), Color(0xFF244F9E), Color(0xFF14808A), Color(0xFF4E9F3D)))),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalIconSlot provides slot) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                apps.forEach { app -> Image(styledIcon(app), contentDescription = app.label, modifier = Modifier.size(52.dp)) }
            }
        }
    }
}

/**
 * Settings › Appearance › Icons. With "Match everywhere" on, one style for every icon. Off, tabs for the home
 * screen, dock and app drawer, each with its own pack, shape, style and background (the dock and drawer start out
 * like the home screen). Sizes are set for each place below.
 */
@Composable
fun IconsSection(ctx: SettingsContext) {
    val store = ctx.store
    val context = LocalContext.current
    val packs by produceState(emptyList<IconPacks.Pack>()) { value = withContext(Dispatchers.IO) { IconPacks.installed(context) } }
    var tab by remember { mutableStateOf(IconSlot.HOME) }
    var colorOpen by remember { mutableStateOf(false) }
    val match = store[S.iconsMatch]
    val slot = if (match) IconSlot.HOME else tab
    // The settings this tab edits: the plain ones for home (and for everything when matching), the place's own otherwise
    val place = when (slot) {
        IconSlot.HOME -> null
        IconSlot.DOCK -> S.iconsDock
        IconSlot.DRAWER -> S.iconsDrawer
    }
    fun <T> set(home: Setting<T>, own: Setting<T>?, value: T) = if (own == null) store.set(home, value = value) else store.set(own, value = value)
    val packSetting = when (slot) {
        IconSlot.HOME -> S.iconPackHome
        IconSlot.DOCK -> S.iconPackDock
        IconSlot.DRAWER -> S.iconPackDrawer
    }

    SectionLabel("Icons")
    GlassPanel {
        SettingRow("Match everywhere", "Home screen, dock and app drawer icons all look the same") {
            Toggle(match) { store.set(S.iconsMatch, value = it) }
        }
        if (!match) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
                Tabs(tab, IconSlot.entries, { slotName(it) }) { tab = it }
            }
        }
        IconPreview(slot)
        Spacer(Modifier.height(8.dp))
        RowDivider()
        val options = listOf("") + packs.map { it.pkg }
        fun label(pkg: String) = if (pkg.isEmpty()) "Apps' own icons" else packs.firstOrNull { it.pkg == pkg }?.label ?: pkg
        SettingRow("Icon pack") {
            Dropdown(store[packSetting], options, { label(it) }) {
                store.set(packSetting, value = it)
                IconRender.clear()
            }
        }
        if (packs.isEmpty()) {
            RowDivider()
            SettingRow("Get icon packs", "Any pack made for Nova, Lawnchair or similar launchers works. Opens the Play Store.", onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=icon%20pack&c=apps")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            })
        }
        RowDivider()
        SettingRow("Shape", "For icons a pack doesn't cover, and every app's own icon") {
            Dropdown(store.orHome(place?.shape, S.iconShape), IconShape.entries, { it.label }) { set(S.iconShape, place?.shape, it) }
        }
        RowDivider()
        SettingRow("Use the shape on every icon", "Pack icons too") {
            Toggle(store.orHome(place?.force, S.iconForceShape)) { set(S.iconForceShape, place?.force, it) }
        }
        RowDivider()
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Style", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
            Segmented(store.orHome(place?.style, S.iconStyle), IconStyle.entries, { if (it == IconStyle.MINIMAL) "Minimal (one color)" else it.label }) {
                set(S.iconStyle, place?.style, it)
            }
        }
        RowDivider()
        val background = store.orHome(place?.background, S.iconBackground)
        SettingRow("Background", "A plate behind each icon, in your shape") {
            Dropdown(background, IconBackground.entries, { it.label }) { set(S.iconBackground, place?.background, it) }
        }
        if (background == IconBackground.COLOR) {
            ColorEditor("Background color", store.orHome(place?.color, S.iconBgColor), colorOpen, { colorOpen = !colorOpen }) {
                set(S.iconBgColor, place?.color, it)
            }
        }
    }

    SectionLabel("Icon sizes")
    GlassPanel {
        val home = store[S.iconSize, ctx.editing]
        SliderRow(
            if (ctx.type.screens.size > 1) "Home screen (${ctx.editing.label.lowercase()})" else "Home screen",
            home, 0.7f..1.5f, "${(home * 100).roundToInt()}%",
        ) { store.set(S.iconSize, ctx.editing, (it * 20).roundToInt() / 20f) }
        val dock = store[S.dockIconSize]
        SliderRow("Dock", dock, 0.7f..1.5f, "${(dock * 100).roundToInt()}%") { store.set(S.dockIconSize, value = (it * 20).roundToInt() / 20f) }
        val drawer = store[S.drawerIconSize]
        SliderRow("App drawer", drawer, 0.7f..1.5f, "${(drawer * 100).roundToInt()}%") { store.set(S.drawerIconSize, value = (it * 20).roundToInt() / 20f) }
    }
}

private fun slotName(slot: IconSlot) = when (slot) {
    IconSlot.HOME -> "Home"
    IconSlot.DOCK -> "Dock"
    IconSlot.DRAWER -> "App drawer"
}

/** Settings › Appearance › Notification badges: on/off (with notification access), dot or count, color steps. */
@Composable
fun BadgesSection(ctx: SettingsContext) {
    val store = ctx.store
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Badges.isAllowed(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = Badges.isAllowed(context)
        onPauseOrDispose { }
    }
    SectionLabel("Notification badges")
    GlassPanel {
        SettingRow("Badges", "A dot or a count on apps with notifications") {
            Toggle(store[S.badgesOn]) { on ->
                store.set(S.badgesOn, value = on)
                if (on && !allowed) Badges.openAccessSettings(context)
            }
        }
        if (store[S.badgesOn]) {
            RowDivider()
            SettingRow(
                "Notification access",
                if (allowed) "Quartz counts notifications per app. It never reads, keeps or sends what they say."
                else "Needed to count notifications. Turn on Quartz badges on the next screen.",
                onClick = { Badges.openAccessSettings(context) },
            ) { Text(if (allowed) "On" else "Off", color = if (allowed) Q.TextDim else Q.Danger, fontSize = 14.sp) }
            RowDivider()
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Segmented(store[S.badgeStyle], BadgeStyle.entries, { it.label }) { store.set(S.badgeStyle, value = it) }
            }
            if (store[S.badgeStyle] == BadgeStyle.COUNTER) {
                RowDivider()
                SettingRow("First color change at") {
                    Stepper(store[S.badgeThreshold1], 2..98, format = { "$it+" }) {
                        store.set(S.badgeThreshold1, value = it)
                        if (store[S.badgeThreshold2] <= it) store.set(S.badgeThreshold2, value = it + 1)
                    }
                }
                RowDivider()
                SettingRow("Second color change at") {
                    Stepper(store[S.badgeThreshold2], (store[S.badgeThreshold1] + 1)..99, format = { "$it+" }) { store.set(S.badgeThreshold2, value = it) }
                }
                RowDivider()
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val t1 = store[S.badgeThreshold1]
                    val t2 = store[S.badgeThreshold2]
                    listOf(3, t1, t2).forEach { n -> BadgeSample(n, Badges.color(n, t1, t2)) }
                }
            }
        } else {
            RowDivider()
            BodyText(
                "Off: Quartz never asks for notification access. Counters cap at 99+ and change color at two steps you choose.",
                Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun BadgeSample(n: Int, color: Color) {
    Box(
        Modifier.clip(CircleShape).background(color).padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (n > 99) "99+" else "$n", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
