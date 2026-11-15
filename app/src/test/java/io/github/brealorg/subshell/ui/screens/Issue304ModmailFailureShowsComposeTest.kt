package io.github.brealorg.subshell.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditModmailConversation
import io.github.brealorg.subshell.data.account.RedditModmailFailedAction
import io.github.brealorg.subshell.data.account.RedditModmailThread
import io.github.brealorg.subshell.data.account.RedditModmailUiFailureKind
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
 * #304: a reply Reddit refused (403/429/5xx) showed nothing in the full-screen
 * modmail thread: "Sending reply…" just stopped. The open thread now says which
 * action failed and why (ISSUE304_A_FAILED_ACTION_SAYS_WHICH_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-w411dp-h891dp")
class Issue304ModmailFailureShowsComposeTest {
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

    private val open = RedditModmailConversation(
        id = "c1",
        communityName = "subshell",
        subject = "Open question",
        participantName = "someone",
        preview = "Hello",
        lastUpdatedEpochSeconds = 1L,
        unread = false,
        isInternal = false,
        messageCount = 1,
    )

    private fun show(modmail: RedditModmailUiState, onOpen: () -> Unit = {}) {
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        composeRule.setContent {
            InboxModmailThreadSurface(
                thread = RedditModmailThread(conversation = open, messages = emptyList()),
                state = state,
                redditModmailState = modmail,
                accountStableId = account.stableId,
                authenticated = true,
                listState = rememberLazyListState(),
                applySystemBars = true,
                onOpenRedditModmailConversation = { _, _ -> onOpen() },
                onSendRedditModmailMessage = { _, _, _, _, _ -> },
                onSetRedditModmailArchived = { _, _, _ -> },
                onSetRedditModmailUnread = { _, _, _ -> },
                onVerifyRedditModmailReadState = { _, _ -> },
                onApplyRedditModmailTriage = { _, _, _ -> },
                onCloseRedditModmailConversation = {},
            )
        }
        composeRule.waitForIdle()
    }

    private fun failed(action: RedditModmailFailedAction, kind: RedditModmailUiFailureKind, status: Int? = null) =
        RedditModmailUiState(
            accountStableId = account.stableId,
            loaded = true,
            conversations = listOf(open),
            selectedConversationId = open.id,
            thread = RedditModmailThread(conversation = open, messages = emptyList()),
            failureKind = kind,
            failedAction = action,
            failureHttpStatusCode = status,
        )

    @Test
    fun aRefusedReplyIsShownInTheThread() {
        show(failed(RedditModmailFailedAction.SEND, RedditModmailUiFailureKind.HTTP, status = 503))

        composeRule.onNodeWithText("Couldn’t send the reply").assertExists()
    }

    @Test
    fun aFailedArchiveSaysSo() {
        show(failed(RedditModmailFailedAction.ARCHIVE, RedditModmailUiFailureKind.TRANSPORT))

        composeRule.onNodeWithText("Couldn’t archive or unarchive").assertExists()
        composeRule.onNodeWithText("Couldn’t reach Reddit Modmail.").assertExists()
    }

    @Test
    fun aThreadThatFailedToReloadOffersRetry() {
        var opens = 0
        show(failed(RedditModmailFailedAction.THREAD, RedditModmailUiFailureKind.TRANSPORT), onOpen = { opens += 1 })

        composeRule.onNodeWithText("Couldn’t load this conversation").assertExists()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, opens)
    }

    @Test
    fun anUnclearReplyIsLeftToTheComposersOwnWarning() {
        show(
            failed(RedditModmailFailedAction.SEND, RedditModmailUiFailureKind.TRANSPORT)
                .copy(sendOutcomeUnknown = true),
        )

        assertEquals(0, composeRule.onAllNodesWithText("Couldn’t send the reply").fetchSemanticsNodes().size)
    }
}
