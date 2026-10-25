package com.tim.launcher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.settings.BadgeStyle
import com.tim.launcher.settings.S
import com.tim.launcher.settings.SettingsStore

/**
 * How many notifications each app has, for badges. Quartz only counts them per app: it never reads, keeps or sends
 * what they say. Needs notification access, which you turn on yourself (Settings › Appearance › Badges).
 */
object Badges {
    /** Package name → number of notifications. */
    val counts = mutableStateMapOf<String, Int>()

    private val main = Handler(Looper.getMainLooper())

    fun update(next: Map<String, Int>) {
        main.post {
            counts.keys.retainAll(next.keys)
            next.forEach { (k, v) -> if (counts[k] != v) counts[k] = v }
        }
    }

    fun isAllowed(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, BadgeService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** Android's page for turning notification access on or off. */
    fun openAccessSettings(context: Context) {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, BadgeService::class.java).flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(detail) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Badge color: calm below the first step, amber from it, red from the second. */
    fun color(count: Int, step1: Int, step2: Int): Color = when {
        count >= step2 -> Color(0xFFE5534B)
        count >= step1 -> Color(0xFFF2A93B)
        else -> Color(0xFF8C6CFF)
    }
}

/** Counts notifications per app as they come and go. Nothing else. */
class BadgeService : NotificationListenerService() {
    override fun onListenerConnected() = recount()
    override fun onListenerDisconnected() = Badges.update(emptyMap())
    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val counts = HashMap<String, Int>()
        active.forEach { sbn ->
            val n = sbn.notification
            // Ongoing ones (music, downloads) and group summaries aren't things waiting for you
            if (sbn.isOngoing || (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return@forEach
            counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + n.number.coerceAtLeast(1)
        }
        Badges.update(counts)
    }
}

/**
 * The badge on an icon: a dot, or a count that changes color at your two steps. [packages] are the apps it counts
 * (one for an app, several for a folder). Draws nothing when badges are off or there's nothing new.
 */
@Composable
fun BadgeOverlay(store: SettingsStore, packages: List<String>, iconSize: Dp, modifier: Modifier = Modifier) {
    if (!store[S.badgesOn]) return
    val count = packages.sumOf { Badges.counts[it] ?: 0 }
    if (count <= 0) return
    val color = Badges.color(count, store[S.badgeThreshold1], store[S.badgeThreshold2])
    if (store[S.badgeStyle] == BadgeStyle.DOT) {
        Box(
            modifier
                .size(iconSize * 0.24f)
                .clip(CircleShape)
                .background(color)
                .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
        )
    } else {
        Box(
            modifier
                .clip(CircleShape)
                .background(color)
                .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                .widthIn(min = iconSize * 0.34f)
                .padding(horizontal = 5.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (count > 99) "99+" else "$count",
                color = Color.White,
                fontSize = (iconSize.value * 0.2f).coerceIn(9f, 13f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
