package io.github.brealorg.subshell.ui.error

import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.ui.s
import io.github.brealorg.subshell.data.account.MessageReplyFailureKind
import io.github.brealorg.subshell.data.account.MessageReplyPhase
import io.github.brealorg.subshell.data.account.MessageReplyUiState
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

// TRANSLATE03: reads string resources through UiText.s, so it needs Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Messages02C4R3R1MessageReplyErrorPresentationPolicyTest {
    @Test
    fun directChatApiFailureUsesCanonicalInlineErrorNoticeCopyInsteadOfRawStateMessage() {
        val presentation = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                failureKind = MessageReplyFailureKind.API,
                message = "Reddit rejected this reply.",
            ),
            directChat = true,
        )

        assertEquals(ErrorPresentationKind.INLINE, presentation.kind)
        assertEquals("Couldn't send message", presentation.title.s)
        assertEquals("Reddit rejected the message.", presentation.message.s)
        assertNull(presentation.primaryAction)
        assertEquals(ErrorActionKind.DISMISS, presentation.secondaryAction?.kind)
        assertEquals("Dismiss", presentation.secondaryAction?.label?.s)
    }

    @Test
    fun privateMessageApiFailureUsesReplyTerminology() {
        val presentation = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                failureKind = MessageReplyFailureKind.API,
            ),
            directChat = false,
        )

        assertEquals("Couldn't send reply", presentation.title.s)
        assertEquals("Reddit rejected the reply.", presentation.message.s)
    }

    @Test
    fun outcomeUnknownUsesCanonicalMayHaveBeenSentCardWithoutBlindWriteRetry() {
        val presentation = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.OUTCOME_UNKNOWN,
                failureKind = MessageReplyFailureKind.OUTCOME_UNKNOWN,
            ),
            directChat = true,
            canRefresh = true,
        )

        assertEquals("Message may have been sent", presentation.title.s)
        assertEquals(
            "Reddit may have received this message. Refresh the conversation before trying again.",
            presentation.message.s,
        )
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
        assertEquals("Refresh", presentation.primaryAction?.label?.s)
        assertEquals(ErrorActionKind.DISMISS, presentation.secondaryAction?.kind)
        assertEquals("Checked", presentation.secondaryAction?.label?.s)
    }

    @Test
    fun authorizationFailuresKeepExistingReconnectAndMediaAccessActions() {
        val reconnect = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                failureKind = MessageReplyFailureKind.AUTHORIZATION_REQUIRED,
            ),
            directChat = true,
        )
        val media = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                failureKind = MessageReplyFailureKind.MEDIA_AUTHORIZATION_REQUIRED,
            ),
            directChat = true,
        )

        assertEquals(ErrorActionKind.SIGN_IN, reconnect.primaryAction?.kind)
        assertEquals("Reconnect", reconnect.primaryAction?.label?.s)
        assertEquals(ErrorActionKind.SIGN_IN, media.primaryAction?.kind)
        assertEquals("Grant media access", media.primaryAction?.label?.s)
    }

    @Test
    fun partialDirectChatTextFailureExplainsThatMediaWasAlreadySent() {
        val presentation = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                failureKind = MessageReplyFailureKind.HTTP,
                sentHadMedia = true,
                // The image was confirmed: it has an event id (as the coordinator sets it).
                sentMatrixEventId = "\$image",
                completionRevision = 1L,
            ),
            directChat = true,
        )

        assertEquals("Media sent, text not sent", presentation.title.s)
        assertEquals(
            "The image/GIF was sent, but the message text wasn't sent.",
            presentation.message.s,
        )
        assertNull(presentation.primaryAction)
    }

    @Test
    fun partialDirectChatUnknownTextOutcomeRequiresRefreshBeforeRetry() {
        val presentation = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.OUTCOME_UNKNOWN,
                failureKind = MessageReplyFailureKind.OUTCOME_UNKNOWN,
                sentHadMedia = true,
                // The image was confirmed: it has an event id (as the coordinator sets it).
                sentMatrixEventId = "\$image",
                completionRevision = 1L,
            ),
            directChat = true,
            canRefresh = true,
        )

        assertEquals("Media sent; text may have been sent", presentation.title.s)
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
        assertEquals("Refresh", presentation.primaryAction?.label?.s)
        assertEquals(ErrorActionKind.DISMISS, presentation.secondaryAction?.kind)
        assertEquals("Checked", presentation.secondaryAction?.label?.s)
    }

    // ISSUE303_AN_UNCLEAR_IMAGE_IS_NOT_CALLED_SENT_V1 (Pixel 11): the image's own
    // answer was unclear, so it has no event id; it must not be called sent.
    @Test
    fun anImageWhoseOwnAnswerWasUnclearIsNotCalledSent() {
        val presentation = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.OUTCOME_UNKNOWN,
                failureKind = MessageReplyFailureKind.OUTCOME_UNKNOWN,
                sentHadMedia = true,
                sentMatrixMediaId = "media303",
                sentMatrixEventId = null,
                sentDraftText = "Test 10",
                completionRevision = 1L,
            ),
            directChat = true,
            canRefresh = true,
        )

        assertEquals("Image may have been sent", presentation.title.s)
        assertEquals(
            "Reddit did not confirm the image/GIF. Refresh the conversation: it shows up there if it arrived. The text was not sent.",
            presentation.message.s,
        )
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
        assertEquals("Refresh", presentation.primaryAction?.label?.s)
        assertEquals(ErrorActionKind.DISMISS, presentation.secondaryAction?.kind)
        assertEquals("Checked", presentation.secondaryAction?.label?.s)
    }
}
