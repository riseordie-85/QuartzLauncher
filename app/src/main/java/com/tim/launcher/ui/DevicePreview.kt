package com.tim.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.settings.ButtonEnd
import com.tim.launcher.settings.CoverSide
import com.tim.launcher.settings.DeviceType
import com.tim.launcher.settings.DockAlign
import com.tim.launcher.settings.DockLength
import com.tim.launcher.settings.DockPosition
import com.tim.launcher.settings.DockShape
import com.tim.launcher.settings.DrawerOpen
import com.tim.launcher.settings.IconShape
import com.tim.launcher.settings.S
import com.tim.launcher.settings.Scope
import com.tim.launcher.settings.SettingsStore
import com.tim.launcher.settings.dockCounts
import kotlin.math.min

/** What one screen looks like with the current settings, for the live preview. */
data class ScreenLook(
    val scope: Scope,
    val label: String,
    val aspect: Float,
    val cols: Int,
    val rows: Int,
    val dock: DockPosition,
    val dockLength: DockLength,
    val dockAlign: DockAlign,
    /** 0 = unlimited */
    val dockIcons: Int,
    val drawerButton: ButtonEnd?,
    val coverHalf: CoverSide?,
)

data class PreviewLook(
    val screens: List<ScreenLook>,
    val panelColor: Int,
    val opacity: Float,
    val shape: DockShape,
    val radius: Int,
    val iconShape: IconShape,
)

private fun isHorizontal(p: DockPosition) = p == DockPosition.BOTTOM || p == DockPosition.TOP

/** Reads everything the preview needs. Unfolded grids and dock counts follow the "doubled" rules. */
fun previewLook(store: SettingsStore, type: DeviceType): PreviewLook {
    val button = when (store[S.drawerOpen]) {
        DrawerOpen.SWIPE -> null
        else -> store[S.drawerButtonEnd]
    }
    fun look(scope: Scope, label: String, aspect: Float): ScreenLook {
        val dock = store[S.dockPosition, scope]
        var cols = store[S.gridCols, scope]
        var rows = store[S.gridRows, scope]
        var dockIcons = store[S.dockIcons, scope]
        var half: CoverSide? = null
        if (scope == Scope.INNER) {
            if (store[S.carryOver]) {
                cols = store[S.gridCols, Scope.COVER] * 2
                rows = store[S.gridRows, Scope.COVER]
                half = store[S.coverSide]
            }
            dockIcons = store.dockCounts(type).second
        }
        return ScreenLook(
            scope = scope,
            label = label,
            aspect = aspect,
            cols = cols,
            rows = rows,
            dock = dock,
            dockLength = store[S.dockLength, scope],
            dockAlign = store[S.dockAlign, scope],
            dockIcons = dockIcons,
            drawerButton = button,
            coverHalf = half,
        )
    }
    val screens = when (type) {
        DeviceType.PHONE -> listOf(look(Scope.PHONE, "Phone", 0.46f))
        DeviceType.FOLDABLE -> listOf(look(Scope.COVER, "Cover", 0.63f), look(Scope.INNER, "Unfolded", 1.33f))
        DeviceType.TABLET -> listOf(look(Scope.TABLET, "Tablet", 1.52f))
    }
    return PreviewLook(
        screens = screens,
        panelColor = store[S.panelColor],
        opacity = store[S.panelOpacity],
        shape = store[S.dockShape],
        radius = store[S.dockRadius],
        iconShape = store[S.iconShape],
    )
}

/**
 * Miniature screens drawn from the current settings. Used by the setup wizard (live preview) and as the screens
 * diagram at the top of Settings, where tapping a screen picks which one you're editing.
 */
@Composable
fun DevicePreview(
    look: PreviewLook,
    height: Dp,
    modifier: Modifier = Modifier,
    selected: Scope? = null,
    onSelect: ((Scope) -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        look.screens.forEach { screen ->
            val isSelected = selected == screen.scope
            val edge by animateFloatAsState(if (isSelected) 1f else 0f, label = "edge")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val shape = RoundedCornerShape(14.dp)
                Canvas(
                    Modifier
                        .height(height)
                        .width(height * screen.aspect)
                        .clip(shape)
                        .then(if (onSelect != null) Modifier.clickable { onSelect(screen.scope) } else Modifier)
                        .border(
                            width = (1 + 2 * edge).dp,
                            color = if (isSelected) Q.Accent else Q.Edge,
                            shape = shape,
                        ),
                ) { drawScreen(screen, look) }
                Text(
                    screen.label,
                    color = if (isSelected) Q.Text else Q.TextDim,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private val Wallpaper = listOf(Color(0xFF3B2A7A), Color(0xFF244F9E), Color(0xFF14808A), Color(0xFF4E9F3D))

private fun DrawScope.drawScreen(screen: ScreenLook, look: PreviewLook) {
    val w = size.width
    val h = size.height
    drawRect(Brush.linearGradient(Wallpaper, start = Offset.Zero, end = Offset(w, h)))

    val m = min(w, h) * 0.05f
    val t = min(w, h) * if (screen.scope == Scope.INNER || screen.scope == Scope.TABLET) 0.12f else 0.17f
    val dock = screen.dock
    var grid = Rect(m, m, w - m, h - m)
    grid = when (dock) {
        DockPosition.BOTTOM -> grid.copy(bottom = grid.bottom - t - m)
        DockPosition.TOP -> grid.copy(top = grid.top + t + m)
        DockPosition.LEFT -> grid.copy(left = grid.left + t + m)
        DockPosition.RIGHT -> grid.copy(right = grid.right - t - m)
        DockPosition.NONE -> grid
    }

    // Which half the cover layout lands on when unfolded: the other half dims, the cover half gets an outline
    if (screen.coverHalf != null) {
        val mid = w / 2f
        val coverLeft = screen.coverHalf == CoverSide.LEFT
        val half = if (coverLeft) Rect(m * 0.6f, m * 0.6f, mid - m * 0.6f, h - m * 0.6f)
        else Rect(mid + m * 0.6f, m * 0.6f, w - m * 0.6f, h - m * 0.6f)
        drawRoundRect(Q.Accent.copy(alpha = 0.22f), half.topLeft, half.size, CornerRadius(m, m))
        drawRoundRect(Q.Accent, half.topLeft, half.size, CornerRadius(m, m), style = Stroke(width = 3f))
        drawLine(
            Color.White.copy(alpha = 0.55f),
            Offset(mid, 0f),
            Offset(mid, h),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        )
    }

    // Home grid
    val cellW = grid.width / screen.cols
    val cellH = grid.height / screen.rows
    val icon = min(cellW, cellH) * 0.58f
    for (r in 0 until screen.rows) {
        for (c in 0 until screen.cols) {
            val center = Offset(grid.left + cellW * (c + 0.5f), grid.top + cellH * (r + 0.5f))
            drawIcon(center, icon, look.iconShape, Color.White.copy(alpha = 0.85f))
        }
    }
    if (screen.coverHalf != null) {
        val mid = w / 2f
        val other = if (screen.coverHalf == CoverSide.LEFT) Rect(mid, 0f, w, h) else Rect(0f, 0f, mid, h)
        drawRect(Color.Black.copy(alpha = 0.45f), other.topLeft, other.size)
    }

    if (dock == DockPosition.NONE) return

    // Dock
    val horizontal = isHorizontal(dock)
    val iconSize = t * 0.62f
    val slot = t * 0.92f
    val maxIcons = ((if (horizontal) w - 2 * m else h - 2 * m) / slot).toInt().coerceAtLeast(1)
    val count = (if (screen.dockIcons == 0) maxIcons else screen.dockIcons).coerceAtMost(maxIcons)
    val total = count + if (screen.drawerButton != null) 1 else 0
    val fitLength = (total.coerceAtMost(maxIcons) * slot + t * 0.3f)
    val full = screen.dockLength == DockLength.FULL

    val rect = if (horizontal) {
        val len = if (full) w - 2 * m else fitLength.coerceAtMost(w - 2 * m)
        val left = (w - len) / 2f
        val top = if (dock == DockPosition.BOTTOM) h - m - t else m
        Rect(left, top, left + len, top + t)
    } else {
        val len = if (full) h - 2 * m else fitLength.coerceAtMost(h - 2 * m)
        val top = when (screen.dockAlign) {
            DockAlign.START -> m
            DockAlign.CENTER -> (h - len) / 2f
            DockAlign.END -> h - m - len
        }
        val left = if (dock == DockPosition.LEFT) m else w - m - t
        Rect(left, top, left + t, top + len)
    }

    if (look.shape != DockShape.NONE) {
        val radius = if (look.shape == DockShape.SQUARE) 0f else (look.radius / 40f).coerceIn(0f, 1f) * t / 2f
        drawRoundRect(
            Color(look.panelColor).copy(alpha = look.opacity.coerceIn(0.05f, 1f)),
            rect.topLeft,
            rect.size,
            CornerRadius(radius, radius),
        )
    }

    // Spread the icons along the dock
    val along = if (horizontal) rect.width else rect.height
    val step = along / total.coerceAtMost(maxIcons)
    for (i in 0 until total.coerceAtMost(maxIcons)) {
        val pos = (if (horizontal) rect.left else rect.top) + step * (i + 0.5f)
        val center = if (horizontal) Offset(pos, rect.center.y) else Offset(rect.center.x, pos)
        val isButton = screen.drawerButton != null &&
            ((screen.drawerButton == ButtonEnd.START && i == 0) ||
                (screen.drawerButton == ButtonEnd.END && i == total.coerceAtMost(maxIcons) - 1))
        if (isButton) {
            drawCircle(Q.Accent, iconSize / 2f, center)
            drawCircle(Color.White, iconSize / 4.5f, center, style = Stroke(width = iconSize / 9f))
        } else {
            drawIcon(center, iconSize, look.iconShape, Color.White.copy(alpha = 0.95f))
        }
    }
}

private fun DrawScope.drawIcon(center: Offset, size: Float, shape: IconShape, color: Color) {
    val half = size / 2f
    val corner = when (shape) {
        IconShape.SYSTEM, IconShape.CIRCLE -> {
            drawCircle(color, half, center)
            return
        }
        IconShape.SQUIRCLE -> size * 0.38f
        IconShape.ROUNDED -> size * 0.24f
        IconShape.SQUARE -> size * 0.06f
    }
    drawRoundRect(color, Offset(center.x - half, center.y - half), Size(size, size), CornerRadius(corner, corner))
}
