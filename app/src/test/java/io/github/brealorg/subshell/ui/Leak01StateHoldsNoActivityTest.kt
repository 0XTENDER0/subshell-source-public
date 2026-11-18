package io.github.brealorg.subshell.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.FakeSubshellRepository
import io.github.brealorg.subshell.data.account.AccountSessionDirectory
import io.github.brealorg.subshell.data.account.AccountSessionDirectorySnapshot
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import java.lang.reflect.Modifier
import java.util.ArrayDeque
import java.util.IdentityHashMap
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LEAK01_STATE_HOLDS_NO_ACTIVITY_V1: SubshellState outlives its activity
 * (#245 keeps it in the ViewModelStore across a recreation), so nothing in it
 * may hold the context it was built with. LeakCanary on the Pixel 11 found
 * FeedPositionFile's lazy file lambda holding a destroyed MainActivity
 * (through UiScaleContext). The context here stands in for that activity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Leak01StateHoldsNoActivityTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:reader",
        username = "reader",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private class Accounts(private val accounts: List<AccountSessionSummary>) : AccountSessionDirectory {
        var activeAccountId = accounts.first().stableId
        override fun snapshot() = AccountSessionDirectorySnapshot(accounts, activeAccountId)
        override fun selectAccount(stableId: String) = snapshot().also { activeAccountId = stableId }
    }

    private class Store : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    /** Stands in for the activity's context (MainActivity wrapped by UiScaleContext). */
    private class ActivityLikeContext(base: Context) : ContextWrapper(base)

    @Test
    fun theStateDoesNotHoldTheContextItWasBuiltWith() {
        val activityContext = ActivityLikeContext(ApplicationProvider.getApplicationContext())
        var state: SubshellState? = null
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides activityContext,
                LocalSubshellStateStoreOwner provides Store(),
            ) {
                state = rememberSubshellState(
                    repository = FakeSubshellRepository(),
                    accountSessionDirectory = Accounts(listOf(account)),
                )
            }
        }
        composeRule.waitForIdle()

        assertNull(
            "SubshellState reaches the activity's context",
            pathTo(activityContext, from = requireNotNull(state)),
        )
    }

    /** The field path from [from] to [target] through object fields, or null. */
    private fun pathTo(target: Any, from: Any, limit: Int = 400_000): String? {
        val seen = IdentityHashMap<Any, String>()
        val queue = ArrayDeque<Any>()
        seen[from] = from.javaClass.simpleName
        queue.add(from)
        while (queue.isNotEmpty() && seen.size < limit) {
            val node: Any = queue.removeFirst()
            val path = seen.getValue(node)
            for ((name, value) in children(node)) {
                if (value === target) return "$path.$name"
                if (value in seen || !walkable(value)) continue
                seen[value] = "$path.$name"
                queue.add(value)
            }
        }
        return null
    }

    private fun walkable(value: Any): Boolean {
        val name = value.javaClass.name
        return !value.javaClass.isPrimitive && value !is String && value !is Number && value !is Class<*> &&
            !name.startsWith("java.") && !name.startsWith("javax.") && !name.startsWith("jdk.") &&
            !name.startsWith("sun.") && !name.startsWith("android.") && !name.startsWith("com.android.") &&
            !name.startsWith("org.robolectric.") && !name.startsWith("dalvik.") && !name.startsWith("libcore.")
    }

    private fun children(node: Any): Sequence<Pair<String, Any>> = sequence {
        if (node is Array<*>) {
            node.forEachIndexed { index, value -> if (value != null) yield("[$index]" to value) }
            return@sequence
        }
        if (node is Iterable<*> && node.javaClass.name.startsWith("java.")) return@sequence
        var type: Class<*>? = node.javaClass
        while (type != null && type != Any::class.java) {
            for (field in type.declaredFields) {
                if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive) continue
                val value = runCatching { field.isAccessible = true; field.get(node) }.getOrNull() ?: continue
                yield(field.name to value)
                // Collections from java.util are not walked field by field; walk their elements.
                if (value is Collection<*> && value.javaClass.name.startsWith("java.")) {
                    value.toList().forEachIndexed { index, element ->
                        if (element != null) yield("${field.name}[$index]" to element)
                    }
                }
                if (value is Map<*, *> && value.javaClass.name.startsWith("java.")) {
                    value.entries.toList().forEach { (key, element) ->
                        if (element != null) yield("${field.name}[$key]" to element)
                    }
                }
            }
            type = type.superclass
        }
    }
}
