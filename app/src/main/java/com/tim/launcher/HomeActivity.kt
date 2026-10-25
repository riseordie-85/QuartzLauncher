package com.tim.launcher

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewConfiguration
import android.view.MotionEvent
import android.view.InputDevice
import android.os.Looper
import android.os.Handler
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.tim.launcher.settings.S
import com.tim.launcher.settings.SettingsActivity
import com.tim.launcher.settings.SettingsStore
import com.tim.launcher.settings.SetupNag
import com.tim.launcher.settings.detectDeviceType
import com.tim.launcher.settings.ensureGridDefaults

class HomeActivity : ComponentActivity() {

    /** Bumped when Home is pressed while Quartz is already showing, to close the drawer and panels. */
    private var homeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diag.activityCreates++
        val displayId = runCatching { display?.displayId }.getOrNull() ?: -1
        Diag.log("Home created (#${Diag.activityCreates}) on display $displayId")

        SettingsStore.get(this).ensureGridDefaults(detectDeviceType(this))

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeScreen(activity = this, homeTick = homeTick)
            }
        }

    }

    override fun onResume() {
        super.onResume()
        // Until setup is finished it comes back, at the step you were on (see SetupNag)
        val store = SettingsStore.get(this)
        if (!store[S.wizardDone] && SetupNag.due()) {
            SetupNag.opened()
            // A setup window left behind (Android brought the home screen forward, say) is replaced, not doubled
            SetupNag.current?.get()?.takeIf { !it.isFinishing }?.finish()
            SettingsActivity.open(this, wizard = true)
        }
    }

    // App widgets update while the home screen is on screen
    override fun onStart() {
        super.onStart()
        AppWidgets.startListening(this)
    }

    override fun onStop() {
        super.onStop()
        AppWidgets.stopListening(this)
    }

    @Deprecated("Android's widget setup screens still answer through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (AppWidgets.onResult(this, requestCode, resultCode)) return
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    // ---- Mouse ----
    // A right click works like holding your finger down: it opens the long-press menus, and right-click-and-drag
    // moves icons and widgets. The click is passed on as a touch, and its release is held back until it has counted
    // as a long press.

    private val mouseHandler = Handler(Looper.getMainLooper())
    private var rightClick = false
    private var rightDownAt = 0L

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!ev.isFromSource(InputDevice.SOURCE_MOUSE)) return super.dispatchTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (ev.buttonState and MotionEvent.BUTTON_SECONDARY != 0) {
                rightClick = true
                rightDownAt = ev.eventTime
            }
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> if (rightClick) return true
        }
        if (!rightClick) return super.dispatchTouchEvent(ev)
        val touch = asTouch(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            rightClick = false
            val wait = rightDownAt + ViewConfiguration.getLongPressTimeout() + 120 - SystemClock.uptimeMillis()
            if (ev.actionMasked == MotionEvent.ACTION_UP && wait > 0) {
                mouseHandler.postDelayed({
                    super.dispatchTouchEvent(touch)
                    touch.recycle()
                }, wait)
                return true
            }
        }
        val handled = super.dispatchTouchEvent(touch)
        touch.recycle()
        return handled
    }

    /** The same event as a finger on the screen (Compose and widgets treat only the main button as a press). */
    private fun asTouch(ev: MotionEvent): MotionEvent {
        val n = ev.pointerCount
        val props = Array(n) { i -> MotionEvent.PointerProperties().also { ev.getPointerProperties(i, it); it.toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(n) { i -> MotionEvent.PointerCoords().also { ev.getPointerCoords(i, it) } }
        return MotionEvent.obtain(
            ev.downTime, ev.eventTime, ev.action, n, props, coords, ev.metaState,
            if (ev.actionMasked == MotionEvent.ACTION_UP) 0 else MotionEvent.BUTTON_PRIMARY,
            ev.xPrecision, ev.yPrecision, ev.deviceId, ev.edgeFlags, InputDevice.SOURCE_TOUCHSCREEN, ev.flags,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeTick++
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Diag.configChanges++
        Diag.lastConfigAt = SystemClock.uptimeMillis()
        Diag.log("Config change: ${newConfig.screenWidthDp}×${newConfig.screenHeightDp}dp")
    }
}
