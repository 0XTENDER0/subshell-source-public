package io.github.brealorg.subshell.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditModmailConversation
import io.github.brealorg.subshell.data.account.RedditModmailThread
import io.github.brealorg.subshell.data.account.RedditModmailUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pixel 11 (2026-11-15, during #304): an unsent modmail reply was gone after
 * closing and reopening the conversation, and Internal note was back to Reply.
 * The draft, its mode and identity stay with the account and conversation
 * (ISSUE304_MODMAIL_DRAFT_SURVIVES_CLOSING_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-w411dp-h891dp")
class Issue304ModmailDraftComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:mod",
        username = "mod_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun conversation(id: String) = RedditModmailConversation(
        id = id,
        communityName = "subshell",
        subject = "Question $id",
        participantName = "someone",
        preview = "Hello",
        lastUpdatedEpochSeconds = 1L,
        unread = false,
        isInternal = false,
        messageCount = 1,
    )

    @Test
    fun theDraftAndItsModeComeBackWhenTheConversationOpensAgain() {
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        var open by mutableStateOf<String?>("c1")
        composeRule.setContent {
            val id = open
            if (id != null) {
                val thread = RedditModmailThread(conversation = conversation(id), messages = emptyList())
                InboxModmailThreadSurface(
                    thread = thread,
                    state = state,
                    redditModmailState = RedditModmailUiState(
                        accountStableId = account.stableId,
                        loaded = true,
                        selectedConversationId = id,
                        thread = thread,
                    ),
                    accountStableId = account.stableId,
                    authenticated = true,
                    listState = rememberLazyListState(),
                    applySystemBars = true,
                    onOpenRedditModmailConversation = { _, _ -> },
                    onSendRedditModmailMessage = { _, _, _, _, _ -> },
                    onSetRedditModmailArchived = { _, _, _ -> },
                    onSetRedditModmailUnread = { _, _, _ -> },
                    onVerifyRedditModmailReadState = { _, _ -> },
                    onApplyRedditModmailTriage = { _, _, _ -> },
                    onCloseRedditModmailConversation = {},
                )
            }
        }
        composeRule.onNodeWithText("Internal note").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("half a note")

        // Closed in the same frame as the last keystroke.
        open = null
        composeRule.waitForIdle()
        assertEquals("stored after closing", "half a note", state.modmailDraft(account.stableId, "c1")?.text)
        open = "c2"
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodes(androidx.compose.ui.test.hasText("half a note")).fetchSemanticsNodes().size)
        open = "c1"
        composeRule.waitForIdle()

        composeRule.onNodeWithText("half a note").assertExists()
        composeRule.onNodeWithText("Internal note").assertIsSelected()
    }
}
