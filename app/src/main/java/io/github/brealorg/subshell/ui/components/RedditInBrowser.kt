package io.github.brealorg.subshell.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

private const val CUSTOM_TABS_SESSION_EXTRA =
    "android.support.customtabs.extra.SESSION"
private const val CUSTOM_TABS_SEND_TO_EXTERNAL_HANDLER_EXTRA =
    "android.support.customtabs.extra.SEND_TO_EXTERNAL_HANDLER"
private const val CUSTOM_TABS_INITIAL_NAVIGATION_CAN_LEAVE_BROWSER_EXTRA =
    "androidx.browser.customtabs.extra.INITIAL_NAVIGATION_CAN_LEAVE_BROWSER"

/**
 * WIKI03_REDDIT_PAGES_STAY_IN_THE_BROWSER_V1: Reddit's own site in the browser.
 * Subshell handles reddit.com links itself, and a browser that opens links in
 * apps (Firefox on the Pixel 11, 2026-11-16) handed the page straight back to
 * Subshell. So the default browser is named and asked for a Custom Tab whose
 * first navigation may not leave the browser (as the poll button always did).
 * Null when no default browser is set.
 */
internal fun redditPageBrowserIntent(context: Context, url: String): Intent? {
    val browserProbe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    val browser = context.packageManager
        .resolveActivity(browserProbe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo
        ?.packageName
        ?.takeUnless { packageName -> packageName == "android" }
        ?: return null
    val customTabsSession = Bundle().apply { putBinder(CUSTOM_TABS_SESSION_EXTRA, null) }
    return Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setPackage(browser)
        .putExtra(CUSTOM_TABS_SEND_TO_EXTERNAL_HANDLER_EXTRA, false)
        .putExtra(CUSTOM_TABS_INITIAL_NAVIGATION_CAN_LEAVE_BROWSER_EXTRA, false)
        .putExtras(customTabsSession)
}

/**
 * WIKI03_SUBSHELL_STEPS_ASIDE_WHILE_THE_BROWSER_OPENS_V1: Firefox ignores the
 * Custom Tab flag and, set to open links in apps, handed reddit.com/r/... straight
 * back to Subshell (Pixel 11, 2026-11-16). So Subshell's link activity is switched
 * off while the browser has the page.
 *
 * ISSUE308_REDDIT_LINKS_STAY_OFF_UNTIL_THE_READER_RETURNS_V1 (#308): a poll's
 * /poll/<id> page sends the browser on to /r/.../comments/<id> at once, and a
 * sign-in or a vote in the tab can move it on again long after a few seconds
 * (Pixel 11, Firefox "Always", 2026-11-17: handed back within 100 ms). The links
 * stay off until Subshell is in front again ([redditLinksBackOnReturn], called
 * from MainActivity.onResume), at the latest after [REDDIT_LINKS_OFF_MILLIS],
 * and every process start switches them on as well ([restoreRedditLinkHandling]).
 * Returns false when no app could take the page.
 */
internal fun openRedditPageInBrowser(context: Context, url: String): Boolean {
    val app = context.applicationContext
    val intent = (redditPageBrowserIntent(context, url) ?: Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    setRedditLinkHandling(app, handled = false)
    redditLinksSteppedAside.set(true)
    val started = runCatching { context.startActivity(intent) }.isSuccess
    val handler = Handler(Looper.getMainLooper())
    handler.removeCallbacks(redditLinksBackstop)
    if (!started) {
        restoreRedditLinkHandling(app)
        return false
    }
    redditLinksBackstopContext = app
    handler.postDelayed(redditLinksBackstop, REDDIT_LINKS_OFF_MILLIS)
    return true
}

/** Called when Subshell's activity is in front again: the reader has left the browser. */
fun redditLinksBackOnReturn(context: Context) {
    if (redditLinksSteppedAside.get()) restoreRedditLinkHandling(context)
}

/** Called at every process start, on return and by the backstop. */
fun restoreRedditLinkHandling(context: Context) {
    redditLinksSteppedAside.set(false)
    setRedditLinkHandling(context.applicationContext, handled = true)
}

/** The backstop: a reader who never comes back to Subshell gets Reddit links back after this. */
internal const val REDDIT_LINKS_OFF_MILLIS = 10 * 60_000L

private val redditLinksSteppedAside = AtomicBoolean(false)

@Volatile
private var redditLinksBackstopContext: Context? = null

private val redditLinksBackstop = Runnable {
    redditLinksBackstopContext?.let { app -> if (redditLinksSteppedAside.get()) restoreRedditLinkHandling(app) }
}

internal fun redditLinkComponent(context: Context): ComponentName =
    ComponentName(context.packageName, "io.github.brealorg.subshell.ExternalLaunchActivity")

private fun setRedditLinkHandling(context: Context, handled: Boolean) {
    val state = if (handled) {
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    } else {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
    runCatching {
        val packageManager = context.packageManager
        val component = redditLinkComponent(context)
        if (packageManager.getComponentEnabledSetting(component) != state) {
            packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
}
