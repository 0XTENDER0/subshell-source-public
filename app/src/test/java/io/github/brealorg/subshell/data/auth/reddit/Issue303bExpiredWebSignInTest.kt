package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.chat.RedditChatWebTokenResult
import io.github.brealorg.subshell.chat.RedditChatWebTokenSource
import io.github.brealorg.subshell.data.account.DirectChatLiveFailureKind
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
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #303 follow-up (probe on the Pixel 11, 2026-11-14): with an expired
 * `reddit_session` Reddit still mints a token, a logged-out one, and Matrix
 * answers whoami with 401. A 401 on a token this call just minted is named an
 * expired web sign-in (CHAT_WEB_SIGN_IN_EXPIRED for an image send,
 * WEB_SIGN_IN_EXPIRED for live chat), so the app can offer Sign in again; a
 * 401 on a cached token stays a plain session failure (the token is dropped
 * and the next call mints a new one)
 * (ISSUE303B_A_MATRIX_401_ON_A_FRESH_TOKEN_IS_AN_EXPIRED_WEB_SIGN_IN_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue303bExpiredWebSignInTest {
    private val fixture = MatrixTestFixture(respond = { request ->
        if (request.path.endsWith("/account/whoami")) MatrixAnswer.Json(401, "{}") else error("must not reach ${request.path}")
    })
    private val directory = createTempDirectory("issue303b").toFile()

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private fun tokens(fresh: Boolean) = object : RedditChatWebTokenSource {
        override suspend fun token(): RedditChatWebTokenResult =
            RedditChatWebTokenResult.Success(fixture.webToken, expiresAtEpochMs = null, fresh = fresh)

        override fun invalidate(expectedToken: String?) {
            fixture.invalidatedTokens += expectedToken
        }
    }

    private fun sendImage(fresh: Boolean) = runBlocking {
        RedditChatMatrixMediaWriter(
            tokenProvider = tokens(fresh),
            mediaSource = RedditMediaUploadSource { _, _ ->
                RedditMediaStageResult.Staged(
                    file = File(directory, "picture.gif").apply { writeBytes(byteArrayOf(1, 2, 3)) },
                    fileName = "picture.gif",
                    mimeType = "image/gif",
                    sizeBytes = 3L,
                )
            },
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
        ) as AuthenticatedRedditMessageReplyResult.Failed
    }

    private fun readLive(fresh: Boolean) = runBlocking {
        RedditChatMatrixLiveReader(
            capability = fixture.capability,
            tokenProvider = tokens(fresh),
            client = fixture.client,
            ioDispatcher = Dispatchers.IO,
            sessionCache = fixture.sessionCache,
        ).stream(
            accountStableId = fixture.account,
            participantUsername = fixture.participant,
            since = null,
            onConnected = {},
            onCursor = {},
            onMessages = {},
            onPeerTyping = {},
            onRedactions = {},
        )
    }

    @Test
    fun anImageSendOnAFreshTokenRefusedWith401IsAnExpiredWebSignIn() {
        val failed = sendImage(fresh = true)

        assertEquals(MessageReplyFailureKind.CHAT_WEB_SIGN_IN_EXPIRED, failed.kind)
        assertEquals(401, failed.statusCode)
        assertEquals(CHAT_STEP_WHOAMI, failed.providerCode)
        assertEquals(listOf<String?>(fixture.webToken), fixture.invalidatedTokens.toList())
        assertFalse(fixture.requests.any { it.path.contains("/upload") || it.path.contains("/send/") })
    }

    @Test
    fun anImageSendOnACachedTokenRefusedWith401StaysASessionFailure() {
        val failed = sendImage(fresh = false)

        assertEquals(MessageReplyFailureKind.CHAT_SESSION_REQUIRED, failed.kind)
        assertEquals(listOf<String?>(fixture.webToken), fixture.invalidatedTokens.toList())
    }

    @Test
    fun liveChatOnAFreshTokenRefusedWith401IsAnExpiredWebSignIn() {
        assertEquals(
            AuthenticatedRedditDirectChatLiveResult.Failed(DirectChatLiveFailureKind.WEB_SIGN_IN_EXPIRED, 401),
            readLive(fresh = true),
        )
        assertEquals(
            AuthenticatedRedditDirectChatLiveResult.Failed(DirectChatLiveFailureKind.SESSION, 401),
            readLive(fresh = false),
        )
    }
}
