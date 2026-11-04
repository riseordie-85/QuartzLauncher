package com.tim.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Schedule
// Renamed here so it can't be mixed up with android.provider.Settings, also used in this file
import androidx.compose.material.icons.rounded.Settings as SettingsGear
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONArray
import org.json.JSONObject

// ---- Tabs ----

/** A drawer tab you made: a name, an icon (one of the folder badges) and the apps in it, in your order. */
data class DrawerTab(val id: String, val name: String, val icon: String, val apps: List<String>)

/**
 * Your custom drawer tabs. Shared by the home screen and settings (same process), saved in the launcher's prefs.
 * Apps that get uninstalled simply stop showing; they're dropped the next time the tab is saved.
 */
object DrawerTabs {
    val tabs = mutableStateListOf<DrawerTab>()

    /** Apps pinned under the tabs, one tap away from any tab (Settings, a calculator…), in your order. */
    val shortcuts = mutableStateListOf<String>()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        Prefs.get(context).getString(Prefs.DRAWER_SHORTCUTS, null)?.let { text ->
            runCatching {
                val arr = JSONArray(text)
                shortcuts += (0 until arr.length()).map { arr.getString(it) }.distinct()
            }
        }
        val text = Prefs.get(context).getString(Prefs.DRAWER_TABS, null) ?: return
        runCatching {
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val apps = o.optJSONArray("apps")
                tabs += DrawerTab(
                    id = o.getString("id"),
                    name = o.optString("name", "Tab"),
                    icon = o.optString("icon", "star"),
                    // distinct: an app listed twice (a hand-edited or imported file) would crash the drawer's list
                    apps = if (apps == null) emptyList() else (0 until apps.length()).map { apps.getString(it) }.distinct(),
                )
            }
        }
    }

    /** Re-reads the saved tabs (after a profile import). */
    fun reload(context: Context) {
        tabs.clear()
        shortcuts.clear()
        loaded = false
        load(context)
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        tabs.forEach { t ->
            arr.put(JSONObject().put("id", t.id).put("name", t.name).put("icon", t.icon).put("apps", JSONArray(t.apps)))
        }
        Prefs.get(context).edit().putString(Prefs.DRAWER_TABS, arr.toString()).apply()
    }

    fun add(context: Context, name: String, icon: String): String {
        val id = System.currentTimeMillis().toString(36)
        tabs += DrawerTab(id, name.trim().ifEmpty { "Tab" }, icon, emptyList())
        save(context)
        return id
    }

    fun update(context: Context, tab: DrawerTab) {
        val i = tabs.indexOfFirst { it.id == tab.id }
        if (i < 0) return
        tabs[i] = tab.copy(name = tab.name.trim().ifEmpty { "Tab" }, apps = tab.apps.distinct())
        save(context)
    }

    fun delete(context: Context, id: String) {
        tabs.removeAll { it.id == id }
        save(context)
    }

    fun move(context: Context, id: String, by: Int) {
        val i = tabs.indexOfFirst { it.id == id }
        val j = i + by
        if (i < 0 || j !in tabs.indices) return
        val t = tabs.removeAt(i)
        tabs.add(j, t)
        save(context)
    }

    fun find(id: String?): DrawerTab? = tabs.firstOrNull { it.id == id }

    private fun saveShortcuts(context: Context) {
        Prefs.get(context).edit().putString(Prefs.DRAWER_SHORTCUTS, JSONArray(shortcuts.toList()).toString()).apply()
    }

    fun addShortcut(context: Context, key: String) {
        if (key in shortcuts) return
        shortcuts += key
        saveShortcuts(context)
    }

    fun removeShortcut(context: Context, key: String) {
        shortcuts.remove(key)
        saveShortcuts(context)
    }

    fun moveShortcut(context: Context, key: String, by: Int) {
        val i = shortcuts.indexOf(key)
        val j = i + by
        if (i < 0 || j !in shortcuts.indices) return
        shortcuts.add(j, shortcuts.removeAt(i))
        saveShortcuts(context)
    }
}

/** One tab as the drawer shows it: custom tabs, automatic category tabs and "All" all look the same here. */
data class TabView(val id: String, val name: String, val icon: String, val apps: List<AppEntry>)

/** Android's own app categories, for automatic tabs and for suggestions. */
object AppCategories {
    private val names = listOf(
        ApplicationInfo.CATEGORY_GAME to ("Games" to "games"),
        ApplicationInfo.CATEGORY_SOCIAL to ("Social" to "social"),
        ApplicationInfo.CATEGORY_AUDIO to ("Music" to "music"),
        ApplicationInfo.CATEGORY_VIDEO to ("Video" to "video"),
        ApplicationInfo.CATEGORY_IMAGE to ("Photos" to "photo"),
        ApplicationInfo.CATEGORY_NEWS to ("News" to "news"),
        ApplicationInfo.CATEGORY_MAPS to ("Travel" to "travel"),
        ApplicationInfo.CATEGORY_PRODUCTIVITY to ("Work" to "work"),
        ApplicationInfo.CATEGORY_ACCESSIBILITY to ("Accessibility" to "health"),
    )

    /** "All" first, then one tab per category that has apps, then "Other". */
    fun tabs(apps: List<AppEntry>): List<TabView> {
        val result = ArrayList<TabView>()
        val used = HashSet<String>()
        names.forEach { (cat, n) ->
            val inCat = apps.filter { it.category == cat }
            if (inCat.isNotEmpty()) {
                result += TabView("cat:$cat", n.first, n.second, inCat)
                used += inCat.map { it.key }
            }
        }
        val other = apps.filter { it.key !in used }
        if (other.isNotEmpty() && result.isNotEmpty()) result += TabView("cat:other", "Other", "folder", other)
        return result
    }

    /** Which category a custom tab's name points at, if any ("Games", "My music", "Work stuff"). */
    fun forName(name: String): Int? {
        val n = name.lowercase()
        fun has(vararg words: String) = words.any { it in n }
        return when {
            has("game", "play") -> ApplicationInfo.CATEGORY_GAME
            has("music", "audio", "podcast", "sound") -> ApplicationInfo.CATEGORY_AUDIO
            has("video", "movie", "tv", "stream", "watch", "anime") -> ApplicationInfo.CATEGORY_VIDEO
            has("photo", "camera", "image", "picture", "gallery") -> ApplicationInfo.CATEGORY_IMAGE
            has("social", "chat", "message", "friend") -> ApplicationInfo.CATEGORY_SOCIAL
            has("news", "read") -> ApplicationInfo.CATEGORY_NEWS
            has("map", "travel", "nav", "trip") -> ApplicationInfo.CATEGORY_MAPS
            has("work", "office", "productiv", "tool", "util") -> ApplicationInfo.CATEGORY_PRODUCTIVITY
            else -> null
        }
    }

    /**
     * Apps that might belong in a tab called [name]: ones in the matching Android category, and ones whose name
     * shares a word with it ("Quartz" suggests Quartz Notes and Quartz Gallery). Worked out on the phone; nothing is
     * looked up online.
     */
    fun suggest(name: String, apps: List<AppEntry>, exclude: Set<String>): List<AppEntry> {
        val cat = forName(name)
        val words = name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
        return apps.filter { app ->
            app.key !in exclude && (
                (cat != null && app.category == cat) ||
                    words.any { w -> app.label.lowercase().split(Regex("[^a-z0-9]+")).any { it.startsWith(w) } }
                )
        }
    }
}

// ---- Search ----

/** An Android settings page search can open. */
data class SettingsShortcut(val title: String, val keywords: String, val icon: ImageVector, val action: String)

object SearchSources {
    val settings: List<SettingsShortcut> = listOf(
        SettingsShortcut("Wi‑Fi", "wifi wireless internet network", Icons.Rounded.Wifi, Settings.ACTION_WIFI_SETTINGS),
        SettingsShortcut("Bluetooth", "bluetooth headphones pair", Icons.Rounded.Bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS),
        SettingsShortcut("Airplane mode", "airplane flight", Icons.Rounded.AirplanemodeActive, Settings.ACTION_AIRPLANE_MODE_SETTINGS),
        SettingsShortcut("Display", "display brightness screen dark mode font", Icons.Rounded.WbSunny, Settings.ACTION_DISPLAY_SETTINGS),
        SettingsShortcut("Sound", "sound volume ringtone vibrate", Icons.Rounded.VolumeUp, Settings.ACTION_SOUND_SETTINGS),
        SettingsShortcut("Notifications", "notifications alerts", Icons.Rounded.Notifications, "android.settings.NOTIFICATION_SETTINGS"),
        SettingsShortcut("Battery", "battery power", Icons.Rounded.BatteryStd, Intent.ACTION_POWER_USAGE_SUMMARY),
        SettingsShortcut("Apps", "apps applications uninstall", Icons.Rounded.Apps, Settings.ACTION_APPLICATION_SETTINGS),
        SettingsShortcut("Default apps", "default apps browser home", Icons.Rounded.SettingsSuggest, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        SettingsShortcut("Location", "location gps", Icons.Rounded.LocationOn, Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        SettingsShortcut("Security", "security lock fingerprint face password", Icons.Rounded.Lock, Settings.ACTION_SECURITY_SETTINGS),
        SettingsShortcut("Privacy", "privacy permissions", Icons.Rounded.PrivacyTip, Settings.ACTION_PRIVACY_SETTINGS),
        SettingsShortcut("Accessibility", "accessibility talkback", Icons.Rounded.AccessibilityNew, Settings.ACTION_ACCESSIBILITY_SETTINGS),
        SettingsShortcut("Storage", "storage space files", Icons.Rounded.SdStorage, Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        SettingsShortcut("Data usage", "data mobile usage", Icons.Rounded.DataUsage, Settings.ACTION_DATA_USAGE_SETTINGS),
        SettingsShortcut("NFC", "nfc tap pay", Icons.Rounded.Nfc, Settings.ACTION_NFC_SETTINGS),
        SettingsShortcut("Date and time", "date time clock timezone", Icons.Rounded.Schedule, Settings.ACTION_DATE_SETTINGS),
        SettingsShortcut("Languages", "language keyboard input", Icons.Rounded.Language, Settings.ACTION_LOCALE_SETTINGS),
        SettingsShortcut("Developer options", "developer usb debugging", Icons.Rounded.DeveloperMode, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        SettingsShortcut("About phone", "about phone device info version", Icons.Rounded.PhoneAndroid, Settings.ACTION_DEVICE_INFO_SETTINGS),
        SettingsShortcut("Wallpaper (Quartz)", "wallpaper background", Icons.Rounded.Wallpaper, "quartz:wallpaper"),
        SettingsShortcut("Quartz settings", "quartz launcher home settings", Icons.Rounded.SettingsGear, "quartz:settings"),
    )

    /** Apps whose names match: starts-with first, then a word starting with it, then anywhere in the name. */
    fun apps(query: String, apps: List<AppEntry>): List<AppEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        fun rank(a: AppEntry): Int {
            val l = a.label.lowercase()
            return when {
                l.startsWith(q) -> 0
                l.split(' ', '-', '.').any { it.startsWith(q) } -> 1
                q in l -> 2
                else -> 9
            }
        }
        return apps.map { it to rank(it) }.filter { it.second < 9 }.sortedWith(compareBy({ it.second }, { it.first.label.lowercase() })).map { it.first }
    }

    fun settings(query: String): List<SettingsShortcut> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return settings.filter { s -> s.title.lowercase().contains(q) || s.keywords.split(' ').any { it.startsWith(q) } }.take(6)
    }

    fun openSetting(context: Context, s: SettingsShortcut) {
        when (s.action) {
            "quartz:settings" -> com.tim.launcher.settings.SettingsActivity.open(context)
            "quartz:wallpaper" -> com.tim.launcher.settings.SettingsActivity.open(context, route = com.tim.launcher.settings.Route.WALLPAPER)
            else -> runCatching { context.startActivity(Intent(s.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure {
                    // Some phones don't have every page; the main settings screen always works
                    runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
        }
    }

    data class Contact(val id: Long, val name: String, val lookup: String)

    fun canReadContacts(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Contacts whose names match, read from the phone's own contacts. Only when you've turned this on. */
    fun contacts(context: Context, query: String): List<Contact> {
        val q = query.trim()
        if (q.length < 2 || !canReadContacts(context)) return emptyList()
        return runCatching {
            val uri = ContactsContract.Contacts.CONTENT_URI
            val cols = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.LOOKUP_KEY)
            val out = ArrayList<Contact>()
            context.contentResolver.query(
                uri, cols, "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?", arrayOf("%$q%"),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
            )?.use { c ->
                while (c.moveToNext() && out.size < 6) {
                    out += Contact(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "")
                }
            }
            out
        }.getOrDefault(emptyList())
    }

    fun openContact(context: Context, contact: Contact) {
        val uri: Uri = ContactsContract.Contacts.getLookupUri(contact.id, contact.lookup)
            ?: ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id)
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
