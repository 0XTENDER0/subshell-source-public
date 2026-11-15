package io.github.brealorg.subshell.ui.components

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.RedditModeratorActionUiState
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItemDetails
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.account.RedditModeratorReportDetails
import io.github.brealorg.subshell.data.account.RedditModeratorReportReason
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE301_QUEUE_CARD_SHOWS_WHY_AND_WHAT_V1 (#301): the Inbox queue card shows the
 * report reasons, "Held for review" with why for an unreported item instead of
 * "No reports returned", and a comment's picture as the feed's thumbnail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue301QueueCardComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun show(item: RedditModeratorQueueItem, showsReportCount: Boolean = true) {
        ensureWorkManagerInitialized(context)
        val state = SubshellState()
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    if (showsReportCount) {
                        InboxModeratorQueueItemCard(
                            item = item,
                            actionUiState = RedditModeratorActionUiState(),
                            onOpen = {},
                            thumbnail = { modifier -> ModeratorQueueItemThumbnail(item, state, modifier) },
                        )
                    } else {
                        // The Removed list draws the summary without report talk.
                        ModeratorQueueItemSummary(item = item, inProgress = false, showsReportCount = false)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun shown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun anUnreportedItemSaysItIsHeldAndWhyNotNoReports() {
        show(
            RedditModeratorQueueItem(
                fullname = "t3_bbb222",
                kind = RedditModeratorQueueKind.POST,
                communityName = "subshell",
                author = "newcomer",
                summary = "Automod-filter-test",
                reportDetails = RedditModeratorReportDetails(userReports = emptyList(), moderatorReports = emptyList(), totalCount = 0),
                details = RedditModeratorQueueItemDetails(ageLabel = "2m", snippet = "Just testing."),
            ),
        )
        assertEquals(1, shown(HELD_FOR_REVIEW_LABEL))
        assertEquals(1, shown(HELD_WITHOUT_REPORTS_EXPLANATION))
        assertEquals(0, shown(HELD_BY_AUTOMODERATOR_EXPLANATION))
        assertEquals(0, shown("No reports returned"))
        assertEquals(1, shown("Just testing."))
        assertEquals(1, shown("r/subshell · u/newcomer · 2m"))
    }

    @Test
    fun aReportedCommentShowsItsReasonsAndItsPicture() {
        show(
            RedditModeratorQueueItem(
                fullname = "t1_ccc333",
                kind = RedditModeratorQueueKind.COMMENT,
                communityName = "subshell",
                author = "replier",
                summary = "Image comment",
                reportCount = 2,
                reportDetails = RedditModeratorReportDetails(
                    userReports = listOf(RedditModeratorReportReason("Spam", count = 1)),
                    moderatorReports = listOf(RedditModeratorReportReason("Review image comment", moderatorName = "AutoModerator")),
                    totalCount = 2,
                ),
                details = RedditModeratorQueueItemDetails(
                    commentImageUrl = "https://preview.redd.it/xyz.jpeg?width=320",
                    linkTitle = "Subshell 1.0.2 is out",
                ),
            ),
        )
        assertEquals(1, shown("AutoModerator: Review image comment"))
        assertEquals(1, shown("Reported: Spam"))
        assertEquals(1, shown("On: Subshell 1.0.2 is out"))
        assertEquals(1, shown("2 reports"))
        assertEquals(0, shown(HELD_FOR_REVIEW_LABEL))
        assertEquals(
            1,
            composeRule.onAllNodes(hasTestTag(POST_THUMBNAIL_TEST_TAG), useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    // ISSUE301B_AUTOMOD_HELD_IS_NAMED_V1: AutoModerator is named, in the queue and in Removed.
    private val autoModHeld = RedditModeratorQueueItem(
        fullname = "t3_fff666",
        kind = RedditModeratorQueueKind.POST,
        communityName = "indianrailways",
        author = "traveller",
        summary = "Ticket available",
        reportDetails = RedditModeratorReportDetails(userReports = emptyList(), moderatorReports = emptyList(), totalCount = 0),
        details = RedditModeratorQueueItemDetails(heldByAutoModerator = true),
    )

    @Test
    fun anItemAutoModeratorHeldNamesAutoModeratorInTheQueue() {
        show(autoModHeld)
        assertEquals(1, shown(HELD_FOR_REVIEW_LABEL))
        assertEquals(1, shown(HELD_BY_AUTOMODERATOR_EXPLANATION))
        assertEquals(0, shown(HELD_WITHOUT_REPORTS_EXPLANATION))
        assertEquals(0, shown("Removed by AutoModerator"))
    }

    @Test
    fun anItemAutoModeratorHeldNamesAutoModeratorInRemoved() {
        show(autoModHeld, showsReportCount = false)
        assertEquals(1, shown(HELD_BY_AUTOMODERATOR_EXPLANATION))
        assertEquals(0, shown(HELD_FOR_REVIEW_LABEL))
        assertEquals(0, shown("Removed by AutoModerator"))
    }
}
