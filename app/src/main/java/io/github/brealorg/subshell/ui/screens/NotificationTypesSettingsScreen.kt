package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.components.RouteTopBar
import io.github.brealorg.subshell.ui.components.rememberRouteTopBarScrollBehavior
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import io.github.brealorg.subshell.ui.symbols.Symbols

// NOTIFY01A1R2_NOTIFICATION_TYPES_SUBSCREEN_PRESENTATION_V1
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationTypesSettingsScreen(
    state: SubshellState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = state.inboxNotificationsEnabled

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
    Column(modifier.fillMaxSize().nestedScroll(routeTopBarScroll.nestedScrollConnection)) {
        RouteTopBar(
            title = { Text(stringResource(R.string.sw_notification_types)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Symbols.ArrowBack,
                        contentDescription = stringResource(R.string.sw_back),
                    )
                }
            },
            scrollBehavior = routeTopBarScroll,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("notification-types-supporting") {
                Text(
                    text = stringResource(R.string.sw_choose_what_you_want_to_be_notified),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item("notification-types-group") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        NotificationTypeRow(
                            title = stringResource(R.string.sw_replies),
                            description = stringResource(R.string.sw_post_and_comment_replies),
                            checked = state.notifyReplies,
                            enabled = enabled,
                            onCheckedChange = state::updateNotifyReplies,
                        )

                        NotificationTypeDivider()

                        NotificationTypeRow(
                            title = stringResource(R.string.sw_mentions),
                            description = stringResource(R.string.sw_username_mentions),
                            checked = state.notifyMentions,
                            enabled = enabled,
                            onCheckedChange = state::updateNotifyMentions,
                        )

                        NotificationTypeDivider()

                        NotificationTypeRow(
                            title = stringResource(R.string.sw_private_messages),
                            description = stringResource(R.string.sw_reddit_messages),
                            checked = state.notifyPrivateMessages,
                            enabled = enabled,
                            onCheckedChange =
                                state::updateNotifyPrivateMessages,
                        )

                        NotificationTypeDivider()

                        NotificationTypeRow(
                            title = stringResource(R.string.sw_reddit_notices),
                            description = stringResource(R.string.sw_system_messages),
                            checked = state.notifyRedditNotices,
                            enabled = enabled,
                            onCheckedChange =
                                state::updateNotifyRedditNotices,
                        )

                        NotificationTypeDivider()

                        NotificationTypeRow(
                            title = stringResource(R.string.sw_modmail),
                            description = stringResource(R.string.sw_moderator_conversations),
                            checked = state.notifyModmail,
                            enabled = enabled,
                            onCheckedChange = state::updateNotifyModmail,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NotificationTypeRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // M3225_SETTINGS_ROWS_ARE_LIST_ITEMS_V1: an M3 ListItem inside its card,
    // with ListItem's own disabled colours.
    ListItem(
        content = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(checked = checked, enabled = enabled, onCheckedChange = null)
        },
        enabled = enabled,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    )
}

@Composable
internal fun NotificationTypeDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
