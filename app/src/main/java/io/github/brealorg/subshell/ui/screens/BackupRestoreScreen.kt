package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.R
import io.github.brealorg.subshell.ui.UiText
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.ui.SavedSearch
import io.github.brealorg.subshell.ui.ScreenMargin
import io.github.brealorg.subshell.ui.components.RouteTopBar
import io.github.brealorg.subshell.ui.components.rememberRouteTopBarScrollBehavior
import io.github.brealorg.subshell.ui.identityKey
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.backup.BACKUP_IMPORT_MAX_CHARACTERS
import io.github.brealorg.subshell.backup.readBackupTextBounded
import io.github.brealorg.subshell.backup.BackupFailureException
import io.github.brealorg.subshell.backup.BackupFailureOperation
import io.github.brealorg.subshell.backup.BackupRetention
import io.github.brealorg.subshell.backup.BackupProtection
import io.github.brealorg.subshell.backup.BackupStatus
import io.github.brealorg.subshell.backup.SettingsBackupRepository
import io.github.brealorg.subshell.backup.PortableCommunityMute
import io.github.brealorg.subshell.backup.PortableModeratorSavedResponse
import io.github.brealorg.subshell.backup.PortableUserTag
import io.github.brealorg.subshell.backup.PortablePostComposerDraft
import io.github.brealorg.subshell.backup.SettingsSnapshot
import io.github.brealorg.subshell.backup.SnapshotKind
import io.github.brealorg.subshell.backup.toBackupFailureDetails
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.LocalFailureKind
import io.github.brealorg.subshell.data.error.toSafeTechnicalDetail
import io.github.brealorg.subshell.ui.SubshellSettings
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.error.ReportViewportError
import io.github.brealorg.subshell.ui.error.backupErrorPresentation
import io.github.brealorg.subshell.ui.symbols.Symbols
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ListItem
import io.github.brealorg.subshell.ui.error.LocalViewportErrorHostState
import io.github.brealorg.subshell.ui.noticeInfo
import androidx.compose.foundation.clickable
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import io.github.brealorg.subshell.ui.PortableBackupState
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    state: SubshellState,
    repository: SettingsBackupRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // LOWUX3_RESTORE_PREVIEW_NAMES_ACCOUNTS_V1 (OCB-176): per-account modules
    // are keyed by stable id ("reddit:native_production:…"); the preview names
    // the account instead.
    val unknownAccount = stringResource(R.string.backup_another_account)
    val thisDevice = stringResource(R.string.backup_this_device)
    val restoreAccountLabel: (String) -> String = { key ->
        if (key == RESTORE_THIS_DEVICE_KEY) thisDevice else restorePreviewAccountLabel(key, state.accountSessions, unknownAccount)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // ISSUE89_BACKUP_SCREEN_READS_OFF_MAIN_V1: listSnapshots() decodes every
    // snapshot under the repository monitor, which a vault sync holds on IO for
    // its whole run, and status() may ask the documents provider once; both ran
    // in composition and after every action, so the main thread waited for a
    // sync (jank, ANR risk). They are read on IO; null until the first read.
    var loadedSnapshots by remember(repository) { mutableStateOf<List<SettingsSnapshot>?>(null) }
    val snapshots = loadedSnapshots.orEmpty()
    var loadedStatus by remember(repository) { mutableStateOf<BackupStatus?>(null) }
    var readRevision by remember(repository) { mutableIntStateOf(0) }
    LaunchedEffect(repository, readRevision) {
        val (listed, current) = withContext(Dispatchers.IO) {
            repository.listSnapshots() to repository.status()
        }
        loadedSnapshots = listed
        loadedStatus = current
    }
    var automaticHistory by remember(repository) { mutableStateOf(repository.automaticHistoryEnabled()) }
    var includeHiddenCommunities by remember(repository) {
        mutableStateOf(repository.hiddenCommunitiesBackupEnabled())
    }
    var includeCommunityMutes by remember(repository) {
        mutableStateOf(repository.communityMutesBackupEnabled())
    }
    var includePostComposerDrafts by remember(repository) {
        mutableStateOf(repository.postComposerDraftsBackupEnabled())
    }
    // BACKUP04A4_MODERATOR_SAVED_RESPONSES_SCHEMA_V8_OPT_IN_V1
    var includeModeratorSavedResponses by remember(repository) {
        mutableStateOf(repository.moderatorSavedResponsesBackupEnabled())
    }
    // BACKUP04A5_USER_TAGS_SCHEMA_V9_OPT_IN_V1
    var includeUserTags by remember(repository) {
        mutableStateOf(repository.userTagsBackupEnabled())
    }
    var automaticHistoryRetention by remember(repository) {
        mutableStateOf(repository.automaticHistoryRetention())
    }
    var safetySnapshotRetention by remember(repository) {
        mutableStateOf(repository.safetySnapshotRetention())
    }
    // ISSUE270_BACKUP_NOTICES_USE_THE_TRAY_V1: an answer to an action goes to the
    // app's one message surface (ISSUE222), which shows wherever the reader is.
    val notices = LocalViewportErrorHostState.current
    fun showNotice(notice: BackupNotice) = notices.noticeInfo(notice.title, notice.detail)
    var failureOperation by remember { mutableStateOf<BackupFailureOperation?>(null) }
    var failureDetails by remember { mutableStateOf<FailureDetails?>(null) }
    var saveDialogVisible by remember { mutableStateOf(false) }
    val defaultSaveName = stringResource(R.string.backup_default_name)
    var saveName by remember { mutableStateOf(defaultSaveName) }
    var renameTarget by remember { mutableStateOf<SettingsSnapshot?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SettingsSnapshot?>(null) }
    var deleteAllDialogVisible by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<PendingRestore?>(null) }
    var retentionChangeTarget by remember { mutableStateOf<PendingRetentionChange?>(null) }
    // LOWUI6_PENDING_EXPORT_SURVIVES_RECREATION_V1 (OCB-174): the picker
    // creates the file before it returns; a recreation while it was open lost
    // which setup to write and left an empty file. The id and name are saved.
    var pendingExportId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportName by rememberSaveable { mutableStateOf<String?>(null) }
    var backupTab by rememberSaveable { mutableStateOf(BackupListTab.YOURS) }

    fun refresh() {
        readRevision += 1
        automaticHistory = repository.automaticHistoryEnabled()
        includeHiddenCommunities = repository.hiddenCommunitiesBackupEnabled()
        includeCommunityMutes = repository.communityMutesBackupEnabled()
        includePostComposerDrafts = repository.postComposerDraftsBackupEnabled()
        includeModeratorSavedResponses = repository.moderatorSavedResponsesBackupEnabled()
        includeUserTags = repository.userTagsBackupEnabled()
        automaticHistoryRetention = repository.automaticHistoryRetention()
        safetySnapshotRetention = repository.safetySnapshotRetention()
    }

    fun reportFailure(
        operation: BackupFailureOperation,
        error: Throwable,
    ) {
        failureOperation = operation
        failureDetails = error.toBackupFailureDetails()
        refresh()
    }

    fun runInBackground(
        operation: BackupFailureOperation,
        action: () -> BackupNotice,
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(action) }
            result.onSuccess { notice ->
                refresh()
                failureOperation = null
                failureDetails = null
                showNotice(notice)
            }.onFailure { error ->
                reportFailure(operation, error)
            }
        }
    }

    fun recordPortableChange(currentPortable: PortableBackupState) {
        repository.recordSettingsChange(
            settings = currentPortable.settings,
            joinedCommunityIdsByAccount = currentPortable.joinedCommunityIdsByAccount,
            favoriteCommunityNamesByAccount = currentPortable.favoriteCommunityNamesByAccount,
            favoriteCustomFeedPathsByAccount = currentPortable.favoriteCustomFeedPathsByAccount,
            hiddenCommunityNamesByAccount = currentPortable.hiddenCommunityNamesByAccount,
            communityMutesByAccount = currentPortable.communityMutesByAccount,
            postComposerDraftsByAccount = currentPortable.postComposerDraftsByAccount,
            moderatorSavedResponses = currentPortable.moderatorSavedResponses,
            userTagsByAccount = currentPortable.userTagsByAccount,
            savedSearchesByAccount = currentPortable.savedSearchesByAccount,
            watchedCommunityNamesByAccount = currentPortable.watchedCommunityNamesByAccount,
        )
    }

    // A privacy-sensitive switch also decides whether a restore applies the data.
    fun recordBackupChoice(enabled: Boolean, @androidx.annotation.StringRes backedUp: Int, @androidx.annotation.StringRes leftOut: Int) {
        val notice = if (enabled) {
            BackupNotice(UiText.Resource(backedUp), UiText.Resource(R.string.backup_notice_backed_up_detail))
        } else {
            BackupNotice(UiText.Resource(leftOut), UiText.Resource(R.string.backup_notice_left_out_detail))
        }
        if (automaticHistory) {
            val currentPortable = state.capturePortableBackupState()
            runInBackground(BackupFailureOperation.SAVE) {
                recordPortableChange(currentPortable)
                notice
            }
        } else {
            showNotice(notice)
        }
    }

    fun pendingRestoreOf(snapshot: SettingsSnapshot) = PendingRestore(
        snapshot = snapshot,
        currentSettings = state.captureSettings(),
        currentJoinedCommunityIdsByAccount = state.captureJoinedCommunityIdsByAccount(),
        currentFavoriteCommunityNamesByAccount = state.captureFavoriteCommunityNamesByAccount(),
        currentFavoriteCustomFeedPathsByAccount = state.captureFavoriteCustomFeedPathsByAccount(),
        currentHiddenCommunityNamesByAccount = state.captureHiddenCommunityNamesByAccount(),
        currentCommunityMutesByAccount = state.captureCommunityMutesByAccount(),
        currentPostComposerDraftsByAccount = state.capturePostComposerDraftsByAccount(),
        currentModeratorSavedResponses = state.captureModeratorSavedResponses(),
        currentUserTagsByAccount = state.captureUserTagsByAccount(),
        currentSavedSearchesByAccount = state.captureSavedSearchesByAccount(),
        currentWatchedCommunityNamesByAccount = state.captureWatchedCommunityNamesByAccount(),
    )

    ReportViewportError(
        scopeKey = "backup-restore",
        presentation = if (failureOperation != null && failureDetails != null) {
            backupErrorPresentation(
                operation = requireNotNull(failureOperation),
                details = requireNotNull(failureDetails),
            )
        } else {
            null
        },
        failureDetails = failureDetails,
        onRetry = null,
    )

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runInBackground(BackupFailureOperation.CONNECT_FOLDER) {
                val connection = repository.connectFolder(uri)
                when {
                    connection.isProtected && connection.importedSnapshots > 0 ->
                        BackupNotice(UiText.Resource(R.string.backup_notice_folder_connected), UiText.Plural(R.plurals.backup_notice_found_in_folder, connection.importedSnapshots, connection.importedSnapshots))
                    connection.isProtected -> BackupNotice(UiText.Resource(R.string.backup_notice_folder_connected), UiText.Resource(R.string.backup_notice_copied_to_folder))
                    else -> throw BackupFailureException(
                        connection.failureDetails ?: FailureDetails(
                            retryable = true,
                            localKind = LocalFailureKind.UNKNOWN,
                        ),
                    )
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val snapshotId = pendingExportId
        val snapshotName = pendingExportName
        pendingExportId = null
        pendingExportName = null
        if (uri != null && snapshotId != null) {
            runInBackground(BackupFailureOperation.EXPORT) {
                val json = repository.exportSnapshot(snapshotId)
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
                    "Could not open the selected location"
                }.bufferedWriter().use { it.write(json) }
                BackupNotice(UiText.Resource(R.string.backup_notice_exported), UiText.Resource(R.string.backup_notice_saved_as_file, snapshotName.orEmpty()))
            }
        } else if (uri != null) {
            // The picker made the file but nothing says what to write: remove it.
            runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
            reportFailure(
                BackupFailureOperation.EXPORT,
                IllegalStateException("The backup to export was lost; export it again."),
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // ISSUE270_BACKUP_SCREEN_LEADS_WITH_ITS_ACTIONS_V1: the file joins
            // Your backups and its restore preview opens; Cancel keeps it there.
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = requireNotNull(context.contentResolver.openInputStream(uri)) {
                            "Could not open the selected backup"
                        }.bufferedReader().use {
                            it.readBackupTextBounded(BACKUP_IMPORT_MAX_CHARACTERS)
                        }
                        repository.importSnapshot(json)
                    }
                }
                result.onSuccess { imported ->
                    refresh()
                    failureOperation = null
                    failureDetails = null
                    backupTab = BackupListTab.YOURS
                    showNotice(BackupNotice(UiText.Resource(R.string.backup_notice_added, imported.displayName()), UiText.Resource(R.string.backup_notice_under_your_backups)))
                    restoreTarget = pendingRestoreOf(imported)
                }.onFailure { error ->
                    reportFailure(BackupFailureOperation.IMPORT, error)
                }
            }
        }
    }

    val manualSnapshots = snapshots.filter { it.kind == SnapshotKind.MANUAL }
    val historySnapshots = snapshots.filter { it.kind == SnapshotKind.CHANGE }
    val safetySnapshots = snapshots.filter { it.kind == SnapshotKind.SAFETY }

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
    Column(modifier.fillMaxSize().nestedScroll(routeTopBarScroll.nestedScrollConnection)) {
        RouteTopBar(
            title = { Text(stringResource(R.string.scr_backup_restore)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Symbols.ArrowBack, contentDescription = stringResource(R.string.scr_back))
                }
            },
            scrollBehavior = routeTopBarScroll,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenMargin, top = 10.dp, end = ScreenMargin, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("protection") {
                val status = loadedStatus ?: return@item
                BackupProtectionCard(
                    status = status,
                    folderConnected = repository.folderConnected(),
                    onChooseFolder = { folderLauncher.launch(null) },
                    onSync = {
                        runInBackground(BackupFailureOperation.SYNC) {
                            if (repository.syncNow()) {
                                BackupNotice(UiText.Resource(R.string.backup_notice_folder_up_to_date), UiText.Resource(R.string.backup_notice_every_backup_copied))
                            } else {
                                throw BackupFailureException(
                                    repository.status().failureDetails ?: FailureDetails(
                                        retryable = true,
                                        localKind = LocalFailureKind.UNKNOWN,
                                    ),
                                )
                            }
                        }
                    },
                    onDisconnect = {
                        runInBackground(BackupFailureOperation.SYNC) {
                            repository.disconnectFolder()
                            BackupNotice(UiText.Resource(R.string.backup_notice_stopped_copying), UiText.Resource(R.string.backup_notice_folder_backups_kept))
                        }
                    },
                )
            }

            // ISSUE270_BACKUP_SCREEN_LEADS_WITH_ITS_ACTIONS_V1 (owner 2026-10-27):
            // back up and restore come first; "Restore from file" opens the restore
            // preview of the file it read.
            item("actions") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        BackupActionRow(
                            icon = Symbols.Save,
                            title = stringResource(R.string.scr_back_up_now),
                            description = stringResource(R.string.scr_keep_your_current_setup_as_a_backup),
                            onClick = { saveDialogVisible = true },
                        )
                        BackupActionRow(
                            icon = Symbols.UploadFile,
                            title = stringResource(R.string.scr_restore_from_file),
                            description = stringResource(R.string.scr_choose_a_backup_file_to_restore),
                            onClick = {
                                importLauncher.launch(
                                    arrayOf("application/json", "text/plain", "application/octet-stream"),
                                )
                            },
                        )
                    }
                }
            }

            // ISSUE270_BACKUP_SCREEN_SAYS_WHAT_IS_BACKED_UP_V1: what every backup
            // holds, then the privacy-sensitive choices, then what never goes in.
            item("contents-title") { BackupSectionTitle(stringResource(R.string.all_what_s_backed_up)) }
            item("contents") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        BackupIncludedRow(stringResource(R.string.all_settings), stringResource(R.string.all_everything_you_choose_in_settings))
                        BackupIncludedRow(
                            stringResource(R.string.all_communities),
                            stringResource(R.string.all_joined_favorites_and_favorite_custom_feeds_for),
                        )
                        BackupIncludedRow(stringResource(R.string.scr_saved_searches_and_new_post_notifications), stringResource(R.string.all_for_each_account))
                    }
                }
            }

            item("Privacy-sensitive backup data") { BackupSubsectionTitle(stringResource(R.string.scr_privacy_sensitive_backup_data)) }
            item("privacy-sensitive-backup") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        BackupSwitchRow(
                            checked = includeHiddenCommunities,
                            onCheckedChange = { enabled ->
                                includeHiddenCommunities = enabled
                                repository.setHiddenCommunitiesBackupEnabled(enabled)
                                recordBackupChoice(enabled, R.string.backup_notice_hidden_communities_backed_up, R.string.backup_notice_hidden_communities_left_out)
                            },
                            title = stringResource(R.string.scr_hidden_communities),
                        )
                        BackupSwitchRow(
                            checked = includeCommunityMutes,
                            onCheckedChange = { enabled ->
                                includeCommunityMutes = enabled
                                repository.setCommunityMutesBackupEnabled(enabled)
                                recordBackupChoice(enabled, R.string.backup_notice_muted_communities_backed_up, R.string.backup_notice_muted_communities_left_out)
                            },
                            title = stringResource(R.string.scr_muted_communities),
                            description = stringResource(R.string.scr_timed_mutes_keep_their_end_time),
                        )
                        BackupSwitchRow(
                            checked = includePostComposerDrafts,
                            onCheckedChange = { enabled ->
                                includePostComposerDrafts = enabled
                                repository.setPostComposerDraftsBackupEnabled(enabled)
                                recordBackupChoice(enabled, R.string.backup_notice_post_drafts_backed_up, R.string.backup_notice_post_drafts_left_out)
                            },
                            title = stringResource(R.string.scr_post_drafts),
                            description = stringResource(R.string.scr_text_and_link_drafts_images_stay_on),
                        )
                        // BACKUP04A4_MODERATOR_SAVED_RESPONSES_SCHEMA_V8_OPT_IN_V1
                        BackupSwitchRow(
                            checked = includeModeratorSavedResponses,
                            onCheckedChange = { enabled ->
                                includeModeratorSavedResponses = enabled
                                repository.setModeratorSavedResponsesBackupEnabled(enabled)
                                recordBackupChoice(enabled, R.string.backup_notice_moderator_responses_backed_up, R.string.backup_notice_moderator_responses_left_out)
                            },
                            title = stringResource(R.string.scr_saved_moderator_responses),
                            description = stringResource(R.string.scr_shared_by_every_account_on_this_device),
                        )
                        // BACKUP04A5_USER_TAGS_SCHEMA_V9_OPT_IN_V1
                        BackupSwitchRow(
                            checked = includeUserTags,
                            onCheckedChange = { enabled ->
                                includeUserTags = enabled
                                repository.setUserTagsBackupEnabled(enabled)
                                recordBackupChoice(enabled, R.string.backup_notice_notes_and_tags_backed_up, R.string.backup_notice_notes_and_tags_left_out)
                            },
                            title = stringResource(R.string.scr_private_notes_tags),
                            description = stringResource(R.string.scr_the_notes_and_tags_you_put_on),
                        )
                    }
                }
            }

            item("never-backed-up") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Symbols.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.scr_never_backed_up_sign_ins_and),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ISSUE270_BACKUPS_ARE_ONE_LIST_WITH_THREE_TABS_V1: each kind keeps its
            // own switch and limit under its tab.
            item("backups-title") { BackupSectionTitle(stringResource(R.string.all_backups)) }
            item("backups-tabs") {
                // Three equal tabs; each label fits a third of a phone.
                SecondaryTabRow(
                    selectedTabIndex = backupTab.ordinal,
                    containerColor = Color.Transparent,
                ) {
                    BackupListTab.entries.forEach { tab ->
                        Tab(
                            selected = tab == backupTab,
                            onClick = { backupTab = tab },
                            text = { Text(stringResource(tab.labelRes), maxLines = 1, softWrap = false) },
                        )
                    }
                }
            }
            when (backupTab) {
                BackupListTab.YOURS -> item("yours-note") {
                    BackupListNote(stringResource(R.string.all_kept_until_you_delete_them))
                }
                BackupListTab.AUTOMATIC -> {
                    item("automatic-history") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            BackupSwitchRow(
                                checked = automaticHistory,
                                onCheckedChange = { enabled ->
                                    automaticHistory = enabled
                                    repository.setAutomaticHistoryEnabled(enabled)
                                    if (enabled) {
                                        val currentPortable = state.capturePortableBackupState()
                                        runInBackground(BackupFailureOperation.SAVE) {
                                            recordPortableChange(currentPortable)
                                            BackupNotice(UiText.Resource(R.string.backup_notice_automatic_on), UiText.Resource(R.string.backup_notice_automatic_on_detail))
                                        }
                                    } else {
                                        showNotice(BackupNotice(UiText.Resource(R.string.backup_notice_automatic_off), UiText.Resource(R.string.backup_notice_automatic_off_detail)))
                                    }
                                },
                                title = stringResource(R.string.scr_back_up_automatically),
                                description = stringResource(R.string.scr_after_you_change_a_setting),
                            )
                        }
                    }
                    item("automatic-retention") {
                        BackupRetentionRow(
                            title = stringResource(R.string.scr_keep_the_newest),
                            retention = automaticHistoryRetention,
                            onRetentionSelected = { retention ->
                                if (retention.isStricterThan(automaticHistoryRetention)) {
                                    retentionChangeTarget = PendingRetentionChange(
                                        kind = RetentionKind.AUTOMATIC_HISTORY,
                                        target = retention,
                                    )
                                } else {
                                    runInBackground(BackupFailureOperation.DELETE) {
                                        repository.setAutomaticHistoryRetention(retention)
                                        BackupNotice(keepingNewest(retention), UiText.Resource(RetentionKind.AUTOMATIC_HISTORY.detailRes))
                                    }
                                }
                            },
                        )
                    }
                }
                BackupListTab.BEFORE_RESTORE -> {
                    item("before-restore-note") {
                        BackupListNote(
                            stringResource(R.string.all_before_every_restore_subshell_backs_up_your),
                        )
                    }
                    item("before-restore-retention") {
                        BackupRetentionRow(
                            title = stringResource(R.string.scr_keep_the_newest),
                            retention = safetySnapshotRetention,
                            onRetentionSelected = { retention ->
                                if (retention.isStricterThan(safetySnapshotRetention)) {
                                    retentionChangeTarget = PendingRetentionChange(
                                        kind = RetentionKind.SAFETY_SNAPSHOTS,
                                        target = retention,
                                    )
                                } else {
                                    runInBackground(BackupFailureOperation.DELETE) {
                                        repository.setSafetySnapshotRetention(retention)
                                        BackupNotice(keepingNewest(retention), UiText.Resource(RetentionKind.SAFETY_SNAPSHOTS.detailRes))
                                    }
                                }
                            },
                        )
                    }
                }
            }
            val shownSnapshots = when (backupTab) {
                BackupListTab.YOURS -> manualSnapshots
                BackupListTab.AUTOMATIC -> historySnapshots
                BackupListTab.BEFORE_RESTORE -> safetySnapshots
            }
            if (loadedSnapshots == null) {
                item("backups-loading") {
                    EmptySnapshots(
                        icon = Symbols.History,
                        title = stringResource(R.string.scr_loading_backups),
                        description = stringResource(R.string.scr_reading_the_backups_on_this_device),
                    )
                }
            } else if (shownSnapshots.isEmpty()) {
                item("backups-empty") {
                    when (backupTab) {
                        BackupListTab.YOURS -> EmptySnapshots(
                            icon = Symbols.Backup,
                            title = stringResource(R.string.scr_no_backups_yet),
                            description = stringResource(R.string.scr_back_up_now_to_keep_a_backup),
                        )
                        BackupListTab.AUTOMATIC -> EmptySnapshots(
                            icon = Symbols.History,
                            title = stringResource(R.string.scr_no_automatic_backups_yet),
                            description = stringResource(R.string.scr_a_backup_is_made_after_you_change),
                        )
                        BackupListTab.BEFORE_RESTORE -> EmptySnapshots(
                            icon = Symbols.History,
                            title = stringResource(R.string.scr_nothing_here_yet),
                            description = stringResource(R.string.scr_a_backup_is_made_here_before_every),
                        )
                    }
                }
            } else {
                items(shownSnapshots, key = SettingsSnapshot::id) { snapshot ->
                    SnapshotCard(
                        snapshot = snapshot,
                        onRestore = { restoreTarget = pendingRestoreOf(snapshot) },
                        onExport = {
                            pendingExportId = snapshot.id
                            pendingExportName = snapshot.displayName().resolve(context)
                            exportLauncher.launch(snapshot.exportFileName())
                        },
                        onRename = if (snapshot.kind == SnapshotKind.MANUAL) {
                            {
                                renameTarget = snapshot
                                renameValue = snapshot.name
                            }
                        } else {
                            null
                        },
                        onDelete = { deleteTarget = snapshot },
                    )
                }
            }

            if (snapshots.isNotEmpty()) {
                item("delete-all") {
                    OutlinedButton(
                        onClick = { deleteAllDialogVisible = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Symbols.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.scr_delete_all_backups))
                    }
                }
            }
        }
    }

    if (saveDialogVisible) {
        NameDialog(
            title = stringResource(R.string.scr_back_up_now),
            value = saveName,
            confirmLabel = stringResource(R.string.all_back_up),
            onValueChange = { saveName = it },
            onDismiss = { saveDialogVisible = false },
            onConfirm = {
                val currentSettings = state.captureSettings()
                val currentJoinedCommunities = state.captureJoinedCommunityIdsByAccount()
                val currentFavoriteCommunities = state.captureFavoriteCommunityNamesByAccount()
                val currentFavoriteCustomFeeds = state.captureFavoriteCustomFeedPathsByAccount()
                val currentHiddenCommunities = state.captureHiddenCommunityNamesByAccount()
                val currentCommunityMutes = state.captureCommunityMutesByAccount()
                val currentPostComposerDrafts = state.capturePostComposerDraftsByAccount()
                val currentModeratorSavedResponses = state.captureModeratorSavedResponses()
                val currentUserTags = state.captureUserTagsByAccount()
                val currentSavedSearches = state.captureSavedSearchesByAccount()
                val currentWatchedCommunities = state.captureWatchedCommunityNamesByAccount()
                val name = saveName
                runInBackground(BackupFailureOperation.SAVE) {
                    val saved = repository.saveCurrent(
                        name,
                        currentSettings,
                        currentJoinedCommunities,
                        currentFavoriteCommunities,
                        currentFavoriteCustomFeeds,
                        currentHiddenCommunities,
                        currentCommunityMutes,
                        currentPostComposerDrafts,
                        currentModeratorSavedResponses,
                        currentUserTags,
                        currentSavedSearches,
                        currentWatchedCommunities,
                    )
                    BackupNotice(UiText.Resource(R.string.backup_notice_backed_up), UiText.Resource(R.string.backup_notice_under_your_backups_as, saved.name))
                }
                saveDialogVisible = false
                saveName = defaultSaveName
            },
        )
    }

    renameTarget?.let { snapshot ->
        NameDialog(
            title = stringResource(R.string.scr_rename_backup),
            value = renameValue,
            confirmLabel = stringResource(R.string.all_rename),
            onValueChange = { renameValue = it },
            onDismiss = { renameTarget = null },
            onConfirm = {
                val name = renameValue
                runInBackground(BackupFailureOperation.RENAME) {
                    val renamed = repository.renameSnapshot(snapshot.id, name)
                    BackupNotice(UiText.Resource(R.string.backup_notice_renamed), UiText.Resource(R.string.backup_notice_now_called, renamed.name))
                }
                renameTarget = null
            },
        )
    }

    restoreTarget?.let { request ->
        val changedSettings = settingsRestoreChangeCount(
            current = request.currentSettings,
            target = request.snapshot.settingsRestoredOver(request.currentSettings),
        )
        val changedLabel = when (changedSettings) {
            0 -> stringResource(R.string.all_settings_will_stay_unchanged)
            else -> pluralStringResource(R.plurals.backup_settings_will_change, changedSettings, changedSettings)
        }
        val communityDiffs = joinedCommunitiesRestoreDiff(
            current = request.currentJoinedCommunityIdsByAccount,
            target = request.snapshot.joinedCommunityIdsByAccount,
        )
        val legacyCommunities =
            request.snapshot.joinedCommunityIdsByAccount == null
        val favoriteCommunityDiffs = portableValuesRestoreDiff(
            current = request.currentFavoriteCommunityNamesByAccount,
            target = request.snapshot.favoriteCommunityNamesByAccount,
        )
        val favoriteCustomFeedDiffs = portableValuesRestoreDiff(
            current = request.currentFavoriteCustomFeedPathsByAccount,
            target = request.snapshot.favoriteCustomFeedPathsByAccount,
        )
        val legacyFavoriteCommunities =
            request.snapshot.favoriteCommunityNamesByAccount == null
        val legacyFavoriteCustomFeeds =
            request.snapshot.favoriteCustomFeedPathsByAccount == null
        val hiddenCommunityDiffs = portableValuesRestoreDiff(
            current = request.currentHiddenCommunityNamesByAccount,
            target = request.snapshot.hiddenCommunityNamesByAccount,
        )
        val legacyHiddenCommunities =
            request.snapshot.hiddenCommunityNamesByAccount == null
        val communityMuteDiffs = portableCommunityMutesRestoreDiff(
            current = request.currentCommunityMutesByAccount,
            target = request.snapshot.communityMutesByAccount,
        )
        val legacyCommunityMutes = request.snapshot.communityMutesByAccount == null
        val postComposerDraftDiffs = portablePostComposerDraftsRestoreDiff(
            current = request.currentPostComposerDraftsByAccount,
            target = request.snapshot.postComposerDraftsByAccount,
        )
        val legacyPostComposerDrafts = request.snapshot.postComposerDraftsByAccount == null
        val moderatorSavedResponseDiffs = portableModeratorSavedResponsesRestoreDiff(
            current = request.currentModeratorSavedResponses,
            target = request.snapshot.moderatorSavedResponses,
        )
        val legacyModeratorSavedResponses = request.snapshot.moderatorSavedResponses == null
        val userTagDiffs = portableValuesRestoreDiff(
            current = portableUserTagValuesByAccount(request.currentUserTagsByAccount),
            target = request.snapshot.userTagsByAccount?.let(::portableUserTagValuesByAccount),
        )
        val legacyUserTags = request.snapshot.userTagsByAccount == null
        // ISSUE206_SAVED_SEARCHES_SCHEMA_V10_V1: always restored with the backup.
        val savedSearchDiffs = portableValuesRestoreDiff(
            current = savedSearchValuesByAccount(request.currentSavedSearchesByAccount),
            target = request.snapshot.savedSearchesByAccount?.let(::savedSearchValuesByAccount),
        )
        val legacySavedSearches = request.snapshot.savedSearchesByAccount == null
        // ISSUE239_WATCHED_COMMUNITIES_SCHEMA_V11_V1: always restored with the backup.
        val watchedCommunityDiffs = portableValuesRestoreDiff(
            current = request.currentWatchedCommunityNamesByAccount,
            target = request.snapshot.watchedCommunityNamesByAccount,
        )
        val legacyWatchedCommunities = request.snapshot.watchedCommunityNamesByAccount == null

        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text(stringResource(R.string.scr_restore, request.snapshot.displayName().asString())) },
            text = {
                Column {
                    Text(changedLabel)
                    Spacer(Modifier.size(8.dp))

                    when {
                        legacyCommunities -> {
                            Text(
                                stringResource(R.string.all_joined_communities_are_not_included_in_this),
                            )
                        }
                        communityDiffs.isEmpty() -> {
                            Text(stringResource(R.string.scr_joined_communities_will_stay_unchanged))
                        }
                        else -> {
                            Text(
                                stringResource(R.string.all_joined_communities),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.size(4.dp))
                            communityDiffs.forEach { diff ->
                                val detail = when {
                                    diff.clearsAllCurrent ->
                                        stringResource(R.string.backup_joined_diff_ends_empty, diff.account, diff.added, diff.removed)
                                    diff.establishesExplicitEmpty ->
                                        stringResource(R.string.backup_joined_diff_set_empty, diff.account)
                                    else ->
                                        stringResource(R.string.backup_joined_diff_to, diff.account, diff.added, diff.removed, diff.targetCount)
                                }
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (diff.clearsAllCurrent) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.size(10.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = stringResource(R.string.scr_favorite_communities),
                        legacy = legacyFavoriteCommunities,
                        diffs = favoriteCommunityDiffs,
                    )
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = stringResource(R.string.scr_favorite_custom_feeds),
                        legacy = legacyFavoriteCustomFeeds,
                        diffs = favoriteCustomFeedDiffs,
                    )
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = stringResource(R.string.scr_hidden_communities_privacy_sensitive),
                        legacy = legacyHiddenCommunities,
                        diffs = hiddenCommunityDiffs,
                        enabled = includeHiddenCommunities,
                        disabledMessage =
                            stringResource(R.string.scr_in_this_backup_but_turned_off),
                    )
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = stringResource(R.string.scr_muted_communities_privacy_sensitive),
                        legacy = legacyCommunityMutes,
                        diffs = communityMuteDiffs,
                        enabled = includeCommunityMutes,
                        disabledMessage =
                            stringResource(R.string.scr_in_this_backup_but_turned_off),
                    )
                    if (!legacyCommunityMutes && includeCommunityMutes) {
                        Text(
                            stringResource(R.string.all_expired_timed_mutes_are_ignored_future_timed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = stringResource(R.string.scr_post_drafts_privacy_sensitive),
                        legacy = legacyPostComposerDrafts,
                        diffs = postComposerDraftDiffs,
                        enabled = includePostComposerDrafts,
                        disabledMessage =
                            stringResource(R.string.scr_in_this_backup_but_turned_off),
                    )
                    if (!legacyPostComposerDrafts && includePostComposerDrafts) {
                        Text(
                            stringResource(R.string.all_only_text_and_link_post_drafts_are),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = stringResource(R.string.scr_saved_moderator_responses),
                        legacy = legacyModeratorSavedResponses,
                        diffs = moderatorSavedResponseDiffs,
                        enabled = includeModeratorSavedResponses,
                        disabledMessage =
                            stringResource(R.string.scr_in_this_backup_but_turned_off),
                    )
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = stringResource(R.string.scr_private_notes_tags_privacy_sensitive),
                        legacy = legacyUserTags,
                        diffs = userTagDiffs,
                        enabled = includeUserTags,
                        disabledMessage =
                            stringResource(R.string.scr_in_this_backup_but_turned_off),
                    )
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = stringResource(R.string.scr_saved_searches),
                        legacy = legacySavedSearches,
                        diffs = savedSearchDiffs,
                        enabled = true,
                        disabledMessage = "",
                    )
                    Spacer(Modifier.size(8.dp))
                    PortableValuesRestorePreview(
                        accountLabel = restoreAccountLabel,
                        title = stringResource(R.string.scr_new_post_notifications),
                        legacy = legacyWatchedCommunities,
                        diffs = watchedCommunityDiffs,
                        enabled = true,
                        disabledMessage = "",
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        stringResource(R.string.scr_subshell_backs_up_your_current_setup),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pending = request
                        restoreTarget = null
                        // LOWUI6_RESTORE_FINISHES_ONCE_STARTED_V1 (OCB-175): once the
                        // safety snapshot is written, leaving the screen must not
                        // skip applying the restore.
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            withContext(NonCancellable) {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        repository.prepareRestore(
                                            pending.snapshot.id,
                                            pending.currentSettings,
                                            pending.currentJoinedCommunityIdsByAccount,
                                            pending.currentFavoriteCommunityNamesByAccount,
                                            pending.currentFavoriteCustomFeedPathsByAccount,
                                            pending.currentHiddenCommunityNamesByAccount,
                                            pending.currentCommunityMutesByAccount,
                                            pending.currentPostComposerDraftsByAccount,
                                            pending.currentModeratorSavedResponses,
                                            pending.currentUserTagsByAccount,
                                            pending.currentSavedSearchesByAccount,
                                            pending.currentWatchedCommunityNamesByAccount,
                                        )
                                    }
                                }
                                result.onSuccess { target ->
                                    runCatching {
                                        // COMMIT01_BACKUP_RESTORE_WRITES_OFF_MAIN_V1 (#243)
                                        val prepared = state.prepareBackupRestore(
                                            settings = target.settingsRestoredOver(
                                                pending.currentSettings,
                                            ),
                                            joinedCommunityIdsByAccount =
                                                target.joinedCommunityIdsByAccount,
                                            favoriteCommunityNamesByAccount =
                                                target.favoriteCommunityNamesByAccount,
                                            favoriteCustomFeedPathsByAccount =
                                                target.favoriteCustomFeedPathsByAccount,
                                            hiddenCommunityNamesByAccount =
                                                target.hiddenCommunityNamesByAccount.takeIf {
                                                    includeHiddenCommunities
                                                },
                                            communityMutesByAccount =
                                                target.communityMutesByAccount.takeIf {
                                                    includeCommunityMutes
                                                },
                                            postComposerDraftsByAccount =
                                                target.postComposerDraftsByAccount.takeIf {
                                                    includePostComposerDrafts
                                                },
                                            moderatorSavedResponses =
                                                target.moderatorSavedResponses.takeIf {
                                                    includeModeratorSavedResponses
                                                },
                                            userTagsByAccount =
                                                target.userTagsByAccount.takeIf {
                                                    includeUserTags
                                                },
                                            savedSearchesByAccount = target.savedSearchesByAccount,
                                            watchedCommunityNamesByAccount = target.watchedCommunityNamesByAccount,
                                        )
                                        withContext(Dispatchers.IO) { state.persistBackupRestore(prepared) }
                                        state.applyBackupRestore(prepared)
                                    }.onSuccess {
                                        refresh()
                                        failureOperation = null
                                        failureDetails = null
                                        showNotice(BackupNotice(UiText.Resource(R.string.backup_notice_restored, target.displayName()), UiText.Resource(R.string.backup_notice_restored_detail)))
                                    }.onFailure { error ->
                                        reportFailure(BackupFailureOperation.RESTORE, error)
                                    }
                                }.onFailure { error ->
                                    reportFailure(BackupFailureOperation.RESTORE, error)
                                }
                            }
                        }
                    },
                ) { Text(stringResource(R.string.scr_restore_2)) }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) { Text(stringResource(R.string.scr_cancel)) }
            },
        )
    }

    retentionChangeTarget?.let { request ->
        val unaffected = when (request.kind) {
            RetentionKind.AUTOMATIC_HISTORY -> stringResource(R.string.all_your_backups_and_the_ones_made_before)
            RetentionKind.SAFETY_SNAPSHOTS -> stringResource(R.string.all_your_backups_and_automatic_backups_stay)
        }
        AlertDialog(
            onDismissRequest = { retentionChangeTarget = null },
            title = { Text(pluralStringResource(R.plurals.backup_keep_only, request.target.limit ?: 0, request.target.limit ?: 0)) },
            text = {
                Text(
                    when (request.kind) {
                        RetentionKind.AUTOMATIC_HISTORY -> stringResource(R.string.backup_retention_older_automatic, unaffected)
                        RetentionKind.SAFETY_SNAPSHOTS -> stringResource(R.string.backup_retention_older_safety, unaffected)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pending = request
                        retentionChangeTarget = null
                        runInBackground(BackupFailureOperation.DELETE) {
                            when (pending.kind) {
                                RetentionKind.AUTOMATIC_HISTORY ->
                                    repository.setAutomaticHistoryRetention(pending.target)
                                RetentionKind.SAFETY_SNAPSHOTS ->
                                    repository.setSafetySnapshotRetention(pending.target)
                            }
                            BackupNotice(keepingNewest(pending.target), UiText.Resource(pending.kind.detailRes))
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.scr_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { retentionChangeTarget = null }) { Text(stringResource(R.string.scr_cancel)) }
            },
        )
    }

    if (deleteAllDialogVisible) {
        val total = snapshots.size
        val automaticHistoryNote = if (automaticHistory) {
            stringResource(R.string.backup_delete_all_automatic_on)
        } else {
            stringResource(R.string.backup_delete_all_automatic_off)
        }
        AlertDialog(
            onDismissRequest = { deleteAllDialogVisible = false },
            title = { Text(stringResource(R.string.scr_delete_all_backups_2, total)) },
            text = {
                Text(
                    stringResource(R.string.error_join_sentences, stringResource(R.string.backup_delete_all_explained), automaticHistoryNote),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteAllDialogVisible = false
                        runInBackground(BackupFailureOperation.DELETE) {
                            val deleted = repository.deleteAllSnapshots()
                            BackupNotice(UiText.Plural(R.plurals.backup_notice_deleted_count, deleted, deleted), UiText.Resource(R.string.backup_notice_deleted_many_detail))
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.scr_delete_all)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteAllDialogVisible = false }) { Text(stringResource(R.string.scr_cancel)) }
            },
        )
    }

    deleteTarget?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.scr_delete, snapshot.displayName().asString())) },
            text = { Text(stringResource(R.string.scr_it_s_removed_from_this_device_and)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        runInBackground(BackupFailureOperation.DELETE) {
                            if (repository.deleteSnapshot(snapshot.id)) {
                                BackupNotice(UiText.Resource(R.string.backup_notice_deleted_one, snapshot.displayName()), UiText.Resource(R.string.backup_notice_deleted_one_detail))
                            } else {
                                BackupNotice(UiText.Resource(R.string.backup_notice_nothing_to_delete), UiText.Resource(R.string.backup_notice_no_longer_exists))
                            }
                        }
                        deleteTarget = null
                    },
                ) { Text(stringResource(R.string.scr_delete_2)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.scr_cancel)) }
            },
        )
    }
}

@Composable
private fun BackupProtectionCard(
    status: BackupStatus,
    folderConnected: Boolean,
    onChooseFolder: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var folderMenuExpanded by remember { mutableStateOf(false) }
    val isProtected = status.protection == BackupProtection.PROTECTED
    val waiting = status.protection == BackupProtection.WAITING
    val icon = when (status.protection) {
        BackupProtection.PROTECTED -> Symbols.CloudDone
        BackupProtection.WAITING -> Symbols.SyncProblem
        BackupProtection.INTERNAL_ONLY -> Symbols.CloudOff
    }
    val title = when (status.protection) {
        BackupProtection.PROTECTED -> stringResource(R.string.all_copied_to_your_backup_folder)
        BackupProtection.WAITING -> stringResource(R.string.all_backup_folder_isn_t_reachable)
        BackupProtection.INTERNAL_ONLY -> stringResource(R.string.all_only_on_this_device)
    }
    val lastSync = status.lastSuccessfulSyncEpochMillis?.let { timestamp ->
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
    }
    val description = when {
        isProtected -> lastSync?.let { stringResource(R.string.all_last_copy, it) } ?: stringResource(R.string.all_backup_folder_connected)
        waiting -> status.failureDetails?.toSafeTechnicalDetail()?.let { detail ->
            stringResource(R.string.all_backups_are_still_kept_on_this_device, detail)
        } ?: stringResource(R.string.all_backups_are_still_kept_on_this_device_2)
        else -> stringResource(R.string.all_backups_are_lost_if_subshell_is_uninstalled)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = when (status.protection) {
            BackupProtection.PROTECTED -> MaterialTheme.colorScheme.primaryContainer
            BackupProtection.WAITING -> MaterialTheme.colorScheme.tertiaryContainer
            BackupProtection.INTERNAL_ONLY -> MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = when (status.protection) {
                        BackupProtection.PROTECTED -> MaterialTheme.colorScheme.onPrimaryContainer
                        BackupProtection.WAITING -> MaterialTheme.colorScheme.onTertiaryContainer
                        BackupProtection.INTERNAL_ONLY -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (folderConnected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Symbols.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                    ) {
                        Text(
                            stringResource(R.string.all_backup_folder),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            status.folderLocation ?: stringResource(R.string.all_selected_folder),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 26.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onSync) {
                        Icon(Symbols.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.scr_copy_now))
                    }
                    TextButton(onClick = onChooseFolder) {
                        Icon(Symbols.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.scr_change))
                    }
                    Box {
                        IconButton(onClick = { folderMenuExpanded = true }) {
                            Icon(Symbols.MoreVert, contentDescription = stringResource(R.string.scr_more_backup_folder_options))
                        }
                        DropdownMenu(
                            expanded = folderMenuExpanded,
                            onDismissRequest = { folderMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.scr_stop_copying_to_this_folder)) },
                                onClick = {
                                    folderMenuExpanded = false
                                    onDisconnect()
                                },
                                leadingIcon = { Icon(Symbols.CloudOff, contentDescription = null) },
                            )
                        }
                    }
                }
            } else {
                OutlinedButton(onClick = onChooseFolder, modifier = Modifier.padding(start = 36.dp)) {
                    Icon(Symbols.Folder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scr_choose_backup_folder))
                }
                Text(
                    stringResource(R.string.scr_for_example_create_documents_subshell_backups),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp),
                )
            }
        }
    }
}

// M3225_RETENTION_IS_AN_EXPOSED_DROPDOWN_V1 (#225, settings-08): a select field
// is M3's exposed dropdown (a non-editable field with an anchored menu), not a button
// with a hand-placed menu.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupRetentionRow(
    title: String,
    retention: BackupRetention,
    onRetentionSelected: (BackupRetention) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = stringResource(retention.labelRes),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            BackupRetention.entries.forEach { option ->
                DropdownMenuItem(
                    // ISSUE219_RETENTION_MENU_SAYS_WHICH_IS_SELECTED_V1: the check icon
                    // has no description; the item itself carries the selection.
                    modifier = Modifier.semantics { selected = option == retention },
                    text = { Text(stringResource(option.labelRes)) },
                    trailingIcon = {
                        if (option == retention) {
                            Icon(
                                Symbols.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        if (option != retention) onRetentionSelected(option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun BackupSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
}

@Composable
private fun BackupSubsectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

/** ISSUE270_BACKUP_SCREEN_LEADS_WITH_ITS_ACTIONS_V1: an action is a list row, like the rest. */
@Composable
private fun BackupActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    ListItem(
        content = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Symbols.ChevronRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun BackupListNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** ISSUE270_BACKUP_SCREEN_SAYS_WHAT_IS_BACKED_UP_V1: always in every backup. */
@Composable
private fun BackupIncludedRow(title: String, description: String) {
    ListItem(
        content = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = {
            Icon(Symbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private data class BackupNotice(val title: UiText, val detail: UiText)

private enum class BackupListTab(@androidx.annotation.StringRes val labelRes: Int) {
    YOURS( io.github.brealorg.subshell.R.string.backup_list_tab_yours),
    AUTOMATIC( io.github.brealorg.subshell.R.string.backup_list_tab_automatic),
    BEFORE_RESTORE( io.github.brealorg.subshell.R.string.backup_list_tab_before_restore),
}

// Automatic backups were named "Automatic settings history" when they were made.
private fun SettingsSnapshot.displayName(): UiText =
    if (kind == SnapshotKind.CHANGE) UiText.Resource(R.string.scr_automatic_backup) else UiText.Plain(name)

@Composable
private fun EmptySnapshots(icon: ImageVector, title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(title, modifier = Modifier.padding(top = 8.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SnapshotCard(
    snapshot: SettingsSnapshot,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val formattedDate = remember(snapshot.createdAtEpochMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(snapshot.createdAtEpochMillis))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    when (snapshot.kind) {
                        SnapshotKind.MANUAL -> Symbols.Save
                        SnapshotKind.CHANGE -> Symbols.History
                        SnapshotKind.SAFETY -> Symbols.History
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        snapshot.displayName().asString(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.backup_date_version, formattedDate, snapshot.appVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Symbols.MoreVert, contentDescription = stringResource(R.string.scr_more_actions))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (onRename != null) {
                            SnapshotMenuItem(Symbols.Edit, stringResource(R.string.all_rename)) {
                                menuExpanded = false
                                onRename()
                            }
                        }
                        SnapshotMenuItem(Symbols.Delete, stringResource(R.string.all_delete)) {
                            menuExpanded = false
                            onDelete()
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Symbols.History, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scr_restore_2))
                }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(Symbols.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scr_export))
                }
            }
        }
    }
}

@Composable
private fun SnapshotMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun NameDialog(
    title: String,
    value: String,
    confirmLabel: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                label = { Text(stringResource(R.string.scr_name)) },
                supportingText = { Text(stringResource(R.string.scr_80, value.length)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = value.isNotBlank() && value.length <= 80,
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.scr_cancel)) } },
    )
}

private enum class RetentionKind(@androidx.annotation.StringRes val detailRes: Int) {
    AUTOMATIC_HISTORY(R.string.backup_notice_of_automatic),
    SAFETY_SNAPSHOTS(R.string.backup_notice_of_safety),
}

/** TRANSLATE14_BACKUP_NOTICES_ARE_WHOLE_SENTENCES_V1 */
private fun keepingNewest(retention: BackupRetention): UiText =
    retention.limit?.let { limit -> UiText.Plural(R.plurals.backup_notice_keeping_newest, limit, limit) }
        ?: UiText.Resource(R.string.backup_notice_keeping_every)

private data class PendingRetentionChange(
    val kind: RetentionKind,
    val target: BackupRetention,
)

private data class PendingRestore(
    val snapshot: SettingsSnapshot,
    val currentSettings: SubshellSettings,
    val currentJoinedCommunityIdsByAccount: Map<String, Set<String>>,
    val currentFavoriteCommunityNamesByAccount: Map<String, Set<String>>,
    val currentFavoriteCustomFeedPathsByAccount: Map<String, Set<String>>,
    val currentHiddenCommunityNamesByAccount: Map<String, Set<String>>,
    val currentCommunityMutesByAccount: Map<String, List<PortableCommunityMute>>,
    val currentPostComposerDraftsByAccount: Map<String, List<PortablePostComposerDraft>>,
    val currentModeratorSavedResponses: List<PortableModeratorSavedResponse>,
    val currentUserTagsByAccount: Map<String, Map<String, PortableUserTag>>,
    val currentSavedSearchesByAccount: Map<String, List<SavedSearch>>,
    val currentWatchedCommunityNamesByAccount: Map<String, Set<String>>,
)

// BACKUP04A4: device-wide module, shown as one row (named by restoreAccountLabel).
internal const val RESTORE_THIS_DEVICE_KEY = "This device"

internal fun portableModeratorSavedResponsesRestoreDiff(
    current: List<PortableModeratorSavedResponse>,
    target: List<PortableModeratorSavedResponse>?,
): List<PortableValuesRestoreAccountDiff> = portableValuesRestoreDiff(
    current = mapOf(
        RESTORE_THIS_DEVICE_KEY to current.mapTo(linkedSetOf()) { response ->
            "${response.id}|${response.updatedAtEpochMs}|${response.title}|${response.body}"
        },
    ),
    target = target?.let { responses ->
        mapOf(
            RESTORE_THIS_DEVICE_KEY to responses.mapTo(linkedSetOf()) { response ->
                "${response.id}|${response.updatedAtEpochMs}|${response.title}|${response.body}"
            },
        )
    },
)

internal fun portablePostComposerDraftsRestoreDiff(
    current: Map<String, List<PortablePostComposerDraft>>,
    target: Map<String, List<PortablePostComposerDraft>>?,
): List<PortableValuesRestoreAccountDiff> = portableValuesRestoreDiff(
    current = current.mapValues { (_, drafts) ->
        drafts.mapTo(linkedSetOf()) { draft ->
            "${draft.id}|${draft.updatedAtEpochMs}|${draft.type}|${draft.title}|${draft.body}|${draft.linkUrl}"
        }
    },
    target = target?.mapValues { (_, drafts) ->
        drafts.mapTo(linkedSetOf()) { draft ->
            "${draft.id}|${draft.updatedAtEpochMs}|${draft.type}|${draft.title}|${draft.body}|${draft.linkUrl}"
        }
    },
)

internal fun portableCommunityMutesRestoreDiff(
    current: Map<String, List<PortableCommunityMute>>,
    target: Map<String, List<PortableCommunityMute>>?,
): List<PortableValuesRestoreAccountDiff> = portableValuesRestoreDiff(
    current = current.mapValues { (_, mutes) ->
        mutes.mapTo(linkedSetOf()) { mute ->
            "${mute.expiresAtEpochMillis ?: "indefinite"}|${mute.communityName.lowercase()}"
        }
    },
    target = target?.mapValues { (_, mutes) ->
        mutes.mapTo(linkedSetOf()) { mute ->
            "${mute.expiresAtEpochMillis ?: "indefinite"}|${mute.communityName.lowercase()}"
        }
    },
)

// BACKUP04A5: one value per tagged username, so an edited tag counts as -1/+1.
internal fun portableUserTagValuesByAccount(
    value: Map<String, Map<String, PortableUserTag>>,
): Map<String, Set<String>> = value.mapValues { (_, tags) ->
    tags.entries.mapTo(linkedSetOf()) { (username, tag) ->
        listOf(username, tag.label, tag.color, tag.note).joinToString("\u0000")
    }
}

// ISSUE206: one value per saved search.
internal fun savedSearchValuesByAccount(
    value: Map<String, List<SavedSearch>>,
): Map<String, Set<String>> = value.mapValues { (_, searches) ->
    searches.mapTo(linkedSetOf()) { search -> search.identityKey() }
}

internal data class PortableValuesRestoreAccountDiff(
    val account: String,
    val added: Int,
    val removed: Int,
    val currentCount: Int?,
    val targetCount: Int,
) {
    val clearsAllCurrent: Boolean
        get() = currentCount != null && currentCount > 0 && targetCount == 0

    val establishesExplicitEmpty: Boolean
        get() = currentCount == null && targetCount == 0
}

internal fun portableValuesRestoreDiff(
    current: Map<String, Set<String>>,
    target: Map<String, Set<String>>?,
): List<PortableValuesRestoreAccountDiff> {
    val restored = target ?: return emptyList()
    val effectiveTarget = if (restored.isEmpty()) {
        current.keys.associateWith { emptySet<String>() }
    } else {
        restored
    }
    return effectiveTarget.entries
        .sortedBy { (account, _) -> account }
        .mapNotNull { (account, targetValues) ->
            val hasCurrentRow = current.containsKey(account)
            val currentValues = current[account].orEmpty()
            if (hasCurrentRow && currentValues == targetValues) {
                null
            } else {
                PortableValuesRestoreAccountDiff(
                    account = account,
                    added = (targetValues - currentValues).size,
                    removed = (currentValues - targetValues).size,
                    currentCount = currentValues.size.takeIf { hasCurrentRow },
                    targetCount = targetValues.size,
                )
            }
        }
}

@Composable
private fun PortableValuesRestorePreview(
    title: String,
    legacy: Boolean,
    diffs: List<PortableValuesRestoreAccountDiff>,
    enabled: Boolean = true,
    disabledMessage: String? = null,
    accountLabel: (String) -> String = { account -> account },
) {
    Text(title, fontWeight = FontWeight.SemiBold)
    when {
        legacy -> Text(
            stringResource(R.string.all_not_included_in_this_older_backup_current),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        !enabled -> Text(
            disabledMessage
                ?: stringResource(R.string.scr_this_privacy_sensitive_module_is_disabled),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        diffs.isEmpty() -> Text(
            stringResource(R.string.all_will_stay_unchanged),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> {
            diffs.take(6).forEach { diff ->
                val who = if (diff.account == RESTORE_THIS_DEVICE_KEY) {
                    stringResource(R.string.backup_this_device)
                } else {
                    accountLabel(diff.account)
                }
                val summary = when {
                    diff.clearsAllCurrent ->
                        stringResource(R.string.backup_diff_clear_current, who, diff.currentCount ?: 0)
                    diff.establishesExplicitEmpty ->
                        stringResource(R.string.backup_diff_explicit_empty, who)
                    else ->
                        stringResource(R.string.backup_diff_added_removed, who, diff.added, diff.removed)
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (diff.clearsAllCurrent) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (diffs.size > 6) {
                Text(
                    pluralStringResource(R.plurals.backup_more_accounts, diffs.size - 6, diffs.size - 6),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun settingsRestoreChangeCount(
    current: SubshellSettings,
    target: SubshellSettings,
): Int {
    val currentValues = current.toStorageMap()
    val targetValues = target.toStorageMap()
    return (currentValues.keys + targetValues.keys)
        .count { key -> currentValues[key] != targetValues[key] }
}

// BACKUP02A3_RICHER_RESTORE_PREVIEW_V1
internal data class JoinedCommunitiesRestoreAccountDiff(
    val account: String,
    val added: Int,
    val removed: Int,
    val currentCount: Int?,
    val targetCount: Int,
) {
    val clearsAllCurrent: Boolean
        get() = currentCount != null && currentCount > 0 && targetCount == 0

    val establishesExplicitEmpty: Boolean
        get() = currentCount == null && targetCount == 0
}

internal fun joinedCommunitiesRestoreDiff(
    current: Map<String, Set<String>>,
    target: Map<String, Set<String>>?,
): List<JoinedCommunitiesRestoreAccountDiff> {
    val restored = target ?: return emptyList()
    return restored.entries
        .sortedBy { (account, _) -> account }
        .mapNotNull { (account, targetIds) ->
            val hasCurrentRow = current.containsKey(account)
            val currentIds = current[account].orEmpty()
            if (hasCurrentRow && currentIds == targetIds) {
                null
            } else {
                JoinedCommunitiesRestoreAccountDiff(
                    account = account,
                    added = (targetIds - currentIds).size,
                    removed = (currentIds - targetIds).size,
                    currentCount = currentIds.size.takeIf { hasCurrentRow },
                    targetCount = targetIds.size,
                )
            }
        }
}

internal fun joinedCommunitiesRestoreChangeCount(
    current: Map<String, Set<String>>,
    target: Map<String, Set<String>>?,
): Int = joinedCommunitiesRestoreDiff(current, target).size

private fun SettingsSnapshot.exportFileName(): String {
    val safeName = name
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(48)
        .ifBlank { "subshell-settings" }
    return "$safeName.subshell-settings.json"
}

/** "u/name" for an account key (stable id or username), "another account" when unknown here. */
internal fun restorePreviewAccountLabel(key: String, sessions: List<AccountSessionSummary>, unknownAccount: String): String {
    sessions.firstOrNull { session -> session.stableId == key }?.let { session -> return "u/${session.username}" }
    sessions.firstOrNull { session -> session.username.equals(key, ignoreCase = true) }
        ?.let { session -> return "u/${session.username}" }
    return if (':' in key) unknownAccount else "u/$key"
}

// ISSUE219_BACKUP_SWITCH_ROWS_ARE_LABELLED_V1: the whole row is the switch
// (as Settings' ToggleRow, LOWUI7), so TalkBack reads the row's text with the
// switch state and a tap anywhere on the row toggles it.
// M3225_SETTINGS_ROWS_ARE_LIST_ITEMS_V1: an M3 ListItem inside its card.
@Composable
private fun BackupSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    overline: String? = null,
    icon: ImageVector? = null,
) {
    ListItem(
        overlineContent = overline?.let { text -> { Text(text) } },
        content = { Text(title) },
        supportingContent = description?.let { text -> { Text(text) } },
        leadingContent = icon?.let { vector -> { Icon(vector, contentDescription = null) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}
