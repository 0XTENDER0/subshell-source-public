package io.github.brealorg.subshell.chat

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #303: a Direct Chat GIF failed with CHAT_WEB_TOKEN_FAILED, one code for every
 * way the web-session mint can fail. Each failure now names its step
 * (ISSUE303_CHAT_TOKEN_FAILURE_NAMES_ITS_STEP_V1); the cause was the robots.txt
 * bootstrap, which Reddit now answers with a 403 block page, so the mint loads
 * the home page (ISSUE303B_THE_MINT_LOADS_THE_HOME_PAGE_V1); the csrf value
 * comes from the cookie store, not only from document.cookie
 * (ISSUE303_CSRF_COMES_FROM_THE_COOKIE_STORE_V1); a 401 on a just-minted token
 * is an expired web sign-in (ISSUE303B_A_MATRIX_401_ON_A_FRESH_TOKEN_IS_AN_EXPIRED_WEB_SIGN_IN_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue303ChatTokenStepTest {
    @Test
    fun theAnswerNamesWhyItHasNoToken() {
        assertEquals(
            RedditChatWebTokenResult.Failed("CHAT_WEB_TOKEN_HTTP_403"),
            redditChatTokenAnswer(status = 403, responseBody = "<html>blocked</html>"),
        )
        assertEquals(
            RedditChatWebTokenResult.Failed("CHAT_WEB_TOKEN_HTTP_OTHER"),
            redditChatTokenAnswer(status = 0, responseBody = ""),
        )
        assertEquals(
            RedditChatWebTokenResult.Failed(CHAT_WEB_TOKEN_NO_TOKEN),
            redditChatTokenAnswer(status = 200, responseBody = "{}"),
        )
        assertEquals(
            RedditChatWebTokenResult.Failed(CHAT_WEB_TOKEN_NO_TOKEN),
            redditChatTokenAnswer(status = 200, responseBody = "not json"),
        )
    }

    @Test
    fun aTwoHundredWithATokenIsASuccess() {
        val result = redditChatTokenAnswer(
            status = 200,
            responseBody = """{"token":"abc","expires":1900000000}""",
        )

        assertEquals(RedditChatWebTokenResult.Success("abc", 1_900_000_000_000L), result)
    }

    @Test
    fun theMintLoadsTheHomePageNeverRobotsTxt() {
        // ISSUE303B: robots.txt now gets a 403 block page; the home page answers.
        assertEquals("https://www.reddit.com/", REDDIT_CHAT_TOKEN_BOOTSTRAP_URL)
    }

    @Test
    fun onlyA401OnAJustMintedTokenIsAnExpiredWebSignIn() {
        val fresh = RedditChatWebTokenResult.Success("abc", null, fresh = true)
        val cached = RedditChatWebTokenResult.Success("abc", null, fresh = false)

        assertTrue(redditChatWebSignInExpired(fresh, 401))
        assertEquals(false, redditChatWebSignInExpired(cached, 401))
        assertEquals(false, redditChatWebSignInExpired(fresh, 403))
        assertEquals(false, redditChatWebSignInExpired(fresh, 500))
    }

    @Test
    fun theCsrfValueIsReadFromTheCookieStoreOnlyWhenItIsAPlainValue() {
        assertEquals("a1b2-c3_d4", redditChatCsrfCookieValue("loid=x; csrf_token=a1b2-c3_d4; reddit_session=y"))
        assertNull(redditChatCsrfCookieValue("x_csrf_token=a1b2; reddit_session=y"))
        assertNull(redditChatCsrfCookieValue("csrf_token=a1'b2"))
        assertNull(redditChatCsrfCookieValue("csrf_token="))
    }

    @Test
    fun theScriptSendsTheStoredCsrfValueAndHandsTheStatusToTheBridge() {
        val nonce = UUID.randomUUID().toString()

        val withValue = redditChatTokenFetchScript(nonce, "a1b2c3")
        val withoutValue = redditChatTokenFetchScript(nonce)

        assertTrue(withValue.contains("var csrf = 'a1b2c3' ||"))
        assertTrue(withoutValue.contains("var csrf = '' ||"))
        assertTrue(withValue.contains("SubshellChatTokenBridge.onToken(nonce, response.status, body);"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aCsrfValueThatCouldBreakOutOfTheScriptIsRefused() {
        redditChatTokenFetchScript(UUID.randomUUID().toString(), "x'); alert(1); ('")
    }
}
