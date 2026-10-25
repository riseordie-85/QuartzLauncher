package com.tim.launcher

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Icon packs: ordinary apps from the Play Store that follow the long-standing launcher icon pack format (Nova, ADW,
 * Apex and others all read it). Quartz reads the pack's own list of which icon goes with which app, on the phone.
 */
object IconPacks {

    data class Pack(val pkg: String, val label: String)

    /** What a pack offers: an icon per app, plus optional pieces for dressing up apps it doesn't cover. */
    class Theme(
        val res: Resources,
        val pkg: String,
        val icons: Map<String, String>,
        val back: List<String>,
        val mask: String?,
        val upon: String?,
        val scale: Float,
    )

    private val actions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "com.teslacoilsw.launcher.THEME",
    )

    /** Icon packs installed on this phone, by name. */
    fun installed(context: Context): List<Pack> {
        val pm = context.packageManager
        val seen = HashMap<String, Pack>()
        actions.forEach { action ->
            runCatching { pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }.getOrDefault(emptyList()).forEach { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg !in seen) seen[pkg] = Pack(pkg, ri.loadLabel(pm).toString())
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    private val themes = HashMap<String, Theme?>()

    /** The pack's theme, read once and kept. Null if it isn't installed or can't be read. */
    @Synchronized
    fun theme(context: Context, pkg: String): Theme? {
        if (pkg.isEmpty()) return null
        if (themes.containsKey(pkg)) return themes[pkg]
        val t = runCatching { load(context, pkg) }
            .onFailure { Diag.log("Icon pack $pkg couldn't be read: ${it.message}") }
            .getOrNull()
        themes[pkg] = t
        return t
    }

    @Synchronized
    fun isLoaded(pkg: String) = themes.containsKey(pkg)

    /** Forget cached packs (one was installed, updated or removed). */
    @Synchronized
    fun clear() = themes.clear()

    @SuppressLint("DiscouragedApi") // icon packs are looked up by resource name; that's the format
    private fun load(context: Context, pkg: String): Theme {
        val res = context.packageManager.getResourcesForApplication(pkg)
        val parser: XmlPullParser = res.getIdentifier("appfilter", "xml", pkg).takeIf { it != 0 }?.let { res.getXml(it) }
            ?: XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(res.assets.open("appfilter.xml"), "UTF-8")
            }
        val icons = HashMap<String, String>()
        val back = ArrayList<String>()
        var mask: String? = null
        var upon: String? = null
        var scale = 1f
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "item" -> {
                        val comp = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        // "ComponentInfo{com.app/com.app.Main}" → "com.app/com.app.Main"
                        val key = comp?.substringAfter("{", "")?.substringBefore("}", "")
                        if (!key.isNullOrEmpty() && !drawable.isNullOrEmpty()) {
                            val cn = ComponentName.unflattenFromString(key)
                            if (cn != null) icons.putIfAbsent(cn.flattenToString(), drawable)
                        }
                    }
                    "iconback" -> for (i in 0 until parser.attributeCount) parser.getAttributeValue(i)?.let { back += it }
                    "iconmask" -> mask = parser.getAttributeValue(null, "img1")
                    "iconupon" -> upon = parser.getAttributeValue(null, "img1")
                    "scale" -> scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
                }
            }
            event = parser.next()
        }
        Diag.log("Icon pack $pkg: ${icons.size} icons")
        return Theme(res, pkg, icons, back, mask, upon, scale)
    }

    @SuppressLint("DiscouragedApi")
    fun drawable(theme: Theme, name: String?): Drawable? {
        if (name.isNullOrEmpty()) return null
        val id = theme.res.getIdentifier(name, "drawable", theme.pkg).takeIf { it != 0 }
            ?: theme.res.getIdentifier(name, "mipmap", theme.pkg).takeIf { it != 0 }
            ?: return null
        return runCatching { theme.res.getDrawable(id, null) }.getOrNull()
    }

    /** The pack's own icon for an app, if it has one. */
    fun iconFor(theme: Theme, component: ComponentName): Drawable? =
        drawable(theme, theme.icons[component.flattenToString()])
}
