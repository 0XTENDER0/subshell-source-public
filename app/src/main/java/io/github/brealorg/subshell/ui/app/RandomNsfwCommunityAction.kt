package io.github.brealorg.subshell.ui.app

import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.model.RandomNsfwCommunityPick
import io.github.brealorg.subshell.ui.RandomNsfwCommunityPlacement
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.error.ErrorPresentation
import io.github.brealorg.subshell.ui.error.ErrorPresentationKind
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import io.github.brealorg.subshell.ui.symbols.Symbols
import kotlinx.coroutines.launch
import io.github.brealorg.subshell.ui.components.InlineProgressIndicator

// ISSUE211_RANDOM_NSFW_COMMUNITY_V1 (#211): one pick action for every
// placement the reader can choose (Search, Communities, navigation menu). A
// tap shows "Finding a community…" at once (the owner could not tell a tap
// had registered), a second tap while it runs does nothing, and the pick
// opens only for the account that asked.

@Stable
internal class RandomNsfwCommunityAction(private val onStart: () -> Unit) {
    var inProgress by mutableStateOf(false)
        internal set

    fun start() {
        if (!inProgress) onStart()
    }
}

internal val LocalRandomNsfwCommunityAction =
    staticCompositionLocalOf<RandomNsfwCommunityAction?> { null }

internal const val RANDOM_NSFW_COMMUNITY_SCOPE = "random-nsfw-community"

@Composable
internal fun rememberRandomNsfwCommunityAction(
    state: SubshellState,
    pick: (suspend (String) -> RandomNsfwCommunityPick)?,
    noticeHost: ViewportErrorHostState,
): RandomNsfwCommunityAction? {
    val scope = rememberCoroutineScope()
    val currentPick by rememberUpdatedState(pick)
    if (pick == null) return null
    return remember(state, noticeHost) {
        lateinit var action: RandomNsfwCommunityAction
        action = RandomNsfwCommunityAction {
            val picker = currentPick ?: return@RandomNsfwCommunityAction
            val account = state.activeAccountId
            action.inProgress = true
            noticeHost.clear(RANDOM_NSFW_COMMUNITY_SCOPE)
            scope.launch {
                try {
                    val pick = picker(account)
                    if (state.activeAccountId != account) return@launch
                    when (pick) {
                        is RandomNsfwCommunityPick.Found -> state.openSubreddit(pick.name)
                        // ISSUE263_NO_NSFW_COMMUNITY_OFFERED_IS_NOT_A_FAILURE_V1:
                        // Reddit's own answer is not one a retry changes.
                        is RandomNsfwCommunityPick.NoneOffered -> noticeHost.present(
                            scopeKey = RANDOM_NSFW_COMMUNITY_SCOPE,
                            presentation = ErrorPresentation(
                                kind = ErrorPresentationKind.DEGRADED,
                                title = UiText.Resource(R.string.notice_randomnsfwco_reddit_offered_no_nsfw_communities),
                                // ISSUE263_NSFW_SEARCH_PREFERENCE_NOTICE_V1: the usual cause.
                                message = UiText.Resource(R.string.notice_randomnsfwco_reddit_returned_none_for_this_account_trying),
                            ),
                            failureDetails = null,
                            onRetry = null,
                        )
                        RandomNsfwCommunityPick.NoneInShownLanguages -> noticeHost.present(
                            scopeKey = RANDOM_NSFW_COMMUNITY_SCOPE,
                            presentation = ErrorPresentation(
                                kind = ErrorPresentationKind.DEGRADED,
                                title = UiText.Resource(R.string.notice_randomnsfwco_no_nsfw_community_in_your_languages),
                                message = UiText.Resource(R.string.notice_randomnsfwco_every_nsfw_community_reddit_offered_is_in),
                            ),
                            failureDetails = null,
                            onRetry = null,
                        )
                        RandomNsfwCommunityPick.Failed -> noticeHost.present(
                            scopeKey = RANDOM_NSFW_COMMUNITY_SCOPE,
                            presentation = ErrorPresentation(
                                kind = ErrorPresentationKind.DEGRADED,
                                title = UiText.Resource(R.string.notice_randomnsfwco_couldn_t_find_an_nsfw_community),
                                message = UiText.Resource(R.string.notice_randomnsfwco_couldn_t_reach_reddit_try_again_in),
                            ),
                            failureDetails = null,
                            onRetry = null,
                        )
                    }
                } finally {
                    action.inProgress = false
                }
            }
        }
        action
    }
}

/** The button at [placement], shown only when the reader chose it and NSFW is on. */
@Composable
internal fun RandomNsfwCommunityButton(
    state: SubshellState,
    placement: RandomNsfwCommunityPlacement,
    modifier: Modifier = Modifier,
) {
    val action = LocalRandomNsfwCommunityAction.current ?: return
    if (!state.offersRandomNsfwCommunity(placement)) return
    AssistChip(
        modifier = modifier,
        onClick = action::start,
        enabled = !action.inProgress,
        label = { Text((if (action.inProgress) UiText.Resource(R.string.notice_randomnsfwco_finding_a_community) else UiText.Resource(R.string.notice_randomnsfwco_random_nsfw_community)).asString()) },
        leadingIcon = {
            Row {
                if (action.inProgress) {
                    InlineProgressIndicator()
                } else {
                    Icon(Symbols.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        },
    )
}
