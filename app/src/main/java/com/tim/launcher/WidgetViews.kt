package com.tim.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.tim.launcher.settings.DockOutline
import com.tim.launcher.settings.DockPosition
import com.tim.launcher.settings.S
import com.tim.launcher.settings.SettingsStore
import com.tim.launcher.settings.TempUnit
import com.tim.launcher.weather.Weather
import com.tim.launcher.weather.WeatherNow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Outfit, a geometric sans (SIL Open Font License), for the widgets' numbers and words. */
val Outfit = FontFamily(
    Font(R.font.outfit_light, FontWeight.Light),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
)

/** Shown in the widget picker before any real weather has arrived. */
private val SampleWeather = WeatherNow("Denver", 9.0, 9.0, -7.8, 51, true, 0L, "")

/** The time, updated as each minute turns over (and whenever the home screen comes back). */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        now = System.currentTimeMillis()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val t = System.currentTimeMillis()
            delay(60_000L - t % 60_000L + 30L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * One of Quartz's widgets, drawn to fill [modifier]'s size. It sits on a glass card styled like your dock (same
 * glass, gradient, pattern and edge light). [sample] shows made-up weather until real weather arrives (the picker).
 */
@Composable
fun WidgetView(kind: WidgetKind, modifier: Modifier = Modifier, sample: Boolean = false) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    remember { Weather.loadCache(context); true }
    val now = rememberNow()
    val ink = LocalHomeLook.current.onPanel
    val accent = Color(store[S.widgetColor])
    val motion = store[S.widgetMotion]
    val h24 = store[S.clock24h]
    val unit = store[S.weatherUnits]
    val weather = Weather.now ?: if (sample) SampleWeather else null

    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val hours = if (h24) "%02d".format(hour) else ((hour + 11) % 12 + 1).toString()
    val minutes = "%02d".format(cal.get(Calendar.MINUTE))

    // Laid out left to right in every language: `at` places by absolute x, and in a right-to-left layout the Box
    // would put each zero-size `at` child at its right edge, pushing everything out of the widget
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        when (kind) {
            WidgetKind.CLOCK -> {
                WidgetCard(Modifier.fillMaxSize())
                var t = minOf(w * 0.3f, h * 0.5f)
                val dateSize = t * 0.27f
                val showDate = dateSize >= 9.dp
                if (!showDate) t = minOf(w * 0.3f, h * 0.7f)
                Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    WText("$hours:$minutes", t, ink, FontWeight.ExtraBold)
                    if (showDate) WText(dateText(now, weekday = false), dateSize, accent, FontWeight.ExtraBold)
                }
            }

            WidgetKind.FLIP -> {
                // Laid out on an 832 × 402 plan (the cards rise above the panel), scaled to fit
                val u = Plan(w, h, 832f, 402f)
                WidgetCard(Modifier.at(0.dp, u.top + u(42f), w, u(360f)))
                FlipPair(hours, minutes, u, w, accent, motion)
                Slot(0.dp, u.top + u(284f), w, u(118f), Alignment.Center) {
                    WText(dateText(now, weekday = true), u(34f), ink, FontWeight.Light, Modifier.fillMaxWidth(), TextAlign.Center)
                }
            }

            WidgetKind.FLIP_WEATHER -> {
                val u = Plan(w, h, 832f, 440f)
                WidgetCard(Modifier.at(0.dp, u.top + u(42f), w, u(360f)))
                FlipPair(hours, minutes, u, w, accent, motion)
                WeatherRow(weather, unit, ink, motion, u, w, rowTop = u.top + u(262f), rowHeight = u(120f), iconSize = u(380f), iconTop = u.top + u(132f))
            }

            WidgetKind.WEATHER -> {
                val u = Plan(w, h, 832f, 200f)
                WidgetCard(Modifier.at(0.dp, u.top + u(40f), w, u(160f)))
                WeatherRow(weather, unit, ink, motion, u, w, rowTop = u.top + u(40f), rowHeight = u(160f), iconSize = u(340f), iconTop = u.top - u(85f))
            }
        }
    }
    }
}

/** A plan [pw] × [ph] scaled to fit [w] × [h] and centered top to bottom: u(x) turns plan units into dp. */
private class Plan(w: Dp, h: Dp, pw: Float, ph: Float) {
    private val s = minOf(w.value / pw, h.value / ph)
    val top: Dp = (h - (ph * s).dp) / 2
    operator fun invoke(v: Float): Dp = (v * s).dp
}

/** The glass card behind a widget: your dock's glass, color, pattern and edge light, as a rounded panel. */
@Composable
private fun WidgetCard(modifier: Modifier) {
    val look = LocalHomeLook.current
    val style = look.dock.copy(background = true, outline = DockOutline.STRAIGHT, floating = true, radius = look.dock.radius.coerceIn(14, 36))
    Box(modifier.dockSurface(style, look.panel, DockPosition.BOTTOM, look.onPanel))
}

/** The hours and minutes cards, side by side above the middle of the panel. */
@Composable
private fun FlipPair(hours: String, minutes: String, u: Plan, w: Dp, color: Color, motion: Boolean) {
    val cardW = u(228f)
    val cardH = u(284f)
    val gap = u(40f)
    val x = w / 2
    FlipCard(hours, color, motion, Modifier.at(x - gap / 2 - cardW, u.top, cardW, cardH))
    FlipCard(minutes, color, motion, Modifier.at(x + gap / 2, u.top, cardW, cardH))
}

/**
 * Where you are, what it's like, an icon and the temperature, with today's high over its low. The icon is bigger
 * than the row and spills over the panel's edge.
 */
@Composable
private fun WeatherRow(
    weather: WeatherNow?,
    unit: TempUnit,
    ink: Color,
    motion: Boolean,
    u: Plan,
    w: Dp,
    rowTop: Dp,
    rowHeight: Dp,
    iconSize: Dp,
    iconTop: Dp,
) {
    val mid = w / 2
    // The icon's cloud takes about the middle 60% of its square; text keeps clear of it
    val clear = iconSize * 0.33f
    val textSize = u(34f)
    Slot(u(40f), rowTop, (mid - clear - u(40f)).coerceAtLeast(0.dp), rowHeight, Alignment.CenterStart) {
        Column {
            WText(weather?.place ?: "Weather", textSize, ink, FontWeight.Light)
            WText(
                weather?.condition ?: (Weather.problem?.let { "Tap to set up" } ?: "Updating…"),
                textSize, ink, FontWeight.Light,
            )
        }
    }
    WeatherIcon(weather?.icon ?: "not-available", motion, Modifier.at(mid - iconSize / 2, iconTop, iconSize, iconSize))
    Slot(mid + clear, rowTop, (w - mid - clear - u(40f)).coerceAtLeast(0.dp), rowHeight, Alignment.CenterEnd) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WText(weather?.let { Weather.format(it.tempC, unit) } ?: "--°", u(84f), ink, FontWeight.Light)
            if (weather != null) {
                Spacer(Modifier.width(u(16f)))
                // High on top, low under it
                Column {
                    WText("↑ " + Weather.format(weather.highC, unit), u(30f), ink, FontWeight.Light)
                    WText("↓ " + Weather.format(weather.lowC, unit), u(30f), ink.copy(alpha = ink.alpha * 0.8f), FontWeight.Light)
                }
            }
        }
    }
}

/** A Meteocons weather icon, playing while the home screen is showing (still when [motion] is off). */
@Composable
fun WeatherIcon(name: String, motion: Boolean, modifier: Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("weather/$name.json"))
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val playing = motion && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever, isPlaying = playing)
    LottieAnimation(composition, { if (motion) progress else 0.25f }, modifier)
}

/**
 * A split-flap card. When [text] changes, the top half of the old card folds down over the new one, shading as
 * it turns, and lands as the new bottom half. With [motion] off it just changes.
 */
@Composable
private fun FlipCard(text: String, color: Color, motion: Boolean, modifier: Modifier) {
    // What's fully showing, and what it's turning to
    var shown by remember { mutableStateOf(text) }
    var next by remember { mutableStateOf(text) }
    val turn = remember { Animatable(1f) }
    LaunchedEffect(text, motion) {
        if (text == next && turn.value >= 1f) {
            shown = text
            return@LaunchedEffect
        }
        if (!motion) {
            shown = text
            next = text
            turn.snapTo(1f)
            return@LaunchedEffect
        }
        shown = next // an earlier turn cut short finishes where it was going
        next = text
        turn.snapTo(0f)
        turn.animateTo(1f, tween(640, easing = FastOutSlowInEasing))
        shown = text
    }
    BoxWithConstraints(modifier) {
        val h = maxHeight
        val corner = RoundedCornerShape(maxWidth * 0.12f)
        val p = turn.value
        val camera = LocalDensity.current.density * 14f
        Box(Modifier.fillMaxSize().clip(corner)) {
            Column {
                // The new value's top shows behind the falling flap; the old bottom stays until the flap covers it
                Half(top = true, full = h) { Face(next, color, shade = if (p < 0.5f) 0.35f * (1f - p * 2f) else 0f) }
                Half(top = false, full = h) { Face(shown, color, shade = if (p < 0.5f) 0.22f * p * 2f else 0f) }
            }
            if (p < 0.5f) {
                Half(
                    top = true, full = h,
                    modifier = Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        rotationX = -180f * p
                        cameraDistance = camera
                    },
                ) { Face(shown, color, shade = 0.45f * p * 2f) }
            } else if (p < 1f) {
                Half(
                    top = false, full = h,
                    modifier = Modifier
                        .offset(y = h / 2)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            rotationX = 180f * (1f - p)
                            cameraDistance = camera
                        },
                ) { Face(next, color, shade = 0.4f * (1f - p) * 2f) }
            }
            // The split across the middle
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(h)
                    .drawWithContent {
                        val y = size.height / 2
                        val line = 1.5.dp.toPx()
                        drawRect(Color.Black.copy(alpha = 0.32f), topLeft = androidx.compose.ui.geometry.Offset(0f, y - line / 2), size = androidx.compose.ui.geometry.Size(size.width, line))
                        drawRect(Color.White.copy(alpha = 0.10f), topLeft = androidx.compose.ui.geometry.Offset(0f, y + line / 2), size = androidx.compose.ui.geometry.Size(size.width, line / 2))
                    },
            )
        }
    }
}

/** The top or bottom half of a card face (the whole face is drawn, only that half shows). */
@Composable
private fun Half(top: Boolean, full: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().height(full / 2).clipToBounds()) {
        Box(Modifier.fillMaxWidth().wrapContentHeight(if (top) Alignment.Top else Alignment.Bottom, unbounded = true).height(full)) {
            content()
        }
    }
}

/** A card face: the color, lighter at the top, with the number in the middle. [shade] darkens it as it turns. */
@Composable
private fun Face(text: String, color: Color, shade: Float) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to lerp(color, Color.White, 0.12f),
                    0.5f to color,
                    0.5f to lerp(color, Color.Black, 0.06f),
                    1f to lerp(color, Color.Black, 0.2f),
                ),
            )
            .drawWithContent {
                drawContent()
                if (shade > 0f) drawRect(Color.Black.copy(alpha = shade.coerceIn(0f, 1f)))
            },
        contentAlignment = Alignment.Center,
    ) {
        // Outfit's digits are a little over half as wide as they're tall
        val size = minOf(maxHeight * 0.66f, maxWidth * 0.8f / (0.58f * maxOf(2, text.length)))
        WText(text, size, Color.White, FontWeight.ExtraBold)
    }
}

/** Text sized in dp (not scaled up by the font size setting, so it always fits its widget). */
@Composable
private fun WText(
    text: String,
    size: Dp,
    color: Color,
    weight: FontWeight,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
) {
    val sp = with(LocalDensity.current) { size.toSp() }
    Text(
        text,
        modifier,
        color = color,
        fontSize = sp,
        fontFamily = Outfit,
        fontWeight = weight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        lineHeight = sp * 1.15f,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
    )
}

/** A box at ([x], [y]) inside the widget, [w] × [h], holding [content] at [alignment]. */
@Composable
private fun Slot(x: Dp, y: Dp, w: Dp, h: Dp, alignment: Alignment, content: @Composable () -> Unit) {
    Box(Modifier.at(x, y, w, h), contentAlignment = alignment) { content() }
}

/**
 * Places this at ([x], [y]) inside its parent, exactly [w] × [h], whatever room the parent has: the widget's icon and
 * flip cards spill past the panel on purpose.
 */
private fun Modifier.at(x: Dp, y: Dp, w: Dp, h: Dp) = layout { m, _ ->
    val placeable = m.measure(Constraints.fixed(w.roundToPx().coerceAtLeast(0), h.roundToPx().coerceAtLeast(0)))
    layout(0, 0) { placeable.place(x.roundToPx(), y.roundToPx()) }
}

/** "December 25th 2026", or with the weekday: "Friday, December 25th 2026". */
fun dateText(now: Long, weekday: Boolean): String {
    val locale = Locale.getDefault()
    val date = Date(now)
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val dayText = if (locale.language == "en") day.toString() + ordinal(day) else day.toString()
    val base = SimpleDateFormat("MMMM", locale).format(date) + " " + dayText + " " + cal.get(Calendar.YEAR)
    return if (weekday) SimpleDateFormat("EEEE", locale).format(date) + ", " + base else base
}

private fun ordinal(day: Int): String = when {
    day % 100 in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}
