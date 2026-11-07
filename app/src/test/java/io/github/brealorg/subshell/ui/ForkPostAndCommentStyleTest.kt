package io.github.brealorg.subshell.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * POSTSTYLE_EDGE_TO_EDGE_V1 and COMMENTSTYLE_COMPACT_V1 (fork): both styles are opt-in, so a
 * fresh install and an old backup look exactly as before.
 */
class ForkPostAndCommentStyleTest {
    @Test
    fun theOldLookIsTheDefault() {
        val defaults = SubshellSettings()
        assertEquals(PostCardStyle.CARD, defaults.postCardStyle)
        assertEquals(CommentLayoutStyle.STANDARD, defaults.commentLayoutStyle)
    }

    @Test
    fun keysKeepTheirSpellingAndAreBackedUp() {
        assertEquals("post_card_style", SubshellSettings.POST_CARD_STYLE)
        assertEquals("comment_layout_style", SubshellSettings.COMMENT_LAYOUT_STYLE)
        assertTrue(SubshellSettings.POST_CARD_STYLE in SubshellSettings.storageKeys)
        assertTrue(SubshellSettings.COMMENT_LAYOUT_STYLE in SubshellSettings.storageKeys)
        val chosen = SubshellSettings(
            postCardStyle = PostCardStyle.EDGE_TO_EDGE,
            commentLayoutStyle = CommentLayoutStyle.COMPACT,
        ).toStorageMap()
        assertEquals("EDGE_TO_EDGE", chosen[SubshellSettings.POST_CARD_STYLE])
        assertEquals("COMPACT", chosen[SubshellSettings.COMMENT_LAYOUT_STYLE])
    }

    @Test
    fun statePersistsBothChoices() {
        val persisted = linkedMapOf<String, String>()
        val state = SubshellState(persistPreference = { key, value -> persisted[key] = value })

        state.updatePostCardStyle(PostCardStyle.EDGE_TO_EDGE)
        state.updateCommentLayoutStyle(CommentLayoutStyle.COMPACT)

        assertEquals(PostCardStyle.EDGE_TO_EDGE, state.postCardStyle)
        assertEquals(CommentLayoutStyle.COMPACT, state.commentLayoutStyle)
        assertEquals("EDGE_TO_EDGE", persisted[SubshellSettings.POST_CARD_STYLE])
        assertEquals("COMPACT", persisted[SubshellSettings.COMMENT_LAYOUT_STYLE])
    }

    @Test
    fun onlyLargePostsRunEdgeToEdge() {
        val state = SubshellState()
        assertEquals(false, feedEdgeToEdge(state, PostLayout.LARGE))
        state.updatePostCardStyle(PostCardStyle.EDGE_TO_EDGE)
        assertEquals(true, feedEdgeToEdge(state, PostLayout.LARGE))
        assertEquals(false, feedEdgeToEdge(state, PostLayout.COMPACT))
        assertEquals(false, feedEdgeToEdge(state, PostLayout.LIST))
        assertEquals(false, feedEdgeToEdge(state, PostLayout.GRID))
    }

    @Test
    fun edgeToEdgeTakesTheListSidesAndTheGapAway() {
        val padding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp)
        val flat = padding.withoutSidesWhen(true)
        assertEquals(0.dp, flat.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr))
        assertEquals(0.dp, flat.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr))
        assertEquals(8.dp, flat.calculateTopPadding())
        assertEquals(96.dp, flat.calculateBottomPadding())
        assertEquals(padding, padding.withoutSidesWhen(false))

        assertEquals(9.dp, feedItemSpacing(PostLayout.LARGE, 9.dp))
        assertEquals(0.dp, feedItemSpacing(PostLayout.LARGE, 9.dp, edgeToEdge = true))
        assertEquals(8.dp, feedItemSpacing(PostLayout.COMPACT, 9.dp, edgeToEdge = true))
    }

    @Test
    fun compactCommentsHaveNoThreadCards() {
        val state = SubshellState()
        state.updateCommentThreadColours(CommentThreadColours.BY_DEPTH)
        assertEquals(CommentThreadColours.BY_DEPTH, state.effectiveCommentThreadColours)
        state.updateCommentLayoutStyle(CommentLayoutStyle.COMPACT)
        assertEquals(CommentThreadColours.OFF, state.effectiveCommentThreadColours)
    }
}
