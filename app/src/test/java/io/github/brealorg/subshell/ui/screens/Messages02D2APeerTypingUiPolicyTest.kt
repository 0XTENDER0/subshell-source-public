package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.data.account.DirectChatLiveUiState
import io.github.brealorg.subshell.ui.s
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertNull
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en")
class Messages02D2APeerTypingUiPolicyTest {
    @Test
    fun directChatShowsPeerTypingLabelOnlyWhileRuntimeStateIsTyping() {
        val typing = DirectChatLiveUiState(peerTyping = true)
        val idle = DirectChatLiveUiState(peerTyping = false)

        assertEquals(
            "u/peer_user is typing…",
            directChatPeerTypingLabel(
                kind = ConversationDetailKind.DIRECT_CHAT,
                participantUsername = "peer_user",
                liveState = typing,
            )?.s,
        )
        assertNull(
            directChatPeerTypingLabel(
                kind = ConversationDetailKind.DIRECT_CHAT,
                participantUsername = "peer_user",
                liveState = idle,
            ),
        )
    }

    @Test
    fun privateMessageThreadNeverShowsMatrixTypingState() {
        assertNull(
            directChatPeerTypingLabel(
                kind = ConversationDetailKind.PRIVATE_MESSAGE_THREAD,
                participantUsername = "peer_user",
                liveState = DirectChatLiveUiState(peerTyping = true),
            ),
        )
    }

    @Test
    fun typingLabelNormalizesRedditUserPrefixAndRejectsBlankParticipant() {
        assertEquals(
            "u/peer_user is typing…",
            directChatPeerTypingLabel(
                kind = ConversationDetailKind.DIRECT_CHAT,
                participantUsername = "u/peer_user",
                liveState = DirectChatLiveUiState(peerTyping = true),
            )?.s,
        )
        assertNull(
            directChatPeerTypingLabel(
                kind = ConversationDetailKind.DIRECT_CHAT,
                participantUsername = " ",
                liveState = DirectChatLiveUiState(peerTyping = true),
            ),
        )
    }
}
