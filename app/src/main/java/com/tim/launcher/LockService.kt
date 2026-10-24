package com.tim.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * "Quartz Lock": an accessibility service whose only job is locking the screen for the lock gesture. It asks for
 * no events and can't read the screen. Android requires it to be turned on by hand in Accessibility settings.
 */
class LockService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        Diag.log("Quartz Lock is on")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: LockService? = null

        val isOn: Boolean get() = instance != null

        /** Locks the screen. False if Quartz Lock isn't turned on. */
        fun lock(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false

        fun openSettings(context: Context) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
