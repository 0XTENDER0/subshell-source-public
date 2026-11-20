package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.network.NetworkBearerTokenProvider
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pixel 11 (2026-11-15): New message found u/subshell_dev for "subshell_"
 * and nothing for "subshell_dev": Reddit's profile autocomplete drops it. A
 * query that is a whole username is also looked up by name
 * (PEOPLESEARCH_AN_EXACT_NAME_IS_ALWAYS_FOUND_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PeopleSearchExactNameTest {
    private val paths = mutableListOf<String>()
    private var aboutCode = 200

    private val client = SubshellHttpClient(
        callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            paths += path
            val (code, body) = when {
                path.endsWith("/about") -> aboutCode to
                    """{"kind":"t2","data":{"name":"subshell_dev","subreddit":{"display_name":"u_subshell_dev","title":"","subreddit_type":"user"}}}"""
                else -> 200 to """{"kind":"Listing","data":{"after":null,"children":[]}}"""
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("answer")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        json = SubshellNetworkJson.providerJson,
        userAgent = "subshell-test",
        bearerTokenProvider = NetworkBearerTokenProvider { "bearer-for-tests" },
    )

    private fun search(query: String) = runBlocking {
        RedditPeopleSearchNetworkDataSource(client).page(query, cursor = null, limit = 10)
    }

    @Test
    fun aWholeUsernameTheAutocompleteMissesIsFoundByName() {
        val result = search("subshell_dev") as NetworkResult.Success

        assertEquals(listOf("subshell_dev"), result.value.items.map { it.username })
        assertEquals("/user/subshell_dev/about", paths.last())
    }

    @Test
    fun aNameThatIsNotThereLeavesTheEmptyResult() {
        aboutCode = 404

        val result = search("nobody_here") as NetworkResult.Success

        assertEquals(emptyList<String>(), result.value.items.map { it.username })
    }

    @Test
    fun aQueryThatCannotBeAUsernameAsksNothingMore() {
        search("a b")

        assertEquals(1, paths.size)
    }
}
