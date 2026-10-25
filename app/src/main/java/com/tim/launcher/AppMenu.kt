package com.tim.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.ui.Q

/**
 * An app held and let go without moving: its icon and name, then App info, Uninstall (for apps you installed),
 * and Remove from home (on the home screen or dock) or Add to home (in the drawer).
 */
@Composable
internal fun AppMenu(
    app: AppEntry,
    canUninstall: Boolean,
    onHome: Boolean,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
    onRemove: () -> Unit,
    onAddToHome: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .pointerInputDismiss(onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.82f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF0221F2E))
                .pointerInputDismiss { }
                .padding(vertical = 8.dp),
        ) {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StyledIcon(app, 40.dp)
                Spacer(Modifier.width(14.dp))
                Text(app.label, color = Q.Text, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            MenuItemRow(Icons.Rounded.Info, "App info", onClick = onInfo)
            if (canUninstall) MenuItemRow(Icons.Rounded.DeleteOutline, "Uninstall", color = Q.Danger, onClick = onUninstall)
            if (onHome) {
                MenuItemRow(Icons.Rounded.RemoveCircleOutline, "Remove from home", onClick = onRemove)
            } else {
                MenuItemRow(Icons.Rounded.AddToHomeScreen, "Add to home screen", onClick = onAddToHome)
            }
        }
    }
}

@Composable
private fun MenuItemRow(icon: ImageVector, text: String, color: Color = Q.Text, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(text, color = color, fontSize = 16.sp)
    }
}

/** Taps here call [onTap] (and don't reach what's underneath). */
private fun Modifier.pointerInputDismiss(onTap: () -> Unit): Modifier =
    this.pointerInput(Unit) { detectTapGestures { onTap() } }
