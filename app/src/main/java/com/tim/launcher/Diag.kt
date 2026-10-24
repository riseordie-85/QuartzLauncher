package com.tim.launcher

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Phase 0 diagnostics: counters and an event log shown in the test panel and copied into reports. */
object Diag {
    var activityCreates by mutableIntStateOf(0)
    var configChanges by mutableIntStateOf(0)
    var lastRelayoutMs by mutableLongStateOf(-1L)

    /** uptimeMillis of the last configuration change (fold/unfold, rotation). */
    var lastConfigAt = 0L

    val events = mutableStateListOf<String>()

    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val main = Handler(Looper.getMainLooper())

    fun log(message: String) {
        val line = "${time.format(Date())}  $message"
        Log.d("QuartzTest", line)
        if (Looper.myLooper() == Looper.getMainLooper()) add(line) else main.post { add(line) }
    }

    private fun add(line: String) {
        events.add(0, line)
        while (events.size > 60) events.removeAt(events.lastIndex)
    }
}

/** Settings shared by the launcher and the test wallpaper (same process). */
object Prefs {
    private const val FILE = "quartz_phase0"

    const val COVER_SIDE = "cover_side"     // "left" or "right"
    const val COLS = "cols"
    const val ROWS = "rows"
    const val WP_MODE = "wp_mode"           // "matched", "independent", "separate"
    const val FOLDABLE = "foldable"
    const val HINT_SEEN = "hint_seen"

    const val LAYOUT_READY = "layout_ready"
    const val L_COVER = "layout_cover"
    const val L_EXTRA = "layout_extra"
    const val L_DOCK_COVER = "layout_dock_cover"
    const val L_DOCK_EXTRA = "layout_dock_extra"

    fun get(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
