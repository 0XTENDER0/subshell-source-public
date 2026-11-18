package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import io.github.brealorg.subshell.data.normalizeRedditCommunityName
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.data.account.ModeratorInviteFailureKind
import io.github.brealorg.subshell.data.account.ModeratorPermissionUpdateFailureKind
import io.github.brealorg.subshell.data.account.ModeratorPermissionUpdateUiState
import io.github.brealorg.subshell.data.account.ModeratorRemovalFailureKind
import io.github.brealorg.subshell.data.account.ModeratorRemovalUiState
import io.github.brealorg.subshell.data.account.ModeratorInviteUiState
import io.github.brealorg.subshell.data.account.RedditCommunityInfoUiState
import io.github.brealorg.subshell.data.account.RedditModeratorInviteRequest
import io.github.brealorg.subshell.data.account.RedditModeratorPermissionUpdateRequest
import io.github.brealorg.subshell.data.account.RedditModeratorRemovalRequest
import io.github.brealorg.subshell.data.account.RedditModeratorPermission
import io.github.brealorg.subshell.data.account.normalizeRedditSafetyUsername
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.toSafeTechnicalDetail
import io.github.brealorg.subshell.model.CommunityModerator
import io.github.brealorg.subshell.model.RedditPeopleSearchItem
import io.github.brealorg.subshell.ui.app.PredictiveBackPage
import io.github.brealorg.subshell.ui.symbols.Symbols
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// MODERATION02A5A4_READ_ONLY_MODERATOR_TEAM_ENTRY_V1
@Composable
fun ModeratorTeamButton(onClick: () -> Unit) {
    OutlinedButton(
        modifier = Modifier.heightIn(min = 48.dp),
        onClick = onClick,
    ) {
        Icon(imageVector = Symbols.Person, contentDescription = null,
            modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.comp_moderators))
    }
}

// Content only: the existing R4 workspace owns the one sheet and system Back.
@Composable
fun ModeratorTeamContent(
    accountStableId: String,
    communityName: String,
    activeUsername: String,
    canRead: Boolean,
    uiState: RedditCommunityInfoUiState,
    avatarImageUri: (String) -> String?,
    onEnsureIdentity: suspend (String) -> Unit,
    onLoad: (Boolean) -> Unit,
    inviteState: ModeratorInviteUiState,
    onOpenInviteContext: () -> Unit,
    onInvite: (RedditModeratorInviteRequest) -> Unit,
    onAuthorizeInvite: () -> Unit,
    onDismissInvite: () -> Unit,
    permissionUpdateState: ModeratorPermissionUpdateUiState,
    onOpenPermissionUpdateContext: (String) -> Unit,
    onUpdatePermissions: (RedditModeratorPermissionUpdateRequest) -> Unit,
    onAuthorizePermissionUpdate: () -> Unit,
    onDismissPermissionUpdate: () -> Unit,
    removalState: ModeratorRemovalUiState,
    onOpenRemovalContext: (String) -> Unit,
    onRemoveModerator: (RedditModeratorRemovalRequest) -> Unit,
    onAuthorizeRemoval: () -> Unit,
    onDismissRemoval: () -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable(accountStableId, communityName) { mutableStateOf("") }
    var showInviteEditor by rememberSaveable(accountStableId, communityName) { mutableStateOf(false) }
    var permissionEditorUsername by rememberSaveable(accountStableId, communityName) {
        mutableStateOf<String?>(null)
    }
    var removalEditorUsername by rememberSaveable(accountStableId, communityName) {
        mutableStateOf<String?>(null)
    }
    val identities = remember(accountStableId, communityName) { Semaphore(3) }
    val projection = moderatorTeamProjection(uiState, accountStableId, communityName, canRead)
    val rows = moderatorTeamFilteredRows(projection.moderators, query)
    val canInvite = moderatorTeamCanInvite(projection.moderators, activeUsername)
    val inviteMatches = inviteState.matches(accountStableId, communityName)
    // LOWUI8_DISMISSED_MODERATOR_RESULTS_STAY_DISMISSED_V1 (OCB-148): a
    // dismissed editor keeps its last result for the coordinator; the banner
    // and its refresh belong to a visible one only, so reopening Moderators
    // no longer brings an old result (and a forced reload) back.
    val invitedUsername = inviteState.lastInvitedUsername.takeIf { inviteMatches && inviteState.visible }
    val permissionUpdateMatches = permissionUpdateState.matches(accountStableId, communityName)
    val updatedUsername = permissionUpdateState.lastUpdatedUsername.takeIf {
        permissionUpdateMatches && permissionUpdateState.visible
    }
    val permissionTarget = permissionEditorUsername?.let { username ->
        projection.moderators.firstOrNull { moderator ->
            moderator.username.equals(username, ignoreCase = true)
        }
    }
    val removalMatches = removalState.matches(accountStableId, communityName)
    val removedUsername = removalState.lastRemovedUsername.takeIf { removalMatches && removalState.visible }
    val removalTarget = removalEditorUsername?.let { username ->
        projection.moderators.firstOrNull { moderator ->
            moderator.username.equals(username, ignoreCase = true)
        }
    }

    LaunchedEffect(accountStableId, communityName, canRead) {
        if (canRead) onLoad(false)
    }
    // ISSUE86_EDITOR_STAYS_WHILE_ITS_WRITE_RUNS_V1: Back (or a sheet swipe)
    // closed an editor while its write was in flight; dismiss cleared the busy
    // flag and the result (even OUTCOME_UNKNOWN) landed in hidden state, so the
    // moderator could send again. While a write runs, Back is swallowed and the
    // sheet refuses to hide (LocalModeratorWorkspaceBusy).
    val inviteBusy = showInviteEditor && inviteMatches && inviteState.busy
    val permissionUpdateBusy =
        permissionEditorUsername != null && permissionUpdateMatches && permissionUpdateState.busy
    val removalBusy = removalEditorUsername != null && removalMatches && removalState.busy
    val workspaceBusy = LocalModeratorWorkspaceBusy.current
    val anyEditorBusy = inviteBusy || permissionUpdateBusy || removalBusy
    SideEffect { workspaceBusy?.busy = anyEditorBusy }
    DisposableEffect(workspaceBusy) { onDispose { workspaceBusy?.busy = false } }
    // ISSUE249_IN_SCREEN_PAGES_FOLLOW_THE_BACK_GESTURE_V1: an editor follows
    // the back gesture (PredictiveBackPage below); while it writes, Back is
    // swallowed here without a preview.
    BackHandler(enabled = showInviteEditor && inviteBusy) {}
    // LOWUI8_HIDDEN_EDITOR_DOES_NOT_TAKE_BACK_V1 (OCB-305): an editor whose
    // moderator is not in the current list is not shown, so its Back handler
    // stands down too; Back then leaves Moderators as the screen suggests.
    BackHandler(enabled = permissionEditorUsername != null && permissionTarget != null && permissionUpdateBusy) {}
    BackHandler(enabled = removalEditorUsername != null && removalTarget != null && removalBusy) {}
    LaunchedEffect(inviteState.completionRevision, invitedUsername) {
        if (inviteMatches && invitedUsername != null && inviteState.completionRevision > 0L) {
            showInviteEditor = false
            onLoad(true)
        }
    }
    LaunchedEffect(permissionUpdateState.completionRevision, updatedUsername) {
        if (
            permissionUpdateMatches &&
            updatedUsername != null &&
            permissionUpdateState.completionRevision > 0L
        ) {
            permissionEditorUsername = null
            onLoad(true)
        }
    }
    // LOWUI6_UNKNOWN_MODERATOR_WRITE_IS_READ_BACK_V1 (OCB-149): a permission
    // change or removal whose answer was lost reloads the moderator list from
    // Reddit at once, so the list behind the editor shows what happened.
    val permissionOutcomeUnknown = permissionUpdateMatches &&
        permissionUpdateState.failureKind == ModeratorPermissionUpdateFailureKind.OUTCOME_UNKNOWN
    val removalOutcomeUnknown = removalMatches &&
        removalState.failureKind == ModeratorRemovalFailureKind.OUTCOME_UNKNOWN
    LaunchedEffect(permissionOutcomeUnknown, removalOutcomeUnknown) {
        if (permissionOutcomeUnknown || removalOutcomeUnknown) onLoad(true)
    }
    LaunchedEffect(removalState.completionRevision, removedUsername) {
        if (
            removalMatches &&
            removedUsername != null &&
            removalState.completionRevision > 0L
        ) {
            removalEditorUsername = null
            onLoad(true)
        }
    }

    if (showInviteEditor) {
        PredictiveBackPage(
            enabled = !inviteBusy,
            onBack = {
                showInviteEditor = false
                onDismissInvite()
            },
        ) {
            ModeratorInviteEditor(
                accountStableId = accountStableId,
                communityName = communityName,
                activeUsername = activeUsername,
                activeModerators = projection.moderators,
                state = inviteState,
                onInvite = onInvite,
                onAuthorize = onAuthorizeInvite,
                onBack = {
                    showInviteEditor = false
                    onDismissInvite()
                },
            )
        }
        return
    }

    if (permissionTarget != null) {
        PredictiveBackPage(
            enabled = !permissionUpdateBusy,
            onBack = {
                permissionEditorUsername = null
                onDismissPermissionUpdate()
            },
        ) {
            ModeratorPermissionEditor(
                accountStableId = accountStableId,
                communityName = communityName,
                target = permissionTarget,
                state = permissionUpdateState,
                onUpdate = onUpdatePermissions,
                onAuthorize = onAuthorizePermissionUpdate,
                onBack = {
                    permissionEditorUsername = null
                    onDismissPermissionUpdate()
                },
            )
        }
        return
    }

    if (removalTarget != null) {
        PredictiveBackPage(
            enabled = !removalBusy,
            onBack = {
                removalEditorUsername = null
                onDismissRemoval()
            },
        ) {
            ModeratorRemovalEditor(
                accountStableId = accountStableId,
                communityName = communityName,
                target = removalTarget,
                avatarUri = avatarImageUri(removalTarget.username),
                state = removalState,
                onRemove = onRemoveModerator,
                onAuthorize = onAuthorizeRemoval,
                onBack = {
                    removalEditorUsername = null
                    onDismissRemoval()
                },
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("moderator-team-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Symbols.ArrowBack, stringResource(R.string.all_back_to_mod_tools))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.comp_moderators), style = MaterialTheme.typography.headlineSmall)
                    Text("r/$communityName", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(enabled = canRead && !projection.loading, onClick = { onLoad(true) }) {
                    Icon(Symbols.Refresh, stringResource(R.string.all_refresh_moderators))
                }
            }
        }
        item("moderator-team-invite") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = canRead && projection.ready && canInvite && !projection.loading,
                    onClick = {
                        onOpenInviteContext()
                        showInviteEditor = true
                    },
                ) {
                    Icon(Symbols.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.comp_invite_moderator))
                }
                if (canRead && projection.ready && !canInvite) {
                    Text(
                        stringResource(R.string.all2_reddit_requires_all_moderator_permissions_to_invite),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item("moderator-team-read-only") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.comp_moderator_permissions), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.all2_permissions_are_reported_by_reddit_eligible_moderators),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        invitedUsername?.let { username ->
            item("moderator-team-invite-success") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.comp_invitation_sent), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.comp_mod_invite_becomes_moderator, username),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        updatedUsername?.let { username ->
            item("moderator-team-permission-success") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.comp_permissions_updated), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.all_reddit_acknowledged_the_permission_change_for_u, username),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        removedUsername?.let { username ->
            item("moderator-team-removal-success") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.comp_moderator_removed), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.all_reddit_acknowledged_removal_of_u_refreshing_the, username),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        if (projection.ready && projection.moderators.isNotEmpty()) {
            item("moderator-team-search") {
                SubshellSearchField(
                    query = query,
                    onQueryChange = { query = it.take(100) },
                    placeholder = stringResource(R.string.comp_search_moderators),
                    supportingText = { Text(stringResource(R.string.comp_filters_the_loaded_moderator_list)) },
                )
            }
        }
        if (projection.loading) {
            item("moderator-team-loading") {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    InlineProgressIndicator()
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(if (projection.ready) stringResource(R.string.all_refreshing_moderators) else stringResource(R.string.all_loading_moderators))
                }
            }
        }
        projection.notice?.let { notice ->
            item("moderator-team-notice") {
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        text = when (notice) {
                            ModeratorTeamNotice.ACCESS_UNAVAILABLE ->
                                stringResource(R.string.all_moderator_overview_is_unavailable_for_this_account)
                            ModeratorTeamNotice.MODERATORS_UNAVAILABLE ->
                                stringResource(R.string.all_moderator_information_could_not_be_loaded_this)
                            ModeratorTeamNotice.REFRESH_FAILED ->
                                stringResource(R.string.all_could_not_refresh_showing_previously_loaded_moderator)
                        },
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (projection.ready && rows.isEmpty() && !projection.loading) {
            item("moderator-team-empty") {
                Text(
                    text = if (query.isBlank()) stringResource(R.string.all_reddit_returned_no_moderators) else stringResource(R.string.all_no_matching_moderators),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(items = rows, key = { "moderator-${it.username.lowercase()}" }) { moderator ->
            // Same visible-row identity pattern as Community About. No bulk profile crawl.
            LaunchedEffect(accountStableId, communityName, moderator.username) {
                try {
                    identities.withPermit { onEnsureIdentity(moderator.username) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Avatar enrichment must not hide a valid moderator or permission row.
                }
            }
            val canEditPermissions = moderatorTeamCanEditPermissions(
                moderators = projection.moderators,
                activeUsername = activeUsername,
                target = moderator,
            )
            val canRemove = moderatorTeamCanRemove(
                moderators = projection.moderators,
                activeUsername = activeUsername,
                target = moderator,
            )
            // MODERATION02A5A6_PERMISSION_ACTION_CARD_ATTACHMENT_V1
            // MODERATION02A5A7_REMOVE_MODERATOR_V1
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    ModeratorTeamRow(
                        moderator = moderator,
                        isCurrentUser = moderatorTeamIsCurrentUser(
                            moderator.username,
                            activeUsername,
                        ),
                        avatarUri = avatarImageUri(moderator.username),
                    )
                    if (canEditPermissions) {
                        TextButton(
                            modifier = Modifier
                                .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                                .align(Alignment.Start),
                            onClick = {
                                onOpenPermissionUpdateContext(moderator.username)
                                permissionEditorUsername = moderator.username
                            },
                        ) {
                            Icon(
                                imageVector = Symbols.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.comp_edit_permissions))
                        }
                    }
                    if (canRemove) {
                        TextButton(
                            modifier = Modifier
                                .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                                .align(Alignment.Start),
                            onClick = {
                                onOpenRemovalContext(moderator.username)
                                removalEditorUsername = moderator.username
                            },
                        ) {
                            Icon(
                                imageVector = Symbols.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.all_remove_moderator),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
        if (projection.ready && projection.moderators.isNotEmpty()) {
            item("moderator-team-count") {
                Text(
                    text = stringResource(R.string.comp_moderators_loaded_reddit_order, projection.moderators.size),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeratorTeamRow(
    moderator: CommunityModerator,
    isCurrentUser: Boolean,
    avatarUri: String?,
) {
    val labels = moderatorTeamPermissionLabels(moderator.permissions).map { it.asString() }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RemoteIdentityImage(
                    uri = avatarUri,
                    contentDescription = stringResource(R.string.comp_avatar_for_u, moderator.username),
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(moderator.username.firstOrNull()?.uppercase() ?: "u",
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.comp_u, moderator.username), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    if (isCurrentUser) {
                        Text(stringResource(R.string.comp_you), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (labels.isEmpty()) {
                // The existing parser cannot distinguish omitted permissions from an empty list.
                Text(stringResource(R.string.comp_permission_details_not_provided), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEach { label ->
                        Surface(shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}


// MODERATION02A5A5_INVITE_EDITOR_V1
@Composable
private fun ModeratorInviteEditor(
    accountStableId: String,
    communityName: String,
    activeUsername: String,
    activeModerators: List<CommunityModerator>,
    state: ModeratorInviteUiState,
    onInvite: (RedditModeratorInviteRequest) -> Unit,
    onAuthorize: () -> Unit,
    onBack: () -> Unit,
) {
    val entryPoints = LocalModeratorUserManagement.current
    val peopleSearchState = entryPoints.peopleSearchState()
    var username by rememberSaveable(accountStableId, communityName) { mutableStateOf("") }
    var selectedPerson by remember(accountStableId, communityName) {
        mutableStateOf<RedditPeopleSearchItem?>(null)
    }
    var allPermissions by rememberSaveable(accountStableId, communityName) { mutableStateOf(false) }
    var selectedPermissionCodes by rememberSaveable(accountStableId, communityName) {
        mutableStateOf("")
    }
    var pendingConfirmation by remember(accountStableId, communityName) {
        mutableStateOf<RedditModeratorInviteRequest?>(null)
    }
    val selectedPermissions = selectedPermissionCodes
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { code -> RedditModeratorPermission.entries.firstOrNull { it.wireValue == code } }
        .toSet()
    val normalizedLookup = username.trim().removePrefix("u/").removePrefix("U/").trim()
    val normalizedUsername = runCatching { normalizeRedditSafetyUsername(username) }.getOrNull()
    val existingNames = activeModerators.mapTo(mutableSetOf()) { it.username.lowercase() }
    val targetValid = normalizedUsername != null &&
        !normalizedUsername.equals(activeUsername.trim().removePrefix("u/").removePrefix("U/"), ignoreCase = true) &&
        normalizedUsername.lowercase() !in existingNames
    val permissionValid = allPermissions || selectedPermissions.isNotEmpty()
    val searchEligible = selectedPerson == null && normalizedLookup.length in 2..24
    val searchMatches = searchEligible && peopleSearchState.matches(accountStableId, normalizedLookup)
    val people = if (searchMatches) {
        peopleSearchState.items.asSequence()
            .filterNot { it.username.equals(activeUsername, ignoreCase = true) }
            .filterNot { it.username.lowercase() in existingNames }
            .take(MODERATOR_INVITE_SEARCH_RESULT_LIMIT)
            .toList()
    } else {
        emptyList()
    }
    val exactUsernameFallback = normalizedUsername?.takeIf {
        selectedPerson == null &&
            searchMatches &&
            peopleSearchState.loaded &&
            people.isEmpty() &&
            targetValid
    }
    val stateMatches = state.matches(accountStableId, communityName)
    val busy = stateMatches && state.busy
    val outcomeUnknown = stateMatches && state.failureKind == ModeratorInviteFailureKind.OUTCOME_UNKNOWN
    val request = if (targetValid && permissionValid) {
        runCatching {
            RedditModeratorInviteRequest(
                accountStableId = accountStableId,
                communityName = communityName,
                username = requireNotNull(normalizedUsername),
                allPermissions = allPermissions,
                permissions = if (allPermissions) emptySet() else selectedPermissions,
            )
        }.getOrNull()
    } else {
        null
    }

    pendingConfirmation?.let { pending ->
        val permissionSummary = if (pending.allPermissions) {
            stringResource(R.string.all_all_permissions)
        } else {
            pending.permissions
                .map { permission -> moderatorInvitePermissionLabel(permission) }
                .sorted()
                .joinToString(", ")
        }
        AlertDialog(
            onDismissRequest = { if (!busy) pendingConfirmation = null },
            title = { Text(stringResource(R.string.comp_invite_u, pending.username)) },
            text = {
                Text(
                    stringResource(R.string.comp_mod_invite_confirm, communityName, permissionSummary),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingConfirmation = null
                        onInvite(pending)
                    },
                ) {
                    Text(stringResource(R.string.comp_send_invitation))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { pendingConfirmation = null },
                ) {
                    Text(stringResource(R.string.comp_cancel))
                }
            },
        )
    }

    LaunchedEffect(accountStableId, normalizedLookup, selectedPerson?.username) {
        if (searchEligible) {
            delay(MODERATOR_INVITE_SEARCH_DEBOUNCE_MS)
            entryPoints.searchPeople(normalizedLookup)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("moderator-invite-header") {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = !busy, onClick = onBack) {
                    Icon(Symbols.ArrowBack, stringResource(R.string.all_back_to_moderators))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.comp_invite_moderator), style = MaterialTheme.typography.headlineSmall)
                    Text("r/$communityName", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item("moderator-invite-explainer") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    stringResource(R.string.all_reddit_sends_an_invitation_the_account_becomes),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item("moderator-invite-target") {
            if (selectedPerson == null) {
                SubshellSearchField(
                    query = username,
                    onQueryChange = { username = it.take(24) },
                    placeholder = stringResource(R.string.comp_search_reddit_users),
                    enabled = !busy,
                    supportingText = {
                        Text(
                            when {
                                normalizedUsername != null && normalizedUsername.lowercase() in existingNames ->
                                    stringResource(R.string.all_this_account_is_already_in_the_active)
                                normalizedUsername != null && normalizedUsername.equals(
                                    activeUsername.trim().removePrefix("u/").removePrefix("U/"),
                                    ignoreCase = true,
                                ) -> stringResource(R.string.all_you_cannot_invite_your_own_account)
                                normalizedUsername != null ->
                                    stringResource(R.string.all_select_a_result_or_use_the_exact)
                                else -> stringResource(R.string.all_enter_a_valid_reddit_username)
                            },
                        )
                    },
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ModeratorInviteAvatar(requireNotNull(selectedPerson))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.comp_u, selectedPerson!!.username), style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.comp_selected_moderator_invite), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(enabled = !busy, onClick = {
                            selectedPerson = null
                            username = ""
                        }) {
                            Icon(Symbols.Close, stringResource(R.string.all_change_moderator_invite_target))
                        }
                    }
                }
            }
        }
        if (selectedPerson == null && searchEligible) {
            when {
                peopleSearchState.loading && peopleSearchState.matches(accountStableId, normalizedLookup) -> {
                    item("moderator-invite-search-loading") {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            InlineProgressIndicator()
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.comp_searching_reddit_users))
                        }
                    }
                }
                people.isNotEmpty() -> {
                    items(items = people, key = { "moderator-invite-${it.username.lowercase()}" }) { person ->
                        ListCard(
                            headline = stringResource(R.string.comp_u, person.username),
                            supporting = person.description,
                            onClick = {
                                selectedPerson = person
                                username = person.username
                            },
                            enabled = !busy,
                            shape = MaterialTheme.shapes.large,
                            leading = { ModeratorInviteAvatar(person) },
                        )
                    }
                }
                peopleSearchState.loaded && peopleSearchState.matches(accountStableId, normalizedLookup) -> {
                    item("moderator-invite-search-empty") {
                        if (exactUsernameFallback != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.all_no_profile_match),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        stringResource(R.string.all2_reddit_search_did_not_return_a_profile, exactUsernameFallback),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            Text(
                                stringResource(R.string.all_no_matching_reddit_users),
                                modifier = Modifier.padding(horizontal = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item("moderator-invite-permissions-title") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.comp_permissions), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.all_choose_only_the_permissions_this_moderator_needs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item("moderator-invite-all-permissions") {
            ModeratorInvitePermissionRow(
                label = stringResource(R.string.comp_all_permissions),
                description = stringResource(R.string.comp_full_moderator_access),
                checked = allPermissions,
                enabled = !busy,
                onCheckedChange = { checked ->
                    allPermissions = checked
                    if (checked) selectedPermissionCodes = ""
                },
            )
        }
        items(items = RedditModeratorPermission.entries, key = { "invite-permission-${it.wireValue}" }) { permission ->
            val checked = permission in selectedPermissions
            ModeratorInvitePermissionRow(
                label = moderatorInvitePermissionLabel(permission),
                description = moderatorInvitePermissionDescription(permission),
                checked = checked,
                enabled = !busy && !allPermissions,
                onCheckedChange = { value ->
                    val updated = selectedPermissions.toMutableSet()
                    if (value) updated += permission else updated -= permission
                    selectedPermissionCodes = updated.map { it.wireValue }.sorted().joinToString(",")
                },
            )
        }
        if (!permissionValid) {
            item("moderator-invite-permission-required") {
                Text(stringResource(R.string.comp_choose_at_least_one_permission_or_all),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (stateMatches && state.authorizationRequired) {
            item("moderator-invite-authorize") {
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.comp_authorization_required), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.comp_mod_invite_needs_permission))
                        FilledTonalButton(enabled = !state.authorizing, onClick = onAuthorize) {
                            Text(if (state.authorizing) stringResource(R.string.all_authorizing) else stringResource(R.string.all_authorize_moderator_invites))
                        }
                    }
                }
            }
        }
        if (stateMatches && state.authorizationReady) {
            item("moderator-invite-authorized") {
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        stringResource(R.string.all_authorization_updated_review_the_target_and_permissions),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        if (stateMatches && state.failureKind != null) {
            item("moderator-invite-failure") {
                InlineErrorNotice( // ISSUE222_ONE_INLINE_ERROR_NOTICE_V1
                    message = moderatorInviteFailureMessage(
                        requireNotNull(state.failureKind),
                        state.failureHttpStatusCode,
                        state.failureDetails,
                    ),
                )
            }
        }
        item("moderator-invite-confirm") {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = request != null && !busy &&
                    !(stateMatches && state.authorizationRequired) && !outcomeUnknown,
                onClick = { request?.let { pendingConfirmation = it } },
            ) {
                Text(if (busy && state.sending) stringResource(R.string.all_sending_invitation) else stringResource(R.string.all_invite_moderator))
            }
        }
    }
}

@Composable
private fun ModeratorInviteAvatar(person: RedditPeopleSearchItem) {
    RemoteIdentityImage(
        uri = person.avatarImageUri,
        contentDescription = stringResource(R.string.comp_avatar_for_u, person.username),
        modifier = Modifier.size(44.dp).clip(CircleShape),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(contentAlignment = Alignment.Center) {
                Text(person.username.firstOrNull()?.uppercase() ?: "u",
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ModeratorInvitePermissionRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        // LOWUI7_SWITCH_ROWS_ARE_LABELLED_V1 (OCB-150): the row is the checkbox.
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(checked = checked, enabled = enabled, onCheckedChange = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun moderatorInvitePermissionLabel(permission: RedditModeratorPermission): String = when (permission) {
    RedditModeratorPermission.ACCESS -> stringResource(R.string.comp_mod_permission_access)
    RedditModeratorPermission.CONFIG -> stringResource(R.string.comp_mod_permission_config)
    RedditModeratorPermission.FLAIR -> stringResource(R.string.comp_mod_permission_flair)
    RedditModeratorPermission.MAIL -> stringResource(R.string.comp_mod_permission_mail)
    RedditModeratorPermission.POSTS -> stringResource(R.string.comp_mod_permission_posts)
    RedditModeratorPermission.WIKI -> stringResource(R.string.comp_mod_permission_wiki)
}

@Composable
private fun moderatorInvitePermissionDescription(permission: RedditModeratorPermission): String = when (permission) {
    RedditModeratorPermission.ACCESS -> stringResource(R.string.comp_mod_permission_desc_access)
    RedditModeratorPermission.CONFIG -> stringResource(R.string.comp_mod_permission_desc_config)
    RedditModeratorPermission.FLAIR -> stringResource(R.string.comp_mod_permission_desc_flair)
    RedditModeratorPermission.MAIL -> stringResource(R.string.comp_mod_permission_desc_mail)
    RedditModeratorPermission.POSTS -> stringResource(R.string.comp_mod_permission_desc_posts)
    RedditModeratorPermission.WIKI -> stringResource(R.string.comp_mod_permission_desc_wiki)
}

@Composable
private fun moderatorInviteFailureMessage(
    kind: ModeratorInviteFailureKind,
    httpStatusCode: Int?,
    failureDetails: FailureDetails?,
): String {
    val summary = when (kind) {
        ModeratorInviteFailureKind.CLIENT_CONFIGURATION ->
            stringResource(R.string.comp_mod_invite_failed_client_configuration)
        ModeratorInviteFailureKind.SESSION ->
            stringResource(R.string.comp_mod_invite_failed_session)
        ModeratorInviteFailureKind.AUTHORIZATION ->
            stringResource(R.string.comp_mod_invite_failed_authorization)
        ModeratorInviteFailureKind.HTTP ->
            stringResource(R.string.comp_mod_invite_failed_http)
        ModeratorInviteFailureKind.TRANSPORT ->
            stringResource(R.string.comp_mod_invite_failed_transport)
        ModeratorInviteFailureKind.PROTOCOL ->
            stringResource(R.string.comp_mod_invite_failed_protocol)
        ModeratorInviteFailureKind.VALIDATION ->
            stringResource(R.string.comp_mod_invite_failed_validation)
        ModeratorInviteFailureKind.OUTCOME_UNKNOWN ->
            stringResource(R.string.comp_mod_invite_failed_outcome_unknown)
        ModeratorInviteFailureKind.ALREADY_MUTATING ->
            stringResource(R.string.comp_mod_invite_failed_already_mutating)
        ModeratorInviteFailureKind.ACCOUNT_CHANGED ->
            stringResource(R.string.comp_mod_invite_failed_account_changed)
    }
    val technical = failureDetails?.toSafeTechnicalDetail()
        ?: httpStatusCode
            ?.takeIf { kind == ModeratorInviteFailureKind.HTTP }
            ?.let { status -> "HTTP $status" }
    return technical?.let { detail -> "$summary\n$detail" } ?: summary
}

// MODERATION02A5A6_PERMISSION_EDITOR_V1
@Composable
private fun ModeratorPermissionEditor(
    accountStableId: String,
    communityName: String,
    target: CommunityModerator,
    state: ModeratorPermissionUpdateUiState,
    onUpdate: (RedditModeratorPermissionUpdateRequest) -> Unit,
    onAuthorize: () -> Unit,
    onBack: () -> Unit,
) {
    val originalAllPermissions = target.permissions.any {
        it.trim().equals("all", ignoreCase = true)
    }
    val originalPermissions = target.permissions.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filterNot { it.equals("all", ignoreCase = true) }
        .mapNotNull { code ->
            RedditModeratorPermission.entries.firstOrNull {
                it.wireValue.equals(code, ignoreCase = true)
            }
        }
        .toSet()
    val editorKey = target.permissions.joinToString(",")
    var allPermissions by rememberSaveable(
        accountStableId,
        communityName,
        target.username,
        editorKey,
    ) {
        mutableStateOf(originalAllPermissions)
    }
    var selectedPermissionCodes by rememberSaveable(
        accountStableId,
        communityName,
        target.username,
        editorKey,
    ) {
        mutableStateOf(
            if (originalAllPermissions) {
                ""
            } else {
                originalPermissions
                    .map(RedditModeratorPermission::wireValue)
                    .sorted()
                    .joinToString(",")
            },
        )
    }
    var pendingConfirmation by remember(
        accountStableId,
        communityName,
        target.username,
    ) {
        mutableStateOf<RedditModeratorPermissionUpdateRequest?>(null)
    }
    val selectedPermissions = selectedPermissionCodes
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { code ->
            RedditModeratorPermission.entries.firstOrNull { it.wireValue == code }
        }
        .toSet()
    val stateMatches = state.matches(accountStableId, communityName, target.username)
    val busy = stateMatches && state.busy
    val outcomeUnknown = stateMatches &&
        state.failureKind == ModeratorPermissionUpdateFailureKind.OUTCOME_UNKNOWN
    val permissionValid = allPermissions || selectedPermissions.isNotEmpty()
    val changed = if (allPermissions) {
        !originalAllPermissions
    } else {
        originalAllPermissions || selectedPermissions != originalPermissions
    }
    val request = if (
        moderatorTeamPermissionSetIsEditable(target.permissions) &&
        permissionValid &&
        changed
    ) {
        runCatching {
            // ISSUE53_CANONICAL_MODERATOR_TARGET_V1: the team lists usernames in
            // display case; the request requires canonical names (a mixed-case
            // name made Save permissions do nothing).
            RedditModeratorPermissionUpdateRequest(
                accountStableId = accountStableId,
                communityName = normalizeRedditCommunityName(communityName),
                username = normalizeRedditSafetyUsername(target.username),
                allPermissions = allPermissions,
                permissions = if (allPermissions) emptySet() else selectedPermissions,
            )
        }.getOrNull()
    } else {
        null
    }

    pendingConfirmation?.let { pending ->
        val permissionSummary = if (pending.allPermissions) {
            stringResource(R.string.all_all_permissions)
        } else {
            pending.permissions
                .map { permission -> moderatorInvitePermissionLabel(permission) }
                .sorted()
                .joinToString(", ")
        }
        AlertDialog(
            onDismissRequest = { if (!busy) pendingConfirmation = null },
            title = { Text(stringResource(R.string.comp_update_u, pending.username)) },
            text = {
                Text(
                    stringResource(R.string.comp_mod_update_confirm, communityName, permissionSummary),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingConfirmation = null
                        onUpdate(pending)
                    },
                ) {
                    Text(stringResource(R.string.comp_save_changes))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { pendingConfirmation = null },
                ) {
                    Text(stringResource(R.string.comp_cancel))
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("moderator-permission-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(enabled = !busy, onClick = onBack) {
                    Icon(Symbols.ArrowBack, stringResource(R.string.all_back_to_moderators))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.comp_edit_permissions), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "u/${target.username} · r/$communityName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item("moderator-permission-explainer") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    stringResource(R.string.all2_this_replaces_the_selected_moderator_permissions_reddit),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item("moderator-permission-all") {
            ModeratorInvitePermissionRow(
                label = stringResource(R.string.comp_all_permissions),
                description = stringResource(R.string.comp_full_moderator_access),
                checked = allPermissions,
                enabled = !busy,
                onCheckedChange = { checked ->
                    allPermissions = checked
                    if (checked) selectedPermissionCodes = ""
                },
            )
        }
        items(
            items = RedditModeratorPermission.entries,
            key = { "edit-permission-${it.wireValue}" },
        ) { permission ->
            val checked = permission in selectedPermissions
            ModeratorInvitePermissionRow(
                label = moderatorInvitePermissionLabel(permission),
                description = moderatorInvitePermissionDescription(permission),
                checked = checked,
                enabled = !busy && !allPermissions,
                onCheckedChange = { value ->
                    val updated = selectedPermissions.toMutableSet()
                    if (value) updated += permission else updated -= permission
                    selectedPermissionCodes = updated
                        .map(RedditModeratorPermission::wireValue)
                        .sorted()
                        .joinToString(",")
                },
            )
        }
        if (!permissionValid) {
            item("moderator-permission-required") {
                Text(
                    stringResource(R.string.all_choose_at_least_one_permission_or_all),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (permissionValid && !changed) {
            item("moderator-permission-unchanged") {
                Text(
                    stringResource(R.string.all_no_permission_changes_selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (stateMatches && state.authorizationRequired) {
            item("moderator-permission-authorize") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(R.string.comp_authorization_required), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.comp_mod_update_needs_permission),
                        )
                        FilledTonalButton(enabled = !state.authorizing, onClick = onAuthorize) {
                            Text(
                                if (state.authorizing) {
                                    stringResource(R.string.all_authorizing)
                                } else {
                                    stringResource(R.string.all_authorize_permission_changes)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (stateMatches && state.authorizationReady) {
            item("moderator-permission-authorized") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        stringResource(R.string.all2_authorization_updated_review_the_permissions_then_press),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        if (stateMatches && state.failureKind != null) {
            item("moderator-permission-failure") {
                InlineErrorNotice( // ISSUE222_ONE_INLINE_ERROR_NOTICE_V1
                    message = moderatorPermissionUpdateFailureMessage(
                        requireNotNull(state.failureKind),
                        state.failureHttpStatusCode,
                        state.failureDetails,
                    ),
                )
            }
        }
        item("moderator-permission-confirm") {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = request != null && !busy &&
                    !(stateMatches && state.authorizationRequired) && !outcomeUnknown,
                onClick = { request?.let { pendingConfirmation = it } },
            ) {
                Text(if (busy && state.sending) stringResource(R.string.all_saving_permissions) else stringResource(R.string.all_save_permissions))
            }
        }
    }
}

@Composable
private fun moderatorPermissionUpdateFailureMessage(
    kind: ModeratorPermissionUpdateFailureKind,
    httpStatusCode: Int?,
    failureDetails: FailureDetails?,
): String {
    val summary = when (kind) {
        ModeratorPermissionUpdateFailureKind.CLIENT_CONFIGURATION ->
            stringResource(R.string.comp_mod_permission_failed_client_configuration)
        ModeratorPermissionUpdateFailureKind.SESSION ->
            stringResource(R.string.comp_mod_permission_failed_session)
        ModeratorPermissionUpdateFailureKind.AUTHORIZATION ->
            stringResource(R.string.comp_mod_permission_failed_authorization)
        ModeratorPermissionUpdateFailureKind.HTTP ->
            stringResource(R.string.comp_mod_permission_failed_http)
        ModeratorPermissionUpdateFailureKind.TRANSPORT ->
            stringResource(R.string.comp_mod_permission_failed_transport)
        ModeratorPermissionUpdateFailureKind.PROTOCOL ->
            stringResource(R.string.comp_mod_permission_failed_protocol)
        ModeratorPermissionUpdateFailureKind.VALIDATION ->
            stringResource(R.string.comp_mod_permission_failed_validation)
        ModeratorPermissionUpdateFailureKind.OUTCOME_UNKNOWN ->
            stringResource(R.string.comp_mod_permission_failed_outcome_unknown)
        ModeratorPermissionUpdateFailureKind.ALREADY_MUTATING ->
            stringResource(R.string.comp_mod_permission_failed_already_mutating)
        ModeratorPermissionUpdateFailureKind.ACCOUNT_CHANGED ->
            stringResource(R.string.comp_mod_permission_failed_account_changed)
    }
    val technical = failureDetails?.toSafeTechnicalDetail()
        ?: httpStatusCode
            ?.takeIf { kind == ModeratorPermissionUpdateFailureKind.HTTP }
            ?.let { status -> "HTTP $status" }
    return technical?.let { detail -> "$summary\n$detail" } ?: summary
}


// MODERATION02A5A7_REMOVE_MODERATOR_EDITOR_V1
@Composable
internal fun ModeratorRemovalEditor(
    accountStableId: String,
    communityName: String,
    target: CommunityModerator,
    avatarUri: String?,
    state: ModeratorRemovalUiState,
    onRemove: (RedditModeratorRemovalRequest) -> Unit,
    onAuthorize: () -> Unit,
    onBack: () -> Unit,
) {
    var pendingConfirmation by remember(
        accountStableId,
        communityName,
        target.username,
    ) {
        mutableStateOf<RedditModeratorRemovalRequest?>(null)
    }
    val stateMatches = state.matches(accountStableId, communityName, target.username)
    val busy = stateMatches && state.busy
    val outcomeUnknown = stateMatches &&
        state.failureKind == ModeratorRemovalFailureKind.OUTCOME_UNKNOWN
    val request = runCatching {
        // ISSUE53_CANONICAL_MODERATOR_TARGET_V1
        RedditModeratorRemovalRequest(
            accountStableId = accountStableId,
            communityName = normalizeRedditCommunityName(communityName),
            username = normalizeRedditSafetyUsername(target.username),
        )
    }.getOrNull()

    pendingConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingConfirmation = null },
            title = { Text(stringResource(R.string.comp_remove_u, pending.username)) },
            text = {
                Text(
                    stringResource(R.string.comp_mod_remove_confirm, pending.username, communityName),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingConfirmation = null
                        onRemove(pending)
                    },
                ) {
                    Text(
                        stringResource(R.string.all_remove_moderator),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { pendingConfirmation = null },
                ) {
                    Text(stringResource(R.string.comp_cancel))
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("moderator-removal-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(enabled = !busy, onClick = onBack) {
                    Icon(Symbols.ArrowBack, stringResource(R.string.all_back_to_moderators))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.comp_remove_moderator), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "u/${target.username} · r/$communityName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item("moderator-removal-explainer") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    stringResource(R.string.comp_mod_remove_explained),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        item("moderator-removal-target") {
            ModeratorTeamRow(
                moderator = target,
                isCurrentUser = false,
                avatarUri = avatarUri,
            )
        }
        if (stateMatches && state.authorizationRequired) {
            item("moderator-removal-authorize") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(R.string.comp_authorization_required), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.comp_mod_remove_needs_permission),
                        )
                        FilledTonalButton(enabled = !state.authorizing, onClick = onAuthorize) {
                            Text(
                                if (state.authorizing) {
                                    stringResource(R.string.all_authorizing)
                                } else {
                                    stringResource(R.string.all_authorize_moderator_removal)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (stateMatches && state.authorizationReady) {
            item("moderator-removal-authorized") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        stringResource(R.string.all2_authorization_updated_review_the_target_then_press),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        if (stateMatches && state.failureKind != null) {
            item("moderator-removal-failure") {
                InlineErrorNotice( // ISSUE222_ONE_INLINE_ERROR_NOTICE_V1
                    message = moderatorRemovalFailureMessage(
                        requireNotNull(state.failureKind),
                        state.failureHttpStatusCode,
                        state.failureDetails,
                    ),
                )
            }
        }
        item("moderator-removal-confirm") {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = request != null && !busy &&
                    !(stateMatches && state.authorizationRequired) && !outcomeUnknown,
                onClick = { request?.let { pendingConfirmation = it } },
            ) {
                Text(
                    if (busy && state.removing) {
                        stringResource(R.string.all_removing_moderator)
                    } else {
                        stringResource(R.string.all_remove_moderator)
                    },
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun moderatorRemovalFailureMessage(
    kind: ModeratorRemovalFailureKind,
    httpStatusCode: Int?,
    failureDetails: FailureDetails?,
): String {
    val summary = when (kind) {
        ModeratorRemovalFailureKind.CLIENT_CONFIGURATION ->
            stringResource(R.string.comp_mod_removal_failed_client_configuration)
        ModeratorRemovalFailureKind.SESSION ->
            stringResource(R.string.comp_mod_removal_failed_session)
        ModeratorRemovalFailureKind.AUTHORIZATION ->
            stringResource(R.string.comp_mod_removal_failed_authorization)
        ModeratorRemovalFailureKind.HTTP ->
            stringResource(R.string.comp_mod_removal_failed_http)
        ModeratorRemovalFailureKind.TRANSPORT ->
            stringResource(R.string.comp_mod_removal_failed_transport)
        ModeratorRemovalFailureKind.PROTOCOL ->
            stringResource(R.string.comp_mod_removal_failed_protocol)
        ModeratorRemovalFailureKind.VALIDATION ->
            stringResource(R.string.comp_mod_removal_failed_validation)
        ModeratorRemovalFailureKind.OUTCOME_UNKNOWN ->
            stringResource(R.string.comp_mod_removal_failed_outcome_unknown)
        ModeratorRemovalFailureKind.ALREADY_MUTATING ->
            stringResource(R.string.comp_mod_removal_failed_already_mutating)
        ModeratorRemovalFailureKind.ACCOUNT_CHANGED ->
            stringResource(R.string.comp_mod_removal_failed_account_changed)
    }
    val technical = failureDetails?.toSafeTechnicalDetail()
        ?: httpStatusCode
            ?.takeIf {
                kind == ModeratorRemovalFailureKind.HTTP ||
                    kind == ModeratorRemovalFailureKind.OUTCOME_UNKNOWN
            }
            ?.let { status -> "HTTP $status" }
    return technical?.let { detail -> "$summary\n$detail" } ?: summary
}

private const val MODERATOR_INVITE_SEARCH_DEBOUNCE_MS = 300L
private const val MODERATOR_INVITE_SEARCH_RESULT_LIMIT = 6

/** ISSUE86_EDITOR_STAYS_WHILE_ITS_WRITE_RUNS_V1: a workspace write in flight. */
internal class ModeratorWorkspaceBusy {
    var busy by mutableStateOf(false)
}

internal val LocalModeratorWorkspaceBusy = staticCompositionLocalOf<ModeratorWorkspaceBusy?> { null }
