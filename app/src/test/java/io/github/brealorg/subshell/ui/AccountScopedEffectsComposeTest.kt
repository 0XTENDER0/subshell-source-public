package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditSubscriptionsUiFailureKind
import io.github.brealorg.subshell.data.account.RedditSubscriptionsUiState
import io.github.brealorg.subshell.data.account.localFixtureAccountSummaries
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.model.Community
import io.github.brealorg.subshell.ui.app.AccountScopedEffects
import io.github.brealorg.subshell.ui.app.FeedEntryPoints
import io.github.brealorg.subshell.ui.app.ModeratorToolsEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The account-scoped effects in their own file, characterised: an
 * authenticated account refreshes its moderator directory and the inbox
 * tab resets; the one-shot authenticated landing waits for authoritative
 * subscriptions, then lands on Home (subscriptions) or Popular (none),
 * stays pending on a context route or a custom feed, and is dropped for
 * another account or a subscriptions failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AccountScopedEffectsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val kotlin = Community(
        id = "t5_kotlin",
        name = "kotlin",
        description = "",
        members = "1",
        accent = 0L,
        joined = true,
    )

    @Test
    fun anAuthenticatedAccountRefreshesItsModeratorDirectory() {
        val directoryLoads = mutableListOf<Pair<String, Boolean>>()
        host(
            state = authenticatedState(),
            moderatorTools = ModeratorToolsEntryPoints(
                onLoadRedditModeratorCommunities = { account, force -> directoryLoads += account to force },
            ),
        )

        // The inbox tab is reset by its rememberSaveable owner in SubshellApp
        // (LOWUI8_ACCOUNT_RESETS_ONLY_ON_A_CHANGE_V1, LowUi8aRecreationComposeTest).
        // The resume effect (MODDIRECTORY01) may ask as well; the coordinator
        // ignores a request while the directory is loading or loaded.
        assertEquals(listOf(alice.stableId to false), directoryLoads.distinct())
    }

    // MODDIRECTORY01_A_FAILED_READ_IS_RETRIED_ON_RETURN_V1
    @Test
    fun comingBackToTheAppAsksForTheModeratorDirectoryAgain() {
        val directoryLoads = mutableListOf<Pair<String, Boolean>>()
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.RESUMED
        host(
            state = authenticatedState(),
            moderatorTools = ModeratorToolsEntryPoints(
                onLoadRedditModeratorCommunities = { account, force -> directoryLoads += account to force },
            ),
            lifecycleOwner = owner,
        )
        val before = directoryLoads.size

        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()

        assertEquals(before + 1, directoryLoads.size)
        assertEquals(alice.stableId to false, directoryLoads.last())
    }

    @Test
    fun aLocalAccountDoesNotRefreshTheModeratorDirectory() {
        val directoryLoads = mutableListOf<Pair<String, Boolean>>()
        val local = localFixtureAccountSummaries(listOf("root"))
        host(
            state = SubshellState(initialAccountSessions = local, initialActiveAccountId = local.first().stableId),
            moderatorTools = ModeratorToolsEntryPoints(
                onLoadRedditModeratorCommunities = { account, force -> directoryLoads += account to force },
            ),
        )

        assertEquals(emptyList<Pair<String, Boolean>>(), directoryLoads)
    }

    @Test
    fun theLandingWaitsForAuthoritativeSubscriptionsThenLandsOnHome() {
        val state = authenticatedState()
        state.selectFeedSource(FeedSource.POPULAR)
        val pending = mutableStateOf<String?>(alice.stableId)
        val subscriptions = mutableStateOf(
            RedditSubscriptionsUiState(accountStableId = alice.stableId, loading = true),
        )
        host(state = state, pending = pending, subscriptions = subscriptions)
        assertEquals(alice.stableId, pending.value)

        subscriptions.value = RedditSubscriptionsUiState(
            accountStableId = alice.stableId,
            communities = listOf(kotlin),
            loaded = true,
            contentRevision = 1L,
        )
        composeRule.waitForIdle()

        assertNull(pending.value)
        assertEquals(FeedSource.HOME, state.feedSource)
        assertEquals(FeedSort.BEST, state.feedSort)
    }

    @Test
    fun zeroAuthoritativeSubscriptionsLandOnPopular() {
        val state = authenticatedState()
        val pending = mutableStateOf<String?>(alice.stableId)
        host(
            state = state,
            pending = pending,
            subscriptions = mutableStateOf(
                RedditSubscriptionsUiState(accountStableId = alice.stableId, loaded = true, contentRevision = 1L),
            ),
        )

        assertNull(pending.value)
        assertEquals(FeedSource.POPULAR, state.feedSource)
        assertEquals(FeedSort.HOT, state.feedSort)
    }

    @Test
    fun theLandingStaysPendingOnAContextRouteOrACustomFeed() {
        val state = authenticatedState()
        state.openUser("bob")
        val pending = mutableStateOf<String?>(alice.stableId)
        val loaded = mutableStateOf(
            RedditSubscriptionsUiState(
                accountStableId = alice.stableId,
                communities = listOf(kotlin),
                loaded = true,
                contentRevision = 1L,
            ),
        )
        val customFeed = mutableStateOf<String?>(null)
        host(state = state, pending = pending, subscriptions = loaded, activeCustomFeedPath = customFeed)
        assertEquals(alice.stableId, pending.value)

        state.navigateBack()
        customFeed.value = "/user/alice/m/cats"
        composeRule.waitForIdle()
        assertEquals(alice.stableId, pending.value)

        customFeed.value = null
        composeRule.waitForIdle()
        assertNull(pending.value)
    }

    @Test
    fun aPendingLandingForAnotherAccountIsDropped() {
        val other = mutableStateOf<String?>("reddit:bob")
        host(state = authenticatedState(), pending = other)
        assertNull(other.value)
    }

    @Test
    fun aSubscriptionsFailureDropsThePendingLandingWithoutLanding() {
        val state = authenticatedState()
        state.selectFeedSource(FeedSource.POPULAR)
        val pending = mutableStateOf<String?>(alice.stableId)
        host(
            state = state,
            pending = pending,
            subscriptions = mutableStateOf(
                RedditSubscriptionsUiState(
                    accountStableId = alice.stableId,
                    failureKind = RedditSubscriptionsUiFailureKind.TRANSPORT,
                    contentRevision = 1L,
                ),
            ),
        )

        assertNull(pending.value)
        assertEquals(FeedSource.POPULAR, state.feedSource)
    }

    private fun authenticatedState() = SubshellState(
        initialAccountSessions = listOf(alice),
        initialActiveAccountId = alice.stableId,
    )

    private fun host(
        state: SubshellState,
        moderatorTools: ModeratorToolsEntryPoints = ModeratorToolsEntryPoints(),
        pending: MutableState<String?> = mutableStateOf(null),
        subscriptions: MutableState<RedditSubscriptionsUiState> =
            mutableStateOf(RedditSubscriptionsUiState()),
        activeCustomFeedPath: MutableState<String?> = mutableStateOf(null),
        lifecycleOwner: LifecycleOwner? = null,
    ) {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides (lifecycleOwner ?: LocalLifecycleOwner.current)) {
                AccountScopedEffects(
                    state = state,
                    moderatorTools = moderatorTools,
                    redditFeed = FeedEntryPoints(redditSubscriptionsState = subscriptions.value),
                    activeCustomFeedPath = activeCustomFeedPath.value,
                    pendingAuthenticatedLandingAccountIdState = pending,
                )
            }
        }
        composeRule.waitForIdle()
    }
}
