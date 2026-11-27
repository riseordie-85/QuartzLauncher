package com.tim.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.ui.BodyText
import com.tim.launcher.ui.GlassPanel
import com.tim.launcher.ui.NavRow
import com.tim.launcher.ui.Q
import com.tim.launcher.ui.RowDivider

/**
 * Search in Settings: every setting by its name, where it lives, and the other words people use for it. A result
 * opens its screen (and the right tab). Nothing leaves the phone: it's a list kept here.
 */
object SettingsSearch {
    /** What was typed, kept while Settings is open, so coming back from a result finds the same list. */
    var last = ""

    class Item(val title: String, val where: String, val route: Route, val words: String = "", val before: () -> Unit = {}) {
        internal val text = "$title $where $words".lowercase()
    }

    private fun dock(title: String, words: String = "") = Item(title, "Dock › Style", Route.DOCK, words) { DockTab.last = DockTab.STYLE }
    private fun place(title: String, words: String = "") = Item(title, "Dock › Placement", Route.DOCK, words) { DockTab.last = DockTab.PLACEMENT }

    val items: List<Item> by lazy {
        listOf(
            // Themes
            Item("Themes", "Settings", Route.THEMES, "look ready-made home screens presets crystal clean midnight paper tide liberl orbment bracer septium"),
            Item("Save your look as a theme", "Themes", Route.THEMES, "export theme file share"),
            Item("Load a theme", "Themes", Route.THEMES, "import theme file"),

            // Home screen
            Item("Columns", "Home screen › Grid", Route.HOME, "grid size layout width"),
            Item("Rows", "Home screen › Grid", Route.HOME, "grid size layout height"),
            Item("Pages", "Home screen › Grid", Route.HOME, "screens add remove"),
            Item("Held upright", "Home screen › Grid", Route.HOME, "portrait rotation tablet fold"),
            Item("Folding", "Home screen", Route.HOME, "fold carry over cover inner unfolded half"),
            Item("App names", "Home screen › Icons", Route.HOME, "labels titles text under icons"),
            Item("Icon size", "Home screen › Icons", Route.HOME, "bigger smaller scale"),
            Item("Rotation", "Home screen", Route.HOME, "landscape portrait turn rotate"),
            Item("Folders", "Home screen", Route.HOME, "make a folder folder icon opens as grid size"),

            // Dock
            place("Dock position", "bottom side left right top where"),
            place("Swap handle", "fold swap side"),
            dock("Dock style", "starting points preset shape"),
            dock("Dock shape", "roundness rounded corners pill wave tooth floating edge padding"),
            dock("Dock background", "show hide glass"),
            dock("Dock app names", "labels"),
            dock("Color and glass", "see-through transparency opacity blur gradient fill tint frosted"),
            dock("Dock pattern", "pattern strength size rings lines dots"),
            dock("Edge light", "rim glow neon outline"),

            // App drawer
            Item("Opening the drawer", "App drawer › Opening", Route.DRAWER, "swipe up button gesture"),
            Item("Drawer button", "App drawer › Opening", Route.DRAWER, "button icon end"),
            Item("Drawer style", "App drawer › Look", Route.DRAWER, "view list grid size scroll columns"),
            Item("Drawer columns", "App drawer › Look", Route.DRAWER, "fit columns to the space"),
            Item("Keep the dock showing", "App drawer › Look", Route.DRAWER, "dock drawer folded unfolded"),
            Item("Tabs", "App drawer", Route.DRAWER, "new tab groups categories work games"),
            Item("Shortcuts", "App drawer", Route.DRAWER, "add a shortcut"),
            Item("Search sources", "App drawer › Search", Route.DRAWER, "apps android settings contacts files search"),
            Item("App order", "App drawer › Order", Route.DRAWER, "sort sorting alphabetical installed headings letter scroller remember where you were"),

            // Wallpaper
            Item("Wallpaper", "Settings", Route.WALLPAPER, "background gradient color photo picture image"),
            Item("Wallpaper presets", "Wallpaper › Start from", Route.WALLPAPER, "gradients starting points amethyst"),
            Item("Wallpaper photo", "Wallpaper", Route.WALLPAPER, "picture image gallery zoom across up and down"),
            Item("Wallpaper pattern", "Wallpaper › Pattern", Route.WALLPAPER, "sparkles shuffle strength size light dark rings from"),
            Item("Darken", "Wallpaper › Readability", Route.WALLPAPER, "dim readability contrast"),
            Item("Cover screen wallpaper", "Wallpaper › Across both screens", Route.WALLPAPER, "fold separate stretched cover unfolded"),

            // Icons and badges
            Item("Icons and badges", "Settings", Route.APPEARANCE, "appearance look"),
            Item("Icon pack", "Icons and badges", Route.APPEARANCE, "get icon packs themed"),
            Item("Icon shape", "Icons and badges", Route.APPEARANCE, "circle squircle square shape on every icon"),
            Item("Icon style", "Icons and badges", Route.APPEARANCE, "minimal one color monochrome tint frosted gems rings paper neon"),
            Item("Icon background", "Icons and badges", Route.APPEARANCE, "tile tint gradient pattern edge shadow"),
            Item("Match icons everywhere", "Icons and badges", Route.APPEARANCE, "dock drawer same different"),
            Item("Icon sizes", "Icons and badges", Route.APPEARANCE, "dock app drawer bigger smaller"),
            Item("Notification badges", "Icons and badges", Route.APPEARANCE, "badges dot count color notification access"),

            // Widgets
            Item("Widgets", "Settings", Route.WIDGETS, "clock flip date calendar weather"),
            Item("24-hour time", "Widgets › Time", Route.WIDGETS, "clock military"),
            Item("Motion", "Widgets", Route.WIDGETS, "animation flip moving icons"),
            Item("Widget card color", "Widgets › Cards", Route.WIDGETS, "flip cards paper"),
            Item("Widget accent", "Widgets › Accent", Route.WIDGETS, "color date temperature"),
            Item("Weather place", "Widgets › Weather", Route.WIDGETS, "city location where zip"),
            Item("Weather units", "Widgets › Weather", Route.WIDGETS, "fahrenheit celsius temperature"),
            Item("Weather icons", "Widgets › Weather", Route.WIDGETS, "fill flat line mono meteocons"),
            Item("Weather warnings", "Widgets › Weather", Route.WIDGETS, "alerts nws national weather service"),
            Item("Rain radar", "Widgets › Weather", Route.WIDGETS, "rainviewer map"),

            // Fonts
            Item("Fonts", "Settings", Route.FONTS, "typeface lettering text"),
            Item("Home screen font", "Fonts", Route.FONTS, "labels app names"),
            Item("Dock font", "Fonts", Route.FONTS, "labels"),
            Item("Drawer font", "Fonts", Route.FONTS, "app drawer labels"),
            Item("Widget font", "Fonts", Route.FONTS, "clock numbers outfit"),
            Item("Add fonts", "Fonts › Your fonts", Route.FONTS, "import ttf otf google fonts zip"),

            // Gestures
            Item("Gestures", "Settings", Route.GESTURES, "swipe tap double tap pinch"),
            Item("Lock screen", "Gestures", Route.GESTURES, "double-tap double tap lock quartz lock accessibility"),
            Item("Open notifications", "Gestures", Route.GESTURES, "swipe down notification shade"),
            Item("Swap dock side", "Gestures", Route.GESTURES, "two-finger swipe pinch page overview"),

            // Privacy and backup
            Item("Privacy", "Privacy & backup", Route.PRIVACY, "tracking ads permissions"),
            Item("Permissions", "Privacy & backup", Route.PRIVACY, "internet location contacts notification access accessibility vibration"),
            Item("Default home app", "Privacy & backup › Permissions", Route.PRIVACY, "launcher home"),
            Item("Export profile", "Privacy & backup › Backup", Route.PRIVACY, "backup save settings layout"),
            Item("Import profile", "Privacy & backup › Backup", Route.PRIVACY, "restore backup"),
            Item("Reset all settings", "Privacy & backup › Backup", Route.PRIVACY, "defaults factory"),

            // Setup and about
            Item("Restart setup", "Settings", Route.WIZARD, "wizard run setup again"),
            Item("About", "Settings", Route.ABOUT, "version credits licenses thanks"),
        )
    }

    /** The items with every typed word in them, best first: the name starting with what you typed, then in the name. */
    fun find(query: String): List<Item> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val q = query.trim().lowercase()
        return items
            .filter { item -> words.all { it in item.text } }
            .sortedBy { item ->
                val t = item.title.lowercase()
                when {
                    t.startsWith(q) -> 0
                    q in t -> 1
                    words.all { it in t } -> 2
                    else -> 3
                }
            }
    }
}

@Composable
fun SettingsSearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Q.Glass)
            .border(1.dp, Q.Edge, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = Q.TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search settings", color = Q.TextFaint, fontSize = 16.sp)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Q.Text, fontSize = 16.sp),
                cursorBrush = SolidColor(Q.Text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Clear",
                tint = Q.TextDim,
                modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onChange("") }.padding(4.dp),
            )
        }
    }
}

@Composable
fun SettingsSearchResults(ctx: SettingsContext, query: String) {
    val found = remember(query) { SettingsSearch.find(query) }
    if (found.isEmpty()) {
        BodyText("Nothing called \"${query.trim()}\". Try another word, like grid, blur or font.", Modifier.padding(horizontal = 20.dp, vertical = 18.dp))
        return
    }
    GlassPanel(Modifier.padding(top = 14.dp)) {
        found.forEachIndexed { i, item ->
            if (i > 0) RowDivider()
            NavRow(item.title, item.where) {
                item.before()
                ctx.go(item.route)
            }
        }
    }
}
