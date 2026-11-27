package com.tim.launcher.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tim.launcher.ui.BodyText
import com.tim.launcher.ui.GlassPanel
import com.tim.launcher.ui.PrimaryButton
import com.tim.launcher.ui.RowDivider
import com.tim.launcher.ui.ScreenTitle
import com.tim.launcher.ui.SectionLabel
import com.tim.launcher.ui.SettingRow
import com.tim.launcher.wizard.ThemeCarousel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which theme the carousel was last on, while Quartz is open, so coming back finds it where you left it. */
private var lastTheme = 0

/**
 * Settings › Themes: the ready-made home screens (the same carousel as setup), and saving or loading a theme file.
 * Using one changes the look only (dock, icons, wallpaper, fonts, widget colors, badges): apps, layout and widgets stay.
 */
@Composable
fun ThemesSettings(ctx: SettingsContext) {
    val context = LocalContext.current
    val store = ctx.store
    val themes = remember { HomeThemes.all(context) }
    var index by remember { mutableIntStateOf(lastTheme.coerceIn(0, themes.lastIndex)) }
    var confirm by remember { mutableStateOf<HomeTheme?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // A theme's icons are drawn ahead with the "Styling your icons" popup, as in setup
    val draft = remember { IconDraft(store) }
    val iconApply = rememberIconApply(draft)
    DisposableEffect(Unit) { onDispose { lastTheme = index } }

    fun use(theme: HomeTheme) {
        if (busy) return
        busy = true
        scope.launch {
            // Its photo and fonts are written first, off the main thread; then the settings
            val staged = runCatching { withContext(Dispatchers.IO) { HomeThemes.stage(context, theme) } }.getOrNull()
            val ok = runCatching { HomeThemes.apply(context, store, draft, theme, staged, widget = false) }
                .onFailure { com.tim.launcher.Diag.log("Theme ${theme.name}: ${it.message}") }
                .isSuccess
            if (!ok) {
                busy = false
                toast(context, "${theme.name} couldn't be used")
                return@launch
            }
            iconApply.run {
                busy = false
                toast(context, "${theme.name} is on")
            }
        }
    }

    // Saving your look, and loading one: a name before exporting, and a look at what's coming in before it replaces yours
    var naming by remember { mutableStateOf(false) }
    var themeName by remember { mutableStateOf("") }
    var incoming by remember { mutableStateOf<Pair<String, String>?>(null) } // name, file text
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // A photo and fonts can make the file a few megabytes: written off the main thread
        val name = themeName
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(ThemeFile.export(context, name).toByteArray()) } }
            }
            result.onSuccess { toast(context, "Theme saved") }.onFailure { toast(context, "Saving failed: ${it.message}") }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Empty file")
                    ThemeFile.nameOf(text) to text
                }
            }
            read.onSuccess { incoming = it }.onFailure { toast(context, it.message ?: "That file couldn't be read") }
        }
    }

    ScreenScaffold(ctx) {
        ScreenTitle("Themes", "A whole look in one go. Your apps, layout and widgets stay where they are.")
        Box(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            ThemeCarousel(index) { index = it }
        }
        Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) {
            val theme = themes[index.coerceIn(0, themes.lastIndex)]
            PrimaryButton(if (busy) "Putting it on…" else "Use ${theme.name}", enabled = !busy) { confirm = theme }
        }

        SectionLabel("Your themes")
        GlassPanel {
            SettingRow("Save your look as a theme", "Just the look: dock, icons, wallpaper (with its photo), fonts, widget colors and badges. No apps or layout.", onClick = {
                themeName = ""
                naming = true
            })
            RowDivider()
            SettingRow("Load a theme", "Use a look you saved or someone shared", onClick = {
                importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            })
        }
        BodyText(
            "A theme file is plain JSON, so it's easy to share. Fonts you added go with it.",
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }

    confirm?.let { theme ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Use ${theme.name}?") },
            text = {
                Text(
                    "Your dock, icons, wallpaper, fonts, widget colors and badges change to this theme's. Your apps, layout and " +
                        "widgets stay where they are. To keep your current look, save it as a theme first.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    use(theme)
                }) { Text("Use theme") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }

    if (naming) {
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Name this theme") },
            text = {
                OutlinedTextField(value = themeName, onValueChange = { themeName = it.take(32) }, singleLine = true, placeholder = { Text("My theme") })
            },
            confirmButton = {
                TextButton(onClick = {
                    naming = false
                    val slug = themeName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "my-theme" }
                    exporter.launch("quartz-theme-$slug.json")
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }

    incoming?.let { (name, text) ->
        AlertDialog(
            onDismissRequest = { incoming = null },
            title = { Text("Use \"$name\"?") },
            text = { Text("Your dock, icons, wallpaper, fonts, widget colors and badges change to this theme's. To keep your current look, save it as a theme first.") },
            confirmButton = {
                TextButton(onClick = {
                    incoming = null
                    busy = true
                    scope.launch {
                        // Reading its photo and fonts takes a moment: off the main thread, then its icons drawn ahead
                        val result = runCatching { ThemeFile.importStaged(context, text) }
                        result.onFailure {
                            busy = false
                            toast(context, "Loading failed: ${it.message}")
                        }.onSuccess { applied ->
                            draft.discard()
                            iconApply.run {
                                busy = false
                                toast(context, "Theme \"$applied\" is on")
                            }
                        }
                    }
                }) { Text("Use theme") }
            },
            dismissButton = { TextButton(onClick = { incoming = null }) { Text("Cancel") } },
        )
    }
}
