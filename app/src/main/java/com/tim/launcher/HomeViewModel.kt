package com.tim.launcher

import android.app.Application
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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

data class AppEntry(
    val key: String,
    val label: String,
    val icon: ImageBitmap,
    val component: ComponentName,
)

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

    /** The cover page you're on; kept across fold and unfold. Starts on your home page. */
    var currentPage by mutableIntStateOf(0)

    /**
     * Your home page. Pages form one fixed strip; with the cover on the left half it's page 1, with the cover on
     * the right half it's page 2, so the unfolded screen opens on pages 1 | 2 either way.
     */
    val homePage: Int get() = if (coverLeft || pageMode != "strip") 0 else minOf(1, pages - 1)

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

    private fun restoreLayout() {
        fun read(key: String): List<String?> =
            prefs.getString(key, null)?.split("|")?.map { it.ifEmpty { null } } ?: emptyList()
        fill(cover, read(Prefs.L_COVER), pages * cols * rows)
        fill(extra, read(Prefs.L_EXTRA), pages * cols * rows)
        val savedDock = read(Prefs.L_DOCK_COVER)
        val savedExtra = read(Prefs.L_DOCK_EXTRA)
        fill(dockCover, savedDock, if (savedDock.isEmpty()) cols else savedDock.size)
        fill(dockExtra, savedExtra, if (savedExtra.isEmpty()) cols else savedExtra.size)
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
        listOf(cover, extra, dockCover, dockExtra).forEach { list ->
            list.indices.forEach { i ->
                val key = list[i]
                if (key != null && key !in apps) {
                    list[i] = null
                    removed++
                }
            }
        }
        if (removed > 0) {
            save()
            Diag.log("Cleared $removed spots for apps that aren't installed")
        }
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
        take(extra, cols * 2)
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
        Diag.log("Removed ${apps[key]?.label ?: key} from home (still installed)")
    }

    fun clearLayout() {
        listOf(cover, extra, dockCover, dockExtra).forEach { list -> list.indices.forEach { list[it] = null } }
        save()
        Diag.log("Cleared the home screen")
    }

    fun setPageCount(count: Int) {
        val n = count.coerceIn(1, 5)
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
            Area.EXTRA to extra.indices,
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

        /** "Unlimited" docks get this many slots and scroll. */
        const val UNLIMITED_DOCK = 20
    }
}
