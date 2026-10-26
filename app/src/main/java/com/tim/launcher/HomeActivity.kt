package com.tim.launcher

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
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
import com.tim.launcher.settings.ensureWidgetAccent

class HomeActivity : ComponentActivity() {

    /** Bumped when Home is pressed while Quartz is already showing, to close the drawer and panels. */
    private var homeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diag.activityCreates++
        val displayId = runCatching { display?.displayId }.getOrNull() ?: -1
        Diag.log("Home created (#${Diag.activityCreates}) on display $displayId")

        SettingsStore.get(this).ensureGridDefaults(detectDeviceType(this))
        SettingsStore.get(this).ensureWidgetAccent()

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
