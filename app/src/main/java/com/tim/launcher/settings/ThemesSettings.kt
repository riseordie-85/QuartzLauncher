package com.tim.launcher.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.tim.launcher.ui.BodyText
import com.tim.launcher.ui.GlassPanel
import com.tim.launcher.ui.PrimaryButton
import com.tim.launcher.ui.Q
import com.tim.launcher.ui.RowDivider
import com.tim.launcher.ui.ScreenTitle
import com.tim.launcher.ui.SectionLabel
import com.tim.launcher.ui.SettingRow
import com.tim.launcher.wizard.ThemeCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which theme the carousel was last on, while Quartz is open, so coming back finds it where you left it. */
private var lastTheme = 0

/**
 * Settings › Themes: the chosen theme pinned at the top with its Use button, every ready-made one in a grid below
 * (tap to look), and saving or loading a theme file.
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

    // While a theme goes on, back waits for it (the "Styling your icons" popup follows straight after)
    BackHandler(enabled = busy) {}

    fun use(theme: HomeTheme) {
        if (busy) return
        busy = true
        // Finished even if you leave the screen part way, so a theme is never half on
        scope.launch(NonCancellable) {
            // Its photo and fonts are written first, off the main thread; then the settings
            val staged = try {
                withContext(Dispatchers.IO) { HomeThemes.stage(context, theme) }
            } catch (e: Exception) {
                com.tim.launcher.Diag.log("Theme ${theme.name}: ${e.message}")
                null
            }
            val ok = try {
                HomeThemes.apply(context, store, draft, theme, staged, widget = false)
                true
            } catch (e: Exception) {
                com.tim.launcher.Diag.log("Theme ${theme.name}: ${e.message}")
                false
            }
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

    val apps = rememberSampleApps(8)
    val config = LocalConfiguration.current
    // This screen's shape, so the pictures look like your own home screen
    val aspect = (config.screenWidthDp.toFloat() / config.screenHeightDp.coerceAtLeast(1)).coerceIn(0.35f, 1.8f)
    val theme = themes[index.coerceIn(0, themes.lastIndex)]

    // The chosen theme stays in view at the top while the grid of them all scrolls underneath
    ScreenScaffold(ctx, pinned = {
        ScreenTitle("Themes", "A whole look in one go. Your apps, layout and widgets stay where they are.")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A wide screen's picture is kept to just over half the width, so the name and button have room
            val fullW = maxWidth
            val bigW = minOf((config.screenHeightDp * 0.3f).coerceIn(150f, 340f).dp * aspect, fullW * 0.58f)
            val bigH = bigW / aspect
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Crossfade(theme, label = "theme") { t -> ThemeCard(t, bigW, bigH, apps, current = false) }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(theme.name, color = Q.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(theme.about, color = Q.TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    PrimaryButton(
                        if (busy) "Putting it on…" else "Use theme",
                        Modifier.padding(top = 14.dp),
                        enabled = !busy,
                    ) { confirm = theme }
                }
            }
        }
    }) {
        SectionLabel("All themes")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Read here: inside the Column below, maxWidth can't be reached
            val fullW = maxWidth
            val cols = when {
                fullW >= 640.dp -> 5
                fullW >= 440.dp -> 4
                else -> 3
            }
            val gap = 10.dp
            val tileW = (fullW - gap * (cols - 1)) / cols
            val tileH = tileW / aspect
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                themes.chunked(cols).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEachIndexed { c, t ->
                            val i = r * cols + c
                            Column(
                                Modifier.width(tileW).clip(RoundedCornerShape(12.dp)).clickable { index = i },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                ThemeCard(t, tileW, tileH, apps, current = i == index)
                                Text(
                                    t.name,
                                    color = if (i == index) Q.Text else Q.TextDim,
                                    fontSize = 12.sp,
                                    fontWeight = if (i == index) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionLabel("Your themes")
        GlassPanel {
            SettingRow("Save your look as a theme", "Just the look: dock, icons, wallpaper (with its photo), fonts, widget colors and badges. No apps or layout.", enabled = !busy, onClick = {
                themeName = ""
                naming = true
            })
            RowDivider()
            SettingRow("Load a theme", "Use a look you saved or someone shared", enabled = !busy, onClick = {
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
                    if (busy) return@TextButton
                    busy = true
                    scope.launch(NonCancellable) {
                        // Reading its photo and fonts takes a moment: off the main thread, then its icons drawn ahead
                        val result = try {
                            Result.success(ThemeFile.importStaged(context, text))
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
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
