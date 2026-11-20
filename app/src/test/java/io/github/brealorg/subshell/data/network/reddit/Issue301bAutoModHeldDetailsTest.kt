package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.ui.s
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.R
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.ui.components.moderatorQueueItemHeldExplanation
import io.github.brealorg.subshell.ui.components.moderatorQueueItemIsHeldWithoutReports
import io.github.brealorg.subshell.ui.components.moderatorQueueItemRemovalLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE301B_AUTOMOD_HELD_IS_NAMED_V1 (#301, u/vpsj): an item AutoModerator filtered
 * into the queue says "Held by AutoModerator" and is not called removed; post
 * guidance keeps the unnamed explanation (Reddit's own filters: Issue301cFilterReasonTest). The first post
 * follows the JSON u/vpsj shared on 2026-11-14 (author, id and text changed).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Issue301bAutoModHeldDetailsTest {
    private val heldByAutoModerator = UiText.Resource(R.string.comp_held_by_automoderator)
    private val heldByFiltersOrGuidance = UiText.Resource(R.string.comp_held_by_filters_or_guidance)
    private val now = 1_794_630_000L

    private fun item(child: String) =
        SubshellNetworkJson.providerJson.decodeFromString(
            RedditReadListingDto.serializer(),
            """{"kind":"Listing","data":{"after":null,"children":[$child]}}""",
        ).toModeratorQueuePage("indianrailways", SubshellNetworkJson.providerJson, now).items.single()

    private val autoModFilteredPost = """
        {"kind":"t3","data":{"id":"fff666","name":"t3_fff666","subreddit":"indianrailways","author":"traveller",
         "title":"Ticket available","selftext":"If anyone needs it, write to me.","created_utc":${now - 600}.0,
         "is_self":true,"thumbnail":"self","approved":false,"approved_by":null,
         "banned_by":"AutoModerator","banned_at_utc":${now - 600},"ban_note":"remove not spam",
         "removed":false,"removed_by":null,"removed_by_category":"automod_filtered","removal_reason":null,
         "mod_reason_by":null,"mod_reason_title":null,"mod_note":null,"spam":false,
         "num_reports":0,"user_reports":[],"mod_reports":[],"report_reasons":[],
         "permalink":"/r/indianrailways/comments/fff666/ticket_available/"}}
    """.trimIndent()

    private val autoModFilteredComment = """
        {"kind":"t1","data":{"name":"t1_ggg777","subreddit":"indianrailways","author":"traveller",
         "body":"Still free?","banned_by":"AutoModerator","removed":false,"spam":false,
         "num_reports":0,"user_reports":[],"mod_reports":[],
         "permalink":"/r/indianrailways/comments/fff666/ticket_available/ggg777/"}}
    """.trimIndent()

    private val autoModRemovedPost = """
        {"kind":"t3","data":{"id":"hhh888","name":"t3_hhh888","subreddit":"indianrailways","author":"traveller",
         "title":"Removed outright","selftext":"","is_self":true,"thumbnail":"self",
         "banned_by":"AutoModerator","removed":true,"removed_by_category":"moderator",
         "num_reports":0,"user_reports":[],"mod_reports":[],
         "permalink":"/r/indianrailways/comments/hhh888/removed_outright/"}}
    """.trimIndent()

    private val postGuidancePost = """
        {"kind":"t3","data":{"id":"bbb222","name":"t3_bbb222","subreddit":"indianrailways","author":"newcomer",
         "title":"Held by post guidance","selftext":"","is_self":true,"thumbnail":"self",
         "num_reports":0,"user_reports":[],"mod_reports":[],"report_reasons":[],
         "removed":false,"banned_by":null,"removed_by_category":null,
         "permalink":"/r/indianrailways/comments/bbb222/held/"}}
    """.trimIndent()

    @Test
    fun aPostAutoModeratorFilteredIsHeldByAutoModeratorNotRemoved() {
        val item = item(autoModFilteredPost)
        assertTrue(item.details.heldByAutoModerator)
        assertNull(item.details.removedBy)
        assertNull(moderatorQueueItemRemovalLine(item))
        assertTrue(moderatorQueueItemIsHeldWithoutReports(item))
        assertEquals(heldByAutoModerator, moderatorQueueItemHeldExplanation(item, heldWithoutReports = true))
        // The Removed list does not ask about reports; AutoModerator is still named.
        assertEquals(heldByAutoModerator, moderatorQueueItemHeldExplanation(item, heldWithoutReports = false))
    }

    @Test
    fun aCommentAutoModeratorFilteredIsHeldByAutoModerator() {
        val item = item(autoModFilteredComment)
        assertTrue(item.details.heldByAutoModerator)
        assertNull(moderatorQueueItemRemovalLine(item))
    }

    @Test
    fun aPostAutoModeratorRemovedStaysRemovedByAutoModerator() {
        val item = item(autoModRemovedPost)
        assertFalse(item.details.heldByAutoModerator)
        assertEquals("Removed by AutoModerator", moderatorQueueItemRemovalLine(item)?.s)
        assertNull(moderatorQueueItemHeldExplanation(item, heldWithoutReports = false))
    }

    @Test
    fun aPostWithoutAutoModeratorKeepsTheUnnamedExplanation() {
        val item = item(postGuidancePost)
        assertFalse(item.details.heldByAutoModerator)
        assertEquals(heldByFiltersOrGuidance, moderatorQueueItemHeldExplanation(item, heldWithoutReports = true))
        assertNull(moderatorQueueItemHeldExplanation(item, heldWithoutReports = false))
    }
}
