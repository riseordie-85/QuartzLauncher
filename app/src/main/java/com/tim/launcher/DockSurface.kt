package com.tim.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tim.launcher.settings.DockEdge
import com.tim.launcher.settings.DockFill
import com.tim.launcher.settings.DockOutline
import com.tim.launcher.settings.DockPosition
import com.tim.launcher.settings.GradientDirection
import com.tim.launcher.settings.S
import com.tim.launcher.settings.SettingsStore
import com.tim.launcher.wallpaper.PatternStyle
import com.tim.launcher.wallpaper.WallRenderer
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Everything about how the dock looks (where it sits is separate). */
data class DockStyle(
    val background: Boolean = true,
    val outline: DockOutline = DockOutline.STRAIGHT,
    /** Corner roundness in dp; 0 is square. */
    val radius: Int = 28,
    val floating: Boolean = true,
    val fill: DockFill = DockFill.GLASS,
    val color2: Int = 0xFF2546A8.toInt(),
    val pattern: PatternStyle = PatternStyle.NONE,
    val patternStrength: Float = 0.3f,
    val patternSize: Float = 0.5f,
    val gradientDirection: GradientDirection = GradientDirection.HORIZONTAL,
    val edge: DockEdge = DockEdge.SOFT,
    /** Extra room across the dock (above and below a bottom dock's icons), in dp. */
    val padding: Int = 0,
    /** Extra room at the dock's two ends, in dp. */
    val paddingEnds: Int = 0,
    /** Tooth size (Zigzag, Ribbon) or how deep the curve or tilt goes (Arch, Bowl, Tilt), 0 to 1. */
    val depth: Float = 0.5f,
    /** A line around the dock (and the panels that match it). */
    val stroke: StrokeSpec = StrokeSpec(),
) {
    /** How wide each tooth is, for Zigzag and Ribbon (they're as deep as half that). */
    val tooth: Dp get() = (14f + 42f * depth.coerceIn(0f, 1f)).dp

    /** How far Arch, Bowl and Tilt reach in from the straight line. */
    val curve: Dp get() = (8f + 32f * depth.coerceIn(0f, 1f)).dp

    /**
     * How far a slanted or pointed dock's inner edge is cut back at each end, for a dock whose icons and padding
     * are [contentThick] deep.
     */
    fun slant(contentThick: Dp): Dp = when {
        !background -> 0.dp
        outline == DockOutline.SLANTED -> contentThick * 0.5f
        outline == DockOutline.POINTED || outline == DockOutline.RIBBON -> contentThick * 0.42f
        else -> 0.dp
    }

    /** Room at each end: the cut-back (so the end icons never hang over the slant) plus your end padding. */
    fun endRoom(contentThick: Dp): Dp = slant(contentThick) + paddingEnds.dp

    /** A wavy, toothed, curved or tilted inner edge needs room so it doesn't run through the icons. */
    val waveRoom: Dp get() = if (!background) 0.dp else when (outline) {
        DockOutline.WAVE -> 8.dp
        DockOutline.ZIGZAG, DockOutline.RIBBON -> tooth / 2
        DockOutline.ARCH, DockOutline.BOWL, DockOutline.TILT_LEFT, DockOutline.TILT_RIGHT -> curve
        else -> 0.dp
    }

    /** A floating ribbon has teeth on its outer edge too, so room there as well. */
    val outerRoom: Dp get() = if (background && floating && outline == DockOutline.RIBBON) tooth / 2 else 0.dp

    /** The outline to draw this dock with. */
    fun shape(edge: DockPosition, slant: Dp = 0.dp) =
        DockOutlineShape(outline, radius.toFloat(), edge, flush = !floating, slantDp = slant.value, toothDp = tooth.value, curveDp = curve.value)

    companion object {
        fun of(store: SettingsStore) = DockStyle(
            background = store[S.dockBackground],
            outline = store[S.dockOutline],
            radius = store[S.dockRadius],
            floating = store[S.dockFloating],
            fill = store[S.dockFill],
            color2 = store[S.dockColor2],
            pattern = store[S.dockPattern],
            patternStrength = store[S.dockPatternStrength],
            patternSize = store[S.dockPatternSize],
            gradientDirection = store[S.dockGradientDirection],
            edge = store[S.dockEdge],
            padding = store[S.dockPadding],
            paddingEnds = store[S.dockPaddingEnds],
            depth = store[S.dockDepth],
            stroke = StrokeSpec(store[S.dockStroke], store[S.dockStrokeColor], store[S.dockStrokeWidth]),
        )
    }
}

/**
 * The dock's outline. [edge] is the screen edge it sits on; when [flush] it touches that edge, so the corners there
 * stay square and only the ones facing into the screen are rounded.
 */
class DockOutlineShape(
    private val outline: DockOutline,
    private val radiusDp: Float,
    private val edge: DockPosition,
    private val flush: Boolean,
    /** How far a slanted or pointed dock is cut back at each end. */
    private val slantDp: Float = 0f,
    /** Tooth width for Zigzag and Ribbon. */
    private val toothDp: Float = 28f,
    /** Depth of Arch, Bowl and Tilt. */
    private val curveDp: Float = 20f,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val vertical = edge == DockPosition.LEFT || edge == DockPosition.RIGHT
        val length = if (vertical) size.height else size.width
        val thick = if (vertical) size.width else size.height
        val r = min(with(density) { radiusDp.dp.toPx() }, min(length, thick) / 2f)
        val outer = if (flush) 0f else r

        // Points are worked out with the dock lying along the bottom edge (u along it, v from the inner side at 0
        // to the screen edge at thick), then turned to the real edge.
        fun at(u: Float, v: Float): Offset = when (edge) {
            DockPosition.TOP -> Offset(u, thick - v)
            DockPosition.LEFT -> Offset(thick - v, u)
            DockPosition.RIGHT -> Offset(v, u)
            else -> Offset(u, v)
        }

        return when (outline) {
            DockOutline.STRAIGHT, DockOutline.FADE -> {
                val inner = CornerRadius(r)
                val out = CornerRadius(outer)
                // Which corners face the screen edge depends on where the dock is
                val rr = when (edge) {
                    DockPosition.TOP -> RoundRect(Rect(Offset.Zero, size), out, out, inner, inner)
                    DockPosition.LEFT -> RoundRect(Rect(Offset.Zero, size), out, inner, inner, out)
                    DockPosition.RIGHT -> RoundRect(Rect(Offset.Zero, size), inner, out, out, inner)
                    else -> RoundRect(Rect(Offset.Zero, size), inner, inner, out, out)
                }
                Outline.Rounded(rr)
            }
            DockOutline.SLANTED -> {
                val s = min(with(density) { slantDp.dp.toPx() }, length / 4f)
                Outline.Generic(
                    rounded(
                        listOf(at(s, 0f), at(length - s, 0f), at(length, thick), at(0f, thick)),
                        listOf(r, r, outer, outer),
                    ),
                )
            }
            DockOutline.POINTED -> {
                val p = min(with(density) { slantDp.dp.toPx() }, length / 4f)
                val h = r * 0.5f
                // Flush, the two corners on the screen edge stay square
                val (points, radii) = if (flush) {
                    listOf(at(p, 0f), at(length - p, 0f), at(length, thick / 2f), at(length, thick), at(0f, thick), at(0f, thick / 2f)) to
                        listOf(h, h, h, 0f, 0f, h)
                } else {
                    listOf(at(0f, thick / 2f), at(p, 0f), at(length - p, 0f), at(length, thick / 2f), at(length - p, thick), at(p, thick)) to
                        List(6) { h }
                }
                Outline.Generic(rounded(points, radii))
            }
            DockOutline.ZIGZAG -> {
                // Teeth along the inner edge, a whole number of them so both ends start on a point
                val tooth = with(density) { toothDp.dp.toPx() }
                val depth = min(tooth / 2f, thick * 0.45f)
                val corner = r * 0.6f
                val points = ArrayList<Offset>()
                val radii = ArrayList<Float>()
                points += at(0f, thick); radii += outer
                zigzag(0f, length, tooth, 0f, depth).forEachIndexed { i, (u, v) ->
                    points += at(u, v)
                    radii += if (i == 0 || u >= length) corner else 0f
                }
                points += at(length, thick); radii += outer
                Outline.Generic(rounded(points, radii))
            }
            DockOutline.RIBBON -> {
                // Pointed ends, teeth on the inner edge, and on the outer edge too unless it runs to the screen edge
                val p = min(with(density) { slantDp.dp.toPx() }, length / 4f)
                val tooth = with(density) { toothDp.dp.toPx() }
                val depth = min(tooth / 2f, thick * 0.3f)
                val h = r * 0.5f
                val points = ArrayList<Offset>()
                val radii = ArrayList<Float>()
                points += at(0f, thick / 2f); radii += h
                zigzag(p, length - p, tooth, 0f, depth).forEach { (u, v) -> points += at(u, v); radii += 0f }
                points += at(length, thick / 2f); radii += h
                if (flush) {
                    points += at(length, thick); radii += 0f
                    points += at(0f, thick); radii += 0f
                } else {
                    zigzag(p, length - p, tooth, thick, thick - depth).asReversed().forEach { (u, v) -> points += at(u, v); radii += 0f }
                }
                Outline.Generic(rounded(points, radii))
            }
            DockOutline.ARCH, DockOutline.BOWL, DockOutline.TILT_LEFT, DockOutline.TILT_RIGHT -> {
                // The inner edge curves up in the middle (arch), dips (bowl), or runs straight from one end to the other
                // (tilt); the screen-side edge stays straight
                val depth = min(with(density) { curveDp.dp.toPx() }, thick * 0.6f)
                fun v(t: Float): Float = when (outline) {
                    DockOutline.ARCH -> depth * (2f * t - 1f) * (2f * t - 1f)
                    DockOutline.BOWL -> depth * (1f - (2f * t - 1f) * (2f * t - 1f))
                    DockOutline.TILT_LEFT -> depth * t
                    else -> depth * (1f - t)
                }
                val steps = if (outline == DockOutline.TILT_LEFT || outline == DockOutline.TILT_RIGHT) 1 else 48
                val corner = r * 0.6f
                val points = ArrayList<Offset>()
                val radii = ArrayList<Float>()
                points += at(0f, thick); radii += outer
                for (k in 0..steps) {
                    val t = k.toFloat() / steps
                    points += at(length * t, v(t))
                    radii += if (k == 0 || k == steps) corner else 0f
                }
                points += at(length, thick); radii += outer
                Outline.Generic(rounded(points, radii))
            }
            DockOutline.WAVE -> {
                // The inner edge rises and falls a whole number of times, so both ends meet it at the same height
                val amp = min(thick * 0.1f, with(density) { 7.dp.toPx() })
                val waves = max(1, (length / (thick * 3f)).roundToInt())
                val steps = waves * 24
                val corner = r * 0.6f
                val points = ArrayList<Offset>()
                val radii = ArrayList<Float>()
                points += at(0f, thick); radii += outer
                for (k in 0..steps) {
                    val u = length * k / steps
                    // Skip the wave right next to the ends: a corner can only round as far as half its shortest side,
                    // and the wave's tiny steps would keep the inner corners almost square
                    if (k != 0 && k != steps && (u < corner * 2f || u > length - corner * 2f)) continue
                    points += at(u, amp - amp * sin(2f * PI.toFloat() * waves * k / steps))
                    radii += if (k == 0 || k == steps) corner else 0f
                }
                points += at(length, thick); radii += outer
                Outline.Generic(rounded(points, radii))
            }
        }
    }

    /**
     * Points of a zigzag from [u0] to [u1]: points at [tip] (where it starts and ends) and dips at [dip] between,
     * as many teeth as fit about [tooth] wide.
     */
    private fun zigzag(u0: Float, u1: Float, tooth: Float, tip: Float, dip: Float): List<Pair<Float, Float>> {
        val n = max(1, ((u1 - u0) / tooth).roundToInt())
        val step = (u1 - u0) / n
        val out = ArrayList<Pair<Float, Float>>(n * 2 + 1)
        for (k in 0 until n) {
            out += (u0 + k * step) to tip
            out += (u0 + (k + 0.5f) * step) to dip
        }
        out += u1 to tip
        return out
    }

    /** A polygon with each corner rounded by its own radius. */
    private fun rounded(points: List<Offset>, radii: List<Float>): Path {
        val path = Path()
        val n = points.size
        for (i in 0 until n) {
            val p0 = points[(i - 1 + n) % n]
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            val d0 = hypot(p0.x - p1.x, p0.y - p1.y)
            val d2 = hypot(p2.x - p1.x, p2.y - p1.y)
            val r = min(radii[i], min(d0, d2) / 2f)
            val a = if (d0 > 0f) p1 + (p0 - p1) * (r / d0) else p1
            val b = if (d2 > 0f) p1 + (p2 - p1) * (r / d2) else p1
            if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
            // quadraticBezierTo is in every Compose version (quadraticTo only in newer ones)
            if (r > 0f) path.quadraticBezierTo(p1.x, p1.y, b.x, b.y)
        }
        path.close()
        return path
    }

    override fun equals(other: Any?) = other is DockOutlineShape && other.outline == outline &&
        other.radiusDp == radiusDp && other.edge == edge && other.flush == flush && other.slantDp == slantDp &&
        other.toothDp == toothDp && other.curveDp == curveDp

    override fun hashCode() = (((((outline.hashCode() * 31 + radiusDp.hashCode()) * 31 + edge.hashCode()) * 31 + flush.hashCode()) * 31 +
        slantDp.hashCode()) * 31 + toothDp.hashCode()) * 31 + curveDp.hashCode()
}

/**
 * The dock's background: frosted glass (when there's a blurred wallpaper), the glass color or a gradient, an
 * optional crystal pattern that lines up with the same pattern on the wallpaper, then the edge: a hairline, or a
 * rim of light brightest at the top left, like light catching the edge of glass. [ink] is the text color on the
 * dock, which says whether the glass is light or dark.
 */
fun Modifier.dockSurface(style: DockStyle, tint: Color, edge: DockPosition, ink: Color, slant: Dp = 0.dp): Modifier = composed {
    val glass = LocalGlass.current
    val image = remember(glass) { glass?.bitmap?.asImageBitmap() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val view = LocalView.current
    val shape = remember(style.outline, style.radius, edge, style.floating, slant, style.depth) { style.shape(edge, slant) }
    val fade = style.outline == DockOutline.FADE
    if (!style.background) this else this
        .onGloballyPositioned { origin = it.positionInWindow() }
        // The stroke goes over the panel and its icons, outside the clip so none of it is cut off (not on a fade,
        // which has no edge to follow)
        .strokeOutline(shape, if (fade) StrokeSpec() else style.stroke)
        .graphicsLayer { compositingStrategy = if (fade) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
        .clip(shape)
        .drawBehind {
            if (glass != null && image != null) {
                val sx = image.width / glass.screenW.toFloat()
                val sy = image.height / glass.screenH.toFloat()
                withTransform({
                    translate(glass.left - origin.x, glass.top - origin.y)
                    scale(1f / sx, 1f / sy, pivot = Offset.Zero)
                }) { drawImage(image, filterQuality = FilterQuality.Low) }
            }
            val vertical = edge == DockPosition.LEFT || edge == DockPosition.RIGHT
            if (style.fill == DockFill.GRADIENT) {
                // The second color's own see-through, on top of the glass's
                val second = Color(style.color2).let { it.copy(alpha = it.alpha * tint.alpha) }
                val colors = listOf(tint, second)
                val brush = when (style.gradientDirection) {
                    GradientDirection.HORIZONTAL -> Brush.horizontalGradient(colors)
                    GradientDirection.VERTICAL -> Brush.verticalGradient(colors)
                    GradientDirection.DIAGONAL -> Brush.linearGradient(colors, start = Offset.Zero, end = Offset(size.width, size.height))
                    // From the middle out to the far ends
                    GradientDirection.RADIAL -> Brush.radialGradient(colors, center = center, radius = maxOf(size.width, size.height) / 2f)
                }
                drawRect(brush)
            } else {
                drawRect(tint)
            }
            if (style.pattern != PatternStyle.NONE) {
                // Drawn in screen coordinates, so a dock pattern lines up with the same pattern on the wallpaper
                val screenW = view.rootView.width.toFloat().coerceAtLeast(1f)
                val screenH = view.rootView.height.toFloat().coerceAtLeast(1f)
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.translate(-origin.x, -origin.y)
                    val clip = android.graphics.Rect(
                        origin.x.toInt(), origin.y.toInt(),
                        (origin.x + size.width).toInt() + 1, (origin.y + size.height).toInt() + 1,
                    )
                    native.clipRect(clip)
                    WallRenderer.drawPatternOnly(native, screenW, screenH, clip, style.pattern, style.patternStrength, style.patternSize)
                    native.restore()
                }
            }
            if (fade) {
                // Solid at the screen edge, clear toward the middle of the screen
                val clear = Color.Transparent
                val solid = Color.Black
                val mask = when (edge) {
                    DockPosition.TOP -> Brush.verticalGradient(listOf(solid, clear))
                    DockPosition.LEFT -> Brush.horizontalGradient(listOf(solid, clear))
                    DockPosition.RIGHT -> Brush.horizontalGradient(listOf(clear, solid))
                    else -> Brush.verticalGradient(listOf(clear, solid))
                }
                drawRect(mask, blendMode = BlendMode.DstIn)
                return@drawBehind
            }
            drawEdgeLight(shape.createOutline(size, layoutDirection, this), style.edge, ink)
        }
}


/**
 * The edge of a glass panel: a hairline, or a rim of light brightest at the top left with a glint at the bottom
 * right, like light catching the edge of glass. Shared by the dock and the app drawer, so they always match.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEdgeLight(outline: Outline, edge: DockEdge, ink: Color) {
    val px = 1.dp.toPx()
    val lightGlass = ink.luminance() < 0.5f
    when (edge) {
        DockEdge.NONE -> Unit
        DockEdge.HAIRLINE -> drawOutline(outline, ink.copy(alpha = 0.22f), style = Stroke(px * 2f))
        DockEdge.SOFT, DockEdge.BRIGHT -> {
            val bright = edge == DockEdge.BRIGHT
            val k = if (bright) 1f else 0.55f
            // On light glass a faint dark line keeps the edge visible under the white rim
            if (lightGlass) drawOutline(outline, Color.Black.copy(alpha = 0.10f), style = Stroke(px * 2f))
            fun w(a: Float) = Color.White.copy(alpha = (a * k).coerceIn(0f, 1f))
            // Brightest around the top-left corner, a second glint at the bottom right, dimmer between. Runs
            // corner to corner, so a long dock gets its light on the curved ends rather than mid-edge.
            val rim = Brush.linearGradient(
                0f to w(0.95f),
                0.3f to w(0.32f),
                0.5f to w(0.15f),
                0.75f to w(0.30f),
                1f to w(0.75f),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            drawOutline(outline, rim, style = Stroke(px * if (bright) 3.2f else 2.4f))
            if (bright) {
                // A soft sheen along the top, as if the glass were curved
                drawRect(
                    Brush.verticalGradient(0f to Color.White.copy(alpha = 0.14f), 0.45f to Color.Transparent),
                )
            }
        }
    }
}

/** The dock's edge light around any panel (the app drawer uses it to match the dock). */
fun Modifier.edgeLight(shape: Shape, edge: DockEdge, ink: Color): Modifier =
    if (edge == DockEdge.NONE) this else drawBehind { drawEdgeLight(shape.createOutline(size, layoutDirection, this), edge, ink) }
