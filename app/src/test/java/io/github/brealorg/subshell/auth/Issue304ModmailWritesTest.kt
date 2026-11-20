package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.RedditModmailConversation
import io.github.brealorg.subshell.data.account.RedditModmailFailedAction
import io.github.brealorg.subshell.data.account.RedditModmailThread
import io.github.brealorg.subshell.data.account.RedditModmailUiFailureKind
import io.github.brealorg.subshell.data.account.RedditModmailUnreadCounts
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModmailFailureKind
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModmailLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModmailResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModmailThreadResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModmailUnreadCountsResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModmailUnreadResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditModmailArchiveRequest
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditModmailArchiveResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditModmailReadStateRequest
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditModmailReadStateResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditModmailSendRequest
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditModmailSendResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditModmailWriter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #304 (code review 2026-11-14): closing a modmail conversation cleared the
 * in-progress flags while the write still ran, so Back, reopen and Send again
 * sent a second reply (a list refresh did the same); results of account A
 * landed in account B's state after a switch, and A's unclear reply told B its
 * reply may not have been sent; a failed action did not say which action
 * failed (ISSUE304_A_WRITE_IN_FLIGHT_KEEPS_ITS_FLAG_V1,
 * ISSUE304_RESULTS_STAY_WITH_THEIR_ACCOUNT_V1,
 * ISSUE304_AN_UNKNOWN_SEND_BELONGS_TO_ITS_ACCOUNT_V1,
 * ISSUE304_A_FAILED_ACTION_SAYS_WHICH_V1).
 */
class Issue304ModmailWritesTest {
    private val accountA = "reddit:mod_a"
    private val accountB = "reddit:mod_b"
    private var active = accountA

    private fun conversation(id: String) = RedditModmailConversation(
        id = id,
        communityName = "shared",
        subject = "Subject $id",
        participantName = null,
        preview = "preview",
        lastUpdatedEpochSeconds = 1L,
        unread = false,
        isInternal = false,
        messageCount = 1,
    )

    private val conversations = listOf(conversation("aaa"), conversation("bbb"))
    private var send = CompletableDeferred<AuthenticatedRedditModmailSendResult>()
    private var archive = CompletableDeferred<AuthenticatedRedditModmailArchiveResult>()
    private var readState = CompletableDeferred<AuthenticatedRedditModmailReadStateResult>()
    private var sends = 0

    private val loader = object : AuthenticatedRedditModmailLoader {
        override suspend fun load(accountStableId: String, after: String?) =
            AuthenticatedRedditModmailResult.Loaded(setOf("shared"), conversations, nextCursor = null)
        override suspend fun loadUnread(accountStableId: String) =
            AuthenticatedRedditModmailUnreadResult.Loaded(emptyList())
        override suspend fun loadUnreadCounts(accountStableId: String) =
            AuthenticatedRedditModmailUnreadCountsResult.Loaded(RedditModmailUnreadCounts())
        override suspend fun loadThread(accountStableId: String, conversationId: String) =
            AuthenticatedRedditModmailThreadResult.Loaded(
                RedditModmailThread(conversations.single { it.id == conversationId }, messages = emptyList()),
            )
    }

    private val writer = object : AuthenticatedRedditModmailWriter {
        override suspend fun send(request: AuthenticatedRedditModmailSendRequest): AuthenticatedRedditModmailSendResult {
            sends += 1
            return send.await()
        }
        override suspend fun setArchived(request: AuthenticatedRedditModmailArchiveRequest) = archive.await()
        override suspend fun setReadState(request: AuthenticatedRedditModmailReadStateRequest) = readState.await()
    }

    private suspend fun coordinator(): ModmailCoordinator {
        val modmail = ModmailCoordinator(
            capability = null,
            loader = loader,
            writer = writer,
            clientMode = null,
            activeAccountStableId = { active },
        )
        modmail.load(accountA)
        return modmail
    }

    private fun CoroutineScope.later(block: suspend () -> Unit) =
        launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) { block() }

    private suspend fun ModmailCoordinator.reply(account: String, conversationId: String) =
        sendMessage(account, conversationId, body = "hello", internal = false, authorHidden = false)

    @Test(timeout = 10_000)
    fun backReopenAndSendAgainWhileTheReplyRunsSendsOnce() = runBlocking<Unit> {
        val modmail = coordinator()
        modmail.openConversation(accountA, "aaa")
        later { modmail.reply(accountA, "aaa") }

        modmail.closeConversation()
        modmail.openConversation(accountA, "aaa")
        modmail.reply(accountA, "aaa")

        assertEquals(1, sends)
        assertTrue(modmail.uiState.value.sendInProgress)
        send.complete(AuthenticatedRedditModmailSendResult.Sent)
        assertFalse(modmail.uiState.value.sendInProgress)
    }

    @Test(timeout = 10_000)
    fun aListRefreshWhileTheReplyRunsKeepsSendBlocked() = runBlocking<Unit> {
        val modmail = coordinator()
        modmail.openConversation(accountA, "aaa")
        later { modmail.reply(accountA, "aaa") }

        modmail.load(accountA, forceRefresh = true, keepOpenConversation = true)
        modmail.reply(accountA, "aaa")

        assertEquals(1, sends)
        send.complete(AuthenticatedRedditModmailSendResult.Sent)
    }

    @Test(timeout = 10_000)
    fun accountAsReadChangeAfterASwitchLeavesAccountBAlone() = runBlocking<Unit> {
        val modmail = coordinator()
        modmail.openConversation(accountA, "aaa")
        later { modmail.setUnread(accountA, "aaa", unread = true) }

        active = accountB
        modmail.load(accountB)
        modmail.openConversation(accountB, "aaa")
        val accountBState = modmail.uiState.value

        readState.complete(AuthenticatedRedditModmailReadStateResult.AuthorizationRequired)

        assertEquals(accountBState, modmail.uiState.value)
        assertFalse(modmail.uiState.value.authorizationRequired)
    }

    @Test(timeout = 10_000)
    fun accountAsArchiveAppliedAfterASwitchLeavesAccountBAlone() = runBlocking<Unit> {
        val modmail = coordinator()
        modmail.openConversation(accountA, "aaa")
        later { modmail.setArchived(accountA, "aaa", archived = true) }

        active = accountB
        modmail.load(accountB)
        modmail.openConversation(accountB, "bbb")
        later { modmail.reply(accountB, "bbb") }
        assertTrue(modmail.uiState.value.sendInProgress)

        archive.complete(AuthenticatedRedditModmailArchiveResult.Applied)

        assertEquals(accountB, modmail.uiState.value.accountStableId)
        assertTrue("B's own reply is still running", modmail.uiState.value.sendInProgress)
        send.complete(AuthenticatedRedditModmailSendResult.Sent)
    }

    @Test(timeout = 10_000)
    fun anUnclearReplyOfAccountADoesNotWarnAccountB() = runBlocking<Unit> {
        val modmail = coordinator()
        modmail.openConversation(accountA, "aaa")
        send.complete(
            AuthenticatedRedditModmailSendResult.Failed(
                kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                outcomeUnknown = true,
            ),
        )
        modmail.reply(accountA, "aaa")
        assertTrue(modmail.uiState.value.sendOutcomeUnknown)

        active = accountB
        modmail.load(accountB)
        later { modmail.openConversation(accountB, "aaa") }

        assertFalse(modmail.uiState.value.sendOutcomeUnknown)
    }

    @Test(timeout = 10_000)
    fun aRefusedReplySaysItWasTheReply() = runBlocking<Unit> {
        val modmail = coordinator()
        modmail.openConversation(accountA, "aaa")
        send.complete(
            AuthenticatedRedditModmailSendResult.Failed(
                kind = AuthenticatedRedditModmailFailureKind.HTTP,
                httpStatusCode = 403,
            ),
        )

        modmail.reply(accountA, "aaa")

        assertEquals(RedditModmailFailedAction.SEND, modmail.uiState.value.failedAction)
        assertEquals(RedditModmailUiFailureKind.HTTP, modmail.uiState.value.failureKind)
    }
}
