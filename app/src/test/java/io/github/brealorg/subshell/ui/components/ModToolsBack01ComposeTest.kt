package io.github.brealorg.subshell.ui.components

import androidx.activity.ComponentDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.brealorg.subshell.data.account.RedditModerationUiState
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * MODTOOLSBACK01_EVERY_BACK_COUNTS_V1: Back goes to the sheet's own dialog
 * window: one Back steps a tool back to the root, the next closes the sheet.
 * On the Pixel 11 (2026-11-15) one Back at the root seemed lost; 2026-11-16
 * showed why: a predictive Back gesture from a tool left the sheet shrunk at
 * the root (MODTOOLSBACK01_BACK_STEPS_INSIDE_THE_SHEET_V1). Robolectric does not
 * run M3's platform back animation, so that part was checked on the phone only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ModToolsBack01ComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var destination by mutableStateOf(ModeratorToolsWorkspaceDestination.MOD_LOG)
    private var dismissed = 0
    private val returns = mutableListOf<ModeratorToolsWorkspaceDestination>()

    private fun sheet() {
        val nav = ModeratorToolsNavigationEntryPoints(
            isRootVisible = { true },
            isVisible = { true },
            destinationFor = { destination },
            openChild = { _, child -> destination = child },
            returnToRoot = { _, from ->
                returns += from
                destination = ModeratorToolsWorkspaceDestination.ROOT
            },
        )
        val content = ModeratorToolsWorkspaceContentEntryPoints(
            modLog = { _ -> Text("The mod log") },
        )
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(
                    LocalModeratorToolsNavigation provides nav,
                    LocalModeratorToolsWorkspaceContent provides content,
                ) {
                    ModeratorToolsSheet(
                        communityLabel = "r/subshell",
                        communityName = "subshell",
                        accountStableId = "reddit:mod",
                        uiState = RedditModerationUiState(accountStableId = "reddit:mod", queueCommunityName = "subshell", queueLoaded = true),
                        onDismiss = { dismissed++ },
                        onRefresh = {},
                        onAppend = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        val window = ShadowDialog.getLatestDialog() as ComponentDialog
        composeRule.runOnIdle { window.onBackPressedDispatcher.onBackPressed() }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    @Test
    fun backStepsAToolToTheRootAndTheNextBackClosesTheSheet() {
        sheet()

        pressBack()
        assertEquals(listOf(ModeratorToolsWorkspaceDestination.MOD_LOG), returns)
        assertEquals(ModeratorToolsWorkspaceDestination.ROOT, destination)
        assertEquals(0, dismissed)

        pressBack()
        assertEquals(1, dismissed)
    }
}
