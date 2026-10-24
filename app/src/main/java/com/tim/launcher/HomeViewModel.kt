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
        fill(cover, read(Prefs.L_COVER), cols * rows)
        fill(extra, read(Prefs.L_EXTRA), cols * rows)
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

    fun move(from: SlotRef, to: SlotRef) {
        val a = list(from.area)
        val b = list(to.area)
        if (from.index !in a.indices || to.index !in b.indices) return
        val moving = a[from.index]
        a[from.index] = b[to.index]
        b[to.index] = moving
        save()
    }

    fun addToHome(key: String) {
        val label = apps[key]?.label ?: key
        val slot = cover.indexOfFirst { it == null }
        if (slot >= 0) {
            cover[slot] = key
            Diag.log("Added $label to the cover layout")
        } else {
            val extraSlot = extra.indexOfFirst { it == null }
            if (extraSlot >= 0) {
                extra[extraSlot] = key
                Diag.log("Cover layout full; added $label to the other half")
            } else {
                Diag.log("No empty slots for $label")
                return
            }
        }
        save()
    }

    fun setGrid(newCols: Int, newRows: Int) {
        val c = newCols.coerceIn(3, 7)
        val r = newRows.coerceIn(4, 9)
        if (c == cols && r == rows) return
        fun resized(list: List<String?>, size: Int) = list.filterNotNull().take(size)
        val coverItems = resized(cover, c * r)
        val extraItems = resized(extra, c * r)
        val dockItems = resized(dockCover, c)
        val dockExtraItems = resized(dockExtra, c)
        cols = c
        rows = r
        fill(cover, coverItems, c * r)
        fill(extra, extraItems, c * r)
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
        Diag.log("Wallpaper mode: $mode")
    }

    fun resetLayout() {
        prefs.edit().putBoolean(Prefs.LAYOUT_READY, false).apply()
        if (sortedApps.isNotEmpty()) fillDefaults()
    }

    companion object {
        private const val ICON_PX = 192
    }
}
