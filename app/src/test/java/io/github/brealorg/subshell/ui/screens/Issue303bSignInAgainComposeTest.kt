package io.github.brealorg.subshell.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.brealorg.subshell.data.account.DirectChatLiveFailureKind
import io.github.brealorg.subshell.data.account.DirectChatLiveUiState
import io.github.brealorg.subshell.data.account.MessageReplyFailureKind
import io.github.brealorg.subshell.data.account.MessageReplyPhase
import io.github.brealorg.subshell.data.account.MessageReplyUiState
import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.model.MessageBubble
import io.github.brealorg.subshell.ui.components.ViewportErrorTrayHost
import io.github.brealorg.subshell.ui.error.ErrorActionKind
import io.github.brealorg.subshell.ui.error.LocalViewportErrorHostState
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import io.github.brealorg.subshell.ui.error.messageReplyErrorPresentation
import io.github.brealorg.subshell.ui.s
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #303 follow-up: an expired Reddit web sign-in is the one chat failure that
 * signing in again fixes (seen on the Pixel 11). Opening the chat puts one
 * notice in the tray with Sign in again, which starts the app's sign-in; an
 * image send that meets it offers the same button inline
 * (ISSUE303B_AN_EXPIRED_WEB_SIGN_IN_OFFERS_SIGN_IN_AGAIN_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-w411dp-h891dp")
class Issue303bSignInAgainComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = "reddit:alice"
    private val chatId = "t4_chat"

    private fun show(
        failureKind: DirectChatLiveFailureKind?,
        onSignInAgain: () -> Unit,
        onReconnectInbox: () -> Unit = {},
    ) {
        val host = ViewportErrorHostState()
        composeRule.setContent {
            CompositionLocalProvider(LocalViewportErrorHostState provides host) {
                Box {
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
                        directChatLiveState = DirectChatLiveUiState(
                            accountStableId = account,
                            inboxItemId = chatId,
                            participantUsername = "bob",
                            failureKind = failureKind,
                        ),
                        onReconnectInbox = onReconnectInbox,
                        onSignInAgain = onSignInAgain,
                    )
                    ViewportErrorTrayHost(state = host)
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun anExpiredWebSignInPutsSignInAgainInTheTray() {
        var signIns = 0
        var inboxAuthorizations = 0
        show(
            DirectChatLiveFailureKind.WEB_SIGN_IN_EXPIRED,
            onSignInAgain = { signIns += 1 },
            onReconnectInbox = { inboxAuthorizations += 1 },
        )

        composeRule.onNodeWithText("Chat sign-in expired").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in again").performClick()

        // The inbox authorization only refreshes while the OAuth grant is
        // valid (seen on the Pixel 11); reconnecting the account signs in.
        assertEquals(1, signIns)
        assertEquals(0, inboxAuthorizations)
    }

    @Test
    fun anotherLiveFailureLeavesTheTrayEmpty() {
        show(DirectChatLiveFailureKind.SESSION, onSignInAgain = {})

        assertEquals(0, composeRule.onAllNodesWithText("Chat sign-in expired").fetchSemanticsNodes().size)
    }

    @Test
    fun anImageSendThatMeetsItOffersSignInAgainInline() {
        val presentation = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                accountStableId = account,
                inboxItemId = chatId,
                parentFullname = chatId,
                failureKind = MessageReplyFailureKind.CHAT_WEB_SIGN_IN_EXPIRED,
            ),
            directChat = true,
            canRefresh = true,
        )

        assertEquals(ErrorActionKind.SIGN_IN, presentation.primaryAction?.kind)
        assertEquals("Sign in again", presentation.primaryAction?.label?.s)
        val other = messageReplyErrorPresentation(
            state = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                accountStableId = account,
                inboxItemId = chatId,
                parentFullname = chatId,
                failureKind = MessageReplyFailureKind.CHAT_SESSION_REQUIRED,
            ),
            directChat = true,
            canRefresh = true,
        )
        assertNull(other.primaryAction)
    }
}
