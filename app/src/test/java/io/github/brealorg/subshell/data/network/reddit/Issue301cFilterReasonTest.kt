package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.ui.s
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.R
import io.github.brealorg.subshell.data.account.RedditModeratorHeldReason
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItemDetails
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.account.RedditModeratorQueueList
import io.github.brealorg.subshell.data.account.RedditModeratorReportDetails
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.ui.components.moderatorQueueItemHeldExplanation
import io.github.brealorg.subshell.ui.components.moderatorQueueItemRemovalLine
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE301C_REDDIT_FILTER_HELD_IS_NAMED_V1 and
 * ISSUE301C_A_FILTER_REASON_COMES_FROM_THE_MOD_LOG_V1 (#301, u/vpsj): a filter
 * leaves no reason on the item it holds, but writes it to the mod log. The
 * held comment follows u/vpsj's JSON of 2026-11-15 (r/indianrailways, author
 * and text changed); the "reddit" log entry is the one the API returned for
 * that comment in u/vpsj's r/indianrailways log (2026-11-16: details
 * "Automatic Filter", description "Identified by the abuse and harassment
 * filter"); the AutoMod entry follows r/astronotyet's mod log as
 * the app read it on 2026-11-15 (the rule's reason in `details`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Issue301cFilterReasonTest {
    private val now = 1_794_770_000L
    private val json = SubshellNetworkJson.providerJson

    private val redditHeldComment = """
        {"kind":"t1","data":{"id":"19soq6ozp73yb","name":"t1_19soq6ozp73yb","subreddit":"indianrailways",
         "author":"traveller","body":"A comment the filter held.","created_utc":${now - 600}.0,
         "link_id":"t3_1xwovey","link_title":"A video post","approved":false,
         "banned_by":true,"banned_at_utc":${now - 597},"ban_note":"remove not spam",
         "removed":false,"spam":false,"removal_reason":null,"mod_reason_by":null,"mod_reason_title":null,
         "mod_note":null,"num_reports":0,"user_reports":[],"mod_reports":[],"report_reasons":[],
         "permalink":"/r/indianrailways/comments/1xwovey/a_video_post/19soq6ozp73yb/"}}
    """.trimIndent()

    private val autoModHeldComment = """
        {"kind":"t1","data":{"id":"19sxyzabc12de","name":"t1_19sxyzabc12de","subreddit":"indianrailways",
         "author":"traveller","body":"An image comment.","created_utc":${now - 900}.0,
         "link_id":"t3_1xwovey","link_title":"A video post",
         "banned_by":"AutoModerator","ban_note":"remove not spam","removed":false,"spam":false,
         "num_reports":0,"user_reports":[],"mod_reports":[],
         "permalink":"/r/indianrailways/comments/1xwovey/a_video_post/19sxyzabc12de/"}}
    """.trimIndent()

    private val reportedPost = """
        {"kind":"t3","data":{"id":"1xwreport","name":"t3_1xwreport","subreddit":"indianrailways","author":"poster",
         "title":"Reported post","selftext":"","is_self":true,"thumbnail":"self","removed":false,
         "num_reports":1,"user_reports":[["Spam",1,false,false]],"mod_reports":[],
         "permalink":"/r/indianrailways/comments/1xwreport/reported_post/"}}
    """.trimIndent()

    private fun listing(vararg children: String) =
        """{"kind":"Listing","data":{"after":null,"children":[${children.joinToString(",")}]}}"""

    private fun logEntry(mod: String, target: String, details: String?, description: String?) =
        """{"kind":"modaction","data":{"id":"ModAction_${target}_$mod","mod":"$mod","action":"removecomment",
           "subreddit":"indianrailways","target_fullname":"$target","target_author":"traveller",
           "details":${details?.let { "\"$it\"" } ?: "null"},"description":${description?.let { "\"$it\"" } ?: "null"},
           "created_utc":${now - 597}.0}}"""

    private val harassmentEntry = logEntry(
        "reddit",
        "t1_19soq6ozp73yb",
        "Automatic Filter",
        "Identified by the abuse and harassment filter",
    )
    // Seen in r/astronotyet's mod log on 2026-11-15: AutoModerator's rule reason is in `details`.
    // Also in that log: a Banned Domain removal.
    private val bannedDomainEntry = logEntry(
        "reddit",
        "t1_19sookrvbrg5f",
        "Banned Domain",
        "Links to a URL not allowed on Reddit",
    )
    private val autoModEntry = logEntry(
        "AutoModerator",
        "t1_19sxyzabc12de",
        "Review image comment: meme/reaction or genuine image",
        null,
    )

    private val asked = mutableListOf<String>()

    private fun source(
        queue: String,
        log: (moderator: String?) -> Pair<Int, String>,
    ) = RedditModerationNetworkDataSource(
        SubshellHttpClient(
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath
                asked += path + (request.url.queryParameter("mod")?.let { "?mod=$it" } ?: "")
                val (code, body) = when {
                    path.endsWith("/about/modqueue") -> 200 to queue
                    path.endsWith("/about/log") -> log(request.url.queryParameter("mod"))
                    else -> 404 to "{}"
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("answer")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }.build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            json = json,
            userAgent = "subshell-test",
        ),
    )

    private fun itemsOf(result: NetworkResult<*>): List<RedditModeratorQueueItem> {
        assertTrue("$result", result is NetworkResult.Success)
        @Suppress("UNCHECKED_CAST")
        return ((result as NetworkResult.Success<*>).value as io.github.brealorg.subshell.data.network.NetworkPage<RedditModeratorQueueItem>).items
    }

    @Test
    fun aCommentRedditsFilterHeldIsNamedAndNotCalledRemoved() {
        val item = listingItems(listing(redditHeldComment)).single()
        assertTrue(item.details.heldByRedditFilter)
        assertFalse(item.details.heldByAutoModerator)
        assertNull(item.details.removedBy)
        assertNull(moderatorQueueItemRemovalLine(item))
        assertEquals(
            UiText.Resource(R.string.comp_held_by_reddit),
            moderatorQueueItemHeldExplanation(item, heldWithoutReports = true),
        )
    }

    @Test
    fun theQueueReadsEachFiltersReasonFromTheModLog() = runBlocking {
        val result = source(listing(redditHeldComment, autoModHeldComment, reportedPost)) { moderator ->
            200 to when (moderator) {
                "AutoModerator" -> listing(autoModEntry)
                null -> listing(bannedDomainEntry, harassmentEntry, autoModEntry)
                else -> listing()
            }
        }.modQueue("indianrailways")
        val items = itemsOf(result).associateBy { it.fullname }

        assertEquals(
            RedditModeratorHeldReason(byAutoModerator = false, reason = "Automatic Filter – Identified by the abuse and harassment filter"),
            items.getValue("t1_19soq6ozp73yb").details.heldReason,
        )
        assertEquals(
            RedditModeratorHeldReason(byAutoModerator = true, reason = "Review image comment: meme/reaction or genuine image"),
            items.getValue("t1_19sxyzabc12de").details.heldReason,
        )
        assertNull(items.getValue("t3_1xwreport").details.heldReason)
        assertEquals(
            listOf(
                "/r/indianrailways/about/modqueue",
                "/r/indianrailways/about/log?mod=AutoModerator",
                "/r/indianrailways/about/log",
            ),
            asked,
        )
        assertEquals(
            UiText.Resource(R.string.comp_held_by_reddit_reason, "Automatic Filter – Identified by the abuse and harassment filter"),
            moderatorQueueItemHeldExplanation(items.getValue("t1_19soq6ozp73yb"), heldWithoutReports = true),
        )
        assertEquals(
            UiText.Resource(R.string.comp_held_by_automoderator_reason, "Review image comment: meme/reaction or genuine image"),
            moderatorQueueItemHeldExplanation(items.getValue("t1_19sxyzabc12de"), heldWithoutReports = true),
        )
    }

    @Test
    fun aFailedLogReadLeavesTheQueueAsRedditSentIt() = runBlocking {
        val result = source(listing(redditHeldComment, autoModHeldComment)) { 403 to """{"message":"Forbidden","error":403}""" }
            .modQueue("indianrailways")
        val items = itemsOf(result)
        assertEquals(2, items.size)
        assertTrue(items.all { it.details.heldReason == null })
        assertEquals(
            UiText.Resource(R.string.comp_held_by_automoderator),
            moderatorQueueItemHeldExplanation(items.single { it.details.heldByAutoModerator }, heldWithoutReports = true),
        )
    }

    @Test
    fun aPageWithoutHeldItemsReadsNoLog() = runBlocking {
        source(listing(reportedPost)) { error("no log read expected") }.modQueue("indianrailways")
        assertEquals(listOf("/r/indianrailways/about/modqueue"), asked)
    }

    @Test
    fun anEntryOfAnotherItemOrAHumanModeratorGivesNoReason() = runBlocking {
        val human = logEntry("somemod", "t1_19soq6ozp73yb", null, "Rule 2")
        val other = logEntry("reddit", "t1_other", "Automatic Filter", "Identified by the abuse and harassment filter")
        val items = itemsOf(source(listing(redditHeldComment)) { 200 to listing(human, other) }.modQueue("indianrailways"))
        assertNull(items.single().details.heldReason)
    }

    // ISSUE301E_A_NAMED_REMOVER_IS_NOT_CALLED_A_FILTER_V1: u/vpsj's moderator app
    // removed a video post into the queue (r/astronotyet, 2026-11-16); the card
    // said both "Held by Reddit's filters or post guidance" and "Removed by".
    private val appRemovedPost = """
        {"kind":"t3","data":{"id":"1xyvideo","name":"t3_1xyvideo","subreddit":"astronotyet","author":"subshell_dev",
         "title":"subshell test - held video(ignore)","is_video":true,"domain":"v.redd.it","url":"https://v.redd.it/abc",
         "created_utc":${now - 1_080}.0,"banned_by":"monitor-test-app","removed":true,"spam":false,
         "num_reports":0,"user_reports":[],"mod_reports":[],
         "permalink":"/r/astronotyet/comments/1xyvideo/subshell_test/"}}
    """.trimIndent()

    @Test
    fun anItemAModeratorAppRemovedSaysRemovedByAndNotHeldByAFilter() = runBlocking {
        val items = itemsOf(source(listing(appRemovedPost)) { error("no log read expected") }.modQueue("astronotyet"))
        val item = items.single()
        assertEquals("Removed by u/monitor-test-app", moderatorQueueItemRemovalLine(item)?.s)
        assertNull(moderatorQueueItemHeldExplanation(item, heldWithoutReports = true))
        assertEquals(listOf("/r/astronotyet/about/modqueue"), asked)
    }

    @Test
    fun theReasonIsTheDescriptionOrDetailsThatAreMoreThanAnActionWord() {
        assertEquals("Automatic Filter – Identified by the abuse and harassment filter", moderatorLogReasonText("Identified by the abuse and harassment filter", "Automatic Filter"))
        // Evasion Guard (fsvreddit) reads ban evasion entries as details "Ban Evasion", description "Higher accuracy …".
        assertEquals("Ban Evasion – Higher accuracy", moderatorLogReasonText("Higher accuracy", "Ban Evasion"))
        assertEquals("Identified by the spam filter", moderatorLogReasonText("Identified by the spam filter", "remove"))
        assertEquals("Review image comment", moderatorLogReasonText(null, "Review image comment"))
        assertEquals("Review image comment", moderatorLogReasonText("  ", "  Review   image comment "))
        assertNull(moderatorLogReasonText(null, "remove"))
        assertNull(moderatorLogReasonText("", "confirm_spam"))
    }

    @Test
    fun anUnsignedHeldItemTakesAReasonOnlyFromRedditOrAutoModerator() {
        val unsigned = RedditModeratorQueueItem(
            fullname = "t3_abc123",
            kind = RedditModeratorQueueKind.POST,
            communityName = "indianrailways",
            author = "poster",
            summary = "Held post",
            reportDetails = RedditModeratorReportDetails(userReports = emptyList(), moderatorReports = emptyList(), totalCount = 0),
            details = RedditModeratorQueueItemDetails(),
        )
        val entries = SubshellNetworkJson.providerJson.decodeFromString(
            RedditModerationLogListingDto.serializer(),
            listing(logEntry("reddit", "t3_abc123", "Automatic Filter", "Identified by the spam filter")),
        ).data.children.mapNotNull { it?.data }
        assertEquals(
            RedditModeratorHeldReason(byAutoModerator = false, reason = "Automatic Filter – Identified by the spam filter"),
            moderatorLogHeldReason(entries, unsigned),
        )
        val withReason = runBlocking {
            moderatorQueueItemsWithFilterReasons(listOf(unsigned), RedditModeratorQueueList.QUEUE) { _, _ -> ModeratorLogPage(entries, null) }
        }.single()
        assertEquals("Automatic Filter – Identified by the spam filter", withReason.details.heldReason?.reason)
    }

    private fun listingItems(body: String): List<RedditModeratorQueueItem> =
        json.decodeFromString(RedditReadListingDto.serializer(), body)
            .toModeratorQueuePage("indianrailways", json, now).items
}
