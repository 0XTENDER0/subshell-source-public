package io.github.brealorg.subshell.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.auth.AuthAccountIdentity
import io.github.brealorg.subshell.data.auth.AuthAccountSessionCoordinator
import io.github.brealorg.subshell.data.auth.AuthCipher
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentResult
import io.github.brealorg.subshell.data.auth.EncryptedAuthSessionStore
import io.github.brealorg.subshell.data.auth.OAuthAccountIdentityClient
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import io.github.brealorg.subshell.data.auth.OAuthProvider
import io.github.brealorg.subshell.data.auth.OAuthProviderConfiguration
import io.github.brealorg.subshell.data.auth.OAuthTokenEndpointClient
import io.github.brealorg.subshell.data.auth.OAuthTokenSet
import io.github.brealorg.subshell.data.auth.StoredAuthAccountRecord
import io.github.brealorg.subshell.data.auth.StoredAuthAccountRegistry
import io.github.brealorg.subshell.data.auth.StoredOAuthSession
import io.github.brealorg.subshell.data.auth.authenticatedAccountId
import io.github.brealorg.subshell.data.auth.live03CommentCacheKey
import io.github.brealorg.subshell.data.cache.MutableCacheClock
import io.github.brealorg.subshell.data.cache.RoomSubshellCacheStore
import io.github.brealorg.subshell.data.cache.SubshellCacheDatabase
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.model.Comment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #307: 1.0.2 stored a deep linked comment's line as a second top comment
 * (r/subshell 1xsz8kz on the owner's Pixel 11), and opening the thread from
 * the cache showed it so: a duplicate row and "Couldn't find this comment".
 * The cached open and the observed thread mend it (ISSUE307_A_LINE_FROM_HALFWAY_DOWN_GOES_UNDER_ITS_PARENT_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue307StoredThreadMendedTest {
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
    private val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
    private val accountId = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")
    private val database = Room.inMemoryDatabaseBuilder(application, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database, clock = MutableCacheClock(1_000L))
    private val noNetwork = Call.Factory { error("no request in this test") }
    private lateinit var loader: RedditCommentLoader

    @Before
    fun signedIn() = runBlocking {
        store.clearAll()
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(
                    StoredAuthAccountRecord(
                        accountId = accountId,
                        provider = OAuthProvider.REDDIT,
                        providerAccountId = "t2me",
                        username = "me",
                        createdAtEpochMs = 1L,
                        lastUsedAtEpochMs = 1L,
                        clientMode = configuration.clientMode,
                    ),
                ),
                activeAccountId = accountId,
            ),
        )
        store.saveSession(
            accountId,
            StoredOAuthSession(
                provider = OAuthProvider.REDDIT,
                tokens = OAuthTokenSet("access", "refresh", "bearer", RedditOAuth.coreReadScopes.joinToString(" "), Long.MAX_VALUE),
                identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
            ),
        )
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = noNetwork, configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        coordinator.hydratedSnapshot()
        loader = RedditCommentLoader(
            callFactory = noNetwork,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            configuration = configuration,
            cacheStore = cache,
            accountCoordinator = coordinator,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun close() {
        cache.close()
    }

    private val cacheKey get() = live03CommentCacheKey(accountId, "abc", RedditCommentSort.BEST)

    private fun comment(id: String, body: String, parent: String = "t3_abc", depth: Int = 0) = Comment(
        id = id,
        author = "someone",
        age = "1h",
        body = body,
        score = 1,
        depth = depth,
        accent = 0xFF000000,
        parentId = parent,
    )

    @Test
    fun aThreadStoredWithALinkedLineAsATopCommentOpensMended() = runBlocking {
        // How 1.0.2 stored r/subshell 1xsz8kz: the linked line from c1 as a
        // second top comment beside c0, which holds c1 too.
        val c1Stored = comment("t1_c1", "one", parent = "t1_c0", depth = 0).copy(
            children = listOf(comment("t1_c2", "two", parent = "t1_c1", depth = 1)),
        )
        cache.cacheComments(
            cacheKey,
            listOf(
                comment("t1_c0", "zero").copy(children = listOf(comment("t1_c1", "one", parent = "t1_c0", depth = 1))),
                c1Stored,
            ),
        )

        val comments = (loader.cached(accountId, "abc", RedditCommentSort.BEST) as AuthenticatedRedditCommentResult.Loaded).comments

        assertEquals(listOf("t1_c0"), comments.map { it.id })
        val c1 = comments.single().children.single()
        assertEquals("t1_c1", c1.id)
        assertEquals(2, c1.children.single().depth)
    }

    @Test
    fun theObservedThreadReachesTheScreenMendedToo() = runBlocking {
        // Pixel 11: the screen also follows the stored thread, and that copy
        // came unmended (a duplicate row; the landing failed when it came first).
        cache.cacheComments(
            cacheKey,
            listOf(
                comment("t1_c0", "zero").copy(children = listOf(comment("t1_c1", "one", parent = "t1_c0", depth = 1))),
                comment("t1_c1", "one", parent = "t1_c0", depth = 0).copy(
                    children = listOf(comment("t1_c2", "two", parent = "t1_c1", depth = 1)),
                ),
            ),
        )

        val observed = loader.observeStoredThread(accountId, "abc", RedditCommentSort.BEST).first()

        assertEquals(listOf("t1_c0"), observed.map { it.id })
        assertEquals(2, observed.single().children.single().children.single().depth)
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
