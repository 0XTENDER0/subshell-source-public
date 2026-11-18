package io.github.brealorg.subshell.ui.screens

import androidx.compose.ui.res.pluralStringResource
import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.communityposts.MAX_WATCHED_COMMUNITIES
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.components.RouteTopBar
import io.github.brealorg.subshell.ui.components.rememberRouteTopBarScrollBehavior
import io.github.brealorg.subshell.ui.symbols.Symbols

/**
 * ISSUE239_WATCHED_COMMUNITIES_IN_SETTINGS_V1 (#239): Settings › Notifications
 * › New posts lists the communities that notify the active account about new
 * posts; each can be removed here. They are added from a community's ⋮.
 */
@Composable
internal fun NewPostNotificationsSettingsScreen(
    state: SubshellState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val watched = state.watchedCommunityNames
    val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
    Column(modifier.fillMaxSize().nestedScroll(routeTopBarScroll.nestedScrollConnection)) {
        RouteTopBar(
            title = { Text(stringResource(R.string.sw_new_posts)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Symbols.ArrowBack, contentDescription = stringResource(R.string.sw_back))
                }
            },
            scrollBehavior = routeTopBarScroll,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("new-posts-supporting") {
                Text(
                    text = newPostNotificationsExplanation(state.communityPostNotificationsAvailable, watched.isEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            // ISSUE239_NEW_COMMENTS_IN_A_WATCHED_COMMUNITY_V1 (owner): the reader
            // decides; off by default. Their own posts and comments never notify.
            item("new-posts-comments") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    NotificationTypeRow(
                        title = stringResource(R.string.sw_also_new_comments),
                        description = stringResource(R.string.scr_a_notification_for_each_new_comment),
                        checked = state.notifyNewComments,
                        enabled = state.communityPostNotificationsAvailable,
                        onCheckedChange = state::updateNotifyNewComments,
                    )
                }
            }
            if (watched.isNotEmpty()) {
                item("new-posts-communities") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            watched.forEachIndexed { index, name ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                                val label = state.subredditLabel(name)
                                ListItem(
                                    content = { Text(label) },
                                    trailingContent = {
                                        IconButton(onClick = { state.setWatchingCommunityPosts(name, watch = false) }) {
                                            Icon(Symbols.Close, contentDescription = stringResource(R.string.sw_stop_notifications_from, label))
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun newPostNotificationsExplanation(available: Boolean, empty: Boolean): String = when {
    !available -> stringResource(R.string.set_new_posts_sign_in)
    empty -> stringResource(R.string.set_new_posts_none_yet)
    else -> stringResource(R.string.set_new_posts_explained, MAX_WATCHED_COMMUNITIES)
}

/** The summary under Settings › Notifications › New posts. */
@Composable
internal fun newPostNotificationsSummary(count: Int): String = when (count) {
    0 -> stringResource(R.string.set_new_posts_off)
    else -> pluralStringResource(R.plurals.set_new_posts_communities, count, count)
}
