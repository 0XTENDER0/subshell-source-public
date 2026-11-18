package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import io.github.brealorg.subshell.data.account.CustomFeedSummary
import io.github.brealorg.subshell.data.account.CustomFeedVisibility
import io.github.brealorg.subshell.data.account.customFeedPathSegments
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import java.net.URI

internal fun canonicalRedditPostUrl(post: Post): String {
    val permalink = post.permalink?.trim().orEmpty()
    val permalinkScheme = permalink
        .takeIf(String::isNotBlank)
        ?.let(::webSchemeOrNull)
    return when {
        permalinkScheme != null -> permalink
        permalink.startsWith("/") -> redditWebUrl(permalink)
        else -> {
            val id = post.id.trim().lowercase().removePrefix("t3_")
            redditWebUrl("/comments/$id")
        }
    }
}

internal fun canonicalRedditCommentUrl(post: Post, commentId: String): String {
    val normalizedCommentId = commentId
        .trim()
        .lowercase()
        .removePrefix("t1_")
        .takeIf { value -> value.isNotEmpty() && value.all(Char::isLetterOrDigit) }
        ?: return canonicalRedditPostUrl(post)
    val postUri = runCatching { URI(canonicalRedditPostUrl(post)) }.getOrNull()
    val permalinkPath = postUri?.path
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.trimEnd('/')
    val postId = post.id.trim().lowercase().removePrefix("t3_")
    val commentPath = if (permalinkPath?.contains("/comments/") == true) {
        "$permalinkPath/$normalizedCommentId/"
    } else {
        "/comments/$postId/_/$normalizedCommentId/"
    }
    return redditWebUrl(commentPath)
}

internal fun externalPostUrl(post: Post, cleanLinks: Boolean = false): String? = post.externalUrl
    ?.trim()
    ?.takeIf(String::isNotBlank)
    // CLEANURL02_CLEAN_LINKS_SETTING_V1: the post keeps the original address; only what is opened is cleaned.
    ?.let { url -> if (cleanLinks) io.github.brealorg.subshell.model.UrlCleaner.clean(url) else url }
    ?.takeIf { url -> webSchemeOrNull(url) != null }

private fun webSchemeOrNull(value: String): String? = runCatching {
    URI(value).scheme?.lowercase()
}
    .getOrNull()
    ?.takeIf { it in setOf("http", "https") }

private fun redditWebUrl(path: String): String = URI(
    REDDIT_WEB_SCHEME,
    REDDIT_WEB_HOST,
    path,
    null,
).toASCIIString()

private const val REDDIT_WEB_SCHEME = "https"
private const val REDDIT_WEB_HOST = "www.reddit.com"

internal fun sharePostLink(context: Context, post: Post, notices: ViewportErrorHostState?) {
    val url = canonicalRedditPostUrl(post)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, post.title)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.sysact_share_post)))
    }.onFailure {
        notices.noticeFailure(UiText.Resource(R.string.sysact_couldn_t_share_this_post), UiText.Resource(R.string.sysact_no_app_on_this_device_can_share))
    }
}

internal fun shareCommentLink(context: Context, post: Post, commentId: String, notices: ViewportErrorHostState?) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, post.title)
        putExtra(Intent.EXTRA_TEXT, canonicalRedditCommentUrl(post, commentId))
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.sysact_share_comment)))
    }.onFailure {
        notices.noticeFailure(UiText.Resource(R.string.sysact_couldn_t_share_this_comment), UiText.Resource(R.string.sysact_no_app_on_this_device_can_share))
    }
}

// REDDITURL05_CUSTOM_FEED_LINK_V1: the web link of a custom feed. Its path
// holds the feed's URL name, which Reddit keeps when the display name is
// changed, so the link is the only place a user can see it.
internal fun canonicalRedditCustomFeedUrl(feed: CustomFeedSummary): String? =
    customFeedPathSegments(feed.path)?.let { segments -> redditWebUrl("/" + segments.joinToString("/") + "/") }

/** Only public feeds open for other people; private and hidden ones do not. */
internal fun customFeedLinkShareable(feed: CustomFeedSummary): Boolean =
    feed.visibility.equals(CustomFeedVisibility.PUBLIC.apiValue, ignoreCase = true) &&
        canonicalRedditCustomFeedUrl(feed) != null

internal fun shareCustomFeedLink(context: Context, feed: CustomFeedSummary, notices: ViewportErrorHostState?) {
    val url = canonicalRedditCustomFeedUrl(feed) ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, feed.displayName)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.sysact_share_feed)))
    }.onFailure {
        notices.noticeFailure(UiText.Resource(R.string.sysact_couldn_t_share_this_feed), UiText.Resource(R.string.sysact_no_app_on_this_device_can_share))
    }
}

internal fun copyCustomFeedLink(context: Context, feed: CustomFeedSummary, notices: ViewportErrorHostState?) {
    val url = canonicalRedditCustomFeedUrl(feed) ?: return
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    if (clipboard == null) {
        notices.noticeFailure(UiText.Resource(R.string.sysact_couldn_t_copy_the_link), UiText.Resource(R.string.sysact_the_clipboard_is_unavailable))
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ui_reddit_custom_feed_link), url))
    notices.noticeCopied(io.github.brealorg.subshell.ui.UiText.Resource(io.github.brealorg.subshell.R.string.nt_copied_what_link))
}
