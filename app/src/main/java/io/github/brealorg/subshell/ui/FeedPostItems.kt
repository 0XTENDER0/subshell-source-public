package io.github.brealorg.subshell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.model.Post

/**
 * ISSUE234_FEED_POST_LAYOUTS_V1 (owner 2026-10-18): Grid is rows of tiles inside
 * the feed's own LazyColumn (owner chose even rows), so scroll anchors,
 * auto-append, pull-to-refresh and scroll-to-top keep their one list type. A
 * row's key is its first post's key; [feedPostItemKeys] lists the keys in the
 * order the list emits them.
 */
internal const val FEED_GRID_MIN_TILE_WIDTH_DP = 180
private const val FEED_GRID_MAX_COLUMNS = 6

internal fun feedGridColumnsFor(widthDp: Float): Int =
    (widthDp / FEED_GRID_MIN_TILE_WIDTH_DP).toInt().coerceIn(2, FEED_GRID_MAX_COLUMNS)

/** Grid only where a list holds posts alone; a list mixing posts with other rows shows List instead. */
internal fun PostLayout.forFeed(gridAllowed: Boolean): PostLayout =
    if (this == PostLayout.GRID && !gridAllowed) PostLayout.LIST else this

/**
 * POSTSTYLE_EDGE_TO_EDGE_V1 (fork): true when Large posts are drawn without a card or side margin.
 * Only the Large layout has cards, so only it has an edge-to-edge form.
 */
internal fun feedEdgeToEdge(state: SubshellState, layout: PostLayout): Boolean =
    layout == PostLayout.LARGE && state.postCardStyle == PostCardStyle.EDGE_TO_EDGE

/**
 * The feed list's padding without its sides, for a feed whose posts run edge to edge. The rows
 * that are not posts (banners, empty states, footers) get the sides back through [feedRowItem].
 */
internal fun PaddingValues.withoutSidesWhen(edgeToEdge: Boolean): PaddingValues =
    if (edgeToEdge) {
        PaddingValues(top = calculateTopPadding(), bottom = calculateBottomPadding())
    } else {
        this
    }

/** A feed row that is not a post; it keeps the screen margin even when the posts have none. */
internal fun LazyListScope.feedRowItem(
    key: Any,
    edgeToEdge: Boolean,
    content: @Composable () -> Unit,
) {
    item(key = key) {
        Box(if (edgeToEdge) Modifier.padding(horizontal = ScreenMargin) else Modifier) { content() }
    }
}

/** The gap between feed items; Large keeps each screen's own spacing. */
internal fun feedItemSpacing(layout: PostLayout, largeSpacing: Dp, edgeToEdge: Boolean = false): Dp = when (layout) {
    PostLayout.LARGE -> if (edgeToEdge) 0.dp else largeSpacing
    PostLayout.COMPACT, PostLayout.GRID -> 8.dp
    PostLayout.LIST -> 2.dp
}

internal fun feedPostItemKeys(
    posts: List<Post>,
    layout: PostLayout,
    columns: Int,
    key: (Post) -> String = Post::id,
): List<String> = if (layout == PostLayout.GRID) {
    posts.chunked(columns.coerceAtLeast(1)).map { row -> key(row.first()) }
} else {
    posts.map(key)
}

/**
 * [horizontalPadding] is the margin a screen gives each post; in Grid it goes on
 * the row, so [content]'s modifier carries it only outside Grid.
 */
internal fun LazyListScope.feedPostItems(
    posts: List<Post>,
    layout: PostLayout,
    columns: Int,
    key: (Post) -> String = Post::id,
    horizontalPadding: Dp = 0.dp,
    content: @Composable (post: Post, modifier: Modifier) -> Unit,
) {
    if (layout != PostLayout.GRID) {
        items(posts, key = key) { post -> content(post, Modifier.padding(horizontal = horizontalPadding)) }
        return
    }
    val safeColumns = columns.coerceAtLeast(1)
    items(posts.chunked(safeColumns), key = { row -> key(row.first()) }) { row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { post ->
                Box(Modifier.weight(1f)) { content(post, Modifier) }
            }
            repeat(safeColumns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Measures the list it is applied to and says how many grid columns fit. */
@Stable
internal class FeedGridColumns(private val density: Density) {
    var count by mutableIntStateOf(2)
        private set

    val measureModifier: Modifier = Modifier.onSizeChanged { size ->
        val widthDp = with(density) { size.width.toDp().value }
        count = feedGridColumnsFor(widthDp - 2 * GRID_SIDE_MARGIN_DP)
    }

    private companion object {
        const val GRID_SIDE_MARGIN_DP = 16f
    }
}

@Composable
internal fun rememberFeedGridColumns(): FeedGridColumns {
    val density = LocalDensity.current
    return remember(density) { FeedGridColumns(density) }
}
