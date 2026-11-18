package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItemDetails
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.account.RedditModeratorQueueList
import io.github.brealorg.subshell.data.account.RedditModeratorReportDetails
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ISSUE301D_THE_LOG_IS_READ_BACK_TO_THE_OLDEST_HELD_ITEM_V1 (#301): in a busy
 * community AutoModerator fills a mod log page of 100 in hours, so an item
 * held since yesterday has its entry further back. The log is read page by
 * page until every held item has its entry, the log is older than the oldest
 * held item, or five pages were read.
 */
class Issue301dLogPagesTest {
    private val now = 1_794_770_000L
    private val reads = mutableListOf<Pair<String?, String?>>()

    private fun held(fullname: String, createdSecondsAgo: Long?) = RedditModeratorQueueItem(
        fullname = fullname,
        kind = RedditModeratorQueueKind.COMMENT,
        communityName = "indianrailways",
        author = "traveller",
        summary = "Image comment",
        reportDetails = RedditModeratorReportDetails(userReports = emptyList(), moderatorReports = emptyList(), totalCount = 0),
        details = RedditModeratorQueueItemDetails(
            heldByAutoModerator = true,
            createdEpochSeconds = createdSecondsAgo?.let { now - it },
        ),
    )

    private fun entry(target: String, secondsAgo: Long, mod: String = "AutoModerator") = RedditModerationLogDataDto(
        id = "ModAction_${target}_$secondsAgo",
        mod = mod,
        action = "removecomment",
        targetFullname = target,
        details = "Review image comment",
        createdUtc = (now - secondsAgo).toDouble(),
    )

    /** 100 AutoModerator entries for other comments, newest first, from [fromSecondsAgo] back. */
    private fun busyPage(fromSecondsAgo: Long) = (0 until 100).map { index -> entry("t1_other$index", fromSecondsAgo + index * 60L) }

    private fun reasons(items: List<RedditModeratorQueueItem>, pages: Map<String?, ModeratorLogPage>) = runBlocking {
        moderatorQueueItemsWithFilterReasons(items, RedditModeratorQueueList.QUEUE) { moderator, after ->
            reads += moderator to after
            pages[after]
        }
    }

    @Test
    fun anItemHeldBeforeTheFirstPageFindsItsEntryOnALaterPage() {
        val item = held("t1_yesterday", createdSecondsAgo = 30_000)
        val result = reasons(
            listOf(item),
            mapOf(
                null to ModeratorLogPage(busyPage(fromSecondsAgo = 60), after = "page2"),
                "page2" to ModeratorLogPage(busyPage(fromSecondsAgo = 6_100) + entry("t1_yesterday", 29_990), after = "page3"),
            ),
        ).single()

        assertEquals("Review image comment", result.details.heldReason?.reason)
        assertEquals(listOf<Pair<String?, String?>>("AutoModerator" to null, "AutoModerator" to "page2"), reads)
    }

    @Test
    fun theLogIsNotReadFurtherBackThanTheOldestHeldItem() {
        val item = held("t1_recent", createdSecondsAgo = 600)
        val result = reasons(
            listOf(item),
            mapOf(null to ModeratorLogPage(busyPage(fromSecondsAgo = 60), after = "page2")),
        ).single()

        assertNull(result.details.heldReason)
        assertEquals(1, reads.size)
    }

    @Test
    fun noMoreThanFivePagesAreRead() {
        val item = held("t1_lastweek", createdSecondsAgo = 600_000)
        val pages = (0 until 8).associate { page ->
            (if (page == 0) null else "page$page") to ModeratorLogPage(busyPage(fromSecondsAgo = page * 6_000L), after = "page${page + 1}")
        }
        val result = reasons(listOf(item), pages).single()

        assertNull(result.details.heldReason)
        assertEquals(5, reads.size)
    }

    @Test
    fun anItemWithoutATimeReadsOnePageAsBefore() {
        val item = held("t1_untimed", createdSecondsAgo = null)
        reasons(listOf(item), mapOf(null to ModeratorLogPage(busyPage(fromSecondsAgo = 60), after = "page2")))
        assertEquals(1, reads.size)
    }

    @Test
    fun aFailedLaterPageKeepsWhatTheEarlierPagesFound() {
        val found = held("t1_found", createdSecondsAgo = 30_000)
        val missing = held("t1_missing", createdSecondsAgo = 40_000)
        val result = reasons(
            listOf(found, missing),
            mapOf(null to ModeratorLogPage(busyPage(fromSecondsAgo = 60) + entry("t1_found", 29_990), after = "page2")),
        ).associateBy { it.fullname }

        assertEquals("Review image comment", result.getValue("t1_found").details.heldReason?.reason)
        assertNull(result.getValue("t1_missing").details.heldReason)
        assertEquals(2, reads.size)
    }
}
