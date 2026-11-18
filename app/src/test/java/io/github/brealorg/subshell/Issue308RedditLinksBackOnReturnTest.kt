package io.github.brealorg.subshell

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.auth.AuthRuntimePreparation
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.ui.components.openRedditPageInBrowser
import io.github.brealorg.subshell.ui.components.redditLinkComponent
import io.github.brealorg.subshell.ui.components.restoreRedditLinkHandling
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * ISSUE308_REDDIT_LINKS_STAY_OFF_UNTIL_THE_READER_RETURNS_V1 (#308): with Firefox
 * set to open links in apps, a poll's page came straight back to Subshell
 * (/poll/<id> -> /comments/<id>) and the tab closed. Subshell's Reddit links stay
 * off while the browser has the page and come back when MainActivity is in front
 * again. (MainActivity starts on 36, see SecAudit10RecentsCardTest.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue308RedditLinksBackOnReturnTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun prepare() {
        ensureWorkManagerInitialized(application)
        AuthRuntimePreparation.clearForTests()
        runBlocking { runCatching { AuthRuntimePreparation.prepare(application).await() } }
        val browser = ComponentName("com.example.browser", "com.example.browser.Main")
        val packageManager = shadowOf(application.packageManager)
        packageManager.addActivityIfNotPresent(browser)
        packageManager.addIntentFilterForActivity(
            browser,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("https")
            },
        )
    }

    @After
    fun forgetTheAuthRuntime() {
        restoreRedditLinkHandling(application)
        AuthRuntimePreparation.clearForTests()
    }

    private fun linkState() =
        application.packageManager.getComponentEnabledSetting(redditLinkComponent(application))

    @Test
    fun redditLinksComeBackWhenSubshellIsInFrontAgain() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        assertTrue(openRedditPageInBrowser(controller.get(), "https://www.reddit.com/poll/abc123"))
        controller.pause()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, linkState())

        controller.resume()
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, linkState())
        controller.pause().stop().destroy()
    }
}
