package com.tim.launcher

import android.app.Activity
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Panel = Color(0xF21A1A22)
private val Chip = Color.White.copy(alpha = 0.10f)
private val ChipOn = Color(0xFF7C5CFF)

@Composable
fun TestPanel(activity: Activity, vm: HomeViewModel, screen: ScreenState, onClose: () -> Unit) {
    val context = activity
    val isDefaultHome = isDefaultHome(context)

    // Dim backdrop; tapping it closes the panel
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(10.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Panel)
                // Swallow taps so they don't fall through to the backdrop
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quartz test panel", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Phase 0 · fold test build", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
                PanelButton("Close") { onClose() }
            }

            Section("What this device reports") {
                Mono(readout(screen, isDefaultHome))
            }

            Section("Cover layout lands on") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelButton("Left half", Modifier.weight(1f), selected = vm.coverLeft) { vm.setCoverSide(true) }
                    PanelButton("Right half", Modifier.weight(1f), selected = !vm.coverLeft) { vm.setCoverSide(false) }
                }
            }

            Section("Cover grid (unfolded doubles the columns)") {
                Stepper("Columns", vm.cols, onMinus = { vm.setGrid(vm.cols - 1, vm.rows) }, onPlus = { vm.setGrid(vm.cols + 1, vm.rows) })
                Stepper("Rows", vm.rows, onMinus = { vm.setGrid(vm.cols, vm.rows - 1) }, onPlus = { vm.setGrid(vm.cols, vm.rows + 1) })
            }

            Section("Test wallpaper") {
                PanelButton("Set Quartz test wallpaper") { setTestWallpaper(context) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelButton("Matched", Modifier.weight(1f), selected = vm.wallpaperMode == "matched") { vm.setWallpaperMode("matched") }
                    PanelButton("Independent", Modifier.weight(1f), selected = vm.wallpaperMode == "independent") { vm.setWallpaperMode("independent") }
                    PanelButton("Separate", Modifier.weight(1f), selected = vm.wallpaperMode == "separate") { vm.setWallpaperMode("separate") }
                }
                Hint(
                    "Matched: the cover screen shows its half of one wide image. Independent: each screen shows " +
                        "the whole image. Separate: different wallpaper per screen.",
                )
            }

            Section("Setup") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelButton(if (isDefaultHome) "Default home ✓" else "Set as default home", Modifier.weight(1f)) { requestDefaultHome(activity) }
                    PanelButton("Reset layout", Modifier.weight(1f)) { vm.resetLayout() }
                }
            }

            Section("Report") {
                PanelButton("Copy report to clipboard") {
                    copyReport(context, readout(screen, isDefaultHome) + "\n\n" + "Events:\n" + Diag.events.joinToString("\n"))
                }
                Hint("Paste it back into our chat after testing each device.")
            }

            Section("Event log") {
                Mono(if (Diag.events.isEmpty()) "No events yet" else Diag.events.take(30).joinToString("\n"))
            }
        }
    }
}

private fun readout(screen: ScreenState, isDefaultHome: Boolean): String = buildString {
    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    appendLine("Posture: ${screen.posture.label}")
    appendLine("Window: ${screen.widthDp}×${screen.heightDp}dp (${screen.widthPx}×${screen.heightPx}px @ ${"%.2f".format(screen.density)}x)")
    appendLine("Display id: ${screen.displayId}")
    appendLine("Foldable: ${if (screen.foldable) "yes" else "no"} · hinge sensor: ${if (screen.hingeSensor) "yes" else "no"}")
    appendLine("Fold feature: ${screen.foldDescription()}")
    appendLine("Hinge split at: ${screen.hingeCenterPx?.let { "${it}px" } ?: "middle of the screen"}")
    appendLine("Default home: ${if (isDefaultHome) "yes" else "no"}")
    appendLine("Activity created ${Diag.activityCreates}× · config changes ${Diag.configChanges}")
    append("Last relayout: ${if (Diag.lastRelayoutMs >= 0) "${Diag.lastRelayoutMs}ms" else "no fold/rotate yet"}")
}

private fun isDefaultHome(context: Context): Boolean {
    val roles = context.getSystemService(RoleManager::class.java) ?: return false
    return runCatching { roles.isRoleAvailable(RoleManager.ROLE_HOME) && roles.isRoleHeld(RoleManager.ROLE_HOME) }
        .getOrDefault(false)
}

@Suppress("DEPRECATION")
private fun requestDefaultHome(activity: Activity) {
    val roles = activity.getSystemService(RoleManager::class.java)
    val asked = runCatching {
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 1)
            true
        } else {
            false
        }
    }.getOrDefault(false)
    if (!asked) {
        // Some phones skip the role prompt; the system "Default apps" screen always works
        runCatching { activity.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .onFailure { Diag.log("Couldn't open home settings: ${it.message}") }
    }
    Diag.log("Asked to become the default home app")
}

private fun setTestWallpaper(context: Context) {
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(context, TestWallpaperService::class.java),
        )
    runCatching { context.startActivity(intent) }
        .onFailure {
            Diag.log("Live wallpaper picker didn't open: ${it.message}")
            Toast.makeText(context, "This device didn't open the live wallpaper picker", Toast.LENGTH_LONG).show()
        }
}

private fun copyReport(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("Quartz Phase 0 report", text))
    Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun PanelButton(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) ChipOn else Chip)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Stepper(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.width(90.dp))
        PanelButton("−") { onMinus() }
        Text("$value", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp))
        PanelButton("+") { onPlus() }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Mono(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(12.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 16.sp)
}
