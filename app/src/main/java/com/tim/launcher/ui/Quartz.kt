package com.tim.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Quartz's palette: dark glass over the wallpaper, one violet accent. */
object Q {
    val Accent = Color(0xFF8C6CFF)
    val AccentSoft = Color(0x338C6CFF)
    val Text = Color(0xFFF4F2FA)
    val TextDim = Color(0xB3F4F2FA)
    val TextFaint = Color(0x80F4F2FA)
    val Glass = Color(0x1FFFFFFF)
    val GlassStrong = Color(0x2EFFFFFF)
    val Edge = Color(0x1AFFFFFF)
    val Scrim = Color(0x8C0B0A12)
    val Danger = Color(0xFFE5534B)

    val PanelShape = RoundedCornerShape(24.dp)
    val ChipShape = RoundedCornerShape(14.dp)
}

@Composable
fun QuartzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Q.Accent,
            onPrimary = Color.White,
            surface = Color(0xFF1C1A26),
            onSurface = Q.Text,
            surfaceContainer = Color(0xFF221F2E),
        ),
        content = content,
    )
}

/** A rounded glass panel: the building block of every Quartz surface. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Q.PanelShape)
            .background(Q.Glass)
            .border(1.dp, Q.Edge, Q.PanelShape),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Q.TextFaint,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)) {
        Text(title, color = Q.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Text(subtitle, color = Q.TextDim, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 20.dp).height(1.dp).background(Q.Edge))
}

/** A setting row: title, optional subtitle, optional link chip underneath, and a control on the right. */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    link: LinkState? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Q.Text.copy(alpha = alpha), fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, color = Q.TextDim.copy(alpha = 0.7f * alpha), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
            }
            if (link != null) LinkChip(link, Modifier.padding(top = 8.dp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun NavRow(title: String, subtitle: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Q.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Q.Text, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Q.Text, fontSize = 17.sp)
            if (subtitle != null) Text(subtitle, color = Q.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Q.TextFaint)
    }
}

/** Whether a per-screen setting follows the others ("Synced") or is set just here. */
class LinkState(val linked: Boolean, val sharedWith: String, val onToggle: () -> Unit)

@Composable
fun LinkChip(state: LinkState, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (state.linked) Q.AccentSoft else Q.Glass, label = "link")
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .clickable { state.onToggle() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (state.linked) Icons.Rounded.Link else Icons.Rounded.LinkOff,
            contentDescription = null,
            tint = Q.Text,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (state.linked) "Synced with ${state.sharedWith}" else "Only this screen",
            color = Q.Text,
            fontSize = 12.sp,
        )
    }
}

/** A compact dropdown: the current choice on the right, the list opens in place. */
@Composable
fun <T> Dropdown(selected: T, options: List<T>, label: (T) -> String, enabled: Boolean = true, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(Q.ChipShape)
                .background(Q.GlassStrong)
                .clickable(enabled = enabled) { open = true }
                .padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(selected), color = Q.Text.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 14.sp)
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = Q.TextDim, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label(option),
                            color = if (option == selected) Q.Accent else Q.Text,
                            fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
fun Toggle(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Q.Accent,
            checkedThumbColor = Color.White,
            uncheckedTrackColor = Q.Glass,
            uncheckedThumbColor = Q.TextDim,
            uncheckedBorderColor = Q.Edge,
        ),
    )
}

/** A slider row with its value shown on the right of the title. */
@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    steps: Int = 0,
    link: LinkState? = null,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Q.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(valueLabel, color = Q.TextDim, fontSize = 14.sp)
        }
        if (link != null) LinkChip(link, Modifier.padding(top = 6.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Q.Accent,
                inactiveTrackColor = Q.Glass,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

/** − value + */
@Composable
fun Stepper(value: Int, range: IntRange, enabled: Boolean = true, format: (Int) -> String = { "$it" }, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", enabled && value > range.first) { onChange(value - 1) }
        Text(
            format(value),
            color = Q.Text.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp),
        )
        StepButton("+", enabled && value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (enabled) Q.GlassStrong else Q.Glass)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Q.Text.copy(alpha = if (enabled) 1f else 0.3f), fontSize = 18.sp)
    }
}

/** Two to four short choices side by side. Longer lists use [Dropdown]. */
@Composable
fun <T> Segmented(selected: T, options: List<T>, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Q.ChipShape)
            .background(Q.Glass)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) Q.Accent else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), color = Q.Text, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
            }
        }
    }
}

/** A large tappable choice card, used by the setup wizard. */
@Composable
fun ChoiceCard(title: String, subtitle: String? = null, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Q.AccentSoft else Q.Glass, label = "choice")
    Column(
        modifier
            .fillMaxWidth()
            .clip(Q.ChipShape)
            .background(bg)
            .border(1.5.dp, if (selected) Q.Accent else Q.Edge, Q.ChipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, color = Q.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        if (subtitle != null) Text(subtitle, color = Q.TextDim, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (enabled) Q.Accent else Q.Glass)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Q.TextDim, fontSize = 16.sp)
    }
}

/** Small swatch row for picking a panel color. */
@Composable
fun SwatchRow(selected: Int, colors: List<Int>, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { c ->
            val on = c == selected
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (on) 3.dp else 1.dp, if (on) Color.White else Q.Edge, CircleShape)
                    .clickable { onSelect(c) },
            )
        }
    }
}

val PanelColors: List<Int> = listOf(
    0xFF1E1B2E.toInt(), // night violet
    0xFF0F1115.toInt(), // graphite
    0xFF14324A.toInt(), // deep blue
    0xFF0F3B36.toInt(), // pine
    0xFF4A1F3D.toInt(), // plum
    0xFFF2EFE8.toInt(), // frost
)

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Q.TextDim, fontSize = 15.sp, lineHeight = 22.sp, modifier = modifier)
}

@Composable
fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(Q.Accent))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Q.Text, fontSize = 15.sp, lineHeight = 21.sp)
    }
}
