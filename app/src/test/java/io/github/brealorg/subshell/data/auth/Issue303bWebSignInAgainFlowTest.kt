package io.github.brealorg.subshell.data.auth

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.ConnectAccountFailureKind
import io.github.brealorg.subshell.data.account.ConnectAccountPhase
import io.github.brealorg.subshell.data.auth.reddit.RedditOAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #303 follow-up: Direct Chat needs Reddit's web sign-in, which can expire
 * while the OAuth grant is good, so the account still reads as signed in and
 * the ordinary reauthentication refused to start (seen on the Pixel 11: Sign
 * in again did nothing). Chat's own entry starts it for a signed-in account;
 * the ordinary one still asks for REAUTHENTICATION_REQUIRED, and a sign-in as
 * another Reddit user leaves the account as it was
 * (ISSUE303B_CHAT_SIGN_IN_AGAIN_REAUTHENTICATES_A_SIGNED_IN_ACCOUNT_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue303bWebSignInAgainFlowTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val configuration = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "client-id-123",
        redirectUri = "subshell-auth-test://oauth/callback",
        scopes = RedditOAuth.coreReadScopes,
        permanentAccess = true,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )
    private val scopes = RedditOAuth.coreReadScopes.joinToString(" ")
    private val store = EncryptedAuthSessionStore(application, cipher = PlainCipher).also { it.clearAll() }
    private var tokenRequests = 0
    private var tokenAnswer: Pair<Int, String> = 200 to
        """{"access_token":"access-1","token_type":"bearer","expires_in":3600,"scope":"$scopes","refresh_token":"refresh-1"}"""
    private var identity: AuthIdentityResult = AuthIdentityResult.Success(AuthAccountIdentity("t2me", "me"))

    private val tokenClient = OAuthTokenEndpointClient(
        callFactory = OkHttpClient.Builder()
            .addInterceptor { chain ->
                tokenRequests += 1
                val (code, body) = tokenAnswer
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("answer")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build(),
        configuration = configuration,
        userAgent = "subshell-test/1.0",
    )
    private val sessionManager = OAuthSessionManager(
        configuration = configuration,
        store = store,
        tokenClient = tokenClient,
        stateGenerator = { STATE },
    )
    private val coordinator = AuthAccountSessionCoordinator(
        configuration = configuration,
        store = store,
        tokenClient = tokenClient,
        identityClient = OAuthAccountIdentityClient { identity },
        localFixtureUsernames = listOf("subshell"),
        storageDispatcher = Dispatchers.Unconfined,
    )
    private val flow = ConfiguredAuthConnectFlowController(
        sessionManager = sessionManager,
        accountCoordinator = coordinator,
        store = store,
        providerLabel = "Reddit",
        clientMode = configuration.clientMode,
        storageDispatcher = Dispatchers.Unconfined,
    )

    private fun tokenAnswerWith(access: String) {
        tokenAnswer = 200 to
            """{"access_token":"$access","token_type":"bearer","expires_in":3600,"scope":"$scopes","refresh_token":"refresh-$access"}"""
    }

    private suspend fun returnFromSignIn() {
        val accepted = OAuthCallbackProcessor(store).process(
            Uri.parse("${configuration.redirectUri}?state=$STATE&code=the-code"),
        )
        assertTrue("$accepted", accepted is AuthCallbackResult.Accepted)
        flow.handleCallbackResultToken("accepted")
    }

    private suspend fun signedInAccount(): String {
        flow.beginAuthorization()
        returnFromSignIn()
        assertEquals(ConnectAccountPhase.CONNECTED, flow.uiState.value.phase)
        return store.accountRegistry()!!.accounts.single().accountId
    }

    @Test
    fun onlyChatsSignInAgainStartsForASignedInAccount() = runBlocking {
        val account = signedInAccount()

        assertEquals(AuthBrowserLaunchResult.NotStarted, flow.beginReauthentication(account))
        val launch = flow.beginWebSignInAgain(account)

        assertTrue("$launch", launch is AuthBrowserLaunchResult.Ready)
        assertEquals(account, store.pendingAuthorization()?.accountStableId)
    }

    @Test
    fun signingInAgainKeepsTheAccountAndStoresTheNewSignIn() = runBlocking {
        val account = signedInAccount()
        tokenAnswerWith("access-2")

        flow.beginWebSignInAgain(account)
        returnFromSignIn()

        assertEquals(ConnectAccountPhase.CONNECTED, flow.uiState.value.phase)
        assertEquals(listOf(account), store.accountRegistry()?.accounts?.map { it.accountId })
        assertEquals("access-2", store.session(account)?.tokens?.accessToken)
    }

    @Test
    fun signingInAsSomeoneElseLeavesTheAccountAsItWas() = runBlocking {
        val account = signedInAccount()
        tokenAnswerWith("access-other")
        identity = AuthIdentityResult.Success(AuthAccountIdentity("t2other", "other"))

        flow.beginWebSignInAgain(account)
        returnFromSignIn()

        assertEquals(ConnectAccountPhase.ERROR, flow.uiState.value.phase)
        assertEquals(listOf(account), store.accountRegistry()?.accounts?.map { it.accountId })
        assertNotNull(store.session(account))
        assertEquals("access-1", store.session(account)?.tokens?.accessToken)
    }

    @Test
    fun anUnknownAccountStartsNothing() = runBlocking {
        signedInAccount()

        assertEquals(AuthBrowserLaunchResult.NotStarted, flow.beginWebSignInAgain("reddit:nobody"))
    }

    private object PlainCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }

    private companion object {
        const val STATE = "abcdefghijklmnopqrstuvwxyz012345"
    }
}
