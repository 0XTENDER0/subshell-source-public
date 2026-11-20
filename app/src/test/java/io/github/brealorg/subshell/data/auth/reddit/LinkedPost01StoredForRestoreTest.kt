package io.github.brealorg.subshell.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.PersistentSubshellRepository
import io.github.brealorg.subshell.data.RepositoryPageRequest
import io.github.brealorg.subshell.data.RepositoryQueryKey
import io.github.brealorg.subshell.data.RepositoryResult
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.auth.AuthAccountIdentity
import io.github.brealorg.subshell.data.auth.AuthInboxReadCapability
import io.github.brealorg.subshell.data.auth.AuthInboxReadCapabilityResult
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditInboxTargetResult
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import io.github.brealorg.subshell.data.auth.OAuthProvider
import io.github.brealorg.subshell.data.auth.OAuthTokenSet
import io.github.brealorg.subshell.data.auth.StoredOAuthSession
import io.github.brealorg.subshell.data.cache.RoomSubshellCacheStore
import io.github.brealorg.subshell.data.cache.SubshellCacheDatabase
import io.github.brealorg.subshell.data.createFixtureFreeLocalSubshellRepository
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.scopedToAccount
import io.github.brealorg.subshell.ui.FeedPostOrigin
import io.github.brealorg.subshell.ui.FeedPostRoute
import io.github.brealorg.subshell.ui.FeedSort
import io.github.brealorg.subshell.ui.FeedSource
import io.github.brealorg.subshell.ui.MainDestination
import io.github.brealorg.subshell.ui.PostComposerDraft
import io.github.brealorg.subshell.ui.SubshellProcessSnapshot
import io.github.brealorg.subshell.ui.SubshellRoute
import io.github.brealorg.subshell.ui.SubshellRouteStack
import io.github.brealorg.subshell.ui.decodeSubshellProcessSnapshot
import io.github.brealorg.subshell.ui.encodeSubshellProcessSnapshot
import io.github.brealorg.subshell.ui.subshellProcessRestoreCatalog
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LINKEDPOST01_A_POST_OPENED_FROM_A_LINK_IS_STORED_V1: a post opened from a link
 * (or a notification, Inbox item, search hit, queue item) is read from Reddit.
 * It was never stored, so after process death the restore dropped the post and
 * the reader landed on the tab. It is now stored as a one-post list of the
 * account, which also survives the orphan clean-up of the next feed write.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LinkedPost01StoredForRestoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database)
    private val account = "reddit:redreader_compatibility:me"
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("test-token", null, "bearer", "identity read", Long.MAX_VALUE),
        identity = AuthAccountIdentity("me", "test-user"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    private val thread = """
        [{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"lnk1","name":"t3_lnk1","author":"someone",
          "title":"Opened from a link","subreddit":"subshell","subreddit_name_prefixed":"r/subshell",
          "created_utc":1700000000,"score":1,"num_comments":0,"permalink":"/r/subshell/comments/lnk1/opened/"}}]}},
         {"kind":"Listing","data":{"children":[]}}]
    """.trimIndent()

    private val callFactory = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK")
            .body(thread.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun loader() = RedditInboxLoader(
        callFactory = callFactory,
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        cacheStore = cache,
        capability = LinkedPostTestCapability(session),
    )

    @After
    fun close() {
        cache.close()
    }

    private fun restoredRoutes(postId: String): List<String> {
        val stack = SubshellRouteStack.forDestination(MainDestination.HOME, FeedSource.HOME, FeedSort.BEST)
            .push(
                SubshellRoute.Post(
                    FeedPostRoute(
                        postId = postId,
                        focusedCommentId = null,
                        visitId = 1,
                        origin = FeedPostOrigin.MainFeed(MainDestination.HOME, FeedSource.HOME, FeedSort.BEST),
                    ),
                ),
            )
        val encoded = encodeSubshellProcessSnapshot(
            SubshellProcessSnapshot(
                routeStack = stack,
                scrollAnchors = emptyMap(),
                userProfileTabs = emptyMap(),
                postComposerDraft = PostComposerDraft(),
                feedVideoMuted = true,
                backgroundResumeMediaId = null,
                backgroundResumeWasManual = false,
            ),
        )
        val repository = PersistentSubshellRepository(createFixtureFreeLocalSubshellRepository(), cache)
        val decoded = requireNotNull(decodeSubshellProcessSnapshot(encoded, subshellProcessRestoreCatalog(repository)))
        return decoded.routeStack.entries.map { route ->
            (route as? SubshellRoute.Post)?.postRoute?.postId ?: route.javaClass.simpleName
        }
    }

    /** The next feed write deletes every post row no list points at. */
    private fun writeAFeedPage() {
        cache.writePostResult(
            request = RepositoryPageRequest(
                queryKey = RepositoryQueryKey("home").scopedToAccount(account, AccountSessionSource.AUTHENTICATED),
            ),
            result = RepositoryResult.Content(listOf(SampleData.posts.first().copy(id = "t3_feed1"))),
        )
    }

    @Test
    fun aPostResolvedFromALinkIsRestoredAfterProcessDeath() = runBlocking {
        val resolved = loader().resolveTargetPost(account, "lnk1")
        assertTrue(resolved.toString(), resolved is AuthenticatedRedditInboxTargetResult.Loaded)
        val postId = (resolved as AuthenticatedRedditInboxTargetResult.Loaded).post.id

        writeAFeedPage()

        assertEquals(listOf("Main", postId), restoredRoutes(postId))
    }

    @Test
    fun aPostNobodyStoredIsStillDropped() {
        writeAFeedPage()
        assertEquals(listOf("Main"), restoredRoutes("t3_never_opened"))
    }
}

private class LinkedPostTestCapability(private val session: StoredOAuthSession) : AuthInboxReadCapability {
    override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
    override suspend fun hasScopeGrant(accountStableId: String): Boolean = true
    override fun hasPendingAuthorization(): Boolean = false
    override fun hasStoredCallback(): Boolean = false
    override fun cancelPendingAuthorization() = Unit
    override suspend fun completePendingAuthorization(): AuthInboxReadCapabilityResult =
        throw UnsupportedOperationException()
    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        AuthSessionResult.Authenticated(session)
}
