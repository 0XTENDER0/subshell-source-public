package io.github.brealorg.subshell.data.error

import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.ui.s
import io.github.brealorg.subshell.data.account.RedditCommentUiFailureKind
import io.github.brealorg.subshell.data.account.RedditCommunityDiscoveryUiFailureKind
import io.github.brealorg.subshell.data.account.RedditFeedUiFailureKind
import io.github.brealorg.subshell.data.account.RedditInboxUiFailureKind
import io.github.brealorg.subshell.data.account.RedditProfileCommentsUiFailureKind
import io.github.brealorg.subshell.data.account.RedditSubscriptionsUiFailureKind
import io.github.brealorg.subshell.data.network.NetworkProtocolFailureKind
import io.github.brealorg.subshell.data.network.NetworkTransportFailureKind
import io.github.brealorg.subshell.ui.error.ErrorActionKind
import io.github.brealorg.subshell.ui.error.ErrorPresentation
import io.github.brealorg.subshell.ui.error.ErrorPresentationKind
import io.github.brealorg.subshell.ui.error.redditCommentsErrorPresentation
import io.github.brealorg.subshell.ui.error.redditCommunityDiscoveryErrorPresentation
import io.github.brealorg.subshell.ui.error.redditCommunityDiscoverySuggestionErrorPresentation
import io.github.brealorg.subshell.ui.error.redditFeedAppendErrorPresentation
import io.github.brealorg.subshell.ui.error.redditFeedErrorPresentation
import io.github.brealorg.subshell.ui.error.redditInboxErrorPresentation
import io.github.brealorg.subshell.ui.error.redditProfileCommentsErrorPresentation
import io.github.brealorg.subshell.ui.error.redditSubscriptionsErrorPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// TRANSLATE03: reads string resources through UiText.s, so it needs Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Error01a2CoreRedditReadErrorPolicyTest {
    @Test
    fun cachedCommentsRetainHttpCauseRetryDelayAndDegradedState() {
        val presentation = redditCommentsErrorPresentation(
            failureKind = RedditCommentUiFailureKind.HTTP,
            details = FailureDetails(
                httpStatusCode = 429,
                retryAfterMs = 18_000L,
                retryable = true,
            ),
            retryable = true,
            showingCachedContent = true,
        )

        assertEquals(ErrorPresentationKind.DEGRADED, presentation.kind)
        assertEquals("Couldn't refresh comments", presentation.title.s)
        assertTrue(presentation.message.s.contains("Saved comments remain visible."))
        assertEquals("HTTP 429 · Retry after 18 s", presentation.technicalDetail)
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
    }

    @Test
    fun appendFailureUsesDegradedLoadMoreCopyAndRetry() {
        val presentation = redditFeedAppendErrorPresentation(
            failureKind = RedditFeedUiFailureKind.TRANSPORT,
            details = FailureDetails(
                retryable = true,
                transportKind = NetworkTransportFailureKind.DNS,
            ),
            retryable = true,
        )

        assertEquals(ErrorPresentationKind.DEGRADED, presentation.kind)
        assertEquals("Couldn't load more posts", presentation.title.s)
        assertTrue(presentation.message.s.contains("Posts already loaded remain visible."))
        assertEquals("Network: DNS", presentation.technicalDetail)
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
    }

    @Test
    fun feedTransportFailureIsBlockingWithoutCachedPostsAndRetryable() {
        val presentation = redditFeedErrorPresentation(
            failureKind = RedditFeedUiFailureKind.TRANSPORT,
            details = FailureDetails(
                retryable = true,
                transportKind = NetworkTransportFailureKind.TIMEOUT,
            ),
            retryable = true,
            showingCachedContent = false,
        )

        assertEquals(ErrorPresentationKind.BLOCKING, presentation.kind)
        assertTrue(presentation.message.s.contains("couldn't reach Reddit"))
        assertEquals("Network: Timeout", presentation.technicalDetail)
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
    }

    @Test
    fun discoveryRateLimitUsesSpecificReasonAndSharedHttpDetail() {
        val presentation = redditCommunityDiscoveryErrorPresentation(
            failureKind = RedditCommunityDiscoveryUiFailureKind.HTTP,
            details = FailureDetails(
                httpStatusCode = 429,
                retryAfterMs = 61_000L,
                retryable = true,
            ),
            retryable = true,
            showingCachedContent = true,
        )

        assertEquals(ErrorPresentationKind.DEGRADED, presentation.kind)
        assertTrue(presentation.message.s.contains("request limit was reached"))
        assertTrue(presentation.message.s.contains("Saved Explore results remain visible."))
        assertEquals("HTTP 429 · Retry after 2 min", presentation.technicalDetail)
    }

    @Test
    fun suggestionFailureDistinguishesInlineFailureFromFallbackDegradation() {
        val details = FailureDetails(
            protocolKind = NetworkProtocolFailureKind.MALFORMED_JSON,
            retryable = false,
        )
        val inline = redditCommunityDiscoverySuggestionErrorPresentation(
            failureKind = RedditCommunityDiscoveryUiFailureKind.PROTOCOL,
            details = details,
            retryable = false,
            showingFallbackResults = false,
        )
        val fallback = redditCommunityDiscoverySuggestionErrorPresentation(
            failureKind = RedditCommunityDiscoveryUiFailureKind.PROTOCOL,
            details = details,
            retryable = false,
            showingFallbackResults = true,
        )

        assertEquals(ErrorPresentationKind.INLINE, inline.kind)
        assertEquals(ErrorPresentationKind.DEGRADED, fallback.kind)
        assertEquals("Response: Malformed response", inline.technicalDetail)
        assertTrue(fallback.message.s.contains("Quick suggestions remain visible."))
        assertNull(fallback.primaryAction)
    }

    @Test
    fun sessionAndConfigurationCopyRemainActionableWithoutPretendingScopeFailure() {
        val session = redditInboxErrorPresentation(
            failureKind = RedditInboxUiFailureKind.SESSION,
            details = null,
            retryable = false,
            showingCachedContent = false,
        )
        val configuration = redditSubscriptionsErrorPresentation(
            failureKind = RedditSubscriptionsUiFailureKind.CLIENT_CONFIGURATION,
            details = FailureDetails(localKind = LocalFailureKind.CONFIGURATION),
            retryable = false,
            showingCachedContent = false,
        )

        assertTrue(session.message.s.contains("sign-in"))
        assertFalse(session.message.s.contains("permission", ignoreCase = true))
        assertTrue(configuration.message.s.contains("isn't configured"))
        assertEquals("Local: Configuration", configuration.technicalDetail)
    }

    @Test
    fun allCoreReadPresentationsAvoidGenericFallbackCopy() {
        val presentations = listOf(
            redditFeedErrorPresentation(
                RedditFeedUiFailureKind.HTTP,
                FailureDetails(httpStatusCode = 503),
                retryable = true,
                showingCachedContent = false,
            ),
            redditCommentsErrorPresentation(
                RedditCommentUiFailureKind.TRANSPORT,
                FailureDetails(transportKind = NetworkTransportFailureKind.DNS),
                retryable = true,
                showingCachedContent = true,
            ),
            redditInboxErrorPresentation(
                RedditInboxUiFailureKind.PROTOCOL,
                FailureDetails(protocolKind = NetworkProtocolFailureKind.EMPTY_BODY),
                retryable = false,
                showingCachedContent = false,
            ),
            redditSubscriptionsErrorPresentation(
                RedditSubscriptionsUiFailureKind.STORAGE,
                FailureDetails(localKind = LocalFailureKind.STORAGE),
                retryable = true,
                showingCachedContent = true,
            ),
            redditCommunityDiscoveryErrorPresentation(
                RedditCommunityDiscoveryUiFailureKind.HTTP,
                FailureDetails(httpStatusCode = 403),
                retryable = false,
                showingCachedContent = false,
            ),
            redditProfileCommentsErrorPresentation(
                RedditProfileCommentsUiFailureKind.TRANSPORT,
                FailureDetails(transportKind = NetworkTransportFailureKind.CONNECT),
                retryable = true,
                showingCachedContent = true,
            ),
        )

        presentations.forEach(::assertUsefulPresentation)
    }

    private fun assertUsefulPresentation(presentation: ErrorPresentation) {
        assertTrue(presentation.title.s.isNotBlank())
        assertTrue(presentation.message.s.isNotBlank())
        assertFalse(presentation.title.s.contains("Something went wrong", ignoreCase = true))
        assertFalse(presentation.message.s.contains("Something went wrong", ignoreCase = true))
        assertFalse(presentation.title.s.equals("Error", ignoreCase = true))
        assertFalse(presentation.message.s.equals("Failed", ignoreCase = true))
    }
}
