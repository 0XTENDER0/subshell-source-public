package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import io.github.brealorg.subshell.ui.error.ReportFailureDiagnostic
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.brealorg.subshell.data.account.ModeratorContentControlConfirmation
import io.github.brealorg.subshell.data.account.ModeratorContentControlsNotice
import io.github.brealorg.subshell.data.account.ModeratorContentControlsUiState
import io.github.brealorg.subshell.data.account.ModeratorReportTarget
import io.github.brealorg.subshell.data.account.RedditModeratorAction
import io.github.brealorg.subshell.data.account.RedditModeratorActionPhase
import io.github.brealorg.subshell.data.account.RedditModeratorActionUiState
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.contentControlConfirmationOrNull
import io.github.brealorg.subshell.data.account.isContentControl
import io.github.brealorg.subshell.data.account.reportTarget
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.github.brealorg.subshell.ui.symbols.Symbols

// MODERATION02A3_ROOT_OWNED_CONTENT_CONTROL_ENTRY_POINTS_V1
internal data class ModeratorContentControlsEntryPoints(
    val canInspect: (String) -> Boolean,
    val open: (ModeratorReportTarget) -> Unit,
)

internal val LocalModeratorContentControls =
    staticCompositionLocalOf<ModeratorContentControlsEntryPoints?> { null }

// MODERATION02A3_MATERIAL_CONTENT_CONTROL_DIALOG_V1
@Composable
internal fun ModeratorContentControlsDialog(
    accountStableId: String,
    activeUsername: String,
    state: ModeratorContentControlsUiState,
    actionState: RedditModeratorActionUiState,
    onRefresh: () -> Unit,
    onConfirm: (ModeratorContentControlConfirmation) -> Unit,
    onAuthorize: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.visible || state.accountStableId != accountStableId) return

    val item = state.item
    val actionBusy = actionState.inProgress
    val busy = state.busy || actionBusy
    var pending by remember(
        accountStableId,
        state.target?.fullname,
        state.revision,
    ) {
        mutableStateOf<ModeratorContentControlConfirmation?>(null)
    }

    val lockAction = when (item?.contentState?.locked) {
        false -> RedditModeratorAction.LOCK
        true -> RedditModeratorAction.UNLOCK
        null -> null
    }
    val distinguishAction = when (item?.contentState?.distinguished) {
        false -> RedditModeratorAction.DISTINGUISH
        true -> RedditModeratorAction.UNDISTINGUISH
        null -> null
    }
    val lockConfirmation = lockAction?.let { action ->
        contentControlConfirmationOrNull(
            state = state,
            activeAccountStableId = accountStableId,
            activeUsername = activeUsername,
            action = action,
        )
    }
    val distinguishConfirmation = distinguishAction?.let { action ->
        contentControlConfirmationOrNull(
            state = state,
            activeAccountStableId = accountStableId,
            activeUsername = activeUsername,
            action = action,
        )
    }

    // M3225_FORM_DIALOGS_ARE_FULL_SCREEN_ON_PHONES_V1
    AdaptiveFullScreenDialog(
        title = stringResource(R.string.comp_moderation_controls),
        onDismissRequest = onDismiss,
        closeLabel = stringResource(R.string.all_close_moderation_controls),
        actions = {
            IconButton(
                onClick = onRefresh,
                enabled = !busy && state.target != null,
            ) {
                Icon(
                    Symbols.Refresh,
                    contentDescription = stringResource(R.string.comp_refresh_moderation_controls),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InlineProgressIndicator()
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.comp_checking_current_reddit_state))
                    }
                }

                item != null -> {
                    ModeratorControlTargetCard(item)

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.all_conversation),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        ModeratorControlStateBadge(
                            label = when (item.contentState.locked) {
                                true -> stringResource(R.string.all_locked)
                                false -> stringResource(R.string.all_unlocked)
                                null -> stringResource(R.string.all_lock_state_unavailable)
                            },
                            known = item.contentState.locked != null,
                            iconLocked = item.contentState.locked == true,
                        )
                        FilledTonalButton(
                            onClick = {
                                lockConfirmation?.let { pending = it }
                            },
                            enabled = lockConfirmation != null && !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                if (item.contentState.locked == true) {
                                    Symbols.LockOpen
                                } else {
                                    Symbols.Lock
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (item.contentState.locked == true) {
                                    stringResource(R.string.all_unlock)
                                } else {
                                    stringResource(R.string.all_lock)
                                },
                            )
                        }

                        Text(
                            stringResource(R.string.all_moderator_identity),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        ModeratorDistinguishStateBadge(
                            distinguished = item.contentState.distinguished,
                        )
                        FilledTonalButton(
                            onClick = {
                                distinguishConfirmation?.let { pending = it }
                            },
                            enabled =
                                distinguishConfirmation != null &&
                                    !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Symbols.AdminPanelSettings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (item.contentState.distinguished == true) {
                                    stringResource(R.string.all_undistinguish)
                                } else {
                                    stringResource(R.string.all_distinguish_as_moderator)
                                },
                            )
                        }
                        if (
                            !item.author.equals(
                                activeUsername.removePrefix("u/"),
                                ignoreCase = true,
                            )
                        ) {
                            Text(
                                stringResource(R.string.all_only_content_authored_by_the_active_moderator),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> {
                    Text(
                        stringResource(R.string.all_the_exact_target_is_not_available_refresh),
                        modifier = Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.notice?.let { notice ->
                ModeratorContentControlNoticeCard(
                    notice = notice,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                ReportFailureDiagnostic(
                    scopeKey = "moderator_content_controls",
                    active = notice == ModeratorContentControlsNotice.READ_FAILED ||
                        notice == ModeratorContentControlsNotice.WRITE_FAILED,
                    failureDetails = state.failureDetails,
                )
            }

            val authorizationRequest = actionState.request
            if (
                authorizationRequest != null &&
                authorizationRequest.thingFullname == state.target?.fullname &&
                authorizationRequest.action.isContentControl() &&
                actionState.phase ==
                    RedditModeratorActionPhase.AUTHORIZATION_REQUIRED
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.all_moderator_authorization_required),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.all_authorize_modposts_then_refresh_this_target_and),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onAuthorize) {
                            Text(stringResource(R.string.comp_authorize))
                        }
                    }
                }
            }
        }
    }

    pending?.let { pinned ->
        val current = contentControlConfirmationOrNull(
            state = state,
            activeAccountStableId = accountStableId,
            activeUsername = activeUsername,
            action = pinned.action,
        )
        val labels = moderatorContentControlLabels(pinned.action)
        AlertDialog(
            onDismissRequest = { pending = null },
            icon = {
                Icon(
                    when (pinned.action) {
                        RedditModeratorAction.LOCK ->
                            Symbols.Lock
                        RedditModeratorAction.UNLOCK ->
                            Symbols.LockOpen
                        else ->
                            Symbols.AdminPanelSettings
                    },
                    contentDescription = null,
                )
            },
            title = { Text(labels.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.item?.let { selected ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    selected.summary,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "r/${pinned.target.communityName} · ${pinned.target.fullname}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Text(labels.description)
                    Text(
                        labels.reversal,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = confirm@{
                        val exact = current ?: return@confirm
                        if (exact != pinned || busy) return@confirm
                        pending = null
                        onConfirm(exact)
                    },
                    enabled = current == pinned && !busy,
                ) {
                    Text(labels.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text(stringResource(R.string.comp_cancel))
                }
            },
        )
    }
}

private data class ModeratorContentControlLabels(
    val title: String,
    val confirm: String,
    val description: String,
    val reversal: String,
)

@androidx.compose.runtime.Composable
private fun moderatorContentControlLabels(
    action: RedditModeratorAction,
): ModeratorContentControlLabels = when (action) {
    RedditModeratorAction.LOCK -> ModeratorContentControlLabels(
        title = stringResource(R.string.comp_mod_control_1),
        confirm = stringResource(R.string.comp_mod_control_2),
        description = stringResource(R.string.comp_mod_control_3),
        reversal = stringResource(R.string.comp_mod_control_4),
    )
    RedditModeratorAction.UNLOCK -> ModeratorContentControlLabels(
        title = stringResource(R.string.comp_mod_control_5),
        confirm = stringResource(R.string.comp_mod_control_6),
        description = stringResource(R.string.comp_mod_control_7),
        reversal = stringResource(R.string.comp_mod_control_8),
    )
    RedditModeratorAction.DISTINGUISH -> ModeratorContentControlLabels(
        title = stringResource(R.string.comp_mod_control_9),
        confirm = stringResource(R.string.comp_mod_control_10),
        description = stringResource(R.string.comp_mod_control_11),
        reversal = stringResource(R.string.comp_mod_control_12),
    )
    RedditModeratorAction.UNDISTINGUISH -> ModeratorContentControlLabels(
        title = stringResource(R.string.comp_mod_control_13),
        confirm = stringResource(R.string.comp_mod_control_14),
        description = stringResource(R.string.comp_mod_control_15),
        reversal = stringResource(R.string.comp_mod_control_16),
    )
    else -> error("Unsupported moderator content control action")
}

@Composable
private fun ModeratorControlTargetCard(
    item: RedditModeratorQueueItem,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                item.summary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "r/${item.communityName} · u/${item.author}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                item.fullname,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeratorControlStateBadge(
    label: String,
    known: Boolean,
    iconLocked: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color =
            if (known) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        contentColor =
            if (known) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
        // LOWUI7_NO_DOUBLE_READOUTS_V1 (OCB-146): the merged label is read once.
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (iconLocked) Symbols.Lock else Symbols.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ModeratorDistinguishStateBadge(
    distinguished: Boolean?,
) {
    val label = when (distinguished) {
        true -> stringResource(R.string.all_distinguished_as_moderator)
        false -> stringResource(R.string.all_not_distinguished)
        null -> stringResource(R.string.all_distinguish_state_unavailable)
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color =
            if (distinguished == null) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        contentColor =
            if (distinguished == null) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Symbols.AdminPanelSettings,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ModeratorContentControlNoticeCard(
    notice: ModeratorContentControlsNotice,
    modifier: Modifier = Modifier,
) {
    val verified =
        notice == ModeratorContentControlsNotice.VERIFIED ||
            notice == ModeratorContentControlsNotice.VERIFIED_AFTER_UNKNOWN
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color =
            if (verified) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        contentColor =
            if (verified) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                if (verified) Symbols.CheckCircle else Symbols.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                moderatorContentControlNoticeText(notice),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@androidx.compose.runtime.Composable
internal fun moderatorContentControlNoticeText(
    notice: ModeratorContentControlsNotice,
): String = when (notice) {
    ModeratorContentControlsNotice.NOT_FOUND ->
        stringResource(R.string.comp_mod_control_notice_1)
    ModeratorContentControlsNotice.READ_FAILED ->
        stringResource(R.string.comp_mod_control_notice_2)
    ModeratorContentControlsNotice.NO_PERMISSION ->
        stringResource(R.string.comp_mod_control_notice_3)
    ModeratorContentControlsNotice.STATE_UNKNOWN ->
        stringResource(R.string.comp_mod_control_notice_4)
    ModeratorContentControlsNotice.STATE_CHANGED ->
        stringResource(R.string.comp_mod_control_notice_5)
    ModeratorContentControlsNotice.BUSY ->
        stringResource(R.string.comp_mod_control_notice_6)
    ModeratorContentControlsNotice.AUTHORIZATION_REQUIRED ->
        stringResource(R.string.comp_mod_control_notice_7)
    ModeratorContentControlsNotice.WRITE_FAILED ->
        stringResource(R.string.comp_mod_control_notice_8)
    ModeratorContentControlsNotice.VERIFIED ->
        stringResource(R.string.comp_mod_control_notice_9)
    ModeratorContentControlsNotice.VERIFIED_AFTER_UNKNOWN ->
        stringResource(R.string.comp_mod_control_notice_10)
    ModeratorContentControlsNotice.NOT_CONFIRMED ->
        stringResource(R.string.comp_mod_control_notice_11)
    ModeratorContentControlsNotice.ACCOUNT_CHANGED ->
        stringResource(R.string.comp_mod_control_notice_12)
}
