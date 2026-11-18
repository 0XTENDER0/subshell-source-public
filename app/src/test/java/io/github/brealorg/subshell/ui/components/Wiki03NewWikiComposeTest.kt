package io.github.brealorg.subshell.ui.components

import android.app.Application
import android.content.ComponentName
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Looper
import android.content.Intent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.RedditCommunityWikiUiFailureKind
import io.github.brealorg.subshell.data.account.RedditCommunityWikiUiState
import io.github.brealorg.subshell.model.CommunityWikiIndex
import io.github.brealorg.subshell.model.CommunityWikiPage
import io.github.brealorg.subshell.model.CommunityWikiPageRef
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * WIKI03_THE_NEW_WIKI_IS_NAMED_V1, WIKI03_CONFIGURATION_PAGES_READ_AS_TEXT_V1:
 * Reddit runs two Wikis; its API gives apps only the old one (Devvit's
 * WIKI_VERSION_V1), while reddit.com shows the new one (V2). On the Pixel
 * (2026-11-16) r/subshell's new page was invisible: only its config pages came
 * back. The Wiki now says so and opens reddit.com; config pages (AutoModerator's
 * YAML) read as text, not Markdown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Wiki03NewWikiComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = "reddit:me"
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun show(state: RedditCommunityWikiUiState, selectedPage: String?) {
        composeRule.setContent {
            SubshellTheme {
                LazyColumn {
                    communityWikiItems(
                        state = state,
                        communityName = "subshell",
                        matches = true,
                        selectedPagePath = selectedPage,
                        showConfigurationPages = false,
                        onSelectedPagePathChange = {},
                        onShowConfigurationPagesChange = {},
                        onAuthorize = {},
                        onRetryIndex = {},
                        onRetryPage = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun openOnRedditOpens(): String? {
        composeRule.onAllNodes(hasText("Open on reddit.com") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        val started: Intent? = shadowOf(app).nextStartedActivity
        return started?.data?.toString()
    }

    @Test
    fun aWikiWithOnlyConfigurationPagesNamesTheNewWikiAndOpensReddit() {
        show(
            RedditCommunityWikiUiState(
                accountStableId = account,
                communityName = "subshell",
                index = CommunityWikiIndex(
                    "subshell",
                    listOf(CommunityWikiPageRef("config/automoderator"), CommunityWikiPageRef("config/sidebar")),
                ),
            ),
            selectedPage = null,
        )
        assertEquals(1, count("Reddit's new Wiki, which Reddit doesn't offer to apps"))
        assertEquals("https://www.reddit.com/r/subshell/wiki/", openOnRedditOpens())
    }

    // WIKI03_REDDIT_PAGES_STAY_IN_THE_BROWSER_V1: Firefox (open links in apps) handed
    // the page straight back to Subshell on the Pixel 11.
    @Test
    fun openOnRedditAsksTheBrowserForATabThatMayNotHandThePageBack() {
        val browser = ComponentName("com.example.browser", "com.example.browser.Main")
        shadowOf(app.packageManager).apply {
            addActivityIfNotPresent(browser)
            addIntentFilterForActivity(
                browser,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addDataScheme("https")
                },
            )
        }
        show(
            RedditCommunityWikiUiState(
                accountStableId = account,
                communityName = "subshell",
                index = CommunityWikiIndex("subshell", listOf(CommunityWikiPageRef("config/automoderator"))),
            ),
            selectedPage = null,
        )
        composeRule.onAllNodes(hasText("Open on reddit.com") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        val started = requireNotNull(shadowOf(app).nextStartedActivity)

        assertEquals("https://www.reddit.com/r/subshell/wiki/", started.dataString)
        assertEquals("com.example.browser", started.`package`)
        assertFalse(started.getBooleanExtra("android.support.customtabs.extra.SEND_TO_EXTERNAL_HANDLER", true))
        assertFalse(started.getBooleanExtra("androidx.browser.customtabs.extra.INITIAL_NAVIGATION_CAN_LEAVE_BROWSER", true))
        assertTrue(started.extras!!.containsKey("android.support.customtabs.extra.SESSION"))

        // WIKI03_SUBSHELL_STEPS_ASIDE_WHILE_THE_BROWSER_OPENS_V1
        val links = redditLinkComponent(app)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, app.packageManager.getComponentEnabledSetting(links))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(REDDIT_LINKS_OFF_MILLIS))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, app.packageManager.getComponentEnabledSetting(links))
    }

    @Test
    fun aProcessStartSwitchesRedditLinksBackOn() {
        val links = redditLinkComponent(app)
        app.packageManager.setComponentEnabledSetting(
            links,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        restoreRedditLinkHandling(app)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, app.packageManager.getComponentEnabledSetting(links))
    }

    @Test
    fun aPageTheApiDoesNotHaveNamesTheNewWikiAndOpensThatPage() {
        show(
            RedditCommunityWikiUiState(
                accountStableId = account,
                communityName = "subshell",
                failureKind = RedditCommunityWikiUiFailureKind.UNAVAILABLE,
            ),
            selectedPage = "index",
        )
        assertEquals(1, count("It may be in Reddit's new Wiki"))
        assertEquals("https://www.reddit.com/r/subshell/wiki/index", openOnRedditOpens())
    }

    @Test
    fun theAutoModeratorConfigReadsAsTextWithItsCommentsAndIndentation() {
        val yaml = "# subshell sync test 06:25\n---\ntype: comment\nbody (regex, includes):\n    - 'x'\naction: filter\n---"
        show(
            RedditCommunityWikiUiState(
                accountStableId = account,
                communityName = "subshell",
                page = CommunityWikiPage("subshell", "config/automoderator", yaml, revisionAuthor = "brealorg"),
            ),
            selectedPage = "config/automoderator",
        )
        val text = composeRule.onAllNodes(hasTestTag(WIKI_CONFIGURATION_TEXT_TEST_TAG)).fetchSemanticsNodes()
        assertEquals(1, text.size)
        assertEquals(1, count("# subshell sync test 06:25"))
        assertEquals(1, count("    - 'x'"))
        assertTrue(count("Open on reddit.com") >= 1)
    }

    @Test
    fun anOrdinaryPageStillReadsAsMarkdownWithALinkToReddit() {
        show(
            RedditCommunityWikiUiState(
                accountStableId = account,
                communityName = "astronotyet",
                page = CommunityWikiPage("astronotyet", "index", "# Welcome\n\nLinks are below."),
            ),
            selectedPage = "index",
        )
        assertEquals(0, composeRule.onAllNodes(hasTestTag(WIKI_CONFIGURATION_TEXT_TEST_TAG)).fetchSemanticsNodes().size)
        assertEquals(1, count("Links are below."))
        assertEquals(0, count("# Welcome"))
        assertEquals("https://www.reddit.com/r/subshell/wiki/index", openOnRedditOpens())
    }
}
