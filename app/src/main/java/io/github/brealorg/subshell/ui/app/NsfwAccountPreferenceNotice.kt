package io.github.brealorg.subshell.ui.app

import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import io.github.brealorg.subshell.data.account.NsfwPreferencesResult
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.error.ErrorAction
import io.github.brealorg.subshell.ui.error.ErrorActionKind
import io.github.brealorg.subshell.ui.error.ErrorPresentation
import io.github.brealorg.subshell.ui.error.ErrorPresentationKind
import io.github.brealorg.subshell.ui.error.TrayTone
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState

// ISSUE211_ACCOUNT_NSFW_PREFERENCE_V1 (#211): with "Show NSFW posts" on, the
// active Reddit account's own over_18 is read once per account (and again
// when the setting is turned on). If the account hides NSFW content, Reddit
// leaves it out of every feed and search whatever Subshell shows, so the tray
// says so and offers Reddit's settings (owner: a link only, Subshell writes
// nothing to the account). The tray's one action button runs onRetry, so the
// link is that action with its own label.

internal const val NSFW_ACCOUNT_PREFERENCE_SCOPE = "nsfw-account-preference"
internal val NSFW_ACCOUNT_PREFERENCE_RETRY_DELAYS_MS = listOf(15_000L, 60_000L, 300_000L)
internal const val REDDIT_PREFERENCES_URL = "https://www.reddit.com/settings/preferences"

internal fun nsfwAccountPreferencePresentation(): ErrorPresentation = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = UiText.Resource(R.string.notice_nsfwaccountp_your_reddit_account_hides_nsfw_content),
    message = UiText.Resource(R.string.notice_nsfwaccountp_reddit_leaves_nsfw_posts_and_communities_out),
    primaryAction = ErrorAction(ErrorActionKind.RETRY, UiText.Resource(R.string.notice_nsfwaccountp_open_reddit_settings)),
    secondaryAction = ErrorAction(ErrorActionKind.DISMISS, UiText.Resource(R.string.notice_nsfwaccountp_dismiss)),
)

/** True when the account's answer means the notice should show. */
internal fun nsfwAccountPreferenceNeedsNotice(result: NsfwPreferencesResult): Boolean =
    result is NsfwPreferencesResult.Known && !result.preferences.over18

// ISSUE263_NSFW_SEARCH_PREFERENCE_NOTICE_V1 (#263, the reader's finding): with
// mature content on but "include mature content in search results" off,
// Reddit leaves NSFW out of every search and of Popular, so NSFW
// communities never show in search and Random NSFW community finds none.
// Reddit's new settings page showed it on while old.reddit.com/prefs had it
// off; the notice links there.
internal const val REDDIT_OLD_PREFERENCES_URL = "https://old.reddit.com/prefs/"

/** True when mature content is on but Reddit says NSFW search results are off. */
internal fun nsfwSearchPreferenceNeedsNotice(result: NsfwPreferencesResult): Boolean =
    result is NsfwPreferencesResult.Known &&
        result.preferences.over18 &&
        result.preferences.searchIncludeOver18 == false

internal fun nsfwSearchPreferencePresentation(): ErrorPresentation = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = UiText.Resource(R.string.notice_nsfwaccountp_your_reddit_account_leaves_nsfw_out_of),
    message = UiText.Resource(R.string.notice_nsfwaccountp_nsfw_communities_won_t_show_in_search),
    primaryAction = ErrorAction(ErrorActionKind.RETRY, UiText.Resource(R.string.notice_nsfwaccountp_open_reddit_preferences)),
    secondaryAction = ErrorAction(ErrorActionKind.DISMISS, UiText.Resource(R.string.notice_nsfwaccountp_dismiss)),
)

@Composable
internal fun NsfwAccountPreferenceNotice(
    state: SubshellState,
    readPreferences: (suspend (String) -> NsfwPreferencesResult)?,
    noticeHost: ViewportErrorHostState,
) {
    val host = noticeHost
    val context = LocalContext.current
    val read by rememberUpdatedState(readPreferences)
    val wanted = state.showNsfwPosts && state.activeAccountIsAuthenticated
    val account = state.activeAccountId
    LaunchedEffect(wanted, account) {
        host.clear(NSFW_ACCOUNT_PREFERENCE_SCOPE)
        val reader = read
        if (!wanted || reader == null) return@LaunchedEffect
        // ISSUE211_ACCOUNT_PREFERENCE_READ_RETRIES_V1: a read that failed (a cold
        // start without network, seen on the owner's phone) is tried again a
        // few times instead of skipping the notice for the whole run.
        var result = reader(account)
        for (pause in NSFW_ACCOUNT_PREFERENCE_RETRY_DELAYS_MS) {
            if (result !is NsfwPreferencesResult.Failed) break
            kotlinx.coroutines.delay(pause)
            result = (read ?: return@LaunchedEffect)(account)
        }
        if (nsfwAccountPreferenceNeedsNotice(result)) {
            host.present(
                scopeKey = NSFW_ACCOUNT_PREFERENCE_SCOPE,
                presentation = nsfwAccountPreferencePresentation(),
                failureDetails = null,
                onRetry = { io.github.brealorg.subshell.ui.components.openRedditPageInBrowser(context, REDDIT_PREFERENCES_URL) },
                tone = TrayTone.INFO, // ISSUE222: information, not an error
            )
        } else if (nsfwSearchPreferenceNeedsNotice(result)) {
            host.present(
                scopeKey = NSFW_ACCOUNT_PREFERENCE_SCOPE,
                presentation = nsfwSearchPreferencePresentation(),
                failureDetails = null,
                onRetry = { io.github.brealorg.subshell.ui.components.openRedditPageInBrowser(context, REDDIT_OLD_PREFERENCES_URL) },
                tone = TrayTone.INFO,
            )
        }
    }
}
