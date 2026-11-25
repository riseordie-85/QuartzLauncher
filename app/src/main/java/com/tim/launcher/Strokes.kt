package com.tim.launcher

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.tim.launcher.settings.StrokeStyle
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sin

/** A line around something: its style, color (with its own see-through) and width in dp. */
data class StrokeSpec(
    val style: StrokeStyle = StrokeStyle.NONE,
    val color: Int = 0xE6FFFFFF.toInt(),
    val width: Float = 2f,
) {
    val none get() = style == StrokeStyle.NONE || width <= 0f
}

/**
 * Draws strokes along any outline: solid, dashed, dotted (round dots), or pen, which swells and thins with the
 * line's direction like a fountain pen nib held at an angle: thick on strokes going one way, hairline the other.
 */
object Strokes {

    /** The angle the pen's nib is held at, in radians (about 30°, a calligrapher's usual angle). */
    private const val NIB = 0.52f

    /** Draws [spec] along [path], [widthPx] wide, centered on the line. */
    fun draw(canvas: Canvas, path: Path, spec: StrokeSpec, widthPx: Float) = prepare(path, spec, widthPx)?.let { (p, paint) -> canvas.drawPath(p, paint) }

    /**
     * What to draw for [spec] along [path]: the path and paint, worked out once so a panel that redraws (the dock)
     * doesn't redo it every frame. The pen's line is a filled shape built ahead.
     */
    fun prepare(path: Path, spec: StrokeSpec, widthPx: Float): Pair<Path, Paint>? {
        if (spec.none || widthPx <= 0f) return null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.color }
        return when (spec.style) {
            StrokeStyle.NONE -> null
            StrokeStyle.SOLID -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = widthPx
                paint.strokeJoin = Paint.Join.ROUND
                path to paint
            }
            StrokeStyle.DASHED -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = widthPx
                paint.strokeCap = Paint.Cap.ROUND
                val dash = max(widthPx * 3f, 4f)
                paint.pathEffect = DashPathEffect(floatArrayOf(dash, dash * 0.8f + widthPx), 0f)
                path to paint
            }
            StrokeStyle.DOTTED -> {
                // A dash of almost nothing with round ends is a round dot
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = widthPx
                paint.strokeCap = Paint.Cap.ROUND
                paint.pathEffect = DashPathEffect(floatArrayOf(0.01f, widthPx * 2.2f), 0f)
                path to paint
            }
            StrokeStyle.PEN -> penRibbon(path, widthPx) to paint.apply { style = Paint.Style.FILL }
        }
    }

    /**
     * The pen line as a filled shape: each side of the line is pushed out by half the width the nib gives at that
     * point's direction. A closed outline becomes a ring (its two sides, filled even-odd).
     */
    private fun penRibbon(path: Path, widthPx: Float): Path {
        // Winding fill, with the two sides of a closed line running opposite ways: the ring is filled, the middle
        // isn't, and where a side loops back on itself at a sharp corner it stays filled (even-odd punched holes there)
        val out = Path().apply { fillType = Path.FillType.WINDING }
        val measure = PathMeasure(path, false)
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        do {
            val length = measure.length
            if (length <= 0f) continue
            val closed = measure.isClosed
            val steps = max(12, (length / 1.5f).toInt())
            val left = Path()
            val right = ArrayList<Pair<Float, Float>>(steps + 1)
            for (i in 0..steps) {
                measure.getPosTan(length * i / steps, pos, tan)
                val angle = atan2(tan[1], tan[0])
                // Full width across the nib, a hairline along it
                val half = widthPx / 2f * (0.18f + 0.82f * abs(sin(angle - NIB)))
                val nx = -tan[1] * half
                val ny = tan[0] * half
                if (i == 0) left.moveTo(pos[0] + nx, pos[1] + ny) else left.lineTo(pos[0] + nx, pos[1] + ny)
                right += (pos[0] - nx) to (pos[1] - ny)
            }
            if (closed) {
                left.close()
                out.addPath(left)
                val inner = Path()
                right.asReversed().forEachIndexed { i, (x, y) -> if (i == 0) inner.moveTo(x, y) else inner.lineTo(x, y) }
                inner.close()
                out.addPath(inner)
            } else {
                right.asReversed().forEach { (x, y) -> left.lineTo(x, y) }
                left.close()
                out.addPath(left)
            }
        } while (measure.nextContour())
        return out
    }

    /** A Compose outline as a plain path. */
    fun pathOf(outline: Outline): Path = when (outline) {
        is Outline.Generic -> Path(outline.path.asAndroidPath())
        is Outline.Rounded -> {
            val rr = outline.roundRect
            Path().apply {
                addRoundRect(
                    android.graphics.RectF(rr.left, rr.top, rr.right, rr.bottom),
                    floatArrayOf(
                        rr.topLeftCornerRadius.x, rr.topLeftCornerRadius.y, rr.topRightCornerRadius.x, rr.topRightCornerRadius.y,
                        rr.bottomRightCornerRadius.x, rr.bottomRightCornerRadius.y, rr.bottomLeftCornerRadius.x, rr.bottomLeftCornerRadius.y,
                    ),
                    Path.Direction.CW,
                )
            }
        }
        is Outline.Rectangle -> Path().apply {
            addRect(outline.rect.left, outline.rect.top, outline.rect.right, outline.rect.bottom, Path.Direction.CW)
        }
    }
}

/**
 * A stroke along [shape]'s outline, drawn over everything inside, centered on the edge. Goes before any clip in
 * the modifier chain, so the outer half isn't cut off. Worked out once per size. [keep], if given, is the part of
 * the panel the stroke may show in (a dock running to the screen edge leaves out the stretch along that edge).
 */
fun Modifier.strokeOutline(shape: Shape, spec: StrokeSpec, keep: (androidx.compose.ui.geometry.Size) -> androidx.compose.ui.geometry.Rect? = { null }): Modifier =
    if (spec.none) this else drawWithCache {
        val prepared = Strokes.prepare(Strokes.pathOf(shape.createOutline(size, layoutDirection, this)), spec, spec.width.dp.toPx())
        onDrawWithContent {
            drawContent()
            val (path, paint) = prepared ?: return@onDrawWithContent
            val area = keep(size)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                if (area != null) {
                    native.save()
                    native.clipRect(area.left, area.top, area.right, area.bottom)
                }
                native.drawPath(path, paint)
                if (area != null) native.restore()
            }
        }
    }
