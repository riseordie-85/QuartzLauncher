package com.tim.launcher.settings

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tim.launcher.ui.Q
import com.tim.launcher.ui.QuartzTheme
import com.tim.launcher.wizard.SetupWizard

enum class Route { MAIN, HOME, DOCK, DRAWER, APPEARANCE, GESTURES, PRIVACY, ABOUT, WIZARD }

/** Quartz settings and the setup wizard, shown as a frosted sheet over the wallpaper. */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Real frosted glass where the device supports cross-window blur; a dark tint otherwise
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius((48 * resources.displayMetrics.density).toInt())
        }

        val start = if (intent.getBooleanExtra(EXTRA_WIZARD, false)) Route.WIZARD else Route.MAIN
        val store = SettingsStore.get(this)
        val type = detectDeviceType(this)

        setContent {
            QuartzTheme {
                var stack by remember { mutableStateOf(listOf(start)) }
                var editing by remember { mutableStateOf(type.primary) }
                fun go(route: Route) {
                    stack = stack + route
                }
                fun back() {
                    if (stack.size > 1) stack = stack.dropLast(1) else finish()
                }
                BackHandler { back() }

                Box(Modifier.fillMaxSize().background(Q.Scrim)) {
                    AnimatedContent(
                        targetState = stack,
                        transitionSpec = {
                            val forward = targetState.size >= initialState.size
                            (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
                        },
                        label = "settings",
                    ) { s ->
                        val ctx = SettingsContext(store, type, editing, onEditing = { editing = it }, go = { go(it) }, back = { back() })
                        when (s.last()) {
                            Route.MAIN -> MainSettings(ctx)
                            Route.HOME -> HomeSettings(ctx)
                            Route.DOCK -> DockSettings(ctx)
                            Route.DRAWER -> DrawerSettings(ctx)
                            Route.APPEARANCE -> AppearanceSettings(ctx)
                            Route.GESTURES -> GestureSettings(ctx)
                            Route.PRIVACY -> PrivacySettings(ctx)
                            Route.ABOUT -> AboutSettings(ctx)
                            Route.WIZARD -> SetupWizard(store, type, onFinish = {
                                if (stack.size > 1) stack = stack.dropLast(1) else finish()
                            })
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_WIZARD = "wizard"

        fun open(context: Context, wizard: Boolean = false) {
            context.startActivity(
                Intent(context, SettingsActivity::class.java)
                    .putExtra(EXTRA_WIZARD, wizard)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** What every settings screen needs: the store, the device, which screen is being edited, and navigation. */
class SettingsContext(
    val store: SettingsStore,
    val type: DeviceType,
    val editing: Scope,
    val onEditing: (Scope) -> Unit,
    val go: (Route) -> Unit,
    val back: () -> Unit,
)
