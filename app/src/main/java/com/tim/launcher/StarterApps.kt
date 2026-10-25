package com.tim.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony

/**
 * The apps a brand new home screen starts with: the ones you use most on any phone (calling, messages, the web,
 * the camera, email) in the dock, and a handful of useful ones on the home page. Each is whichever app you've set
 * as the default for that job, or the first one that can do it.
 */
object StarterApps {

    /** The key Quartz Settings has on the home screen and in the drawer (it isn't a separate app). */
    const val QUARTZ_SETTINGS = "quartz:settings"

    data class Picks(val dock: List<String>, val home: List<String>)

    fun pick(context: Context, apps: Map<String, AppEntry>, dockSize: Int, homeSize: Int): Picks {
        val pm = context.packageManager
        val byPackage = apps.values.groupBy { it.component.packageName }

        fun handler(intent: Intent): String? {
            val default = runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) }.getOrNull()
                ?.activityInfo?.packageName
            // No default set gives the chooser ("android", or the phone maker's own): take the first app that can
            // and has an icon to show
            default?.let { byPackage[it]?.firstOrNull()?.key }?.let { return it }
            return runCatching { pm.queryIntentActivities(intent, 0) }.getOrNull().orEmpty()
                .firstNotNullOfOrNull { info -> byPackage[info.activityInfo.packageName]?.firstOrNull()?.key }
        }

        fun category(cat: String) = handler(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, cat))
        fun packageKey(vararg pkgs: String) = pkgs.firstNotNullOfOrNull { byPackage[it]?.firstOrNull()?.key }

        val phone = handler(Intent(Intent.ACTION_DIAL))
        val messages = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let { packageKey(it) }
            ?: category(Intent.CATEGORY_APP_MESSAGING)
        val browser = handler(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
        val camera = handler(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        val email = category(Intent.CATEGORY_APP_EMAIL)
        val gallery = category(Intent.CATEGORY_APP_GALLERY)
        val music = category(Intent.CATEGORY_APP_MUSIC)
        val maps = category(Intent.CATEGORY_APP_MAPS)
        val calendar = category(Intent.CATEGORY_APP_CALENDAR)
        val clock = handler(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        val files = category(Intent.CATEGORY_APP_FILES)
        val store = packageKey("com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia")
        val settings = handler(Intent(Settings.ACTION_SETTINGS))

        // A tablet usually has no phone app: the next ones along fill its place
        val dock = listOfNotNull(phone, messages, browser, camera, email, gallery, music, maps).distinct().take(dockSize)
        val home = (listOf(QUARTZ_SETTINGS) + listOfNotNull(store, gallery, calendar, clock, files, settings, music, maps))
            .distinct()
            .filter { it !in dock && (it == QUARTZ_SETTINGS || it in apps) }
            .take(homeSize)
        return Picks(dock, home)
    }
}
