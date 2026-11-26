package com.tim.launcher

import android.content.Context
import android.graphics.fonts.SystemFonts
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.tim.launcher.settings.S
import com.tim.launcher.settings.SettingsStore
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * The fonts you can pick for labels and widgets, and where they come from:
 * - **Your phone's font** ([SYSTEM]): whatever the phone uses, including a font chosen in the phone's own settings.
 * - **Outfit** ([OUTFIT]): bundled, the widgets' original typeface.
 * - **Android's named families** (`family:serif` and so on): on every phone.
 * - **On this phone** (`phone:<family>`): font files the phone has, apart from the many language and emoji ones.
 * - **Imported** (`user:<family>`): .ttf or .otf files you add (a Google Fonts .zip works as it downloads), kept in
 *   Quartz's private folder. They travel inside theme files.
 *
 * A font that isn't on this phone (from a theme made elsewhere) shows as your phone's font.
 */
@OptIn(ExperimentalTextApi::class)
object Fonts {
    const val SYSTEM = "system"
    const val OUTFIT = "outfit"
    /** The dock or drawer uses the home screen's font. */
    const val HOME = "home"

    data class Choice(val id: String, val name: String)

    /** Android's own families, by the names it knows them by. */
    private val named = listOf(
        "sans-serif" to "Sans",
        "sans-serif-condensed" to "Sans condensed",
        "serif" to "Serif",
        "monospace" to "Mono",
        "serif-monospace" to "Serif mono",
        "casual" to "Casual",
        "cursive" to "Cursive",
        "sans-serif-smallcaps" to "Small caps",
    )

    val builtIn: List<Choice> = listOf(Choice(SYSTEM, "Your phone's font"), Choice(OUTFIT, "Outfit")) +
        named.map { (id, name) -> Choice("family:$id", name) }

    // ---- What's in a font file ----

    /** A font file's family name, whether it's variable (one file, every weight), its weight and whether it's italic. */
    class Info(val file: File, val family: String, val variable: Boolean, val weight: Int, val italic: Boolean)

    private val infos = ConcurrentHashMap<String, Any>()
    private val notAFont = Any()

    /** Reads the font's own tables (name, OS/2, fvar); null if it isn't a TrueType or OpenType font. */
    fun inspect(file: File): Info? = infos.getOrPut("${file.path}|${file.length()}|${file.lastModified()}") {
        runCatching { read(file) }.getOrNull() ?: notAFont
    } as? Info

    /** Goes up whenever fonts are added or removed, so anything showing one looks again. */
    private val changes = androidx.compose.runtime.mutableIntStateOf(0)
    val version: Int get() = changes.intValue

    private fun changed() {
        families.clear()
        changes.intValue++
    }

    private fun read(file: File): Info? = RandomAccessFile(file, "r").use { f ->
        val version = f.readInt()
        // TrueType (0x00010000 or 'true') or OpenType with CFF outlines ('OTTO'); collections aren't supported
        if (version != 0x00010000 && version != 0x4F54544F && version != 0x74727565) return null
        val count = f.readUnsignedShort()
        f.skipBytes(6)
        val tables = HashMap<String, Long>()
        repeat(count) {
            val tag = ByteArray(4).also { f.readFully(it) }
            f.readInt() // checksum
            val offset = f.readInt().toLong() and 0xFFFFFFFFL
            f.readInt() // length
            tables[String(tag, Charsets.US_ASCII)] = offset
        }
        // The family: the "typographic" name (16) if there is one, else the plain one (1); English if offered
        var best: String? = null
        var bestRank = Int.MAX_VALUE
        tables["name"]?.let { base ->
            f.seek(base)
            f.readUnsignedShort()
            val records = f.readUnsignedShort()
            val strings = base + f.readUnsignedShort()
            for (i in 0 until records) {
                f.seek(base + 6 + i * 12L)
                val platform = f.readUnsignedShort()
                f.readUnsignedShort() // encoding
                val language = f.readUnsignedShort()
                val id = f.readUnsignedShort()
                val length = f.readUnsignedShort()
                val offset = f.readUnsignedShort()
                if (id != 16 && id != 1) continue
                val text = when (platform) {
                    0, 3 -> ByteArray(length).also { f.seek(strings + offset); f.readFully(it) }.toString(Charsets.UTF_16BE)
                    1 -> ByteArray(length).also { f.seek(strings + offset); f.readFully(it) }.toString(Charsets.ISO_8859_1)
                    else -> continue
                }.trim()
                if (text.isEmpty()) continue
                val english = platform == 1 || language == 0x409 || platform == 0
                val rank = (if (id == 16) 0 else 2) + (if (english) 0 else 1)
                if (rank < bestRank) {
                    best = text
                    bestRank = rank
                }
            }
        }
        var weight = 400
        var italic = false
        tables["OS/2"]?.let { base ->
            f.seek(base + 4)
            weight = f.readUnsignedShort()
            f.seek(base + 62)
            italic = (f.readUnsignedShort() and 1) != 0
        }
        Info(file, best ?: file.nameWithoutExtension, "fvar" in tables, weight.coerceIn(100, 900), italic)
    }

    // ---- The phone's own fonts ----

    @Volatile
    private var phone: Map<String, List<Info>>? = null

    /** Families of font files on this phone, leaving out language and emoji fonts. Slow the first time: call off the main thread. */
    fun phoneFamilies(): Map<String, List<Info>> = phone ?: synchronized(this) {
        phone ?: runCatching {
            SystemFonts.getAvailableFonts()
                .mapNotNull { it.file }
                .distinctBy { it.path }
                .mapNotNull { inspect(it) }
                .filterNot { info ->
                    val n = info.family.lowercase()
                    n.startsWith("noto") || "emoji" in n || "symbol" in n || "fallback" in n || "clock" in n || n.startsWith("source han")
                }
                .groupBy { it.family }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        }.getOrDefault(emptyMap()).also { phone = it }
    }

    fun phoneChoices(): List<Choice> = phoneFamilies().keys.map { Choice("phone:$it", it) }

    // ---- Fonts you've added ----

    fun folder(context: Context): File = File(context.filesDir, "fonts").apply { mkdirs() }

    fun importedFamilies(context: Context): Map<String, List<Info>> =
        (folder(context).listFiles()?.toList() ?: emptyList())
            .mapNotNull { inspect(it) }
            .groupBy { it.family }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)

    fun importedChoices(context: Context): List<Choice> = importedFamilies(context).keys.map { Choice("user:$it", it) }

    /** The files behind an imported font, for a theme file to carry. */
    fun importedFiles(context: Context, id: String): List<File> =
        if (!id.startsWith("user:")) emptyList() else importedFamilies(context)[id.removePrefix("user:")]?.map { it.file } ?: emptyList()

    /**
     * Adds the fonts in [uri]: a .ttf or .otf, or a .zip of them (as Google Fonts downloads a family). From a zip
     * with both, only the variable files come in (they already hold every weight). Returns the families added.
     */
    fun import(context: Context, uri: Uri): List<String> {
        val temp = File(context.cacheDir, "font_import").apply { deleteRecursively(); mkdirs() }
        try {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "font"
            val incoming = ArrayList<File>()
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                    ZipInputStream(bytes.inputStream()).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            val entryName = entry.name.substringAfterLast('/')
                            if (!entry.isDirectory && (entryName.endsWith(".ttf", true) || entryName.endsWith(".otf", true))) {
                                File(temp, safe(entryName)).also { it.writeBytes(zip.readBytes()); incoming += it }
                            }
                        }
                    }
                } else {
                    File(temp, safe(if (name.contains('.')) name else "$name.ttf")).also { it.writeBytes(bytes); incoming += it }
                }
            }
            val fonts = incoming.mapNotNull { inspect(it) }
            require(fonts.isNotEmpty()) { "No fonts in that file" }
            val keep = fonts.groupBy { it.family }.values.flatMap { family -> family.filter { it.variable }.ifEmpty { family } }
            keep.forEach { info ->
                // Named after what's in it, so the same font added twice replaces itself
                val ext = info.file.extension.lowercase().ifEmpty { "ttf" }
                val label = "${info.family}-${if (info.variable) "var" else info.weight}${if (info.italic) "i" else ""}.$ext"
                info.file.copyTo(File(folder(context), safe(label)), overwrite = true)
            }
            changed()
            return keep.map { it.family }.distinct()
        } finally {
            temp.deleteRecursively()
        }
    }

    /** Puts font files that came with a theme in place. */
    fun addFile(context: Context, name: String, bytes: ByteArray) {
        File(folder(context), safe(name)).writeBytes(bytes)
        changed()
    }

    fun remove(context: Context, id: String) {
        importedFiles(context, id).forEach { it.delete() }
        changed()
    }

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9._,-]"), "_").take(120)

    // ---- Turning a choice into something to draw with ----

    private val families = ConcurrentHashMap<String, FontFamily>()

    /** The font for [id], ready to draw with. Anything not on this phone is your phone's font. */
    fun family(context: Context, id: String): FontFamily = families.getOrPut(id) { build(context, id) ?: FontFamily.Default }

    private fun build(context: Context, id: String): FontFamily? = when {
        id == SYSTEM || id == HOME -> FontFamily.Default
        id == OUTFIT -> Outfit
        id.startsWith("family:") -> FontFamily(android.graphics.Typeface.create(id.removePrefix("family:"), android.graphics.Typeface.NORMAL))
        id.startsWith("phone:") -> fromFiles(phoneFamilies()[id.removePrefix("phone:")])
        id.startsWith("user:") -> fromFiles(importedFamilies(context)[id.removePrefix("user:")])
        else -> null
    }

    /** A family from its files: a variable file covers every weight; a fixed one, its own weight (bolder is made up). */
    private fun fromFiles(files: List<Info>?): FontFamily? {
        if (files.isNullOrEmpty()) return null
        val fonts = files.flatMap { info ->
            val style = if (info.italic) FontStyle.Italic else FontStyle.Normal
            if (info.variable) (100..900 step 100).map { w -> Font(info.file, FontWeight(w), style) }
            else listOf(Font(info.file, FontWeight(info.weight), style))
        }
        return FontFamily(fonts)
    }

    /** A short name for [id], for settings rows. */
    fun nameOf(id: String): String = when {
        id == HOME -> "Same as home screen"
        id.startsWith("phone:") -> id.removePrefix("phone:")
        id.startsWith("user:") -> id.removePrefix("user:")
        else -> builtIn.firstOrNull { it.id == id }?.name ?: "Your phone's font"
    }
}

/** The font for the widgets' numbers and words (set by each widget from Settings › Fonts). */
val LocalWidgetFont = staticCompositionLocalOf<FontFamily> { Outfit }

/** The font for app and folder names where they're shown: home screen, dock or drawer (from [LocalIconSlot]). */
@Composable
fun labelFont(): FontFamily {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val home = store[S.fontHome]
    val id = when (LocalIconSlot.current) {
        IconSlot.HOME -> home
        IconSlot.DOCK -> store[S.fontDock].let { if (it == Fonts.HOME) home else it }
        IconSlot.DRAWER -> store[S.fontDrawer].let { if (it == Fonts.HOME) home else it }
    }
    return remember(id, Fonts.version) { Fonts.family(context, id) }
}
