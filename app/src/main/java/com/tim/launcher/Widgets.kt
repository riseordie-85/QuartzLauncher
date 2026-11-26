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
    TIPS("Quartz tips", "Where to find wallpaper, setup and customizing", 4, 2, 2, 2, false),

    // Dates (tapping one opens your calendar unless you pick another app)
    DAY_DATE("Day and date", "The weekday's letter beside the date on a card", 2, 1, 1, 1, false),
    DATE("Date", "The month over the day", 2, 1, 1, 1, false),
    CALENDAR_PAGE("Calendar page", "A tear-off page: month, day and weekday", 2, 2, 2, 2, false),
    DATE_YEAR("Date and year", "Month, day and year, big", 2, 2, 2, 2, false),
    MONTH("Month", "This month's calendar with today circled", 2, 2, 2, 2, false),
    WEEK("Week", "This week with today circled", 4, 1, 3, 1, false),
    DATE_LINE("Date line", "Month, weekday and day in one line", 4, 1, 2, 1, false),
    DAY_SMALL("Day and date, small", "The weekday's letter and the date", 1, 1, 1, 1, false),
    DAY_ORDINAL("Day of the month", "Just the date: 16th", 1, 1, 1, 1, false),
    DATE_NUMBERS("Date in numbers", "Month and day as numbers", 1, 1, 1, 1, false),

    /** A widget from another app (Calendar, Gmail, a music player…), hosted by Quartz. Its sizes come from the app. */
    ANDROID("App widget", "A widget from one of your apps", 2, 2, 1, 1, false);

    companion object {
        /** Quartz's own widgets, for pickers and "Change style". */
        val quartz: List<WidgetKind> get() = entries.filter { it != ANDROID }

        /** The ones to choose from for your home page in setup (tips come with every new home screen anyway). */
        val forSetup: List<WidgetKind> get() = listOf(CLOCK, FLIP, FLIP_WEATHER, WEATHER)

        val dates: Set<WidgetKind> = setOf(DAY_DATE, DATE, CALENDAR_PAGE, DATE_YEAR, MONTH, WEEK, DATE_LINE, DAY_SMALL, DAY_ORDINAL, DATE_NUMBERS)
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
    /**
     * What a tap opens: an app's key, [OPENS_NOTHING], or empty for the usual (your clock for clocks, your calendar
     * for dates, a weather refresh for weather).
     */
    val opens: String = "",
)

const val OPENS_NOTHING = "none"

const val WIDGET_PREFIX = "widget:"

fun isWidgetKey(key: String?) = key != null && key.startsWith(WIDGET_PREFIX)

internal object WidgetJson {
    fun write(widgets: Collection<HomeWidget>): String {
        val array = JSONArray()
        widgets.forEach { w ->
            val o = JSONObject().put("id", w.id).put("kind", w.kind.name).put("w", w.w).put("h", w.h)
            if (w.kind == WidgetKind.ANDROID) o.put("appWidgetId", w.appWidgetId).put("provider", w.provider).put("glass", w.glass)
            if (w.opens.isNotEmpty()) o.put("opens", w.opens)
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
                    opens = o.optString("opens", ""),
                )
            }
        }.getOrElse {
            Diag.log("Couldn't read widgets: ${it.message}")
            emptyList()
        }
    }
}
