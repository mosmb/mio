package io.morgan.mio.feature.today.impl.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Observe the entire card, including child controls, without consuming their gestures. */
@Composable
internal fun Modifier.observeTodayPointer(onChanged: (Boolean) -> Unit): Modifier {
    val callback = rememberUpdatedState(onChanged)
    return pointerInput(Unit) {
        try {
            awaitPointerEventScope {
                var pressed = false
                while (true) {
                    val next = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                    if (next != pressed) {
                        pressed = next
                        callback.value(next)
                    }
                }
            }
        } finally {
            callback.value(false)
        }
    }
}
