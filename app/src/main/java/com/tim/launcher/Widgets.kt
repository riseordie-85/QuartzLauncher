package com.tim.launcher

import org.json.JSONArray
import org.json.JSONObject

/**
 * Quartz's own home screen widgets. Each one covers a block of cells on a page: every cell it covers holds
 * "widget:<id>" in the page's slot list, so icons flow around it and everything that moves pages and grids carries
 * it along. Its kind and size live here.
 */
enum class WidgetKind(
    val label: String,
    val about: String,
    /** Size it's added at, in cells (narrowed to the grid), and the smallest it can be resized to. */
    val w: Int,
    val h: Int,
    val minW: Int,
    val minH: Int,
    val weather: Boolean,
) {
    CLOCK("Clock", "The time, big, with the date under it", 4, 2, 2, 1, false),
    FLIP("Flip clock", "Cards that flip over as the minutes change", 4, 2, 2, 2, false),
    FLIP_WEATHER("Flip clock and weather", "The flip clock with today's weather under it", 4, 3, 3, 2, true),
    WEATHER("Weather", "Today's weather in a slim bar", 4, 1, 2, 1, true),

    /** A widget from another app (Calendar, Gmail, a music player…), hosted by Quartz. Its sizes come from the app. */
    ANDROID("App widget", "A widget from one of your apps", 2, 2, 1, 1, false);

    companion object {
        /** Quartz's own widgets, for pickers and "Change style". */
        val quartz: List<WidgetKind> get() = entries.filter { it != ANDROID }
    }
}

data class HomeWidget(
    val id: String,
    val kind: WidgetKind,
    val w: Int,
    val h: Int,
    /** For an app's widget: Android's id for it, and which app widget it is ("package/class"). */
    val appWidgetId: Int = 0,
    val provider: String = "",
    /** For an app's widget: a glass panel behind it, like Quartz's own widgets. */
    val glass: Boolean = false,
)

const val WIDGET_PREFIX = "widget:"

fun isWidgetKey(key: String?) = key != null && key.startsWith(WIDGET_PREFIX)

internal object WidgetJson {
    fun write(widgets: Collection<HomeWidget>): String {
        val array = JSONArray()
        widgets.forEach { w ->
            val o = JSONObject().put("id", w.id).put("kind", w.kind.name).put("w", w.w).put("h", w.h)
            if (w.kind == WidgetKind.ANDROID) o.put("appWidgetId", w.appWidgetId).put("provider", w.provider).put("glass", w.glass)
            array.put(o)
        }
        return array.toString()
    }

    fun read(text: String?): List<HomeWidget> {
        if (text.isNullOrEmpty()) return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val kind = WidgetKind.entries.firstOrNull { it.name == o.optString("kind") } ?: return@mapNotNull null
                HomeWidget(
                    o.getString("id"), kind, o.optInt("w", kind.w).coerceAtLeast(1), o.optInt("h", kind.h).coerceAtLeast(1),
                    appWidgetId = o.optInt("appWidgetId", 0), provider = o.optString("provider", ""), glass = o.optBoolean("glass", false),
                )
            }
        }.getOrElse {
            Diag.log("Couldn't read widgets: ${it.message}")
            emptyList()
        }
    }
}
