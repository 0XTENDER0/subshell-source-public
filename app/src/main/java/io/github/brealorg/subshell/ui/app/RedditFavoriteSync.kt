package io.github.brealorg.subshell.ui.app

import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import io.github.brealorg.subshell.data.account.CustomFeedsUiState
import io.github.brealorg.subshell.data.account.RedditFavoriteUiFailure
import io.github.brealorg.subshell.data.account.RedditFavoriteUiFailureKind
import io.github.brealorg.subshell.data.account.RedditSubscriptionsUiState
import io.github.brealorg.subshell.data.account.REDDIT_FAVORITE_SYNC_ENABLED_KEY
import io.github.brealorg.subshell.data.account.RedditFavoriteSyncSettingStore
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.error.ErrorAction
import io.github.brealorg.subshell.ui.error.ErrorActionKind
import io.github.brealorg.subshell.ui.error.ErrorPresentation
import io.github.brealorg.subshell.ui.error.ErrorPresentationKind
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import io.github.brealorg.subshell.ui.state.RedditFavoriteSyncSnapshot

/** REDDITFAV01: the stored setting, updated whenever Settings writes it. */
@Composable
internal fun rememberRedditFavoriteSyncEnabled(): State<Boolean> {
    val appContext = LocalContext.current.applicationContext
    return produceState(
        initialValue = RedditFavoriteSyncSettingStore.read(appContext),
        appContext,
    ) {
        val preferences = RedditFavoriteSyncSettingStore.preferences(appContext)
        // Held here: SharedPreferences keeps its listeners weakly.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == REDDIT_FAVORITE_SYNC_ENABLED_KEY) {
                value = RedditFavoriteSyncSettingStore.read(appContext)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        value = RedditFavoriteSyncSettingStore.read(appContext)
        awaitDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

/**
 * REDDITFAV01_SHOW_REDDIT_FAVORITES_V1 / REDDITFAV02_FAVORITE_WRITE_V1: hands
 * the subscriptions read (Reddit's favorites, the joined communities) and
 * the sent changes to the state, gives the state the write action, and
 * reports a change Reddit refused or did not confirm in the tray.
 */
@Composable
internal fun RedditFavoriteSyncEffects(
    state: SubshellState,
    subscriptions: RedditSubscriptionsUiState,
    customFeeds: CustomFeedsUiState,
    community: CommunityEntryPoints,
    noticeHost: ViewportErrorHostState,
) {
    val enabled by rememberRedditFavoriteSyncEnabled()
    val favorites = community.redditFavoriteState
    val authenticated = state.activeAccountIsAuthenticated

    val snapshot = redditFavoriteSyncSnapshot(
        enabled = enabled && authenticated,
        subscriptions = subscriptions,
        pending = favorites.pending.takeIf { favorites.accountStableId == subscriptions.accountStableId },
    ).withCustomFeeds(
        customFeeds = customFeeds,
        pending = favorites.pendingCustomFeeds.takeIf { favorites.accountStableId == customFeeds.accountStableId },
    )
    val onUpdate = community.onUpdateRedditFavorite
    val onUpdateCustomFeed = community.onUpdateRedditCustomFeedFavorite
    SideEffect {
        if (state.redditFavoriteSync != snapshot) {
            state.redditFavoriteSync = snapshot
        }
        state.redditFavoriteWriter = onUpdate
        state.redditCustomFeedFavoriteWriter = onUpdateCustomFeed
    }

    LaunchedEffect(favorites.failureRevision) {
        val failure = favorites.failure ?: return@LaunchedEffect
        noticeHost.clear(RedditFavoriteNoticeScope)
        // REDDITFAV03_FAVORITE_AUTHORIZATION_V1: a missing grant offers Authorize.
        val authorize = community.onAuthorizeRedditFavorite
        noticeHost.present(
            scopeKey = RedditFavoriteNoticeScope,
            presentation = redditFavoriteFailurePresentation(failure),
            failureDetails = null,
            onRetry = authorize.takeIf { failure.kind == RedditFavoriteUiFailureKind.AUTHORIZATION },
        )
        community.onDismissRedditFavoriteFailure()
    }
}

internal fun redditFavoriteSyncSnapshot(
    enabled: Boolean,
    subscriptions: RedditSubscriptionsUiState,
    pending: Map<String, Boolean>?,
): RedditFavoriteSyncSnapshot {
    val accountStableId = subscriptions.accountStableId
    if (!enabled || accountStableId == null || !subscriptions.loaded) {
        return RedditFavoriteSyncSnapshot(enabled = enabled)
    }
    return RedditFavoriteSyncSnapshot(
        enabled = true,
        accountStableId = accountStableId,
        favoriteCommunityNames = subscriptions.favoriteCommunityNames,
        joinedCommunityNames = subscriptions.communities.map { community -> community.name }.toSet(),
        pending = pending.orEmpty(),
    )
}

// REDDITFAV04_CUSTOM_FEED_FAVORITES_V1: the active account's own custom
// feeds; they count only for the account the subscriptions were read for.
internal fun RedditFavoriteSyncSnapshot.withCustomFeeds(
    customFeeds: CustomFeedsUiState,
    pending: Map<String, Boolean>?,
): RedditFavoriteSyncSnapshot {
    if (!enabled || accountStableId == null || customFeeds.accountStableId != accountStableId) return this
    return copy(
        customFeedPaths = customFeeds.feeds.map { feed -> feed.path }.toSet(),
        favoriteCustomFeedPaths = customFeeds.feeds.filter { feed -> feed.redditFavorite }.map { feed -> feed.path }.toSet(),
        pendingCustomFeeds = pending.orEmpty(),
    )
}

internal const val RedditFavoriteNoticeScope = "reddit-favorite"

internal fun redditFavoriteFailurePresentation(
    failure: RedditFavoriteUiFailure,
): ErrorPresentation {
    val community = if (failure.customFeed) failure.communityName else UiText.Resource(R.string.notice_redditfavori_r, failure.communityName)
    return ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = if (failure.makeFavorite) {
            UiText.Resource(R.string.notice_redditfavori_couldn_t_add_to_your_reddit_favorites, community)
        } else {
            UiText.Resource(R.string.notice_redditfavori_couldn_t_remove_from_your_reddit_favorites, community)
        },
        message = when (failure.kind) {
            RedditFavoriteUiFailureKind.FAILED ->
                UiText.Resource(R.string.notice_redditfavori_reddit_didn_t_accept_the_change_or)
            RedditFavoriteUiFailureKind.AUTHORIZATION ->
                UiText.Resource(R.string.notice_redditfavori_subshell_needs_your_permission_to_manage_your)
            RedditFavoriteUiFailureKind.NOT_CONFIRMED ->
                UiText.Resource(R.string.notice_redditfavori_reddit_s_list_didn_t_show_the)
        },
        primaryAction = ErrorAction(ErrorActionKind.RETRY, UiText.Resource(R.string.notice_redditfavori_authorize))
            .takeIf { failure.kind == RedditFavoriteUiFailureKind.AUTHORIZATION },
    )
}
