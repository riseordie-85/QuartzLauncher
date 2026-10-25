package com.tim.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A mouse's right click: runs [action] the moment the button goes down (the same menu holding a finger opens),
 * and takes the whole click so nothing else treats it as a tap or a hold.
 *
 * Things on top (icons, widgets) look first ([PointerEventPass.Initial]); the home screen behind them passes
 * [behind] = true and only answers clicks nothing on top took.
 */
fun Modifier.onRightClick(behind: Boolean = false, action: () -> Unit): Modifier = composed {
    val current by rememberUpdatedState(action)
    val pass = if (behind) PointerEventPass.Final else PointerEventPass.Initial
    pointerInput(behind) {
        awaitEachGesture {
            val event = awaitPointerEvent(pass)
            val press = event.type == PointerEventType.Press && event.buttons.isSecondaryPressed
            if (!press || (behind && event.changes.any { it.isConsumed })) return@awaitEachGesture
            event.changes.forEach { it.consume() }
            current()
            // Keep the rest of the click (moves, the release) from reaching anything else
            do {
                val next = awaitPointerEvent(pass)
                next.changes.forEach { it.consume() }
            } while (next.changes.any { it.pressed })
        }
    }
}
