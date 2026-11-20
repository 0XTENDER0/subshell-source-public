package io.github.brealorg.subshell.ui.screens

import androidx.compose.ui.res.pluralStringResource
import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brealorg.subshell.data.account.CommunityMembershipAction
import io.github.brealorg.subshell.data.account.CommunityMembershipPhase
import io.github.brealorg.subshell.data.account.CommunityMembershipUiState
import io.github.brealorg.subshell.data.account.RedditSubscriptionsUiState
import io.github.brealorg.subshell.data.reading.READING_STATS_SUGGESTION_MIN_DAYS
import io.github.brealorg.subshell.data.reading.ReadingStats
import io.github.brealorg.subshell.data.reading.ReadingSummary
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.app.onDisk
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ISSUE268_SUGGESTIONS_HIDE_OR_LEAVE_V1: the signed-in account's Reddit
 * subscriptions and its Leave action, for the communities it never opens.
 * [subscribedNames] is null while they are not known (signed out, loading).
 */
internal data class ReadingStatsCommunities(
    val subscribedNames: List<String>? = null,
    val leavingName: String? = null,
    val leaveFailedName: String? = null,
    val onLeave: (String) -> Unit = {},
)

internal val LocalReadingStatsCommunities = staticCompositionLocalOf { ReadingStatsCommunities() }

/** Subscriptions only when Reddit's list for this signed-in account is loaded. */
internal fun readingStatsCommunities(
    accountStableId: String,
    authenticated: Boolean,
    subscriptions: RedditSubscriptionsUiState,
    membership: CommunityMembershipUiState,
    onChangeMembership: (String, String, CommunityMembershipAction) -> Unit,
): ReadingStatsCommunities {
    if (!authenticated || subscriptions.accountStableId != accountStableId || !subscriptions.loaded) {
        return ReadingStatsCommunities()
    }
    val leave = membership.takeIf {
        it.accountStableId == accountStableId && it.action == CommunityMembershipAction.LEAVE
    }
    return ReadingStatsCommunities(
        subscribedNames = subscriptions.communities.map { it.name },
        leavingName = leave?.takeIf {
            it.phase == CommunityMembershipPhase.SENDING ||
                it.phase == CommunityMembershipPhase.AUTHORIZING ||
                it.phase == CommunityMembershipPhase.AWAITING_CALLBACK
        }?.communityName,
        leaveFailedName = leave?.takeIf {
            it.phase == CommunityMembershipPhase.FAILED ||
                it.phase == CommunityMembershipPhase.AUTHORIZATION_REQUIRED
        }?.communityName,
        onLeave = { name -> onChangeMembership(accountStableId, name, CommunityMembershipAction.LEAVE) },
    )
}

internal const val READING_STATS_COMMUNITY_ROWS = 25

/** "1 h 5 min", "12 min", "under a minute". */
internal fun readingDurationLabel(seconds: Long): UiText {
    val minutes = seconds / 60L
    return when {
        minutes < 1L -> UiText.Resource(R.string.reading_under_a_minute)
        minutes < 60L -> UiText.Resource(R.string.reading_minutes, minutes)
        minutes % 60L == 0L -> UiText.Resource(R.string.reading_hours, minutes / 60L)
        else -> UiText.Resource(R.string.reading_hours_minutes, minutes / 60L, minutes % 60L)
    }
}

internal fun readingCountsLabel(opens: Int, postOpens: Int): UiText {
    val parts = listOfNotNull(
        opens.takeIf { it > 0 }?.let { UiText.Plural(R.plurals.reading_visits, it, it) },
        postOpens.takeIf { it > 0 }?.let { UiText.Plural(R.plurals.reading_posts, it, it) },
    )
    return when (parts.size) {
        0 -> UiText.Plain("")
        1 -> parts[0]
        else -> UiText.Resource(R.string.join_middot, parts[0], parts[1])
    }
}

/**
 * The subscribed communities "Your reading" suggests hiding or leaving:
 * none opened (page or post) in the summary's days, not already hidden.
 * Empty until recording has run [READING_STATS_SUGGESTION_MIN_DAYS] days.
 */
internal fun readingSuggestions(
    summary: ReadingSummary,
    subscribedNames: List<String>,
    isHidden: (String) -> Boolean,
): List<String> =
    if (!summary.suggestionsReady) {
        emptyList()
    } else {
        subscribedNames
            .filterNot { name -> summary.hasRead(name) || isHidden(name) }
            .distinctBy(String::lowercase)
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

/**
 * ISSUE268_YOUR_READING_PAGE_V1 (#268): You › Library › Reading. Where the
 * time goes, which communities are read and which subscriptions are never
 * opened, from counts kept on this device only.
 */
@Composable
internal fun ReadingStatsPanel(state: SubshellState) {
    val store = ReadingStats.store(LocalContext.current)
    LaunchedEffect(store) { store.onDisk { load() } }
    val enabled by store.enabled.collectAsStateWithLifecycle()
    val revision by store.revision.collectAsStateWithLifecycle()
    val account = state.activeAccountId
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (enabled != true) {
            ReadingStatsOff(onTurnOn = { store.onDisk { setEnabled(true) } }, loading = enabled == null)
            return@Column
        }
        val summary by produceState<ReadingSummary?>(null, account, revision) {
            value = withContext(Dispatchers.IO) { store.summary(account) }
        }
        val current = summary ?: return@Column
        ReadingStatsTotals(current)
        ReadingStatsCommunityList(current, onOpen = state::openSubreddit)
        ReadingStatsSuggestionList(current, state)
        Text(
            text = stringResource(R.string.all2_counted_only_on_this_device_opens_and),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { confirmDelete = "clear" }) { Text(stringResource(R.string.scr_clear)) }
            OutlinedButton(onClick = { confirmDelete = "off" }) { Text(stringResource(R.string.scr_turn_off)) }
        }
    }

    confirmDelete?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (action == "off") stringResource(R.string.scr_turn_off_your_reading) else stringResource(R.string.scr_clear_your_reading)) },
            text = {
                Text(
                    if (action == "off") {
                        stringResource(R.string.all_what_was_counted_is_deleted_and_nothing)
                    } else {
                        stringResource(R.string.all_what_was_counted_is_deleted_counting_starts)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = null
                        if (action == "off") store.onDisk { setEnabled(false) } else store.onDisk { clear() }
                    },
                ) { Text(if (action == "off") stringResource(R.string.scr_turn_off) else stringResource(R.string.all_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.scr_cancel)) } },
        )
    }
}

@Composable
private fun ReadingStatsOff(onTurnOn: () -> Unit, loading: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.scr_your_reading), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.all2_see_where_your_time_goes_and_which),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onTurnOn, enabled = !loading) { Text(stringResource(R.string.scr_turn_on)) }
        }
    }
}

@Composable
private fun ReadingStatsTotals(summary: ReadingSummary) {
    val since = remember(summary.sinceEpochDay) {
        LocalDate.ofEpochDay(summary.sinceEpochDay)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    val period = if (summary.days >= io.github.brealorg.subshell.data.reading.READING_STATS_WINDOW_DAYS) {
        pluralStringResource(R.plurals.reading_last_days, summary.days, summary.days)
    } else {
        stringResource(R.string.all_since, since)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(period, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(readingDurationLabel(summary.totalSeconds).asString(), style = MaterialTheme.typography.headlineMedium)
        if (summary.homeSeconds > 0L) {
            Text(
                stringResource(R.string.reading_home_feed, readingDurationLabel(summary.homeSeconds).asString()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadingStatsCommunityList(summary: ReadingSummary, onOpen: (String) -> Unit) {
    Text(stringResource(R.string.scr_communities_you_read), style = MaterialTheme.typography.titleSmall)
    if (summary.communities.isEmpty()) {
        Text(
            stringResource(R.string.all_nothing_counted_yet_communities_and_posts_you),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    summary.communities.take(READING_STATS_COMMUNITY_ROWS).forEach { community ->
        ListItem(
            content = { Text(stringResource(R.string.scr_r, community.communityName)) },
            modifier = Modifier.clickable { onOpen(community.communityName) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            supportingContent = {
                Text(
                    listOf(
                        readingDurationLabel(community.seconds).asString(),
                        readingCountsLabel(community.opens, community.postOpens).asString(),
                    ).filter(String::isNotBlank).joinToString(" · "),
                )
            },
        )
    }
}

@Composable
private fun ReadingStatsSuggestionList(summary: ReadingSummary, state: SubshellState) {
    val communities = LocalReadingStatsCommunities.current
    val subscribed = communities.subscribedNames ?: return
    Text(stringResource(R.string.scr_subscriptions_you_never_open), style = MaterialTheme.typography.titleSmall)
    if (!summary.suggestionsReady) {
        val left = READING_STATS_SUGGESTION_MIN_DAYS - summary.days
        Text(
            stringResource(R.string.all_shown_after_days_of_counting_to_go, READING_STATS_SUGGESTION_MIN_DAYS, left),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val suggestions = readingSuggestions(summary, subscribed, state::isCommunityHidden)
    if (suggestions.isEmpty()) {
        Text(
            pluralStringResource(R.plurals.reading_opened_every_community, summary.days, summary.days),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Text(
        pluralStringResource(R.plurals.reading_not_opened_in, summary.days, summary.days),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    suggestions.forEach { name ->
        val leaving = communities.leavingName.equals(name, ignoreCase = true)
        ListItem(
            content = { Text("r/$name") },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            supportingContent = if (communities.leaveFailedName.equals(name, ignoreCase = true)) {
                { Text(stringResource(R.string.scr_couldn_t_leave_try_again_from_the), color = MaterialTheme.colorScheme.error) }
            } else {
                null
            },
            trailingContent = {
                Row {
                    TextButton(onClick = { state.toggleHiddenCommunity(name) }, enabled = !leaving) { Text(stringResource(R.string.scr_hide)) }
                    TextButton(onClick = { communities.onLeave(name) }, enabled = !leaving) {
                        Text(if (leaving) stringResource(R.string.all_leaving) else stringResource(R.string.all_leave))
                    }
                }
            },
        )
    }
}
