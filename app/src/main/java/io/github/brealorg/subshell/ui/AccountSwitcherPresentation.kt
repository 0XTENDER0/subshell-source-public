package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.ConnectAccountFailureKind
import io.github.brealorg.subshell.data.account.ConnectAccountPhase
import io.github.brealorg.subshell.data.account.ConnectAccountUiState

internal enum class AccountConnectAction {
    CONNECT,
    CANCEL,
    RESET,
    NONE,
}

internal data class AccountConnectPresentation(
    val headline: UiText,
    val supporting: UiText,
    val action: AccountConnectAction,
    val actionLabel: UiText? = null,
)

internal fun accountSwitcherSessions(
    accounts: List<AccountSessionSummary>,
): List<AccountSessionSummary> =
    accounts.filter {
        it.source == AccountSessionSource.AUTHENTICATED
    }

internal fun accountSwitcherSessionLabel(
    account: AccountSessionSummary,
    selected: Boolean,
): UiText = when {
    account.state != AccountSessionState.CONNECTED && selected ->
        UiText.Resource(R.string.acct_current_account_sign_in_again)

    account.state != AccountSessionState.CONNECTED ->
        UiText.Resource(R.string.acct_sign_in_again)

    selected ->
        UiText.Resource(R.string.acct_current_account)

    else ->
        UiText.Resource(R.string.acct_connected)
}

internal fun accountConnectPresentation(
    connectAccountState: ConnectAccountUiState,
    hasAuthenticatedAccounts: Boolean,
): AccountConnectPresentation = when (
    connectAccountState.phase
) {
    ConnectAccountPhase.UNAVAILABLE ->
        AccountConnectPresentation(
            headline = if (hasAuthenticatedAccounts) {
                UiText.Resource(R.string.acct_add_reddit_account_unavailable)
            } else {
                UiText.Resource(R.string.acct_reddit_sign_in_unavailable)
            },
            supporting = if (hasAuthenticatedAccounts) {
                UiText.Resource(R.string.acct_adding_another_reddit_account_isn_t_available)
            } else {
                UiText.Resource(R.string.acct_reddit_sign_in_isn_t_available_in)
            },
            action = AccountConnectAction.NONE,
        )

    ConnectAccountPhase.IDLE,
    ConnectAccountPhase.CONNECTED ->
        AccountConnectPresentation(
            headline = if (hasAuthenticatedAccounts) {
                UiText.Resource(R.string.acct_add_reddit_account)
            } else {
                UiText.Resource(R.string.acct_connect_reddit_account)
            },
            supporting = if (hasAuthenticatedAccounts) {
                UiText.Resource(R.string.acct_sign_in_with_another_reddit_account)
            } else {
                UiText.Resource(R.string.acct_sign_in_to_use_your_reddit_account)
            },
            action = AccountConnectAction.CONNECT,
            actionLabel = if (hasAuthenticatedAccounts) {
                UiText.Resource(R.string.acct_add)
            } else {
                UiText.Resource(R.string.acct_connect)
            },
        )

    ConnectAccountPhase.LAUNCHING ->
        AccountConnectPresentation(
            headline = UiText.Resource(R.string.acct_opening_reddit_sign_in),
            supporting = UiText.Resource(R.string.acct_starting_reddit_authorization),
            action = AccountConnectAction.NONE,
        )

    ConnectAccountPhase.AWAITING_CALLBACK ->
        AccountConnectPresentation(
            headline = UiText.Resource(R.string.acct_waiting_for_reddit),
            supporting =
                UiText.Resource(R.string.acct_finish_sign_in_in_your_browser_then),
            action = AccountConnectAction.CANCEL,
            actionLabel = UiText.Resource(R.string.acct_cancel),
        )

    ConnectAccountPhase.EXCHANGING ->
        AccountConnectPresentation(
            headline = UiText.Resource(R.string.acct_completing_sign_in),
            supporting = UiText.Resource(R.string.acct_finishing_reddit_sign_in),
            action = AccountConnectAction.NONE,
        )

    ConnectAccountPhase.RESOLVING_IDENTITY ->
        AccountConnectPresentation(
            headline = UiText.Resource(R.string.acct_loading_reddit_account),
            supporting = UiText.Resource(R.string.acct_reading_your_reddit_account),
            action = AccountConnectAction.NONE,
        )

    ConnectAccountPhase.SYNCING_REDDIT_FEED ->
        AccountConnectPresentation(
            headline = UiText.Resource(R.string.acct_preparing_reddit_account),
            supporting = UiText.Resource(R.string.acct_loading_the_first_reddit_feed),
            action = AccountConnectAction.NONE,
        )

    ConnectAccountPhase.CANCELLED ->
        AccountConnectPresentation(
            headline = UiText.Resource(R.string.acct_reddit_sign_in_cancelled),
            supporting = UiText.Resource(R.string.acct_no_account_was_added),
            action = AccountConnectAction.RESET,
            actionLabel = UiText.Resource(R.string.acct_done),
        )

    ConnectAccountPhase.ERROR ->
        AccountConnectPresentation(
            headline = if (
                connectAccountState.failureKind ==
                ConnectAccountFailureKind.REAUTHENTICATION_ACCOUNT_MISMATCH
            ) {
                UiText.Resource(R.string.acct_could_not_reconnect_reddit_account)
            } else if (hasAuthenticatedAccounts) {
                UiText.Resource(R.string.acct_could_not_add_reddit_account)
            } else {
                UiText.Resource(R.string.acct_could_not_connect_reddit_account)
            },
            supporting =
                connectAccountFailureLabel(
                    connectAccountState.failureKind,
                ),
            action = if (connectAccountState.retryable) {
                AccountConnectAction.CONNECT
            } else {
                AccountConnectAction.RESET
            },
            actionLabel = if (connectAccountState.retryable) {
                UiText.Resource(R.string.acct_retry)
            } else {
                UiText.Resource(R.string.acct_dismiss)
            },
        )
}

private fun connectAccountFailureLabel(
    failureKind: ConnectAccountFailureKind?,
): UiText = when (failureKind) {
    ConnectAccountFailureKind.CONFIGURATION_REQUIRED ->
        UiText.Resource(R.string.acct_oauth_build_configuration_is_incomplete)
    ConnectAccountFailureKind.BROWSER_LAUNCH ->
        UiText.Resource(R.string.acct_no_compatible_browser_could_open_the_authorization)
    ConnectAccountFailureKind.CALLBACK_REJECTED ->
        UiText.Resource(R.string.acct_the_callback_did_not_match_the_pending)
    ConnectAccountFailureKind.CALLBACK_MISSING ->
        UiText.Resource(R.string.acct_the_callback_could_not_be_recovered_start)
    ConnectAccountFailureKind.PROVIDER ->
        UiText.Resource(R.string.acct_reddit_returned_an_authorization_error)
    ConnectAccountFailureKind.TOKEN_EXCHANGE ->
        UiText.Resource(R.string.acct_the_authorization_response_could_not_be_exchanged)
    ConnectAccountFailureKind.IDENTITY ->
        UiText.Resource(R.string.acct_the_authenticated_account_identity_could_not_be)
    ConnectAccountFailureKind.REDDIT_FEED ->
        UiText.Resource(R.string.acct_the_first_reddit_feed_could_not_be)
    ConnectAccountFailureKind.ACCOUNT_CONFLICT ->
        UiText.Resource(R.string.acct_that_reddit_account_conflicts_with_an_account)
    ConnectAccountFailureKind.REAUTHENTICATION_ACCOUNT_MISMATCH ->
        UiText.Resource(R.string.acct_reddit_returned_a_different_account_than_the)
    ConnectAccountFailureKind.STORAGE ->
        UiText.Resource(R.string.acct_the_encrypted_account_session_could_not_be)
    null -> UiText.Resource(R.string.acct_the_account_connection_could_not_be_completed)
}
