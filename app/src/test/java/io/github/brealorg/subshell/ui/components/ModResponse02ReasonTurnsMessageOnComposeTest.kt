package io.github.brealorg.subshell.ui.components

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.brealorg.subshell.data.account.RedditModeratorAction
import io.github.brealorg.subshell.data.account.RedditModeratorActionRequest
import io.github.brealorg.subshell.data.account.RedditModeratorActionUiState
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.account.RedditRemovalMessageType
import io.github.brealorg.subshell.data.account.RedditRemovalReason
import io.github.brealorg.subshell.data.account.RedditRemovalReasonPhase
import io.github.brealorg.subshell.data.account.RedditRemovalReasonUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MODRESPONSE02_PICKING_A_REASON_TURNS_THE_MESSAGE_ON_V1: in the real remove
 * form, picking a removal reason that has a message switches "Send a removal
 * message" on, and the remove carries the reason's text; "No removal reason"
 * switches it off; a moderator who switches it off keeps it off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ModResponse02ReasonTurnsMessageOnComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = "reddit:mod"
    private val item = RedditModeratorQueueItem(
        fullname = "t3_abc123",
        kind = RedditModeratorQueueKind.POST,
        communityName = "subshell",
        author = "someone",
        summary = "A reported post",
    )
    private val reasons = RedditRemovalReasonUiState(
        phase = RedditRemovalReasonPhase.LOADED,
        accountStableId = account,
        communityName = "subshell",
        reasons = listOf(
            RedditRemovalReason(id = "r1", title = "Rule 1", message = "Please read rule 1."),
            RedditRemovalReason(id = "r2", title = "Silent", message = ""),
        ),
    )

    private fun showRemoveForm(onModerate: (RedditModeratorActionRequest) -> Unit) {
        composeRule.setContent {
            SubshellTheme {
                ModeratorQuickActionsHarness(
                    accountStableId = account,
                    actionState = RedditModeratorActionUiState(),
                    onModerate = onModerate,
                    notices = null,
                    removalReasonState = reasons,
                ) {
                    InboxModeratorQueueItemCard(
                        item = item,
                        actionUiState = RedditModeratorActionUiState(),
                        onOpen = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasText("Remove") and hasClickAction()).onFirst().performClick()
        composeRule.waitForIdle()
    }

    private fun pick(title: String) {
        composeRule.onNodeWithText(title).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private val messageSwitch get() =
        composeRule.onNode(isToggleable() and hasText("Send a removal message", substring = true))

    @Test
    fun pickingAReasonWithAMessageSendsItWithTheRemove() {
        var moderated: RedditModeratorActionRequest? = null
        showRemoveForm(onModerate = { moderated = it })
        messageSwitch.performScrollTo().assertIsOff()

        pick("Rule 1")
        messageSwitch.performScrollTo().assertIsOn()
        composeRule.onNodeWithText("Remove and send").performClick()

        assertEquals(RedditModeratorAction.REMOVE, moderated?.action)
        assertEquals("r1", moderated?.removalReasonId)
        assertEquals(RedditRemovalMessageType.PRIVATE, moderated?.removalMessage?.type)
        assertEquals("Please read rule 1.", moderated?.removalMessage?.body)
        assertEquals("Rule 1", moderated?.removalMessage?.title)
    }

    @Test
    fun noReasonOrAReasonWithoutAMessageLeavesItOff() {
        var moderated: RedditModeratorActionRequest? = null
        showRemoveForm(onModerate = { moderated = it })

        pick("Rule 1")
        pick("No removal reason")
        messageSwitch.performScrollTo().assertIsOff()
        pick("Silent")
        messageSwitch.performScrollTo().assertIsOff()
        composeRule.onAllNodes(hasText("Remove") and hasClickAction()).onLast().performClick()

        assertEquals("r2", moderated?.removalReasonId)
        assertNull(moderated?.removalMessage)
    }

    @Test
    fun aMessageTheModeratorSwitchedOffStaysOff() {
        var moderated: RedditModeratorActionRequest? = null
        showRemoveForm(onModerate = { moderated = it })

        pick("Rule 1")
        messageSwitch.performScrollTo().performClick()
        composeRule.waitForIdle()
        pick("No removal reason")
        pick("Rule 1")
        messageSwitch.performScrollTo().assertIsOff()
        composeRule.onAllNodes(hasText("Remove") and hasClickAction()).onLast().performClick()

        assertEquals("r1", moderated?.removalReasonId)
        assertNull(moderated?.removalMessage)
    }
}
