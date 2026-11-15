package io.github.brealorg.subshell.ui.components

import android.app.Application
import android.content.ClipboardManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditCommentsUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator
import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.screens.PostScreen
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE300_ONE_COPY_ENTRY_OPENS_A_SHEET_V1 (#300): a post's and a comment's menu has one entry,
 * "Copy…", opening a sheet with Copy text, Select text, Copy link to the post/comment and the
 * web links in the text as link cards; a card opens the link menu (LINKMENU01).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue300CopySheetComposeTest {
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

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    private val linkPost = SampleData.posts.first().copy(
        id = "t3_abc123",
        permalink = "/r/kotlin/comments/abc123/a_title/",
        title = "A link post",
        body = "See [the docs](https://example.org/docs?utm_source=x) and https://example.com/plain too.",
        kind = PostKind.LINK,
        externalUrl = "https://news.example.net/story",
    )

    private fun clip(): String =
        context.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun shown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun showCard() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    PostCard(post = linkPost, state = state, onOpen = {})
                }
            }
        }
        composeRule.waitForIdle()
        click(hasContentDescription("Post actions") and hasClickAction())
        click(hasText("Copy…") and hasClickAction())
    }

    @Test
    fun webLinksAreFoundInReadingOrderOnceEachAndNotInCode() {
        assertEquals(
            listOf("https://a.example/1", "https://b.example/2", "https://c.example/3"),
            webLinksInMarkdown(
                "First [label](https://a.example/1), then https://b.example/2.\n\n" +
                    "> quoted https://c.example/3 and again https://a.example/1\n\n" +
                    "    https://code.example/not-a-link\n\n" +
                    "u/someone and r/kotlin are not web links",
            ),
        )
        assertEquals(emptyList<String>(), webLinksInMarkdown("No links at all."))
    }

    @Test
    fun aPostsCopySheetCopiesTitleAndTextAndItsLinkAndListsItsLinks() {
        showCard()
        // The link post's own address first, then the links in the text.
        assertEquals(1, shown("Links in this post"))
        assertEquals(1, shown("news.example.net"))
        assertEquals(1, shown("example.org"))
        assertEquals(1, shown("example.com"))

        click(hasText("Copy text") and hasClickAction())
        assertEquals(
            "A link post\n\nSee [the docs](https://example.org/docs?utm_source=x) and https://example.com/plain too.",
            clip(),
        )

        click(hasContentDescription("Post actions") and hasClickAction())
        click(hasText("Copy…") and hasClickAction())
        click(hasText("Copy link to post") and hasClickAction())
        assertEquals("https://www.reddit.com/r/kotlin/comments/abc123/a_title/", clip())
    }

    @Test
    fun aLinkCardOpensTheLinkMenuWithTheCleanedAddress() {
        showCard()
        click(hasText("example.org") and hasClickAction())
        assertEquals(1, shown("Share link"))
        assertEquals(1, shown("Copy original link"))
        click(hasText("Copy link") and hasClickAction())
        assertEquals("https://example.org/docs", clip())
        assertEquals(0, shown("Share link"))
    }

    @Test
    fun selectTextShowsTheTitleAndTheTextToSelect() {
        showCard()
        click(hasText("Select text") and hasClickAction())
        assertEquals(1, shown("Long-press to select, then copy."))
        // The card behind the sheet shows the title too.
        assertEquals(2, shown("A link post"))
        assertEquals(0, shown("Copy link to post"))
    }

    @Test
    fun aCommentsCopySheetCopiesItsTextAndItsLink() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val post = state.posts.first()
        val comment = Comment(
            id = "t1_c1", author = "alice", age = "1h", body = "Read https://example.com/x first",
            score = 1, depth = 0, accent = 0L,
        )
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    redditCommentsState = RedditCommentsUiState(
                        accountStableId = state.activeAccountId,
                        postId = post.id,
                        sort = RedditCommentSort.BEST,
                        comments = listOf(comment),
                        fullDiscussionLoaded = true,
                    ),
                )
            }
        }
        composeRule.waitForIdle()
        runCatching {
            composeRule.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey("comment:${comment.id}")
        }
        composeRule.waitForIdle()

        click(hasContentDescription("Comment options") and hasClickAction())
        click(hasText("Copy…") and hasClickAction())
        assertEquals(1, shown("Link in this comment"))
        click(hasText("Copy text") and hasClickAction())
        assertEquals("Read https://example.com/x first", clip())

        click(hasContentDescription("Comment options") and hasClickAction())
        click(hasText("Copy…") and hasClickAction())
        click(hasText("Copy link to comment") and hasClickAction())
        assertEquals(true, clip().contains("/c1/"))
    }
}
