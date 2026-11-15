package io.github.brealorg.subshell.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.brealorg.subshell.data.account.MessageReplyPhase
import io.github.brealorg.subshell.data.account.MessageReplyUiState
import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.model.MessageBubble
import io.github.brealorg.subshell.ui.ComposerMediaAttachment
import io.github.brealorg.subshell.ui.ComposerMediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #306: coming back to a conversation whose last send had succeeded cleared
 * the image of the draft the reader left there
 * (ISSUE306_AN_OLD_SEND_LEAVES_A_NEW_DRAFT_ALONE_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-w411dp-h891dp")
class Issue306OldSendLeavesDraftAloneComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = "reddit:alice"
    private val chatId = "t4_chat"
    private val gif = ComposerMediaAttachment(uri = "content://media/picked/9", mimeType = "image/gif", kind = ComposerMediaKind.GIF)

    @Test
    fun anEarlierSuccessfulSendLeavesTheDraftsImage() {
        val reported = mutableListOf<ComposerMediaAttachment?>()
        composeRule.setContent {
            ConversationScreen(
                item = InboxItem(
                    id = chatId,
                    kind = ActivityKind.MESSAGE,
                    title = "[direct chat room]",
                    sender = "bob",
                    age = "1h",
                    preview = "Hi",
                    conversation = listOf(
                        MessageBubble(id = "t4_mine", sender = "alice", age = "1h", body = "earlier", outgoing = true),
                    ),
                ),
                onBack = {},
                accountStableId = account,
                directChatParticipantUsername = "bob",
                replyParentFullname = chatId,
                replyMedia = gif,
                onReplyMediaChange = { reported += it },
                messageReplyState = MessageReplyUiState(
                    phase = MessageReplyPhase.SUCCEEDED,
                    accountStableId = account,
                    inboxItemId = chatId,
                    parentFullname = chatId,
                    completionRevision = 3L,
                ),
            )
        }
        composeRule.waitForIdle()

        assertTrue(reported.isNotEmpty())
        assertEquals(gif, reported.last())
    }
}
