package com.tim.launcher.ui

import android.content.ComponentName
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tim.launcher.Prefs
import com.tim.launcher.settings.DockShape
import com.tim.launcher.settings.S
import com.tim.launcher.settings.SettingsStore

/**
 * A life-size sample dock over a wallpaper strip, drawn from the current settings, so shape, roundness, color and
 * see-through changes show up the moment a slider moves. Uses the apps from your real dock when it can.
 */
@Composable
fun DockSample(store: SettingsStore, modifier: Modifier = Modifier) {
    val icons = rememberDockIcons()
    val shape = store[S.dockShape]
    val corner = if (shape == DockShape.SQUARE) 0.dp else store[S.dockRadius].dp
    val panel = Color(store[S.panelColor]).copy(alpha = store[S.panelOpacity].coerceIn(0.05f, 1f))
    Box(
        modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3B2A7A), Color(0xFF244F9E), Color(0xFF14808A), Color(0xFF4E9F3D))))
            .padding(14.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(corner))
                .background(if (shape == DockShape.NONE) Color.Transparent else panel)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(5) { i ->
                val icon = icons.getOrNull(i)
                if (icon != null) {
                    Image(icon, contentDescription = null, modifier = Modifier.size(46.dp))
                } else {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
                }
            }
        }
    }
}

@Composable
private fun rememberDockIcons(): List<ImageBitmap> {
    val context = LocalContext.current
    return remember {
        val saved = Prefs.get(context).getString(Prefs.L_DOCK_COVER, null) ?: return@remember emptyList<ImageBitmap>()
        saved.split("|").filter { it.isNotEmpty() }.take(5).mapNotNull { flat ->
            runCatching {
                val cn = ComponentName.unflattenFromString(flat) ?: return@runCatching null
                context.packageManager.getActivityIcon(cn).toBitmap(144, 144).asImageBitmap()
            }.getOrNull()
        }
    }
}
