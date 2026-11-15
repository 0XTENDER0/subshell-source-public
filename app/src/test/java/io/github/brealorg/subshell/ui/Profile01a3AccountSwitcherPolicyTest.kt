package io.github.brealorg.subshell.ui

import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.ConnectAccountPhase
import io.github.brealorg.subshell.data.account.ConnectAccountUiState
import io.github.brealorg.subshell.data.account.localFixtureAccountSummaries
import org.junit.Assert.assertEquals
import org.junit.Test

// TRANSLATE14: reads string resources through UiText.s, so it needs Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Profile01a3AccountSwitcherPolicyTest {
    @Test
    fun productSwitcherHidesLocalFixtures() {
        val local =
            localFixtureAccountSummaries(
                listOf("root", "packetmoss"),
            )

        val reddit = authenticatedAccount()

        assertEquals(
            listOf(reddit),
            accountSwitcherSessions(
                local + reddit,
            ),
        )
    }

    @Test
    fun idleWithAuthenticatedAccountUsesAdd() {
        val presentation =
            accountConnectPresentation(
                connectAccountState =
                    connectState(
                        ConnectAccountPhase.IDLE,
                    ),
                hasAuthenticatedAccounts = true,
            )

        assertEquals(
            "Add Reddit account",
            presentation.headline.s,
        )
        assertEquals(
            "Sign in with another Reddit account.",
            presentation.supporting.s,
        )
        assertEquals(
            AccountConnectAction.CONNECT,
            presentation.action,
        )
        assertEquals(
            "Add",
            presentation.actionLabel?.s,
        )
    }

    @Test
    fun connectedFlowStillUsesAdd() {
        val presentation =
            accountConnectPresentation(
                connectAccountState =
                    connectState(
                        ConnectAccountPhase.CONNECTED,
                    ),
                hasAuthenticatedAccounts = true,
            )

        assertEquals(
            "Add Reddit account",
            presentation.headline.s,
        )
        assertEquals(
            "Add",
            presentation.actionLabel?.s,
        )
    }

    @Test
    fun emptyAuthenticatedDirectoryUsesConnect() {
        val presentation =
            accountConnectPresentation(
                connectAccountState =
                    connectState(
                        ConnectAccountPhase.IDLE,
                    ),
                hasAuthenticatedAccounts = false,
            )

        assertEquals(
            "Connect Reddit account",
            presentation.headline.s,
        )
        assertEquals(
            "Connect",
            presentation.actionLabel?.s,
        )
    }

    @Test
    fun reauthenticationRemainsVisible() {
        val account =
            authenticatedAccount(
                AccountSessionState
                    .REAUTHENTICATION_REQUIRED,
            )

        assertEquals(
            "Current account · Sign in again",
            accountSwitcherSessionLabel(
                account = account,
                selected = true,
            ).s,
        )

        assertEquals(
            "Sign in again",
            accountSwitcherSessionLabel(
                account = account,
                selected = false,
            ).s,
        )
    }

    private fun authenticatedAccount(
        state: AccountSessionState =
            AccountSessionState.CONNECTED,
    ): AccountSessionSummary =
        AccountSessionSummary(
            stableId =
                "reddit:redreader_compatibility:"
                    + "t2_brealorg",
            username = "brealorg",
            source =
                AccountSessionSource.AUTHENTICATED,
            state = state,
            providerLabel = "Reddit",
        )

    private fun connectState(
        phase: ConnectAccountPhase,
    ): ConnectAccountUiState =
        ConnectAccountUiState(
            phase = phase,
            providerLabel = "Reddit",
        )
}
