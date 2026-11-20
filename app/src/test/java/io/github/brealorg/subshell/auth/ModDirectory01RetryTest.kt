package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModQueueResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratedCommunitiesResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModerationFailureKind
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModerationLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModerationTargetResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MODDIRECTORY01_A_FAILED_READ_IS_RETRIED_ON_RETURN_V1: a failed read of the
 * moderator directory left it empty for the process (no Mod tab, no Mod
 * tools); the next unforced request (the app coming back) reads it again,
 * while a loaded directory is not read again.
 */
class ModDirectory01RetryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val answers = ArrayDeque<AuthenticatedRedditModeratedCommunitiesResult>()
    private var reads = 0
    private val loader = object : AuthenticatedRedditModerationLoader {
        override suspend fun loadModeratorCommunities(accountStableId: String): AuthenticatedRedditModeratedCommunitiesResult {
            reads++
            return answers.removeFirst()
        }

        override suspend fun loadModQueue(accountStableId: String, rawCommunityName: String, after: String?) =
            AuthenticatedRedditModQueueResult.Loaded(rawCommunityName, emptyList(), null)

        override suspend fun resolveTargetPost(accountStableId: String, postId: String) =
            AuthenticatedRedditModerationTargetResult.NotFound
    }
    private val coordinator = ModerationQueueCoordinator(moderationLoader = loader, scope = scope)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun aFailedReadIsReadAgainAndALoadedOneIsNot() {
        answers += AuthenticatedRedditModeratedCommunitiesResult.Failed(
            kind = AuthenticatedRedditModerationFailureKind.TRANSPORT,
            retryable = true,
        )
        answers += AuthenticatedRedditModeratedCommunitiesResult.Loaded(setOf("subshell", "astronotyet"))

        coordinator.loadRedditModeratorCommunities("acct")
        assertTrue(coordinator.redditModerationUiState.value.moderatedCommunities.isEmpty())

        coordinator.loadRedditModeratorCommunities("acct")
        assertEquals(setOf("subshell", "astronotyet"), coordinator.redditModerationUiState.value.moderatedCommunities.toSet())

        coordinator.loadRedditModeratorCommunities("acct")
        assertEquals(2, reads)
    }
}
