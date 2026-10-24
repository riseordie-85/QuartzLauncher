package com.tim.launcher

import android.app.Application
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
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
    /** Android's app category (games, social, ...), used only to suggest folder names. */
    val category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
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
        get() = if (homePageChoice in 0 until pages) homePageChoice else if (coverLeft) 0 else minOf(1, pages - 1)

    /** True when you've picked your home page yourself (in the page overview). */
    val homePageChosen: Boolean get() = homePageChoice in 0 until pages

    fun setHomePage(page: Int) {
        homePageChoice = page
        prefs.edit().putInt(Prefs.HOME_PAGE, page).apply()
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
        val moving = cover.subList(page * per, (page + 1) * per).filterNotNull()
        val kept = (0 until pages).filter { it != page }.flatMap { p -> cover.subList(p * per, (p + 1) * per).toList() }
        pages -= 1
        fill(cover, kept, pages * per)
        fill(extra, emptyList(), pages * per)
        prefs.edit().putInt(Prefs.PAGES, pages).apply()
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

    /** Slots per page. Home lists are flat: page p holds indices p*perPage until (p+1)*perPage. */
    val perPage: Int get() = cols * rows

    val cover = mutableStateListOf<String?>()
    val extra = mutableStateListOf<String?>()
    val dockCover = mutableStateListOf<String?>()
    val dockExtra = mutableStateListOf<String?>()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
    }

    /** Folders by id. Declared before init, which reads them. */
    val folders = mutableStateMapOf<String, Folder>()

    init {
        restoreLayout()
        migratePairedPages()
        currentPage = homePage
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
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
            if (!prefs.getBoolean(Prefs.LAYOUT_READY, false) && loaded.isNotEmpty()) fillDefaults()
            else if (loaded.isNotEmpty()) dropMissingApps()
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
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(app: AppEntry, sourceBounds: android.graphics.Rect?) {
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
        if (existing == key) return
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
        fun read(key: String): List<String?> =
            prefs.getString(key, null)?.split("|")?.map { it.ifEmpty { null } } ?: emptyList()
        fill(cover, read(Prefs.L_COVER), pages * cols * rows)
        fill(extra, read(Prefs.L_EXTRA), pages * cols * rows)
        val savedDock = read(Prefs.L_DOCK_COVER)
        val savedExtra = read(Prefs.L_DOCK_EXTRA)
        fill(dockCover, savedDock, if (savedDock.isEmpty()) cols else savedDock.size)
        fill(dockExtra, savedExtra, if (savedExtra.isEmpty()) cols else savedExtra.size)
        restoreFolders()
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
        listOf(cover, extra, dockCover, dockExtra).forEach { list ->
            list.indices.forEach { i ->
                val key = list[i] ?: return@forEach
                if (isFolderKey(key)) {
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
        val placed = (cover + dockCover + dockExtra).filter { isFolderKey(it) }.map { it!!.removePrefix(FOLDER_PREFIX) }.toSet()
        folders.keys.filter { it !in placed }.forEach { folders.remove(it) }
        saveFolders()
        save()
        if (removed > 0) Diag.log("Cleared $removed spots for apps that aren't installed")
    }

    /** Reloads the layout if it was replaced by an import. */
    fun reloadIfChanged() {
        val stamp = prefs.getInt(Prefs.LAYOUT_STAMP, 0)
        if (stamp == layoutStamp) return
        layoutStamp = stamp
        cols = prefs.getInt(Prefs.COLS, cols)
        rows = prefs.getInt(Prefs.ROWS, rows)
        pages = prefs.getInt(Prefs.PAGES, pages)
        restoreLayout()
        migratePairedPages()
        if (apps.isNotEmpty()) dropMissingApps()
        currentPage = homePage
        Diag.log("Loaded an imported layout")
    }

    private fun fill(target: SnapshotStateList<String?>, items: List<String?>, size: Int) {
        target.clear()
        repeat(size) { target.add(items.getOrNull(it)) }
    }

    private fun fillDefaults() {
        listOf(cover, extra, dockCover, dockExtra).forEach { list -> list.indices.forEach { list[it] = null } }
        val keys = sortedApps.map { it.key }.iterator()
        fun take(list: SnapshotStateList<String?>, count: Int) {
            for (i in 0 until minOf(count, list.size)) if (keys.hasNext()) list[i] = keys.next()
        }
        take(cover, cols * (rows - 1))
        take(dockCover, cols)
        take(dockExtra, cols)
        save()
        Diag.log("Filled a starter layout with ${sortedApps.size} apps")
    }

    private fun save() {
        fun join(list: List<String?>) = list.joinToString("|") { it ?: "" }
        prefs.edit()
            .putBoolean(Prefs.LAYOUT_READY, true)
            .putString(Prefs.L_COVER, join(cover))
            .putString(Prefs.L_EXTRA, join(extra))
            .putString(Prefs.L_DOCK_COVER, join(dockCover))
            .putString(Prefs.L_DOCK_EXTRA, join(dockExtra))
            .apply()
    }

    fun keyAt(ref: SlotRef): String? = list(ref.area).getOrNull(ref.index)

    fun remove(ref: SlotRef) {
        val target = list(ref.area)
        val key = target.getOrNull(ref.index) ?: return
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
        listOf(cover, extra, dockCover, dockExtra).forEach { list -> list.indices.forEach { list[it] = null } }
        save()
        Diag.log("Cleared the home screen")
    }

    fun setPageCount(count: Int) {
        reloadIfChanged() // an import may have replaced the saved layout; don't save over it with the old one
        val n = count.coerceIn(1, MAX_PAGES)
        if (n == pages) return
        val size = n * perPage
        val moving = (cover.drop(size) + extra.drop(size)).filterNotNull()
        fill(cover, cover.toList(), size)
        fill(extra, extra.toList(), size)
        pages = n
        currentPage = currentPage.coerceAtMost(n - 1)
        val dropped = moving.count { !putInFirstEmpty(it, Area.COVER to ((n - 1) * perPage until size)) }
        prefs.edit().putInt(Prefs.PAGES, n).apply()
        save()
        Diag.log(
            "Pages: $n" + when {
                moving.isEmpty() -> ""
                dropped == 0 -> " (moved ${moving.size} apps from removed pages)"
                else -> " (moved ${moving.size - dropped}; $dropped had no room and were taken off home)"
            },
        )
    }


    fun move(from: SlotRef, to: SlotRef) {
        val a = list(from.area)
        val b = list(to.area)
        if (from.index !in a.indices || to.index !in b.indices) return
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
        if (to.index !in target.indices) return
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
        )
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
        for (p in 0 until pages) {
            for (i in 0 until oldPer) {
                val key = cover.getOrNull(p * oldPer + i) ?: continue
                val row = i / oldCols
                val col = i % oldCols
                if (row < r && col < c) next[p * newPer + row * c + col] = key else overflow += p to key
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
        prefs.edit().putInt(Prefs.COLS, c).putInt(Prefs.ROWS, r).apply()
        save()
        Diag.log("Grid set to ${c}×$r" + if (overflow.isNotEmpty()) " (moved ${overflow.size - lost} apps that no longer fit)" else "")
    }

    /**
     * Sizes the docks from settings: [folded] slots on the cover (or only) screen and [extra] more that are added
     * after them when unfolded. Icons that no longer fit move to an empty spot instead of disappearing.
     */
    fun setDockSizes(folded: Int, extra: Int) {
        reloadIfChanged() // an import may have replaced the saved layout; don't save over it with the old one
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
        Diag.log("Wallpaper mode: " + when (mode) {
            "independent" -> "Shared · stretched"
            "separate" -> "Independent"
            else -> "Shared · cropped"
        })
    }

    fun resetLayout() {
        prefs.edit().putBoolean(Prefs.LAYOUT_READY, false).apply()
        if (sortedApps.isNotEmpty()) fillDefaults()
    }

    companion object {
        private const val ICON_PX = 192

        const val MAX_PAGES = 9

        /** "Unlimited" docks get this many slots and scroll. */
        const val UNLIMITED_DOCK = 20
    }
}
