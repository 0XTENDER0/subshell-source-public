package io.github.brealorg.subshell.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * TRANSLATE02_UITEXT_V1 (#299): text that is decided outside Compose (state,
 * ViewModels, data, notifications) without holding a [Context]. It is
 * resolved where it is shown: [asString] in Compose, [resolve] where a
 * Context exists (notifications, widgets). The shape follows Google's
 * architecture guidance for UI text.
 *
 * [Plain] is for text that is not ours to translate (a username, a subreddit
 * name, a message Reddit wrote). Our own wording is always a [Resource]
 * or a [Plural] for a count with a noun. Arguments may be [UiText] themselves; they are resolved first.
 */
sealed interface UiText {
    data class Plain(val value: String) : UiText

    class Resource(@StringRes val id: Int, vararg val args: Any) : UiText {
        override fun equals(other: Any?): Boolean =
            other is Resource && id == other.id && args.contentEquals(other.args)

        override fun hashCode(): Int = 31 * id + args.contentHashCode()

        override fun toString(): String = "Resource($id, ${args.contentToString()})"
    }

    /**
     * A count with a noun: the plural rules belong to the language, so the text
     * is a `<plurals>` resource. [count] picks the form; [args] fill the
     * format arguments (pass the count again as `%1$d` when the text shows it).
     */
    class Plural(@PluralsRes val id: Int, val count: Int, vararg val args: Any) : UiText {
        override fun equals(other: Any?): Boolean =
            other is Plural && id == other.id && count == other.count && args.contentEquals(other.args)

        override fun hashCode(): Int = 31 * (31 * id + count) + args.contentHashCode()

        override fun toString(): String = "Plural($id, $count, ${args.contentToString()})"
    }

    fun resolve(context: Context): String = when (this) {
        is Plain -> value
        is Resource -> context.getString(id, *resolveArgs(args, context))
        is Plural -> context.resources.getQuantityString(id, count, *resolveArgs(args, context))
    }

    @Composable
    fun asString(): String = resolve(LocalContext.current)
}

private fun resolveArgs(args: Array<out Any>, context: Context): Array<Any> =
    Array(args.size) { index ->
        val arg = args[index]
        if (arg is UiText) arg.resolve(context) else arg
    }
