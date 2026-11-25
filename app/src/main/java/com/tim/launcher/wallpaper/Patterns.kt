package com.tim.launcher.wallpaper

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The drawn (not crystal) patterns: dots, lines, grids, shapes and curves. Each is drawn in screen coordinates, so
 * the same pattern on the wallpaper, the dock and widgets lines up; only the part inside the clip is drawn.
 *
 * [cell] is the pattern's size (how far it repeats), [weight] 0–1 how heavy its lines and dots are (0.5 usual), and
 * [color] includes how strong the pattern is (its alpha). Shapes in several shades use fractions of that alpha.
 */
object Patterns {

    fun draw(canvas: Canvas, w: Float, h: Float, clip: Rect, style: PatternStyle, color: Int, cell: Float, weight: Float) {
        if (cell < 2f || style == PatternStyle.NONE || style.faceted) return
        val wt = weight.coerceIn(0f, 1f)
        val line = max(1f, cell * (0.015f + 0.08f * wt))
        // A paint of its own each time: patterns are drawn from more than one thread (icons, dock, wallpaper)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val a = (color ushr 24) and 0xFF
        fun shade(f: Float) = (color and 0x00FFFFFF) or ((a * f).toInt().coerceIn(0, 255) shl 24)
        fun fill(f: Float = 1f) {
            p.style = Paint.Style.FILL
            p.pathEffect = null
            p.color = shade(f)
        }
        fun stroke(width: Float = line, f: Float = 1f, cap: Paint.Cap = Paint.Cap.BUTT) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = width
            p.strokeCap = cap
            p.strokeJoin = Paint.Join.MITER
            p.pathEffect = null
            p.color = shade(f)
        }
        val box = RectF(clip)
        // One path, reused for every shape (thousands of them on a wallpaper)
        val tmp = Path()

        when (style) {
            // ---- Dots ----
            PatternStyle.DOTS -> dots(canvas, box, cell, cell * (0.06f + 0.16f * wt), offset = true, p.also { fill() })
            PatternStyle.DOT_GRID -> dots(canvas, box, cell, cell * (0.05f + 0.14f * wt), offset = false, p.also { fill() })
            PatternStyle.POLKA -> dots(canvas, box, cell, cell * (0.16f + 0.16f * wt), offset = true, p.also { fill() })
            PatternStyle.HALFTONE -> {
                // Dots that grow from the top left of the screen to the bottom right
                fill()
                eachCell(box, cell, cell) { x, y ->
                    val cx = x + cell / 2f
                    val cy = y + cell / 2f
                    val f = ((cx + cy) / (w + h)).coerceIn(0f, 1f)
                    val r = min(cell * 0.5f, cell * (0.3f + 0.4f * wt) * (0.12f + 0.88f * f))
                    canvas.drawCircle(cx, cy, r, p)
                }
            }
            PatternStyle.CROSSES -> {
                stroke(line * 1.3f, cap = Paint.Cap.ROUND)
                val arm = cell * 0.16f
                eachCell(box, cell, cell) { x, y ->
                    val cx = x + cell / 2f
                    val cy = y + cell / 2f
                    canvas.drawLine(cx - arm, cy, cx + arm, cy, p)
                    canvas.drawLine(cx, cy - arm, cx, cy + arm, p)
                }
            }

            // ---- Lines ----
            // (Diagonal stripes run top-left to bottom-right, as they always have)
            PatternStyle.STRIPES -> rotated(canvas, clip, 45f) { r -> fill(); bands(canvas, r, cell * 0.71f, cell * (0.09f + 0.3f * wt), p) }
            PatternStyle.STRIPES_BACK -> rotated(canvas, clip, -45f) { r -> fill(); bands(canvas, r, cell * 0.71f, cell * (0.09f + 0.3f * wt), p) }
            PatternStyle.PINSTRIPES -> rotated(canvas, clip, -45f) { r -> fill(); bands(canvas, r, cell * 0.45f, max(1f, line * 0.7f), p) }
            PatternStyle.CANDY -> rotated(canvas, clip, -45f) { r ->
                fill()
                bands(canvas, r, cell, cell * (0.28f + 0.2f * wt), p)
                bands(canvas, r, cell, max(1f, line * 0.8f), p, offset = cell * 0.68f)
            }
            PatternStyle.LINES_ACROSS -> { fill(); bands(canvas, box, cell * 0.5f, line, p) }
            PatternStyle.LINES_DOWN -> rotated(canvas, clip, 90f) { r -> fill(); bands(canvas, r, cell * 0.5f, line, p) }
            PatternStyle.DASHED_LINES -> {
                stroke()
                val period = cell * 0.7f
                p.pathEffect = DashPathEffect(floatArrayOf(cell * 0.45f, cell * 0.25f), 0f)
                // Each line starts on a whole number of dashes, so they line up wherever they're drawn
                val x0 = floor(box.left / period) * period - period
                rows(box, cell * 0.5f) { y -> canvas.drawPath(segment(tmp, x0, y, box.right + period, y), p) }
            }
            PatternStyle.DOTTED_LINES -> {
                stroke(line * 1.6f, cap = Paint.Cap.ROUND)
                val period = cell * 0.25f
                p.pathEffect = DashPathEffect(floatArrayOf(0.01f, period - 0.01f), 0f)
                val x0 = floor(box.left / period) * period - period
                rows(box, cell * 0.5f) { y -> canvas.drawPath(segment(tmp, x0, y, box.right + period, y), p) }
            }
            PatternStyle.SCANLINES -> { fill(); bands(canvas, box, max(2f, cell * 0.2f), max(1f, cell * 0.05f * (0.5f + wt)), p) }

            // ---- Grids ----
            PatternStyle.GRID -> { stroke(); grid(canvas, box, cell, p) }
            PatternStyle.GRAPH -> {
                // Fine lines with a heavier one every fourth
                stroke(max(1f, line * 0.5f), f = 0.55f)
                grid(canvas, box, cell / 4f, p)
                stroke()
                grid(canvas, box, cell, p)
            }
            PatternStyle.GRID_DASHED -> {
                stroke()
                // Four dashes to a square, centred so they cross at the corners
                p.pathEffect = DashPathEffect(floatArrayOf(cell * 0.15f, cell * 0.1f), cell * 0.075f)
                gridPaths(canvas, box, cell, p, tmp)
            }
            PatternStyle.GRID_DOTTED -> {
                stroke(line * 1.5f, cap = Paint.Cap.ROUND)
                // Eight dots to a side, one on every corner
                p.pathEffect = DashPathEffect(floatArrayOf(0.01f, cell * 0.125f - 0.01f), 0f)
                gridPaths(canvas, box, cell, p, tmp)
            }
            PatternStyle.GRID_DIAGONAL -> rotated(canvas, clip, 45f) { r -> stroke(); grid(canvas, r, cell * 0.75f, p) }
            PatternStyle.TARTAN -> {
                // Wide soft bands and a fine line, across and down: where they cross, they deepen
                val wide = cell * (0.22f + 0.2f * wt)
                fill(0.45f)
                bands(canvas, box, cell, wide, p)
                rotated(canvas, clip, 90f) { r -> bands(canvas, r, cell, wide, p) }
                fill(0.9f)
                bands(canvas, box, cell, max(1f, line * 0.7f), p, offset = cell * 0.62f)
                rotated(canvas, clip, 90f) { r -> bands(canvas, r, cell, max(1f, line * 0.7f), p, offset = cell * 0.62f) }
            }

            // ---- Shapes ----
            PatternStyle.CHECKS -> {
                fill()
                eachCellAt(box, cell, cell) { x, y, col, row -> if ((row + col) % 2 == 0) canvas.drawRect(x, y, x + cell, y + cell, p) }
            }
            PatternStyle.HARLEQUIN -> rotated(canvas, clip, 45f) { r ->
                fill()
                val c = cell * 0.7f
                eachCellAt(r, c, c) { x, y, col, row -> if ((row + col) % 2 == 0) canvas.drawRect(x, y, x + c, y + c, p) }
            }
            PatternStyle.HONEYCOMB, PatternStyle.HONEYCOMB_FILLED -> {
                val filled = style == PatternStyle.HONEYCOMB_FILLED
                if (filled) fill() else stroke()
                val rad = cell * 0.58f
                val shrink = if (filled) 0.9f - 0.25f * wt else 1f
                hexes(box, rad) { cx, cy -> canvas.drawPath(hexagon(tmp, cx, cy, rad * shrink), p) }
            }
            PatternStyle.TRIANGLES -> {
                fill()
                val th = cell * 0.866f
                rowsAt(box, th, pad = 1) { y, row ->
                    val shift = if (row % 2 == 0) 0f else cell / 2f
                    var x = floor((box.left - shift) / cell) * cell + shift - cell
                    while (x < box.right + cell) {
                        tmp.reset()
                        tmp.moveTo(x, y + th); tmp.lineTo(x + cell / 2f, y); tmp.lineTo(x + cell, y + th); tmp.close()
                        canvas.drawPath(tmp, p)
                        x += cell
                    }
                }
            }
            PatternStyle.CUBES -> {
                // Each hexagon split into three faces, lit from above
                val rad = cell * 0.6f
                val s3 = sqrt(3f) / 2f
                hexes(box, rad) { cx, cy ->
                    fill(1f)
                    tmp.reset()
                    tmp.moveTo(cx, cy); tmp.lineTo(cx - rad * s3, cy - rad / 2f); tmp.lineTo(cx, cy - rad); tmp.lineTo(cx + rad * s3, cy - rad / 2f); tmp.close()
                    canvas.drawPath(tmp, p)
                    fill(0.5f)
                    tmp.reset()
                    tmp.moveTo(cx, cy); tmp.lineTo(cx - rad * s3, cy - rad / 2f); tmp.lineTo(cx - rad * s3, cy + rad / 2f); tmp.lineTo(cx, cy + rad); tmp.close()
                    canvas.drawPath(tmp, p)
                    fill(0.2f)
                    tmp.reset()
                    tmp.moveTo(cx, cy); tmp.lineTo(cx, cy + rad); tmp.lineTo(cx + rad * s3, cy + rad / 2f); tmp.lineTo(cx + rad * s3, cy - rad / 2f); tmp.close()
                    canvas.drawPath(tmp, p)
                }
            }
            PatternStyle.ZIGZAG -> {
                stroke()
                val amp = cell * 0.18f
                rows(box, cell * 0.6f) { y ->
                    tmp.reset()
                    zigzagCorners(box, cell, y, amp).forEachIndexed { i, (x, yy) -> if (i == 0) tmp.moveTo(x, yy) else tmp.lineTo(x, yy) }
                    canvas.drawPath(tmp, p)
                }
            }
            PatternStyle.CHEVRON -> {
                fill()
                val amp = cell * 0.25f
                val thick = cell * (0.2f + 0.25f * wt)
                rows(box, cell) { y ->
                    // The zigzag's corners along the top, then the same corners lower down, back the other way
                    val corners = zigzagCorners(box, cell, y, amp)
                    tmp.reset()
                    corners.forEachIndexed { i, (x, yy) -> if (i == 0) tmp.moveTo(x, yy) else tmp.lineTo(x, yy) }
                    corners.asReversed().forEach { (x, yy) -> tmp.lineTo(x, yy + thick) }
                    tmp.close()
                    canvas.drawPath(tmp, p)
                }
            }

            // ---- Curves ----
            PatternStyle.WAVES -> {
                stroke()
                val amp = cell * 0.22f
                rows(box, cell) { y ->
                    val base = y + cell / 2f
                    val path = Path()
                    var x = box.left - cell
                    path.moveTo(x, base + amp * sin(x / cell * PI).toFloat())
                    while (x < box.right + cell) {
                        x += cell / 8f
                        path.lineTo(x, base + amp * sin(x / cell * PI).toFloat())
                    }
                    canvas.drawPath(path, p)
                }
            }
            PatternStyle.SCALES -> {
                // Rows of overlapping half-circles, like fish scales
                stroke()
                val r = cell / 2f
                rowsAt(box, r, pad = 2) { y, row ->
                    val shift = if (row % 2 == 0) 0f else r
                    var x = floor((box.left - shift) / cell) * cell + shift - cell
                    while (x < box.right + cell) {
                        canvas.drawArc(x - r, y - r, x + r, y + r, 0f, 180f, false, p)
                        x += cell
                    }
                }
            }
            PatternStyle.ARCS -> {
                // Arcs up and down in turn, linked into a chain
                stroke()
                val r = cell / 4f
                rows(box, cell * 0.5f, pad = 1) { y ->
                    var x = floor(box.left / cell) * cell - cell
                    while (x < box.right + cell) {
                        canvas.drawArc(x - r, y - r, x + r, y + r, 180f, 180f, false, p)
                        canvas.drawArc(x + cell / 2f - r, y - r, x + cell / 2f + r, y + r, 0f, 180f, false, p)
                        x += cell
                    }
                }
            }
            PatternStyle.CIRCLES -> {
                // Circles as wide as the spacing between them, so each overlaps its neighbours
                stroke()
                val s = cell * 0.5f
                eachCell(RectF(box.left - s, box.top - s, box.right + s, box.bottom + s), s, s) { x, y -> canvas.drawCircle(x, y, s, p) }
            }
            PatternStyle.RINGS -> {
                // Circles out from the middle of the screen
                stroke()
                val cx = w / 2f
                val cy = h / 2f
                val near = hypot(max(0f, max(box.left - cx, cx - box.right)), max(0f, max(box.top - cy, cy - box.bottom)))
                val far = maxOf(hypot(box.left - cx, box.top - cy), hypot(box.right - cx, box.top - cy), hypot(box.left - cx, box.bottom - cy), hypot(box.right - cx, box.bottom - cy))
                val step = cell * 0.5f
                var k = max(1, floor(near / step).toInt())
                while (k * step <= far + step) {
                    canvas.drawCircle(cx, cy, k * step, p)
                    k++
                }
            }

            else -> Unit
        }
    }

    // ---- Helpers ----

    /** Calls [block] with the top-left of each [cw]×[ch] cell covering [box] (plus one around it). */
    private inline fun eachCell(box: RectF, cw: Float, ch: Float, block: (x: Float, y: Float) -> Unit) =
        eachCellAt(box, cw, ch) { x, y, _, _ -> block(x, y) }

    /** [eachCell], with each cell's column and row too. */
    private inline fun eachCellAt(box: RectF, cw: Float, ch: Float, block: (x: Float, y: Float, col: Int, row: Int) -> Unit) {
        val c0 = floor(box.left / cw).toInt() - 1
        val c1 = ceil(box.right / cw).toInt() + 1
        val r0 = floor(box.top / ch).toInt() - 1
        val r1 = ceil(box.bottom / ch).toInt() + 1
        for (row in r0..r1) for (col in c0..c1) block(col * cw, row * ch, col, row)
    }

    /** Calls [block] with the y of each row [step] apart across [box]. */
    private inline fun rows(box: RectF, step: Float, pad: Int = 1, block: (y: Float) -> Unit) =
        rowsAt(box, step, pad) { y, _ -> block(y) }

    /** [rows], with each row's number too (worked out exactly, not back from its y). */
    private inline fun rowsAt(box: RectF, step: Float, pad: Int = 1, block: (y: Float, row: Int) -> Unit) {
        val r0 = floor(box.top / step).toInt() - pad
        val r1 = ceil(box.bottom / step).toInt() + pad
        for (r in r0..r1) block(r * step, r)
    }

    private fun dots(canvas: Canvas, box: RectF, cell: Float, radius: Float, offset: Boolean, p: Paint) {
        eachCellAt(box, cell, cell) { x, y, _, row ->
            val shift = if (offset && row % 2 != 0) cell / 2f else 0f
            canvas.drawCircle(x + shift + cell / 2f, y + cell / 2f, radius, p)
        }
    }

    /** Bands [thick] deep, one every [step], across [box] ([offset] down from each step). */
    private fun bands(canvas: Canvas, box: RectF, step: Float, thick: Float, p: Paint, offset: Float = 0f) {
        rows(box, step) { y -> canvas.drawRect(box.left - step, y + offset, box.right + step, y + offset + thick, p) }
    }

    private fun grid(canvas: Canvas, box: RectF, step: Float, p: Paint) {
        val c0 = floor(box.left / step).toInt() - 1
        val c1 = ceil(box.right / step).toInt() + 1
        for (c in c0..c1) canvas.drawLine(c * step, box.top - step, c * step, box.bottom + step, p)
        rows(box, step) { y -> canvas.drawLine(box.left - step, y, box.right + step, y, p) }
    }

    /**
     * A grid drawn as paths, so dashes and dots apply (lines don't take path effects everywhere). Each line starts
     * on a grid line (a whole number of dash patterns), so dashes line up wherever the grid is drawn.
     */
    private fun gridPaths(canvas: Canvas, box: RectF, step: Float, p: Paint, tmp: Path) {
        val c0 = floor(box.left / step).toInt() - 1
        val c1 = ceil(box.right / step).toInt() + 1
        val y0 = floor(box.top / step) * step - step
        val x0 = floor(box.left / step) * step - step
        for (c in c0..c1) canvas.drawPath(segment(tmp, c * step, y0, c * step, box.bottom + step), p)
        rows(box, step) { y -> canvas.drawPath(segment(tmp, x0, y, box.right + step, y), p) }
    }

    private fun segment(path: Path, x0: Float, y0: Float, x1: Float, y1: Float): Path {
        path.reset()
        path.moveTo(x0, y0)
        path.lineTo(x1, y1)
        return path
    }

    /** The corners of a zigzag along y, rising and falling [amp], one point every half [cell], starting on a cell. */
    private fun zigzagCorners(box: RectF, cell: Float, y: Float, amp: Float): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        var x = floor(box.left / cell) * cell - cell
        var up = false
        out += x to y + amp
        while (x < box.right + cell) {
            x += cell / 2f
            up = !up
            out += x to (if (up) y - amp else y + amp)
        }
        return out
    }

    /** Centres of pointy-topped hexagons of [rad] tiling [box]. */
    private inline fun hexes(box: RectF, rad: Float, block: (cx: Float, cy: Float) -> Unit) {
        val hw = sqrt(3f) * rad
        val step = rad * 1.5f
        val r0 = floor(box.top / step).toInt() - 1
        val r1 = ceil(box.bottom / step).toInt() + 1
        for (row in r0..r1) {
            val shift = if (row % 2 == 0) 0f else hw / 2f
            val c0 = floor((box.left - shift) / hw).toInt() - 1
            val c1 = ceil((box.right - shift) / hw).toInt() + 1
            for (col in c0..c1) block(col * hw + shift, row * step)
        }
    }

    private fun hexagon(path: Path, cx: Float, cy: Float, rad: Float): Path = path.apply {
        reset()
        for (i in 0 until 6) {
            val ang = (PI / 3.0 * i - PI / 2.0)
            val x = cx + rad * kotlin.math.cos(ang).toFloat()
            val y = cy + rad * kotlin.math.sin(ang).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /**
     * Draws in a turned frame (turned about the screen's corner, so it lines up across the wallpaper and panels):
     * [block] gets the clip as seen in that frame.
     */
    private inline fun rotated(canvas: Canvas, clip: Rect, degrees: Float, block: (RectF) -> Unit) {
        canvas.save()
        canvas.rotate(degrees)
        val r = RectF(clip)
        Matrix().apply { setRotate(-degrees) }.mapRect(r)
        block(r)
        canvas.restore()
    }
}
