package io.github.brealorg.subshell.data.auth.reddit

import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.ui.s
import io.github.brealorg.subshell.data.account.CommentWriteFailureKind
import io.github.brealorg.subshell.data.account.CommentWritePhase
import io.github.brealorg.subshell.data.account.CommentWriteUiState
import io.github.brealorg.subshell.data.network.reddit.RedditCommentApiError
import io.github.brealorg.subshell.data.network.reddit.RedditCommentApiErrorKind
import io.github.brealorg.subshell.ui.error.commentWriteErrorPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COMPOSER09: Reddit's META_MEDIA_TYPE_NOT_ALLOWED (the community does not
 * allow this media in comments) is its own failure with a plain message,
 * not "Reddit rejected the comment" plus the raw provider code.
 */
// TRANSLATE03: reads string resources through UiText.s, so it needs Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Composer09CommentMediaNotAllowedTest {
    @Test
    fun redditsMediaTypeRefusalIsRecognised() {
        assertTrue(
            redditCommentMediaTypeNotAllowed(
                listOf(RedditCommentApiError(RedditCommentApiErrorKind.UNKNOWN, code = "META_MEDIA_TYPE_NOT_ALLOWED")),
            ),
        )
        assertFalse(
            redditCommentMediaTypeNotAllowed(
                listOf(RedditCommentApiError(RedditCommentApiErrorKind.UNKNOWN, code = "THREAD_LOCKED")),
            ),
        )
    }

    @Test
    fun theNoticeSaysWhatToDoAndOffersNoResend() {
        val presentation = commentWriteErrorPresentation(
            CommentWriteUiState(
                phase = CommentWritePhase.FAILED,
                failureKind = CommentWriteFailureKind.MEDIA_NOT_ALLOWED,
            ),
        )
        assertEquals(
            "This community doesn't allow this kind of image in comments. Remove it to send the text.",
            presentation.message.s,
        )
        assertEquals(null, presentation.primaryAction)
    }
}
