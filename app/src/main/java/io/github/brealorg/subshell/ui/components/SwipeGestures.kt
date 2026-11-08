package io.github.brealorg.subshell.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * How forgiving the swipes are. [SLOP_FACTOR] scales the system touch slop (the dead zone before
 * a drag counts): 0.6 starts a drag after a few millimetres. [DIRECTION_RATIO] is how much more
 * the finger must travel along the swipe axis than across it, 1.0 meaning anything closer to the
 * swipe axis than 45 degrees counts. Distance and speed are either/or: a short quick flick and a
 * long slow drag both finish the swipe.
 */
private const val SLOP_FACTOR = 0.6f
private const val DIRECTION_RATIO = 1.0f
private const val BACK_DISTANCE_FRACTION = 0.22f
private const val BACK_FLING_PX_PER_S = 500f
private const val MEDIA_DISTANCE_FRACTION = 0.12f
private const val MEDIA_FLING_PX_PER_S = 450f

private class DragEnd(val distance: Float, val velocity: Float, val finished: Boolean)

/**
 * Watches one finger before the children see it (the Initial pass), so a list that is about to
 * scroll, a pager or a swipe row cannot take a clear swipe first. Until the finger has clearly
 * moved along [horizontal] in direction [sign], nothing is consumed and taps and scrolls behave
 * as usual. Once it has, the moves are consumed and [onDrag] gets the distance travelled.
 * Returns null when the gesture was not ours.
 */
private suspend fun AwaitPointerEventScope.awaitSwipe(
    horizontal: Boolean,
    sign: Int,
    slop: Float,
    startAllowed: (Offset) -> Boolean,
    allowed: () -> Boolean,
    onDrag: (Float) -> Unit,
): DragEnd? {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    if (!startAllowed(down.position) || !allowed()) return null
    val tracker = VelocityTracker()
    tracker.addPosition(down.uptimeMillis, down.position)
    var started = false
    var distance = 0f
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: return null
        // A second finger means pinch or pan: not a swipe.
        if (event.changes.size > 1) return if (started) DragEnd(distance, 0f, false) else null
        if (change.changedToUpIgnoreConsumed()) {
            if (!started) return null
            change.consume()
            val v = tracker.calculateVelocity()
            return DragEnd(distance, (if (horizontal) v.x else v.y) * sign, true)
        }
        if (!change.pressed) return if (started) DragEnd(distance, 0f, false) else null
        tracker.addPosition(change.uptimeMillis, change.position)
        val total = change.position - down.position
        val along = (if (horizontal) total.x else total.y) * sign
        val across = if (horizontal) total.y else total.x
        if (!started) {
            if (change.isConsumed) return null
            // Clearly going the other way or across: leave it to the content.
            if (along < -slop && -along > abs(across)) return null
            if (abs(across) > slop && abs(across) > along * DIRECTION_RATIO && abs(across) > abs(along)) return null
            if (along > slop && along > abs(across) * DIRECTION_RATIO) {
                started = true
            } else {
                continue
            }
        }
        distance = along.coerceAtLeast(0f)
        onDrag(distance)
        change.consume()
    }
}

/**
 * SWIPEBACK_V1 (fork): a left-to-right drag goes back, like the system Back. It is forgiving:
 * a small dead zone, about 45 degrees of tolerance, and a short flick is enough. With
 * [startZoneFraction] below 1 the swipe only starts in that left share of the width, so rows
 * with their own right-swipe action keep the rest of the screen.
 */
@Composable
internal fun Modifier.swipeRightToGoBack(
    enabled: Boolean,
    startZoneFraction: Float = 1f,
    onBack: () -> Unit,
): Modifier {
    if (!enabled) return this
    val currentOnBack by rememberUpdatedState(onBack)
    var offsetX by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val slop = LocalViewConfiguration.current.touchSlop * SLOP_FACTOR
    // pointerInput sits outside graphicsLayer so the pointer coordinates do not move with the
    // content that is being dragged.
    return this
        .pointerInput(slop, startZoneFraction) {
            val width = size.width.toFloat().coerceAtLeast(1f)
            awaitEachGesture {
                val end = awaitSwipe(
                    horizontal = true,
                    sign = 1,
                    slop = slop,
                    startAllowed = { start -> start.x <= width * startZoneFraction },
                    allowed = { true },
                    onDrag = { offsetX = it },
                ) ?: return@awaitEachGesture
                val commit = end.finished &&
                    (end.distance > width * BACK_DISTANCE_FRACTION || end.velocity > BACK_FLING_PX_PER_S)
                val from = end.distance
                scope.launch {
                    if (commit) {
                        animate(from, width, animationSpec = tween(110)) { v, _ -> offsetX = v }
                        currentOnBack()
                        offsetX = 0f
                    } else {
                        animate(from, 0f, animationSpec = tween(150)) { v, _ -> offsetX = v }
                    }
                }
            }
        }
        .graphicsLayer { translationX = offsetX }
}

/**
 * SWIPEUP_MEDIA_V1 (fork): an upward drag closes a full screen picture or video. The media
 * follows the finger and fades. [allowed] lets the caller step aside (zoomed in); with
 * [portraitOnly] a landscape screen is left to its own vertical gestures.
 */
@Composable
internal fun Modifier.swipeUpToClose(
    enabled: Boolean,
    portraitOnly: Boolean = false,
    allowed: () -> Boolean = { true },
    onClose: () -> Unit,
): Modifier {
    if (!enabled) return this
    val currentOnClose by rememberUpdatedState(onClose)
    val currentAllowed by rememberUpdatedState(allowed)
    var offsetY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val slop = LocalViewConfiguration.current.touchSlop * SLOP_FACTOR
    return this
        .pointerInput(slop, portraitOnly) {
            val height = size.height.toFloat().coerceAtLeast(1f)
            awaitEachGesture {
                val end = awaitSwipe(
                    horizontal = false,
                    sign = -1,
                    slop = slop,
                    startAllowed = { true },
                    allowed = { (!portraitOnly || size.width <= size.height) && currentAllowed() },
                    onDrag = { offsetY = -it },
                ) ?: return@awaitEachGesture
                val commit = end.finished &&
                    (end.distance > height * MEDIA_DISTANCE_FRACTION || end.velocity > MEDIA_FLING_PX_PER_S)
                val from = -end.distance
                scope.launch {
                    if (commit) {
                        animate(from, -height, animationSpec = tween(130)) { v, _ -> offsetY = v }
                        currentOnClose()
                        offsetY = 0f
                    } else {
                        animate(from, 0f, animationSpec = tween(150)) { v, _ -> offsetY = v }
                    }
                }
            }
        }
        .graphicsLayer {
            translationY = offsetY
            alpha = 1f - (abs(offsetY) / size.height.coerceAtLeast(1f)).coerceIn(0f, 0.5f)
        }
}
