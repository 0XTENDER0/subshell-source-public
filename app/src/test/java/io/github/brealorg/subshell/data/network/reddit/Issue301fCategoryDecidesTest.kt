package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.ui.s
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.R
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.ui.components.moderatorQueueItemHeldExplanation
import io.github.brealorg.subshell.ui.components.moderatorQueueItemRemovalLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE301F_A_POSTS_CATEGORY_DECIDES_V1 (#301): a post's removed_by_category
 * says who acted. An AutoModerator removal is "moderator" even when `removed`
 * reads false (fsvreddit's Spam Src Spotter notes it), so it is removed, not
 * held; Reddit's filters (Crowd Control, ban evasion) hold a post as "reddit".
 * A comment has no category and keeps the banned_by + removed rule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Issue301fCategoryDecidesTest {
    private val now = 1_794_780_000L

    private fun item(child: String) =
        SubshellNetworkJson.providerJson.decodeFromString(
            RedditReadListingDto.serializer(),
            """{"kind":"Listing","data":{"after":null,"children":[$child]}}""",
        ).toModeratorQueuePage("astronotyet", SubshellNetworkJson.providerJson, now).items.single()

    private fun post(category: String?, bannedBy: String, removed: Boolean) = """
        {"kind":"t3","data":{"id":"p1","name":"t3_p1","subreddit":"astronotyet","author":"poster",
         "title":"A post","selftext":"","is_self":true,"thumbnail":"self","created_utc":${now - 60}.0,
         "banned_by":$bannedBy,"removed":$removed,"removed_by_category":${category?.let { "\"$it\"" } ?: "null"},
         "num_reports":0,"user_reports":[],"mod_reports":[],"permalink":"/r/astronotyet/comments/p1/a_post/"}}
    """.trimIndent()

    @Test
    fun aPostAutoModeratorRemovedIsRemovedEvenWhenRemovedReadsFalse() {
        val item = item(post(category = "moderator", bannedBy = "\"AutoModerator\"", removed = false))
        assertFalse(item.details.heldByAutoModerator)
        assertFalse(item.details.heldByRedditFilter)
        assertEquals("Removed by AutoModerator", moderatorQueueItemRemovalLine(item)?.s)
        assertNull(moderatorQueueItemHeldExplanation(item, heldWithoutReports = true))
    }

    @Test
    fun aPostRedditsFiltersHeldIsHeldByRedditsFilters() {
        val item = item(post(category = "reddit", bannedBy = "true", removed = false))
        assertTrue(item.details.heldByRedditFilter)
        assertFalse(item.details.heldByAutoModerator)
        assertNull(moderatorQueueItemRemovalLine(item))
        assertEquals(UiText.Resource(R.string.comp_held_by_reddit), moderatorQueueItemHeldExplanation(item, heldWithoutReports = true))
    }

    @Test
    fun aPostAutoModeratorFilteredStaysHeldByAutoModerator() {
        val item = item(post(category = "automod_filtered", bannedBy = "\"AutoModerator\"", removed = false))
        assertTrue(item.details.heldByAutoModerator)
        assertNull(moderatorQueueItemRemovalLine(item))
    }

    @Test
    fun aCommentWithoutACategoryKeepsTheOldRule() {
        assertTrue(moderatorQueueItemHeldByAutoModerator(null, "AutoModerator", removed = false))
        assertFalse(moderatorQueueItemHeldByAutoModerator(null, "AutoModerator", removed = true))
        assertTrue(moderatorQueueItemHeldByRedditFilter(null, bannedByReddit = true, removed = false))
        assertFalse(moderatorQueueItemHeldByRedditFilter("moderator", bannedByReddit = true, removed = false))
    }
}
