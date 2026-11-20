package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.R

import io.github.brealorg.subshell.ui.s
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(qualifiers = "en")
class Settings03CAppearancePreviewPolicyTest {
    @Test
    fun themeLabelsAreProductCopy() {
        assertEquals("System", appearanceThemeLabel(ThemeMode.SYSTEM).s)
        assertEquals("Light", appearanceThemeLabel(ThemeMode.LIGHT).s)
        assertEquals("Dark", appearanceThemeLabel(ThemeMode.DARK).s)
    }

    @Test
    fun motionTrackUsesTheAvailableWidth() {
        assertEquals(0.dp, appearanceMotionTargetX(false, 52.dp, 12.dp))
        assertEquals(40.dp, appearanceMotionTargetX(true, 52.dp, 12.dp))
    }

    @Test
    fun appearanceOwnsThemeAndMotionSectionLabels() {
        assertEquals(R.string.set_section_theme_style, settingsSectionTitle(SettingsDestination.APPEARANCE, R.string.all_appearance))
        assertEquals(R.string.set_section_motion, settingsSectionTitle(SettingsDestination.APPEARANCE, R.string.all_accessibility))
        assertTrue(
            settingsSectionVisible(
                destination = SettingsDestination.APPEARANCE,
                query = "",
                searchResult = settingsSearchResult(""),
                section = "Appearance",
                debug = false,
            ),
        )
    }
    @Test
    fun votePreviewUsesOneSelectedDirectionAtATime() {
        assertEquals(
            AppearanceVoteTone.UPVOTE,
            appearanceVoteTone(
                direction = 1,
                selectedDirection = 1,
                palette = io.github.brealorg.subshell.ui.VoteColorPalette.RED_BLUE,
            ),
        )
        assertEquals(
            AppearanceVoteTone.NEUTRAL,
            appearanceVoteTone(
                direction = -1,
                selectedDirection = 1,
                palette = io.github.brealorg.subshell.ui.VoteColorPalette.RED_BLUE,
            ),
        )
        assertEquals(
            AppearanceVoteTone.DOWNVOTE,
            appearanceVoteTone(
                direction = -1,
                selectedDirection = -1,
                palette = io.github.brealorg.subshell.ui.VoteColorPalette.RED_BLUE,
            ),
        )
        assertEquals(
            AppearanceVoteTone.NEUTRAL,
            appearanceVoteTone(
                direction = 1,
                selectedDirection = -1,
                palette = io.github.brealorg.subshell.ui.VoteColorPalette.RED_BLUE,
            ),
        )
    }

    @Test
    fun themeAccentPreviewUsesAccentOnlyForSelectedVote() {
        assertEquals(
            AppearanceVoteTone.THEME_ACCENT,
            appearanceVoteTone(
                direction = 1,
                selectedDirection = 1,
                palette = io.github.brealorg.subshell.ui.VoteColorPalette.NEUTRAL,
            ),
        )
        assertEquals(
            AppearanceVoteTone.NEUTRAL,
            appearanceVoteTone(
                direction = -1,
                selectedDirection = 1,
                palette = io.github.brealorg.subshell.ui.VoteColorPalette.NEUTRAL,
            ),
        )
    }

    @Test
    fun previewReplayAlternatesSelectedVoteDirection() {
        assertEquals(1, appearancePreviewSelectedVoteDirection(motionAtEnd = false))
        assertEquals(-1, appearancePreviewSelectedVoteDirection(motionAtEnd = true))
    }

}
