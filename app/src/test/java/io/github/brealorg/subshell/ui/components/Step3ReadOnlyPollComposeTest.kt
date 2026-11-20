package io.github.brealorg.subshell.ui.components

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * STEP3 (polls01a1): a signed-in account cannot vote in a Reddit poll inside
 * Subshell — a vote changes nothing locally, and the poll offers "Open on
 * Reddit to vote" (or "Open on Reddit" once closed); tapping an option opens
 * the poll in the browser's own tab, which may not hand the page to another
 * app, and Subshell's Reddit links stay off until the reader is back (#308).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ReadOnlyPollComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val poll: Post = SampleData.posts.single { it.kind == PostKind.POLL }.copy(id = "t3_pollabc")

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    private fun installBrowser() {
        val browser = ComponentName("com.example.browser", "com.example.browser.Main")
        val packageManager = shadowOf(context.packageManager)
        packageManager.addActivityIfNotPresent(browser)
        packageManager.addIntentFilterForActivity(
            browser,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("https")
            },
        )
    }

    private fun showCard(post: Post, state: SubshellState) {
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    PostCard(post = post, state = state, onOpen = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aSignedInVoteChangesNothingLocally() {
        val state = signedIn()

        state.votePoll(poll.id, "media")

        assertNull(state.pollVotes[poll.id])
    }

    @Test
    fun aPollWithoutAUsableIdHasNoRedditPage() {
        assertEquals("https://www.reddit.com/poll/pollabc", redditPollWebUrl(poll))
        assertNull(redditPollWebUrl(poll.copy(id = "subshell-poll")))
    }

    @Test
    fun aSignedInPollOffersRedditAndAnOptionOpensItThere() {
        installBrowser()
        val state = signedIn()
        showCard(poll, state)
        assertEquals(1, composeRule.onAllNodesWithText("Open on Reddit to vote").fetchSemanticsNodes().size)

        composeRule.onNode(hasClickAction() and hasText("Media viewer")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        val started = shadowOf(context).nextStartedActivity
        assertEquals("https://www.reddit.com/poll/pollabc", started.dataString)
        assertEquals("com.example.browser", started.`package`)
        assertFalse(started.getBooleanExtra("android.support.customtabs.extra.SEND_TO_EXTERNAL_HANDLER", true))
        assertFalse(started.getBooleanExtra("androidx.browser.customtabs.extra.INITIAL_NAVIGATION_CAN_LEAVE_BROWSER", true))
        assertTrue(started.extras!!.containsKey("android.support.customtabs.extra.SESSION"))
        assertNull(state.pollVotes[poll.id])
    }

    /**
     * ISSUE308_REDDIT_LINKS_STAY_OFF_UNTIL_THE_READER_RETURNS_V1 (#308): Reddit sends
     * /poll/<id> on to /comments/<id>, and a sign-in or vote in the tab can move it
     * on again much later; Subshell must not take the page back until the reader is
     * in Subshell again.
     */
    @Test
    fun redditLinksStayOffWhileTheReaderIsInTheBrowser() {
        installBrowser()
        showCard(poll, signedIn())
        val links = redditLinkComponent(context)

        composeRule.onAllNodesWithText("Open on Reddit to vote").onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, context.packageManager.getComponentEnabledSetting(links))

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, context.packageManager.getComponentEnabledSetting(links))

        redditLinksBackOnReturn(context)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, context.packageManager.getComponentEnabledSetting(links))
    }

    @Test
    fun aReaderWhoNeverComesBackGetsRedditLinksBackLater() {
        installBrowser()
        showCard(poll, signedIn())
        val links = redditLinkComponent(context)

        composeRule.onAllNodesWithText("Open on Reddit to vote").onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(REDDIT_LINKS_OFF_MILLIS))

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, context.packageManager.getComponentEnabledSetting(links))
    }

    @Test
    fun aClosedPollOffersOnlyToOpenItOnReddit() {
        val closed = poll.copy(poll = poll.poll!!.copy(isClosed = true))
        showCard(closed, signedIn())

        assertEquals(1, composeRule.onAllNodesWithText("Open on Reddit").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Open on Reddit to vote").fetchSemanticsNodes().size)
    }
}
