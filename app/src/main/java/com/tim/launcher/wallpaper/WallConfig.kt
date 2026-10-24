package com.tim.launcher.wallpaper

import android.content.Context
import com.tim.launcher.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class WallKind(val label: String) { GRADIENT("Gradient"), SOLID("Color"), IMAGE("Image"), TEST("Test") }

enum class GradientStyle(val label: String) { LINEAR("Linear"), RADIAL("Radial"), CORNERS("Corners") }

enum class PatternStyle(val label: String, val faceted: Boolean = false) {
    NONE("None"),
    CRYSTAL("Crystal", true),
    RIPPLE("Ripple", true),
    DIAMONDS("Diamonds", true),
    PYRAMIDS("Pyramids", true),
    SHARDS("Shards", true),
    DOTS("Dots"), GRID("Grid"), STRIPES("Stripes"), CHECKS("Checks"), WAVES("Waves"),
}

/** How one wallpaper spans a folding phone's two screens. */
enum class FoldMode(val id: String, val label: String, val about: String) {
    MATCHED("matched", "Shared · cropped", "One wallpaper. The cover screen shows its half of the unfolded one."),
    STRETCHED("independent", "Shared · stretched", "One wallpaper, fitted to each screen on its own."),
    SEPARATE("separate", "Independent", "A different wallpaper for the cover and unfolded screens."),
    ;

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: MATCHED
    }
}

/** Which saved wallpaper: the main one, or the cover screen's own (only used in [FoldMode.SEPARATE]). */
enum class WallSlot(val key: String) { MAIN(Prefs.WP_MAIN), COVER(Prefs.WP_COVER) }

/**
 * One wallpaper. Gradient colors: linear uses [colors] 0 (start), 1 (middle, if [linearMiddle]) and 2 (end); radial
 * uses 0 (center) and 2 (edge); corners uses all five: top left, top right, bottom left, bottom right, middle.
 */
data class WallConfig(
    val kind: WallKind = WallKind.GRADIENT,
    val solid: Int = 0xFF1E2A4A.toInt(),
    val gradient: GradientStyle = GradientStyle.LINEAR,
    val colors: List<Int> = listOf(0xFF4B1D7A.toInt(), 0xFF2546A8.toInt(), 0xFF138A8A.toInt(), 0xFF4E9F3D.toInt(), 0xFF2E5EA8.toInt()),
    val linearMiddle: Boolean = true,
    /** Degrees; 0 runs left to right, 90 top to bottom. */
    val angle: Float = 135f,
    val radialX: Float = 0.5f,
    val radialY: Float = 0.4f,
    val radialSize: Float = 0.8f,
    /** File name in the app's private wallpaper folder. */
    val image: String? = null,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val zoom: Float = 1f,
    val pattern: PatternStyle = PatternStyle.NONE,
    val patternColor: Int = 0xFFFFFFFF.toInt(),
    val patternStrength: Float = 0.12f,
    val patternSize: Float = 0.5f,
    /** Picks a different arrangement for patterns with randomness in them (Crystal, Shards, sparkles). */
    val patternSeed: Int = 0,
    /** Tiny glints over the pattern. */
    val sparkles: Boolean = false,
    /** Darkens the whole wallpaper, 0–0.7, so icons and labels stand out. */
    val dim: Float = 0f,
) {
    fun color(i: Int): Int = colors.getOrElse(i) { colors.lastOrNull() ?: 0xFF000000.toInt() }

    fun withColor(i: Int, c: Int): WallConfig {
        val list = (0 until 5).map { color(it) }.toMutableList()
        list[i] = c
        return copy(colors = list)
    }

    fun toJson(): String = JSONObject().apply {
        put("kind", kind.name)
        put("solid", solid)
        put("gradient", gradient.name)
        put("colors", JSONArray(colors))
        put("linearMiddle", linearMiddle)
        put("angle", angle.toDouble())
        put("radialX", radialX.toDouble())
        put("radialY", radialY.toDouble())
        put("radialSize", radialSize.toDouble())
        image?.let { put("image", it) }
        put("focusX", focusX.toDouble())
        put("focusY", focusY.toDouble())
        put("zoom", zoom.toDouble())
        put("pattern", pattern.name)
        put("patternColor", patternColor)
        put("patternStrength", patternStrength.toDouble())
        put("patternSize", patternSize.toDouble())
        put("patternSeed", patternSeed)
        put("sparkles", sparkles)
        put("dim", dim.toDouble())
    }.toString()

    companion object {
        /** The look Quartz starts with: the violet-to-green gradient from the test wallpaper. */
        val Default = WallConfig()

        fun fromJson(text: String?): WallConfig? = runCatching {
            if (text.isNullOrBlank()) return null
            val j = JSONObject(text)
            val d = Default
            fun f(name: String, def: Float) = j.optDouble(name, def.toDouble()).toFloat()
            val arr = j.optJSONArray("colors")
            WallConfig(
                kind = WallKind.entries.firstOrNull { it.name == j.optString("kind") } ?: d.kind,
                solid = j.optInt("solid", d.solid),
                gradient = GradientStyle.entries.firstOrNull { it.name == j.optString("gradient") } ?: d.gradient,
                colors = if (arr != null && arr.length() > 0) (0 until arr.length()).map { arr.getInt(it) } else d.colors,
                linearMiddle = j.optBoolean("linearMiddle", d.linearMiddle),
                angle = f("angle", d.angle),
                radialX = f("radialX", d.radialX),
                radialY = f("radialY", d.radialY),
                radialSize = f("radialSize", d.radialSize),
                image = j.optString("image").takeIf { it.isNotEmpty() },
                focusX = f("focusX", d.focusX),
                focusY = f("focusY", d.focusY),
                zoom = f("zoom", d.zoom),
                pattern = PatternStyle.entries.firstOrNull { it.name == j.optString("pattern") } ?: d.pattern,
                patternColor = j.optInt("patternColor", d.patternColor),
                patternStrength = f("patternStrength", d.patternStrength),
                patternSize = f("patternSize", d.patternSize),
                patternSeed = j.optInt("patternSeed", d.patternSeed),
                sparkles = j.optBoolean("sparkles", d.sparkles),
                dim = f("dim", d.dim),
            )
        }.getOrNull()
    }
}

/** Ready-made gradients for settings and setup. */
object WallPresets {
    data class Preset(val name: String, val config: WallConfig)

    private fun linear(name: String, angle: Float, vararg c: Long) = Preset(
        name,
        WallConfig(
            kind = WallKind.GRADIENT,
            gradient = GradientStyle.LINEAR,
            colors = listOf(c[0].toInt(), c[1].toInt(), c[2].toInt(), c[2].toInt(), c[1].toInt()),
            linearMiddle = true,
            angle = angle,
        ),
    )

    private fun corners(name: String, vararg c: Long) = Preset(
        name,
        WallConfig(kind = WallKind.GRADIENT, gradient = GradientStyle.CORNERS, colors = c.map { it.toInt() }),
    )

    private fun radial(name: String, center: Long, edge: Long, y: Float, size: Float) = Preset(
        name,
        WallConfig(
            kind = WallKind.GRADIENT,
            gradient = GradientStyle.RADIAL,
            colors = listOf(center.toInt(), center.toInt(), edge.toInt(), edge.toInt(), center.toInt()),
            radialX = 0.5f,
            radialY = y,
            radialSize = size,
        ),
    )

    private fun Preset.cut(style: PatternStyle, strength: Float, size: Float, sparkles: Boolean = false) =
        copy(config = config.copy(pattern = style, patternColor = 0xFFFFFFFF.toInt(), patternStrength = strength, patternSize = size, sparkles = sparkles))

    val all: List<Preset> = listOf(
        Preset("Quartz", WallConfig.Default),
        // Quartz stones, cut: each brings its own pattern
        radial("Blue quartz", 0xFF1E63E0, 0xFF02081A, 0.45f, 0.85f).cut(PatternStyle.RIPPLE, 0.3f, 0.2f),
        radial("Citrine", 0xFFE8A21E, 0xFF7A4306, 0.4f, 1.25f).cut(PatternStyle.CRYSTAL, 0.42f, 0.3f),
        radial("Amethyst", 0xFFA4509E, 0xFF2E0F45, 0.42f, 0.95f).cut(PatternStyle.DIAMONDS, 0.2f, 0.35f, sparkles = true),
        corners("Rose quartz", 0xFF7A2A6A, 0xFFD0566E, 0xFF3A1A6E, 0xFF2C34A8, 0xFFB8346E).cut(PatternStyle.PYRAMIDS, 0.42f, 0.45f),
        linear("Smoky quartz", 90f, 0xFF2A2046, 0xFF33284A, 0xFF1D1838).cut(PatternStyle.SHARDS, 0.2f, 0.5f),
        linear("Dusk", 160f, 0xFF1B1035, 0xFF6B2D6E, 0xFFE07A5F),
        linear("Ocean", 120f, 0xFF061A2B, 0xFF0F5C7A, 0xFF4FB3BF),
        linear("Forest", 100f, 0xFF0B1F14, 0xFF1F5135, 0xFF8DB36B),
        corners("Aurora", 0xFF14203F, 0xFF1D6A6A, 0xFF3A1C5C, 0xFF0E3B2E, 0xFF3F8F7A),
        corners("Ember", 0xFF2A0F1F, 0xFF7A2A1F, 0xFF140A1A, 0xFFB5582A, 0xFF5A1E2E),
        linear("Graphite", 90f, 0xFF15161A, 0xFF24262D, 0xFF3A3D47),
        corners("Frost", 0xFFE8EEF5, 0xFFD5E3F0, 0xFFF4F1EA, 0xFFDCE7E3, 0xFFFFFFFF),
    )
}

/** Where wallpapers are saved: settings in the launcher's prefs, images in a private folder. */
object WallStore {
    fun load(context: Context, slot: WallSlot): WallConfig {
        val prefs = Prefs.get(context)
        return WallConfig.fromJson(prefs.getString(slot.key, null))
            ?: if (slot == WallSlot.COVER) load(context, WallSlot.MAIN) else WallConfig.Default
    }

    fun save(context: Context, slot: WallSlot, config: WallConfig) {
        val prefs = Prefs.get(context)
        val before = WallConfig.fromJson(prefs.getString(slot.key, null))?.image
        prefs.edit().putString(slot.key, config.toJson()).apply()
        // Only look for unused photos when the photo changed, not on every slider step
        if (before != config.image) cleanImages(context)
        WallCache.changed()
    }

    fun mode(context: Context): FoldMode = FoldMode.of(Prefs.get(context).getString(Prefs.WP_MODE, null))

    fun setMode(context: Context, mode: FoldMode) {
        Prefs.get(context).edit().putString(Prefs.WP_MODE, mode.id).apply()
        WallCache.changed()
    }

    fun folder(context: Context): File = File(context.filesDir, "wallpapers").apply { mkdirs() }

    fun imageFile(context: Context, name: String): File = File(folder(context), name)

    /** Deletes images no saved wallpaper uses any more. */
    private fun cleanImages(context: Context) {
        val prefs = Prefs.get(context)
        val used = WallSlot.entries.mapNotNull { WallConfig.fromJson(prefs.getString(it.key, null))?.image }.toSet()
        folder(context).listFiles()?.forEach { f ->
            // Keep files written in the last minute: one may be about to be saved
            if (f.name !in used && System.currentTimeMillis() - f.lastModified() > 60_000) f.delete()
        }
    }
}
