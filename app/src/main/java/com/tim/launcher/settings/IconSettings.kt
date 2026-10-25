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

/** Settings › Appearance › Icons: packs (one, or one each for home, dock and drawer), shape, style, background. */
@Composable
fun IconsSection(ctx: SettingsContext) {
    val store = ctx.store
    val context = LocalContext.current
    val packs by produceState(emptyList<IconPacks.Pack>()) { value = withContext(Dispatchers.IO) { IconPacks.installed(context) } }
    var previewSlot by remember { mutableStateOf(IconSlot.HOME) }
    var colorOpen by remember { mutableStateOf(false) }
    val match = store[S.iconsMatch]

    SectionLabel("Icons")
    GlassPanel {
        IconPreview(if (match) IconSlot.HOME else previewSlot)
        if (!match) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Segmented(previewSlot, IconSlot.entries, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { previewSlot = it }
            }
        }
        SettingRow("Match everywhere", "Home screen, dock and app drawer use the same icon pack") {
            Toggle(match) { store.set(S.iconsMatch, value = it) }
        }
        RowDivider()
        val options = listOf("") + packs.map { it.pkg }
        fun label(pkg: String) = if (pkg.isEmpty()) "Apps' own icons" else packs.firstOrNull { it.pkg == pkg }?.label ?: pkg
        if (match) {
            SettingRow("Icon pack") { Dropdown(store[S.iconPackHome], options, { label(it) }) { store.set(S.iconPackHome, value = it); IconRender.clear() } }
        } else {
            SettingRow("Home screen icons") { Dropdown(store[S.iconPackHome], options, { label(it) }) { store.set(S.iconPackHome, value = it); IconRender.clear() } }
            RowDivider()
            SettingRow("Dock icons") { Dropdown(store[S.iconPackDock], options, { label(it) }) { store.set(S.iconPackDock, value = it); IconRender.clear() } }
            RowDivider()
            SettingRow("App drawer icons") { Dropdown(store[S.iconPackDrawer], options, { label(it) }) { store.set(S.iconPackDrawer, value = it); IconRender.clear() } }
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
            Dropdown(store[S.iconShape], IconShape.entries, { it.label }) { store.set(S.iconShape, value = it) }
        }
        RowDivider()
        SettingRow("Use the shape on every icon", "Pack icons too") {
            Toggle(store[S.iconForceShape]) { store.set(S.iconForceShape, value = it) }
        }
        RowDivider()
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Style", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
            Segmented(store[S.iconStyle], IconStyle.entries, { if (it == IconStyle.MINIMAL) "Minimal (one color)" else it.label }) {
                store.set(S.iconStyle, value = it)
            }
        }
        RowDivider()
        SettingRow("Background", "A plate behind each icon, in your shape") {
            Dropdown(store[S.iconBackground], IconBackground.entries, { it.label }) { store.set(S.iconBackground, value = it) }
        }
        if (store[S.iconBackground] == IconBackground.COLOR) {
            ColorEditor("Background color", store[S.iconBgColor], colorOpen, { colorOpen = !colorOpen }) { store.set(S.iconBgColor, value = it) }
        }
    }
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
