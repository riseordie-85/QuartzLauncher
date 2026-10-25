package com.tim.launcher.wizard

import android.app.role.RoleManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tim.launcher.settings.ButtonEnd
import com.tim.launcher.settings.ButtonIcon
import com.tim.launcher.settings.CoverSide
import com.tim.launcher.settings.DeviceType
import com.tim.launcher.settings.DockLength
import com.tim.launcher.settings.DockPosition
import com.tim.launcher.settings.DockEdge
import com.tim.launcher.settings.DockOutline
import com.tim.launcher.settings.DockColorControls
import com.tim.launcher.settings.DockStyleControls
import com.tim.launcher.settings.DrawerOpen
import com.tim.launcher.settings.DrawerScroll
import com.tim.launcher.settings.DrawerView
import com.tim.launcher.settings.GestureAction
import com.tim.launcher.settings.GestureSlot
import com.tim.launcher.settings.IconBackground
import com.tim.launcher.settings.IconShape
import com.tim.launcher.settings.IconStyle
import com.tim.launcher.settings.Profile
import com.tim.launcher.settings.S
import com.tim.launcher.settings.SettingsContext
import com.tim.launcher.settings.BadgesSection
import com.tim.launcher.settings.IconsSection
import com.tim.launcher.Prefs
import com.tim.launcher.WidgetKind
import com.tim.launcher.settings.WidgetPreview
import com.tim.launcher.settings.WeatherControls
import com.tim.launcher.settings.IconDraft
import com.tim.launcher.settings.rememberIconApply
import com.tim.launcher.settings.TabMode
import com.tim.launcher.settings.DrawerStyle
import com.tim.launcher.settings.applyPreset
import com.tim.launcher.wallpaper.WallStore
import com.tim.launcher.wallpaper.WallSlot
import com.tim.launcher.wallpaper.WallCache
import com.tim.launcher.wallpaper.FoldMode
import com.tim.launcher.settings.WallpaperPreview
import com.tim.launcher.settings.UseQuartzWallpaper
import com.tim.launcher.settings.PresetRow
import com.tim.launcher.settings.GlassSliders
import com.tim.launcher.settings.Scope
import com.tim.launcher.settings.SettingsStore
import com.tim.launcher.settings.isDefaultHome
import com.tim.launcher.settings.openHomeSettings
import com.tim.launcher.settings.toast
import com.tim.launcher.settings.dockCounts
import com.tim.launcher.ui.DockSample
import com.tim.launcher.ui.BodyText
import com.tim.launcher.ui.Bullet
import com.tim.launcher.ui.ChoiceCard
import com.tim.launcher.ui.DevicePreview
import com.tim.launcher.ui.Dropdown
import com.tim.launcher.ui.GhostButton
import com.tim.launcher.ui.GlassPanel
import com.tim.launcher.ui.PanelColors
import com.tim.launcher.ui.PrimaryButton
import com.tim.launcher.ui.Q
import com.tim.launcher.ui.RowDivider
import com.tim.launcher.ui.Segmented
import com.tim.launcher.ui.SectionLabel
import com.tim.launcher.ui.SettingRow
import com.tim.launcher.ui.SliderRow
import com.tim.launcher.ui.Stepper
import com.tim.launcher.ui.SwatchRow
import com.tim.launcher.ui.Toggle
import com.tim.launcher.ui.previewLook
import kotlin.math.roundToInt

private enum class Path { RECOMMENDED, WIZARD, IMPORT }

private enum class Step(val title: String, val preview: Boolean = true) {
    WELCOME("Welcome to Quartz", preview = false),
    HOME_APP("Make Quartz your home screen", preview = false),
    PATH("How do you want to set up?", preview = false),
    DOCK_POSITION("Where do you want your dock?"),
    CARRY_OVER("Should your cover screen carry over when you unfold?"),
    COVER_SIDE("Which half should it land on?"),
    COVER_GRID("How many icons on your home screen?"),
    INNER_GRID("How many icons when unfolded?"),
    DOCK_LENGTH("Which dock style do you prefer?"),
    DOCK_SAME_SIDE("Should the dock stay on the same side folded and unfolded?"),
    DOCK_ICONS("How many icons in your dock?"),
    DOCK_ICONS_UNFOLDED("How many when unfolded?"),
    DRAWER_OPEN("How would you like to open your apps?"),
    DRAWER_SWIPE("Which way should you swipe?"),
    DRAWER_BUTTON("Where should the apps button go?"),
    DRAWER_VIEW("How should your apps appear?"),
    DRAWER_ICONS("How should the app list scroll?"),
    DRAWER_STYLE("How should your app drawer open up?"),
    FUNCTION_DONE("You've finished the basics", preview = false),
    DOCK_STYLE("Let's start with the dock"),
    WALLPAPER("Pick a wallpaper", preview = false),
    COLOR("What's your favorite color, and can we see through it?"),
    ICONS("You can style your icons"),
    WIDGETS("Let's add a few widgets"),
    DONE("You're all set", preview = false),
}

/**
 * The setup wizard. Function first (dock, grid, drawer), then form (style), with a skip at each stage and a live
 * preview of this device that updates with every answer.
 */
@Composable
fun SetupWizard(store: SettingsStore, type: DeviceType, onFinish: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(Step.WELCOME) }
    var path by remember { mutableStateOf(Path.WIZARD) }
    var customize by remember { mutableStateOf<Boolean?>(null) }
    var forward by remember { mutableStateOf(true) }
    val foldable = type == DeviceType.FOLDABLE
    val iconDraft = remember { IconDraft(store) }
    val iconApply = rememberIconApply(iconDraft)

    val steps = buildList {
        add(Step.WELCOME)
        add(Step.HOME_APP)
        add(Step.PATH)
        if (path == Path.WIZARD) {
            add(Step.DOCK_POSITION)
            if (foldable) {
                add(Step.CARRY_OVER)
                if (store[S.carryOver]) add(Step.COVER_SIDE)
            }
            add(Step.COVER_GRID)
            if (foldable && !store[S.carryOver]) add(Step.INNER_GRID)
            if (store[S.dockPosition, type.primary] != DockPosition.NONE) {
                add(Step.DOCK_LENGTH)
                if (foldable) add(Step.DOCK_SAME_SIDE)
                add(Step.DOCK_ICONS)
                if (foldable && store[S.dockIcons, type.primary] != 0) add(Step.DOCK_ICONS_UNFOLDED)
            }
            add(Step.DRAWER_OPEN)
            if (store[S.drawerOpen] != DrawerOpen.BUTTON) add(Step.DRAWER_SWIPE)
            if (store[S.drawerOpen] != DrawerOpen.SWIPE) add(Step.DRAWER_BUTTON)
            add(Step.DRAWER_VIEW)
            if (store[S.drawerView] == DrawerView.ICONS) add(Step.DRAWER_ICONS)
            add(Step.DRAWER_STYLE)
            add(Step.FUNCTION_DONE)
            if (customize == true) {
                add(Step.DOCK_STYLE)
                add(Step.WALLPAPER)
                add(Step.COLOR)
                add(Step.ICONS)
                add(Step.WIDGETS)
            }
        }
        add(Step.DONE)
    }
    val index = steps.indexOf(step).coerceAtLeast(0)

    fun goTo(target: Step) {
        forward = steps.indexOf(target) >= index
        step = target
    }
    fun next() {
        steps.getOrNull(index + 1)?.let { goTo(it) }
    }
    fun back() {
        steps.getOrNull(index - 1)?.let { goTo(it) }
    }
    fun finish() {
        store.set(S.wizardDone, value = true)
        // The widget you picked goes on your home page when it next shows
        if ((path == Path.RECOMMENDED || Step.WIDGETS in steps) && store[S.widgets].isNotBlank()) {
            Prefs.get(context).edit().putString(Prefs.PENDING_WIDGETS, store[S.widgets]).apply()
        }
        onFinish()
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Empty file")
            Profile.import(context, text)
        }.onSuccess {
            path = Path.IMPORT
            toast(context, "Imported $it settings")
            goTo(Step.DONE)
        }.onFailure { toast(context, "Couldn't import: ${it.message}") }
    }

    val tall = LocalConfiguration.current.screenHeightDp > LocalConfiguration.current.screenWidthDp
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Progress
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressDots(index, steps.size, Modifier.weight(1f))
            if (step != Step.DONE && step != Step.WELCOME) {
                GhostButton("Skip setup") {
                    // Before any answers, skipping means "recommended"; after, it keeps what you've picked
                    if (index <= steps.indexOf(Step.PATH)) applyRecommended(store, type)
                    goTo(Step.DONE)
                }
            }
        }

        if (step.preview) {
            DevicePreview(
                previewLook(store, type),
                height = if (tall) 170.dp else 130.dp,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally { if (forward) it / 5 else -it / 5 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 5 else it / 5 } + fadeOut())
            },
            modifier = Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth(),
            label = "wizard",
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(
                    if (s == Step.DOCK_ICONS && foldable) "How many icons when folded?" else s.title,
                    color = Q.Text,
                    fontSize = if (s.preview) 22.sp else 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = if (s.preview) 28.sp else 36.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
                )
                StepContent(
                    step = s,
                    store = store,
                    type = type,
                    iconDraft = iconDraft,
                    onPath = { chosen ->
                        path = chosen
                        when (chosen) {
                            Path.RECOMMENDED -> {
                                applyRecommended(store, type)
                                goTo(Step.DONE)
                            }
                            Path.IMPORT -> importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                            Path.WIZARD -> {
                                customize = null
                                goTo(Step.DOCK_POSITION)
                            }
                        }
                    },
                    onCustomize = { yes ->
                        customize = yes
                        if (yes) {
                            goTo(Step.DOCK_STYLE)
                        } else {
                            applyDefaultForm(store)
                            goTo(Step.DONE)
                        }
                    },
                )
            }
        }

        // Back / Next
        Row(
            Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (index > 0 && step != Step.DONE) GhostButton("Back") { back() }
            Spacer(Modifier.weight(1f))
            when (step) {
                Step.PATH, Step.FUNCTION_DONE -> Unit // choosing a card moves on
                Step.DONE -> PrimaryButton("Go home") { finish() }
                Step.WELCOME -> PrimaryButton("Let's go") { next() }
                // Icon changes get applied (with the progress popup) on the way to the next step
                Step.ICONS -> PrimaryButton(if (iconDraft.dirty) "Apply and next" else "Next") {
                    if (iconDraft.dirty) iconApply.run { next() } else next()
                }
                else -> PrimaryButton("Next") { next() }
            }
        }
    }
}

@Composable
private fun ProgressDots(index: Int, count: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (i == index) 20.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (i <= index) Q.Accent else Q.GlassStrong),
            )
        }
    }
}

@Composable
private fun ColumnScope.StepContent(
    step: Step,
    store: SettingsStore,
    type: DeviceType,
    iconDraft: IconDraft,
    onPath: (Path) -> Unit,
    onCustomize: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val primary = type.primary
    val foldable = type == DeviceType.FOLDABLE
    val gap = Modifier.padding(bottom = 10.dp)

    when (step) {
        Step.WELCOME -> {
            BodyText("We'll shape the form and function of your ${type.label.lowercase()} to suit you. First, the promise:")
            Spacer(Modifier.height(16.dp))
            GlassPanel {
                Column(Modifier.padding(20.dp)) {
                    Bullet("No ads, no analytics, no accounts.")
                    Bullet("Your layout and settings stay on this device.")
                    Bullet("The internet is only used for things you ask for, like weather.")
                    Bullet("You can see every permission in Settings › Privacy & backup.")
                }
            }
        }

        Step.HOME_APP -> {
            var tick by remember { mutableIntStateOf(0) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
            val isHome = remember(tick) { isDefaultHome(context) }
            val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
            BodyText(
                if (isHome) "Quartz is your home screen. Pressing Home brings you here."
                else "Android needs your OK before Quartz can be your home screen. You can switch back any time.",
            )
            Spacer(Modifier.height(20.dp))
            if (isHome) {
                ChoiceCard("✓  Quartz is your home screen", selected = true) {}
            } else {
                PrimaryButton("Make Quartz my home screen", Modifier.fillMaxWidth()) {
                    val roles = context.getSystemService(RoleManager::class.java)
                    val ok = runCatching {
                        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                            request.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                            true
                        } else false
                    }.getOrDefault(false)
                    if (!ok) openHomeSettings(context)
                }
            }
        }

        Step.PATH -> {
            ChoiceCard("Recommended", "Rounded dock and drawer, blurred glass and a flip clock. Done in one tap.", false, gap) { onPath(Path.RECOMMENDED) }
            ChoiceCard("Walk me through it", "A few quick questions about your dock, grid and apps.", false, gap) { onPath(Path.WIZARD) }
            ChoiceCard("Import from another device", "Use a settings file exported from Quartz on your other phone or tablet.", false, gap) { onPath(Path.IMPORT) }
        }

        Step.DOCK_POSITION -> {
            val current = store[S.dockPosition, primary]
            DockPosition.entries.forEach { p ->
                ChoiceCard(p.label, null, current == p, gap) {
                    store.linkAll(S.dockPosition, type.screens)
                    store.set(S.dockPosition, primary, p)
                    // The side you pick here is where it goes, even if it had been swapped
                    store.set(S.dockSwapped, value = false)
                }
            }
        }

        Step.CARRY_OVER -> {
            val on = store[S.carryOver]
            ChoiceCard("Yes", "Your cover layout becomes one half of the unfolded screen.", on, gap) { store.set(S.carryOver, value = true) }
            ChoiceCard("No", "The unfolded screen gets its own layout.", !on, gap) { store.set(S.carryOver, value = false) }
        }

        Step.COVER_SIDE -> {
            val side = store[S.coverSide]
            CoverSide.entries.forEach { s ->
                ChoiceCard(s.label, null, side == s, gap) { store.set(S.coverSide, value = s) }
            }
        }

        Step.COVER_GRID -> {
            val cols = store[S.gridCols, primary]
            val rows = store[S.gridRows, primary]
            if (foldable && store[S.carryOver]) {
                BodyText("This is your cover screen. Unfolded, the columns double: ${cols * 2} × $rows.")
                Spacer(Modifier.height(12.dp))
            }
            if (type == DeviceType.TABLET) {
                BodyText("Set it for landscape. Held upright, the grid flips to $rows × $cols and your apps keep their order.")
                Spacer(Modifier.height(12.dp))
            }
            GridSteppers(
                cols,
                rows,
                maxCols = if (type == DeviceType.TABLET) 10 else 8,
                onCols = { store.set(S.gridCols, primary, it) },
                onRows = { store.set(S.gridRows, primary, it) },
            )
        }

        Step.INNER_GRID -> {
            GridSteppers(
                store[S.gridCols, Scope.INNER],
                store[S.gridRows, Scope.INNER],
                maxCols = 12,
                onCols = { store.setAt(S.gridCols, Scope.INNER, it) },
                onRows = { store.setAt(S.gridRows, Scope.INNER, it) },
            )
        }

        Step.DOCK_LENGTH -> {
            val len = store[S.dockLength, primary]
            ChoiceCard("Full width or height", "Edge to edge, like Windows 11", len == DockLength.FULL, gap) { store.set(S.dockLength, primary, DockLength.FULL) }
            ChoiceCard("Fit to icons", "Only as long as it needs to be", len == DockLength.FIT, gap) { store.set(S.dockLength, primary, DockLength.FIT) }
        }

        Step.DOCK_SAME_SIDE -> {
            val same = store.isLinked(S.dockPosition, Scope.COVER) && store.isLinked(S.dockPosition, Scope.INNER)
            ChoiceCard("Yes", "It stays on the outer edge of the same side.", same, gap) {
                val p = store[S.dockPosition, Scope.COVER]
                store.linkAll(S.dockPosition, type.screens)
                store.set(S.dockPosition, Scope.COVER, p)
            }
            ChoiceCard("No", "Choose a position for each.", !same, gap) {
                store.setAt(S.dockPosition, Scope.COVER, store[S.dockPosition, Scope.COVER])
                store.setAt(S.dockPosition, Scope.INNER, store[S.dockPosition, Scope.INNER])
            }
            if (!same) {
                GlassPanel(Modifier.padding(top = 6.dp)) {
                    SettingRow("Folded") {
                        Dropdown(store[S.dockPosition, Scope.COVER], DockPosition.entries.filter { it != DockPosition.NONE }, { it.label }) {
                            store.setAt(S.dockPosition, Scope.COVER, it)
                        }
                    }
                    RowDivider()
                    SettingRow("Unfolded") {
                        Dropdown(store[S.dockPosition, Scope.INNER], DockPosition.entries.filter { it != DockPosition.NONE }, { it.label }) {
                            store.setAt(S.dockPosition, Scope.INNER, it)
                        }
                    }
                }
            }
        }

        Step.DOCK_ICONS -> {
            val count = store[S.dockIcons, primary]
            listOf(4, 5, 6, 0).forEach { n ->
                ChoiceCard(
                    if (n == 0) "Unlimited" else "$n",
                    if (n == 0) "The dock scrolls when it's full" else null,
                    count == n,
                    gap,
                ) { store.set(S.dockIcons, primary, n) }
            }
        }

        Step.DOCK_ICONS_UNFOLDED -> {
            val (folded, unfolded) = store.dockCounts(type)
            BodyText("Your $folded folded icons stay first. Extra icons are added after them.")
            Spacer(Modifier.height(12.dp))
            GlassPanel(gap) {
                SettingRow("Icons when unfolded", if (unfolded == 0) "The dock scrolls when it's full" else null) {
                    Stepper(if (unfolded == 0) 16 else unfolded, folded..16, enabled = unfolded != 0) {
                        store.setAt(S.dockIcons, Scope.INNER, it)
                    }
                }
            }
            ChoiceCard("Unlimited", "The dock scrolls when it's full", unfolded == 0, gap) {
                store.setAt(S.dockIcons, Scope.INNER, if (unfolded == 0) minOf(folded * 2, 16) else 0)
            }
        }

        Step.DRAWER_OPEN -> {
            val open = store[S.drawerOpen]
            ChoiceCard("Swipe", "A gesture on the home screen", open == DrawerOpen.SWIPE, gap) { store.set(S.drawerOpen, value = DrawerOpen.SWIPE) }
            ChoiceCard("Button", "An apps button in your dock", open == DrawerOpen.BUTTON, gap) { store.set(S.drawerOpen, value = DrawerOpen.BUTTON) }
            ChoiceCard("Both", null, open == DrawerOpen.BOTH, gap) { store.set(S.drawerOpen, value = DrawerOpen.BOTH) }
        }

        Step.DRAWER_SWIPE -> {
            val slots = listOf(
                GestureSlot.SWIPE_UP to ("Swipe up" to null),
                GestureSlot.SWIPE_DOWN to ("Swipe down" to "Replaces swipe down for notifications"),
                GestureSlot.SWIPE_PAST_LEFT to ("Swipe left" to "Opens when you swipe past your first page"),
                GestureSlot.SWIPE_PAST_RIGHT to ("Swipe right" to "Opens when you swipe past your last page"),
            )
            slots.forEach { (slot, text) ->
                ChoiceCard(text.first, text.second, store[S.gesture(slot)] == GestureAction.APP_DRAWER, gap) {
                    slots.forEach { (other, _) ->
                        if (store[S.gesture(other)] == GestureAction.APP_DRAWER) store.set(S.gesture(other), value = GestureAction.NONE)
                    }
                    store.set(S.gesture(slot), value = GestureAction.APP_DRAWER)
                }
            }
        }

        Step.DRAWER_BUTTON -> {
            val position = store[S.dockPosition, primary]
            if (position == DockPosition.NONE) {
                BodyText("With no dock, the apps button sits at the bottom of your home screen.")
                Spacer(Modifier.height(12.dp))
            } else {
                val vertical = position == DockPosition.LEFT || position == DockPosition.RIGHT
                Segmented(store[S.drawerButtonEnd], ButtonEnd.entries, {
                    when {
                        vertical && it == ButtonEnd.START -> "Top"
                        vertical -> "Bottom"
                        it == ButtonEnd.START -> "Left"
                        else -> "Right"
                    }
                }, gap) { store.set(S.drawerButtonEnd, value = it) }
            }
            GlassPanel {
                SettingRow("Button icon") {
                    Dropdown(store[S.drawerButtonIcon], ButtonIcon.entries, { it.label }) { store.set(S.drawerButtonIcon, value = it) }
                }
            }
        }

        Step.DRAWER_VIEW -> {
            val view = store[S.drawerView]
            ChoiceCard("Icons", "A grid of app icons", view == DrawerView.ICONS, gap) { store.set(S.drawerView, value = DrawerView.ICONS) }
            ChoiceCard("List", "Names in a list, with small icons", view == DrawerView.LIST, gap) { store.set(S.drawerView, value = DrawerView.LIST) }
        }

        Step.DRAWER_ICONS -> {
            Segmented(store[S.drawerScroll], DrawerScroll.entries, { it.label }, gap) { store.set(S.drawerScroll, value = it) }
            GlassPanel {
                SettingRow("Fit columns to the space", "As many as fit beside the tabs and dock") {
                    Toggle(store[S.drawerColsAuto]) { store.set(S.drawerColsAuto, value = it) }
                }
                if (!store[S.drawerColsAuto]) {
                    RowDivider()
                    val cols = store[S.drawerCols, primary]
                    SettingRow("Columns", if (foldable) "Doubled when unfolded (${cols * 2})" else null) {
                        Stepper(cols, 3..8) { store.set(S.drawerCols, primary, it) }
                    }
                }
            }
        }

        Step.DRAWER_STYLE -> {
            val style = store[S.drawerStyle, primary]
            fun pick(s: DrawerStyle) {
                store.linkAll(S.drawerStyle, type.screens)
                store.set(S.drawerStyle, primary, s)
            }
            ChoiceCard("Full screen", "Covers the home screen, with your dock still showing", style == DrawerStyle.FULL, gap) { pick(DrawerStyle.FULL) }
            ChoiceCard("Floating box", "A rounded panel in the middle", style == DrawerStyle.FLOATING, gap) { pick(DrawerStyle.FLOATING) }
            ChoiceCard("Side list", "A tall panel down one side", style == DrawerStyle.SIDE, gap) { pick(DrawerStyle.SIDE) }
            ChoiceCard("Bottom popup", "Rises from the bottom, like a Start menu", style == DrawerStyle.BOTTOM, gap) { pick(DrawerStyle.BOTTOM) }
            Text("Tabs", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp))
            Segmented(store[S.drawerTabMode], TabMode.entries, { it.label }, gap) { store.set(S.drawerTabMode, value = it) }
            BodyText(
                when (store[S.drawerTabMode]) {
                    TabMode.NONE -> "One list of all your apps."
                    TabMode.CUSTOM -> "Make your tabs in Settings › App drawer. Quartz suggests apps for each one; you approve them."
                    TabMode.AUTO -> "Tabs from the categories apps give themselves: Games, Social, Music and so on."
                },
            )
        }

        Step.FUNCTION_DONE -> {
            BodyText("You can start on the look now, or come back later. We know you're busy.")
            Spacer(Modifier.height(16.dp))
            ChoiceCard("Start customizing", "Dock style, colors, icons and widgets", false, gap) { onCustomize(true) }
            ChoiceCard("Skip for now", "Use the rounded default look", false, gap) { onCustomize(false) }
        }

        Step.DOCK_STYLE -> {
            DockSample(store, gap)
            val on = store[S.dockBackground]
            ChoiceCard("Glass", "A frosted panel behind your dock icons", on, gap) { store.set(S.dockBackground, value = true) }
            ChoiceCard("No background", "Icons float on the wallpaper", !on, gap) { store.set(S.dockBackground, value = false) }
            if (on) GlassPanel(gap) { DockStyleControls(store) }
        }

        Step.WALLPAPER -> {
            BodyText("Quartz draws your wallpaper, so the glass on your dock and drawer can frost it. More options (photos, patterns, colors) are in Settings › Wallpaper.")
            Spacer(Modifier.height(16.dp))
            val generation = WallCache.generation
            val chosen = remember(generation) { WallStore.load(context, WallSlot.MAIN) }
            WallpaperPreview(foldable, Modifier.fillMaxWidth().padding(bottom = 16.dp), height = 150.dp)
            // Keeps your darkening and photo (a photo no wallpaper uses gets deleted); cut-quartz ones bring their pattern
            PresetRow(chosen) { WallStore.save(context, WallSlot.MAIN, applyPreset(chosen, it)) }
            Spacer(Modifier.height(16.dp))
            var active by remember { mutableStateOf(WallCache.isActive(context)) }
            LifecycleResumeEffect(Unit) {
                active = WallCache.isActive(context)
                onPauseOrDispose { }
            }
            if (!active) UseQuartzWallpaper()
            if (foldable) {
                Text("Across both screens", color = Q.Text, fontSize = 16.sp, modifier = Modifier.padding(top = 16.dp, bottom = 10.dp))
                var mode by remember { mutableStateOf(WallStore.mode(context)) }
                Segmented(mode, FoldMode.entries, { it.label.replace(" · ", "\n") }) {
                    mode = it
                    WallStore.setMode(context, it)
                }
                BodyText(mode.about, Modifier.padding(top = 8.dp))
            }
        }

        Step.COLOR -> {
            BodyText("This sets the dock and the app drawer together, so they match.")
            Spacer(Modifier.height(12.dp))
            DockSample(store, gap)
            GlassPanel {
                // Fill, gradient and pattern moved out of the dock style controls; they only matter with a background
                if (store[S.dockBackground]) {
                    DockColorControls(store)
                } else {
                    SwatchRow(store[S.panelColor], PanelColors) { store.set(S.panelColor, value = it) }
                    RowDivider()
                    GlassSliders(store)
                }
            }
        }

        Step.ICONS -> {
            val ctx = remember { SettingsContext(store, type, type.primary, onEditing = {}, go = {}, back = {}) }
            BodyText("Icon packs, a shape, and one-color or plated icons. \"Match everywhere\" keeps the dock, home screen and drawer the same.")
            // Going back keeps changes you haven't applied
            DisposableEffect(iconDraft) { onDispose { iconDraft.saveQuietly() } }
            IconsSection(ctx, iconDraft)
            BadgesSection(ctx)
        }

        Step.WIDGETS -> {
            val chosen = WidgetKind.quartz.firstOrNull { it.name == store[S.widgets] }
            BodyText("Pick one for your home page. Later you can add more, move them and resize them: long-press an empty spot and tap Widgets.")
            Spacer(Modifier.height(12.dp))
            if (chosen != null) WidgetPreview(store, chosen)
            ChoiceCard("No widget for now", null, chosen == null, gap) { store.set(S.widgets, value = "") }
            WidgetKind.quartz.forEach { kind ->
                ChoiceCard(kind.label, kind.about, chosen == kind, gap) { store.set(S.widgets, value = kind.name) }
            }
            if (chosen?.weather == true) {
                SectionLabel("Weather")
                WeatherControls(store)
            }
            if (chosen != null) {
                Spacer(Modifier.height(12.dp))
                GlassPanel {
                    SettingRow("24-hour clock") { Toggle(store[S.clock24h]) { store.set(S.clock24h, value = it) } }
                }
            }
        }

        Step.DONE -> {
            BodyText("Everything can be changed later in Settings. To get there, long-press an empty spot on your home screen.")
            Spacer(Modifier.height(16.dp))
            DevicePreview(previewLook(store, type), height = 150.dp)
        }
    }
}

@Composable
private fun GridSteppers(cols: Int, rows: Int, maxCols: Int = 8, onCols: (Int) -> Unit, onRows: (Int) -> Unit) {
    GlassPanel {
        SettingRow("Columns") { Stepper(cols, 3..maxCols) { onCols(it) } }
        RowDivider()
        SettingRow("Rows") { Stepper(rows, 3..10) { onRows(it) } }
    }
}

/** One-tap setup: rounded glass, bottom dock, cover on the right, swipe up for apps, clock and weather. */
private fun applyRecommended(store: SettingsStore, type: DeviceType) {
    store.linkAll(S.dockPosition, type.screens)
    store.set(S.dockPosition, type.primary, DockPosition.BOTTOM)
    store.set(S.dockSwapped, value = false)
    store.set(S.dockLength, type.primary, DockLength.FULL)
    store.set(S.carryOver, value = true)
    store.set(S.coverSide, value = CoverSide.RIGHT)
    store.set(S.drawerOpen, value = DrawerOpen.SWIPE)
    GestureSlot.entries.forEach { if (store[S.gesture(it)] == GestureAction.APP_DRAWER) store.set(S.gesture(it), value = GestureAction.NONE) }
    store.set(S.gesture(GestureSlot.SWIPE_UP), value = GestureAction.APP_DRAWER)
    store.set(S.drawerView, value = DrawerView.ICONS)
    store.set(S.drawerScroll, value = DrawerScroll.VERTICAL)
    store.set(S.widgets, value = WidgetKind.FLIP.name)
    applyDefaultForm(store)
}

private fun applyDefaultForm(store: SettingsStore) {
    store.set(S.dockBackground, value = true)
    store.set(S.dockOutline, value = DockOutline.STRAIGHT)
    store.set(S.dockRadius, value = 28)
    store.set(S.dockFloating, value = true)
    store.set(S.dockEdge, value = DockEdge.SOFT)
    store.set(S.panelBlur, value = 0.6f)
}
