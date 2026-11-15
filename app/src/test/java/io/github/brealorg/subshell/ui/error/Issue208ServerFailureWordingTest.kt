package io.github.brealorg.subshell.ui.error

import io.github.brealorg.subshell.ui.UiText
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.ui.s
import io.github.brealorg.subshell.data.account.CustomFeedPostsFailureKind
import io.github.brealorg.subshell.data.account.RedditCommentUiFailureKind
import io.github.brealorg.subshell.data.account.RedditFeedUiFailureKind
import io.github.brealorg.subshell.data.account.RedditInboxUiFailureKind
import io.github.brealorg.subshell.data.error.FailureDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE208_SERVER_FAILURES_ARE_NOT_REJECTIONS_V1 (#208): a read that fails
 * with HTTP 5xx says Reddit had a server problem, keeps Retry, and a 4xx
 * still reads as a rejection.
 */
// TRANSLATE03: reads string resources through UiText.s, so it needs Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Issue208ServerFailureWordingTest {
    private fun details(status: Int) = FailureDetails(retryable = status >= 500, httpStatusCode = status)

    @Test
    fun aServerErrorIsAServerProblemWithRetry() {
        val feed = redditFeedErrorPresentation(RedditFeedUiFailureKind.HTTP, details(500), true, false)
        assertEquals(REDDIT_READ_SERVER_PROBLEM_REASON.s, feed.message.s)
        assertNotNull(feed.primaryAction)

        val comments = redditCommentsErrorPresentation(RedditCommentUiFailureKind.HTTP, details(503), true, true)
        assertTrue(comments.message.s, comments.message.s.startsWith(REDDIT_READ_SERVER_PROBLEM_REASON.s))

        val inbox = redditInboxErrorPresentation(RedditInboxUiFailureKind.HTTP, details(502), true, false)
        assertEquals(REDDIT_READ_SERVER_PROBLEM_REASON.s, inbox.message.s)

        val customFeed = customFeedPostsErrorPresentation(CustomFeedPostsFailureKind.HTTP, 504, true, append = true)
        assertTrue(customFeed.message.s, customFeed.message.s.startsWith(REDDIT_READ_SERVER_PROBLEM_REASON.s))
    }

    @Test
    fun aClientErrorIsStillARejectionAndALimitSaysSo() {
        assertEquals(
            "Reddit rejected the feed request.",
            redditFeedErrorPresentation(RedditFeedUiFailureKind.HTTP, details(403), false, false).message.s,
        )
        assertEquals(
            "Reddit returned HTTP 403. Loaded posts remain visible.",
            customFeedPostsErrorPresentation(CustomFeedPostsFailureKind.HTTP, 403, false, append = true).message.s,
        )
        assertEquals(REDDIT_READ_RATE_LIMIT_REASON.s, redditReadHttpReason(429, UiText.Plain("rejected")).s)
        assertEquals("rejected", redditReadHttpReason(null, UiText.Plain("rejected")).s)
    }
}
