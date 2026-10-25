package com.tim.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.AdapterView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Widgets from your other apps (Calendar, Gmail, music players…). Quartz hosts them the standard Android way: it
 * asks Android for an id, you allow Quartz to show that app's widget (once per app, Android asks), the app's own
 * setup screen runs if it has one, and Android keeps the widget up to date while the home screen is showing.
 */
object AppWidgets {
    internal const val HOST_ID = 0x51A7
    private const val REQ_BIND = 0x5101
    private const val REQ_CONFIGURE = 0x5102
    private const val REQ_RECONFIGURE = 0x5103

    private var host: QuartzWidgetHost? = null

    fun host(context: Context): AppWidgetHost =
        host ?: QuartzWidgetHost(context.applicationContext).also { host = it }

    fun manager(context: Context): AppWidgetManager = AppWidgetManager.getInstance(context.applicationContext)

    /** Widgets update while the home screen is showing. */
    fun startListening(context: Context) {
        runCatching { host(context).startListening() }.onFailure { Diag.log("App widgets couldn't start: ${it.message}") }
    }

    fun stopListening(context: Context) {
        runCatching { host(context).stopListening() }
    }

    fun info(context: Context, appWidgetId: Int): AppWidgetProviderInfo? =
        if (appWidgetId <= 0) null else runCatching { manager(context).getAppWidgetInfo(appWidgetId) }.getOrNull()

    /**
     * True if a placed widget is gone for good: Android no longer holds its id for Quartz (its app was uninstalled,
     * or it came from another device's layout), or the id now belongs to a different widget. A widget whose app is
     * only unavailable for a moment (still starting up after a reboot) keeps its id, so it isn't gone.
     */
    fun gone(context: Context, appWidgetId: Int, provider: String): Boolean {
        if (appWidgetId <= 0) return true
        val info = info(context, appWidgetId)
        if (info != null) return info.provider.flattenToString() != provider
        val ids = runCatching { host(context).appWidgetIds }.getOrNull() ?: return false
        return appWidgetId !in ids
    }

    /** The widget's view, kept up to date by Android. */
    fun createView(context: Context, appWidgetId: Int): QuartzWidgetHostView? {
        val info = info(context, appWidgetId) ?: return null
        return runCatching { host(context).createView(context, appWidgetId, info) as? QuartzWidgetHostView }
            .onFailure { Diag.log("Couldn't show an app widget: ${it.message}") }
            .getOrNull()
    }

    /** Gives an id back to Android (the widget was removed). */
    fun release(context: Context, appWidgetId: Int) {
        if (appWidgetId <= 0) return
        runCatching { host(context).deleteAppWidgetId(appWidgetId) }
    }

    /** Ids Android still holds for Quartz that no widget uses (left by a crash or a cancelled add). */
    fun cleanUp(context: Context, inUse: Set<Int>) {
        val waiting = pending?.id
        val ids = runCatching { host(context).appWidgetIds }.getOrNull() ?: return
        val stray = ids.filter { it !in inUse && it != waiting }
        stray.forEach { release(context, it) }
        if (stray.isNotEmpty()) Diag.log("Gave back ${stray.size} unused app widget ids")
    }

    /** Every widget your apps offer for the home screen. */
    fun providers(context: Context): List<AppWidgetProviderInfo> =
        runCatching { manager(context).getInstalledProvidersForProfile(Process.myUserHandle()) }.getOrDefault(emptyList())
            .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }

    /** Can it be made bigger or smaller, across and up and down. */
    fun resizable(info: AppWidgetProviderInfo?): Pair<Boolean, Boolean> {
        val mode = info?.resizeMode ?: return false to false
        return (mode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0) to (mode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0)
    }

    fun reconfigurable(info: AppWidgetProviderInfo?): Boolean =
        info?.configure != null && info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0

    /** The size a widget starts at and the smallest it goes, in cells of [cellW] × [cellH] dp. */
    data class Spans(val w: Int, val h: Int, val minW: Int, val minH: Int)

    fun spans(context: Context, info: AppWidgetProviderInfo, cellW: Float, cellH: Float, cols: Int, rows: Int): Spans {
        val density = context.resources.displayMetrics.density
        // Cells keep a little room around a widget, as the app's sizes don't include it
        val pad = WIDGET_PAD.value * 2
        fun cells(px: Int, cell: Float) = ceil((px / density + pad) / cell.coerceAtLeast(1f) - 0.08f).toInt().coerceAtLeast(1)
        val w = (if (info.targetCellWidth > 0) info.targetCellWidth else cells(info.minWidth, cellW)).coerceIn(1, cols)
        val h = (if (info.targetCellHeight > 0) info.targetCellHeight else cells(info.minHeight, cellH)).coerceIn(1, rows)
        val (across, upDown) = resizable(info)
        val minW = if (!across) w else cells(if (info.minResizeWidth in 1..info.minWidth) info.minResizeWidth else info.minWidth, cellW).coerceIn(1, w)
        val minH = if (!upDown) h else cells(if (info.minResizeHeight in 1..info.minHeight) info.minResizeHeight else info.minHeight, cellH).coerceIn(1, h)
        return Spans(w, h, minW, minH)
    }

    // ---- Adding ----

    private class Pending(val id: Int, val info: AppWidgetProviderInfo, val place: (Activity, Int) -> Boolean)

    private var pending: Pending? = null

    /**
     * Adds [info]'s widget: asks Android for an id and permission, runs the app's setup if it has one, then calls
     * [place] with the id and the home screen as it is then (it may have been recreated while Android's screens
     * were showing). If anything is cancelled (or [place] finds no room), the id is given back.
     */
    fun add(activity: Activity, info: AppWidgetProviderInfo, place: (Activity, Int) -> Boolean) {
        pending?.let { finish(activity, false) }
        val id = runCatching { host(activity).allocateAppWidgetId() }.getOrElse {
            Diag.log("Couldn't get an app widget id: ${it.message}")
            return
        }
        pending = Pending(id, info, place)
        val allowed = runCatching { manager(activity).bindAppWidgetIdIfAllowed(id, info.profile, info.provider, Bundle()) }.getOrDefault(false)
        if (allowed) {
            configure(activity)
        } else {
            // Android asks you once whether Quartz may show this app's widgets
            val ask = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            runCatching { @Suppress("DEPRECATION") activity.startActivityForResult(ask, REQ_BIND) }
                .onFailure { finish(activity, false) }
        }
    }

    private fun configure(activity: Activity) {
        val p = pending ?: return
        // Setup can only be skipped when it can be opened again later ("Its settings")
        val optional = p.info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0 &&
            reconfigurable(p.info)
        if (p.info.configure == null || optional) {
            finish(activity, true)
            return
        }
        runCatching { host(activity).startAppWidgetConfigureActivityForResult(activity, p.id, 0, REQ_CONFIGURE, null) }
            .onFailure {
                Diag.log("The widget's setup didn't open: ${it.message}")
                finish(activity, false)
            }
    }

    /** Opens the app's own settings for a widget that's already placed, if it has them. */
    fun reconfigure(activity: Activity, appWidgetId: Int) {
        runCatching { host(activity).startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, REQ_RECONFIGURE, null) }
            .onFailure { Diag.log("The widget's settings didn't open: ${it.message}") }
    }

    /** From the home screen's onActivityResult. True if it was one of ours. */
    fun onResult(activity: Activity, requestCode: Int, resultCode: Int): Boolean {
        when (requestCode) {
            REQ_BIND -> if (resultCode == Activity.RESULT_OK) configure(activity) else finish(activity, false)
            REQ_CONFIGURE -> finish(activity, resultCode == Activity.RESULT_OK)
            REQ_RECONFIGURE -> Unit
            else -> return false
        }
        return true
    }

    private fun finish(activity: Activity, ok: Boolean) {
        val p = pending ?: return
        pending = null
        val placed = ok && runCatching { p.place(activity, p.id) }.getOrDefault(false)
        if (!placed) release(activity, p.id)
        Diag.log(if (placed) "Added ${p.info.provider.packageName}'s widget" else "Didn't add the app widget")
    }
}

private class QuartzWidgetHost(context: Context) : AppWidgetHost(context, AppWidgets.HOST_ID) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        QuartzWidgetHostView(context)
}

/**
 * An app's widget on the home screen. Taps and scrolling go to the widget as usual; holding it (without moving)
 * hands it to Quartz: then it can be dragged to move it, or let go for its menu.
 */
class QuartzWidgetHostView(context: Context) : AppWidgetHostView(context) {
    /** Held long enough: where, in window coordinates. */
    var onHold: ((x: Float, y: Float) -> Unit)? = null
    var onMove: ((dx: Float, dy: Float) -> Unit)? = null
    /** Let go after holding; [moved] says whether it was dragged. */
    var onLetGo: ((moved: Boolean) -> Unit)? = null
    var onCancelHold: (() -> Unit)? = null
    /** Off while resizing, so holding does nothing. */
    var holdEnabled = true

    /**
     * Where the app's widget actually draws inside this view (left, top, right, bottom, px). Many widgets are
     * shorter than the space they're given, so a glass panel can hug what's really there.
     */
    var onContent: ((l: Int, t: Int, r: Int, b: Int) -> Unit)? = null
    private val content = IntArray(4)

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        var l = Int.MAX_VALUE
        var t = Int.MAX_VALUE
        var r = Int.MIN_VALUE
        var b = Int.MIN_VALUE
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE || c.width == 0 || c.height == 0) continue
            l = minOf(l, c.left)
            t = minOf(t, c.top)
            r = maxOf(r, c.right)
            b = maxOf(b, c.bottom)
        }
        if (l >= r || t >= b) return
        if (content[0] != l || content[1] != t || content[2] != r || content[3] != b) {
            content[0] = l
            content[1] = t
            content[2] = r
            content[3] = b
            onContent?.invoke(l, t, r, b)
        }
    }

    /** The last content bounds seen, for a new listener. */
    fun content(): IntArray? = if (content[2] > content[0]) content.copyOf() else null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = 0f
    private var held = false
    private var lastDown = Long.MIN_VALUE
    /** A finger was lifted while holding: the next move starts from wherever the remaining finger is. */
    private var resync = false
    private val location = IntArray(2)

    private val hold = Runnable {
        held = true
        moved = 0f
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        getLocationInWindow(location)
        onHold?.invoke(location[0] + downX, location[1] + downY)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Once held, the touch is Quartz's, including the lift: the button under the finger gets a cancel instead,
        // so holding and letting go doesn't also tap it
        val wasHeld = held && ev.actionMasked != MotionEvent.ACTION_DOWN
        track(ev)
        return wasHeld || held
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        // Take the touch even where the widget has nothing to tap, so the home screen behind doesn't also react
        return true
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // While held the touch stays Quartz's (letting a list inside keep it would lose the move and the lift)
        if (held) return
        // Something inside (a list scrolling) has taken the touch: it isn't a hold
        if (disallowIntercept) removeCallbacks(hold)
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    /** Stops reporting to the home screen (the view has left it; Android may keep it around until it's replaced). */
    fun release() {
        removeCallbacks(hold)
        held = false
        onHold = null
        onMove = null
        onLetGo = null
        onCancelHold = null
        onContent = null
    }

    /**
     * Touches arrive here either looked at on their way to something inside the widget, or handled here; only a
     * press nothing inside takes is seen twice (looked at, then handled), so it's only counted once.
     */
    private fun track(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (ev.downTime == lastDown) return
                lastDown = ev.downTime
                held = false
                downX = ev.x
                downY = ev.y
                lastX = ev.rawX
                lastY = ev.rawY
                resync = false
                removeCallbacks(hold)
                if (holdEnabled) postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
                // A widget with a list inside (an inbox, an agenda) scrolls up and down: keep the home screen's
                // swipes (pages, drawer) from taking the touch first, as Android's own launcher does
                if (scrolls(this)) parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (resync) {
                    resync = false
                    lastX = ev.rawX
                    lastY = ev.rawY
                    return
                }
                val dx = ev.rawX - lastX
                val dy = ev.rawY - lastY
                lastX = ev.rawX
                lastY = ev.rawY
                if (held) {
                    moved += hypot(dx, dy)
                    onMove?.invoke(dx, dy)
                } else if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) {
                    removeCallbacks(hold)
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(hold)
                if (held) {
                    held = false
                    onLetGo?.invoke(moved > slop * 2)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(hold)
                if (held) {
                    held = false
                    onCancelHold?.invoke()
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (!held) removeCallbacks(hold)
            MotionEvent.ACTION_POINTER_UP -> resync = true
        }
    }

    private fun scrolls(v: View): Boolean {
        if (v is AdapterView<*>) return true
        if (v !is ViewGroup) return false
        for (i in 0 until v.childCount) if (scrolls(v.getChildAt(i))) return true
        return false
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hold)
        // Taken off the screen mid-drag (a page went away): the drag can't finish, so it's called off
        if (held) {
            held = false
            onCancelHold?.invoke()
        }
        super.onDetachedFromWindow()
    }
}
