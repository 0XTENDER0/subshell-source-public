package io.github.brealorg.subshell.ui.app

import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.brealorg.subshell.ui.error.ErrorPresentation
import io.github.brealorg.subshell.ui.error.ErrorPresentationKind
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState

// ISSUE41_UNSUPPORTED_REDDIT_LINK_NOTICE_V1
// A Reddit link that Subshell cannot open used to do nothing at all. The
// deep-link effect now reports it, and this notice says so in the tray.
// Subshell does not send Reddit links out of the app, so there is no
// browser fallback.
internal enum class RedditLinkUnavailableReason {
    /** Subshell does not know this kind of reddit.com link. */
    UNSUPPORTED_LINK,

    /** The link names a post that could not be loaded. */
    POST_UNAVAILABLE,

    /** Reddit did not say where a share link leads. */
    SHARE_LINK_UNAVAILABLE,
}

internal data class RedditLinkUnavailable(
    val requestId: Long,
    val rawUri: String,
    val reason: RedditLinkUnavailableReason,
)

internal const val RedditLinkNoticeScope = "reddit-link"

internal fun redditLinkUnavailablePresentation(
    reason: RedditLinkUnavailableReason,
): ErrorPresentation = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = when (reason) {
        RedditLinkUnavailableReason.UNSUPPORTED_LINK -> UiText.Resource(R.string.app_link_unsupported)
        RedditLinkUnavailableReason.POST_UNAVAILABLE -> UiText.Resource(R.string.app_link_post_unavailable)
        RedditLinkUnavailableReason.SHARE_LINK_UNAVAILABLE -> UiText.Resource(R.string.app_link_share_unavailable)
    },
    message = when (reason) {
        RedditLinkUnavailableReason.UNSUPPORTED_LINK ->
            UiText.Resource(R.string.app_link_unsupported_detail)
        RedditLinkUnavailableReason.POST_UNAVAILABLE ->
            UiText.Resource(R.string.app_link_post_unavailable_detail)
        RedditLinkUnavailableReason.SHARE_LINK_UNAVAILABLE ->
            UiText.Resource(R.string.app_link_share_unavailable_detail)
    },
)

@Composable
internal fun RedditLinkUnavailableNoticeEffect(
    unavailable: RedditLinkUnavailable?,
    onShown: () -> Unit,
    noticeHost: ViewportErrorHostState,
) {
    LaunchedEffect(unavailable?.requestId) {
        val current = unavailable ?: return@LaunchedEffect
        noticeHost.presentRedditLinkUnavailable(current.reason)
        onShown()
    }
}

internal fun ViewportErrorHostState.presentRedditLinkUnavailable(reason: RedditLinkUnavailableReason) {
    // clear() first forgets an earlier dismissal of the same notice.
    clear(RedditLinkNoticeScope)
    present(
        scopeKey = RedditLinkNoticeScope,
        presentation = redditLinkUnavailablePresentation(reason),
        failureDetails = null,
        onRetry = null,
    )
}
