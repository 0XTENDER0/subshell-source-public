package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditInboxUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #306: the inbox selection (and the list positions) carried over to another
 * account: "1 selected" over its items, Back eaten by the selection
 * (ISSUE306_INBOX_LISTS_AND_SELECTION_BELONG_TO_THE_ACCOUNT_V1); a selectable
 * row says so to TalkBack (ISSUE306_INBOX_ROWS_TELL_TALKBACK_ABOUT_SELECTION_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-w411dp-h891dp")
class Issue306InboxSelectionPerAccountComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private fun account(id: String, name: String) = AccountSessionSummary(
        stableId = id,
        username = name,
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val first = account("reddit:first", "first_user")
    private val second = account("reddit:second", "second_user")
    private val items = listOf(
        InboxItem(id = "t1_a", kind = ActivityKind.REPLY, title = "", sender = "alice", age = "1h", preview = "First answer", unread = true),
    )

    @Test
    fun aSelectionDoesNotFollowToAnotherAccount() {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(first, second),
            initialActiveAccountId = first.stableId,
            initialInteractionSideConfigured = true,
        )
        var selection by mutableStateOf<InboxContextualSelection?>(null)
        composeRule.setContent {
            InboxScreen(
                state = state,
                redditInboxState = RedditInboxUiState(accountStableId = state.activeAccountId, items = items, loaded = true),
                onContextualSelectionChanged = { selection = it },
            )
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("First answer", substring = true).onFirst().performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertEquals(1, selection?.selectedCount)

        composeRule.runOnIdle { state.switchAccount(second.stableId) }
        composeRule.waitForIdle()

        assertNull(selection)
    }

    @Test
    fun aRowInSelectionModeSaysItIsSelected() {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(first),
            initialActiveAccountId = first.stableId,
            initialInteractionSideConfigured = true,
        )
        composeRule.setContent {
            InboxScreen(
                state = state,
                redditInboxState = RedditInboxUiState(accountStableId = first.stableId, items = items, loaded = true),
            )
        }
        composeRule.waitForIdle()
        val row = composeRule.onAllNodesWithText("First answer", substring = true, useUnmergedTree = false).onFirst()
        val longClickLabel = row.fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnLongClick)?.label
        assertEquals("Select activity", longClickLabel)

        row.performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertEquals(true, row.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
    }
}
