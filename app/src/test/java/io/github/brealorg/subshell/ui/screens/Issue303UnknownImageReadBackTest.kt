package io.github.brealorg.subshell.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.brealorg.subshell.auth.MessageReplyCoordinator
import io.github.brealorg.subshell.data.account.MessageReplyChannel
import io.github.brealorg.subshell.data.account.MessageReplyFailureKind
import io.github.brealorg.subshell.data.account.MessageReplyMediaAttachment
import io.github.brealorg.subshell.data.account.MessageReplyPhase
import io.github.brealorg.subshell.data.account.MessageReplyRequest
import io.github.brealorg.subshell.data.account.MessageReplyUiState
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditMessageReplyResult
import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.model.MessageBubble
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Review finding 2 (with #303): a Direct Chat image whose send answer was
 * unclear went to the generic failure branch: no inbox reload, nothing to
 * recognise it by, and once dismissed it could be sent again. It now keeps the
 * media id, blocks Send, reloads the inbox, and the conversation clears the
 * warning once Reddit lists an image with that media id; the caption (never
 * sent) stays in the composer (ISSUE303_AN_UNKNOWN_IMAGE_SEND_KEEPS_ITS_MEDIA_ID_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue303UnknownImageReadBackTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = "reddit:alice"
    private val chatId = "t4_chat"

    @Test
    fun anUnknownImageKeepsItsMediaIdBlocksSendAndReloadsTheInbox() {
        val reloads = mutableListOf<String>()
        var sends = 0
        val coordinator = MessageReplyCoordinator(
            messageReplyWriter = {
                sends += 1
                AuthenticatedRedditMessageReplyResult.Failed(
                    kind = MessageReplyFailureKind.OUTCOME_UNKNOWN,
                    statusCode = 503,
                    sentMediaUri = "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/media303",
                    sentMatrixMediaId = "media303",
                )
            },
            loadRedditInbox = { account, _ -> reloads += account },
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        val request = MessageReplyRequest(
            accountStableId = account,
            inboxItemId = chatId,
            parentFullname = chatId,
            channel = MessageReplyChannel.DIRECT_CHAT,
            text = "caption",
            media = MessageReplyMediaAttachment(uri = "content://media/picked/1", mimeType = "image/gif"),
            directChatParticipantUsername = "bob",
        )

        coordinator.sendRedditMessageReply(request)
        coordinator.sendRedditMessageReply(request)

        val state = coordinator.messageReplyUiState.value
        assertEquals(MessageReplyPhase.OUTCOME_UNKNOWN, state.phase)
        assertTrue(state.blocksSend)
        assertEquals(true, state.sentHadMedia)
        assertEquals("media303", state.sentMatrixMediaId)
        assertEquals("caption", state.sentDraftText)
        assertEquals("content://media/picked/1", state.optimisticMediaUri)
        assertTrue(state.completedAtEpochSeconds != null)
        assertEquals(1, sends)
        assertEquals(listOf(account), reloads)
    }

    private val sentAt = 1_800_000_000L
    private val unknown = MessageReplyUiState(
        phase = MessageReplyPhase.OUTCOME_UNKNOWN,
        accountStableId = account,
        inboxItemId = chatId,
        parentFullname = chatId,
        failureKind = MessageReplyFailureKind.OUTCOME_UNKNOWN,
        sentDraftText = "caption",
        sentHadMedia = true,
        sentMatrixMediaId = "media303",
        completedAtEpochSeconds = sentAt,
        completionRevision = 1L,
    )

    private val incoming = MessageBubble(id = chatId, sender = "bob", age = "1h", body = "Hi there")

    private fun show(thread: List<MessageBubble>, onReset: () -> Unit, onText: (String) -> Unit) {
        composeRule.setContent {
            ConversationScreen(
                item = InboxItem(
                    id = chatId,
                    kind = ActivityKind.MESSAGE,
                    title = "[direct chat room]",
                    sender = "bob",
                    age = "1h",
                    preview = "Hi there",
                    conversation = thread,
                ),
                onBack = {},
                accountStableId = account,
                replyText = "caption",
                onReplyTextChange = onText,
                replyParentFullname = chatId,
                messageReplyState = unknown,
                onResetMessageReplyState = onReset,
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theImageListedWithItsMediaIdCountsAsSentAndTheCaptionStays() {
        var resets = 0
        val texts = mutableListOf<String>()
        show(
            thread = listOf(
                incoming,
                MessageBubble(
                    id = "t4_mine",
                    sender = "alice",
                    age = "now",
                    body = "sent an image\nhttps://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/media303",
                    outgoing = true,
                    createdEpochSeconds = sentAt + 3,
                ),
            ),
            onReset = { resets += 1 },
            onText = { texts += it },
        )

        assertEquals(1, resets)
        assertEquals(false, "" in texts)
    }

    @Test
    fun anotherImageInTheThreadLeavesTheWarning() {
        var resets = 0
        show(
            thread = listOf(
                incoming,
                MessageBubble(
                    id = "t4_older",
                    sender = "alice",
                    age = "now",
                    body = "sent an image\nhttps://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/other",
                    outgoing = true,
                    createdEpochSeconds = sentAt + 3,
                ),
            ),
            onReset = { resets += 1 },
            onText = {},
        )

        assertEquals(0, resets)
    }
}
