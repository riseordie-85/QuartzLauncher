package com.tim.launcher.settings

import android.content.Context
import com.tim.launcher.IconRender
import com.tim.launcher.WidgetKind
import com.tim.launcher.wallpaper.FoldMode
import com.tim.launcher.wallpaper.GradientStyle
import com.tim.launcher.wallpaper.PatternAnchor
import com.tim.launcher.wallpaper.PatternStyle
import com.tim.launcher.wallpaper.WallPresets
import com.tim.launcher.wallpaper.WallSlot
import com.tim.launcher.wallpaper.WallStore

/**
 * A ready-made set of look settings (a dock preset, an icon preset). Anything it doesn't name is at its default,
 * so picking one gives exactly what its picture shows.
 */
class Look(internal val values: Map<Setting<*>, Any>) : IconRender.Values {
    /** This look with [other]'s values on top. */
    operator fun plus(other: Look) = Look(values + other.values)

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(setting: Setting<T>): T = (values[setting] as T?) ?: setting.default

    override fun <T> place(own: Setting<T>?, home: Setting<T>): T = if (own != null && values.containsKey(own)) get(own) else get(home)
}

/** A setting and its value, typed so a preset can't hold the wrong kind of value. */
private infix fun <T : Any> Setting<T>.of(value: T): Pair<Setting<*>, Any> = this to value

private fun look(vararg values: Pair<Setting<*>, Any>) = Look(mapOf(*values))

/** A named starting point. */
data class Preset(val name: String, val look: Look)

// ---- Dock ----

object DockPresets {
    /** Everything a dock preset sets: the dock's shape and fill, and the glass it shares with the drawer and folders. */
    val keys: List<Setting<*>> = listOf(
        S.dockBackground, S.dockOutline, S.dockRadius, S.dockFloating, S.dockFill, S.dockColor2, S.dockPattern,
        S.dockPatternStrength, S.dockPatternSize, S.dockPatternWeight, S.dockPatternAnchor, S.dockPatternDark, S.dockGradientDirection, S.dockEdge,
        S.dockDepth, S.dockStroke, S.dockStrokeColor, S.dockStrokeWidth, S.dockPadding, S.dockPaddingEnds,
        S.panelColor, S.panelOpacity, S.panelBlur,
    )

    val glass = Preset("Glass", look())
    val taskbar = Preset(
        "Taskbar",
        look(S.dockRadius of 0, S.dockFloating of false, S.dockEdge of DockEdge.HAIRLINE, S.panelColor of 0xFF0E0E14.toInt(), S.panelOpacity of 0.82f, S.panelBlur of 0.4f),
    )
    val pill = Preset(
        "Pill",
        look(S.dockRadius of 40, S.dockPaddingEnds of 10, S.dockEdge of DockEdge.BRIGHT, S.panelColor of 0xFFFFFFFF.toInt(), S.panelOpacity of 0.16f, S.panelBlur of 0.85f),
    )
    val wave = Preset(
        "Wave",
        look(
            S.dockOutline of DockOutline.WAVE, S.dockFloating of false, S.dockFill of DockFill.GRADIENT, S.panelColor of 0xFF0F5C7A.toInt(),
            S.dockColor2 of 0xFF4FB3BF.toInt(), S.panelOpacity of 0.72f, S.panelBlur of 0.5f,
        ),
    )
    val ribbon = Preset(
        "Ribbon",
        look(
            S.dockOutline of DockOutline.RIBBON, S.dockFill of DockFill.GRADIENT, S.panelColor of 0xFF6B2D6E.toInt(), S.dockColor2 of 0xFFE07A5F.toInt(),
            S.panelOpacity of 0.9f, S.dockStroke of StrokeStyle.SOLID, S.dockStrokeColor of 0xB3FFFFFF.toInt(), S.dockStrokeWidth of 1.5f,
        ),
    )
    val crystal = Preset(
        "Crystal",
        look(
            S.dockOutline of DockOutline.SLANTED, S.dockRadius of 20, S.dockFill of DockFill.GRADIENT, S.panelColor of 0xFFA4509E.toInt(),
            S.dockColor2 of 0xFF2E0F45.toInt(), S.dockGradientDirection of GradientDirection.DIAGONAL, S.panelOpacity of 0.75f,
            S.dockPattern of PatternStyle.CRYSTAL, S.dockEdge of DockEdge.BRIGHT,
        ),
    )
    val neon = Preset(
        "Neon",
        look(
            S.dockRadius of 24, S.panelColor of 0xFF0A0A12.toInt(), S.panelOpacity of 0.85f, S.panelBlur of 0.3f, S.dockEdge of DockEdge.NONE,
            S.dockStroke of StrokeStyle.SOLID, S.dockStrokeColor of 0xFF8C6CFF.toInt(), S.dockStrokeWidth of 2f,
        ),
    )
    val paper = Preset(
        "Paper",
        look(
            S.dockRadius of 20, S.panelColor of 0xFFF4F1EA.toInt(), S.panelOpacity of 0.88f, S.panelBlur of 0.3f, S.dockEdge of DockEdge.NONE,
            S.dockStroke of StrokeStyle.PEN, S.dockStrokeColor of 0xCC2A2046.toInt(), S.dockStrokeWidth of 3f,
        ),
    )
    val bare = Preset("No background", look(S.dockBackground of false))

    val all = listOf(glass, taskbar, pill, wave, ribbon, crystal, neon, paper, bare)

    fun matches(store: SettingsStore, preset: Preset) = keys.all { store[it] == preset.look.get(it) }
}

// ---- Icons ----

object IconPresets {
    /** Everything an icon preset sets. Icon packs (and "use the shape on every icon", which is about packs) stay yours. */
    val keys: List<Setting<*>> = listOf(
        S.iconShape, S.iconStyle, S.iconBackground, S.iconBgColor, S.iconBgColor2, S.iconPattern, S.iconPatternStrength,
        S.iconPatternSize, S.iconPatternWeight, S.iconPatternAnchor, S.iconPatternDark, S.iconGradient, S.iconEdge, S.iconStroke, S.iconStrokeColor,
        S.iconStrokeWidth, S.iconShadow,
    )

    val own = Preset("As they come", look())
    val circles = Preset("Circles", look(S.iconShape of IconShape.CIRCLE, S.iconShadow of IconShadow.SOFT))
    val frosted = Preset(
        "Frosted",
        look(S.iconShape of IconShape.SQUIRCLE, S.iconBackground of IconBackground.FROSTED, S.iconBgColor of 0x38FFFFFF, S.iconEdge of DockEdge.SOFT),
    )
    val oneColor = Preset("One color", look(S.iconShape of IconShape.SQUIRCLE, S.iconStyle of IconStyle.MINIMAL))
    val darkTiles = Preset(
        "Dark tiles",
        look(S.iconShape of IconShape.ROUNDED, S.iconBackground of IconBackground.DARK, S.iconEdge of DockEdge.HAIRLINE, S.iconShadow of IconShadow.SOFT),
    )
    val gems = Preset(
        "Gems",
        look(
            S.iconShape of IconShape.SQUIRCLE, S.iconStyle of IconStyle.MINIMAL, S.iconBackground of IconBackground.GRADIENT,
            S.iconBgColor of 0xFFA4509E.toInt(), S.iconBgColor2 of 0xFF2E0F45.toInt(), S.iconGradient of GradientDirection.DIAGONAL,
            S.iconPattern of PatternStyle.DIAMONDS, S.iconPatternStrength of 0.25f, S.iconEdge of DockEdge.BRIGHT,
        ),
    )
    val rings = Preset(
        "Rings",
        look(
            S.iconShape of IconShape.CIRCLE, S.iconStyle of IconStyle.MINIMAL, S.iconBackground of IconBackground.GRADIENT,
            S.iconBgColor of 0xFF1A7A96.toInt(), S.iconBgColor2 of 0xFF061A2B.toInt(), S.iconGradient of GradientDirection.RADIAL,
            S.iconPattern of PatternStyle.RINGS, S.iconPatternStrength of 0.3f, S.iconPatternAnchor of PatternAnchor.CENTER, S.iconEdge of DockEdge.SOFT,
        ),
    )
    val paper = Preset(
        "Paper",
        look(
            S.iconShape of IconShape.ROUNDED, S.iconBackground of IconBackground.LIGHT, S.iconStroke of StrokeStyle.PEN,
            S.iconStrokeColor of 0xCC2A2046.toInt(), S.iconStrokeWidth of 2f,
        ),
    )
    val neon = Preset(
        "Neon",
        look(
            S.iconShape of IconShape.SQUIRCLE, S.iconStyle of IconStyle.MINIMAL, S.iconBackground of IconBackground.DARK,
            S.iconStroke of StrokeStyle.SOLID, S.iconStrokeColor of 0xFF8C6CFF.toInt(), S.iconStrokeWidth of 1.5f,
        ),
    )

    val all = listOf(own, circles, frosted, oneColor, darkTiles, gems, rings, paper, neon)

    /** Whether [v] (the saved icons, or changes waiting to be applied) looks like [preset] everywhere. */
    fun matches(v: IconRender.Values, preset: Preset) = v.get(S.iconsMatch) && keys.all { v.get(it) == preset.look.get(it) }

    /** Puts [preset] into the icon changes waiting to be applied, for every icon ("Match everywhere" on). */
    fun pick(draft: IconDraft, preset: Preset) {
        keys.forEach { draft.copy(it, preset.look) }
        draft.set(S.iconsMatch, true)
    }

    @Suppress("UNCHECKED_CAST")
    private fun IconDraft.copy(setting: Setting<*>, from: Look) {
        val s = setting as Setting<Any>
        set(s, from.get(s))
    }
}

/** Sets every one of [keys] to [look]'s value (its default where the look doesn't name it). */
fun SettingsStore.applyLook(keys: List<Setting<*>>, look: Look) {
    @Suppress("UNCHECKED_CAST")
    keys.forEach { k -> (k as Setting<Any?>).let { set(it, value = look.get(it)) } }
}

// ---- Gradients ----

/** Two-color gradients for the dock and icon plates, taken from the wallpaper presets so they match. */
object GradientPresets {
    data class Gradient(val name: String, val from: Int, val to: Int)

    val all: List<Gradient> = WallPresets.all.map { p ->
        val c = p.config
        // The colors each kind of wallpaper gradient actually shows
        val used = when (c.gradient) {
            GradientStyle.LINEAR -> if (c.linearMiddle) listOf(c.color(0), c.color(1), c.color(2)) else listOf(c.color(0), c.color(2))
            GradientStyle.RADIAL -> listOf(c.color(0), c.color(2))
            GradientStyle.CORNERS -> (0 until 5).map { c.color(it) }
        }
        val from = used.first()
        // To the color furthest from the first, so every pair is a real gradient
        val to = used.maxBy { distance(from, it) }
        Gradient(p.name, from or 0xFF000000.toInt(), to or 0xFF000000.toInt())
    }

    private fun distance(a: Int, b: Int): Int {
        val dr = (a shr 16 and 0xFF) - (b shr 16 and 0xFF)
        val dg = (a shr 8 and 0xFF) - (b shr 8 and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }
}

// ---- Home screen themes ----

/**
 * A whole home screen to start from: its look (dock, icons, widget colors, fonts…), a wallpaper and a widget.
 * Most are theme files kept in the app (assets/themes, made with Export theme); Crystal is built from presets.
 */
data class HomeTheme(
    val name: String,
    val about: String,
    val widget: WidgetKind,
    /** Every look setting it sets (anything else at its default). */
    val look: Look,
    val wallConfig: com.tim.launcher.wallpaper.WallConfig,
    /** Its file in assets/themes, without ".json" (null: built here). */
    val file: String? = null,
    /** Its wallpaper photo and fonts, in assets/themes/files. */
    val wallImage: String? = null,
    val fontFiles: List<String> = emptyList(),
) {
    val cards: Int get() = look.get(S.widgetColor)
    val accent: Int get() = look.get(S.widgetAccent)
}

object HomeThemes {
    private val crystal: HomeTheme
        get() = HomeTheme(
            "Crystal", "Frosted icons over amethyst, with a cut-crystal dock", WidgetKind.FLIP,
            DockPresets.crystal.look + IconPresets.frosted.look +
                Look(mapOf<Setting<*>, Any>(S.widgetColor to 0xFFA4509E.toInt(), S.widgetAccent to 0xFFE9B8F2.toInt())),
            (WallPresets.all.firstOrNull { it.name == "Amethyst" } ?: WallPresets.all.first()).config,
        )

    @Volatile
    private var loaded: List<HomeTheme>? = null

    /** Every theme, in the order they're shown: Crystal, then the files in assets/themes/index.json. */
    fun all(context: Context): List<HomeTheme> = loaded ?: synchronized(this) {
        loaded ?: runCatching { files(context) }
            .onFailure { com.tim.launcher.Diag.log("Themes: ${it.message}") }
            // (Not kept when the list couldn't be read, so it's tried again)
            .fold({ (listOf(crystal) + it).also { list -> loaded = list } }, { listOf(crystal) })
    }

    private fun files(context: Context): List<HomeTheme> {
        val index = org.json.JSONArray(context.assets.open("themes/index.json").bufferedReader().readText())
        return (0 until index.length()).mapNotNull { i ->
            val entry = index.optJSONObject(i)
            val file = entry?.optString("file").orEmpty()
            if (entry == null || file.isEmpty()) return@mapNotNull null
            runCatching {
                val json = org.json.JSONObject(context.assets.open("themes/$file.json").bufferedReader().readText())
                val main = json.optJSONObject("wallpaper")?.optJSONObject("main")
                val wall = main?.let { com.tim.launcher.wallpaper.WallConfig.fromJson(it.toString()) } ?: WallPresets.all.first().config
                HomeTheme(
                    name = entry.optString("name", json.optString("name")),
                    about = entry.optString("about"),
                    widget = runCatching { WidgetKind.valueOf(entry.optString("widget", "FLIP")) }.getOrDefault(WidgetKind.FLIP),
                    look = ThemeFile.lookOf(json.optJSONObject("settings") ?: org.json.JSONObject()),
                    wallConfig = wall,
                    file = file,
                    wallImage = wall.image,
                    fontFiles = json.optJSONArray("assetFonts")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                )
            }.onFailure { com.tim.launcher.Diag.log("Theme $file: ${it.message}") }.getOrNull()
        }
    }

    /** A theme file's photo and fonts written ahead (off the main thread), for [apply]; null for Crystal. */
    fun stage(context: Context, theme: HomeTheme): ThemeFile.Staged? = theme.file?.let { ThemeFile.stageAsset(context, it) }

    /**
     * Puts [theme] in place: its look and wallpaper straight away, the widget for when setup ends. Crystal's icons go
     * into [draft] (applied with the "Styling your icons" popup, so nothing flickers); a theme file sets them itself.
     * [staged]: from [stage], when that was done ahead.
     */
    fun apply(context: Context, store: SettingsStore, draft: IconDraft, theme: HomeTheme, staged: ThemeFile.Staged? = null) {
        if (theme.file != null) {
            ThemeFile.finish(context, staged ?: ThemeFile.stageAsset(context, theme.file))
            draft.discard()
        } else {
            // Nothing left over from a theme used before this one (fonts, weather icons, badges, the dock's and
            // drawer's own icons): what Crystal doesn't set goes back to its default
            ThemeFile.clearRest(store, theme.look, DockPresets.keys + IconPresets.keys + S.iconsMatch)
            store.applyLook(DockPresets.keys, theme.look)
            IconPresets.keys.forEach { k -> @Suppress("UNCHECKED_CAST") draft.set(k as Setting<Any>, theme.look.get(k) as Any) }
            draft.set(S.iconsMatch, true)
            val current = WallStore.load(context, WallSlot.MAIN)
            WallStore.save(context, WallSlot.MAIN, applyPreset(current, theme.wallConfig))
            // A fold with its own cover wallpaper gets the theme's there too
            if (WallStore.mode(context) == FoldMode.SEPARATE) {
                WallStore.save(context, WallSlot.COVER, applyPreset(WallStore.load(context, WallSlot.COVER), theme.wallConfig))
            }
            store.set(S.widgetColor, value = theme.cards)
            store.set(S.widgetAccent, value = theme.accent)
        }
        store.set(S.widgets, value = theme.widget.name)
    }
}
