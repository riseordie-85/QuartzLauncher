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

class HomeActivity : ComponentActivity() {

    /** Bumped when Home is pressed while Quartz is already showing, to close the drawer and panels. */
    private var homeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diag.activityCreates++
        val displayId = runCatching { display?.displayId }.getOrNull() ?: -1
        Diag.log("Home created (#${Diag.activityCreates}) on display $displayId")

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeScreen(activity = this, homeTick = homeTick)
            }
        }

        // First launch: run setup once
        if (savedInstanceState == null && !wizardShown && !SettingsStore.get(this)[S.wizardDone]) {
            wizardShown = true
            SettingsActivity.open(this, wizard = true)
        }
    }

    companion object {
        /** Only offer setup once per launch of the app, even if the home screen is recreated. */
        private var wizardShown = false
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
