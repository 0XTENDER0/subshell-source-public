package io.github.brealorg.subshell.ui.screens

import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import io.github.brealorg.subshell.data.account.DirectChatLiveUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * ISSUE309_THE_MESSAGE_MENU_LEAVES_THE_KEYBOARD_ALONE_V1 (#309): with the keyboard open,
 * a chat message's ⋮ menu opened and closed within 0.1 s, because the menu's window took
 * focus from the reply field. The menu's window does not take focus, and Back still
 * closes the menu before it leaves the conversation. (Robolectric has no keyboard, so
 * the test reads the window flag the fix depends on.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue309MessageMenuKeepsTheKeyboardComposeTest {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val account = "reddit:test"
    private var backs = 0

    private fun show() {
        val own = MessageBubble(id = "\$own1", sender = "me", age = "now", body = "mine", outgoing = true)
        composeRule.setContent {
            ConversationScreen(
                item = InboxItem(
                    id = "chat_1",
                    kind = ActivityKind.MESSAGE,
                    title = "[direct chat room]",
                    sender = "peer",
                    age = "1h",
                    preview = "mine",
                    conversation = listOf(own),
                ),
                onBack = { backs++ },
                writeEnabled = true,
                accountStableId = account,
                ownUsername = "me",
                directChatLiveState = DirectChatLiveUiState(
                    accountStableId = account,
                    inboxItemId = "chat_1",
                    participantUsername = "peer",
                    live = true,
                    messages = listOf(own),
                ),
                directChatParticipantUsername = "peer",
                onReactDirectChatMessage = { _, _ -> },
            )
        }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasContentDescription("Message actions") and hasClickAction())
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private val react: SemanticsMatcher = hasText("React") and hasClickAction()

    private fun popupWindows(): List<WindowManager.LayoutParams> {
        // WindowManagerGlobal is hidden from the SDK stubs; Robolectric runs the real class.
        val global = ReflectionHelpers.callStaticMethod<Any>(Class.forName("android.view.WindowManagerGlobal"), "getInstance")
        return ReflectionHelpers.getField<List<View>>(global, "mViews")
            .filter { it.isAttachedToWindow && it.javaClass.name.contains("PopupLayout") }
            .mapNotNull { it.layoutParams as? WindowManager.LayoutParams }
    }

    @Test
    fun theMenuWindowDoesNotTakeFocusFromTheReplyField() {
        show()

        assertEquals(1, composeRule.onAllNodes(react).fetchSemanticsNodes().size)
        val popups = popupWindows()
        assertEquals(1, popups.size)
        assertTrue(
            "the menu's window must not take focus (it hid the keyboard)",
            popups.single().flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0,
        )
    }

    @Test
    fun backClosesTheMenuAndStaysInTheConversation() {
        show()

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodes(react).fetchSemanticsNodes().size)
        assertEquals(0, backs)
        assertEquals(1, composeRule.onAllNodes(hasText("mine")).fetchSemanticsNodes().size)
    }
}
