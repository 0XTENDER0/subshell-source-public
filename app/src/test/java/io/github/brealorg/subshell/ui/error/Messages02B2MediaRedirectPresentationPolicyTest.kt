package io.github.brealorg.subshell.ui.error

import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.ui.s
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.LocalFailureKind
import org.junit.Assert.assertTrue
import org.junit.Test

// TRANSLATE03: reads string resources through UiText.s, so it needs Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Messages02B2MediaRedirectPresentationPolicyTest {
    @Test
    fun unresolved308IsPresentedAsRedirectNotRejection() {
        val presentation = remoteMediaErrorPresentation(
            kind = RemoteMediaKind.IMAGE,
            details = FailureDetails(
                httpStatusCode = 308,
                retryable = false,
            ),
        )

        assertTrue(presentation.message.s.contains("redirected", ignoreCase = true))
        assertTrue(!presentation.message.s.contains("rejected", ignoreCase = true))
    }

    @Test
    fun blockedRedirectExplainsSecureMediaBoundary() {
        val presentation = remoteMediaErrorPresentation(
            kind = RemoteMediaKind.IMAGE,
            details = FailureDetails(
                httpStatusCode = 308,
                retryable = false,
                localKind = LocalFailureKind.SECURITY,
            ),
        )

        assertTrue(presentation.message.s.contains("blocked", ignoreCase = true))
        assertTrue(presentation.message.s.contains("secure", ignoreCase = true))
    }
}
