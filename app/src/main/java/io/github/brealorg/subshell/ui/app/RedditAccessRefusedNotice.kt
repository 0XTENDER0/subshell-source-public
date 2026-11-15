package io.github.brealorg.subshell.ui.app

import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brealorg.subshell.data.account.RedditAccessRefusal
import io.github.brealorg.subshell.ui.error.ErrorAction
import io.github.brealorg.subshell.ui.error.ErrorActionKind
import io.github.brealorg.subshell.ui.error.ErrorPresentation
import io.github.brealorg.subshell.ui.error.ErrorPresentationKind
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import kotlinx.coroutines.launch

// ISSUE32_REDDIT_REFUSED_ACCESS_IS_ONE_NOTICE_V1 (#32): while Reddit refuses
// the app's Client ID, the tray says so once (every screen's own "couldn't
// refresh" is left out, see ViewportErrorHostState.present) and the stored
// feeds, threads and inbox stay readable. Try again lets one request through
// to the token endpoint; the notice leaves by itself when Reddit lets the app
// in again or the reader signs in with another Client ID.

internal const val REDDIT_ACCESS_REFUSED_SCOPE = "reddit-access-refused"

internal fun redditAccessRefusedPresentation(): ErrorPresentation = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = UiText.Resource(R.string.notice_redditaccess_reddit_has_shut_out_this_app_s),
    message = UiText.Resource(R.string.notice_redditaccess_reddit_refuses_the_client_id_subshell_signs),
    primaryAction = ErrorAction(ErrorActionKind.RETRY, UiText.Resource(R.string.notice_redditaccess_try_again)),
)

@Composable
internal fun RedditAccessRefusedNotice(
    noticeHost: ViewportErrorHostState,
    checkAccess: (suspend () -> Unit)?,
) {
    val refusal by RedditAccessRefusal.state.collectAsStateWithLifecycle()
    val check by rememberUpdatedState(checkAccess)
    val scope = rememberCoroutineScope()
    val refused = refusal != null
    LaunchedEffect(refused) {
        if (!refused) {
            noticeHost.clear(REDDIT_ACCESS_REFUSED_SCOPE)
            return@LaunchedEffect
        }
        noticeHost.present(
            scopeKey = REDDIT_ACCESS_REFUSED_SCOPE,
            presentation = redditAccessRefusedPresentation(),
            failureDetails = null,
            onRetry = {
                RedditAccessRefusal.allowCheck()
                check?.let { read -> scope.launch { read() } }
            },
        )
    }
}
