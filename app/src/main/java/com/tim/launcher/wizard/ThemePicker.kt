package com.tim.launcher.wizard

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.AppEntry
import com.tim.launcher.DockStyle
import com.tim.launcher.HomeLook
import com.tim.launcher.IconRender
import com.tim.launcher.IconSlot
import com.tim.launcher.Ink
import com.tim.launcher.LocalGlass
import com.tim.launcher.LocalHomeLook
import com.tim.launcher.StyledIcon
import com.tim.launcher.WidgetView
import com.tim.launcher.settings.HomeTheme
import com.tim.launcher.settings.HomeThemes
import com.tim.launcher.settings.MiniDock
import com.tim.launcher.settings.S
import com.tim.launcher.settings.rememberSampleApps
import com.tim.launcher.ui.Q
import com.tim.launcher.wallpaper.FoldMode
import com.tim.launcher.wallpaper.Frame
import com.tim.launcher.wallpaper.ScreenKind
import com.tim.launcher.wallpaper.WallCache
import com.tim.launcher.wallpaper.WallRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * "Choose your Home Screen": every theme as a little home screen the shape of this screen, drawn live with the real
 * dock, icon and widget drawing code over the theme's wallpaper. Swipe through; the one in the middle is [selected].
 */
@Composable
internal fun ThemeCarousel(selected: Int, onSelect: (Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val themes = remember { HomeThemes.all(context) }
    val config = LocalConfiguration.current
    // This screen's shape, so the pictures look like your own home screen
    val aspect = (config.screenWidthDp.toFloat() / config.screenHeightDp.coerceAtLeast(1)).coerceIn(0.35f, 1.8f)
    val pager = rememberPagerState(initialPage = selected.coerceIn(0, themes.lastIndex)) { themes.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { onSelect(pager.currentPage) }
    val apps = rememberSampleApps(8)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tall = (config.screenHeightDp * 0.46f).coerceIn(220f, 460f).dp
        // Read here: inside the Column below, BoxWithConstraints' maxWidth can't be reached
        val fullW = maxWidth
        val cardW = minOf(tall * aspect, fullW * 0.78f)
        val cardH = cardW / aspect
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(
                pager,
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = ((fullW - cardW) / 2).coerceAtLeast(0.dp)),
                pageSize = PageSize.Fixed(cardW),
                pageSpacing = 14.dp,
                beyondViewportPageCount = 1,
            ) { page ->
                // The ones either side sit back a little
                val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .graphicsLayer {
                            val s = 1f - 0.08f * off
                            scaleX = s
                            scaleY = s
                            alpha = 1f - 0.35f * off
                        }
                        .clickable { if (page != pager.currentPage) scope.launch { pager.animateScrollToPage(page) } },
                ) {
                    ThemeCard(themes[page], cardW, cardH, apps, page == pager.currentPage)
                }
            }
            val theme = themes[pager.currentPage]
            Text(theme.name, color = Q.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            Text(
                theme.about,
                color = Q.TextDim,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp),
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                themes.indices.forEach { i ->
                    val on by animateFloatAsState(if (i == pager.currentPage) 1f else 0f, label = "dot")
                    Box(
                        Modifier
                            .height(6.dp)
                            .width((6 + 12 * on).dp)
                            .clip(CircleShape)
                            .background(if (i == pager.currentPage) Q.Accent else Q.GlassStrong),
                    )
                }
            }
        }
    }
}

/** The theme's wallpaper at a card's size, and a blurred copy for its glass (none when the theme has no blur). */
private class ThemeWall(val sharp: Bitmap, val blurred: Bitmap?, val brightness: Float)

/** One theme as a small home screen: a widget at the top, a row of apps, and the dock. Also Settings › Themes. */
@Composable
internal fun ThemeCard(theme: HomeTheme, w: Dp, h: Dp, apps: List<AppEntry>, current: Boolean) {
    val density = LocalDensity.current
    val pxW = with(density) { w.roundToPx() }.coerceAtLeast(1)
    val pxH = with(density) { h.roundToPx() }.coerceAtLeast(1)
    val blur = theme.look.get(S.panelBlur)
    val cardContext = androidx.compose.ui.platform.LocalContext.current
    val wall by produceState<ThemeWall?>(null, theme, pxW, pxH) {
        value = withContext(Dispatchers.Default) {
            val frame = Frame(theme.wallConfig, pxW / pxH.toFloat(), 0f, 1f, ScreenKind.SINGLE, FoldMode.STRETCHED)
            // A theme's photo comes from the app's own copy, read small
            val photo = theme.wallImage?.let { name ->
                runCatching {
                    val path = "themes/files/$name"
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    cardContext.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                    // The card's picture is drawn at half size, so the photo only needs to be that big
                    val needW = (pxW / 2).coerceAtLeast(1)
                    val needH = (pxH / 2).coerceAtLeast(1)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= needW && bounds.outHeight / (sample * 2) >= needH) sample *= 2
                    cardContext.assets.open(path).use {
                        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                }.getOrNull()
            }
            val sharp = WallRenderer.renderSmall(pxW, pxH, 0.5f, frame) { if (it == theme.wallImage) photo else null }
            val blurred = if (blur < 0.03f) null else sharp.copy(Bitmap.Config.ARGB_8888, true).also {
                WallRenderer.blur(it, (2 + blur * 12).roundToInt())
            }
            ThemeWall(sharp, blurred, brightness(sharp))
        }
    }
    var at by remember { mutableStateOf(Offset.Zero) }
    val glass = wall?.blurred?.let { WallCache.Glass(it, pxW, pxH, wall?.brightness ?: 0.3f, blurred = true, left = at.x, top = at.y) }
    val style = DockStyle.of(theme.look)
    val panel = Color(theme.look.get(S.panelColor)).copy(alpha = theme.look.get(S.panelOpacity).coerceIn(0.05f, 1f))
    // The theme's widget font: one of its own (read from the app's copy) or a built-in one
    val widgetFont by produceState<androidx.compose.ui.text.font.FontFamily?>(null, theme) {
        value = withContext(Dispatchers.IO) { com.tim.launcher.Fonts.themeFamily(cardContext, theme.look.get(S.fontWidgets), theme.fontFiles) }
    }
    val look = HomeLook(panel = panel, dock = style, onPanel = Ink.on(Ink.brightness(panel, wall?.brightness ?: 0.3f)))
    // The theme's icons with your icon pack, which picking a theme keeps
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { com.tim.launcher.settings.SettingsStore.get(context) }
    val pack = store[S.iconPackHome]
    val force = store[S.iconForceShape]
    val homeSpec = remember(theme, pack, force) {
        val values = object : IconRender.Values {
            @Suppress("UNCHECKED_CAST")
            override fun <T> get(setting: com.tim.launcher.settings.Setting<T>): T = when (setting) {
                S.iconPackHome -> pack as T
                S.iconForceShape -> force as T
                else -> theme.look.get(setting)
            }
            override fun <T> place(own: com.tim.launcher.settings.Setting<T>?, home: com.tim.launcher.settings.Setting<T>): T = get(home)
        }
        IconRender.specFor(values, IconSlot.HOME)
    }
    // Sizes from the narrower of the card's width and a phone-shaped share of its height, so a wide tablet card
    // doesn't get giant icons
    val unit = min(w.value, h.value * 0.6f)
    val icon = (unit * 0.14f).roundToInt()
    // Rounder on a big card, less so on a small one, like a real screen's corners
    val shape = RoundedCornerShape((unit * 0.09f).coerceIn(10f, 22f).dp)

    Box(
        Modifier
            .size(w, h)
            .clip(shape)
            .border(if (current) 2.dp else 1.dp, if (current) Color.White.copy(alpha = 0.9f) else Q.Edge, shape)
            .background(Color(0xFF1A1826))
            .onGloballyPositioned { at = it.positionInWindow() },
    ) {
        wall?.let { Image(it.sharp.asImageBitmap(), contentDescription = theme.name, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
        CompositionLocalProvider(LocalGlass provides glass, LocalHomeLook provides look) {
            Column(Modifier.fillMaxSize().padding(horizontal = (unit * 0.06f).dp).padding(top = (unit * 0.1f).dp)) {
                val widgetW = (w.value - unit * 0.12f)
                WidgetView(
                    theme.widget,
                    Modifier.fillMaxWidth().height((minOf(widgetW, unit * 1.2f) * 0.5f).dp),
                    sample = true,
                    cardColor = Color(theme.cards),
                    accentColor = Color(theme.accent),
                    // (Its font id too, so a built-in one shows at once while a theme's own loads)
                    fontId = theme.look.get(S.fontWidgets),
                    fontFamily = widgetFont,
                )
                Spacer(Modifier.weight(1f))
                // A row of apps above the dock
                Row(Modifier.fillMaxWidth().padding(bottom = (icon * 2.4f).dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(4) { i ->
                        val app = apps.getOrNull(i + 4)
                        if (app != null) StyledIcon(app, icon.dp, spec = homeSpec)
                        else Box(Modifier.size(icon.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)))
                    }
                }
            }
            MiniDock(theme.look, apps, icon = icon, count = 4, spec = homeSpec)
        }
    }
}

/** How light a picture is overall, 0 dark to 1 light, from a grid of samples. */
private fun brightness(bmp: Bitmap): Float {
    var sum = 0f
    var n = 0
    for (y in 0 until 8) for (x in 0 until 8) {
        val c = bmp.getPixel((bmp.width - 1) * x / 7, (bmp.height - 1) * y / 7)
        sum += (0.299f * (c shr 16 and 0xFF) + 0.587f * (c shr 8 and 0xFF) + 0.114f * (c and 0xFF)) / 255f
        n++
    }
    return sum / n
}
