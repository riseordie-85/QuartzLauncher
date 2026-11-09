package com.tim.launcher

import kotlin.math.abs
import android.app.Application
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AppEntry(
    val key: String,
    val label: String,
    val icon: ImageBitmap,
    val component: ComponentName,
    /** Android's app category (games, social, ...), for folder names, automatic tabs and tab suggestions. */
    val category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    /** When it was installed (ms), for "newest first" in the drawer. */
    val installed: Long = 0L,
)

/** A folder on the home screen or dock. It sits in a slot as "folder:<id>". */
data class Folder(
    val id: String,
    val name: String,
    val items: List<String>,
    /** Badge icon name (see FolderBadges), shown with the "folder with badge" icon style. */
    val badge: String = "folder",
)

const val FOLDER_PREFIX = "folder:"

fun isFolderKey(key: String?) = key != null && key.startsWith(FOLDER_PREFIX)

/** Which list a home-screen slot belongs to. "Cover" lists are the folded layout; "extra" fills the other half. */
enum class Area { COVER, EXTRA, DOCK_COVER, DOCK_EXTRA }

data class SlotRef(val area: Area, val index: Int)

/**
 * Which layout the home screen is showing. MAIN is your phone, tablet or cover screen layout (which the unfolded
 * screen also shows, two pages at a time, when the cover carries over). INNER is the unfolded screen's own layout,
 * used when it doesn't: its own grid, pages, apps, dock and widgets. Only one shows at a time; the other waits.
 */
enum class Board { MAIN, INNER }

/**
 * What dropping an icon would do to one page or dock: its new contents with the icon at [at], the icons beside it
 * slid over to make room, and [overflow], an icon pushed off because there was no room left.
 */
data class DropPlan(val area: Area, val items: List<String?>, val at: Int, val overflow: String?)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val launcherApps: LauncherApps = requireNotNull(app.getSystemService(LauncherApps::class.java))
    private val prefs = Prefs.get(app)

    var apps by mutableStateOf<Map<String, AppEntry>>(emptyMap())
        private set
    var sortedApps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var cols by mutableIntStateOf(prefs.getInt(Prefs.COLS, 4))
        private set
    var rows by mutableIntStateOf(prefs.getInt(Prefs.ROWS, 6))
        private set
    var coverLeft by mutableStateOf(prefs.getString(Prefs.COVER_SIDE, "left") != "right")
        private set
    var wallpaperMode by mutableStateOf(prefs.getString(Prefs.WP_MODE, "matched") ?: "matched")
        private set
    var pages by mutableIntStateOf(prefs.getInt(Prefs.PAGES, 3))
        private set
    /** Pages always form one strip; unfolding shows two neighbors at a time. (The old "paired" mode is gone.) */
    val pageMode = "strip"

    /** Bumped in Prefs when a layout is imported, so the home screen knows to reload it. */
    private var layoutStamp = prefs.getInt(Prefs.LAYOUT_STAMP, 0)

    /** The page you chose as home in the page overview, or -1 to follow the cover side. */
    var homePageChoice by mutableIntStateOf(prefs.getInt(Prefs.HOME_PAGE, -1))
        private set

    /** The cover page you're on; kept across fold and unfold. Starts on your home page. */
    var currentPage by mutableIntStateOf(0)

    /**
     * Your home page. Pages form one fixed strip; with the cover on the left half it's page 1, with the cover on
     * the right half it's page 2, so the unfolded screen opens on pages 1 | 2 either way.
     */
    val homePage: Int
        get() = when {
            homePageChoice in 0 until pages -> homePageChoice
            // One screen (phones, tablets, or the unfolded screen's own layout): the middle page, a page either side
            !foldable || board == Board.INNER -> (pages - 1) / 2
            coverLeft -> 0
            else -> minOf(1, pages - 1)
        }

    private val foldable = com.tim.launcher.settings.detectDeviceType(app) == com.tim.launcher.settings.DeviceType.FOLDABLE

    /** True when you've picked your home page yourself (in the page overview). */
    val homePageChosen: Boolean get() = homePageChoice in 0 until pages

    fun setHomePage(page: Int) {
        homePageChoice = page
        prefs.edit().putInt(keys(board).home, page).apply()
        Diag.log("Home page set to page ${page + 1}")
    }

    /** Moves a whole page to a new position. Your home page and the page you're on follow it. */
    fun movePage(from: Int, to: Int) {
        if (from == to || from !in 0 until pages || to !in 0 until pages) return
        val per = perPage
        val chunks = (0 until pages).map { p -> cover.subList(p * per, (p + 1) * per).toList() }.toMutableList()
        chunks.add(to, chunks.removeAt(from))
        fill(cover, chunks.flatten(), pages * per)
        fun follow(i: Int) = when {
            i == from -> to
            from < to && i in (from + 1)..to -> i - 1
            from > to && i in to until from -> i + 1
            else -> i
        }
        if (homePageChosen) setHomePage(follow(homePageChoice))
        currentPage = follow(currentPage)
        save()
        Diag.log("Moved page ${from + 1} to position ${to + 1}")
    }

    /** Deletes one page. Its apps move to empty spots on your other pages. */
    fun deletePage(page: Int) {
        if (pages <= 1 || page !in 0 until pages) return
        val per = perPage
        // Widgets on the page go with it; apps and folders move to your other pages
        val moving = cover.subList(page * per, (page + 1) * per).filterNotNull().filterNot { isWidgetKey(it) }
        val kept = (0 until pages).filter { it != page }.flatMap { p -> cover.subList(p * per, (p + 1) * per).toList() }
        pages -= 1
        fill(cover, kept, pages * per)
        fill(extra, emptyList(), pages * per)
        normalizeWidgets()
        prefs.edit().putInt(keys(board).pages, pages).apply()
        val lost = moving.count { key ->
            val slot = cover.indexOfFirst { it == null }
            if (slot >= 0) {
                cover[slot] = key
                false
            } else {
                true
            }
        }
        when {
            homePageChoice == page -> setHomePage(-1)
            homePageChoice > page -> setHomePage(homePageChoice - 1)
        }
        currentPage = (if (currentPage > page) currentPage - 1 else currentPage).coerceIn(0, pages - 1)
        save()
        Diag.log("Deleted page ${page + 1}" + if (moving.isNotEmpty()) " (moved ${moving.size - lost} apps" + (if (lost > 0) ", $lost had no room" else "") + ")" else "")
    }

    fun goHome() {
        currentPage = homePage
    }

    /** The layout showing now. `cover`, `dockCover`, `cols`, `rows`, `pages` and the page fields are its. */
    var board by mutableStateOf(Board.MAIN)
        private set

    /** The layout that isn't showing, as it was left (null if there's none yet). */
    private class Parked(
        val items: MutableList<String?>,
        val dock: MutableList<String?>,
        val cols: Int,
        val rows: Int,
        val pages: Int,
        val homeChoice: Int,
        val current: Int,
    )

    // Snapshot state, so a swap made during a composition that's thrown away is undone along with `board`
    private var parked by mutableStateOf<Parked?>(null)

    /** The grid and pages to make the unfolded screen's own layout with, the first time it's needed. */
    private var innerSpec = Triple(6, 5, 3)

    private class BoardKeys(val items: String, val dock: String, val cols: String, val rows: String, val pages: String, val home: String)

    private fun keys(b: Board) = if (b == Board.MAIN) {
        BoardKeys(Prefs.L_COVER, Prefs.L_DOCK_COVER, Prefs.COLS, Prefs.ROWS, Prefs.PAGES, Prefs.HOME_PAGE)
    } else {
        BoardKeys(Prefs.L_INNER, Prefs.L_DOCK_INNER, Prefs.INNER_COLS, Prefs.INNER_ROWS, Prefs.INNER_PAGES, Prefs.INNER_HOME_PAGE)
    }

    private fun other(b: Board) = if (b == Board.MAIN) Board.INNER else Board.MAIN

    private fun readList(key: String): List<String?> =
        prefs.getString(key, null)?.split("|")?.map { it.ifEmpty { null } } ?: emptyList()

    /** A saved layout that isn't showing, or null if it was never made. */
    private fun readParked(b: Board): Parked? {
        val k = keys(b)
        if (!prefs.contains(k.items)) return null
        val c = prefs.getInt(k.cols, 4).coerceIn(3, 10)
        val r = prefs.getInt(k.rows, 6).coerceIn(3, 10)
        val p = prefs.getInt(k.pages, 3).coerceIn(1, MAX_PAGES)
        val items = readList(k.items)
        return Parked(MutableList(p * c * r) { items.getOrNull(it) }, readList(k.dock).toMutableList(), c, r, p, prefs.getInt(k.home, -1), -1)
    }

    /**
     * Shows [target]'s layout: the one showing is set aside as it is, and the other comes in. The unfolded screen's
     * own layout is made the first time from your cover pages ([innerSpec] says its grid and pages).
     */
    private fun swapTo(target: Board) {
        if (target == board) return
        val incoming = parked ?: readParked(target) ?: if (target == Board.INNER) seedInner() else return
        val leaving = Parked(cover.toMutableList(), dockCover.toMutableList(), cols, rows, pages, homePageChoice, currentPage)
        board = target
        cols = incoming.cols
        rows = incoming.rows
        pages = incoming.pages
        homePageChoice = incoming.homeChoice
        fill(cover, incoming.items, pages * cols * rows)
        fill(extra, emptyList(), pages * cols * rows)
        fill(dockCover, incoming.dock, incoming.dock.size.coerceAtLeast(1))
        parked = leaving
        currentPage = (incoming.current.takeIf { it >= 0 } ?: homePage).coerceIn(0, pages - 1)
    }

    /**
     * Shows the layout for the screen you're on: [target] is INNER on the unfolded screen when the cover doesn't
     * carry over. [innerCols], [innerRows] and [innerPages] size the unfolded layout if it has to be made.
     */
    fun useBoard(target: Board, innerCols: Int, innerRows: Int, innerPages: Int) {
        innerSpec = Triple(innerCols.coerceIn(3, 10), innerRows.coerceIn(3, 10), innerPages.coerceIn(1, MAX_PAGES))
        if (target == board) return
        reloadIfChanged()
        swapTo(target)
        normalizeWidgets()
        save()
        Diag.log(if (board == Board.INNER) "Showing the unfolded screen's own layout" else "Showing your cover layout")
    }

    /**
     * The unfolded screen's own layout, made from your cover pages so it doesn't start empty: page by page, Quartz
     * widgets copied across the top, then your apps and folders in order, and your dock (folded and unfolded icons).
     * From then on the two are separate. Apps' own widgets can only be in one place, so they stay on the cover.
     */
    private fun seedInner(): Parked {
        val (c, r, p) = innerSpec
        val per = c * r
        val items = MutableList<String?>(p * per) { null }
        fun copy(key: String): String? = when {
            isWidgetKey(key) -> null
            isFolderKey(key) -> folderFor(key)?.let { f ->
                val id = newFolderId()
                folders[id] = f.copy(id = id)
                FOLDER_PREFIX + id
            }
            else -> key
        }
        fun put(key: String, page: Int) {
            val onPage = (page * per until (page + 1) * per).firstOrNull { items[it] == null }
            val slot = onPage ?: items.indexOfFirst { it == null }.takeIf { it >= 0 } ?: return
            copy(key)?.let { items[slot] = it }
        }
        // Widgets first (every page), so apps from pages the unfolded screen doesn't have can't take their room
        for (page in 0 until minOf(pages, p)) {
            widgetsOn(page).forEach { (w, _) ->
                if (w.kind == WidgetKind.ANDROID) return@forEach
                val ww = minOf(w.w, c)
                val hh = minOf(w.h, r)
                val spot = (0..r - hh).flatMap { rr -> (0..c - ww).map { cc -> rr to cc } }.firstOrNull { (rr, cc) ->
                    (0 until hh).all { dr -> (0 until ww).all { dc -> items[page * per + (rr + dr) * c + cc + dc] == null } }
                } ?: return@forEach
                val id = newWidgetId()
                widgets[id] = w.copy(id = id, w = ww, h = hh)
                for (dr in 0 until hh) for (dc in 0 until ww) items[page * per + (spot.first + dr) * c + spot.second + dc] = WIDGET_PREFIX + id
            }
        }
        for (page in 0 until pages) {
            (page * perPage until (page + 1) * perPage).mapNotNull { cover.getOrNull(it) }.filterNot { isWidgetKey(it) }
                .forEach { put(it, page.coerceAtMost(p - 1)) }
        }
        val dock = (dockCover + dockExtra).filterNotNull().mapNotNull { copy(it) }
        saveFolders()
        Diag.log("Made the unfolded screen's own layout (${c}×$r, $p pages) from your cover pages")
        return Parked(items, dock.toMutableList(), c, r, p, -1, -1)
    }

    /** A new folder id (time-based, never one already in use). */
    private fun newFolderId(): String {
        var t = System.currentTimeMillis()
        while (t.toString(36) in folders) t++
        return t.toString(36)
    }

    /** Slots per page. Home lists are flat: page p holds indices p*perPage until (p+1)*perPage. */
    val perPage: Int get() = cols * rows

    val cover = mutableStateListOf<String?>()
    val extra = mutableStateListOf<String?>()
    val dockCover = mutableStateListOf<String?>()
    val dockExtra = mutableStateListOf<String?>()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
            IconRender.packageChanged(packageName) // an icon pack in use may be gone
            reload()
        }
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) {
            IconRender.packageChanged(packageName)
            reload()
        }
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
    }

    /** Folders by id. Declared before init, which reads them. */
    val folders = mutableStateMapOf<String, Folder>()

    /** Quartz widgets by id. Each covers a block of cells holding "widget:<id>". Declared before init too. */
    val widgets = mutableStateMapOf<String, HomeWidget>()

    /** Android ids of the app widgets on your pages, as of the last save. Declared before init too. */
    private val heldAppWidgets = HashSet<Int>()

    init {
        restoreLayout()
        heldAppWidgets += appWidgetIds()
        AppWidgets.cleanUp(app, heldAppWidgets)
        migratePairedPages()
        keepOldHomePage()
        currentPage = homePage
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    /**
     * Phones and tablets used to open on the first page (the middle one is new). A layout made before then keeps
     * the home page it had, rather than opening on a page that may be empty. Runs once.
     */
    private fun keepOldHomePage() {
        if (prefs.getBoolean(Prefs.HOME_MIDDLE, false)) return
        if (!foldable && homePageChoice !in 0 until pages && prefs.getBoolean(Prefs.LAYOUT_READY, false)) {
            setHomePage(if (coverLeft) 0 else minOf(1, pages - 1))
        }
        prefs.edit().putBoolean(Prefs.HOME_MIDDLE, true).apply()
    }

    override fun onCleared() {
        launcherApps.unregisterCallback(callback)
    }

    fun list(area: Area): SnapshotStateList<String?> = when (area) {
        Area.COVER -> cover
        Area.EXTRA -> extra
        Area.DOCK_COVER -> dockCover
        Area.DOCK_EXTRA -> dockExtra
    }

    // ---- Apps ----

    fun reload() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.Default) { loadApps() }
            apps = loaded.associateBy { it.key }
            sortedApps = loaded
            // Quartz Settings is always listed, so only a list with real apps in it counts as loaded (an empty
            // answer from Android mid-update must not clear the layout or make a starter one)
            val real = loaded.any { it.key != StarterApps.QUARTZ_SETTINGS }
            if (!prefs.getBoolean(Prefs.LAYOUT_READY, false) && real) fillDefaults()
            else if (real) dropMissingApps()
            addPendingWidgets()
        }
    }

    private fun loadApps(): List<AppEntry> {
        val context = getApplication<Application>()
        val density = context.resources.displayMetrics.densityDpi
        return launcherApps.getActivityList(null, Process.myUserHandle())
            .filter { it.applicationInfo.packageName != context.packageName }
            .mapNotNull { info ->
                runCatching {
                    AppEntry(
                        key = info.componentName.flattenToString(),
                        label = info.label.toString(),
                        icon = info.getIcon(density).toBitmap(ICON_PX, ICON_PX).asImageBitmap(),
                        component = info.componentName,
                        category = info.applicationInfo.category,
                        installed = info.firstInstallTime,
                    )
                }.getOrNull()
            }
            .plus(quartzSettings(context))
            .sortedBy { it.label.lowercase() }
    }

    /** Quartz Settings as an app you can put on the home screen or dock (Quartz itself isn't listed otherwise). */
    private fun quartzSettings(context: Application): AppEntry {
        val icon = runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }
            .getOrElse { android.graphics.Bitmap.createBitmap(ICON_PX, ICON_PX, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap() }
        return AppEntry(
            key = StarterApps.QUARTZ_SETTINGS,
            label = "Quartz Settings",
            icon = icon,
            component = ComponentName(context, com.tim.launcher.settings.SettingsActivity::class.java),
        )
    }

    /** Android's info page for an app (storage, permissions, notifications…). */
    fun appInfo(app: AppEntry) {
        if (app.key == StarterApps.QUARTZ_SETTINGS) {
            runCatching {
                getApplication<Application>().startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + app.component.packageName))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        runCatching { launcherApps.startAppDetailsActivity(app.component, Process.myUserHandle(), null, null) }
            .onFailure { Diag.log("Couldn't open app info for ${app.label}: ${it.message}") }
    }

    /** False for apps that came with the phone (they can only be turned off, in app info) and for Quartz itself. */
    fun canUninstall(key: String?): Boolean {
        val app = apps[key ?: return false] ?: return false
        if (app.key == StarterApps.QUARTZ_SETTINGS) return false
        val info = runCatching { getApplication<Application>().packageManager.getApplicationInfo(app.component.packageName, 0) }.getOrNull() ?: return false
        val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0 && info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
        return !system
    }

    /** Asks Android to uninstall an app (Android shows its own confirmation). Its spots clear once it's gone. */
    fun uninstall(key: String) {
        val app = apps[key] ?: return
        if (!canUninstall(key)) {
            say("${app.label} came with this device. You can turn it off in its app info.")
            return
        }
        runCatching {
            getApplication<Application>().startActivity(
                android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:" + app.component.packageName))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { say("Couldn't start uninstalling ${app.label}") }
    }

    /** Puts an app from the drawer on the page you're on (first empty spot there, then anywhere). */
    fun addToHome(key: String) {
        val current = currentPage * perPage
        if (putInFirstEmpty(key, Area.COVER to (current until current + perPage))) {
            save()
            say("Added ${apps[key]?.label ?: "it"} to your home screen")
        } else {
            say("No room on your home screen")
        }
    }

    fun launch(app: AppEntry, sourceBounds: android.graphics.Rect?) {
        if (app.key == StarterApps.QUARTZ_SETTINGS) {
            com.tim.launcher.settings.SettingsActivity.open(getApplication<Application>())
            return
        }
        runCatching {
            launcherApps.startMainActivity(app.component, Process.myUserHandle(), sourceBounds, null)
        }.onFailure { Diag.log("Couldn't open ${app.label}: ${it.message}") }
    }

    // ---- Layout ----

    // ---- Folders ----

    fun folderFor(key: String?): Folder? = if (isFolderKey(key)) folders[key!!.removePrefix(FOLDER_PREFIX)] else null

    private fun restoreFolders() {
        folders.clear()
        val text = prefs.getString(Prefs.L_FOLDERS, null) ?: return
        runCatching {
            val array = JSONArray(text)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val items = o.getJSONArray("items").let { a -> (0 until a.length()).map { a.getString(it) } }
                val folder = Folder(o.getString("id"), o.optString("name", "Folder"), items, o.optString("badge", "folder"))
                folders[folder.id] = folder
            }
        }.onFailure { Diag.log("Couldn't read folders: ${it.message}") }
    }

    private fun saveFolders() {
        val array = JSONArray()
        folders.values.forEach { f ->
            array.put(JSONObject().put("id", f.id).put("name", f.name).put("badge", f.badge).put("items", JSONArray(f.items)))
        }
        prefs.edit().putString(Prefs.L_FOLDERS, array.toString()).apply()
    }

    private fun slotOf(key: String): SlotRef? {
        for (area in listOf(Area.COVER, Area.DOCK_COVER, Area.DOCK_EXTRA)) {
            val i = list(area).indexOf(key)
            if (i >= 0) return SlotRef(area, i)
        }
        return null
    }

    /** A name for a new folder: the apps' shared category if Android knows one, otherwise "Folder". */
    private fun suggestName(keys: List<String>): String {
        val categories = keys.mapNotNull { apps[it]?.category }.distinct()
        val c = categories.singleOrNull() ?: return "Folder"
        if (c == ApplicationInfo.CATEGORY_UNDEFINED) return "Folder"
        return ApplicationInfo.getCategoryTitle(getApplication<Application>(), c)?.toString() ?: "Folder"
    }

    /**
     * Drops [key] onto the app or folder at [target]: onto a folder it joins it, onto an app the two become a new
     * folder. The app leaves wherever it came from ([source] slot or [fromFolder]).
     */
    fun makeFolder(target: SlotRef, key: String, source: SlotRef?, fromFolder: String?) {
        if (isFolderKey(key) || target == source) return
        val slots = list(target.area)
        val existing = slots.getOrNull(target.index) ?: return
        if (existing == key || isWidgetKey(existing) || isWidgetKey(key)) return
        val folder = folderFor(existing)
        if (folder != null) {
            if (folder.id == fromFolder) return
            if (key !in folder.items) folders[folder.id] = folder.copy(items = folder.items + key)
            Diag.log("Added ${apps[key]?.label ?: key} to ${folder.name}")
        } else {
            val id = System.currentTimeMillis().toString(36)
            val name = suggestName(listOf(existing, key))
            folders[id] = Folder(id, name, listOf(existing, key))
            slots[target.index] = FOLDER_PREFIX + id
            Diag.log("Made a folder \"$name\"")
        }
        if (source != null) list(source.area)[source.index] = null
        if (fromFolder != null) takeOutOfFolder(fromFolder, key, save = false)
        save()
        saveFolders()
    }

    /** An app dragged out of a folder onto a home or dock slot. */
    fun placeFromFolder(folderId: String, key: String, to: SlotRef) {
        if (keyAt(to) == FOLDER_PREFIX + folderId) return
        takeOutOfFolder(folderId, key, save = false)
        place(key, to)
        saveFolders()
    }

    /** Removes an app from a folder (it leaves the home screen). A folder left with one app turns back into it. */
    fun takeOutOfFolder(folderId: String, key: String, save: Boolean = true) {
        val folder = folders[folderId] ?: return
        val rest = folder.items - key
        val slot = slotOf(FOLDER_PREFIX + folderId)
        when {
            rest.size >= 2 -> folders[folderId] = folder.copy(items = rest)
            else -> {
                folders.remove(folderId)
                if (slot != null) list(slot.area)[slot.index] = rest.firstOrNull()
            }
        }
        if (save) {
            save()
            saveFolders()
        }
    }

    /** "Remove folder": its apps go back onto the screen, the first where the folder was. */
    fun dissolveFolder(folderId: String) {
        val folder = folders.remove(folderId) ?: return
        val slot = slotOf(FOLDER_PREFIX + folderId)
        val items = folder.items
        if (slot != null) {
            list(slot.area)[slot.index] = items.firstOrNull()
            items.drop(1).forEach { putInFirstEmpty(it, screenRange(slot)) }
        }
        save()
        saveFolders()
        Diag.log("Removed folder \"${folder.name}\"; its apps are back on the screen")
    }

    fun renameFolder(folderId: String, name: String) {
        val folder = folders[folderId] ?: return
        folders[folderId] = folder.copy(name = name.trim().ifEmpty { "Folder" })
        saveFolders()
    }

    /** New order for a folder's apps, after dragging them around inside it. Ignored if the apps don't match. */
    fun reorderFolder(folderId: String, items: List<String>) {
        val folder = folders[folderId] ?: return
        if (items == folder.items || items.size != folder.items.size || items.toSet() != folder.items.toSet()) return
        folders[folderId] = folder.copy(items = items)
        saveFolders()
    }

    fun setFolderBadge(folderId: String, badge: String) {
        val folder = folders[folderId] ?: return
        folders[folderId] = folder.copy(badge = badge)
        saveFolders()
    }

    private fun restoreLayout() {
        fun read(key: String): List<String?> = readList(key)
        fill(cover, read(keys(board).items), pages * cols * rows)
        fill(extra, read(Prefs.L_EXTRA), pages * cols * rows)
        val savedDock = read(keys(board).dock)
        val savedExtra = read(Prefs.L_DOCK_EXTRA)
        fill(dockCover, savedDock, if (savedDock.isEmpty()) cols else savedDock.size)
        fill(dockExtra, savedExtra, if (savedExtra.isEmpty()) cols else savedExtra.size)
        restoreFolders()
        widgets.clear()
        val app = getApplication<Application>()
        val shown = HashSet<Int>()
        WidgetJson.read(prefs.getString(Prefs.L_WIDGETS, null)).forEach { w ->
            // An app's widget is only kept if Android still has it (not one from another device's profile, or an
            // app that's gone), and only once (Android shows each widget in one place)
            if (w.kind == WidgetKind.ANDROID && (AppWidgets.gone(app, w.appWidgetId, w.provider) || !shown.add(w.appWidgetId))) {
                return@forEach
            }
            widgets[w.id] = w
        }
        // The other layout, so its widgets and folders count as placed (phones and tablets have no unfolded one,
        // even if a foldable's profile brought one along)
        parked = if (foldable) readParked(other(board)) else null
        if (normalizeWidgets()) save()
    }

    /** Apps placed in the old "paired" inner pages move onto empty spots of your pages. */
    private fun migratePairedPages() {
        val moving = extra.filterNotNull()
        if (moving.isEmpty()) return
        extra.indices.forEach { extra[it] = null }
        var lost = 0
        moving.forEach { key ->
            val slot = cover.indexOfFirst { it == null }
            if (slot >= 0) cover[slot] = key else lost++
        }
        save()
        Diag.log("Moved ${moving.size - lost} apps from the old inner pages onto your pages" + if (lost > 0) " ($lost had no room)" else "")
    }

    /** Uninstalled apps (or apps from another device's imported layout) leave their spots instead of hiding in them. */
    private fun dropMissingApps() {
        var removed = 0
        // Apps inside folders
        folders.values.toList().forEach { f ->
            val kept = f.items.filter { it in apps }
            if (kept.size != f.items.size) {
                removed += f.items.size - kept.size
                folders[f.id] = f.copy(items = kept)
            }
        }
        val away = parked
        (listOf<MutableList<String?>>(cover, extra, dockCover, dockExtra) + listOfNotNull(away?.items, away?.dock)).forEach { list ->
            list.indices.forEach { i ->
                val key = list[i] ?: return@forEach
                if (isWidgetKey(key)) {
                    // The waiting layout's widgets are checked here; the showing one's below
                    if (away != null && (list === away.items || list === away.dock) && widgetFor(key) == null) list[i] = null
                    return@forEach
                } else if (isFolderKey(key)) {
                    val f = folderFor(key)
                    when {
                        f == null || f.items.isEmpty() -> { list[i] = null; f?.let { folders.remove(it.id) } }
                        f.items.size == 1 -> { list[i] = f.items.first(); folders.remove(f.id) }
                    }
                } else if (key !in apps) {
                    list[i] = null
                    removed++
                }
            }
        }
        // Folders no slot points to anymore
        val placed = (cover + dockCover + dockExtra + away?.items.orEmpty() + away?.dock.orEmpty())
            .filter { isFolderKey(it) }.map { it!!.removePrefix(FOLDER_PREFIX) }.toSet()
        folders.keys.filter { it !in placed }.forEach { folders.remove(it) }
        // Widgets of apps that were uninstalled (Android has already taken their ids back); their cells clear below
        val app = getApplication<Application>()
        widgets.values.filter { it.kind == WidgetKind.ANDROID && AppWidgets.gone(app, it.appWidgetId, it.provider) }.forEach {
            widgets.remove(it.id)
            Diag.log("Removed a widget whose app is gone (${it.provider.substringBefore('/')})")
        }
        normalizeWidgets()
        saveFolders()
        save()
        if (removed > 0) Diag.log("Cleared $removed spots for apps that aren't installed")
    }

    /** Reloads the layout if it was replaced by an import. */
    fun reloadIfChanged() {
        val stamp = prefs.getInt(Prefs.LAYOUT_STAMP, 0)
        if (stamp == layoutStamp) return
        layoutStamp = stamp
        // An imported file may not have an unfolded layout: then it's made again from the imported pages
        if (board == Board.INNER && !prefs.contains(Prefs.L_INNER)) {
            val innerDock = dockCover.size
            board = Board.MAIN
            parked = null
            homePageChoice = prefs.getInt(Prefs.HOME_PAGE, -1)
            cols = prefs.getInt(Prefs.COLS, cols)
            rows = prefs.getInt(Prefs.ROWS, rows)
            pages = prefs.getInt(Prefs.PAGES, pages)
            restoreLayout()
            migratePairedPages()
            // Apps from another device that aren't installed here mustn't be copied into the unfolded layout
            if (apps.keys.any { it != StarterApps.QUARTZ_SETTINGS }) dropMissingApps()
            swapTo(Board.INNER)
            applyDockSizes(innerDock, dockExtra.size)
            normalizeWidgets()
            save()
            currentPage = homePage
            Diag.log("Loaded an imported layout")
            return
        }
        cols = prefs.getInt(keys(board).cols, cols)
        rows = prefs.getInt(keys(board).rows, rows)
        pages = prefs.getInt(keys(board).pages, pages)
        homePageChoice = prefs.getInt(keys(board).home, -1)
        restoreLayout()
        migratePairedPages()
        if (apps.keys.any { it != StarterApps.QUARTZ_SETTINGS }) dropMissingApps()
        currentPage = homePage
        Diag.log("Loaded an imported layout")
    }

    private fun fill(target: SnapshotStateList<String?>, items: List<String?>, size: Int) {
        target.clear()
        repeat(size) { target.add(items.getOrNull(it)) }
    }

    /**
     * A brand new home screen: your everyday apps in the dock (calls, messages, the web, the camera, email), a row
     * of useful ones along the bottom of your home page with Quartz Settings first, and a tips card above them.
     * The rest of your apps are in the drawer. The top of the page is left for a widget picked in setup.
     */
    private fun fillDefaults() {
        // A new start is made on your main layout; the unfolded screen's own one is made again from it
        val wasInner = board == Board.INNER
        val innerDock = dockCover.size
        if (wasInner) swapTo(Board.MAIN)
        parked = null
        prefs.edit().remove(Prefs.L_INNER).remove(Prefs.L_DOCK_INNER).remove(Prefs.INNER_HOME_PAGE).apply()
        listOf(cover, extra, dockCover, dockExtra).forEach { list -> list.indices.forEach { list[it] = null } }
        widgets.clear()
        val context = getApplication<Application>()
        val picks = StarterApps.pick(context, apps, dockSize = minOf(5, dockCover.size), homeSize = cols)
        picks.dock.forEachIndexed { i, k -> if (i < dockCover.size) dockCover[i] = k }
        val base = homePage * perPage
        // Along the bottom row, centered
        val row = rows - 1
        val start = (cols - picks.home.size).coerceAtLeast(0) / 2
        picks.home.forEachIndexed { i, k -> if (start + i < cols) cover[base + row * cols + start + i] = k }
        // The tips card just above them
        val tipsW = if (cols >= 6) 3 else minOf(4, cols)
        val tipsH = minOf(2, (rows - 1).coerceAtLeast(1))
        val tipsRow = (row - tipsH).coerceAtLeast(0)
        cellsOf(base + tipsRow * cols + (cols - tipsW) / 2, tipsW, tipsH)?.takeIf { cells -> cells.all { cover[it] == null } }?.let { cells ->
            val id = newWidgetId()
            widgets[id] = HomeWidget(id, WidgetKind.TIPS, tipsW, tipsH)
            cells.forEach { cover[it] = WIDGET_PREFIX + id }
        }
        prefs.edit().putBoolean(Prefs.LAYOUT_READY, true).apply()
        save()
        Diag.log("Starter layout: ${picks.dock.size} apps in the dock, ${picks.home.size} on page ${homePage + 1}, and tips")
        if (wasInner) {
            swapTo(Board.INNER)
            applyDockSizes(innerDock, dockExtra.size)
            save()
        }
    }

    private fun save() {
        fun join(list: List<String?>) = list.joinToString("|") { it ?: "" }
        // Docks show their icons packed together, so keep them stored that way too: no gaps, same order
        listOf(dockCover, dockExtra).forEach { dock ->
            val packed = dock.filterNotNull()
            dock.indices.forEach { i -> val k = packed.getOrNull(i); if (dock[i] != k) dock[i] = k }
        }
        val edit = prefs.edit()
            .putString(keys(board).items, join(cover))
            .putString(Prefs.L_EXTRA, join(extra))
            .putString(keys(board).dock, join(dockCover))
            .putString(Prefs.L_DOCK_EXTRA, join(dockExtra))
            .putString(Prefs.L_WIDGETS, WidgetJson.write(widgets.values))
            .putInt(keys(board).cols, cols)
            .putInt(keys(board).rows, rows)
            .putInt(keys(board).pages, pages)
        // The layout that's waiting is saved as it stands too (a removed app or widget may have left it)
        parked?.let { away ->
            val k = keys(other(board))
            // Packed, but keeping its size (empty slots at the end), so it comes back the same size
            val packed = away.dock.filterNotNull()
            edit.putString(k.items, join(away.items))
                .putString(k.dock, join(packed + List(away.dock.size - packed.size) { null }))
                .putInt(k.cols, away.cols)
                .putInt(k.rows, away.rows)
                .putInt(k.pages, away.pages)
        }
        edit.apply()
        // App widgets that are no longer on any page: give their ids back to Android
        val now = appWidgetIds()
        (heldAppWidgets - now).forEach { AppWidgets.release(getApplication<Application>(), it) }
        heldAppWidgets.clear()
        heldAppWidgets += now
    }


    private fun appWidgetIds(): Set<Int> = widgets.values.mapNotNull { w -> w.appWidgetId.takeIf { w.kind == WidgetKind.ANDROID && it > 0 } }.toSet()

    fun keyAt(ref: SlotRef): String? = list(ref.area).getOrNull(ref.index)

    fun remove(ref: SlotRef) {
        val target = list(ref.area)
        val key = target.getOrNull(ref.index) ?: return
        widgetFor(key)?.let { removeWidget(it.id); return }
        target[ref.index] = null
        save()
        val folder = folderFor(key)
        if (folder != null) {
            folders.remove(folder.id)
            saveFolders()
            Diag.log("Removed folder \"${folder.name}\" from home (its apps are still installed)")
            return
        }
        Diag.log("Removed ${apps[key]?.label ?: key} from home (still installed)")
    }

    fun clearLayout() {
        // The extra dock slots belong to the cover layout: left alone while the unfolded screen's own one shows
        (if (board == Board.MAIN) listOf(cover, extra, dockCover, dockExtra) else listOf(cover, extra, dockCover))
            .forEach { list -> list.indices.forEach { list[it] = null } }
        val away = parked?.items.orEmpty().toSet()
        widgets.keys.filter { WIDGET_PREFIX + it !in away }.forEach { widgets.remove(it) }
        save()
        Diag.log("Cleared the home screen")
    }

    fun setPageCount(count: Int) {
        reloadIfChanged() // an import may have replaced the saved layout; don't save over it with the old one
        val n = count.coerceIn(1, MAX_PAGES)
        if (n == pages) return
        val size = n * perPage
        // Widgets on removed pages go with them
        val moving = (cover.drop(size) + extra.drop(size)).filterNotNull().filterNot { isWidgetKey(it) }
        fill(cover, cover.toList(), size)
        fill(extra, extra.toList(), size)
        normalizeWidgets()
        pages = n
        currentPage = currentPage.coerceAtMost(n - 1)
        val dropped = moving.count { !putInFirstEmpty(it, Area.COVER to ((n - 1) * perPage until size)) }
        prefs.edit().putInt(keys(board).pages, n).apply()
        save()
        Diag.log(
            "Pages: $n" + when {
                moving.isEmpty() -> ""
                dropped == 0 -> " (moved ${moving.size} apps from removed pages)"
                else -> " (moved ${moving.size - dropped}; $dropped had no room and were taken off home)"
            },
        )
    }


    /**
     * Dropping [key] on a home page slot. If the slot is taken, the icons between it and the nearest empty slot on
     * that page slide over one place to make room. On a full page everything from the slot on slides along and the
     * last icon moves to another page.
     */
    fun planGrid(key: String, source: SlotRef?, target: SlotRef): DropPlan? {
        val items = list(target.area).toMutableList()
        val t = target.index
        if (t !in items.indices) return null
        // Widgets stay put: nothing lands on one, and icons slide around them
        if (isWidgetKey(items[t]) || isWidgetKey(key)) return null
        if (source != null && source.area == target.area && items.getOrNull(source.index) == key) items[source.index] = null
        var overflow: String? = null
        if (items[t] != null) {
            val range = screenRange(target).second.filter { it in items.indices && !isWidgetKey(items[it]) }
            val at = range.indexOf(t)
            val empty = range.indices.filter { items[range[it]] == null }.minWithOrNull(compareBy({ abs(it - at) }, { if (it > at) 0 else 1 }))
            when {
                empty == null -> {
                    val last = range.lastIndex
                    overflow = items[range[last]]
                    for (p in last downTo at + 1) items[range[p]] = items[range[p - 1]]
                }
                empty > at -> for (p in empty downTo at + 1) items[range[p]] = items[range[p - 1]]
                else -> for (p in empty until at) items[range[p]] = items[range[p + 1]]
            }
        }
        items[t] = key
        return DropPlan(target.area, items, t, overflow)
    }

    /**
     * Dropping [key] into a dock so it lands at [position] in the dock's order (0 = first). The dock's icons stay
     * packed together; if it's full, its last icon moves off to a page.
     */
    fun planDock(key: String, source: SlotRef?, area: Area, position: Int): DropPlan? {
        val slots = list(area)
        if (slots.isEmpty()) return null
        val items = slots.mapIndexedNotNull { i, k -> k.takeUnless { source != null && source.area == area && i == source.index } }
            .toMutableList<String?>()
        var at = position.coerceIn(0, items.size)
        items.add(at, key)
        var overflow: String? = null
        if (items.size > slots.size) {
            // Full: the last icon (or the one before it, if the new icon went last) moves off
            val out = if (at == items.lastIndex) at - 1 else items.lastIndex
            if (out < 0) return null
            overflow = items.removeAt(out)
            if (out < at) at--
        }
        return DropPlan(area, items + List(slots.size - items.size) { null }, at, overflow)
    }

    /** Writes a plan's contents into its page or dock. False if the layout changed size since it was planned. */
    private fun writePlan(plan: DropPlan, gap: String?): Boolean {
        val slots = list(plan.area)
        if (plan.items.size != slots.size) return false
        plan.items.forEachIndexed { i, k -> if (slots[i] != k) slots[i] = k }
        slots[plan.at] = gap
        return true
    }

    /** Drops [key] as planned: it leaves where it came from, and an icon pushed off the page finds a new spot. */
    fun dropInto(plan: DropPlan, key: String, source: SlotRef?, fromFolder: String?) {
        if (!writePlan(plan, gap = null)) return
        list(plan.area)[plan.at] = key
        if (source != null && source.area != plan.area && list(source.area).getOrNull(source.index) == key) {
            list(source.area)[source.index] = null
        }
        // The pushed-off icon finds its spot first: it may be the folder the app came out of, which has to be on
        // the screen for a folder left with one app to turn back into that app
        plan.overflow?.let { pushed ->
            if (!putInFirstEmpty(pushed, screenRange(SlotRef(plan.area, plan.at)))) {
                Diag.log("No empty slot for ${apps[pushed]?.label ?: pushed}; it was removed from home")
            }
        }
        if (fromFolder != null) takeOutOfFolder(fromFolder, key, save = false)
        save()
        if (fromFolder != null) saveFolders()
    }

    /**
     * Making a folder while the icons are slid over: keep them where they are now (the icon pushed off, if any,
     * goes back into the gap) and drop [key] onto [target].
     */
    fun folderAfterPlan(plan: DropPlan, target: SlotRef, key: String, source: SlotRef?, fromFolder: String?) {
        // The icons made room on another page or dock than the folder: they go back, nothing lands in that gap
        if (screenRange(target) != screenRange(SlotRef(plan.area, plan.at))) {
            makeFolder(target, key, source, fromFolder)
            return
        }
        if (!writePlan(plan, gap = plan.overflow)) return
        makeFolder(target, key, source?.takeIf { it.area != plan.area }, fromFolder)
    }

    fun move(from: SlotRef, to: SlotRef) {
        val a = list(from.area)
        val b = list(to.area)
        if (from.index !in a.indices || to.index !in b.indices) return
        if (isWidgetKey(a[from.index]) || isWidgetKey(b[to.index])) return
        val moving = a[from.index]
        a[from.index] = b[to.index]
        b[to.index] = moving
        save()
    }

    /**
     * Places an app dragged in from the drawer. If the slot is taken, the app already there moves to the first
     * empty spot on the same screen (same page, or the dock it was in), then the page you're on, then anywhere.
     */
    fun place(key: String, to: SlotRef) {
        val target = list(to.area)
        if (to.index !in target.indices || isWidgetKey(target[to.index])) return
        val label = apps[key]?.label ?: key
        val bumped = target[to.index]
        target[to.index] = key
        if (bumped != null && bumped != key) {
            if (!putInFirstEmpty(bumped, screenRange(to))) {
                Diag.log("No empty slot for ${apps[bumped]?.label ?: bumped}; it was removed from home")
            }
        }
        save()
        Diag.log("Placed $label in ${to.area.name.lowercase()} slot ${to.index + 1}")
    }

    /** The slots that make up the screen a slot sits on: its page for home grids, the whole dock for docks. */
    private fun screenRange(ref: SlotRef): Pair<Area, IntRange> = when (ref.area) {
        Area.COVER, Area.EXTRA -> {
            val start = (ref.index / perPage) * perPage
            ref.area to (start until start + perPage)
        }
        else -> ref.area to list(ref.area).indices
    }

    /** Puts [key] in the first empty slot of [first], then the current cover page, then anywhere. */
    private fun putInFirstEmpty(key: String, first: Pair<Area, IntRange>): Boolean {
        val current = currentPage * perPage
        val candidates = listOf(
            first,
            Area.COVER to (current until current + perPage),
            Area.COVER to cover.indices,
            Area.DOCK_COVER to dockCover.indices,
            Area.DOCK_EXTRA to dockExtra.indices,
        ).filter { board == Board.MAIN || it.first != Area.DOCK_EXTRA }
        for ((area, range) in candidates) {
            val target = list(area)
            val slot = range.firstOrNull { it in target.indices && target[it] == null } ?: continue
            target[slot] = key
            return true
        }
        return false
    }

    /**
     * Changes the grid without shuffling your apps: each app keeps its row and column on its page. Apps that no
     * longer fit (a column or row was removed) move to the first empty spot on the same page, then any page.
     */
    fun setGrid(newCols: Int, newRows: Int) {
        reloadIfChanged() // an import may have replaced the saved layout; don't save over it with the old one
        val c = newCols.coerceIn(3, 10)
        val r = newRows.coerceIn(3, 10)
        if (c == cols && r == rows) return
        val oldCols = cols
        val oldRows = rows
        val oldPer = oldCols * oldRows
        val newPer = c * r
        val next = MutableList<String?>(pages * newPer) { null }
        val overflow = mutableListOf<Pair<Int, String>>()
        // Widgets first, as whole blocks: each keeps its top-left where it can, pulled in from the right and bottom
        // edges and narrowed to the new grid (or moved to the nearest free block on its page). Cut off cell by cell,
        // a widget at the right or bottom edge would lose all its cells and disappear.
        val resized = HashMap<String, HomeWidget>()
        for (p in 0 until pages) {
            for (i in 0 until oldPer) {
                val widget = widgetFor(cover.getOrNull(p * oldPer + i)) ?: continue
                if (widget.id in resized) continue // its top-left is the first of its cells in reading order
                val w = minOf(widget.w, c)
                val h = minOf(widget.h, r)
                val wantR = minOf(i / oldCols, r - h)
                val wantC = minOf(i % oldCols, c - w)
                val spot = (0..r - h).flatMap { rr -> (0..c - w).map { cc -> rr to cc } }
                    .filter { (rr, cc) -> (0 until h).all { dr -> (0 until w).all { dc -> next[p * newPer + (rr + dr) * c + cc + dc] == null } } }
                    .minByOrNull { (rr, cc) -> abs(rr - wantR) * 2 + abs(cc - wantC) }
                if (spot == null) {
                    resized[widget.id] = widget // no room on its page; it's dropped below
                    continue
                }
                for (dr in 0 until h) for (dc in 0 until w) next[p * newPer + (spot.first + dr) * c + spot.second + dc] = WIDGET_PREFIX + widget.id
                resized[widget.id] = widget.copy(w = w, h = h)
            }
        }
        resized.values.forEach { widgets[it.id] = it }
        for (p in 0 until pages) {
            for (i in 0 until oldPer) {
                val key = cover.getOrNull(p * oldPer + i) ?: continue
                if (isWidgetKey(key)) continue
                val row = i / oldCols
                val col = i % oldCols
                val slot = p * newPer + row * c + col
                if (row < r && col < c && next[slot] == null) next[slot] = key
                else overflow += p to key
            }
        }
        var lost = 0
        overflow.forEach { (p, key) ->
            val onPage = (p * newPer until (p + 1) * newPer).firstOrNull { next[it] == null }
            val slot = onPage ?: next.indexOfFirst { it == null }.takeIf { it >= 0 }
            if (slot != null) next[slot] = key else lost++
        }
        cols = c
        rows = r
        fill(cover, next, pages * newPer)
        fill(extra, emptyList(), pages * newPer)
        normalizeWidgets()
        prefs.edit().putInt(keys(board).cols, c).putInt(keys(board).rows, r).apply()
        save()
        Diag.log("Grid set to ${c}×$r" + if (overflow.isNotEmpty()) " (moved ${overflow.size - lost} apps that no longer fit)" else "")
    }

    /**
     * Sizes the docks from settings: [folded] slots on the cover (or only) screen and [extra] more that are added
     * after them when unfolded. Icons that no longer fit move to an empty spot instead of disappearing.
     */
    fun setDockSizes(folded: Int, extra: Int) {
        reloadIfChanged() // an import may have replaced the saved layout; don't save over it with the old one
        applyDockSizes(folded, extra)
    }

    private fun applyDockSizes(folded: Int, extra: Int) {
        val f = folded.coerceIn(1, UNLIMITED_DOCK)
        val e = extra.coerceIn(0, UNLIMITED_DOCK)
        if (dockCover.size == f && dockExtra.size == e) return
        val overflow = (dockCover.drop(f) + dockExtra.drop(e)).filterNotNull()
        fill(dockCover, dockCover.toList(), f)
        fill(dockExtra, dockExtra.toList(), e)
        overflow.forEach { key ->
            if (!putInFirstEmpty(key, Area.DOCK_EXTRA to dockExtra.indices)) Diag.log("No room for ${apps[key]?.label ?: key}")
        }
        save()
        Diag.log("Dock: $f folded" + if (e > 0) " + $e more unfolded" else "")
    }

    fun setCoverSide(left: Boolean) {
        if (left == coverLeft) return
        // Keep the same unfolded spread: the cover just becomes its other half, so it shows the neighboring page
        if (pageMode == "strip") {
            currentPage = (if (left) currentPage - 1 else currentPage + 1).coerceIn(0, pages - 1)
        }
        coverLeft = left
        prefs.edit().putString(Prefs.COVER_SIDE, if (left) "left" else "right").apply()
        Diag.log("Cover layout lands on the ${if (left) "left" else "right"} half")
    }

    fun chooseWallpaperMode(mode: String) {
        wallpaperMode = mode
        prefs.edit().putString(Prefs.WP_MODE, mode).apply()
        com.tim.launcher.wallpaper.WallCache.changed()
        Diag.log("Wallpaper mode: " + when (mode) {
            "independent" -> "Shared · stretched"
            "separate" -> "Independent"
            else -> "Shared · cropped"
        })
    }

    fun resetLayout() {
        prefs.edit().putBoolean(Prefs.LAYOUT_READY, false).apply()
        if (sortedApps.any { it.key != StarterApps.QUARTZ_SETTINGS }) fillDefaults()
    }

    // ---- Widgets ----

    fun widgetFor(key: String?): HomeWidget? = if (isWidgetKey(key)) widgets[key!!.removePrefix(WIDGET_PREFIX)] else null

    /** The slot of a widget's top-left cell, or null if it isn't on a page. */
    fun widgetSpot(id: String): Int? = cover.indexOf(WIDGET_PREFIX + id).takeIf { it >= 0 }

    /** Widgets whose top-left cell is on [page], in reading order, with that cell's slot. */
    fun widgetsOn(page: Int): List<Pair<HomeWidget, Int>> {
        val range = page * perPage until (page + 1) * perPage
        // A widget's cells are all on one page, so its first cell in reading order here is its top-left
        val seen = HashSet<String>()
        return range.mapNotNull { i ->
            val w = widgetFor(cover.getOrNull(i)) ?: return@mapNotNull null
            if (seen.add(w.id)) w to i else null
        }
    }

    /** A new widget id (time-based like folder ids, but never one already in use). */
    private fun newWidgetId(): String {
        var t = System.currentTimeMillis()
        while (t.toString(36) in widgets) t++
        return t.toString(36)
    }

    /** The cells a [w]×[h] block covers with its top-left at [anchor], or null if it runs off the page. */
    private fun cellsOf(anchor: Int, w: Int, h: Int): List<Int>? {
        if (anchor !in cover.indices || w < 1 || h < 1) return null
        val page = anchor / perPage
        val c = (anchor % perPage) % cols
        val r = (anchor % perPage) / cols
        if (c + w > cols || r + h > rows) return null
        return (0 until h).flatMap { dr -> (0 until w).map { dc -> page * perPage + (r + dr) * cols + c + dc } }
    }

    /**
     * Adds a widget to [page] where a block of empty cells fits it, top first. If its usual size doesn't fit
     * anywhere, smaller sizes are tried down to its smallest. False if there's no room at all.
     */
    fun addWidget(kind: WidgetKind, page: Int, makeRoom: Boolean = false): Boolean {
        reloadIfChanged()
        val p = page.coerceIn(0, pages - 1)
        val sizes = buildList {
            for (h in minOf(kind.h, rows) downTo minOf(kind.minH, rows)) {
                for (w in minOf(kind.w, cols) downTo minOf(kind.minW, cols)) add(w to h)
            }
        }
        for ((w, h) in sizes) {
            for (r in 0..rows - h) {
                for (c in 0..cols - w) {
                    val cells = cellsOf(p * perPage + r * cols + c, w, h) ?: continue
                    if (cells.any { cover[it] != null }) continue
                    val id = newWidgetId()
                    widgets[id] = HomeWidget(id, kind, w, h)
                    cells.forEach { cover[it] = WIDGET_PREFIX + id }
                    save()
                    Diag.log("Added a ${kind.label.lowercase()} widget (${w}×$h) to page ${p + 1}")
                    return true
                }
            }
        }
        if (makeRoom) {
            // Across the top of the page; the apps there move to empty spots further down (or another page)
            val w = minOf(kind.w, cols)
            val h = minOf(kind.h, rows)
            val cells = cellsOf(p * perPage, w, h)
            if (cells != null && cells.none { isWidgetKey(cover[it]) }) {
                val id = newWidgetId()
                val moving = cells.mapNotNull { cover[it] }
                widgets[id] = HomeWidget(id, kind, w, h)
                cells.forEach { cover[it] = WIDGET_PREFIX + id }
                val lost = moving.count { !putInFirstEmpty(it, Area.COVER to (p * perPage until (p + 1) * perPage)) }
                save()
                Diag.log("Added a ${kind.label.lowercase()} widget to page ${p + 1}; moved ${moving.size} apps to make room" + if (lost > 0) " ($lost had nowhere to go)" else "")
                return true
            }
        }
        Diag.log("No room on page ${p + 1} for a ${kind.label.lowercase()} widget")
        return false
    }

    /**
     * Where a [w]×[h] widget would go on [page]: the first block of empty cells, top first, trying smaller sizes
     * down to [minW]×[minH]. Returns the top-left slot and the size, or null if there's no room.
     */
    fun findSpace(page: Int, w: Int, h: Int, minW: Int, minH: Int): Triple<Int, Int, Int>? {
        val p = page.coerceIn(0, pages - 1)
        for (hh in minOf(h, rows) downTo minOf(minH, rows).coerceAtLeast(1)) {
            for (ww in minOf(w, cols) downTo minOf(minW, cols).coerceAtLeast(1)) {
                for (r in 0..rows - hh) {
                    for (c in 0..cols - ww) {
                        val anchor = p * perPage + r * cols + c
                        val cells = cellsOf(anchor, ww, hh) ?: continue
                        if (cells.all { cover[it] == null }) return Triple(anchor, ww, hh)
                    }
                }
            }
        }
        return null
    }

    /** Puts an app's widget (already given its Android id) on [page]. False if there's no room left for it. */
    fun addAppWidget(page: Int, appWidgetId: Int, provider: String, spans: AppWidgets.Spans): Boolean {
        reloadIfChanged()
        val (anchor, w, h) = findSpace(page, spans.w, spans.h, spans.minW, spans.minH) ?: run {
            say("No room left on this page for that widget")
            return false
        }
        val id = newWidgetId()
        widgets[id] = HomeWidget(id, WidgetKind.ANDROID, w, h, appWidgetId = appWidgetId, provider = provider)
        cellsOf(anchor, w, h)?.forEach { cover[it] = WIDGET_PREFIX + id }
        save()
        Diag.log("Added an app widget (${w}×$h) to page ${anchor / perPage + 1}")
        return true
    }

    /** A glass panel behind an app's widget, or not. */
    fun setWidgetGlass(id: String, on: Boolean) {
        val w = widgets[id] ?: return
        widgets[id] = w.copy(glass = on)
        save()
    }

    /** Widgets picked in setup: added to your home page the next time it shows. */
    fun addPendingWidgets() {
        // Wait for the starter layout, which would otherwise clear it
        if (!prefs.getBoolean(Prefs.LAYOUT_READY, false)) return
        val pending = prefs.getString(Prefs.PENDING_WIDGETS, null)?.takeIf { it.isNotEmpty() } ?: return
        prefs.edit().remove(Prefs.PENDING_WIDGETS).apply()
        pending.split(",").mapNotNull { name -> WidgetKind.forSetup.firstOrNull { it.name == name } }.forEach { kind ->
            if (widgets.values.none { it.kind == kind } && !addSetupWidget(kind) && !addWidget(kind, homePage, makeRoom = true)) {
                say("No room on your home page for the ${kind.label.lowercase()}. Long-press an empty spot and tap Widgets to add it.")
            }
        }
    }

    /**
     * The widget picked in setup, across the top of your home page: the middle three columns and top three rows on
     * a tablet (columns 3 to 5 of 7), the full width on a phone. False if those cells aren't free.
     */
    private fun addSetupWidget(kind: WidgetKind): Boolean {
        val tablet = cols >= 6
        val w = if (tablet) 3 else minOf(kind.w, cols)
        val h = minOf(if (tablet && kind == WidgetKind.FLIP_WEATHER) 3 else kind.h, rows)
        val c = (cols - w) / 2
        val cells = cellsOf(homePage * perPage + c, w, h) ?: return false
        if (cells.any { cover[it] != null }) return false
        val id = newWidgetId()
        widgets[id] = HomeWidget(id, kind, w, h)
        cells.forEach { cover[it] = WIDGET_PREFIX + id }
        save()
        Diag.log("Added the ${kind.label.lowercase()} from setup to your home page")
        return true
    }

    /** Where a widget's top-left would go when dropped with its corner over [index]: pulled in so it stays on the page. */
    fun clampWidgetSpot(id: String, index: Int): Int? {
        val w = widgets[id] ?: return null
        if (index !in cover.indices) return null
        val page = index / perPage
        val c = minOf(index % perPage % cols, cols - w.w).coerceAtLeast(0)
        val r = minOf(index % perPage / cols, rows - w.h).coerceAtLeast(0)
        return page * perPage + r * cols + c
    }

    /**
     * What the page would look like with widget [id] at [anchor], [w]×[h]: other widgets in the way move to the
     * nearest block that fits them, and apps and folders in the way take the nearest empty spots. Null if there
     * isn't room for everything on that page. Nothing changes until it's applied.
     */
    fun planWidget(id: String, anchor: Int, w: Int, h: Int): DropPlan? {
        if (widgets[id] == null) return null
        val key = WIDGET_PREFIX + id
        val cells = cellsOf(anchor, w, h) ?: return null
        val page = anchor / perPage
        val range = page * perPage until (page + 1) * perPage
        val items = cover.toMutableList()
        fun lift(k: String) = items.indices.forEach { if (items[it] == k) items[it] = null }
        lift(key)
        // Widgets in the way are picked up too, and set down again nearby
        val hit = cells.mapNotNull { items[it]?.takeIf { k -> isWidgetKey(k) } }.distinct()
        val displaced = hit.mapNotNull { k -> widgetFor(k)?.let { it to items.indexOf(k) } }
        hit.forEach { lift(it) }
        val bumped = ArrayList<Pair<Int, String>>()
        cells.forEach { i -> items[i]?.let { bumped += i to it } }
        cells.forEach { items[it] = key }
        fun row(i: Int) = (i % perPage) / cols
        fun col(i: Int) = (i % perPage) % cols
        displaced.forEach { (other, from) ->
            // The nearest block with no widget in it, preferring one with fewer apps to move
            var best: List<Int>? = null
            var bestScore = Int.MAX_VALUE
            for (r in 0..rows - other.h) {
                for (c in 0..cols - other.w) {
                    val block = cellsOf(page * perPage + r * cols + c, other.w, other.h) ?: continue
                    if (block.any { isWidgetKey(items[it]) }) continue
                    val score = (abs(r - row(from)) + abs(c - col(from))) * 4 + block.count { items[it] != null } * 3
                    if (score < bestScore) {
                        bestScore = score
                        best = block
                    }
                }
            }
            val block = best ?: return null
            block.forEach { i -> items[i]?.let { bumped += i to it } }
            block.forEach { items[it] = WIDGET_PREFIX + other.id }
        }
        // Each app or folder in the way takes the nearest empty spot to where it was
        val open = range.filter { items[it] == null }.toMutableList()
        if (bumped.size > open.size) return null
        bumped.forEach { (from, k) ->
            val spot = open.minBy { s -> abs(row(s) - row(from)) * 2 + abs(col(s) - col(from)) }
            open.remove(spot)
            items[spot] = k
        }
        return DropPlan(Area.COVER, items, anchor, null)
    }

    /** Where a carried widget would land with its corner over [index], and everything moved out of its way. */
    fun planWidgetMove(id: String, index: Int): DropPlan? {
        val w = widgets[id] ?: return null
        val spot = clampWidgetSpot(id, index) ?: return null
        if (spot == widgetSpot(id)) return null
        return planWidget(id, spot, w.w, w.h)
    }

    /** Puts a widget at [anchor], [w]×[h], moving whatever's in the way (see [planWidget]). False if it won't fit. */
    fun placeWidget(id: String, anchor: Int, w: Int, h: Int): Boolean {
        val widget = widgets[id] ?: return false
        val plan = planWidget(id, anchor, w, h) ?: return false
        if (plan.items.size != cover.size) return false
        plan.items.forEachIndexed { i, k -> if (cover[i] != k) cover[i] = k }
        widgets[id] = widget.copy(w = w, h = h)
        save()
        return true
    }

    /** Moves a widget so its top-left lands at (or as near as fits to) [index]. */
    fun moveWidget(id: String, index: Int): Boolean {
        val w = widgets[id] ?: return false
        val spot = clampWidgetSpot(id, index) ?: return false
        if (spot == widgetSpot(id)) return true
        val ok = placeWidget(id, spot, w.w, w.h)
        if (ok) Diag.log("Moved the ${w.kind.label.lowercase()} widget") else say("Not enough room there")
        return ok
    }

    /** Resizes a widget to [w]×[h] with its top-left at [anchor]. */
    fun resizeWidget(id: String, anchor: Int, w: Int, h: Int): Boolean {
        val widget = widgets[id] ?: return false
        if (anchor == widgetSpot(id) && w == widget.w && h == widget.h) return true
        val ok = placeWidget(id, anchor, w, h)
        if (ok) Diag.log("Resized the ${widget.kind.label.lowercase()} widget to ${w}×$h") else say("Not enough room for that size")
        return ok
    }

    /** Switches a widget to another kind, growing it to that kind's smallest size if it has to. */
    fun changeWidgetKind(id: String, kind: WidgetKind): Boolean {
        val widget = widgets[id] ?: return false
        // An app's widget is what it is; and a Quartz widget can't become one
        if (widget.kind == WidgetKind.ANDROID || kind == WidgetKind.ANDROID) return false
        val anchor = widgetSpot(id) ?: return false
        val w = maxOf(widget.w, minOf(kind.minW, cols))
        val h = maxOf(widget.h, minOf(kind.minH, rows))
        val spot = clampWidgetSpot(id, anchor)?.let { a ->
            // Clamp for the new size
            val page = a / perPage
            page * perPage + minOf((a % perPage) / cols, rows - h).coerceAtLeast(0) * cols + minOf((a % perPage) % cols, cols - w).coerceAtLeast(0)
        } ?: return false
        if ((w != widget.w || h != widget.h || spot != anchor) && !placeWidget(id, spot, w, h)) {
            say("Not enough room to make it a ${kind.label.lowercase()}")
            return false
        }
        widgets[id] = widgets.getValue(id).copy(kind = kind)
        save()
        Diag.log("Widget is now a ${kind.label.lowercase()}")
        return true
    }

    fun removeWidget(id: String) {
        val widget = widgets.remove(id) ?: return
        val key = WIDGET_PREFIX + id
        cover.indices.forEach { if (cover[it] == key) cover[it] = null }
        extra.indices.forEach { if (extra[it] == key) extra[it] = null }
        parked?.items?.let { list -> list.indices.forEach { if (list[it] == key) list[it] = null } }
        save()
        Diag.log("Removed the ${widget.kind.label.lowercase()} widget")
    }

    /**
     * Keeps widgets whole after pages or the grid change: each one becomes a clean block from its top-left cell,
     * narrowed to fit; cells pointing at a widget that's gone are cleared, and a widget with no cells left is
     * dropped. True if anything changed.
     */
    private fun normalizeWidgets(): Boolean {
        var changed = false
        listOf(cover, extra, dockCover, dockExtra).forEach { list ->
            list.indices.forEach { i ->
                val k = list[i]
                if (isWidgetKey(k) && (list !== cover || widgetFor(k) == null)) {
                    list[i] = null
                    changed = true
                }
            }
        }
        val away = parked?.items.orEmpty().filter { isWidgetKey(it) }.toSet()
        widgets.values.toList().forEach { widget ->
            val key = WIDGET_PREFIX + widget.id
            if (key in away) return@forEach // on the layout that isn't showing
            val cells = cover.indices.filter { cover[it] == key }
            if (cells.isEmpty()) {
                widgets.remove(widget.id)
                changed = true
                return@forEach
            }
            val page = cells.first() / perPage
            val onPage = cells.filter { it / perPage == page }
            val r0 = onPage.minOf { (it % perPage) / cols }
            val c0 = onPage.minOf { (it % perPage) % cols }
            val w = minOf(widget.w, cols - c0).coerceAtLeast(1)
            val h = minOf(widget.h, rows - r0).coerceAtLeast(1)
            val block = cellsOf(page * perPage + r0 * cols + c0, w, h) ?: return@forEach
            if (cells.toSet() == block.toSet() && w == widget.w && h == widget.h) return@forEach
            changed = true
            cells.forEach { cover[it] = null }
            if (block.any { isWidgetKey(cover[it]) }) {
                widgets.remove(widget.id) // overlapping another widget (only from a damaged layout)
                return@forEach
            }
            val bumped = block.mapNotNull { cover[it] }
            block.forEach { cover[it] = key }
            bumped.forEach { k -> putInFirstEmpty(k, Area.COVER to (page * perPage until (page + 1) * perPage)) }
            widgets[widget.id] = widget.copy(w = w, h = h)
        }
        return changed
    }

    private fun say(text: String) {
        Toast.makeText(getApplication<Application>(), text, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ICON_PX = 192

        const val MAX_PAGES = 9

        /** "Unlimited" docks get this many slots and scroll. */
        const val UNLIMITED_DOCK = 20
    }
}
