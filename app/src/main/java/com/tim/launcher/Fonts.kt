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
import java.nio.ByteBuffer
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
        // Each piece is read whole: RandomAccessFile on its own reads a byte at a time, which adds up over the
        // hundreds of font files a phone has (and the first look can happen on the main thread)
        fun chunk(at: Long, size: Int): ByteBuffer = ByteBuffer.wrap(ByteArray(size).also { f.seek(at); f.readFully(it) })
        val head = chunk(0, 12)
        val sfnt = head.getInt(0)
        // TrueType (0x00010000 or 'true') or OpenType with CFF outlines ('OTTO'); collections aren't supported
        if (sfnt != 0x00010000 && sfnt != 0x4F54544F && sfnt != 0x74727565) return null
        val count = head.u16(4)
        val dir = chunk(12, count * 16)
        val tables = HashMap<String, Pair<Long, Int>>() // offset and length
        for (i in 0 until count) {
            val at = i * 16
            val tag = String(ByteArray(4) { dir.get(at + it) }, Charsets.US_ASCII)
            val offset = dir.getInt(at + 8).toLong() and 0xFFFFFFFFL
            val length = dir.getInt(at + 12)
            tables[tag] = offset to length
        }
        // The family: the "typographic" name (16) if there is one, else the plain one (1); English if offered
        var best: String? = null
        var bestRank = Int.MAX_VALUE
        tables["name"]?.let { (base, size) ->
            val t = chunk(base, size.coerceIn(0, 1 shl 20))
            val records = t.u16(2)
            val strings = t.u16(4)
            for (i in 0 until records) {
                val at = 6 + i * 12
                val platform = t.u16(at)
                val language = t.u16(at + 4)
                val id = t.u16(at + 6)
                val length = t.u16(at + 8)
                val offset = t.u16(at + 10)
                if (id != 16 && id != 1) continue
                val bytes = ByteArray(length) { t.get(strings + offset + it) }
                val text = when (platform) {
                    0, 3 -> bytes.toString(Charsets.UTF_16BE)
                    1 -> bytes.toString(Charsets.ISO_8859_1)
                    else -> continue
                }.trim()
                if (text.isEmpty()) continue
                // Unicode names have no language; Mac English is 0, Windows US English 0x409
                val english = when (platform) {
                    0 -> true
                    1 -> language == 0
                    else -> language == 0x409
                }
                val rank = (if (id == 16) 0 else 2) + (if (english) 0 else 1)
                if (rank < bestRank) {
                    best = text
                    bestRank = rank
                }
            }
        }
        var weight = 400
        var italic = false
        tables["OS/2"]?.let { (base, size) ->
            if (size >= 64) {
                val t = chunk(base, 64)
                weight = t.u16(4)
                italic = (t.u16(62) and 1) != 0
            }
        }
        Info(file, best ?: file.nameWithoutExtension, "fvar" in tables, weight.coerceIn(100, 900), italic)
    }

    private fun ByteBuffer.u16(at: Int): Int = getShort(at).toInt() and 0xFFFF

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
        }.getOrDefault(emptyMap<String, List<Info>>()).also { phone = it }
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
            // The file's real name where the picker gives it (a content address can be just a number)
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "font"
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
        val file = File(folder(context), safe(name))
        file.writeBytes(bytes)
        // Anything that isn't a font would only sit there, out of sight (Your fonts lists fonts only)
        if (inspect(file) == null) file.delete()
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
    fun family(context: Context, id: String): FontFamily {
        families[id]?.let { return it }
        // The phone's fonts haven't been looked through yet: never on the main thread. Your phone's font until
        // they have, then everything showing it looks again
        if (id.startsWith("phone:") && phone == null) {
            if (warming.compareAndSet(false, true)) Thread { phoneFamilies(); changes.intValue++ }.start()
            return FontFamily.Default
        }
        return (build(context, id) ?: FontFamily.Default).also { families[id] = it }
    }

    private val warming = java.util.concurrent.atomic.AtomicBoolean(false)

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
        }.distinctBy { it.weight to it.style } // two files of one weight and style (a family's widths): the first
        return FontFamily(fonts)
    }

    /**
     * The font [id] for a theme's picture before it's used: a font the theme brings ([files], in assets/themes/files,
     * copied out once to look at) or any other kind. Call off the main thread.
     */
    fun themeFamily(context: Context, id: String, files: List<String>): FontFamily {
        if (!id.startsWith("user:")) return family(context, id)
        val want = id.removePrefix("user:")
        val dir = File(context.cacheDir, "theme_fonts").apply { mkdirs() }
        val infos = files.mapNotNull { name ->
            val f = File(dir, safe(name))
            if (!f.exists()) runCatching { context.assets.open("themes/files/$name").use { input -> f.outputStream().use { input.copyTo(it) } } }
            inspect(f)
        }.filter { it.family == want }
        // Already added (an earlier theme, or by you)
        return fromFiles(infos) ?: family(context, id)
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
