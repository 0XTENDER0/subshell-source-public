package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.ui.s
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.account.RedditRemovalMessage
import io.github.brealorg.subshell.data.account.RedditRemovalMessageType
import io.github.brealorg.subshell.data.account.RedditRemovalReason
import io.github.brealorg.subshell.data.account.RedditRemovalStepOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(qualifiers = "en")
class ModResponse01A2RemovalResponseDraftTest {
    private val reason = RedditRemovalReason(id = "r1", title = "Rule 1", message = "Please read rule 1.")

    // MODRESPONSE02_PICKING_A_REASON_TURNS_THE_MESSAGE_ON_V1 (successor of
    // "a selected reason alone never sends")
    @Test
    fun offWithoutAReasonAndOnOnceAReasonWithAMessageIsPicked() {
        val draft = RemovalResponseDraft()
        assertFalse(draft.enabled)
        draft.prefillFrom(null)
        assertFalse(draft.enabled)
        assertNull(draft.toMessageOrNull(null))

        draft.prefillFrom(reason)
        assertTrue("a reason with a message switches the message on", draft.enabled)
        assertEquals(
            RedditRemovalMessage(RedditRemovalMessageType.PRIVATE, "Please read rule 1.", "Rule 1"),
            draft.toMessageOrNull(reason),
        )
        assertFalse(draft.blocksConfirm)

        draft.prefillFrom(null)
        assertFalse("No removal reason switches it off again", draft.enabled)
        draft.prefillFrom(RedditRemovalReason(id = "r3", title = "Spam", message = "  "))
        assertFalse("a reason without a message leaves it off", draft.enabled)
    }

    @Test
    fun theModeratorsOwnChoiceIsNeverMovedByTheReason() {
        val off = RemovalResponseDraft()
        off.prefillFrom(reason)
        off.setEnabledByModerator(false)
        off.prefillFrom(RedditRemovalReason(id = "r2", title = "Rule 2", message = "Rule 2 text"))
        assertFalse(off.enabled)
        assertNull(off.toMessageOrNull(reason))

        val on = RemovalResponseDraft()
        on.setEnabledByModerator(true)
        on.prefillFrom(reason)
        on.prefillFrom(null)
        assertTrue(on.enabled)

        val written = RemovalResponseDraft()
        written.prefillFrom(reason)
        written.edit("My own words")
        written.prefillFrom(null)
        assertTrue("a written message stays on", written.enabled)
    }

    @Test
    fun prefillFollowsTheReasonUntilEditedThenKeepsTheEdit() {
        val draft = RemovalResponseDraft()
        draft.prefillFrom(reason)
        val other = RedditRemovalReason(id = "r2", title = "Rule 2", message = "Rule 2 text")
        draft.prefillFrom(other)
        assertEquals("Rule 2 text", draft.body)
        draft.prefillFrom(null)
        assertEquals("", draft.body)

        draft.edit("My own words")
        draft.prefillFrom(reason)
        assertEquals("My own words", draft.body)
        assertTrue(draft.edited)
    }

    @Test
    fun enabledDraftBuildsTheMessageWithTheReasonTitleAndDefaultsToPrivate() {
        val draft = RemovalResponseDraft()
        draft.setEnabledByModerator(true)
        assertEquals(RedditRemovalMessageType.PRIVATE, draft.type)
        assertTrue("empty body blocks confirm", draft.blocksConfirm)
        assertNull(draft.toMessageOrNull(reason))

        draft.edit("  Removed for rule 1.  ")
        assertFalse(draft.blocksConfirm)
        assertEquals(
            RedditRemovalMessage(RedditRemovalMessageType.PRIVATE, "Removed for rule 1.", "Rule 1"),
            draft.toMessageOrNull(reason),
        )
        draft.type = RedditRemovalMessageType.PUBLIC_AS_SUBREDDIT
        assertEquals(
            RedditRemovalMessage(RedditRemovalMessageType.PUBLIC_AS_SUBREDDIT, "Removed for rule 1.", "Removed"),
            draft.toMessageOrNull(null),
        )
    }

    @Test
    fun bodyIsBoundedAndTemplatesCountAsEdits() {
        val draft = RemovalResponseDraft()
        draft.edit("x".repeat(RedditRemovalMessage.MAX_REMOVAL_MESSAGE_BODY_CHARS + 5))
        assertEquals(RedditRemovalMessage.MAX_REMOVAL_MESSAGE_BODY_CHARS, draft.body.length)
        val fresh = RemovalResponseDraft()
        fresh.useTemplate("Template")
        assertTrue(fresh.edited)
        fresh.prefillFrom(reason)
        assertEquals("Template", fresh.body)
    }

    @Test
    fun presentationCopyMatchesRedditSemantics() {
        assertEquals(
            "Reddit posts it as a distinguished, stickied comment.",
            removalResponseTypeDescription(RedditRemovalMessageType.PUBLIC, RedditModeratorQueueKind.POST).s,
        )
        assertEquals(
            "Reddit posts it as a distinguished reply.",
            removalResponseTypeDescription(RedditRemovalMessageType.PUBLIC_AS_SUBREDDIT, RedditModeratorQueueKind.COMMENT).s,
        )
        assertEquals(
            "Sent from the subreddit's modmail.",
            removalResponseTypeDescription(RedditRemovalMessageType.PRIVATE_EXPOSED, RedditModeratorQueueKind.POST).s,
        )
        assertEquals(4, RedditRemovalMessageType.entries.map(::removalResponseTypeLabel).distinct().size)
    }

    @Test
    fun partialSuccessNoticeNamesEveryFailedStep() {
        assertEquals(
            "Reddit removed the item, but the removal message was not sent.",
            removalPartialSuccessMessage(reasonWarning = false, messageOutcome = RedditRemovalStepOutcome.FAILED).s,
        )
        assertEquals(
            "Reddit removed the item, but the removal reason or mod note could not be confirmed and the removal message was not sent.",
            removalPartialSuccessMessage(reasonWarning = true, messageOutcome = RedditRemovalStepOutcome.FAILED).s,
        )
        assertEquals(
            "Reddit removed the item, but the removal reason or mod note could not be confirmed.",
            removalPartialSuccessMessage(reasonWarning = true, messageOutcome = RedditRemovalStepOutcome.SENT).s,
        )
    }
}
