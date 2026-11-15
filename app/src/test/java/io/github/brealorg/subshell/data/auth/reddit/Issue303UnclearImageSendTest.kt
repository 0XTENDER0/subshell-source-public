package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.chat.CHAT_WEB_TOKEN_NO_TOKEN
import io.github.brealorg.subshell.chat.RedditChatWebTokenResult
import io.github.brealorg.subshell.chat.RedditChatWebTokenSource
import io.github.brealorg.subshell.data.account.MessageReplyChannel
import io.github.brealorg.subshell.data.account.MessageReplyFailureKind
import io.github.brealorg.subshell.data.account.MessageReplyMediaAttachment
import io.github.brealorg.subshell.data.account.MessageReplyRequest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #303 and review finding 2: a Direct Chat image send answered 5xx (or 2xx
 * without an event id) may have been stored, but it was reported as a plain
 * failure with no read-back and nothing to recognise it by, so the reader
 * could send the GIF twice. It is now an unknown outcome that is read back by
 * transaction id, and an unknown result keeps the uploaded media id
 * (ISSUE303_A_SEND_WITHOUT_A_CLEAR_ANSWER_IS_READ_BACK_V1,
 * ISSUE303_AN_UNKNOWN_IMAGE_SEND_KEEPS_ITS_MEDIA_ID_V1). A failed mint reports
 * its own step (ISSUE303_CHAT_TOKEN_FAILURE_NAMES_ITS_STEP_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue303UnclearImageSendTest {
    private val fixture = MatrixTestFixture()
    private val directory = createTempDirectory("issue303-matrix").toFile()
    private val contentUri = "mxc://reddit.com/media303"

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private val stager = RedditMediaUploadSource { _, _ ->
        RedditMediaStageResult.Staged(
            file = File(directory, "picture-${System.nanoTime()}.gif").apply { writeBytes(byteArrayOf(1, 2, 3)) },
            fileName = "picture.gif",
            mimeType = "image/gif",
            sizeBytes = 3L,
        )
    }

    private fun send(tokenSource: RedditChatWebTokenSource = fixture.tokenSource) = runBlocking {
        RedditChatMatrixMediaWriter(
            tokenProvider = tokenSource,
            mediaSource = stager,
            client = fixture.client,
            ioDispatcher = Dispatchers.IO,
            sessionCache = fixture.sessionCache,
        ).send(
            MessageReplyRequest(
                accountStableId = fixture.account,
                inboxItemId = "t4_chat",
                parentFullname = "t4_chat",
                channel = MessageReplyChannel.DIRECT_CHAT,
                text = "",
                media = MessageReplyMediaAttachment(uri = "content://media/1", mimeType = "image/gif"),
                directChatParticipantUsername = fixture.participant,
            ),
            expectedProviderAccountId = fixture.providerAccountId,
        )
    }

    private fun decode(path: String): String = java.net.URLDecoder.decode(path.replace("+", "%2B"), "UTF-8")

    private fun sends() = fixture.requests.filter { decode(it.path).contains("/send/m.room.message/") }

    /** Upload, one send answered by [sendAnswer], and a sync that holds the send when [stored]. */
    private fun answering(sendAnswer: MatrixAnswer, stored: Boolean) {
        var transaction: String? = null
        fixture.respond = { request ->
            val path = decode(request.path)
            when {
                path == "/_matrix/media/v3/upload" -> MatrixAnswer.Json(200, """{"content_uri":"$contentUri"}""")
                path.contains("/send/m.room.message/") -> {
                    transaction = path.substringAfterLast('/')
                    sendAnswer
                }
                path.endsWith("/sync") -> {
                    val events = if (stored) {
                        """{"type":"m.room.message","event_id":"${'$'}stored","sender":"${fixture.ownMatrixUserId}",""" +
                            """"unsigned":{"transaction_id":"$transaction"}}"""
                    } else {
                        ""
                    }
                    MatrixAnswer.Json(
                        200,
                        """{"rooms":{"join":{"${fixture.roomId}":{"timeline":{"events":[$events]}}}}}""",
                    )
                }
                else -> MatrixAnswer.Json(404, "{}")
            }
        }
    }

    @Test
    fun aServerErrorWhoseImageWasStoredIsReadBackAsSent() {
        fixture.warmCache()
        answering(MatrixAnswer.Json(502, "{}"), stored = true)

        val accepted = send() as AuthenticatedRedditMessageReplyResult.Accepted

        assertEquals("\$stored", accepted.sentMatrixEventId)
        assertEquals("media303", accepted.sentMatrixMediaId)
        assertEquals(1, sends().size)
    }

    @Test
    fun aServerErrorNotFoundIsUnknownKeepsTheMediaIdAndIsNeverSentAgain() {
        fixture.warmCache()
        answering(MatrixAnswer.Json(503, "{}"), stored = false)

        val failed = send() as AuthenticatedRedditMessageReplyResult.Failed

        assertEquals(MessageReplyFailureKind.OUTCOME_UNKNOWN, failed.kind)
        assertEquals(503, failed.statusCode)
        assertEquals("media303", failed.sentMatrixMediaId)
        assertEquals(null, failed.sentMatrixEventId)
        assertEquals(1, sends().size)
        val afterSend = fixture.requests.dropWhile { !decode(it.path).contains("/send/") }.drop(1)
        assertTrue(afterSend.toString(), afterSend.isNotEmpty() && afterSend.all { it.method == "GET" })
    }

    @Test
    fun aTwoHundredWithoutAnEventIdIsReadBackToo() {
        fixture.warmCache()
        answering(MatrixAnswer.Json(200, "{}"), stored = true)

        val accepted = send() as AuthenticatedRedditMessageReplyResult.Accepted

        assertEquals("\$stored", accepted.sentMatrixEventId)
        assertEquals(1, sends().size)
    }

    @Test
    fun aFailedMintNamesItsStepInTheProviderCode() {
        val failing = object : RedditChatWebTokenSource {
            override suspend fun token(): RedditChatWebTokenResult = RedditChatWebTokenResult.Failed(CHAT_WEB_TOKEN_NO_TOKEN)
            override fun invalidate(expectedToken: String?) = Unit
        }

        val failed = send(failing) as AuthenticatedRedditMessageReplyResult.Failed

        assertEquals(MessageReplyFailureKind.CHAT_SESSION_REQUIRED, failed.kind)
        assertEquals("CHAT_WEB_TOKEN_NO_TOKEN", failed.providerCode)
        assertTrue(fixture.requests.isEmpty())
    }
}
