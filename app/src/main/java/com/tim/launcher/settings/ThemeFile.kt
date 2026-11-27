package com.tim.launcher.settings

import android.content.Context
import android.util.Base64
import com.tim.launcher.wallpaper.FoldMode
import com.tim.launcher.wallpaper.WallConfig
import com.tim.launcher.wallpaper.WallSlot
import com.tim.launcher.wallpaper.WallStore
import org.json.JSONObject

/**
 * A theme file: just the look (the dock, the icons everywhere, the wallpaper with its photo, fonts (with any you
 * added), widget colors and badge style), with none of your layout, apps or other settings. For keeping a look, sharing one, or loading one someone
 * made. Plain JSON, so it can be read and written by hand too.
 *
 * ```
 * { "format": "quartz-launcher-theme", "version": 1, "name": "Crystal",
 *   "settings": { "dock.radius": "20", ... },          // setting id → value, as Settings stores it
 *   "wallpaper": { "main": {…}, "cover": {…}, "mode": "separate" },   // cover and mode only for a separate cover
 *   "images": { "img_1.jpg": "<base64 JPEG>" } }        // the wallpaper's photo, if it has one
 * ```
 */
object ThemeFile {
    const val FORMAT = "quartz-launcher-theme"
    const val VERSION = 1

    /** The dock and drawer's own icon values: a theme only carries them when that place has its own look. */
    private val placeKeys: List<Setting<*>> = S.iconsDock.look + S.iconsDrawer.look

    /** Everything a theme carries, apart from [placeKeys] and the wallpaper. */
    private val keys: List<Setting<*>> = (DockPresets.keys + IconPresets.keys + listOf(S.iconsMatch, S.widgetColor, S.widgetAccent, S.weatherIcons, S.badgeStyle) + fontKeys).distinct()

    private val fontKeys: List<Setting<String>> get() = listOf(S.fontHome, S.fontDock, S.fontDrawer, S.fontWidgets)

    private val byId by lazy { (keys + placeKeys).associateBy { it.id } }

    @Suppress("UNCHECKED_CAST")
    private fun encode(store: SettingsStore, s: Setting<*>): String = (s as Setting<Any?>).codec.encode(store[s])

    fun export(context: Context, name: String): String {
        val store = SettingsStore.get(context)
        val settings = JSONObject()
        keys.forEach { settings.put(it.id, encode(store, it)) }
        placeKeys.forEach { if (store.owner(it, Scope.GLOBAL) != null) settings.put(it.id, encode(store, it)) }

        val wallpaper = JSONObject()
        val images = JSONObject()
        fun addWall(slot: WallSlot, key: String) {
            val config = WallStore.load(context, slot)
            wallpaper.put(key, JSONObject(config.toJson()))
            config.image?.let { img ->
                val file = WallStore.imageFile(context, img)
                if (file.exists() && !images.has(img)) images.put(img, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            }
        }
        addWall(WallSlot.MAIN, "main")
        if (WallStore.mode(context) == FoldMode.SEPARATE) {
            addWall(WallSlot.COVER, "cover")
            wallpaper.put("mode", FoldMode.SEPARATE.id)
        }

        // Fonts you added travel with the theme (a font from this phone or Android is named, not carried)
        val fonts = JSONObject()
        fontKeys.map { store[it] }.distinct().forEach { id ->
            com.tim.launcher.Fonts.importedFiles(context, id).forEach { f ->
                if (!fonts.has(f.name)) fonts.put(f.name, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
            }
        }

        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("name", name.trim().ifEmpty { "My theme" })
            put("settings", settings)
            put("wallpaper", wallpaper)
            if (images.length() > 0) put("images", images)
            if (fonts.length() > 0) put("fonts", fonts)
        }.toString(2)
    }

    /** The theme's name, or an error if [text] isn't a theme file (a full backup says so). */
    fun nameOf(text: String): String {
        val json = JSONObject(text)
        val format = json.optString("format")
        require(format != "quartz-launcher-settings") { "That's a full backup. Import it under Import profile." }
        require(format == FORMAT) { "Not a Quartz theme" }
        return json.optString("name").ifBlank { "Theme" }
    }

    /**
     * Puts the theme in place. Anything it doesn't name goes back to its default (and dock or drawer icons it doesn't
     * give a look of their own follow the home screen's), so you get the theme as it was made. Returns its name.
     */
    fun import(context: Context, text: String): String {
        val name = nameOf(text)
        val json = JSONObject(text)
        val images = json.optJSONObject("images")
        val fonts = json.optJSONObject("fonts")
        apply(
            context, json,
            images?.keys()?.asSequence()?.toList() ?: emptyList(), { Base64.decode(images!!.getString(it), Base64.DEFAULT) },
            fonts?.keys()?.asSequence()?.toList() ?: emptyList(), { Base64.decode(fonts!!.getString(it), Base64.DEFAULT) },
        )
        return name
    }

    /**
     * A theme kept in the app (assets/themes/[file].json): the same as a theme file, with its photo and fonts stored
     * beside it (assets/themes/files) so each is in the app once.
     */
    fun importAsset(context: Context, file: String): String {
        val json = JSONObject(context.assets.open("themes/$file.json").bufferedReader().readText())
        fun names(key: String) = json.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        val read: (String) -> ByteArray = { context.assets.open("themes/files/$it").use { s -> s.readBytes() } }
        apply(context, json, names("assetImages"), read, names("assetFonts"), read)
        return json.optString("name", file)
    }

    /** A theme's look settings as a [Look], for drawing it before it's used. */
    fun lookOf(settings: JSONObject): Look {
        val values = HashMap<Setting<*>, Any>()
        settings.keys().forEach { id ->
            val s = byId[id] ?: return@forEach
            @Suppress("UNCHECKED_CAST")
            (s as Setting<Any?>).codec.decode(settings.optString(id))?.let { values[s] = it }
        }
        return Look(values)
    }

    private fun apply(
        context: Context,
        json: JSONObject,
        imageNames: List<String>,
        image: (String) -> ByteArray,
        fontNames: List<String>,
        font: (String) -> ByteArray,
    ) {
        val store = SettingsStore.get(context)
        val settings = json.optJSONObject("settings") ?: JSONObject()
        // Its fonts first, so the settings that name them find them
        fontNames.forEach { file -> runCatching { com.tim.launcher.Fonts.addFile(context, file, font(file)) } }

        (keys + placeKeys).forEach { s ->
            val raw = settings.optString(s.id, "")
            @Suppress("UNCHECKED_CAST")
            val value = if (settings.has(s.id)) (s as Setting<Any?>).codec.decode(raw) else null
            if (value != null) {
                @Suppress("UNCHECKED_CAST")
                store.set(s as Setting<Any?>, value = value)
                // These are shared by every screen, so no screen keeps a value of its own
                store.linkAll(s, Scope.entries.filter { it != Scope.GLOBAL })
            } else {
                store.clear(s)
            }
        }
        // (Keys from a newer Quartz that this one doesn't know are skipped)
        settings.keys().forEach { k -> if (k !in byId) com.tim.launcher.Diag.log("Theme setting skipped: $k") }

        // The photo(s) come in under new names, so they never replace one you have
        val renamed = HashMap<String, String>()
        imageNames.forEach { old ->
            runCatching {
                val bytes = image(old)
                val fresh = "img_${System.currentTimeMillis()}_${renamed.size}.jpg"
                WallStore.imageFile(context, fresh).writeBytes(bytes)
                renamed[old] = fresh
            }
        }
        fun wall(key: String): WallConfig? {
            val obj = json.optJSONObject("wallpaper")?.optJSONObject(key) ?: return null
            val config = WallConfig.fromJson(obj.toString()) ?: return null
            // A photo that didn't come with the file can't be shown here: the rest of the wallpaper still applies
            val image = config.image?.let { renamed[it] }
            return config.copy(image = image)
        }
        wall("main")?.let { WallStore.save(context, WallSlot.MAIN, it) }
        val cover = wall("cover")
        if (cover != null) {
            WallStore.save(context, WallSlot.COVER, cover)
            WallStore.setMode(context, FoldMode.SEPARATE)
        } else if (WallStore.mode(context) == FoldMode.SEPARATE) {
            // One wallpaper in the theme: the cover screen shows it too
            WallStore.setMode(context, FoldMode.STRETCHED)
        }
    }
}
