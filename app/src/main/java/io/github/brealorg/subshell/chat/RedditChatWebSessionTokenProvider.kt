package io.github.brealorg.subshell.chat

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal sealed interface RedditChatWebTokenResult {
    /**
     * [fresh] is true for a token minted by this call, false for one from the
     * cache. A Matrix 401 on a fresh token means Reddit's web sign-in itself has
     * expired (ISSUE303B_A_MATRIX_401_ON_A_FRESH_TOKEN_IS_AN_EXPIRED_WEB_SIGN_IN_V1): Reddit then mints
     * a logged-out token, and only signing in again helps.
     */
    data class Success(
        val token: String,
        val expiresAtEpochMs: Long?,
        val fresh: Boolean = false,
    ) : RedditChatWebTokenResult

    data object NoRedditWebSession : RedditChatWebTokenResult

    /**
     * ISSUE303_CHAT_TOKEN_FAILURE_NAMES_ITS_STEP_V1: [step] is a short code
     * (no content) that a device report shows, so a failed mint says whether
     * the page never loaded, Reddit answered with an error status, the answer
     * had no token, the script failed, or no WebView could be made.
     */
    data class Failed(val step: String) : RedditChatWebTokenResult
}

/** ISSUE303B_THE_MINT_LOADS_THE_HOME_PAGE_V1 */
internal const val REDDIT_CHAT_TOKEN_BOOTSTRAP_URL = "https://www.reddit.com/"

internal const val CHAT_WEB_TOKEN_PAGE_TIMEOUT = "CHAT_WEB_TOKEN_PAGE_TIMEOUT"
internal const val CHAT_WEB_TOKEN_FETCH_TIMEOUT = "CHAT_WEB_TOKEN_FETCH_TIMEOUT"
internal const val CHAT_WEB_TOKEN_NO_TOKEN = "CHAT_WEB_TOKEN_NO_TOKEN"
internal const val CHAT_WEB_TOKEN_SCRIPT = "CHAT_WEB_TOKEN_SCRIPT"
internal const val CHAT_WEB_TOKEN_WEBVIEW = "CHAT_WEB_TOKEN_WEBVIEW"
internal const val CHAT_WEB_TOKEN_CANCELLED = "CHAT_WEB_TOKEN_CANCELLED"
internal const val CHAT_WEB_TOKEN_HTTP_PREFIX = "CHAT_WEB_TOKEN_HTTP_"

/** The token answer read into a result; [status] is the HTTP status the page script saw. */
internal fun redditChatTokenAnswer(
    status: Int,
    responseBody: String,
): RedditChatWebTokenResult {
    if (status !in 200..299) {
        val code = if (status in 100..599) status.toString() else "OTHER"
        return RedditChatWebTokenResult.Failed(CHAT_WEB_TOKEN_HTTP_PREFIX + code)
    }
    val parsed = runCatching { JSONObject(responseBody) }.getOrNull()
    val token = parsed
        ?.optString("token")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return RedditChatWebTokenResult.Failed(CHAT_WEB_TOKEN_NO_TOKEN)
    val expires = redditChatTokenExpiryEpochMs(expires = parsed.opt("expires"), token = token)
    return RedditChatWebTokenResult.Success(token = token, expiresAtEpochMs = expires)
}

/**
 * ISSUE303B_A_MATRIX_401_ON_A_FRESH_TOKEN_IS_AN_EXPIRED_WEB_SIGN_IN_V1: a cached
 * token can simply be stale (it is dropped and the next call mints again); a
 * just-minted one refused with 401 is the logged-out token Reddit mints for an
 * expired `reddit_session` (seen on the Pixel 11, 2026-11-14).
 */
internal fun redditChatWebSignInExpired(
    token: RedditChatWebTokenResult.Success,
    matrixStatusCode: Int,
): Boolean = token.fresh && matrixStatusCode == 401

/**
 * ISSUE303_CSRF_COMES_FROM_THE_COOKIE_STORE_V1: the csrf_token value as the
 * cookie store holds it (the page's document.cookie may not show it). Only a
 * plain token shape is returned, so it can be put into the script as is.
 */
internal fun redditChatCsrfCookieValue(cookieHeader: String): String? = cookieHeader
    .split(';')
    .map { it.trim() }
    .firstOrNull { it.startsWith("csrf_token=") }
    ?.substringAfter('=', missingDelimiterValue = "")
    ?.takeIf { value -> value.length in 1..256 && value.all { it.isLetterOrDigit() || it == '-' || it == '_' } }

internal data class RedditChatCachedWebToken(
    val token: String,
    val expiresAtEpochMs: Long,
    val redditSessionFingerprint: String,
)

internal fun redditChatWebTokenCacheUsable(
    cached: RedditChatCachedWebToken?,
    redditSessionFingerprint: String?,
    nowEpochMs: Long,
    expirySkewMs: Long = RedditChatWebSessionTokenProvider.TOKEN_EXPIRY_SKEW_MS,
): Boolean = cached != null &&
    redditSessionFingerprint != null &&
    cached.redditSessionFingerprint == redditSessionFingerprint &&
    nowEpochMs < cached.expiresAtEpochMs - expirySkewMs

internal fun redditChatSessionFingerprint(cookieHeader: String): String? {
    val session = cookieHeader
        .split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("reddit_session=") }
        ?.substringAfter('=', missingDelimiterValue = "")
        ?.takeIf(String::isNotEmpty)
        ?: return null
    return MessageDigest.getInstance("SHA-256")
        .digest(session.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

// STEP3_MATRIX_WEB_TOKEN_SEAM_V1: the Matrix reader and writers take this
// interface, so a test can hand them a fixed token instead of minting one in a
// WebView. RedditChatWebSessionTokenProvider is the only production source.
internal interface RedditChatWebTokenSource {
    suspend fun token(): RedditChatWebTokenResult

    fun invalidate(expectedToken: String? = null)
}

/**
 * Mints Reddit's first-party web JWT from the existing reddit.com WebView session.
 *
 * The bridge is attached only to a one-shot WebView pinned to reddit.com. It never receives
 * arbitrary navigation, local files, content:// URLs, or a non-Reddit origin.
 */
internal class RedditChatWebSessionTokenProvider(
    context: Context,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : RedditChatWebTokenSource {
    private val appContext = context.applicationContext
    private val mintMutex = Mutex()
    @Volatile
    private var cachedToken: RedditChatCachedWebToken? = null

    override suspend fun token(): RedditChatWebTokenResult {
        mintMutex.lock()
        try {
            return withContext(Dispatchers.Main.immediate) {
                val cookieManager = CookieManager.getInstance()
                val cookies = cookieManager.getCookie(REDDIT_ORIGIN).orEmpty()
                val sessionFingerprint = redditChatSessionFingerprint(cookies)
                if (sessionFingerprint == null) {
                    cachedToken = null
                    return@withContext RedditChatWebTokenResult.NoRedditWebSession
                }

                val cached = cachedToken
                if (
                    redditChatWebTokenCacheUsable(
                        cached = cached,
                        redditSessionFingerprint = sessionFingerprint,
                        nowEpochMs = nowEpochMs(),
                    )
                ) {
                    return@withContext RedditChatWebTokenResult.Success(
                        token = requireNotNull(cached).token,
                        expiresAtEpochMs = cached.expiresAtEpochMs,
                    )
                }
                cachedToken = null

                // ISSUE303B_THE_MINT_LOADS_THE_HOME_PAGE_V1: Reddit answers the
                // token call from robots.txt with a 403 block page (seen on the
                // Pixel 11 with a valid sign-in, 2026-11-14), which is what broke
                // #303; from the home page it answers with a token.
                mint(bootstrapUrl = REDDIT_CHAT_TOKEN_BOOTSTRAP_URL, sessionFingerprint = sessionFingerprint)
            }
        } finally {
            mintMutex.unlock()
        }
    }

    private suspend fun mint(
        bootstrapUrl: String,
        sessionFingerprint: String,
    ): RedditChatWebTokenResult {
        // SECAUDIT12_CHAT_BRIDGE_ANSWERS_ONLY_ITS_OWN_SCRIPT_V1 (security
        // audit #12): any reddit.com frame in this WebView can call the
        // bridge. Only the injected script knows this mint's nonce, so
        // a call without it is ignored (it can neither feed a token nor
        // end the mint early).
        val bridgeNonce = UUID.randomUUID().toString()
        return suspendCancellableCoroutine<RedditChatWebTokenResult> { continuation ->
            val handler = Handler(Looper.getMainLooper())
            var finished = false
            var fetchStarted = false
            var webView: WebView? = null

            fun complete(result: RedditChatWebTokenResult) {
                if (finished) return
                finished = true
                handler.removeCallbacksAndMessages(null)
                webView?.let { view ->
                    runCatching {
                        view.removeJavascriptInterface(BRIDGE_NAME)
                        view.stopLoading()
                        view.loadUrl("about:blank")
                        view.destroy()
                    }
                }
                if (continuation.isActive) continuation.resume(result)
            }

            val bridge = object {
                @JavascriptInterface
                fun onToken(nonce: String?, status: Int, responseBody: String) {
                    if (!redditChatBridgeCallIsOwn(bridgeNonce, nonce)) return
                    handler.post {
                        val result = redditChatTokenAnswer(
                            status = status,
                            responseBody = responseBody,
                        )
                        if (result is RedditChatWebTokenResult.Success) {
                            val expires = result.expiresAtEpochMs
                            if (
                                expires != null &&
                                nowEpochMs() < expires - TOKEN_EXPIRY_SKEW_MS
                            ) {
                                cachedToken = RedditChatCachedWebToken(
                                    token = result.token,
                                    expiresAtEpochMs = expires,
                                    redditSessionFingerprint = sessionFingerprint,
                                )
                            }
                            complete(result.copy(fresh = true))
                        } else {
                            complete(result)
                        }
                    }
                }

                @JavascriptInterface
                fun onError(nonce: String?, reason: String) {
                    if (!redditChatBridgeCallIsOwn(bridgeNonce, nonce)) return
                    @Suppress("UNUSED_VARIABLE")
                    val ignored = reason
                    handler.post { complete(RedditChatWebTokenResult.Failed(CHAT_WEB_TOKEN_SCRIPT)) }
                }
            }

            continuation.invokeOnCancellation {
                handler.post { complete(RedditChatWebTokenResult.Failed(CHAT_WEB_TOKEN_CANCELLED)) }
            }
            // A WebView that cannot be made (the provider is missing or
            // being updated) is a failed mint, not a crash of the send.
            val view = try {
                createPinnedWebView(
                    bridge = bridge,
                    onRedditPageReady = { readyView ->
                        if (!fetchStarted) {
                            fetchStarted = true
                            val csrf = redditChatCsrfCookieValue(
                                CookieManager.getInstance().getCookie(REDDIT_ORIGIN).orEmpty(),
                            )
                            readyView.evaluateJavascript(
                                redditChatTokenFetchScript(bridgeNonce, csrf),
                                null,
                            )
                        }
                    },
                )
            } catch (_: RuntimeException) {
                complete(RedditChatWebTokenResult.Failed(CHAT_WEB_TOKEN_WEBVIEW))
                return@suspendCancellableCoroutine
            }
            webView = view
            handler.postDelayed(
                {
                    complete(
                        RedditChatWebTokenResult.Failed(
                            if (fetchStarted) CHAT_WEB_TOKEN_FETCH_TIMEOUT else CHAT_WEB_TOKEN_PAGE_TIMEOUT,
                        ),
                    )
                },
                TOKEN_TIMEOUT_MS,
            )
            view.loadUrl(bootstrapUrl)
        }
    }

    /**
     * Invalidates only the token that actually failed. A stale failure can never evict a newer JWT.
     */
    override fun invalidate(expectedToken: String?) {
        val cached = cachedToken ?: return
        if (expectedToken == null || cached.token == expectedToken) {
            cachedToken = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createPinnedWebView(
        bridge: Any,
        onRedditPageReady: (WebView) -> Unit,
    ): WebView = WebView(appContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        addJavascriptInterface(bridge, BRIDGE_NAME)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = !isRedditHttps(request.url)

            @Deprecated("Required for WebView callback compatibility below API 24")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                !isRedditHttps(Uri.parse(url))

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? = if (isRedditHttps(request.url)) {
                null
            } else {
                WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    ByteArrayInputStream(ByteArray(0)),
                )
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (isRedditHttps(Uri.parse(url))) {
                    onRedditPageReady(view)
                }
            }
        }
    }

    private fun isRedditHttps(uri: Uri?): Boolean {
        if (uri == null || !uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return host == "reddit.com" || host.endsWith(".reddit.com")
    }

    companion object {
        private const val BRIDGE_NAME = "SubshellChatTokenBridge"
        private const val REDDIT_ORIGIN = "https://www.reddit.com"
        private const val TOKEN_TIMEOUT_MS = 12_000L
        internal const val TOKEN_EXPIRY_SKEW_MS = 5 * 60 * 1_000L
    }
}

/**
 * LOW_CHAT_TOKEN_EXPIRY_IN_ANY_UNIT_V1 (OCB-600): the format of the token
 * response's "expires" is not documented or verified. A number below 10^12 is
 * epoch seconds, a larger one epoch milliseconds; anything else (missing, a
 * string) falls back to the JWT's own "exp" claim (seconds). Read as
 * milliseconds unchecked, a seconds value disabled token caching and expired
 * the cached chat rooms at once.
 */
internal fun redditChatTokenExpiryEpochMs(expires: Any?, token: String): Long? {
    val numeric = when (expires) {
        is Number -> expires.toLong()
        is String -> expires.trim().toLongOrNull()
        else -> null
    }?.takeIf { it > 0L }
    if (numeric != null) {
        return if (numeric < EPOCH_MS_THRESHOLD) numeric * 1_000L else numeric
    }
    val payload = token.split('.').getOrNull(1) ?: return null
    val exp = runCatching {
        val json = String(java.util.Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
        JSONObject(json).optLong("exp", 0L)
    }.getOrNull()?.takeIf { it > 0L } ?: return null
    return if (exp < EPOCH_MS_THRESHOLD) exp * 1_000L else exp
}

private const val EPOCH_MS_THRESHOLD = 1_000_000_000_000L

/** SECAUDIT12_CHAT_BRIDGE_ANSWERS_ONLY_ITS_OWN_SCRIPT_V1 */
internal fun redditChatBridgeCallIsOwn(expectedNonce: String, receivedNonce: String?): Boolean =
    receivedNonce != null &&
        MessageDigest.isEqual(
            expectedNonce.toByteArray(Charsets.UTF_8),
            receivedNonce.toByteArray(Charsets.UTF_8),
        )

/**
 * The token fetch, run in the main frame only; [nonce] is a UUID and [csrf] a
 * plain token (letters, digits, - and _), so no quote can break out. Without
 * [csrf] the script reads the page's own cookie. The bridge gets the HTTP
 * status with the answer (ISSUE303_CHAT_TOKEN_FAILURE_NAMES_ITS_STEP_V1).
 */
internal fun redditChatTokenFetchScript(nonce: String, csrf: String? = null): String {
    require(nonce.all { it.isLetterOrDigit() || it == '-' }) { "Bridge nonce must be a UUID" }
    require(csrf == null || csrf.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
        "CSRF value must be a plain token"
    }
    return """
            (function(){
              var nonce = '$nonce';
              try {
                var csrfCookie = document.cookie.split(';')
                  .map(function(v){ return v.trim(); })
                  .find(function(v){ return v.indexOf('csrf_token=') === 0; });
                var csrf = '${csrf.orEmpty()}' ||
                  (csrfCookie ? decodeURIComponent(csrfCookie.substring('csrf_token='.length)) : '');
                fetch('/svc/shreddit/token', {
                  method: 'POST',
                  credentials: 'include',
                  headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                  },
                  body: JSON.stringify({csrf_token: csrf})
                })
                .then(function(response){
                  return response.text().then(function(body){
                    SubshellChatTokenBridge.onToken(nonce, response.status, body);
                  });
                })
                .catch(function(error){ SubshellChatTokenBridge.onError(nonce, String(error)); });
              } catch (error) {
                SubshellChatTokenBridge.onError(nonce, String(error));
              }
            })();
    """.trimIndent()
}
