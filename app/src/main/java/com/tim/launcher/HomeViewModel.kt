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
    /** "paired": each cover page has its own inner half. "strip": unfolded shows two cover pages side by side. */
    var pageMode by mutableStateOf(prefs.getString(Prefs.PAGE_MODE, "strip") ?: "strip")
        private set

    /** The cover page you're on; kept across fold and unfold. */
    var currentPage by mutableIntStateOf(0)

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
        fill(dockCover, read(Prefs.L_DOCK_COVER), cols)
        fill(dockExtra, read(Prefs.L_DOCK_EXTRA), cols)
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

    fun choosePageMode(mode: String) {
        pageMode = mode
        prefs.edit().putString(Prefs.PAGE_MODE, mode).apply()
        Diag.log("Unfolded pages: " + if (mode == "strip") "side by side (strip)" else "paired with inner pages")
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

    fun setGrid(newCols: Int, newRows: Int) {
        val c = newCols.coerceIn(3, 8)
        val r = newRows.coerceIn(4, 10)
        if (c == cols && r == rows) return
        fun resized(list: List<String?>, size: Int) = list.filterNotNull().take(size)
        val coverItems = resized(cover, pages * c * r)
        val extraItems = resized(extra, pages * c * r)
        val dockItems = resized(dockCover, c)
        val dockExtraItems = resized(dockExtra, c)
        cols = c
        rows = r
        fill(cover, coverItems, pages * c * r)
        fill(extra, extraItems, pages * c * r)
        fill(dockCover, dockItems, c)
        fill(dockExtra, dockExtraItems, c)
        prefs.edit().putInt(Prefs.COLS, c).putInt(Prefs.ROWS, r).apply()
        save()
        Diag.log("Cover grid set to ${c}×$r (unfolded ${c * 2}×$r)")
    }

    fun setCoverSide(left: Boolean) {
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
    }
}
