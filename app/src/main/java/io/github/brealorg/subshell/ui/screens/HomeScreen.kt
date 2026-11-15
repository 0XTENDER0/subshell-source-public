package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import android.util.Log
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.BuildConfig
import io.github.brealorg.subshell.data.RepositoryLoadOperation
import io.github.brealorg.subshell.data.RedditListingTimePeriod
import io.github.brealorg.subshell.data.RedditFeedSort
import io.github.brealorg.subshell.data.RedditMainFeed
import io.github.brealorg.subshell.data.account.RedditFeedUiFailureKind
import io.github.brealorg.subshell.data.account.RedditFeedUiState
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.ui.FeedPostEntryTarget
import io.github.brealorg.subshell.ui.PostLayout
import io.github.brealorg.subshell.ui.feedItemSpacing
import io.github.brealorg.subshell.ui.feedPostItemKeys
import io.github.brealorg.subshell.ui.feedPostItems
import io.github.brealorg.subshell.ui.rememberFeedGridColumns
import io.github.brealorg.subshell.ui.ScreenMargin
import io.github.brealorg.subshell.ui.components.FilteredPostsSummaryRow
import io.github.brealorg.subshell.ui.isDeepForAutomaticRefresh
import io.github.brealorg.subshell.ui.FindTheResumedFeedPlace
import io.github.brealorg.subshell.ui.resumedFeedEntry
import io.github.brealorg.subshell.ui.FeedPreviewState
import io.github.brealorg.subshell.ui.FeedSort
import io.github.brealorg.subshell.ui.ObserveFeedVideoViewport
import io.github.brealorg.subshell.ui.GoogleFeedVideoViewport
import io.github.brealorg.subshell.ui.ObserveLiveFeedAutoAppend
import io.github.brealorg.subshell.ui.rememberTerminalVideoId
import io.github.brealorg.subshell.ui.selectHomeFeedPosts
import io.github.brealorg.subshell.ui.selectLiveHomePosts
import io.github.brealorg.subshell.ui.FeedSource
import io.github.brealorg.subshell.ui.filterMutedCommunityPosts
import io.github.brealorg.subshell.ui.captureFeedScrollAnchor
import io.github.brealorg.subshell.ui.canAppendRedditFeed
import io.github.brealorg.subshell.ui.mainFeedScrollKey
import io.github.brealorg.subshell.ui.mainPostFeedQueryKey
import io.github.brealorg.subshell.ui.pagingFooterKey
import io.github.brealorg.subshell.ui.requireRedditMainFeed
import io.github.brealorg.subshell.ui.rememberFeedScrollState
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.symbols.Symbols
import io.github.brealorg.subshell.ui.toRedditFeedSort
import io.github.brealorg.subshell.ui.toRedditMainFeedOrNull
import io.github.brealorg.subshell.ui.supportsTimePeriod
import io.github.brealorg.subshell.ui.components.ErrorNotice
import io.github.brealorg.subshell.ui.components.PostCard
import io.github.brealorg.subshell.ui.components.RepositoryPagingFooter
import io.github.brealorg.subshell.ui.components.RefreshableLazyColumn
import io.github.brealorg.subshell.ui.error.ErrorActionKind
import io.github.brealorg.subshell.ui.error.ReportViewportError
import io.github.brealorg.subshell.ui.error.redditFeedAppendErrorPresentation
import io.github.brealorg.subshell.ui.error.redditFeedErrorPresentation
import io.github.brealorg.subshell.ui.uiDebugLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.brealorg.subshell.ui.components.InlineProgressIndicator

private const val FEED04B_BENCHMARK_PROBE_LOG_TAG = "subshell-feed04b-probe"

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    state: SubshellState,
    redditFeedState: RedditFeedUiState = RedditFeedUiState(),
    onRefreshRedditFeed: (
        String,
        RedditMainFeed,
        RedditFeedSort,
        RedditListingTimePeriod,
    ) -> Unit = { _, _, _, _ -> },
    onAppendRedditFeed: (
        String,
        RedditMainFeed,
        RedditFeedSort,
        RedditListingTimePeriod,
    ) -> Unit = { _, _, _, _ -> },
    authenticatedLandingPending: Boolean = false,
    authenticatedHomeWithoutSubscriptions: Boolean = false,
    onBrowsePopular: () -> Unit = {},
    onFindCommunities: () -> Unit = {},
    onOpenPostSelection: ((Post, Boolean) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(start = ScreenMargin, top = 8.dp, end = ScreenMargin, bottom = 96.dp),
    videoViewportPadding: PaddingValues = PaddingValues(0.dp),
) {
    val authenticated = state.activeAccountIsAuthenticated
    val liveMainFeed = state.feedSource.toRedditMainFeedOrNull()
    val authenticatedRedditFeed = authenticated && liveMainFeed != null
    val liveSort = state.feedSort.toRedditFeedSort()
    val pagingKey = remember(
        state.activeAccountSession,
        state.feedSource,
        state.feedSort,
        state.listingTimePeriod,
    ) {
        mainPostFeedQueryKey(
            account = state.activeAccountSession,
            source = state.feedSource,
            sort = state.feedSort,
            timePeriod = state.listingTimePeriod,
        )
    }
    val activeRedditFeedState = if (
        redditFeedState.matches(
            accountStableId = state.activeAccountId,
            queryKey = pagingKey,
        )
    ) {
        redditFeedState
    } else {
        RedditFeedUiState()
    }
    val successfulEmptyRedditListing = authenticatedRedditFeed &&
        activeRedditFeedState.lastAppliedOperation != null &&
        activeRedditFeedState.failureKind == null &&
        !activeRedditFeedState.inProgress
    val activeRedditListingLabel = buildString {
        append(stringResource(state.feedSource.labelRes))
        append(" · ")
        append(stringResource(state.feedSort.labelRes))
        if (state.feedSort.supportsTimePeriod) {
            append(" · ")
            append(stringResource(state.listingTimePeriod.labelRes))
        }
    }
    LaunchedEffect(
        pagingKey,
        state.postPagingInvalidationRevision,
        authenticatedLandingPending,
        authenticatedHomeWithoutSubscriptions,
        state.feedPositionsResumePending,
    ) {
        if (
            !authenticatedLandingPending &&
            !authenticatedHomeWithoutSubscriptions
        ) {
            state.ensurePostPaging(pagingKey)
            // ISSUE290_FEEDS_RESUME_WHERE_YOU_LEFT_OFF_V1: the first entry waits
            // for the position file at a cold start.
            if (authenticatedRedditFeed && !state.feedPositionsResumePending) {
                val scrollKey = mainFeedScrollKey(
                    state.feedSource,
                    state.feedSort,
                    state.listingTimePeriod.takeIf { authenticatedRedditFeed }
                        ?: RedditListingTimePeriod.ALL,
                )
                val resumed = resumedFeedEntry(state, scrollKey, pagingKey)
                // ISSUE17_AUTHENTICATED_FEED_ENTRY_REVALIDATION_V1
                val decision = state.claimFeedEntryRefresh(
                    pagingKey,
                    deepInList = state.feedScrollAnchor(scrollKey).isDeepForAutomaticRefresh(),
                    resumed = resumed,
                )
                uiDebugLog(
                    "subshell-issue17",
                    "entry-refresh surface=home source=${state.feedSource.name.lowercase()} sort=${state.feedSort.name.lowercase()} query=${pagingKey.value} decision=${decision.logToken} cached=${state.postPagingSnapshot(pagingKey).items.size}",
                )
                if (decision.execute) {
                    onRefreshRedditFeed(
                        state.activeAccountId,
                        state.feedSource.requireRedditMainFeed(),
                        liveSort,
                        state.listingTimePeriod,
                    )
                }
            }
        }
    }
    val pagingSnapshot = state.postPagingSnapshot(pagingKey)
    // ISSUE257_HOME_SAYS_LOADING_UNTIL_ITS_STORE_IS_READ_V1 (#257): right after
    // the landing the stored page is still being read; until then Home says
    // it is loading instead of "No cached Reddit posts" with a Refresh button.
    val storedFeedRead = state.postPagingStoreRead(pagingKey)
    val allPosts = pagingSnapshot.items
    // FEEDS04_DERIVED_ID_SET_READS_V1: the id lists are read inside
    // derivedStateOf instead of being copied as remember keys on every
    // recomposition (readPostIds can hold thousands).
    val visiblePostsWithoutContentFilters by remember(
        allPosts,
        state.feedSort,
        state.feedSource,
        state.hideReadPosts,
        authenticated,
        authenticatedRedditFeed,
    ) {
        derivedStateOf {
            if (authenticatedRedditFeed) {
                selectLiveHomePosts(
                    posts = allPosts,
                    hiddenPostIds = state.hiddenPostIds,
                    readPostIds = state.readPostIds,
                    hideReadPosts = state.hideReadPosts,
                )
            } else if (authenticated) {
                emptyList()
            } else {
                selectHomeFeedPosts(
                    posts = allPosts,
                    source = state.feedSource,
                    sort = state.feedSort,
                    joinedCommunityIds = state.joinedCommunityIds,
                    savedPostIds = state.savedPostIds,
                    hiddenPostIds = state.hiddenPostIds,
                    readPostIds = state.readPostIds,
                    hideReadPosts = state.hideReadPosts,
                )
            }
        }
    }
    val nextCommunityMuteExpiry = state.nextCommunityMuteExpiryEpochMillis
    LaunchedEffect(state.activeAccountId, nextCommunityMuteExpiry) {
        val delayMillis = state.millisUntilNextCommunityMuteExpiry()
            ?: return@LaunchedEffect
        delay(delayMillis + 50L)
        state.refreshCommunityMuteClock()
    }
    val activeMutedCommunityNames by remember(state.activeAccountId, state.communityMuteClockEpochMillis) {
        derivedStateOf {
            state.activeMutedCommunityNames()
        }
    }
    val postsAfterCommunityMutes = remember(
        visiblePostsWithoutContentFilters,
        state.feedSource,
        activeMutedCommunityNames,
    ) {
        filterMutedCommunityPosts(
            posts = visiblePostsWithoutContentFilters,
            source = state.feedSource,
            mutedCommunityNames = activeMutedCommunityNames,
        )
    }
    LaunchedEffect(
        state.activeAccountId,
        state.feedSource,
        activeMutedCommunityNames,
        visiblePostsWithoutContentFilters,
        postsAfterCommunityMutes,
    ) {
        if (BuildConfig.DEBUG && activeMutedCommunityNames.isNotEmpty()) {
            val mutedKeys = activeMutedCommunityNames
                .mapTo(linkedSetOf()) { communityName -> communityName.lowercase() }
            val matchCounts = visiblePostsWithoutContentFilters
                .groupingBy { post ->
                    post.community
                        .trim()
                        .removePrefix("r/")
                        .lowercase()
                }
                .eachCount()
                .filterKeys { communityName -> communityName in mutedKeys }
                .entries
                .sortedBy { entry -> entry.key }
                .joinToString(separator = ",") { (communityName, count) ->
                    "$communityName:$count"
                }
                .ifBlank { "none" }
            uiDebugLog(
                "SubshellMute",
                "phase=projection account=${state.activeAccountId} source=${state.feedSource.name} active=${mutedKeys.sorted().joinToString(",")} input=${visiblePostsWithoutContentFilters.size} suppressed=${visiblePostsWithoutContentFilters.size - postsAfterCommunityMutes.size} matches=$matchCounts",
            )
        }
    }
    val postsWithoutFilteredPosts = remember(
        postsAfterCommunityMutes,
        state.contentFilters,
    ) {
        postsAfterCommunityMutes.filterNot(state.contentFilters::hides)
    }
    // CONTENTFILTER04_FILTERED_SUMMARY_V1: optional count row; Show lets the
    // filtered posts through for this feed until Hide or leaving it.
    var filteredRevealed by rememberSaveable(pagingKey.value) { mutableStateOf(false) }
    // CONTENTFILTER06_HOME_COUNT_AFTER_NSFW_V1: count only posts the NSFW
    // setting would show, as the other feeds do.
    val filteredCount = remember(postsAfterCommunityMutes, state.contentFilters, state.showNsfwPosts) {
        postsAfterCommunityMutes.count { post -> state.showsPost(post) && state.contentFilters.hides(post) }
    }
    val filteredSummaryVisible = state.filteredPostsSummary &&
        filteredCount > 0 &&
        !authenticatedLandingPending &&
        !authenticatedHomeWithoutSubscriptions
    val postsAfterContentFilters = if (filteredSummaryVisible && filteredRevealed) {
        postsAfterCommunityMutes
    } else {
        postsWithoutFilteredPosts
    }
    val visiblePosts = remember(
        postsAfterContentFilters,
        state.showNsfwPosts,
        authenticatedLandingPending,
        authenticatedHomeWithoutSubscriptions,
    ) {
        if (
            authenticatedLandingPending ||
            authenticatedHomeWithoutSubscriptions
        ) {
            emptyList()
        } else {
            postsAfterContentFilters.filter(state::showsPost)
        }
    }
    val benchmarkProbeDescription = if (BuildConfig.FEED04B_BENCHMARK_PROBES) {
        remember(
            authenticatedRedditFeed,
            state.feedSource,
            state.feedSort,
            pagingSnapshot.generation,
            pagingSnapshot.nextCursor,
            allPosts,
            visiblePosts,
            activeRedditFeedState.inProgress,
            activeRedditFeedState.lastAppliedOperation,
            activeRedditFeedState.failureKind,
            activeRedditFeedState.contentRevision,
        ) {
            val visibleRemotePosts = visiblePosts.count { post ->
                post.remoteImageUri != null ||
                    post.remoteVideoUri != null ||
                    post.gallery.any { item -> item.remoteImageUri != null }
            }
            val visibleKinds = PostKind.entries.joinToString(separator = ",") { kind ->
                "${kind.name.lowercase()}:${visiblePosts.count { it.kind == kind }}"
            }
            buildString {
                append("subshell_feed_probe")
                append(" source=").append(if (authenticatedRedditFeed) "reddit" else "fixture")
                append(" feed=").append(state.feedSource.name.lowercase())
                append(" sort=").append(state.feedSort.name.lowercase())
                append(" generation=").append(pagingSnapshot.generation)
                append(" revision=").append(activeRedditFeedState.contentRevision)
                append(" stored=").append(allPosts.size)
                append(" visible=").append(visiblePosts.size)
                append(" in_progress=").append(activeRedditFeedState.inProgress)
                append(" operation=").append(
                    activeRedditFeedState.lastAppliedOperation?.name?.lowercase() ?: "none",
                )
                append(" failure=").append(
                    activeRedditFeedState.failureKind?.name?.lowercase() ?: "none",
                )
                append(" next=").append(pagingSnapshot.nextCursor != null)
                append(" visible_remote=").append(visibleRemotePosts)
                append(" visible_kinds=").append(visibleKinds)
            }
        }
    } else {
        null
    }
    LaunchedEffect(benchmarkProbeDescription) {
        benchmarkProbeDescription?.let { description ->
            Log.i(FEED04B_BENCHMARK_PROBE_LOG_TAG, description)
        }
    }
    val visibleCommunityNames = remember(visiblePosts) {
        visiblePosts.map(Post::community).distinct()
    }
    LaunchedEffect(visibleCommunityNames, state.activeAccountSession) {
        state.ensureCommunityIdentities(visibleCommunityNames)
    }
    val communityMutesHideAllPosts =
        postsAfterCommunityMutes.isEmpty() &&
            visiblePostsWithoutContentFilters.isNotEmpty() &&
            state.feedSource != FeedSource.SAVED
    val contentFiltersHideAllPosts =
        postsAfterContentFilters.isEmpty() && postsAfterCommunityMutes.isNotEmpty()
    // LOWUI8_HIDDEN_POSTS_ARE_NAMED_WHEN_THEY_EMPTY_HOME_V1 (OCB-217): every
    // loaded post hidden by the reader is not an empty listing from Reddit.
    val hiddenPostsHideAllPosts =
        allPosts.isNotEmpty() &&
            visiblePostsWithoutContentFilters.isEmpty() &&
            allPosts.all { post -> post.id in state.hiddenPostIds }
    val nsfwPreferenceHidesAllPosts =
        !state.showNsfwPosts &&
            visiblePosts.isEmpty() &&
            postsAfterContentFilters.any(Post::nsfw)
    val liveAppendEnabled = canAppendRedditFeed(
        live = authenticatedRedditFeed &&
            !authenticatedLandingPending &&
            !authenticatedHomeWithoutSubscriptions,
        nextCursor = pagingSnapshot.nextCursor,
        state = activeRedditFeedState,
    )
    val liveReadFailure = activeRedditFeedState.failureKind?.takeUnless { failure ->
        failure == RedditFeedUiFailureKind.END_REACHED
    }
    val showLiveReadError =
        authenticatedRedditFeed &&
            !authenticatedLandingPending &&
            !authenticatedHomeWithoutSubscriptions &&
            liveReadFailure != null
    // ISSUE248_FIRST_FEED_PAGE_REPORTS_FULLY_DRAWN_V1 (#248): the start counts
    // as fully drawn once Home shows posts or a settled answer (an empty
    // listing, a read failure, no subscriptions), so StartupTimingMetric's
    // timeToFullDisplay measures a usable feed, not the first frame. Reported
    // once per activity; a start on another screen reports nothing.
    val liveFeedSettled = !authenticatedRedditFeed || (
        !activeRedditFeedState.inProgress &&
            (activeRedditFeedState.lastAppliedOperation != null || liveReadFailure != null)
        )
    ReportDrawnWhen {
        visiblePosts.isNotEmpty() ||
            authenticatedHomeWithoutSubscriptions ||
            (!authenticatedLandingPending && liveFeedSettled)
    }
    val degradedFeedErrorPresentation = if (
        liveReadFailure != null &&
        showLiveReadError &&
        visiblePosts.isNotEmpty()
    ) {
        if (activeRedditFeedState.failedOperation == RepositoryLoadOperation.APPEND) {
            redditFeedAppendErrorPresentation(
                failureKind = liveReadFailure,
                details = activeRedditFeedState.failureDetails,
                retryable = activeRedditFeedState.failureRetryable,
            )
        } else {
            redditFeedErrorPresentation(
                failureKind = liveReadFailure,
                details = activeRedditFeedState.failureDetails,
                retryable = activeRedditFeedState.failureRetryable,
                showingCachedContent = true,
            )
        }
    } else {
        null
    }
    ReportViewportError(
        scopeKey = "home-feed:${pagingKey.value}",
        presentation = degradedFeedErrorPresentation,
        failureDetails = activeRedditFeedState.failureDetails,
        onRetry = if (degradedFeedErrorPresentation != null) {
            if (activeRedditFeedState.failedOperation == RepositoryLoadOperation.APPEND) {
                {
                    onAppendRedditFeed(
                        state.activeAccountId,
                        state.feedSource.requireRedditMainFeed(),
                        liveSort,
                        state.listingTimePeriod,
                    )
                }
            } else {
                {
                    onRefreshRedditFeed(
                        state.activeAccountId,
                        state.feedSource.requireRedditMainFeed(),
                        liveSort,
                        state.listingTimePeriod,
                    )
                }
            }
        } else {
            null
        },
    )
    val showLivePagingStatus =
        authenticatedRedditFeed &&
            !authenticatedLandingPending &&
            !authenticatedHomeWithoutSubscriptions &&
            (storedFeedRead || activeRedditFeedState.inProgress) && (
        activeRedditFeedState.inProgress ||
            activeRedditFeedState.failureKind == RedditFeedUiFailureKind.END_REACHED ||
            visiblePosts.isEmpty() ||
            pagingSnapshot.nextCursor == null
        )

    val pagingFooterKey = pagingSnapshot.pagingFooterKey.takeUnless {
        authenticatedLandingPending ||
            authenticatedHomeWithoutSubscriptions
    }
    // ISSUE234_FEED_POST_LAYOUTS_V1
    val postLayout = state.postLayout
    val gridColumns = rememberFeedGridColumns()
    val feedItemKeys = remember(
        state.feedPreviewState,
        visiblePosts,
        postLayout,
        gridColumns.count,
        pagingFooterKey,
        authenticated,
        authenticatedRedditFeed,
        showLiveReadError,
        showLivePagingStatus,
        filteredSummaryVisible,
    ) {
        buildList {
            if (state.feedPreviewState == FeedPreviewState.OFFLINE) add("offline-banner")
            if (showLiveReadError && visiblePosts.isEmpty()) add("reddit-read-error")
            when (state.feedPreviewState) {
                FeedPreviewState.LOADING -> repeat(4) { index -> add("skeleton-$index") }
                FeedPreviewState.ERROR -> add("error")
                FeedPreviewState.EMPTY -> add("empty-preview")
                FeedPreviewState.CONTENT, FeedPreviewState.OFFLINE -> {
                    if (filteredSummaryVisible) add("filtered-summary")
                    if (visiblePosts.isEmpty()) {
                        add("empty-results")
                    } else {
                        addAll(feedPostItemKeys(visiblePosts, postLayout, gridColumns.count))
                    }
                }
            }
            pagingFooterKey?.let(::add)
            if (showLivePagingStatus) add("reddit-paging")
        }
    }
    val listState = rememberFeedScrollState(
        state = state,
        scrollKey = mainFeedScrollKey(
            state.feedSource,
            state.feedSort,
            state.listingTimePeriod.takeIf { authenticatedRedditFeed }
                ?: RedditListingTimePeriod.ALL,
        ),
        itemKeys = feedItemKeys,
    )
    val captureCurrentScroll = {
        captureFeedScrollAnchor(
            state = state,
            scrollKey = mainFeedScrollKey(
                state.feedSource,
                state.feedSort,
                state.listingTimePeriod.takeIf { authenticatedRedditFeed }
                    ?: RedditListingTimePeriod.ALL,
            ),
            itemKeys = feedItemKeys,
            listState = listState,
        )
    }

    if (authenticatedRedditFeed) {
        // ISSUE290_LOAD_NEW_FINDS_THE_PLACE_WITHIN_FOUR_PAGES_V1
        FindTheResumedFeedPlace(
            state = state,
            scrollKey = mainFeedScrollKey(
                state.feedSource,
                state.feedSort,
                state.listingTimePeriod,
            ),
            feedState = activeRedditFeedState,
            itemKeys = feedItemKeys,
            canAppend = liveAppendEnabled,
            onAppend = {
                onAppendRedditFeed(
                    state.activeAccountId,
                    state.feedSource.requireRedditMainFeed(),
                    liveSort,
                    state.listingTimePeriod,
                )
            },
        )
    }

    ObserveLiveFeedAutoAppend(
        listState = listState,
        sessionKey = "${pagingKey.value}|generation=${pagingSnapshot.generation}",
        appendEnabled = liveAppendEnabled,
        contentToken = pagingSnapshot.nextCursor,
        loadedItemCount = pagingSnapshot.items.size,
        visibleItemCount = visiblePosts.size,
        onAppend = {
            onAppendRedditFeed(
                state.activeAccountId,
                state.feedSource.requireRedditMainFeed(),
                liveSort,
                state.listingTimePeriod,
            )
        },
    )

    LaunchedEffect(state.homeScrollToTopRequest) {
        if (state.consumeHomeScrollToTopRequest()) {
            listState.animateScrollToItem(0)
        }
    }

    val revealedMediaIds = state.revealedMediaIds.toSet()
    val videoPostIds = remember(visiblePosts, revealedMediaIds, state.showNsfwMedia, postLayout) {
        // ISSUE234_FEED_POST_LAYOUTS_V1: only Large plays media in the feed.
        visiblePosts
            .filter { postLayout == PostLayout.LARGE }
            .filter { it.kind == PostKind.VIDEO && state.sensitiveMediaAutoplayAllowed(it) }
            .map(Post::id)
    }
    val terminalVideoId = rememberTerminalVideoId(videoPostIds, pagingSnapshot.endReached)
    LaunchedEffect(videoPostIds, terminalVideoId) {
        state.updateVideoFeedContext(videoPostIds, terminalVideoId)
    }

    ObserveFeedVideoViewport(listState = listState, state = state)

    val animatedPostIds = remember(visiblePosts, revealedMediaIds, state.showNsfwMedia, postLayout) {
        visiblePosts
            .filter { postLayout == PostLayout.LARGE }
            .filter {
                it.kind in listOf(PostKind.GIF, PostKind.GALLERY) &&
                    state.sensitiveMediaAutoplayAllowed(it)
            }
            .mapTo(mutableSetOf()) { it.id }
    }
    LaunchedEffect(listState, animatedPostIds, state.videoAutoplayAllowed) {
        if (!state.videoAutoplayAllowed) {
            state.setFeedAutoplayAnimatedMedia(null)
        } else {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo
                    .filter { it.key in animatedPostIds }
                    .maxByOrNull { item ->
                        val start = item.offset.coerceAtLeast(0)
                        val end = (item.offset + item.size).coerceAtMost(listState.layoutInfo.viewportEndOffset)
                        (end - start).coerceAtLeast(0)
                    }
                    ?.key as? String
            }.distinctUntilChanged().collect(state::setFeedAutoplayAnimatedMedia)
        }
    }
    // PARITY01_FEED_SURFACE_CLEARS_ONLY_WHAT_IT_OWNS_V1
    DisposableEffect(Unit) {
        val owner = Any()
        state.claimFeedSurface(owner)
        onDispose { state.releaseFeedSurface(owner) }
    }

    GoogleFeedVideoViewport(
        state = state,
        sessionKey = pagingKey,
        modifier = modifier.fillMaxSize(),
        viewportPadding = videoViewportPadding,
    ) {
        RefreshableLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    testTagsAsResourceId = true
                    benchmarkProbeDescription?.let { contentDescription = it }
                }
                .testTag(HOME_FEED_TEST_TAG)
                .then(gridColumns.measureModifier),
            state = listState,
            // ISSUE205_PASSIVE_SCROLL_POSITION_INDICATOR_V1
            scrollPositionIndicator = state.scrollPositionIndicator,
            contentPadding = contentPadding,
            indicatorTopPadding = contentPadding.calculateTopPadding(),
            verticalArrangement = Arrangement.spacedBy(feedItemSpacing(postLayout, 9.dp)),
            isRefreshing = if (
                authenticatedRedditFeed &&
                !authenticatedLandingPending &&
                !authenticatedHomeWithoutSubscriptions
            ) {
                activeRedditFeedState.refreshing
            } else {
                !authenticated && pagingSnapshot.refreshing
            },
            onRefresh = when {
                authenticatedRedditFeed &&
                    !authenticatedLandingPending &&
                    !authenticatedHomeWithoutSubscriptions -> {
                    {
                        onRefreshRedditFeed(
                            state.activeAccountId,
                            state.feedSource.requireRedditMainFeed(),
                            liveSort,
                            state.listingTimePeriod,
                        )
                    }
                }
                authenticated -> null
                else -> {
                    { state.refreshPostPaging(pagingKey) }
                }
            },
            loadNextEnabled = if (authenticatedRedditFeed) {
                liveAppendEnabled
            } else {
                !authenticated && pagingSnapshot.canAppend
            },
            loadNextToken = if (authenticatedRedditFeed) {
                pagingSnapshot.nextCursor
            } else {
                pagingSnapshot.items.size
            },
            onLoadNext = {
                if (authenticatedRedditFeed) {
                    onAppendRedditFeed(
                        state.activeAccountId,
                        state.feedSource.requireRedditMainFeed(),
                        liveSort,
                        state.listingTimePeriod,
                    )
                } else {
                    state.appendPostPaging(pagingKey)
                }
            },
        ) {
            if (state.feedPreviewState == FeedPreviewState.OFFLINE) {
                item(key = "offline-banner") {
                    StatusBanner(
                        icon = { Icon(Symbols.CloudOff, contentDescription = null) },
                        title = if (authenticated) {
                            stringResource(R.string.all_offline_showing_the_cached_reddit_page)
                        } else {
                            stringResource(R.string.all_offline_showing_cached_local_fixtures)
                        },
                        description = if (authenticated) {
                            stringResource(R.string.all_showing_cached_reddit_data_no_network_request)
                        } else {
                            stringResource(R.string.all_nothing_on_this_screen_is_presented_as)
                        },
                    )
                }
            }

            if (
                liveReadFailure != null &&
                showLiveReadError &&
                visiblePosts.isEmpty()
            ) {
                item(key = "reddit-read-error") {
                    // LOWUI8_EMPTY_HOME_RETRY_RESUMES_THE_APPEND_V1 (OCB-216): as the
                    // tray does, a failed load-more is retried as a load-more, not
                    // as a reload from the top.
                    val appendFailed =
                        activeRedditFeedState.failedOperation == RepositoryLoadOperation.APPEND
                    ErrorNotice(
                        presentation = if (appendFailed) {
                            redditFeedAppendErrorPresentation(
                                failureKind = liveReadFailure,
                                details = activeRedditFeedState.failureDetails,
                                retryable = activeRedditFeedState.failureRetryable,
                            )
                        } else {
                            redditFeedErrorPresentation(
                                failureKind = liveReadFailure,
                                details = activeRedditFeedState.failureDetails,
                                retryable = activeRedditFeedState.failureRetryable,
                                showingCachedContent = false,
                            )
                        },
                        diagnosticScopeKey = "home_feed",
                        onAction = { action ->
                            if (action == ErrorActionKind.RETRY && appendFailed) {
                                onAppendRedditFeed(
                                    state.activeAccountId,
                                    state.feedSource.requireRedditMainFeed(),
                                    liveSort,
                                    state.listingTimePeriod,
                                )
                            } else if (action == ErrorActionKind.RETRY) {
                                onRefreshRedditFeed(
                                    state.activeAccountId,
                                    state.feedSource.requireRedditMainFeed(),
                                    liveSort,
                                    state.listingTimePeriod,
                                )
                            }
                        },
                    )
                }
            }

            when (state.feedPreviewState) {
                FeedPreviewState.LOADING -> items(4, key = { "skeleton-$it" }) { SkeletonPost() }
                FeedPreviewState.ERROR -> item(key = "error") {
                    EmptyState(
                        title = stringResource(R.string.feed_could_not_load_the_feed),
                        description = stringResource(R.string.feed_a_deterministic_error_state_for_retry_and),
                        action = {
                            Button(onClick = { state.feedPreviewState = FeedPreviewState.CONTENT }) {
                                Icon(Symbols.Refresh, contentDescription = null)
                                Text(stringResource(R.string.feed_retry), modifier = Modifier.padding(start = 8.dp))
                            }
                        },
                    )
                }
                FeedPreviewState.EMPTY -> item(key = "empty-preview") {
                    EmptyState(stringResource(R.string.all_this_feed_is_empty), stringResource(R.string.all_join_a_community_or_choose_another_feed))
                }
                else -> {
                    if (filteredSummaryVisible) {
                        item(key = "filtered-summary") {
                            FilteredPostsSummaryRow(
                                filteredCount = filteredCount,
                                revealed = filteredRevealed,
                                onToggle = { filteredRevealed = !filteredRevealed },
                            )
                        }
                    }
                    if (visiblePosts.isEmpty()) {
                        item(key = "empty-results") {
                            EmptyState(
                                if (authenticatedLandingPending) {
                                    stringResource(R.string.all_preparing_your_feed)
                                } else if (authenticatedHomeWithoutSubscriptions) {
                                    stringResource(R.string.all_your_home_feed_is_empty)
                                } else if (nsfwPreferenceHidesAllPosts) {
                                    "NSFW posts are hidden"
                                } else if (communityMutesHideAllPosts) {
                                    stringResource(R.string.all_muted_communities_fill_this_page)
                                } else if (contentFiltersHideAllPosts) {
                                    stringResource(R.string.all_all_posts_are_filtered)
                                } else if (state.hideReadPosts && allPosts.any { it.id in state.readPostIds }) {
                                    stringResource(R.string.all_all_posts_are_read)
                                } else if (hiddenPostsHideAllPosts) {
                                    stringResource(R.string.all_all_posts_are_hidden)
                                } else if (authenticated && state.feedSource == FeedSource.SAVED) {
                                    stringResource(R.string.all_nothing_saved_yet)
                                } else if (successfulEmptyRedditListing) {
                                    stringResource(R.string.all_reddit_returned_no_posts)
                                } else if (authenticatedRedditFeed && (activeRedditFeedState.inProgress || !storedFeedRead)) {
                                    // LOWUX3_FIRST_LOAD_SAYS_LOADING_V1 (OCB-218): a first page
                                    // on its way read "No cached Reddit posts / reconnect".
                                    stringResource(R.string.all_loading_from_reddit)
                                } else if (authenticated) {
                                    stringResource(R.string.all_no_cached_reddit_posts)
                                } else if (state.feedSource == FeedSource.SAVED) {
                                    stringResource(R.string.all_nothing_saved_yet)
                                } else {
                                    stringResource(R.string.all_no_local_results)
                                },
                                if (authenticatedLandingPending) {
                                    stringResource(R.string.all_checking_your_joined_communities)
                                } else if (authenticatedHomeWithoutSubscriptions) {
                                    stringResource(R.string.all_join_a_community_to_build_your_home)
                                } else if (nsfwPreferenceHidesAllPosts) {
                                    stringResource(R.string.all_use_the_quick_switch_in_the_navigation)
                                } else if (communityMutesHideAllPosts) {
                                    stringResource(R.string.all_muted_posts_stay_out_of_mixed_feeds)
                                } else if (contentFiltersHideAllPosts) {
                                    stringResource(R.string.all_manage_content_filters_in_settings)
                                } else if (state.hideReadPosts && allPosts.any { it.id in state.readPostIds }) {
                                    stringResource(R.string.all_show_read_posts_from_the_home_sort)
                                } else if (hiddenPostsHideAllPosts) {
                                    stringResource(R.string.all_posts_you_hide_stay_out_of_your)
                                } else if (authenticated && state.feedSource == FeedSource.SAVED) {
                                    stringResource(R.string.all_save_a_post_and_it_will_appear)
                                } else if (successfulEmptyRedditListing) {
                                    "$activeRedditListingLabel returned an empty listing. Try another sort or time period."
                                } else if (authenticatedRedditFeed && (activeRedditFeedState.inProgress || !storedFeedRead)) {
                                    stringResource(R.string.all_the_first_page_is_on_its_way)
                                } else if (authenticated) {
                                    stringResource(R.string.all_pull_to_refresh_or_reconnect_the_account)
                                } else if (state.feedSource == FeedSource.SAVED) {
                                    stringResource(R.string.all_save_a_post_and_it_will_appear)
                                } else {
                                    stringResource(R.string.all_try_another_search)
                                },
                                action = if (
                                    authenticatedHomeWithoutSubscriptions
                                ) {
                                    {
                                        Row(
                                            horizontalArrangement =
                                                Arrangement.spacedBy(8.dp),
                                            verticalAlignment =
                                                Alignment.CenterVertically,
                                        ) {
                                            Button(
                                                onClick = onBrowsePopular,
                                            ) {
                                                Text(stringResource(R.string.feed_browse_popular))
                                            }
                                            TextButton(
                                                onClick = onFindCommunities,
                                            ) {
                                                Text(stringResource(R.string.feed_find_communities))
                                            }
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    } else {
                        feedPostItems(visiblePosts, postLayout, gridColumns.count) { post, itemModifier ->
                            PostCard(
                                modifier = itemModifier,
                                post = post,
                                state = state,
                                layout = postLayout,
                                onOpen = {
                                    // A11Y01A4_HOME_LIST_DETAIL_SELECTION_V1
                                    onOpenPostSelection?.invoke(
                                        post,
                                        false,
                                    ) ?: state.openFromMainFeed(post)
                                },
                                onComments = {
                                    onOpenPostSelection?.invoke(
                                        post,
                                        true,
                                    ) ?: state.openFromMainFeed(
                                        post,
                                        FeedPostEntryTarget.COMMENTS,
                                    )
                                },
                                beforeNavigation = captureCurrentScroll,
                            )
                        }
                    }
                }
            }

            if (pagingFooterKey != null) {
                item(key = pagingFooterKey) {
                    RepositoryPagingFooter(
                        snapshot = pagingSnapshot,
                        onRetry = { state.retryPostPaging(pagingKey) },
                    )
                }
            }
            if (showLivePagingStatus) {
                item(key = "reddit-paging") {
                    RedditPagingControl(
                        state = activeRedditFeedState,
                        hasPosts = visiblePosts.isNotEmpty(),
                        canAppend = pagingSnapshot.nextCursor != null,
                        onRefresh = {
                            onRefreshRedditFeed(
                                state.activeAccountId,
                                state.feedSource.requireRedditMainFeed(),
                                liveSort,
                                state.listingTimePeriod,
                            )
                        },
                    )
                }
            }
        }
    }
}

const val HOME_FEED_TEST_TAG = "subshell_home_feed"

@Composable
internal fun RedditPagingControl(
    state: RedditFeedUiState,
    hasPosts: Boolean,
    canAppend: Boolean,
    onRefresh: () -> Unit,
) {
    val failure = state.failureKind
    val endReached = failure == RedditFeedUiFailureKind.END_REACHED
    if (failure != null && !endReached) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                state.inProgress -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InlineProgressIndicator()
                        Text(
                            if (state.refreshing) {
                                stringResource(R.string.all_refreshing_from_reddit)
                            } else {
                                stringResource(R.string.all_loading_25_more)
                            },
                        )
                    }
                }
                canAppend && !endReached -> {
                    Text(
                        text = stringResource(R.string.feed_more_posts_load_automatically_as_you_scroll),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                !hasPosts && !endReached -> {
                    Button(onClick = onRefresh) {
                        Text(stringResource(R.string.feed_refresh_from_reddit))
                    }
                }
                else -> {
                    Text(
                        text = stringResource(R.string.feed_no_more_posts_in_this_listing),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonPost() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth(0.42f).height(14.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small))
            Box(Modifier.fillMaxWidth(0.86f).height(24.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small))
            Box(Modifier.fillMaxWidth().height(156.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small))
        }
    }
}

@Composable
private fun StatusBanner(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        androidx.compose.foundation.layout.Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    description: String,
    action: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}
