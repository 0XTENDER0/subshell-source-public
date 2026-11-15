package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import io.github.brealorg.subshell.ui.components.settingsRowBleed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.ui.CommentSwipeAction
import io.github.brealorg.subshell.ui.PostSwipeAction
import io.github.brealorg.subshell.ui.symbols.Symbols

// ISSUE16_INLINE_EXPANDABLE_SWIPE_SETTINGS_V1
// ISSUE16_M3_RADIO_SINGLE_CHOICE_SETTINGS_V1
internal enum class SwipeActionSettingsTarget {
    POSTS,
    COMMENTS,
}

private enum class SwipeActionChoiceTarget {
    POST_LEFT,
    POST_RIGHT,
    COMMENT_LEFT,
    COMMENT_RIGHT,
}

@Composable
internal fun SwipeActionsSettingsSummary(
    enabled: Boolean,
    expandedTarget: SwipeActionSettingsTarget?,
    postLeftAction: PostSwipeAction,
    postRightAction: PostSwipeAction,
    commentLeftAction: CommentSwipeAction,
    commentRightAction: CommentSwipeAction,
    onEnabledChange: (Boolean) -> Unit,
    onExpandedTargetChange: (SwipeActionSettingsTarget?) -> Unit,
    onPostLeftActionChange: (PostSwipeAction) -> Unit,
    onPostRightActionChange: (PostSwipeAction) -> Unit,
    onCommentLeftActionChange: (CommentSwipeAction) -> Unit,
    onCommentRightActionChange: (CommentSwipeAction) -> Unit,
) {
    var choiceTargetName by rememberSaveable { mutableStateOf<String?>(null) }
    val choiceTarget = choiceTargetName?.let { stored ->
        SwipeActionChoiceTarget.entries.firstOrNull { it.name == stored }
    }

    // M3225_SETTINGS_ROWS_ARE_LIST_ITEMS_V1: a list row like the other settings, not a card.
    Box(Modifier.fillMaxWidth().settingsRowBleed()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            // ISSUE219_SWIPE_MASTER_ROW_IS_THE_SWITCH_V1: as ToggleRow (LOWUI7).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
                    .defaultMinSize(minHeight = 46.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Symbols.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.set_swipe_actions),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = if (enabled) {
                            stringResource(R.string.all_horizontal_shortcuts_side_menu_opens_from_the)
                        } else {
                            stringResource(R.string.all_horizontal_shortcuts_for_posts_and_comments)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = null,
                )
            }

            HorizontalDivider()

            SwipeSummaryRow(
                label = stringResource(R.string.set_posts),
                value = "← ${stringResource(postLeftAction.labelRes)} · ${stringResource(postRightAction.labelRes)} →",
                expanded = expandedTarget == SwipeActionSettingsTarget.POSTS,
                onClick = {
                    onExpandedTargetChange(
                        if (expandedTarget == SwipeActionSettingsTarget.POSTS) {
                            null
                        } else {
                            SwipeActionSettingsTarget.POSTS
                        },
                    )
                },
            )

            if (expandedTarget == SwipeActionSettingsTarget.POSTS) {
                InlineSwipeActionRows(
                    leftValue = stringResource(postLeftAction.labelRes),
                    rightValue = stringResource(postRightAction.labelRes),
                    onLeftClick = {
                        choiceTargetName = SwipeActionChoiceTarget.POST_LEFT.name
                    },
                    onRightClick = {
                        choiceTargetName = SwipeActionChoiceTarget.POST_RIGHT.name
                    },
                )
            }

            HorizontalDivider()

            SwipeSummaryRow(
                label = stringResource(R.string.set_comments),
                value = "← ${stringResource(commentLeftAction.labelRes)} · ${stringResource(commentRightAction.labelRes)} →",
                expanded = expandedTarget == SwipeActionSettingsTarget.COMMENTS,
                onClick = {
                    onExpandedTargetChange(
                        if (expandedTarget == SwipeActionSettingsTarget.COMMENTS) {
                            null
                        } else {
                            SwipeActionSettingsTarget.COMMENTS
                        },
                    )
                },
            )

            if (expandedTarget == SwipeActionSettingsTarget.COMMENTS) {
                InlineSwipeActionRows(
                    leftValue = stringResource(commentLeftAction.labelRes),
                    rightValue = stringResource(commentRightAction.labelRes),
                    onLeftClick = {
                        choiceTargetName = SwipeActionChoiceTarget.COMMENT_LEFT.name
                    },
                    onRightClick = {
                        choiceTargetName = SwipeActionChoiceTarget.COMMENT_RIGHT.name
                    },
                )
            }
        }
    }

    when (choiceTarget) {
        SwipeActionChoiceTarget.POST_LEFT -> SwipeActionRadioDialog(
            title = stringResource(R.string.set_post_swipe_left),
            options = PostSwipeAction.entries,
            selected = postLeftAction,
            label = { stringResource(it.labelRes) },
            onSelect = { action ->
                onPostLeftActionChange(action)
                choiceTargetName = null
            },
            onDismiss = { choiceTargetName = null },
        )

        SwipeActionChoiceTarget.POST_RIGHT -> SwipeActionRadioDialog(
            title = stringResource(R.string.set_post_swipe_right),
            options = PostSwipeAction.entries,
            selected = postRightAction,
            label = { stringResource(it.labelRes) },
            onSelect = { action ->
                onPostRightActionChange(action)
                choiceTargetName = null
            },
            onDismiss = { choiceTargetName = null },
        )

        SwipeActionChoiceTarget.COMMENT_LEFT -> SwipeActionRadioDialog(
            title = stringResource(R.string.set_comment_swipe_left),
            options = CommentSwipeAction.entries,
            selected = commentLeftAction,
            label = { stringResource(it.labelRes) },
            onSelect = { action ->
                onCommentLeftActionChange(action)
                choiceTargetName = null
            },
            onDismiss = { choiceTargetName = null },
        )

        SwipeActionChoiceTarget.COMMENT_RIGHT -> SwipeActionRadioDialog(
            title = stringResource(R.string.set_comment_swipe_right),
            options = CommentSwipeAction.entries,
            selected = commentRightAction,
            label = { stringResource(it.labelRes) },
            onSelect = { action ->
                onCommentRightActionChange(action)
                choiceTargetName = null
            },
            onDismiss = { choiceTargetName = null },
        )

        null -> Unit
    }
}

@Composable
private fun SwipeSummaryRow(
    label: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // ISSUE219_EXPANDABLE_ROWS_SAY_THEIR_STATE_V1: the state is spoken, not
            // inferred from the icon's label.
            .clickable(
                onClickLabel = if (expanded) stringResource(R.string.all_collapse) else stringResource(R.string.all_expand),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .defaultMinSize(minHeight = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = if (expanded) {
                Symbols.ExpandLess
            } else {
                Symbols.ExpandMore
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InlineSwipeActionRows(
    leftValue: String,
    rightValue: String,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 18.dp),
    ) {
        HorizontalDivider()
        SwipeActionValueRow(
            title = stringResource(R.string.set_swipe_left),
            value = leftValue,
            onClick = onLeftClick,
        )
        HorizontalDivider()
        SwipeActionValueRow(
            title = stringResource(R.string.set_swipe_right),
            value = rightValue,
            onClick = onRightClick,
        )
    }
}

@Composable
private fun SwipeActionValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    ListItem(
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Symbols.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun <T> SwipeActionRadioDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @androidx.compose.runtime.Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            )
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.set_cancel))
            }
        },
    )
}
