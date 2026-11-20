package io.github.brealorg.subshell.update

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import android.content.ActivityNotFoundException
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brealorg.subshell.BuildConfig
import io.github.brealorg.subshell.media.findLifecycleOwner
import io.github.brealorg.subshell.ui.ScreenMargin
import io.github.brealorg.subshell.ui.components.RouteTopBar
import io.github.brealorg.subshell.ui.components.rememberRouteTopBarScrollBehavior
import io.github.brealorg.subshell.ui.symbols.Symbols
import java.util.Locale
import kotlinx.coroutines.launch

/*
 * ISSUE286_SETTINGS_UPDATES_V1 (#286, owner: in Settings): one place that says
 * which version is installed, which is the newest, when subshell last looked,
 * and lets the reader check now and update. Debug builds never check
 * (UpdateRuntimePolicy), and the screen says so.
 */

internal data class UpdatesScreenState(
    val installedVersionName: String,
    val runtimeEnabled: Boolean,
    val latest: UpdateManifestRelease?,
    val available: UpdateManifestRelease?,
    val lastCheckedEpochMillis: Long,
    val nowEpochMillis: Long,
    val checking: Boolean = false,
    val checkMessage: String? = null,
    val install: UpdateInstallState = UpdateInstallState.Idle,
)

// TRANSLATE14_UPDATE_CHECK_MESSAGES_ARE_SHOWN_TRANSLATED_V1: the state keeps
// these English keys (tests read them); the screen shows the resource.
internal const val UPDATES_CHECK_FAILED = "Couldn't reach the update information. Try again later."
internal const val UPDATES_UP_TO_DATE = "You have the latest version."
internal const val UPDATE_WAITING_FOR_YOU = "Android is waiting for you to confirm the update."
internal const val UPDATES_OFF_IN_THIS_BUILD = "Update checks are off in this build."

@Composable
private fun updatesMessageShown(message: String): String = when (message) {
    UPDATES_CHECK_FAILED -> stringResource(R.string.upd_couldn_t_reach_the_update_information)
    UPDATES_UP_TO_DATE -> stringResource(R.string.upd_you_have_the_latest_version)
    else -> message
}

@Composable
internal fun UpdatesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    runtimeEnabled: Boolean = UpdateRuntimePolicy.enabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG),
) {
    val activityContext = LocalContext.current
    val appContext = activityContext.applicationContext
    val store = remember(appContext) { UpdateStateStore(appContext) }
    val scope = rememberCoroutineScope()
    val install by UpdateInstallCoordinator.state.collectAsStateWithLifecycle()
    var revision by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    var checkMessage by remember { mutableStateOf<String?>(null) }
    var permissionNeededFor by remember { mutableStateOf<UpdateManifestRelease?>(null) }
    var resumeAfterPermission by remember { mutableStateOf<UpdateManifestRelease?>(null) }

    fun startUpdate(release: UpdateManifestRelease) {
        if (updateInstallAllowed(appContext)) {
            UpdateInstallCoordinator.start(appContext, release)
        } else {
            permissionNeededFor = release
        }
    }

    // ISSUE291: Update in the update dialog starts the update as this page opens.
    LaunchedEffect(Unit) {
        val requested = UpdateStartRequest.take()
        if (requested > 0) {
            store.availableRelease(BuildConfig.VERSION_CODE)
                ?.takeIf { it.versionCode == requested }
                ?.let(::startUpdate)
        }
    }

    // Back from Android's "Install unknown apps" page: go on if it was allowed.
    val lifecycleOwner = remember(activityContext) { activityContext.findLifecycleOwner() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                revision++
                // ISSUE45_UPDATE_CONFIRMATION_IS_NEVER_LOST_V1: Android's question may not have opened while away.
                UpdateInstallCoordinator.showConfirmation(activityContext, onlyIfNotShown = true)
                resumeAfterPermission?.let { release ->
                    resumeAfterPermission = null
                    if (updateInstallAllowed(appContext)) UpdateInstallCoordinator.start(appContext, release)
                }
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val state = remember(revision, checking, checkMessage, install) {
        UpdatesScreenState(
            installedVersionName = BuildConfig.VERSION_NAME,
            runtimeEnabled = runtimeEnabled,
            latest = store.knownRelease(),
            available = store.availableRelease(BuildConfig.VERSION_CODE),
            lastCheckedEpochMillis = store.lastCheckEpochMillis(),
            nowEpochMillis = System.currentTimeMillis(),
            checking = checking,
            checkMessage = checkMessage,
            install = install,
        )
    }

    UpdatesContent(
        state = state,
        onBack = onBack,
        modifier = modifier,
        onCheckNow = {
            checking = true
            checkMessage = null
            scope.launch {
                val checked = UpdateCheckCoordinator.check(appContext, force = true)
                val available = store.availableRelease(BuildConfig.VERSION_CODE)
                // This screen shows the release itself, so the dialog does not open over it.
                available?.let { release ->
                    UpdatePresentationCoordinator.withdrawAvailable()
                    store.markUpdatePresented(release.versionCode)
                }
                checkMessage = when {
                    !checked -> UPDATES_CHECK_FAILED
                    available == null -> UPDATES_UP_TO_DATE
                    else -> null
                }
                checking = false
                revision++
            }
        },
        onUpdate = ::startUpdate,
        onContinueInstall = { UpdateInstallCoordinator.showConfirmation(activityContext) },
        onViewRelease = { release -> openUpdateRelease(activityContext, release.releaseUrl) },
    )

    permissionNeededFor?.let { release ->
        AlertDialog(
            onDismissRequest = { permissionNeededFor = null },
            title = { Text(stringResource(R.string.sw_allow_subshell_to_install_its_update)) },
            text = {
                Text(
                    stringResource(R.string.all2_android_asks_once_whether_subshell_may_install),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        permissionNeededFor = null
                        try {
                            activityContext.startActivity(updateInstallPermissionIntent(activityContext))
                            resumeAfterPermission = release
                        } catch (_: ActivityNotFoundException) {
                            // No settings page to open; View release still works.
                        }
                    },
                ) { Text(stringResource(R.string.sw_open_android_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { permissionNeededFor = null }) { Text(stringResource(R.string.sw_not_now)) }
            },
        )
    }
}

@Composable
internal fun UpdatesContent(
    state: UpdatesScreenState,
    onBack: () -> Unit,
    onCheckNow: () -> Unit,
    onUpdate: (UpdateManifestRelease) -> Unit,
    onViewRelease: (UpdateManifestRelease) -> Unit,
    modifier: Modifier = Modifier,
    onContinueInstall: () -> Unit = {},
) {
    val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
    Column(modifier.fillMaxSize().nestedScroll(routeTopBarScroll.nestedScrollConnection)) {
        RouteTopBar(
            title = { Text(stringResource(R.string.sw_updates)) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Symbols.ArrowBack, contentDescription = stringResource(R.string.sw_back)) }
            },
            scrollBehavior = routeTopBarScroll,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenMargin, top = 20.dp, end = ScreenMargin, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item("versions") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VersionLine(stringResource(R.string.all2_installed), state.installedVersionName)
                    if (state.runtimeEnabled) {
                        VersionLine(stringResource(R.string.all2_latest), state.latest?.versionName ?: stringResource(R.string.all2_not_checked_yet))
                        if (state.lastCheckedEpochMillis > 0L) {
                            VersionLine(
                                stringResource(R.string.all2_last_checked),
                                DateUtils.getRelativeTimeSpanString(
                                    state.lastCheckedEpochMillis,
                                    state.nowEpochMillis,
                                    DateUtils.MINUTE_IN_MILLIS,
                                ).toString(),
                            )
                        }
                    }
                }
            }

            if (!state.runtimeEnabled) {
                item("off") {
                    Text(
                        stringResource(R.string.upd_update_checks_are_off_in_this),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@LazyColumn
            }

            state.available?.let { release ->
                item("available") {
                    AvailableRelease(
                        release = release,
                        install = state.install.takeIf { it.versionCode() == release.versionCode }
                            ?: UpdateInstallState.Idle,
                        onUpdate = { onUpdate(release) },
                        onContinueInstall = onContinueInstall,
                        onViewRelease = { onViewRelease(release) },
                    )
                }
            }

            item("check") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCheckNow, enabled = !state.checking) {
                        Text(if (state.checking) stringResource(R.string.all2_checking) else stringResource(R.string.all2_check_now))
                    }
                    state.checkMessage?.let { message ->
                        Text(
                            updatesMessageShown(message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AvailableRelease(
    release: UpdateManifestRelease,
    install: UpdateInstallState,
    onUpdate: () -> Unit,
    onContinueInstall: () -> Unit,
    onViewRelease: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.upd_subshell_is_available, release.versionName), style = MaterialTheme.typography.titleMedium)
            Text(release.headline, style = MaterialTheme.typography.bodyMedium)
            release.highlights.forEach { highlight ->
                Text("• $highlight", style = MaterialTheme.typography.bodyMedium)
            }
            SelectionContainer {
                Text(
                    updateApkHashLine(release),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (install) {
                is UpdateInstallState.Downloading -> {
                    if (install.total > 0L) {
                        LinearProgressIndicator(
                            progress = { (install.downloaded.toFloat() / install.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(updateDownloadProgressText(install), style = MaterialTheme.typography.bodyMedium)
                }
                is UpdateInstallState.Installing ->
                    Text(
                        if (install.waitingForYou) stringResource(R.string.upd_android_is_waiting_for_you_to) else stringResource(R.string.all2_android_is_installing_the_update),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                is UpdateInstallState.Failed ->
                    Text(
                        install.message.asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                UpdateInstallState.Idle -> Unit
            }
            val waiting = install is UpdateInstallState.Installing && install.waitingForYou
            val busy = install is UpdateInstallState.Downloading ||
                (install is UpdateInstallState.Installing && !install.waitingForYou)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (waiting) {
                    Button(onClick = onContinueInstall) { Text(stringResource(R.string.sw_continue)) }
                } else {
                    Button(onClick = onUpdate, enabled = !busy) {
                        Text(if (install is UpdateInstallState.Failed) stringResource(R.string.all2_try_again) else stringResource(R.string.all2_update))
                    }
                }
                TextButton(onClick = onViewRelease) { Text(stringResource(R.string.sw_view_release)) }
            }
        }
    }
}

private fun UpdateInstallState.versionCode(): Int? = when (this) {
    is UpdateInstallState.Downloading -> versionCode
    is UpdateInstallState.Installing -> versionCode
    is UpdateInstallState.Failed -> versionCode
    UpdateInstallState.Idle -> null
}

@Composable
internal fun updateDownloadProgressText(state: UpdateInstallState.Downloading): String {
    fun mb(bytes: Long) = String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0))
    return if (state.total > 0L) {
        stringResource(R.string.update_downloading_of, mb(state.downloaded), mb(state.total))
    } else {
        stringResource(R.string.update_downloading)
    }
}
