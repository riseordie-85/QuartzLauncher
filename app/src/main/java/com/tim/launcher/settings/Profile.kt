package com.tim.launcher.settings

import android.content.Context
import com.tim.launcher.Prefs
import org.json.JSONObject

/**
 * A profile file: your settings plus your home screen layout, so a new device can be set up from an old one.
 * Apps the other device doesn't have simply leave their spots empty.
 */
object Profile {
    private val layoutKeys = listOf(Prefs.L_COVER, Prefs.L_DOCK_COVER, Prefs.L_DOCK_EXTRA)

    fun export(context: Context): String {
        val json = JSONObject(SettingsStore.get(context).exportJson())
        val prefs = Prefs.get(context)
        val layout = JSONObject()
        layout.put("cols", prefs.getInt(Prefs.COLS, 4))
        layout.put("rows", prefs.getInt(Prefs.ROWS, 6))
        layout.put("pages", prefs.getInt(Prefs.PAGES, 3))
        layoutKeys.forEach { key -> prefs.getString(key, null)?.let { layout.put(key, it) } }
        json.put("layout", layout)
        return json.toString(2)
    }

    /** Imports settings and, if the file has one, the layout. Returns how many settings were imported. */
    fun import(context: Context, text: String): Int {
        val count = SettingsStore.get(context).importJson(text)
        val layout = JSONObject(text).optJSONObject("layout") ?: return count
        val prefs = Prefs.get(context)
        val edit = prefs.edit()
        edit.putInt(Prefs.COLS, layout.optInt("cols", prefs.getInt(Prefs.COLS, 4)))
        edit.putInt(Prefs.ROWS, layout.optInt("rows", prefs.getInt(Prefs.ROWS, 6)))
        edit.putInt(Prefs.PAGES, layout.optInt("pages", prefs.getInt(Prefs.PAGES, 3)))
        layoutKeys.forEach { key -> if (layout.has(key)) edit.putString(key, layout.getString(key)) }
        edit.putString(Prefs.L_EXTRA, "")
        edit.putBoolean(Prefs.LAYOUT_READY, true)
        edit.putInt(Prefs.LAYOUT_STAMP, prefs.getInt(Prefs.LAYOUT_STAMP, 0) + 1)
        edit.apply()
        return count
    }
}
