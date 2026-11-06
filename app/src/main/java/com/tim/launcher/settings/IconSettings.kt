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
import androidx.compose.runtime.MutableState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import android.os.SystemClock
import androidx.compose.runtime.Stable
import kotlin.math.sin
import kotlin.math.cos
import kotlinx.coroutines.launch
import com.tim.launcher.ui.PrimaryButton
import com.tim.launcher.ui.GhostButton
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Spacer
import com.tim.launcher.ui.SliderRow
import com.tim.launcher.ui.Tabs
import com.tim.launcher.wallpaper.PatternStyle
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
import com.tim.launcher.StyledIcon
import com.tim.launcher.FolderTile
import com.tim.launcher.FolderGrid
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

/**
 * Icon style changes you haven't applied yet. The preview draws from these; Apply saves them and gets every icon
 * drawn in the new style before the home screen switches over, so nothing flickers.
 */
class IconDraft(val store: SettingsStore) : IconRender.Values {
    private val pending = mutableStateMapOf<Setting<*>, Any>()

    /**
     * True when the changes would make any icon look different. Picking something and then picking the old value
     * again (or turning "Match everywhere" off and back on) leaves nothing to apply.
     */
    val dirty: Boolean
        get() = pending.isNotEmpty() && IconSlot.entries.any { IconRender.specFor(this, it) != IconRender.specFor(store, it) }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(setting: Setting<T>): T = if (pending.containsKey(setting)) pending[setting] as T else store[setting]

    override fun <T> place(own: Setting<T>?, home: Setting<T>): T =
        if (own != null && (pending.containsKey(own) || store.owner(own, Scope.GLOBAL) != null)) get(own) else get(home)

    fun <T : Any> set(setting: Setting<T>, value: T) {
        pending[setting] = value
    }

    fun discard() = pending.clear()

    /** Saves the changes. Returns true if an icon pack changed (everything drawn so far is out of date). */
    @Suppress("UNCHECKED_CAST")
    fun save(): Boolean {
        val packChanged = pending.keys.any { it == S.iconPackHome || it == S.iconPackDock || it == S.iconPackDrawer }
        pending.forEach { (k, v) -> store.set(k as Setting<Any>, value = v) }
        pending.clear()
        return packChanged
    }

    /** Leaving with changes still waiting: keep them rather than lose them. Icons switch over as they're drawn. */
    fun saveQuietly() {
        if (pending.isEmpty()) return
        val changesIcons = dirty
        if (save() && changesIcons) IconRender.clear()
    }
}

/** Runs Apply: saves the draft, draws every icon in the new style, then says when it's done. */
@Stable
class IconApply internal constructor(private val context: Context, private val scope: CoroutineScope, private val draft: IconDraft) {
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    fun run(then: () -> Unit = {}) {
        if (progress != null) return
        progress = 0 to 0
        val started = SystemClock.uptimeMillis()
        if (draft.save()) IconRender.clear()
        val specs = IconSlot.entries.map { IconRender.specFor(draft.store, it) }
        scope.launch {
            // If the app list can't be read, there's just nothing to draw ahead: the popup still closes
            val apps = withContext(Dispatchers.IO) { runCatching { loadAppEntries(context) }.getOrDefault(emptyList()) }
            IconRender.prepare(context, apps, specs, onProgress = { done, total -> progress = done to total }) {
                scope.launch {
                    // Let the gem turn for a moment even when there was little to draw, rather than flash
                    delay((900 - (SystemClock.uptimeMillis() - started)).coerceAtLeast(0))
                    progress = null
                    then()
                }
            }
        }
    }
}

/** An [IconApply] for this screen, with its progress popup. */
@Composable
fun rememberIconApply(draft: IconDraft): IconApply {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apply = remember(draft) { IconApply(context.applicationContext, scope, draft) }
    apply.progress?.let { (done, total) -> ApplyingDialog(done, total) }
    return apply
}

/** The icon preview for setup, pinned above the icon settings: the open tab's style as you change it. */
@Composable
fun IconDraftPreview(draft: IconDraft, tab: IconSlot) {
    val slot = if (draft.get(S.iconsMatch)) IconSlot.HOME else tab
    IconPreview(slot, IconRender.specFor(draft, slot))
}

/** Your apps drawn in [spec], over a wallpaper strip. */
@Composable
private fun IconPreview(slot: IconSlot, spec: IconRender.Spec) {
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
                // Folders on the home screen and dock wear the same style; the drawer has none
                val folder = slot != IconSlot.DRAWER && apps.size >= 4
                (if (folder) apps.take(4) else apps).forEach { app -> StyledIcon(app, 52.dp, contentDescription = app.label, spec = spec) }
                if (folder) {
                    FolderTile(spec, 52.dp) { _, inset -> FolderGrid(2, apps.reversed().take(4), 52.dp * (1f - 2 * inset), spec) }
                }
            }
        }
    }
}

/**
 * Settings › Appearance › Icons. With "Match everywhere" on, one style for every icon. Off, tabs for the home
 * screen, dock and app drawer, each with its own pack, shape, style and background (the dock and drawer start out
 * like the home screen). Changes show in the preview right away and reach your icons when you tap Apply. Sizes
 * (below) apply straight away.
 */
@Composable
fun IconsSection(
    ctx: SettingsContext,
    draft: IconDraft,
    /** Which place's tab is open, when someone else shows the preview (setup pins it at the top). */
    tabState: MutableState<IconSlot>? = null,
    showPreview: Boolean = true,
) {
    val store = ctx.store
    val context = LocalContext.current
    val packs by produceState(emptyList<IconPacks.Pack>()) { value = withContext(Dispatchers.IO) { IconPacks.installed(context) } }
    val ownTab = remember { mutableStateOf(IconSlot.HOME) }
    var tab by (tabState ?: ownTab)
    var colorOpen by remember { mutableStateOf<String?>(null) }
    val match = draft.get(S.iconsMatch)
    val slot = if (match) IconSlot.HOME else tab
    // The settings this tab edits: the plain ones for home (and for everything when matching), the place's own otherwise
    val place = when (slot) {
        IconSlot.HOME -> null
        IconSlot.DOCK -> S.iconsDock
        IconSlot.DRAWER -> S.iconsDrawer
    }
    fun <T : Any> set(home: Setting<T>, own: Setting<T>?, value: T) = draft.set(own ?: home, value)
    val packSetting = when (slot) {
        IconSlot.HOME -> S.iconPackHome
        IconSlot.DOCK -> S.iconPackDock
        IconSlot.DRAWER -> S.iconPackDrawer
    }

    SectionLabel("Icons")
    GlassPanel {
        SettingRow("Match everywhere", "Home screen, dock and app drawer icons all look the same") {
            Toggle(match) { draft.set(S.iconsMatch, it) }
        }
        if (!match) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
                Tabs(tab, IconSlot.entries, { slotName(it) }) { tab = it }
            }
        }
        if (showPreview) IconPreview(slot, IconRender.specFor(draft, slot))
        Spacer(Modifier.height(8.dp))
        RowDivider()
        val options = listOf("") + packs.map { it.pkg }
        fun label(pkg: String) = if (pkg.isEmpty()) "Apps' own icons" else packs.firstOrNull { it.pkg == pkg }?.label ?: pkg
        SettingRow("Icon pack") {
            Dropdown(draft.get(packSetting), options, { label(it) }) { draft.set(packSetting, it) }
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
            Dropdown(draft.place(place?.shape, S.iconShape), IconShape.entries, { it.label }) { set(S.iconShape, place?.shape, it) }
        }
        RowDivider()
        SettingRow("Use the shape on every icon", "Pack icons too") {
            Toggle(draft.place(place?.force, S.iconForceShape)) { set(S.iconForceShape, place?.force, it) }
        }
        RowDivider()
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Style", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
            Segmented(draft.place(place?.style, S.iconStyle), IconStyle.entries, { if (it == IconStyle.MINIMAL) "Minimal (one color)" else it.label }) {
                set(S.iconStyle, place?.style, it)
            }
        }
        RowDivider()
        val background = draft.place(place?.background, S.iconBackground)
        SettingRow("Background", "A plate behind each icon, in your shape") {
            Dropdown(background, IconBackground.entries, { it.label }) {
                set(S.iconBackground, place?.background, it)
                // Frosted glass needs a see-through tint: a solid color would hide the glass, so start it faint
                val tint = draft.place(place?.color, S.iconBgColor)
                if (it == IconBackground.FROSTED && (tint ushr 24) > 0x99) set(S.iconBgColor, place?.color, 0x38FFFFFF)
            }
        }
        if (background == IconBackground.FROSTED) {
            ColorEditor("Tint", draft.place(place?.color, S.iconBgColor), colorOpen == "from", {
                colorOpen = if (colorOpen == "from") null else "from"
            }, alpha = true) { set(S.iconBgColor, place?.color, it) }
            Text(
                "Your wallpaper, blurred behind each icon like the dock's glass, under this tint. Keep it see-through " +
                    "for the glass to show. On the home screen and dock (the drawer is already glass).",
                color = Q.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )
        }
        if (background == IconBackground.COLOR || background == IconBackground.GRADIENT) {
            val gradient = background == IconBackground.GRADIENT
            ColorEditor(if (gradient) "Gradient from" else "Background color", draft.place(place?.color, S.iconBgColor), colorOpen == "from", {
                colorOpen = if (colorOpen == "from") null else "from"
            }, alpha = true) { set(S.iconBgColor, place?.color, it) }
            if (gradient) {
                ColorEditor("Gradient to", draft.place(place?.color2, S.iconBgColor2), colorOpen == "to", {
                    colorOpen = if (colorOpen == "to") null else "to"
                }, alpha = true) { set(S.iconBgColor2, place?.color2, it) }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("Direction", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                    Segmented(draft.place(place?.gradient, S.iconGradient), GradientDirection.entries, { it.label }) {
                        set(S.iconGradient, place?.gradient, it)
                    }
                }
                RowDivider()
            }
        }
        if (background != IconBackground.NONE) {
            val pattern = draft.place(place?.pattern, S.iconPattern)
            SettingRow("Pattern", "Over the background") {
                Dropdown(pattern, PatternStyle.entries, { if (it.faceted) "◆ ${it.label}" else it.label }) { set(S.iconPattern, place?.pattern, it) }
            }
            if (pattern != PatternStyle.NONE) {
                val strength = draft.place(place?.patternStrength, S.iconPatternStrength)
                SliderRow("Pattern strength", strength, 0.05f..0.6f, "${(strength * 100).roundToInt()}%") {
                    set(S.iconPatternStrength, place?.patternStrength, it)
                }
                val size = draft.place(place?.patternSize, S.iconPatternSize)
                SliderRow("Pattern size", size, 0f..1f, patternSizeLabel(size)) {
                    set(S.iconPatternSize, place?.patternSize, it)
                }
            }
        }
        RowDivider()
        SettingRow("Edge", "A hairline, or light catching the rim like the dock's") {
            Dropdown(draft.place(place?.edge, S.iconEdge), DockEdge.entries, { it.label }) { set(S.iconEdge, place?.edge, it) }
        }
        RowDivider()
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Shadow", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
            Segmented(draft.place(place?.shadow, S.iconShadow), IconShadow.entries, { it.label }) { set(S.iconShadow, place?.shadow, it) }
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

/** The bar that appears while icon changes are waiting: Apply (with its progress popup) or Undo. */
@Composable
fun IconApplyBar(draft: IconDraft, apply: IconApply, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        draft.dirty && apply.progress == null,
        modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .clip(CircleShape)
                .background(Color(0xF0221F2E))
                .border(1.dp, Q.Edge, CircleShape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Icon changes", color = Q.Text, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp, end = 8.dp))
            GhostButton("Undo") { draft.discard() }
            PrimaryButton("Apply") { apply.run() }
        }
    }
}

/** "Styling your icons": a slowly turning cut gem catching the light, and how far along it is. */
@Composable
private fun ApplyingDialog(done: Int, total: Int) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xF0221F2E))
                .border(1.dp, Q.Edge, RoundedCornerShape(28.dp))
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val fraction = if (total <= 0) 0f else done.toFloat() / total
            GemSpinner(fraction, Modifier.size(120.dp))
            Spacer(Modifier.height(18.dp))
            Text("Styling your icons", color = Q.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (total == 0) "Getting ready…" else "$done of $total",
                color = Q.TextDim,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** A six-sided cut gem that turns, its facets brightening as they face the light, inside a ring that fills up. */
@Composable
private fun GemSpinner(progress: Float, modifier: Modifier) {
    val spin = rememberInfiniteTransition(label = "gem")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "turn")
    val pulse by spin.animateFloat(0.92f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse")
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), label = "progress")
    Canvas(modifier) {
        val c = center
        val ringR = size.minDimension / 2f - 4.dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.08f), ringR, c, style = Stroke(4.dp.toPx()))
        drawArc(
            Q.Accent, -90f, 360f * shown, useCenter = false,
            topLeft = Offset(c.x - ringR, c.y - ringR), size = Size(ringR * 2, ringR * 2),
            style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
        )
        val r = ringR * 0.62f * pulse
        val inner = r * 0.48f
        val light = Math.toRadians(-135.0)
        val dark = Color(0xFF3A2470)
        val bright = Color(0xFFC9B8FF)
        for (i in 0 until 6) {
            val a0 = Math.toRadians((angle + i * 60).toDouble())
            val a1 = Math.toRadians((angle + (i + 1) * 60).toDouble())
            val mid = (a0 + a1) / 2
            val p0 = Offset(c.x + r * cos(a0).toFloat(), c.y + r * sin(a0).toFloat())
            val p1 = Offset(c.x + r * cos(a1).toFloat(), c.y + r * sin(a1).toFloat())
            val q0 = Offset(c.x + inner * cos(a0).toFloat(), c.y + inner * sin(a0).toFloat())
            val q1 = Offset(c.x + inner * cos(a1).toFloat(), c.y + inner * sin(a1).toFloat())
            // How much this facet faces the light, 0 to 1
            val lit = ((cos(mid - light) + 1) / 2).toFloat()
            val facet = Path().apply { moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(q1.x, q1.y); lineTo(q0.x, q0.y); close() }
            drawPath(facet, lerp(dark, bright, lit * 0.85f))
            drawPath(facet, Color.White.copy(alpha = 0.18f), style = Stroke(1.dp.toPx()))
        }
        // The flat top of the gem, catching a sheen
        val table = Path()
        for (i in 0 until 6) {
            val a = Math.toRadians((angle + i * 60).toDouble())
            val p = Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat())
            if (i == 0) table.moveTo(p.x, p.y) else table.lineTo(p.x, p.y)
        }
        table.close()
        drawPath(table, Brush.linearGradient(listOf(Color(0xFFE9E2FF), Color(0xFF8C6CFF)), start = c - Offset(inner, inner), end = c + Offset(inner, inner)))
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
