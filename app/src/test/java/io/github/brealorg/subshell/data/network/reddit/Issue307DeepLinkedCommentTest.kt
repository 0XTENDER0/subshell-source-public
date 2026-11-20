package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.network.NetworkBearerTokenProvider
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.model.Comment
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #307 (owner's Pixel 11): a notification for a reply 9 levels down opened
 * the post and said "Couldn't find this comment". Reddit sends at most 8
 * parents above a linked comment, so the line started at a reply of the top
 * comment, with depth 0; the thread took it for a second top comment (a
 * duplicate row) and the linked comment never got a place. The line now
 * reaches the top comment (ISSUE307_A_DEEP_LINKED_COMMENT_REACHES_THE_TOP_COMMENT_V1),
 * and a line that still starts halfway down goes under its parent when the
 * parent is in the thread (ISSUE307_A_LINE_FROM_HALFWAY_DOWN_GOES_UNDER_ITS_PARENT_V1),
 * which also mends a thread stored that way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue307DeepLinkedCommentTest {
    /** One line of replies, c0 (top comment) to c[last]. */
    private val last = 11
    private val requests = mutableListOf<String>()
    private var failParentReads = false

    private fun thing(index: Int, top: Int, target: Int): String {
        val parent = if (index == 0) "t3_p1" else "t1_c${index - 1}"
        val replies = if (index < target) {
            """{"kind":"Listing","data":{"children":[${thing(index + 1, top, target)}]}}"""
        } else {
            "\"\""
        }
        return """{"kind":"t1","data":{"id":"c$index","name":"t1_c$index","author":"u$index",""" +
            """"body":"reply $index","depth":${index - top},"parent_id":"$parent","replies":$replies}}"""
    }

    /** Reddit's answer to comment=c<n>&context=8: up to 8 parents, depth counted from the first. */
    private fun focusedAnswer(target: Int): String {
        val top = (target - 8).coerceAtLeast(0)
        return """[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"p1"}}]}},""" +
            """{"kind":"Listing","data":{"children":[${thing(top, top, target)}]}}]"""
    }

    private val client = SubshellHttpClient(
        callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url
            requests += url.encodedQuery.orEmpty()
            val target = url.queryParameter("comment")!!.removePrefix("c").toInt()
            val refused = failParentReads && requests.size > 1
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (refused) 500 else 200).message("answer")
                .body(
                    (if (refused) "{}" else focusedAnswer(target))
                        .toResponseBody("application/json".toMediaType()),
                )
                .build()
        }.build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        json = SubshellNetworkJson.providerJson,
        userAgent = "subshell-test",
        bearerTokenProvider = NetworkBearerTokenProvider { "bearer-for-tests" },
    )

    private fun linked(context: Int = 8): List<Comment> = runBlocking {
        val result = RedditCommentNetworkDataSource(client).comments(
            postId = "p1",
            sort = RedditCommentSort.BEST,
            focusedCommentId = "c$last",
            context = context,
        )
        (result as NetworkResult.Success).value.comments
    }

    private fun List<Comment>.flatten(): List<Comment> = flatMap { comment -> listOf(comment) + comment.children.flatten() }

    @Test
    fun aDeepLinkedCommentComesWithTheWholeLineFromTheTopComment() {
        val comments = linked()

        assertEquals(listOf("t1_c0"), comments.map { it.id })
        assertEquals(0, comments.single().depth)
        val target = comments.flatten().single { it.id == "t1_c$last" }
        assertEquals(last, target.depth)
        assertEquals((0..last).map { "t1_c$it" }, comments.flatten().map { it.id }.filter { it.startsWith("t1_") })
        // The line from c3 down, then the line above c3's parent.
        assertEquals(2, requests.size)
        assertTrue(requests[1], requests[1].contains("comment=c2"))
    }

    @Test
    fun aFailedReadAboveKeepsTheLineRedditSent() {
        failParentReads = true

        val comments = linked()

        assertEquals(listOf("t1_c3"), comments.map { it.id })
        assertNotNull(comments.flatten().singleOrNull { it.id == "t1_c$last" })
    }

    @Test
    fun aReadWithoutParentsAsksOnce() {
        linked(context = 0)

        assertEquals(1, requests.size)
    }

    private fun comment(index: Int, depth: Int, children: List<Comment> = emptyList()) = Comment(
        id = "t1_c$index",
        author = "u$index",
        age = "1h",
        body = "reply $index",
        score = 1,
        depth = depth,
        accent = 0L,
        parentId = if (index == 0) "t3_p1" else "t1_c${index - 1}",
        children = children,
    )

    /** c[from]..c[to] as one line, depths from [firstDepth]. */
    private fun line(from: Int, to: Int, firstDepth: Int): Comment =
        comment(from, firstDepth, if (from < to) listOf(line(from + 1, to, firstDepth + 1)) else emptyList())

    @Test
    fun aLineFromHalfwayDownGoesUnderItsParentInTheThread() {
        // The thread as the full load has it: c0 with its reply c1 and a sibling.
        val thread = listOf(comment(0, 0, listOf(comment(1, 1), comment(1, 1).copy(id = "t1_s1", parentId = "t1_c0"))))

        val merged = mergeFocusedCommentContext(
            current = thread,
            focused = listOf(line(1, 9, firstDepth = 0)),
            focusedCommentId = "t1_c9",
        )

        assertEquals(listOf("t1_c0"), merged.map { it.id })
        val ids = merged.flatten().map { it.id }.filter { it.startsWith("t1_") }
        assertEquals(ids.distinct(), ids)
        assertEquals(9, merged.flatten().single { it.id == "t1_c9" }.depth)
        assertTrue(merged.flatten().any { it.id == "t1_s1" })
    }

    @Test
    fun aThreadStoredWithTheLineAsATopCommentIsMended() {
        val stored = listOf(comment(0, 0, listOf(comment(1, 1))), line(1, 9, firstDepth = 0))

        val merged = mergeFocusedCommentContext(current = stored, focused = listOf(line(1, 9, firstDepth = 0)))

        assertEquals(listOf("t1_c0"), merged.map { it.id })
        assertEquals(9, merged.flatten().single { it.id == "t1_c9" }.depth)
    }

    private fun continueUnder(index: Int, depth: Int) =
        io.github.brealorg.subshell.model.continueThreadNode("t1_c$index", depth, 0L)

    @Test
    fun theThreadAsThePixelStoredItShowsEveryCommentOnce() {
        // The stored thread from the owner's Pixel (1xsz8kz): c0 with c1 and
        // a continue node, and c1's line as a second top comment.
        val stored = listOf(
            comment(0, 0, listOf(comment(1, 1, listOf(continueUnder(1, 2))))),
            comment(1, 0, listOf(comment(2, 1, listOf(comment(3, 2, listOf(continueUnder(3, 3))))), continueUnder(1, 1))),
        )
        val mended = rehomedUnderTheirParents(stored)
        val rows = io.github.brealorg.subshell.ui.screens.visibleCommentRows(mended, emptySet())
        assertEquals(rows.map { it.key }.distinct(), rows.map { it.key })
        assertEquals(1, rows.count { it.key == "comment:t1_c1" })

        // Then the focused read, merged over the stored thread as the loader does.
        val merged = mergeFocusedCommentContext(
            current = stored,
            focused = listOf(line(0, 9, firstDepth = 0)),
            preferFocusedFields = true,
            focusedCommentId = "t1_c9",
        )
        val ids = merged.flatten().map { it.id }
        assertEquals(ids.distinct(), ids)
        assertEquals(listOf("t1_c0"), merged.map { it.id })
    }

    @Test
    fun aLineWhoseParentIsNotInTheThreadStaysWhereItIs() {
        val merged = mergeFocusedCommentContext(
            current = listOf(comment(0, 0).copy(id = "t1_other", parentId = "t3_p1")),
            focused = listOf(line(4, 9, firstDepth = 0)),
        )

        assertEquals(listOf("t1_other", "t1_c4"), merged.map { it.id })
    }
}
