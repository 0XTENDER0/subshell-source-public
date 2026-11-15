package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.DirectChatStartFailureKind
import io.github.brealorg.subshell.data.account.DirectChatStartPhase
import io.github.brealorg.subshell.data.account.DirectChatStartRequest
import io.github.brealorg.subshell.data.account.MessageReplyChannel
import io.github.brealorg.subshell.data.account.MessageReplyFailureKind
import io.github.brealorg.subshell.data.account.MessageReplyMediaAttachment
import io.github.brealorg.subshell.data.account.MessageReplyRequest
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditDirectChatStartResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditDirectChatStartWriter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #306: a refused reply (validation) had no account or conversation, so the
 * conversation's scoped state fell back to empty and Send seemed to do
 * nothing (ISSUE306_A_REFUSED_SEND_IS_SHOWN_IN_ITS_CONVERSATION_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue306ValidationIsScopedTest {
    @Test
    fun aRefusedReplyBelongsToItsConversation() {
        val coordinator = MessageReplyCoordinator(
            messageReplyWriter = { error("not sent") },
            loadRedditInbox = { _, _ -> },
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

        coordinator.sendRedditMessageReply(
            MessageReplyRequest(
                accountStableId = "reddit:alice",
                inboxItemId = "t4_chat",
                parentFullname = "t4_chat",
                channel = MessageReplyChannel.DIRECT_CHAT,
                text = "",
                media = MessageReplyMediaAttachment(uri = "content://media/1", mimeType = "video/mp4"),
                directChatParticipantUsername = "bob",
            ),
        )

        val state = coordinator.messageReplyUiState.value
        assertEquals(MessageReplyFailureKind.VALIDATION, state.failureKind)
        assertEquals("reddit:alice", state.accountStableId)
        assertEquals("t4_chat", state.inboxItemId)
    }

    // ISSUE306_VALIDATION_NEVER_REPLACES_A_START_IN_FLIGHT_V1: an invalid start
    // replaced the account's start in flight (its answer was then dropped).
    @Test(timeout = 10_000)
    fun anInvalidStartLeavesTheStartInFlightAlone() {
        val answer = CompletableDeferred<AuthenticatedRedditDirectChatStartResult>()
        val coordinator = DirectChatCoordinator(
            directChatStartWriter = object : AuthenticatedRedditDirectChatStartWriter {
                override suspend fun start(request: DirectChatStartRequest) = answer.await()
            },
            directChatLiveReader = null,
            directChatTypingWriter = null,
            directChatRedactionWriter = null,
            directChatReactionWriter = null,
            directChatReactionPresentationCache = null,
            directChatReactionRelationsProbeReader = null,
            onChatStarted = {},
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        coordinator.startRedditDirectChat(DirectChatStartRequest("reddit:alice", "bob", text = "hi"))
        assertEquals(DirectChatStartPhase.SENDING, coordinator.directChatStartUiState.value.phase)

        coordinator.startRedditDirectChat(DirectChatStartRequest("reddit:alice", "bob", text = ""))

        assertEquals(DirectChatStartPhase.SENDING, coordinator.directChatStartUiState.value.phase)
        answer.complete(AuthenticatedRedditDirectChatStartResult.Failed(DirectChatStartFailureKind.HTTP))
    }
}
