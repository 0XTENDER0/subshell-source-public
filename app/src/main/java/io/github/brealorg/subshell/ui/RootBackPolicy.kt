package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import io.github.brealorg.subshell.data.account.RedditSubscriptionsUiState
import io.github.brealorg.subshell.ui.error.ErrorPresentation
import io.github.brealorg.subshell.ui.error.ErrorPresentationKind

// ROOTBACK01_DEFAULT_ROOT_BACK_V1: at the root of the route stack, Back returns
// Home to the landing feed source before it reaches the system. The other
// tabs need nothing here: navigate() keeps Main(HOME) beneath them, so the
// route stack already pops them to Home. A custom feed at Home is left first
// by its own handler (ISSUE25_CUSTOM_FEED_BACK_V1).

/**
 * ISSUE26_BACK_TWICE_TO_EXIT_V1: with the option on, a second Back within
 * this window after the first one at the root leaves the app. The prompt is
 * shown in the viewport tray in its neutral notice tone (#222).
 */
internal const val BackTwiceToExitWindowMillis = 2_000L

internal const val BackTwiceToExitNoticeScope = "back-twice-to-exit"

internal val BackTwiceToExitNotice = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = UiText.Resource(R.string.backpol_press_back_again_to_exit),
    message = UiText.Resource(R.string.backpol_back_once_more_leaves_subshell),
)

/** The feed source Back should select at Home, or null to let Back through. */
internal fun rootBackFeedSource(
    destination: MainDestination,
    feedSource: FeedSource,
    landingSource: FeedSource,
): FeedSource? =
    landingSource.takeIf {
        destination == MainDestination.HOME && feedSource != landingSource
    }

/**
 * The feed source an account lands on, as the authenticated landing decides
 * it: the chosen start feed when it is Popular or All (ISSUE302_START_FEED_V1,
 * signed in or not); with Home, Popular only for an account with
 * authoritatively zero subscriptions, Home otherwise (anonymous accounts, and
 * while the decision is unknown).
 */
internal fun rootBackLandingSource(
    accountStableId: String,
    authenticated: Boolean,
    subscriptions: RedditSubscriptionsUiState,
    startFeed: StartFeed = StartFeed.HOME,
): FeedSource = when {
    startFeed != StartFeed.HOME -> startFeed.source
    authenticated &&
        resolveAuthenticatedLandingFeed(accountStableId, subscriptions) ==
        AuthenticatedMainFeedLandingDecision.POPULAR -> FeedSource.POPULAR
    else -> FeedSource.HOME
}
