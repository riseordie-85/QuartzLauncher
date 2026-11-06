package com.tim.launcher.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateMapOf
import com.tim.launcher.Prefs
import com.tim.launcher.wallpaper.PatternStyle
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Where a setting's value lives. Values resolve upward: a screen inherits from its device type, which inherits
 * from "All devices". A screen only holds its own value when it's been unlinked ("Only this screen").
 */
enum class Scope(val label: String, val parent: Scope?) {
    GLOBAL("All devices", null),
    PHONE("Phone", GLOBAL),
    FOLDABLE("Folding phone", GLOBAL),
    COVER("Cover screen", FOLDABLE),
    INNER("Unfolded screen", FOLDABLE),
    TABLET("Tablet", GLOBAL),
}

enum class DeviceType(val label: String) {
    PHONE("Phone"),
    FOLDABLE("Folding phone"),
    TABLET("Tablet");

    /** The screens this kind of device has, in the order they're shown. */
    val screens: List<Scope>
        get() = when (this) {
            PHONE -> listOf(Scope.PHONE)
            FOLDABLE -> listOf(Scope.COVER, Scope.INNER)
            TABLET -> listOf(Scope.TABLET)
        }

    /** The small "main" screen whose grid the others are based on. */
    val primary: Scope get() = screens.first()
}

fun detectDeviceType(context: Context): DeviceType {
    val foldable = Prefs.get(context).getBoolean(Prefs.FOLDABLE, false) ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
    if (foldable) return DeviceType.FOLDABLE
    val sw = context.resources.configuration.smallestScreenWidthDp
    return if (sw >= 600) DeviceType.TABLET else DeviceType.PHONE
}

// ---- Value types ----

sealed interface Codec<T> {
    fun encode(value: T): String
    fun decode(text: String): T?
}

object IntCodec : Codec<Int> {
    override fun encode(value: Int) = value.toString()
    override fun decode(text: String) = text.toIntOrNull()
}

object FloatCodec : Codec<Float> {
    override fun encode(value: Float) = value.toString()
    override fun decode(text: String) = text.toFloatOrNull()
}

object BoolCodec : Codec<Boolean> {
    override fun encode(value: Boolean) = value.toString()
    override fun decode(text: String) = text.toBooleanStrictOrNull()
}

object StringCodec : Codec<String> {
    override fun encode(value: String) = value
    override fun decode(text: String) = text
}

class EnumCodec<E : Enum<E>>(private val values: Array<E>) : Codec<E> {
    override fun encode(value: E) = value.name
    override fun decode(text: String) = values.firstOrNull { it.name == text }
}

/** One setting. [perScreen] settings can differ between screens; the rest are shared everywhere. */
class Setting<T>(val id: String, val default: T, val codec: Codec<T>, val perScreen: Boolean = false)

// ---- Choices ----

enum class DockPosition(val label: String) { BOTTOM("Bottom"), RIGHT("Right"), TOP("Top"), LEFT("Left"), NONE("No dock") }
enum class DockLength(val label: String) { FULL("Full width or height"), FIT("Fit to icons") }
enum class DockAlign(val label: String) { START("Start"), CENTER("Center"), END("End") }
/** Phase 3's dock shape. Only read once, to carry old choices over to [S.dockBackground] and [S.dockRadius]. */
enum class DockShape(val label: String) { SQUARE("Square"), ROUNDED("Rounded"), NONE("No background") }
enum class DockOutline(val label: String) {
    STRAIGHT("Straight"), SLANTED("Slanted"), POINTED("Pointed"), WAVE("Wave"), FADE("Fade"),
}
enum class DockFill(val label: String) { GLASS("Glass"), GRADIENT("Gradient") }
/** Which way a gradient runs (the dock's, icon plates'). Radial glows out from the middle. */
enum class GradientDirection(val label: String) { HORIZONTAL("Across →"), VERTICAL("Down ↓"), DIAGONAL("Diagonal ↘"), RADIAL("Radial ◎") }
enum class DockEdge(val label: String) { NONE("None"), HAIRLINE("Hairline"), SOFT("Soft light"), BRIGHT("Bright light") }
enum class CoverSide(val label: String) { LEFT("Left half"), RIGHT("Right half") }
enum class PageMode(val label: String) { STRIP("Side by side"), PAIRED("Paired (old)") }
enum class DrawerOpen(val label: String) { SWIPE("Swipe"), BUTTON("Button"), BOTH("Swipe and button") }
enum class ButtonEnd(val label: String) { START("Start of the dock"), END("End of the dock") }
enum class ButtonIcon(val label: String) { QUARTZ("Quartz Q"), GRID("Grid"), DOTS("Dots"), CIRCLE("Circle") }
enum class DrawerView(val label: String) { ICONS("Icons"), LIST("List") }
enum class DrawerScroll(val label: String) { VERTICAL("Vertical (infinite)"), HORIZONTAL("Horizontal (paged)") }
enum class DrawerStyle(val label: String) { FULL("Full screen"), FLOATING("Floating box"), SIDE("Side list"), BOTTOM("Bottom popup") }
enum class DrawerSort(val label: String) { ALPHA("A to Z"), INSTALLED("Newest first"), CUSTOM("Custom order") }
enum class TabMode(val label: String) { NONE("No tabs"), CUSTOM("My tabs"), AUTO("Automatic") }
enum class IconShape(val label: String) {
    SYSTEM("App's own shape"), CIRCLE("Circle"), SQUIRCLE("Squircle"), ROUNDED("Rounded square"), SQUARE("Square"),
    TEARDROP("Teardrop"), HEXAGON("Hexagon"),
}
enum class IconStyle(val label: String) { DEFAULT("Default"), MINIMAL("Minimal") }
enum class IconBackground(val label: String) {
    NONE("None"),

    /** Frosted glass: the wallpaper blurred behind the icon, like the dock, under a see-through tint. */
    FROSTED("Frosted glass"),
    LIGHT("Light"),
    DARK("Dark"),
    COLOR("Color"),
    GRADIENT("Gradient"),
}
enum class IconShadow(val label: String) { NONE("None"), SOFT("Soft"), STRONG("Strong") }
enum class BadgeStyle(val label: String) { DOT("Dot"), COUNTER("Counter") }
enum class FolderIcon(val label: String) { PLAIN("Folder"), BADGE("Folder with badge"), GRID2("2 × 2 preview"), GRID3("3 × 3 preview") }
enum class FolderOpen(val label: String) { FULL("Full screen"), CENTER("Floating box"), POPUP("Pop up by the icon"), ROW("Expanding row") }
enum class FolderView(val label: String) { GRID("Grid"), LIST("List") }
enum class RowDirection(val label: String) { HORIZONTAL("Sideways"), VERTICAL("Up and down") }
enum class WeatherSource(val label: String) { CITY("A city you choose"), DEVICE("This phone's location") }
enum class TempUnit(val label: String) { F("°F"), C("°C") }

enum class GestureAction(val label: String) {
    NONE("Nothing"),
    APP_DRAWER("Open apps"),
    NOTIFICATIONS("Open notifications"),
    SEARCH("Search apps"),
    SETTINGS("Open Quartz settings"),
    LOCK("Lock screen"),
    SWAP_DOCK("Swap dock side"),
    OVERVIEW("Page overview"),
}

enum class GestureSlot(val label: String) {
    SWIPE_UP("Swipe up"),
    SWIPE_DOWN("Swipe down"),
    SWIPE_PAST_LEFT("Swipe past the first page"),
    SWIPE_PAST_RIGHT("Swipe past the last page"),
    DOUBLE_TAP("Double-tap"),
    TWO_FINGER_DOWN("Two-finger swipe down"),
    PINCH("Pinch"),
}

/** One place's icon style (the dock's or the drawer's). The home screen uses the plain icon settings in [S]. */
class IconPlaceSettings(id: String) {
    val shape = Setting("look.$id.iconShape", IconShape.SYSTEM, EnumCodec(IconShape.entries.toTypedArray()))
    val force = Setting("look.$id.iconForceShape", false, BoolCodec)
    val style = Setting("look.$id.iconStyle", IconStyle.DEFAULT, EnumCodec(IconStyle.entries.toTypedArray()))
    val background = Setting("look.$id.iconBackground", IconBackground.NONE, EnumCodec(IconBackground.entries.toTypedArray()))
    val color = Setting("look.$id.iconBgColor", 0xFF8C6CFF.toInt(), IntCodec)
    val color2 = Setting("look.$id.iconBgColor2", 0xFF2546A8.toInt(), IntCodec)
    val pattern = Setting("look.$id.iconPattern", PatternStyle.NONE, EnumCodec(PatternStyle.entries.toTypedArray()))
    val patternStrength = Setting("look.$id.iconPatternStrength", 0.3f, FloatCodec)
    val patternSize = Setting("look.$id.iconPatternScale", 0.5f, FloatCodec)
    val gradient = Setting("look.$id.iconGradient", GradientDirection.DIAGONAL, EnumCodec(GradientDirection.entries.toTypedArray()))
    val edge = Setting("look.$id.iconEdge", DockEdge.NONE, EnumCodec(DockEdge.entries.toTypedArray()))
    val shadow = Setting("look.$id.iconShadow", IconShadow.NONE, EnumCodec(IconShadow.entries.toTypedArray()))
}

/** A dock or drawer icon setting, or the home screen's while that place hasn't been given its own. */
fun <T> SettingsStore.orHome(place: Setting<T>?, home: Setting<T>): T =
    if (place != null && owner(place, Scope.GLOBAL) != null) this[place] else this[home]

/** Every setting Quartz knows about. Changing an id here orphans saved values, so ids stay stable. */
object S {
    // Home screen
    val gridCols = Setting("home.cols", 4, IntCodec, perScreen = true)
    val gridRows = Setting("home.rows", 6, IntCodec, perScreen = true)
    val pages = Setting("home.pages", 3, IntCodec)
    val carryOver = Setting("home.carryOver", true, BoolCodec)
    val coverSide = Setting("home.coverSide", CoverSide.RIGHT, EnumCodec(CoverSide.entries.toTypedArray()))
    val pageMode = Setting("home.pageMode", PageMode.STRIP, EnumCodec(PageMode.entries.toTypedArray()))
    val labelsHome = Setting("home.labels", true, BoolCodec, perScreen = true)
    val iconSize = Setting("home.iconSize", 1.0f, FloatCodec, perScreen = true)
    val rotateHome = Setting("home.rotate", false, BoolCodec)

    // Folders
    val folderIcon = Setting("folder.icon", FolderIcon.GRID2, EnumCodec(FolderIcon.entries.toTypedArray()))
    val folderOpen = Setting("folder.open", FolderOpen.POPUP, EnumCodec(FolderOpen.entries.toTypedArray()))
    val folderView = Setting("folder.view", FolderView.GRID, EnumCodec(FolderView.entries.toTypedArray()))
    /** Columns in an open folder; it sets how wide the window is. */
    val folderCols = Setting("folder.cols", 3, IntCodec)
    val folderRow = Setting("folder.row", RowDirection.HORIZONTAL, EnumCodec(RowDirection.entries.toTypedArray()))

    // Dock
    val dockPosition = Setting("dock.position", DockPosition.BOTTOM, EnumCodec(DockPosition.entries.toTypedArray()), perScreen = true)
    val dockLength = Setting("dock.length", DockLength.FULL, EnumCodec(DockLength.entries.toTypedArray()), perScreen = true)
    /** Where a "fit" side dock sits along its edge. */
    val dockAlign = Setting("dock.align", DockAlign.END, EnumCodec(DockAlign.entries.toTypedArray()), perScreen = true)
    /** Where the icons sit inside a full-length dock. (A fit dock always centers its icons.) */
    val dockIconAlign = Setting("dock.iconAlign", DockAlign.CENTER, EnumCodec(DockAlign.entries.toTypedArray()), perScreen = true)
    /** 0 means unlimited (the dock scrolls). */
    val dockIcons = Setting("dock.icons", 5, IntCodec, perScreen = true)
    val dockShape = Setting("dock.shape", DockShape.ROUNDED, EnumCodec(DockShape.entries.toTypedArray()))
    /** 0 is square. Phase 3 started at 4. */
    val dockRadius = Setting("dock.radius", 28, IntCodec)
    val dockBackground = Setting("dock.background", true, BoolCodec)
    val dockOutline = Setting("dock.outline", DockOutline.STRAIGHT, EnumCodec(DockOutline.entries.toTypedArray()))
    /** Off: the dock sits flush against the screen edge, like a taskbar. */
    val dockFloating = Setting("dock.floating", true, BoolCodec)
    val dockFill = Setting("dock.fill", DockFill.GLASS, EnumCodec(DockFill.entries.toTypedArray()))
    /** The gradient's second color (the first is the shared glass color). */
    val dockColor2 = Setting("dock.color2", 0xFF2546A8.toInt(), IntCodec)
    val dockPattern = Setting("dock.pattern", PatternStyle.NONE, EnumCodec(PatternStyle.entries.toTypedArray()))
    val dockPatternStrength = Setting("dock.patternStrength", 0.3f, FloatCodec)
    /** How big the pattern's shapes are, 0–1, with 0.5 the usual size. */
    val dockPatternSize = Setting("dock.patternScale", 0.5f, FloatCodec)
    val dockGradientDirection = Setting("dock.gradientDirection", GradientDirection.HORIZONTAL, EnumCodec(GradientDirection.entries.toTypedArray()))
    val dockEdge = Setting("dock.edge", DockEdge.SOFT, EnumCodec(DockEdge.entries.toTypedArray()))
    /** Extra room across the dock (above and below the icons on a bottom dock), in dp. */
    val dockPadding = Setting("dock.padding", 0, IntCodec)
    /** Extra room at the dock's two ends, in dp. */
    val dockPaddingEnds = Setting("dock.paddingEnds", 0, IntCodec)
    val swapHandle = Setting("dock.swapHandle", false, BoolCodec)
    /** Flipped by the swap handle or gesture: left ↔ right, top ↔ bottom, on every screen. */
    val dockSwapped = Setting("dock.swapped", false, BoolCodec)
    val labelsDock = Setting("dock.labels", false, BoolCodec)

    // App drawer
    val drawerOpen = Setting("drawer.open", DrawerOpen.SWIPE, EnumCodec(DrawerOpen.entries.toTypedArray()))
    val drawerButtonEnd = Setting("drawer.buttonEnd", ButtonEnd.START, EnumCodec(ButtonEnd.entries.toTypedArray()))
    val drawerButtonIcon = Setting("drawer.buttonIcon", ButtonIcon.QUARTZ, EnumCodec(ButtonIcon.entries.toTypedArray()))
    val drawerView = Setting("drawer.view", DrawerView.ICONS, EnumCodec(DrawerView.entries.toTypedArray()))
    val drawerScroll = Setting("drawer.scroll", DrawerScroll.VERTICAL, EnumCodec(DrawerScroll.entries.toTypedArray()))
    val drawerCols = Setting("drawer.cols", 4, IntCodec, perScreen = true)
    val drawerStyle = Setting("drawer.style", DrawerStyle.FULL, EnumCodec(DrawerStyle.entries.toTypedArray()), perScreen = true)
    val drawerSort = Setting("drawer.sort", DrawerSort.ALPHA, EnumCodec(DrawerSort.entries.toTypedArray()))
    val drawerTabs = Setting("drawer.tabs", false, BoolCodec)
    val drawerTabMode = Setting("drawer.tabMode", TabMode.NONE, EnumCodec(TabMode.entries.toTypedArray()))
    /** Columns fit the space on their own; off uses [drawerCols]. */
    val drawerColsAuto = Setting("drawer.colsAuto", true, BoolCodec)
    /** How much of the screen a floating, side or bottom drawer takes, 0.4–1. */
    val drawerSize = Setting("drawer.size", 0.8f, FloatCodec, perScreen = true)
    /** Letter (A to Z) or month (newest first) headings between apps. */
    val drawerDividers = Setting("drawer.dividers", false, BoolCodec)
    /** A strip of letters down the side of a scrolling drawer: slide along it to jump through your apps. */
    val drawerLetters = Setting("drawer.letters", true, BoolCodec)
    /** The drawer opens on the tab and spot you left it at, instead of the top of All apps. */
    val drawerRemember = Setting("drawer.remember", false, BoolCodec)
    /** Off: the dock stays on screen while the drawer is open. */
    val drawerHideDock = Setting("drawer.hideDock", false, BoolCodec, perScreen = true)
    val searchSettings = Setting("search.settings", true, BoolCodec)
    val searchContacts = Setting("search.contacts", false, BoolCodec)
    val labelsDrawer = Setting("drawer.labels", true, BoolCodec)

    // Appearance
    val panelColor = Setting("look.panelColor", 0xFF1E1B2E.toInt(), IntCodec)
    val panelOpacity = Setting("look.panelOpacity", 0.55f, FloatCodec)
    val panelBlur = Setting("look.panelBlur", 0.6f, FloatCodec)
    val iconsMatch = Setting("look.iconsMatch", true, BoolCodec)
    val iconShape = Setting("look.iconShape", IconShape.SYSTEM, EnumCodec(IconShape.entries.toTypedArray()))
    val iconStyle = Setting("look.iconStyle", IconStyle.DEFAULT, EnumCodec(IconStyle.entries.toTypedArray()))
    val iconBackground = Setting("look.iconBackground", IconBackground.NONE, EnumCodec(IconBackground.entries.toTypedArray()))
    /** The background color when [iconBackground] is "Color". */
    val iconBgColor = Setting("look.iconBgColor", 0xFF8C6CFF.toInt(), IntCodec)
    /** The background gradient's second color, and a pattern drawn over the background. */
    val iconBgColor2 = Setting("look.iconBgColor2", 0xFF2546A8.toInt(), IntCodec)
    val iconPattern = Setting("look.iconPattern", PatternStyle.NONE, EnumCodec(PatternStyle.entries.toTypedArray()))
    val iconPatternStrength = Setting("look.iconPatternStrength", 0.3f, FloatCodec)
    val iconPatternSize = Setting("look.iconPatternScale", 0.5f, FloatCodec)
    val iconGradient = Setting("look.iconGradient", GradientDirection.DIAGONAL, EnumCodec(GradientDirection.entries.toTypedArray()))
    /** A lit rim around each icon, like the dock's. */
    val iconEdge = Setting("look.iconEdge", DockEdge.NONE, EnumCodec(DockEdge.entries.toTypedArray()))
    /** A shadow under each icon. */
    val iconShadow = Setting("look.iconShadow", IconShadow.NONE, EnumCodec(IconShadow.entries.toTypedArray()))
    /** Cut every icon to the shape, even ones from an icon pack. */
    val iconForceShape = Setting("look.iconForceShape", false, BoolCodec)
    /** Icon pack package names; "" is the apps' own icons. Home is used everywhere when icons match. */
    val iconPackHome = Setting("look.iconPackHome", "", StringCodec)
    val iconPackDock = Setting("look.iconPackDock", "", StringCodec)
    val iconPackDrawer = Setting("look.iconPackDrawer", "", StringCodec)
    /** How big dock and drawer icons are (the home screen's size is per screen: [iconSize]). */
    val dockIconSize = Setting("dock.iconSize", 1.0f, FloatCodec)
    val drawerIconSize = Setting("drawer.iconSize", 1.0f, FloatCodec)
    /** The dock's and drawer's own icon style, used when icons don't match everywhere. Unset ones follow home. */
    val iconsDock = IconPlaceSettings("dock")
    val iconsDrawer = IconPlaceSettings("drawer")
    /** Notification badges; needs notification access, which you turn on yourself. */
    val badgesOn = Setting("look.badgesOn", false, BoolCodec)
    val badgeStyle = Setting("look.badgeStyle", BadgeStyle.DOT, EnumCodec(BadgeStyle.entries.toTypedArray()))
    val badgeThreshold1 = Setting("look.badgeThreshold1", 10, IntCodec)
    val badgeThreshold2 = Setting("look.badgeThreshold2", 50, IntCodec)
    /** The widget picked in setup (a WidgetKind name, or "" for none). It's added to your home page when setup ends. */
    val widgets = Setting("look.widgets", "", StringCodec)
    val clock24h = Setting("look.clock24h", false, BoolCodec)

    // Widgets
    /** The flip clock's cards. */
    val widgetColor = Setting("widget.color", 0xFFBF5AA6.toInt(), IntCodec)
    /** The clock's date and the buttons on the tips card: their own color, apart from the flip cards. */
    val widgetAccent = Setting("widget.accent", 0xFFBF5AA6.toInt(), IntCodec)
    /** Flip cards turning over, and animated weather icons. Off: they change in place and the icons stay still. */
    val widgetMotion = Setting("widget.motion", true, BoolCodec)
    val weatherSource = Setting("weather.source", WeatherSource.CITY, EnumCodec(WeatherSource.entries.toTypedArray()))
    /** The city you chose: its name as shown, and where it is (kept as text, rounded to about 1 km). */
    val weatherCity = Setting("weather.city", "", StringCodec)
    val weatherLat = Setting("weather.lat", "", StringCodec)
    val weatherLon = Setting("weather.lon", "", StringCodec)
    val weatherUnits = Setting("weather.units", TempUnit.F, EnumCodec(TempUnit.entries.toTypedArray()))

    // Setup
    val wizardDone = Setting("setup.wizardDone", false, BoolCodec)
    /** Where you were in setup (a step's name, and the path and customize choices), so it picks up from there. */
    val wizardStep = Setting("setup.step", "", StringCodec)
    val wizardPath = Setting("setup.path", "", StringCodec)
    val wizardCustomize = Setting("setup.customize", "", StringCodec)

    fun gesture(slot: GestureSlot) = gestures.getValue(slot)

    private val gestureDefaults = mapOf(
        GestureSlot.SWIPE_UP to GestureAction.APP_DRAWER,
        GestureSlot.SWIPE_DOWN to GestureAction.NOTIFICATIONS,
        GestureSlot.PINCH to GestureAction.OVERVIEW,
    )
    private val gestures: Map<GestureSlot, Setting<GestureAction>> = GestureSlot.entries.associateWith { slot ->
        Setting(
            "gesture.${slot.name.lowercase()}",
            gestureDefaults[slot] ?: GestureAction.NONE,
            EnumCodec(GestureAction.entries.toTypedArray()),
        )
    }

    /** Every per-screen setting, for counting what's synced vs split. */
    val perScreenSettings: List<Setting<*>> = listOf(
        gridCols, gridRows, labelsHome, iconSize, dockPosition, dockLength, dockAlign, dockIconAlign, dockIcons, drawerCols, drawerStyle,
        drawerSize,
    )
}

/**
 * The settings store. One instance per process (the home screen and settings share it), backed by
 * SharedPreferences and mirrored in Compose state so every screen updates the moment a value changes.
 */
class SettingsStore private constructor(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val values = mutableStateMapOf<String, String>()

    init {
        prefs.all.forEach { (k, v) -> if (v is String) values[k] = v }
    }

    private fun key(scope: Scope, setting: Setting<*>) = "${scope.name}/${setting.id}"

    /** The scope a value is stored at for [setting] when seen from [scope] (non-screen settings are always global). */
    private fun effective(setting: Setting<*>, scope: Scope) = if (setting.perScreen) scope else Scope.GLOBAL

    operator fun <T> get(setting: Setting<T>, scope: Scope = Scope.GLOBAL): T {
        var s: Scope? = effective(setting, scope)
        while (s != null) {
            values[key(s, setting)]?.let { text -> setting.codec.decode(text)?.let { return it } }
            s = s.parent
        }
        return setting.default
    }

    /** The nearest scope (from [scope] upward) that holds its own value, or null if it's all defaults. */
    fun owner(setting: Setting<*>, scope: Scope): Scope? {
        var s: Scope? = effective(setting, scope)
        while (s != null) {
            if (values.containsKey(key(s, setting))) return s
            s = s.parent
        }
        return null
    }

    /** True when [scope] follows its parent instead of having its own value. */
    fun isLinked(setting: Setting<*>, scope: Scope): Boolean =
        !setting.perScreen || scope.parent == null || !values.containsKey(key(scope, setting))

    /**
     * Changes a value as seen from [scope]. A linked screen edits the shared value it follows, so every linked
     * screen changes together; an unlinked screen edits only itself.
     */
    fun <T> set(setting: Setting<T>, scope: Scope = Scope.GLOBAL, value: T) {
        val target = if (!setting.perScreen) Scope.GLOBAL else owner(setting, scope) ?: Scope.GLOBAL
        put(key(target, setting), setting.codec.encode(value))
    }

    /** Writes a value at exactly [scope], unlinking it if needed. Used by setup when screens differ. */
    fun <T> setAt(setting: Setting<T>, scope: Scope, value: T) {
        put(key(effective(setting, scope), setting), setting.codec.encode(value))
    }

    /** "Only this screen": copies the current value down so this screen can change on its own. */
    fun unlink(setting: Setting<*>, scope: Scope) {
        if (!setting.perScreen || scope.parent == null) return
        @Suppress("UNCHECKED_CAST")
        val s = setting as Setting<Any?>
        put(key(scope, s), s.codec.encode(get(s, scope)))
    }

    /** "Synced": drops this screen's own value so it follows its parent again. */
    fun link(setting: Setting<*>, scope: Scope) {
        if (scope.parent == null) return
        val k = key(scope, setting)
        values.remove(k)
        prefs.edit().remove(k).apply()
    }

    /** Clears any per-screen values for [setting] on these screens so they all follow one shared value. */
    fun linkAll(setting: Setting<*>, scopes: List<Scope>) {
        scopes.forEach { s ->
            link(setting, s)
            s.parent?.takeIf { it != Scope.GLOBAL }?.let { link(setting, it) }
        }
    }

    private fun put(k: String, v: String) {
        values[k] = v
        prefs.edit().putString(k, v).apply()
    }

    // ---- Profiles ----

    fun exportJson(): String {
        val json = JSONObject()
        json.put("format", "quartz-launcher-settings")
        json.put("version", 1)
        val data = JSONObject()
        values.forEach { (k, v) -> data.put(k, v) }
        json.put("settings", data)
        return json.toString(2)
    }

    /** Imports settings from another device. Values for this device's own screens are kept unless overwritten. */
    fun importJson(text: String): Int {
        val json = JSONObject(text)
        require(json.optString("format") == "quartz-launcher-settings") { "Not a Quartz settings file" }
        val data = json.getJSONObject("settings")
        var count = 0
        val edit = prefs.edit()
        data.keys().forEach { k ->
            // Setup's own progress belongs to this device
            if (listOf(S.wizardDone, S.wizardStep, S.wizardPath, S.wizardCustomize).any { k.endsWith("/" + it.id) }) return@forEach
            val v = data.getString(k)
            values[k] = v
            edit.putString(k, v)
            count++
        }
        edit.apply()
        return count
    }

    fun resetAll() {
        values.clear()
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE = "quartz_settings"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) { instance ?: SettingsStore(context).also { instance = it } }
    }
}

/**
 * Dock icon counts as (folded or only screen, unfolded). 0 means unlimited. Unfolded never has fewer than folded:
 * the extra icons are added after your folded ones. Until it's set on its own, unfolded doubles a top or bottom
 * dock (twice the width) and matches a side dock.
 */
/**
 * Carries phase 3's dock shape over to the new settings once: "No background" turns the background off and
 * "Square" sets roundness to 0.
 */
fun SettingsStore.migrateDockShape() {
    if (owner(S.dockShape, Scope.GLOBAL) == null || owner(S.dockBackground, Scope.GLOBAL) != null) return
    val old = this[S.dockShape]
    set(S.dockBackground, value = old != DockShape.NONE)
    if (old == DockShape.SQUARE) set(S.dockRadius, value = 0)
}

/** Where the dock really is on [scope]: its position, flipped if the dock has been swapped. */
fun SettingsStore.dockPositionOn(scope: Scope): DockPosition {
    val p = this[S.dockPosition, scope]
    if (!this[S.dockSwapped]) return p
    return when (p) {
        DockPosition.BOTTOM -> DockPosition.TOP
        DockPosition.TOP -> DockPosition.BOTTOM
        DockPosition.LEFT -> DockPosition.RIGHT
        DockPosition.RIGHT -> DockPosition.LEFT
        DockPosition.NONE -> DockPosition.NONE
    }
}

/** A tablet starts with a 7 × 6 grid (set for landscape) until you choose your own. */
fun SettingsStore.ensureGridDefaults(type: DeviceType) {
    if (type == DeviceType.TABLET && owner(S.gridCols, Scope.TABLET) == null) {
        setAt(S.gridCols, Scope.TABLET, 7)
        setAt(S.gridRows, Scope.TABLET, 6)
    }
}

/** The accent used to be the flip cards' color: someone who picked a card color keeps it on the date too. */
fun SettingsStore.ensureWidgetAccent() {
    if (owner(S.widgetAccent, Scope.GLOBAL) == null && owner(S.widgetColor, Scope.GLOBAL) != null) {
        set(S.widgetAccent, value = this[S.widgetColor])
    }
}

/**
 * Setup keeps coming back until it's finished: whenever the home screen shows, unless you backed out of it in the
 * last minute (so you can step away briefly, to answer a message or allow something).
 */
object SetupNag {
    private var lastOpened = 0L
    private var backedOutAt = -60_000L
    var current: java.lang.ref.WeakReference<android.app.Activity>? = null

    fun due(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        return now - lastOpened > 3_000 && now - backedOutAt > 60_000
    }

    fun opened() {
        lastOpened = android.os.SystemClock.elapsedRealtime()
    }

    fun backedOut() {
        backedOutAt = android.os.SystemClock.elapsedRealtime()
    }
}

fun SettingsStore.dockCounts(type: DeviceType): Pair<Int, Int> {
    val folded = this[S.dockIcons, type.primary]
    if (type != DeviceType.FOLDABLE) return folded to folded
    if (folded == 0) return 0 to 0
    val inner = if (isLinked(S.dockIcons, Scope.INNER)) {
        val p = this[S.dockPosition, Scope.INNER]
        if (p == DockPosition.BOTTOM || p == DockPosition.TOP) folded * 2 else folded
    } else {
        this[S.dockIcons, Scope.INNER]
    }
    return folded to if (inner == 0) 0 else maxOf(inner, folded)
}

/** A pattern size slider's label: 100% is the usual size, in the middle; it runs from about 35% to about 280%. */
fun patternSizeLabel(scale: Float): String =
    "${(Math.pow(2.0, ((scale.coerceIn(0f, 1f) - 0.5f) * 3f).toDouble()) * 100).roundToInt()}%"
