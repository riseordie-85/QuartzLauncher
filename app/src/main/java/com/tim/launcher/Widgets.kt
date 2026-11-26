package com.tim.launcher

import org.json.JSONArray
import org.json.JSONObject

/**
 * Quartz's own home screen widgets. Each one covers a block of cells on a page: every cell it covers holds
 * "widget:<id>" in the page's slot list, so icons flow around it and everything that moves pages and grids carries
 * it along. Its kind and size live here.
 */
/** How the widget list is grouped. */
enum class WidgetGroup(val label: String) {
    CLOCKS("Clocks"), DATES("Dates"), WEATHER("Weather"), FORECASTS("Forecasts"), PLACES("Two places"), OTHER("Tips"),
}

/**
 * Changes to a widget made away from the home screen (its settings page), for the home screen to apply. Same
 * process, so a plain list of changes is enough.
 */
object WidgetBus {
    val changes = androidx.compose.runtime.mutableStateListOf<Pair<String, (HomeWidget) -> HomeWidget>>()

    fun change(id: String, change: (HomeWidget) -> HomeWidget) {
        changes += id to change
    }
}

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
    WEATHER("Weather", "Your town, the sky, low and high, and the temperature", 4, 1, 2, 1, true),
    TIPS("Quartz tips", "Where to find wallpaper, setup and customizing", 4, 2, 2, 2, false),

    // Weather for one day (tapping one opens the weather page)
    W_TEMP("Temperature", "Just the temperature", 1, 1, 1, 1, true),
    W_ICON("Weather icon", "Just the sky, as an icon", 1, 1, 1, 1, true),
    W_HILO("Low and high", "Today's low over its high", 1, 1, 1, 1, true),
    W_TALL("Weather, tall", "Icon, temperature, low and high", 1, 2, 1, 2, true),
    W_SMALL("Weather, small", "Icon, temperature, low and high", 2, 1, 2, 1, true),
    W_CARD("Weather card", "Low, high and temperature over a big icon on paper", 2, 2, 2, 2, true),
    W_BIG("Big weather", "A big icon with low, high and current", 2, 2, 2, 2, true),
    W_FLIP_SMALL("Flip clock and weather, small", "The time over today's weather", 2, 2, 2, 2, true),
    W_WIND("Wind", "Speed, direction and gusts", 2, 1, 2, 1, true),
    W_SUN("Sunrise and sunset", "Today's sunrise and sunset", 2, 1, 2, 1, true),
    W_DETAILS("Weather details", "Feels like, wind, humidity, and air quality or UV", 2, 2, 2, 2, true),

    // Forecasts
    W_HOURLY("Next hours", "The next six hours", 4, 1, 3, 1, true),
    W_FORECAST_ROW("5-day forecast", "The next five days in a row", 4, 1, 3, 1, true),
    W_FLIP_FORECAST("Flip clock and forecast", "The time, today and the next five days", 4, 2, 4, 2, true),
    W_FORECAST("Today and 5 days", "Today's weather over the next five days, one per row", 4, 3, 4, 3, true),

    // Two places
    W_DUAL("Two places", "Your weather next to another place's", 4, 1, 3, 1, true),
    W_DUAL_TALL("Two places with forecast", "Two places side by side, each with three days", 4, 2, 4, 2, true),

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

    /** Which group it's listed under. */
    val group: WidgetGroup
        get() = when {
            this == TIPS -> WidgetGroup.OTHER
            this in duals -> WidgetGroup.PLACES
            this in forecasts -> WidgetGroup.FORECASTS
            weather -> WidgetGroup.WEATHER
            this in dates -> WidgetGroup.DATES
            else -> WidgetGroup.CLOCKS
        }

    companion object {
        /** Quartz's own widgets, for pickers and "Change style". */
        val quartz: List<WidgetKind> get() = entries.filter { it != ANDROID }

        /** The ones to choose from for your home page in setup (tips come with every new home screen anyway). */
        val forSetup: List<WidgetKind> get() = listOf(CLOCK, FLIP, FLIP_WEATHER, WEATHER)

        // ---- Which settings each widget uses (Widget settings shows only those for the widget it was opened from) ----

        /** Flip cards that turn over. */
        val flips: Set<WidgetKind> = setOf(FLIP, FLIP_WEATHER, W_FLIP_SMALL, W_FLIP_FORECAST)

        /** Shows a time of day: the clocks, and hours or sunrise and sunset. */
        val times: Set<WidgetKind> = setOf(CLOCK, FLIP, FLIP_WEATHER, W_FLIP_SMALL, W_FLIP_FORECAST, W_HOURLY, W_SUN)

        /** Uses the card color: flip cards, or the "paper" parts of the date and weather cards. */
        val cards: Set<WidgetKind> = flips + setOf(DAY_DATE, CALENDAR_PAGE, MONTH, WEEK, W_CARD)

        /** Uses the accent color somewhere. */
        val accents: Set<WidgetKind> = setOf(
            CLOCK, TIPS, DATE, DATE_YEAR, DATE_LINE, DATE_NUMBERS,
            WEATHER, W_TEMP, W_TALL, W_SMALL, W_CARD, W_BIG, W_WIND, W_DETAILS, W_FORECAST, W_DUAL, W_DUAL_TALL,
        )

        /** Has weather icons that move. */
        val moving: Set<WidgetKind> get() = entries.filter { it.weather && it !in setOf(W_TEMP, W_HILO, W_WIND, W_DETAILS) }.toSet()

        val forecasts: Set<WidgetKind> = setOf(W_HOURLY, W_FORECAST_ROW, W_FLIP_FORECAST, W_FORECAST)

        /** Weather widgets showing two places. */
        val duals: Set<WidgetKind> = setOf(W_DUAL, W_DUAL_TALL)

        /** Weather widgets whose icon can be made big enough to spill past the panel. */
        val bigIcons: Set<WidgetKind> = setOf(WEATHER, W_ICON, W_TALL, W_SMALL, W_BIG, W_FLIP_SMALL, W_FLIP_FORECAST)

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
    /** A weather widget's place: empty for your own weather, else a saved place's key. */
    val place: String = "",
    /** A two-place weather widget's other place. */
    val place2: String = "",
    /** A weather widget's icon drawn big, spilling past the panel. */
    val bigIcon: Boolean = false,
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
            if (w.place.isNotEmpty()) o.put("place", w.place)
            if (w.place2.isNotEmpty()) o.put("place2", w.place2)
            if (w.bigIcon) o.put("bigIcon", true)
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
                    place = o.optString("place", ""),
                    place2 = o.optString("place2", ""),
                    bigIcon = o.optBoolean("bigIcon", false),
                )
            }
        }.getOrElse {
            Diag.log("Couldn't read widgets: ${it.message}")
            emptyList()
        }
    }
}
