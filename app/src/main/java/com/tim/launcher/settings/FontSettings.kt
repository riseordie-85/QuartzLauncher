package com.tim.launcher.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.Fonts
import com.tim.launcher.ui.BodyText
import com.tim.launcher.ui.GlassPanel
import com.tim.launcher.ui.Q
import com.tim.launcher.ui.RowDivider
import com.tim.launcher.ui.ScreenTitle
import com.tim.launcher.ui.SectionLabel
import com.tim.launcher.ui.SettingRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings › Fonts: a font for the home screen, dock, app drawer and widgets, and fonts you've added. */
@Composable
fun FontSettings(ctx: SettingsContext) {
    val context = LocalContext.current
    val store = ctx.store
    val scope = rememberCoroutineScope()
    // The phone's own fonts take a moment to look through the first time
    val phone by produceState(emptyList<Fonts.Choice>()) { value = withContext(Dispatchers.IO) { Fonts.phoneChoices() } }
    val imported = remember(Fonts.version) { Fonts.importedChoices(context) }
    var open by remember { mutableStateOf<Setting<String>?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val added = ArrayList<String>()
            var problem: String? = null
            withContext(Dispatchers.IO) {
                uris.forEach { uri -> runCatching { Fonts.import(context, uri) }.onSuccess { added += it }.onFailure { problem = it.message } }
            }
            toast(
                context,
                when {
                    added.isNotEmpty() -> "Added ${added.distinct().joinToString()}"
                    else -> "Couldn't add that: ${problem ?: "not a font"}"
                },
            )
        }
    }

    ScreenScaffold(ctx) {
        ScreenTitle("Fonts", "For app and folder names, and the widgets' numbers and words.")

        SectionLabel("Where")
        GlassPanel {
            listOf(
                Triple("Home screen", S.fontHome, false),
                Triple("Dock", S.fontDock, true),
                Triple("App drawer", S.fontDrawer, true),
                Triple("Widgets", S.fontWidgets, false),
            ).forEachIndexed { i, (title, setting, followsHome) ->
                if (i > 0) RowDivider()
                val id = store[setting]
                SettingRow(title, Fonts.nameOf(id), onClick = { open = if (open == setting) null else setting }) {
                    Text("Aa", color = Q.Text, fontSize = 22.sp, fontFamily = remember(id, Fonts.version) { Fonts.family(context, if (id == Fonts.HOME) store[S.fontHome] else id) })
                }
                if (open == setting) {
                    FontList(
                        selected = id,
                        followsHome = followsHome,
                        phone = phone,
                        imported = imported,
                    ) {
                        store.set(setting, value = it)
                        open = null
                    }
                }
            }
        }

        SectionLabel("Your fonts")
        GlassPanel {
            SettingRow("Add fonts", "A .ttf or .otf file, or a family's .zip as it downloads from fonts.google.com", onClick = {
                importer.launch(arrayOf("font/*", "application/zip", "application/x-zip-compressed", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"))
            })
            imported.forEach { font ->
                RowDivider()
                SettingRow(font.name, "Added by you") {
                    IconButton(onClick = {
                        Fonts.remove(context, font.id)
                        // Anywhere it was used goes back to the usual font
                        listOf(S.fontHome, S.fontDock, S.fontDrawer, S.fontWidgets).forEach { s ->
                            if (store[s] == font.id) store.set(s, value = s.default)
                        }
                    }) { Icon(Icons.Rounded.Close, contentDescription = "Remove ${font.name}", tint = Q.TextDim) }
                }
            }
        }
        BodyText(
            "Fonts stay on this phone. A font you've added goes inside theme files you export, so only share themes with fonts whose license allows it (every Google Font does).",
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

/** Every font to pick from, each shown in itself, grouped by where it comes from. */
@Composable
private fun FontList(
    selected: String,
    followsHome: Boolean,
    phone: List<Fonts.Choice>,
    imported: List<Fonts.Choice>,
    onPick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (followsHome) FontChoice(Fonts.Choice(Fonts.HOME, "Same as home screen"), selected, sample = false, onPick = onPick)
        Group("Built in")
        Fonts.builtIn.forEach { FontChoice(it, selected, onPick = onPick) }
        if (imported.isNotEmpty()) {
            Group("Added by you")
            imported.forEach { FontChoice(it, selected, onPick = onPick) }
        }
        if (phone.isNotEmpty()) {
            Group("On this phone")
            phone.forEach { FontChoice(it, selected, onPick = onPick) }
        }
    }
}

@Composable
private fun Group(text: String) {
    Text(text, color = Q.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun FontChoice(choice: Fonts.Choice, selected: String, sample: Boolean = true, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val family = remember(choice.id, Fonts.version) { Fonts.family(context, choice.id) }
    val on = choice.id == selected
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPick(choice.id) }
            .padding(start = 28.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                choice.name,
                color = if (on) Q.Text else Q.TextDim,
                fontSize = 17.sp,
                fontFamily = if (sample) family else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sample) {
                Text("Photos  Messages  12:45", color = Q.TextFaint, fontSize = 13.sp, fontFamily = family, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (on) {
            Box(Modifier.size(24.dp).background(Q.Accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = "Chosen", tint = Q.Text, modifier = Modifier.size(16.dp))
            }
        }
    }
}
