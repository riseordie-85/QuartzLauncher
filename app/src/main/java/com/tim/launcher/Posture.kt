package com.tim.launcher

import android.app.Activity
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import kotlin.math.min
import kotlin.math.roundToInt

enum class Posture(val label: String, val split: Boolean) {
    PHONE("Phone", false),
    COVER("Cover screen", false),
    UNFOLDED("Unfolded", true),
    TABLET("Tablet", true),
}

data class ScreenState(
    val posture: Posture,
    val widthDp: Int,
    val heightDp: Int,
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val displayId: Int,
    val foldable: Boolean,
    val hingeSensor: Boolean,
    val fold: FoldingFeature?,
    /** Center of a vertical hinge in window pixels, or null to split down the middle. */
    val hingeCenterPx: Int?,
) {
    fun foldDescription(): String {
        val f = fold ?: return "none reported"
        val b = f.bounds
        return "${f.state}, ${f.orientation}, separating=${f.isSeparating}, " +
            "occlusion=${f.occlusionType}, bounds=[${b.left},${b.top} – ${b.right},${b.bottom}]"
    }
}

@Composable
fun rememberScreenState(activity: Activity): ScreenState {
    val config = LocalConfiguration.current
    val density = LocalDensity.current.density
    val prefs = remember { Prefs.get(activity) }

    val layoutFlow = remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
    }
    val layoutInfo by layoutFlow.collectAsState(initial = null)
    val fold = layoutInfo?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()

    val hingeSensor = remember {
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
    }
    var foldable by remember { mutableStateOf(prefs.getBoolean(Prefs.FOLDABLE, false) || hingeSensor) }
    LaunchedEffect(fold, hingeSensor) {
        if ((fold != null || hingeSensor) && !prefs.getBoolean(Prefs.FOLDABLE, false)) {
            prefs.edit().putBoolean(Prefs.FOLDABLE, true).apply()
        }
        if (fold != null) foldable = true
    }

    val bounds = remember(config.screenWidthDp, config.screenHeightDp, config.orientation, config.densityDpi) {
        WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
    }
    val widthDp = (bounds.width() / density).roundToInt()
    val heightDp = (bounds.height() / density).roundToInt()

    // Samsung reports the open hinge a moment before the window grows to the inner screen size, so a fold
    // report alone doesn't mean "unfolded" until the window is actually wide enough.
    val posture = when {
        foldable && min(widthDp, heightDp) >= 600 -> Posture.UNFOLDED
        fold != null && widthDp >= 600 -> Posture.UNFOLDED
        foldable -> Posture.COVER
        min(widthDp, heightDp) >= 600 -> Posture.TABLET
        else -> Posture.PHONE
    }

    val hingeCenter = fold
        ?.takeIf { it.orientation == FoldingFeature.Orientation.VERTICAL }
        ?.let { (it.bounds.left + it.bounds.right) / 2 }

    val displayId = runCatching { activity.display?.displayId }.getOrNull() ?: -1

    return ScreenState(
        posture = posture,
        widthDp = widthDp,
        heightDp = heightDp,
        widthPx = bounds.width(),
        heightPx = bounds.height(),
        density = density,
        displayId = displayId,
        foldable = foldable,
        hingeSensor = hingeSensor,
        fold = fold,
        hingeCenterPx = hingeCenter,
    )
}
