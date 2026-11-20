package io.github.brealorg.subshell.ui

/**
 * ISSUE302_START_FEED_V1 (#302, owner 2026-11-13): the feed the app opens on. Home keeps today's
 * landing (Popular for an account with authoritatively zero subscriptions); Popular and All are
 * chosen at once, without waiting for the subscription list.
 */
enum class StartFeed(@androidx.annotation.StringRes val labelRes: Int, val source: FeedSource) {
    HOME( io.github.brealorg.subshell.R.string.start_feed_home, FeedSource.HOME),
    POPULAR( io.github.brealorg.subshell.R.string.start_feed_popular, FeedSource.POPULAR),
    ALL( io.github.brealorg.subshell.R.string.start_feed_all, FeedSource.ALL),
}

/**
 * ISSUE300_DEFAULT_FEED_SORT_V1 (#300): the sort a feed opens with at a start or landing. Popular
 * and All have no Best on Reddit, so Best opens them on Hot (as [normalizedFeedSort] does).
 */
internal fun startFeedSort(source: FeedSource, defaultSort: FeedSort): FeedSort =
    normalizedFeedSort(source, defaultSort)
