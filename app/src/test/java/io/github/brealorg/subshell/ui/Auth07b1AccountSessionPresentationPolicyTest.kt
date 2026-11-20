package io.github.brealorg.subshell.ui

import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

// TRANSLATE14: reads string resources through UiText.s, so it needs Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Auth07b1AccountSessionPresentationPolicyTest {
    @Test
    fun connectedAccountCopyHidesOAuthTransport() {
        val connected = account(
            AccountSessionState.CONNECTED,
        )

        val selected =
            accountSwitcherSessionLabel(
                account = connected,
                selected = true,
            ).s
        val inactive =
            accountSwitcherSessionLabel(
                account = connected,
                selected = false,
            ).s

        assertEquals(
            "Current account",
            selected,
        )
        assertEquals(
            "Connected",
            inactive,
        )

        listOf(
            selected,
            inactive,
        ).forEach { label ->
            assertFalse(
                label.contains(
                    "RedReader",
                    ignoreCase = true,
                ),
            )
            assertFalse(
                label.contains(
                    "Connected via",
                    ignoreCase = true,
                ),
            )
        }
    }

    @Test
    fun reauthenticationCopyRemainsExplicit() {
        val account = account(
            AccountSessionState.REAUTHENTICATION_REQUIRED,
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

    private fun account(
        state: AccountSessionState,
    ): AccountSessionSummary =
        AccountSessionSummary(
            stableId =
                "reddit:redreader_compatibility:"
                    + "t2_auth07b1",
            username = "auth07b1",
            source =
                AccountSessionSource.AUTHENTICATED,
            state = state,
            providerLabel =
                "Reddit · RedReader compatibility",
        )
}
