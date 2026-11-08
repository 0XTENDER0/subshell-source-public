package io.github.brealorg.subshell.ui

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect

// ISSUE23_DRAWER_DISMISS_OWNERSHIP_V1
/**
 * Whether the navigation drawer may be operated by gestures.
 *
 * Material 3 ties three things to `gesturesEnabled`: the drag gesture, the
 * scrim tap, and nothing else (Back lives in `ModalDrawerSheet(drawerState)`).
 * ISSUE16 gave configurable row swipes ownership of horizontal drag, which
 * must only remove the drawer's drag-to-open: while the drawer is open the
 * content sits under the scrim and cannot be swiped, so scrim tap and
 * drag-to-close keep working. Contextual routes never expose the drawer.
 */
internal fun drawerGesturesEnabled(
    inContext: Boolean,
    swipeActionsEnabled: Boolean,
    drawerOpen: Boolean,
): Boolean = !inContext && (!swipeActionsEnabled || drawerOpen)

/**
 * DRAWER_SETTLED_V1 (fork): `drawerState.isOpen` turns true while the opening animation is still
 * running, which flipped `gesturesEnabled` mid-animation and made the drawer open, close and
 * open again. This is true only once the drawer has come to rest open, and false only once it
 * has come to rest closed.
 */
@Composable
internal fun rememberDrawerSettledOpen(drawerState: DrawerState): Boolean {
    var settledOpen by remember { mutableStateOf(false) }
    LaunchedEffect(drawerState) {
        snapshotFlow {
            Triple(drawerState.currentValue, drawerState.targetValue, drawerState.isAnimationRunning)
        }.collect { (current, target, running) ->
            if (!running && current == target) settledOpen = current == DrawerValue.Open
        }
    }
    return settledOpen
}
