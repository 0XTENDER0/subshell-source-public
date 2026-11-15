package io.github.brealorg.subshell.ui.app

import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.brealorg.subshell.data.account.CustomFeedsUiState
import io.github.brealorg.subshell.shortcuts.LauncherShortcutResolution
import io.github.brealorg.subshell.shortcuts.LauncherShortcutTarget
import io.github.brealorg.subshell.shortcuts.resolveLauncherShortcutTarget
import io.github.brealorg.subshell.ui.MainDestination
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.error.ErrorPresentation
import io.github.brealorg.subshell.ui.error.ErrorPresentationKind
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState

internal const val LauncherShortcutNoticeScope = "launcher-shortcut"

/**
 * ISSUE37_LAUNCHER_SHORTCUT_TARGETS_V1: opens a custom-feed or Inbox
 * launcher shortcut for the account it belongs to, the way the drawer and
 * inbox notifications open them (a chosen destination also ends a pending
 * authenticated landing). With another account active it only explains
 * which account the shortcut needs; it never switches account. A feed waits
 * for that account's feed list and is dropped with a notice if the list
 * fails or no longer has it.
 */
@Composable
internal fun LauncherShortcutTargetEffect(
    state: SubshellState,
    feedTarget: LauncherShortcutTarget.CustomFeed?,
    onFeedTargetConsumed: () -> Unit,
    inboxTarget: LauncherShortcutTarget.Inbox?,
    onInboxTargetConsumed: () -> Unit,
    customFeedsState: CustomFeedsUiState,
    onLoadCustomFeeds: (String, Boolean) -> Unit,
    activeCustomFeedPathState: MutableState<String?>,
    pendingAuthenticatedLandingAccountIdState: MutableState<String?>,
    noticeHost: ViewportErrorHostState,
) {
    var activeCustomFeedPath by activeCustomFeedPathState
    var pendingAuthenticatedLandingAccountId by pendingAuthenticatedLandingAccountIdState
    val target: LauncherShortcutTarget? = feedTarget ?: inboxTarget
    LaunchedEffect(
        target?.requestId,
        state.activeAccountId,
        customFeedsState.accountStableId,
        customFeedsState.loading,
        customFeedsState.contentRevision,
        customFeedsState.failureKind,
    ) {
        target ?: return@LaunchedEffect
        val consume = if (target is LauncherShortcutTarget.CustomFeed) {
            onFeedTargetConsumed
        } else {
            onInboxTargetConsumed
        }
        val resolution = resolveLauncherShortcutTarget(
            target = target,
            activeAccountId = state.activeAccountId,
            signedInAccountIds = state.accountSessions.map { session -> session.stableId },
            customFeeds = customFeedsState,
        )
        when (resolution) {
            LauncherShortcutResolution.LoadFeeds -> {
                onLoadCustomFeeds(target.accountStableId, false)
                return@LaunchedEffect
            }
            is LauncherShortcutResolution.OpenFeed -> {
                pendingAuthenticatedLandingAccountId = null
                activeCustomFeedPath = resolution.path
                state.navigate(MainDestination.HOME)
            }
            LauncherShortcutResolution.OpenInbox -> {
                pendingAuthenticatedLandingAccountId = null
                state.openInboxNotificationRoot()
            }
            is LauncherShortcutResolution.OtherAccount -> {
                val username = state.accountSessions
                    .firstOrNull { session -> session.stableId == resolution.accountStableId }
                    ?.username
                presentNotice(
                    noticeHost,
                    title = username?.let { name -> UiText.Resource(R.string.notice_launchershor_switch_to_u, name) } ?: UiText.Resource(R.string.notice_launchershor_switch_account),
                    message = UiText.Resource(R.string.notice_launchershor_this_shortcut_belongs_to_another_account),
                )
            }
            LauncherShortcutResolution.AccountGone -> presentNotice(
                noticeHost,
                title = UiText.Resource(R.string.notice_launchershor_account_not_on_this_device),
                message = UiText.Resource(R.string.notice_launchershor_this_shortcut_belongs_to_an_account_that),
            )
            LauncherShortcutResolution.FeedsFailed -> presentNotice(
                noticeHost,
                title = UiText.Resource(R.string.notice_launchershor_couldn_t_open_the_custom_feed),
                message = UiText.Resource(R.string.notice_launchershor_your_custom_feeds_did_not_load),
            )
            LauncherShortcutResolution.FeedMissing -> presentNotice(
                noticeHost,
                title = UiText.Resource(R.string.notice_launchershor_custom_feed_not_found),
                message = UiText.Resource(R.string.notice_launchershor_it_may_have_been_deleted_or_renamed),
            )
        }
        consume()
    }
}

private fun presentNotice(
    noticeHost: ViewportErrorHostState,
    title: UiText,
    message: UiText,
) {
    // clear() first forgets an earlier dismissal of the same notice.
    noticeHost.clear(LauncherShortcutNoticeScope)
    noticeHost.present(
        scopeKey = LauncherShortcutNoticeScope,
        presentation = ErrorPresentation(
            kind = ErrorPresentationKind.DEGRADED,
            title = title,
            message = message,
        ),
        failureDetails = null,
        onRetry = null,
    )
}
