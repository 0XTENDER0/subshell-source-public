package io.github.brealorg.subshell.ui.app

import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.brealorg.subshell.share.ShareIntoSubshellLaunch
import io.github.brealorg.subshell.share.pruneSharedMedia
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.error.ErrorPresentation
import io.github.brealorg.subshell.ui.error.ErrorPresentationKind
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ISSUE33_SHARE_INTO_SUBSHELL_V1
// Opens a share from another app as a prefilled post in the composer, for
// the active account. Posting needs a signed-in Reddit account: without one
// the tray says so instead of making a local-only post. Items that could
// not be used are reported too. Copies of shared images no draft uses any
// more are deleted once per app start.
@Composable
internal fun ShareIntoSubshellEffect(
    state: SubshellState,
    launch: ShareIntoSubshellLaunch?,
    onConsumed: () -> Unit,
    noticeHost: ViewportErrorHostState,
    // LOWUI8_SHARE_WAITS_FOR_THE_POST_BEING_SENT_V1 (OCB-122): a share opened
    // during a send replaced the locked draft under the send; it waits,
    // unconsumed, and opens once the send has finished.
    postWriteInProgress: Boolean = false,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        // COMPOSER16_PASTED_IMAGE_COPIED_V1: pasted images in the comment
        // field are copies in the same folder.
        val referenced = (state.postComposerDrafts.toList() + state.postComposerDraft)
            .flatMap { draft -> draft.media.map { media -> media.uri } }
            .toSet() + listOfNotNull(state.commentComposerDraft?.media?.uri)
        withContext(Dispatchers.IO) { pruneSharedMedia(context, referenced) }
    }
    LaunchedEffect(launch?.requestId, postWriteInProgress) {
        val current = launch ?: return@LaunchedEffect
        if (postWriteInProgress && current.content != null && state.activeAccountIsAuthenticated) {
            return@LaunchedEffect
        }
        val content = current.content
        when {
            content == null -> presentShareNotice(
                noticeHost,
                title = UiText.Resource(R.string.notice_shareintosub_nothing_to_post),
                message = UiText.Resource(R.string.notice_shareintosub_subshell_can_post_links_text_and_images),
            )
            !state.activeAccountIsAuthenticated -> presentShareNotice(
                noticeHost,
                title = UiText.Resource(R.string.notice_shareintosub_sign_in_to_reddit_to_post),
                message = UiText.Resource(R.string.notice_shareintosub_connect_an_account_then_share_again),
            )
            else -> {
                state.openSharedPostComposer(content)
                if (content.skippedCount > 0) {
                    presentShareNotice(
                        noticeHost,
                        title = if (content.skippedCount == 1) UiText.Resource(R.string.notice_shareintosub_1_item_left_out) else UiText.Resource(R.string.notice_shareintosub_items_left_out, content.skippedCount),
                        message = UiText.Resource(R.string.notice_shareintosub_only_jpeg_png_webp_and_gif_images),
                    )
                }
            }
        }
        onConsumed()
    }
}

internal const val ShareIntoSubshellNoticeScope = "share-into-subshell"

private fun presentShareNotice(noticeHost: ViewportErrorHostState, title: UiText, message: UiText) {
    noticeHost.clear(ShareIntoSubshellNoticeScope)
    noticeHost.present(
        scopeKey = ShareIntoSubshellNoticeScope,
        presentation = ErrorPresentation(
            kind = ErrorPresentationKind.DEGRADED,
            title = title,
            message = message,
        ),
        failureDetails = null,
        onRetry = null,
    )
}
