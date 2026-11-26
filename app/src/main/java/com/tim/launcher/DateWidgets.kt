package com.tim.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * The date widgets: on your widget panel, with "paper" (the flip cards' color) for the parts that stand out, the
 * accent for the bits picked out, light and bold weights of the widget font. Text on paper takes the panel's own
 * color when it reads well there (navy on white), else whatever reads.
 *
 * Each is drawn to fill its cell, with text sized from a plan of [pw] × [ph] units so it scales with the widget.
 */
@Composable
internal fun DateWidget(kind: WidgetKind, now: Long, w: Dp, h: Dp, ink: Color, paper: Color, accent: Color) {
    val panel = LocalHomeLook.current.panel.copy(alpha = 1f)
    val onPaper = if (abs(panel.luminance() - paper.luminance()) > 0.35f) panel else inkOn(paper)
    val d = androidx.compose.runtime.remember(now) { DateParts(now) }
    // A plan's units in dp, for this widget's size
    fun plan(pw: Float, ph: Float): (Float) -> Dp {
        val s = minOf(w.value / pw, h.value / ph)
        return { v -> (v * s).dp }
    }
    val light = FontWeight.Light
    val bold = FontWeight.Bold

    when (kind) {
        // "W | 16": the weekday's letter on the panel, the date on paper
        WidgetKind.DAY_DATE -> {
            val u = plan(400f, 180f)
            WidgetCard(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { WText(d.dayLetter, u(124f), ink, light) }
                    Box(Modifier.weight(1f).fillMaxHeight().background(paper), contentAlignment = Alignment.Center) { WText(d.day, u(124f), onPaper, bold) }
                }
            }
        }

        // "Dec / 16th"
        WidgetKind.DATE -> {
            val u = plan(400f, 180f)
            WidgetCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(horizontal = u(30f)), verticalArrangement = Arrangement.Center) {
                    WText(d.monthShort, u(92f), ink, light, line = 0.86f)
                    WText(d.ordinal, u(92f), accent, bold, line = 0.86f)
                }
            }
        }

        // A tear-off page: "DECEMBER" on the panel, then the day and weekday on paper
        WidgetKind.CALENDAR_PAGE -> {
            val u = plan(400f, 400f)
            WidgetCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(0.26f).padding(horizontal = u(16f)), contentAlignment = Alignment.Center) {
                        WText(d.monthLong.uppercase(), u(68f), ink, bold)
                    }
                    Column(
                        Modifier.fillMaxWidth().weight(1f).background(paper),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        WText(d.day, u(236f), onPaper, bold, line = 0.82f)
                        WText(d.weekdayLong.uppercase(), u(46f), onPaper, light)
                    }
                }
            }
        }

        // "Dec / 16th / 2026", big
        WidgetKind.DATE_YEAR -> {
            val u = plan(400f, 400f)
            WidgetCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(horizontal = u(30f)), verticalArrangement = Arrangement.Center) {
                    WText(d.monthShort, u(126f), ink, light, line = 0.88f)
                    WText(d.ordinal, u(126f), accent, bold, line = 0.88f)
                    WText(d.year, u(126f), ink, light, line = 0.88f)
                }
            }
        }

        // The month: its name, the weekdays on a paper band, the dates with today circled in paper
        WidgetKind.MONTH -> {
            val u = plan(400f, 400f)
            WidgetCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(u(10f))) {
                    Box(Modifier.fillMaxWidth().height(u(58f)), contentAlignment = Alignment.Center) {
                        WText(d.monthLong.uppercase(), u(46f), ink, bold)
                    }
                    WeekdayBand(d, u(44f), u(32f), paper, onPaper)
                    val weeks = d.monthWeeks()
                    Column(Modifier.fillMaxWidth().weight(1f).padding(top = u(6f))) {
                        weeks.forEach { week ->
                            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                week.forEach { date ->
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        if (date != null) {
                                            val text = "%02d".format(date)
                                            if (date == d.dayOfMonth) {
                                                Box(Modifier.size(u(44f)).background(paper, CircleShape), contentAlignment = Alignment.Center) {
                                                    WText(text, u(30f), onPaper, bold)
                                                }
                                            } else {
                                                WText(text, u(30f), ink, bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // This week: the weekdays on a paper band, the dates under them, today circled
        WidgetKind.WEEK -> {
            val u = plan(826f, 135f)
            WidgetCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(u(10f))) {
                    WeekdayBand(d, u(48f), u(36f), paper, onPaper)
                    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        d.weekDates().forEach { (date, today) ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (today) {
                                    Box(Modifier.size(u(56f)).background(paper, CircleShape), contentAlignment = Alignment.Center) {
                                        WText(date.toString(), u(36f), onPaper, bold)
                                    }
                                } else {
                                    WText(date.toString(), u(38f), ink, light)
                                }
                            }
                        }
                    }
                }
            }
        }

        // "DEC WED 16" in one line
        WidgetKind.DATE_LINE -> {
            val u = plan(826f, 135f)
            WidgetCard(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(horizontal = u(30f)), contentAlignment = Alignment.Center) {
                    val text = buildAnnotatedString {
                        withStyle(SpanStyle(color = ink, fontWeight = light)) { append(d.monthShort.uppercase() + " ") }
                        withStyle(SpanStyle(color = accent, fontWeight = light)) { append(d.weekdayShort.uppercase() + " ") }
                        withStyle(SpanStyle(color = ink, fontWeight = bold)) { append(d.day) }
                    }
                    WText(text, u(108f), ink, light)
                }
            }
        }

        // Small: "W16"
        WidgetKind.DAY_SMALL -> {
            val u = plan(190f, 135f)
            WidgetCard(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(horizontal = u(14f)), contentAlignment = Alignment.Center) {
                    val text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = light)) { append(d.dayLetter) }
                        withStyle(SpanStyle(fontWeight = bold)) { append(d.day) }
                    }
                    WText(text, u(78f), ink, light)
                }
            }
        }

        // Small: "16th"
        WidgetKind.DAY_ORDINAL -> {
            val u = plan(190f, 135f)
            WidgetCard(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(horizontal = u(14f)), contentAlignment = Alignment.Center) {
                    WText(d.ordinal, u(78f), ink, bold)
                }
            }
        }

        // Small: "12/16", the numbers in your country's order, the marks between them in the accent
        WidgetKind.DATE_NUMBERS -> {
            val u = plan(190f, 135f)
            WidgetCard(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(horizontal = u(12f)), contentAlignment = Alignment.Center) {
                    val text = buildAnnotatedString {
                        d.numeric.forEach { c ->
                            withStyle(SpanStyle(color = if (c.isDigit()) ink else accent)) { append(c) }
                        }
                    }
                    WText(text, u(70f), ink, bold)
                }
            }
        }

        else -> Unit
    }
}

/** The week's day letters, bold, on a rounded paper band. */
@Composable
private fun WeekdayBand(d: DateParts, height: Dp, size: Dp, paper: Color, onPaper: Color) {
    Row(
        Modifier.fillMaxWidth().height(height).background(paper, CircleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        d.weekLetters().forEach { letter ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { WText(letter, size, onPaper, FontWeight.Bold) }
        }
    }
}

/** Today's date in the pieces the widgets show, in your language and with your week's first day. */
internal class DateParts(now: Long) {
    private val locale: Locale = Locale.getDefault()
    private val date = Date(now)
    private val cal: Calendar = Calendar.getInstance(locale).apply { timeInMillis = now }

    val dayOfMonth: Int = cal.get(Calendar.DAY_OF_MONTH)
    val day: String = dayOfMonth.toString()
    val year: String = cal.get(Calendar.YEAR).toString()

    /** "16th" in English; just the number elsewhere. */
    val ordinal: String = if (locale.language == "en") day + suffix(dayOfMonth) else day

    /** "Dec", "December" (the forms used on their own, which some languages spell differently). */
    val monthShort: String = SimpleDateFormat("LLL", locale).format(date).trimEnd('.').cap()
    val monthLong: String = SimpleDateFormat("LLLL", locale).format(date).cap()
    val weekdayShort: String = SimpleDateFormat("EEE", locale).format(date).trimEnd('.').cap()
    val weekdayLong: String = SimpleDateFormat("EEEE", locale).format(date).cap()
    val dayLetter: String = SimpleDateFormat("EEEEE", locale).format(date).uppercase(locale)

    /** "12/16", "16/12" or "16.12.", as your country writes a month and day in numbers. */
    val numeric: String = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "Md"), locale).format(date)

    /** The week's day letters, starting on your first day of the week. */
    fun weekLetters(): List<String> {
        val f = SimpleDateFormat("EEEEE", locale)
        val c = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }
        return List(7) { f.format(c.time).uppercase(locale).also { c.add(Calendar.DAY_OF_MONTH, 1) } }
    }

    /** This week's dates, and which is today. */
    fun weekDates(): List<Pair<Int, Boolean>> {
        val c = (cal.clone() as Calendar)
        // Back to the week's first day (which can be in last month)
        while (c.get(Calendar.DAY_OF_WEEK) != c.firstDayOfWeek) c.add(Calendar.DAY_OF_MONTH, -1)
        return List(7) {
            val today = c.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) && c.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
            (c.get(Calendar.DAY_OF_MONTH) to today).also { c.add(Calendar.DAY_OF_MONTH, 1) }
        }
    }

    /** This month as weeks of seven, with nulls before the 1st and after the last day. */
    fun monthWeeks(): List<List<Int?>> {
        val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val lead = (first.get(Calendar.DAY_OF_WEEK) - first.firstDayOfWeek + 7) % 7
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = List(lead) { null } + (1..days).map { it }
        return cells.chunked(7).map { it + List(7 - it.size) { null } }
    }

    private fun String.cap() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    private fun suffix(day: Int): String = when {
        day % 100 in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
}
