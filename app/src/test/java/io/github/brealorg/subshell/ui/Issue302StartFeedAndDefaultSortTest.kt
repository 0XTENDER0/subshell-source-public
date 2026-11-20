package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.applock.AppLockEntryPoints
import io.github.brealorg.subshell.applock.AppLockSettings
import io.github.brealorg.subshell.applock.LocalAppLock
import io.github.brealorg.subshell.backup.SettingsBackupRepository
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditSubscriptionsUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.model.Community
import io.github.brealorg.subshell.ui.app.FeedEntryPoints
import io.github.brealorg.subshell.ui.screens.SettingsDestination
import io.github.brealorg.subshell.ui.screens.SettingsScreen
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE302_START_FEED_V1 (#302) and ISSUE300_DEFAULT_FEED_SORT_V1 /
 * ISSUE300_DEFAULT_COMMUNITY_SORT_V1 (#300 part 2): the app opens on the chosen start feed with
 * the default sort (Best opens Popular and All on Hot); a signed-in reader who chose Popular or
 * All lands there without waiting for the subscription list, also after an account switch; Back
 * at the root returns to the start feed; a community without a remembered sort opens on the
 * default; the three settings are in the backup, older backups get today's behaviour; the
 * Settings rows change them. The defaults are today's behaviour (Home, Best, Hot).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue302StartFeedAndDefaultSortTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:reader",
        username = "reader",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val other = account.copy(stableId = "reddit:other", username = "other")
    private val community = Community(id = "t5_test", name = "test", description = "", members = "1", accent = 0xFF6750A4L)

    @Test
    fun theDefaultsAreTodaysBehaviour() {
        val settings = SubshellSettings()
        assertEquals(StartFeed.HOME, settings.startFeed)
        assertEquals(FeedSort.BEST, settings.defaultFeedSort)
        assertEquals(CommunitySort.HOT, settings.defaultCommunitySort)
        val state = SubshellState(initialInteractionSideConfigured = true)
        assertEquals(FeedSource.HOME, state.feedSource)
        assertEquals(FeedSort.BEST, state.feedSort)
    }

    @Test
    fun aStartOpensTheChosenFeedWithTheDefaultSort() {
        val all = SubshellState(
            initialInteractionSideConfigured = true,
            initialStartFeed = StartFeed.ALL,
            initialDefaultFeedSort = FeedSort.NEW,
        )
        assertEquals(FeedSource.ALL, all.feedSource)
        assertEquals(FeedSort.NEW, all.feedSort)

        // All has no Best on Reddit: Best opens it on Hot.
        val allBest = SubshellState(initialInteractionSideConfigured = true, initialStartFeed = StartFeed.ALL)
        assertEquals(FeedSort.HOT, allBest.feedSort)

        val homeTop = SubshellState(initialInteractionSideConfigured = true, initialDefaultFeedSort = FeedSort.TOP)
        assertEquals(FeedSource.HOME, homeTop.feedSource)
        assertEquals(FeedSort.TOP, homeTop.feedSort)
    }

    @Test
    fun popularOrAllLandAtOnceAndHomeStillAsksTheSubscriptions() {
        val loading = RedditSubscriptionsUiState(accountStableId = account.stableId, loading = true)
        assertEquals(
            AuthenticatedMainFeedLandingDecision.ALL,
            resolveAuthenticatedLandingFeed(account.stableId, loading, startFeed = StartFeed.ALL),
        )
        assertEquals(
            AuthenticatedMainFeedLandingDecision.POPULAR,
            resolveAuthenticatedLandingFeed(account.stableId, RedditSubscriptionsUiState(), startFeed = StartFeed.POPULAR),
        )
        assertEquals(
            AuthenticatedMainFeedLandingDecision.WAIT,
            resolveAuthenticatedLandingFeed(account.stableId, loading, startFeed = StartFeed.HOME),
        )
        // With Home, zero subscriptions still land on Popular.
        assertEquals(
            AuthenticatedMainFeedLandingDecision.POPULAR,
            resolveAuthenticatedLandingFeed(
                account.stableId,
                RedditSubscriptionsUiState(accountStableId = account.stableId, loaded = true),
            ),
        )
    }

    @Test
    fun backAtTheRootReturnsToTheStartFeed() {
        val joined = RedditSubscriptionsUiState(accountStableId = account.stableId, loaded = true, communities = listOf(community))
        assertEquals(FeedSource.ALL, rootBackLandingSource(account.stableId, true, joined, StartFeed.ALL))
        assertEquals(FeedSource.ALL, rootBackLandingSource(account.stableId, false, joined, StartFeed.ALL))
        assertEquals(FeedSource.POPULAR, rootBackLandingSource(account.stableId, false, joined, StartFeed.POPULAR))
        assertEquals(FeedSource.HOME, rootBackLandingSource(account.stableId, true, joined, StartFeed.HOME))
        // Already on the start feed: Back leaves the app as before.
        assertNull(rootBackFeedSource(MainDestination.HOME, FeedSource.ALL, FeedSource.ALL))
        assertEquals(FeedSource.ALL, rootBackFeedSource(MainDestination.HOME, FeedSource.HOME, FeedSource.ALL))
    }

    @Test
    fun aSignedInReaderLandsOnAllWithoutWaitingAlsoAfterASwitch() {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account, other),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
            initialStartFeed = StartFeed.ALL,
            initialDefaultFeedSort = FeedSort.TOP,
        )
        // Reddit has not answered with the subscriptions.
        var subscriptions by mutableStateOf(RedditSubscriptionsUiState(accountStableId = account.stableId, loading = true))
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                redditFeed = FeedEntryPoints(redditSubscriptionsState = subscriptions),
            )
        }
        composeRule.waitForIdle()
        assertEquals(FeedSource.ALL, state.feedSource)
        assertEquals(FeedSort.TOP, state.feedSort)

        composeRule.runOnIdle {
            state.selectFeedSource(FeedSource.HOME)
            state.updateFeedSort(FeedSort.NEW)
            state.switchAccount(other.stableId)
            subscriptions = RedditSubscriptionsUiState(accountStableId = other.stableId, loading = true)
        }
        composeRule.waitForIdle()
        assertEquals("a switch lands on the start feed at once", FeedSource.ALL, state.feedSource)
        assertEquals(FeedSort.TOP, state.feedSort)
    }

    @Test
    fun aCommunityWithoutARememberedSortOpensOnTheDefault() {
        assertEquals(CommunitySort.HOT, preferredCommunitySort(emptyMap(), "kotlin"))
        assertEquals(CommunitySort.NEW, preferredCommunitySort(emptyMap(), "kotlin", CommunitySort.NEW))
        assertEquals(
            CommunitySort.TOP,
            preferredCommunitySort(mapOf("kotlin" to CommunitySort.TOP), "kotlin", CommunitySort.NEW),
        )

        val state = SubshellState(
            initialInteractionSideConfigured = true,
            initialDefaultCommunitySort = CommunitySort.NEW,
        )
        state.openSubreddit("kotlin")
        assertEquals(CommunitySort.NEW, state.communitySort)
        state.updateCommunitySort(CommunitySort.TOP)
        state.navigateBack()
        state.openSubreddit("kotlin")
        assertEquals("a picked sort is still remembered", CommunitySort.TOP, state.communitySort)
    }

    @Test
    fun theThreeSettingsAreBackedUpAndOlderBackupsGetTheDefaults() {
        val chosen = SubshellSettings(
            startFeed = StartFeed.POPULAR,
            defaultFeedSort = FeedSort.RISING,
            defaultCommunitySort = CommunitySort.CONTROVERSIAL,
        )
        val stored = chosen.toStorageMap()
        assertEquals("POPULAR", stored[SubshellSettings.START_FEED])
        assertEquals("RISING", stored[SubshellSettings.DEFAULT_FEED_SORT])
        assertEquals("CONTROVERSIAL", stored[SubshellSettings.DEFAULT_COMMUNITY_SORT])
        val restored = SubshellSettings.fromStorageMap(stored)
        assertEquals(StartFeed.POPULAR, restored.startFeed)
        assertEquals(FeedSort.RISING, restored.defaultFeedSort)
        assertEquals(CommunitySort.CONTROVERSIAL, restored.defaultCommunitySort)

        val older = SubshellSettings.fromStorageMap(
            stored - SubshellSettings.START_FEED - SubshellSettings.DEFAULT_FEED_SORT -
                SubshellSettings.DEFAULT_COMMUNITY_SORT,
        )
        assertEquals(StartFeed.HOME, older.startFeed)
        assertEquals(FeedSort.BEST, older.defaultFeedSort)
        assertEquals(CommunitySort.HOT, older.defaultCommunitySort)

        val state = SubshellState(initialInteractionSideConfigured = true)
        state.restoreSettings(chosen)
        assertEquals(StartFeed.POPULAR, state.startFeed)
        assertEquals(FeedSort.RISING, state.defaultFeedSort)
        assertEquals(CommunitySort.CONTROVERSIAL, state.defaultCommunitySort)
        val captured = state.captureSettings()
        assertEquals(StartFeed.POPULAR, captured.startFeed)
        assertEquals(FeedSort.RISING, captured.defaultFeedSort)
        assertEquals(CommunitySort.CONTROVERSIAL, captured.defaultCommunitySort)
    }

    @Test
    fun theSettingsRowsChangeTheThreeChoices() {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(initialInteractionSideConfigured = true)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppLock provides AppLockEntryPoints(
                    settings = AppLockSettings(),
                    deviceUnlockAvailable = false,
                    onChange = {},
                    onRequestUnlock = { onResult -> onResult(false) },
                ),
            ) {
                SubshellTheme {
                    SettingsScreen(
                        state = state,
                        backupRepository = SettingsBackupRepository(context, "test"),
                        onBack = {},
                        initialDestinationName = SettingsDestination.FEEDS.name,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        fun choose(row: String, option: String) {
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(row))
            composeRule.onNodeWithText(row).performClick()
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText(option).onLast().performClick()
            composeRule.waitForIdle()
        }
        choose("Start feed", "All")
        assertEquals(StartFeed.ALL, state.startFeed)
        choose("Feed sort", "New")
        assertEquals(FeedSort.NEW, state.defaultFeedSort)
        choose("Community sort", "Top")
        assertEquals(CommunitySort.TOP, state.defaultCommunitySort)
        assertTrue(state.captureSettings().startFeed == StartFeed.ALL)
    }
}
