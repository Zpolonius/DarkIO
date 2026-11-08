package app.darkio.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Kalder [onSwipe] med den samlede lodrette bevægelse i px, når fingeren slipper. */
@Composable
fun Modifier.onVerticalSwipe(onSwipe: (totalPx: Float) -> Unit): Modifier {
    val latest by rememberUpdatedState(onSwipe)
    return pointerInput(Unit) {
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onVerticalDrag = { change, amount -> change.consume(); total += amount },
            onDragEnd = { latest(total) },
        )
    }
}
