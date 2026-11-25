package com.tim.launcher.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tim.launcher.ui.Dropdown
import com.tim.launcher.ui.RowDivider
import com.tim.launcher.ui.SettingRow
import com.tim.launcher.ui.SliderRow

/**
 * A stroke's style, and (when there is one) its color and width. Shared by icons and the dock. [what] says what
 * the line goes around, for the row's description.
 */
@Composable
fun StrokeControls(
    what: String,
    style: StrokeStyle,
    color: Int,
    width: Float,
    onStyle: (StrokeStyle) -> Unit,
    onColor: (Int) -> Unit,
    onWidth: (Float) -> Unit,
) {
    var colorOpen by remember { mutableStateOf(false) }
    SettingRow("Stroke", "A line around $what: solid, dashed, dotted, or pen, which thickens and thins like a fountain pen") {
        Dropdown(style, StrokeStyle.entries, { it.label }) { onStyle(it) }
    }
    if (style != StrokeStyle.NONE) {
        RowDivider()
        ColorEditor("Stroke color", color, colorOpen, { colorOpen = !colorOpen }, alpha = true) { onColor(it) }
        SliderRow("Stroke width", width, 0.5f..8f, "%.1f".format(width)) { onWidth((it * 2f).let { v -> kotlin.math.round(v) } / 2f) }
    }
}
