package io.github.brealorg.subshell.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.brealorg.subshell.data.account.MessageReplyFailureKind
import io.github.brealorg.subshell.data.account.MessageReplyPhase
import io.github.brealorg.subshell.data.account.MessageReplyUiState
import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.model.MessageBubble
import io.github.brealorg.subshell.ui.ComposerMediaAttachment
import io.github.brealorg.subshell.ui.ComposerMediaKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #303 phone test (Pixel 11, 2026-11-14): an image whose own send answer was
 * unclear said "Media sent; text may have been sent" (now "Image may have been
 * sent"), and the picked image left the field for good: "Checked" (the reader
 * looked, it is not there) gave nothing back. It comes back on Checked; when
 * Reddit lists the image instead, it stays gone, so it is never sent twice
 * (ISSUE303_CHECKED_GIVES_AN_UNCLEAR_IMAGE_BACK_V1,
 * ISSUE303_AN_UNCLEAR_IMAGE_IS_NOT_CALLED_SENT_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-w411dp-h891dp")
class Issue303CheckedGivesImageBackComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = "reddit:alice"
    private val chatId = "t4_chat"
    private val gif = ComposerMediaAttachment(
        uri = "content://media/picked/7",
        mimeType = "image/gif",
        kind = ComposerMediaKind.GIF,
    )
    private val sending = MessageReplyUiState(
        phase = MessageReplyPhase.SENDING,
        accountStableId = account,
        inboxItemId = chatId,
        parentFullname = chatId,
    )
    private val unclear = MessageReplyUiState(
        phase = MessageReplyPhase.OUTCOME_UNKNOWN,
        accountStableId = account,
        inboxItemId = chatId,
        parentFullname = chatId,
        failureKind = MessageReplyFailureKind.OUTCOME_UNKNOWN,
        sentDraftText = "Test 10",
        sentHadMedia = true,
        sentMatrixMediaId = "media303",
        sentMatrixEventId = null,
        completedAtEpochSeconds = 1_800_000_000L,
        completionRevision = 1L,
    )

    private var replyState by mutableStateOf(sending)
    private val reported = mutableListOf<ComposerMediaAttachment?>()
    private var resets = 0

    private fun show() {
        composeRule.setContent {
            ConversationScreen(
                item = InboxItem(
                    id = chatId,
                    kind = ActivityKind.MESSAGE,
                    title = "[direct chat room]",
                    sender = "bob",
                    age = "1h",
                    preview = "Hi",
                    conversation = listOf(MessageBubble(id = chatId, sender = "bob", age = "1h", body = "Hi")),
                ),
                onBack = {},
                accountStableId = account,
                directChatParticipantUsername = "bob",
                replyText = "Test 10",
                replyParentFullname = chatId,
                replyMedia = gif,
                onReplyMediaChange = { reported += it },
                messageReplyState = replyState,
                onResetMessageReplyState = { resets += 1; replyState = MessageReplyUiState() },
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun anUnclearImageSaysSoAndCheckedGivesItBack() {
        show()
        replyState = unclear
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Image may have been sent").assertExists()
        assertEquals(null, reported.last())

        composeRule.onNodeWithText("Checked").performClick()
        composeRule.waitForIdle()

        assertEquals(1, resets)
        assertEquals(gif, reported.last())
    }

    @Test
    fun anImageRedditListsIsNotGivenBack() {
        show()
        replyState = unclear
        composeRule.waitForIdle()

        // The conversation found the image and cleared the warning itself.
        replyState = MessageReplyUiState()
        composeRule.waitForIdle()

        assertEquals(null, reported.last())
    }
}
