package io.github.brealorg.subshell.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * SWIPEBACK_V1 (fork): a left-to-right drag on the content goes back, like the system Back.
 * It runs after the children, so a horizontal scroller, a pager or a swipe action that wants
 * the drag keeps it; only an unclaimed, mostly horizontal, rightward drag arrives here.
 */
@Composable
internal fun Modifier.swipeRightToGoBack(enabled: Boolean, onBack: () -> Unit): Modifier {
    if (!enabled) return this
    val currentOnBack by rememberUpdatedState(onBack)
    var offsetX by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    // pointerInput sits outside graphicsLayer so the pointer coordinates do not move with the
    // content that is being dragged.
    return this
        .pointerInput(Unit) {
            val width = size.width.toFloat().coerceAtLeast(1f)
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                val start = down.position
                val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    val dx = change.position.x - start.x
                    val dy = abs(change.position.y - start.y)
                    // Only rightward and clearly horizontal drags are ours.
                    if (over > 0f && dx > dy * 1.5f) change.consume()
                } ?: return@awaitEachGesture
                var dragged = 0f
                tracker.addPosition(slop.uptimeMillis, slop.position)
                val finished = horizontalDrag(slop.id) { change ->
                    dragged = (dragged + change.positionChange().x).coerceAtLeast(0f)
                    offsetX = dragged
                    tracker.addPosition(change.uptimeMillis, change.position)
                    change.consume()
                }
                val velocity = tracker.calculateVelocity().x
                val commit = finished && (dragged > width * 0.33f || velocity > 1400f)
                val from = dragged
                scope.launch {
                    if (commit) {
                        animate(from, width, animationSpec = androidx.compose.animation.core.tween(120)) { v, _ -> offsetX = v }
                        currentOnBack()
                        offsetX = 0f
                    } else {
                        animate(from, 0f, animationSpec = androidx.compose.animation.core.tween(150)) { v, _ -> offsetX = v }
                    }
                }
            }
        }
        .graphicsLayer { translationX = offsetX }
}

/**
 * SWIPEDOWN_MEDIA_V1 (fork): a downward drag closes a full screen picture or video. The media
 * follows the finger and the caller's backdrop can fade with [progress]. Runs after the
 * children, so zoomed pan, a pager or the landscape brightness/volume drag win when they
 * claim the drag; [allowed] lets the caller step aside (zoomed in, landscape gestures).
 */
@Composable
internal fun Modifier.swipeDownToClose(
    enabled: Boolean,
    allowed: () -> Boolean = { true },
    onClose: () -> Unit,
): Modifier {
    if (!enabled) return this
    val currentOnClose by rememberUpdatedState(onClose)
    val currentAllowed by rememberUpdatedState(allowed)
    var offsetY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    return this
        .pointerInput(Unit) {
            val height = size.height.toFloat().coerceAtLeast(1f)
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                val start = down.position
                val slop = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                    val dy = change.position.y - start.y
                    val dx = abs(change.position.x - start.x)
                    if (over > 0f && dy > dx * 1.5f && currentAllowed()) change.consume()
                } ?: return@awaitEachGesture
                var dragged = 0f
                val finished = verticalDrag(slop.id) { change ->
                    dragged = (dragged + change.positionChange().y).coerceAtLeast(0f)
                    offsetY = dragged
                    tracker.addPosition(change.uptimeMillis, change.position)
                    change.consume()
                }
                val velocity = tracker.calculateVelocity().y
                val commit = finished && (dragged > height * 0.2f || velocity > 1200f)
                val from = dragged
                scope.launch {
                    if (commit) {
                        animate(from, height, animationSpec = androidx.compose.animation.core.tween(140)) { v, _ -> offsetY = v }
                        currentOnClose()
                        offsetY = 0f
                    } else {
                        animate(from, 0f, animationSpec = androidx.compose.animation.core.tween(150)) { v, _ -> offsetY = v }
                    }
                }
            }
        }
        .graphicsLayer {
            translationY = offsetY
            alpha = 1f - (offsetY / size.height.coerceAtLeast(1f)).coerceIn(0f, 0.5f)
        }
}
