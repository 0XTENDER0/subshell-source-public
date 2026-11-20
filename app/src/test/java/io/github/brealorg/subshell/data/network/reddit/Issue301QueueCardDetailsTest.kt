package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.ui.s
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.ui.components.moderatorQueueItemByline
import io.github.brealorg.subshell.ui.components.moderatorQueueItemHasPicture
import io.github.brealorg.subshell.ui.components.moderatorQueueItemIsHeldWithoutReports
import io.github.brealorg.subshell.ui.components.moderatorQueueItemReasons
import io.github.brealorg.subshell.ui.components.moderatorQueueItemRemovalLine
import io.github.brealorg.subshell.ui.components.moderatorQueueThumbnailPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE301_QUEUE_CARD_SHOWS_WHY_AND_WHAT_V1 (#301, u/vpsj): a queue card shows why the
 * item is there (report reasons with who made them; "Held for review" for an
 * unreported item), the removal reason in the Removed list, the post's picture or a
 * comment's first picture, flair, age and the first lines of a post's text. The JSON
 * follows what Reddit answered for r/subshell's lists on 2026-11-13 (ids changed).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Issue301QueueCardDetailsTest {
    private val now = 1_794_600_000L

    private fun page(vararg children: String) =
        SubshellNetworkJson.providerJson.decodeFromString(
            RedditReadListingDto.serializer(),
            """{"kind":"Listing","data":{"after":null,"children":[${children.joinToString(",")}]}}""",
        ).toModeratorQueuePage("subshell", SubshellNetworkJson.providerJson, now)

    private val reportedImagePost = """
        {"kind":"t3","data":{"id":"aaa111","name":"t3_aaa111","subreddit":"subshell","author":"poster",
         "title":"A picture","selftext":"","created_utc":${now - 3 * 3600}.0,"link_flair_text":"Meme",
         "author_flair_text":"Regular","post_hint":"image","domain":"i.redd.it",
         "url_overridden_by_dest":"https://i.redd.it/pic1.jpeg",
         "preview":{"images":[{"source":{"url":"https://preview.redd.it/pic1.jpeg?width=640&amp;s=abc"}}]},
         "num_reports":3,"user_reports":[["Spam",2,false,false]],
         "mod_reports":[["Review image: low effort","AutoModerator"]],
         "permalink":"/r/subshell/comments/aaa111/a_picture/"}}
    """.trimIndent()

    private val heldTextPost = """
        {"kind":"t3","data":{"id":"bbb222","name":"t3_bbb222","subreddit":"subshell","author":"newcomer",
         "title":"Automod-filter-test","selftext":"Just testing.\n\nSecond line","created_utc":${now - 120}.0,
         "is_self":true,"thumbnail":"self","num_reports":0,"user_reports":[],"mod_reports":[],
         "report_reasons":[],"removed":false,"banned_by":null,"removed_by_category":null,
         "permalink":"/r/subshell/comments/bbb222/automodfiltertest/"}}
    """.trimIndent()

    private val commentWithStoredGif = """
        {"kind":"t1","data":{"name":"t1_ccc333","subreddit":"subshell","author":"replier",
         "body":"Mohahahah\n\n*Processing img gif1...*","created_utc":${now - 86400}.0,
         "link_title":"Subshell 1.0.2 is out","num_reports":1,"user_reports":[],
         "mod_reports":[["Review image comment","AutoModerator"]],
         "media_metadata":{"gif1":{"status":"valid","e":"AnimatedImage","m":"image/gif",
           "p":[{"x":108,"y":58,"u":"https://preview.redd.it/gif1.gif?width=108&amp;s=a"},
                {"x":320,"y":173,"u":"https://preview.redd.it/gif1.gif?width=320&amp;s=b"},
                {"x":640,"y":346,"u":"https://preview.redd.it/gif1.gif?width=640&amp;s=c"}],
           "s":{"x":800,"y":432,"gif":"https://i.redd.it/gif1.gif","u":"https://preview.redd.it/gif1.gif?s=d"}}},
         "permalink":"/r/subshell/comments/aaa111/a_picture/ccc333/"}}
    """.trimIndent()

    private val commentWithPastedAddress = """
        {"kind":"t1","data":{"name":"t1_ddd444","subreddit":"subshell","author":"paster",
         "body":"https://preview.redd.it/xyz.jpeg?width=1080&amp;format=pjpg&amp;s=e1",
         "num_reports":1,"user_reports":[["Off topic",1]],"mod_reports":[],
         "permalink":"/r/subshell/comments/aaa111/a_picture/ddd444/"}}
    """.trimIndent()

    private val removedComment = """
        {"kind":"t1","data":{"name":"t1_eee555","subreddit":"subshell","author":"rude",
         "body":"Something rude","num_reports":-1,"user_reports":[],"mod_reports":[],
         "banned_by":"brealorg","ban_note":"remove not spam","mod_reason_by":"brealorg",
         "mod_reason_title":"Test removal reason","removed":true,
         "permalink":"/r/subshell/comments/aaa111/a_picture/eee555/"}}
    """.trimIndent()

    @Test
    fun aReportedPostShowsItsReasonsPictureFlairAndAge() {
        val item = page(reportedImagePost).items.single()
        assertEquals(
            listOf("AutoModerator: Review image: low effort", "Reported: Spam (2)"),
            moderatorQueueItemReasons(item).map { it.s },
        )
        assertFalse(moderatorQueueItemIsHeldWithoutReports(item))
        // QUEUEBYLINE01: the age before the user flair, so a long flair cannot hide it.
        assertEquals("r/subshell · 3h · u/poster (Regular) · Meme", moderatorQueueItemByline(item))
        assertEquals(PostKind.IMAGE, item.details.post?.kind)
        assertTrue(moderatorQueueItemHasPicture(item))
        assertEquals(item.details.post, moderatorQueueThumbnailPost(item))
    }

    @Test
    fun anUnreportedItemInTheQueueIsHeldAndShowsItsText() {
        val item = page(heldTextPost).items.single()
        assertTrue(moderatorQueueItemIsHeldWithoutReports(item))
        assertEquals(emptyList<String>(), moderatorQueueItemReasons(item).map { it.s })
        assertEquals("Just testing. Second line", item.details.snippet)
        // A text post has no picture.
        assertFalse(moderatorQueueItemHasPicture(item))
        assertNull(moderatorQueueThumbnailPost(item))
    }

    @Test
    fun aCommentShowsItsStoredPictureAndThePostItIsOn() {
        val item = page(commentWithStoredGif).items.single()
        assertEquals(RedditModeratorQueueKind.COMMENT, item.kind)
        assertEquals("Mohahahah · Image", item.summary)
        assertEquals("Subshell 1.0.2 is out", item.details.linkTitle)
        assertEquals("https://preview.redd.it/gif1.gif?width=320&s=b", item.details.commentImageUrl)
        assertEquals(listOf("AutoModerator: Review image comment"), moderatorQueueItemReasons(item).map { it.s })
        val picture = moderatorQueueThumbnailPost(item)!!
        assertEquals(PostKind.GIF, picture.kind)
        assertEquals("t1_ccc333", picture.id)
    }

    @Test
    fun aPictureAddressInACommentIsShownAsThePictureNotAsTheTitle() {
        val item = page(commentWithPastedAddress).items.single()
        assertEquals("Image comment", item.summary)
        assertEquals(
            "https://preview.redd.it/xyz.jpeg?width=1080&format=pjpg&s=e1",
            item.details.commentImageUrl,
        )
        assertEquals(listOf("Reported: Off topic"), moderatorQueueItemReasons(item).map { it.s })
    }

    @Test
    fun aRemovedItemSaysWhoRemovedItAndWhy() {
        val item = page(removedComment).items.single()
        assertEquals("Removed by u/brealorg · Test removal reason", moderatorQueueItemRemovalLine(item)?.s)
        assertFalse(moderatorQueueItemHasPicture(item))
    }

    @Test
    fun aPostTextThatRepeatsTheTitleIsNotShownTwice() {
        val item = page(heldTextPost.replace("Just testing.\\n\\nSecond line", "automod-filter-test")).items.single()
        assertNull(item.details.snippet)
    }

    @Test
    fun aChildTheQueueCannotReadDoesNotCostTheOthers() {
        val items = page("""{"kind":"t3","data":{"name":42}}""", heldTextPost).items
        assertEquals(listOf("t3_bbb222"), items.map { it.fullname })
    }
}
