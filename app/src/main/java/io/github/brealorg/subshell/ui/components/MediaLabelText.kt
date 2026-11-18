package io.github.brealorg.subshell.ui.components

import androidx.compose.runtime.Composable
import io.github.brealorg.subshell.R
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.ui.INLINE_REDDIT_VIDEO_FALLBACK_TITLE
import io.github.brealorg.subshell.ui.INLINE_REDDIT_VIDEO_POST_ID_PREFIX
import io.github.brealorg.subshell.ui.REDDIT_IMAGE_LINK_POST_ID_PREFIX
import io.github.brealorg.subshell.ui.REDDIT_LINKED_GIF_TITLE
import io.github.brealorg.subshell.ui.REDDIT_LINKED_IMAGE_TITLE
import io.github.brealorg.subshell.ui.UiText

// TRANSLATE14_MEDIA_LABELS_ARE_SHOWN_TRANSLATED_V1: the data layer and the
// cache keep the English media codes ("Video", "GIF", "3 items", "Image 2");
// the screen shows them translated. Anything else (a caption, an address) is
// shown as stored.
private val MEDIA_ITEMS_LABEL = Regex("""^(\d+) items$""")
private val MEDIA_IMAGE_NUMBER_LABEL = Regex("""^Image (\d+)$""")
private val COMMENT_PICTURE_LABEL = Regex("""^Picture in a comment by u/(.+)$""")

internal fun mediaLabelText(stored: String): UiText {
    MEDIA_ITEMS_LABEL.matchEntire(stored)?.groupValues?.get(1)?.toIntOrNull()?.let { count ->
        return UiText.Plural(R.plurals.media_label_items, count, count)
    }
    MEDIA_IMAGE_NUMBER_LABEL.matchEntire(stored)?.groupValues?.get(1)?.toIntOrNull()?.let { number ->
        return UiText.Resource(R.string.media_label_image_number, number)
    }
    COMMENT_PICTURE_LABEL.matchEntire(stored)?.groupValues?.get(1)?.let { author ->
        return UiText.Resource(R.string.media_label_comment_picture, author)
    }
    return when (stored) {
        "Video" -> UiText.Resource(R.string.media_label_video)
        "GIF" -> UiText.Resource(R.string.media_label_gif)
        "Image" -> UiText.Resource(R.string.media_label_image)
        "Photo" -> UiText.Resource(R.string.media_label_photo)
        else -> UiText.Plain(stored)
    }
}

@Composable
internal fun mediaLabelShown(stored: String): String = mediaLabelText(stored).asString()

/** The title of a post Subshell made for a linked media file, translated; a real post's title as it is. */
internal fun mediaPostTitleText(post: Post): UiText {
    val made = post.id.startsWith(INLINE_REDDIT_VIDEO_POST_ID_PREFIX) ||
        post.id.startsWith(REDDIT_IMAGE_LINK_POST_ID_PREFIX)
    if (!made) return UiText.Plain(post.title)
    return when (post.title) {
        INLINE_REDDIT_VIDEO_FALLBACK_TITLE -> UiText.Resource(R.string.media_embedded_video)
        REDDIT_LINKED_GIF_TITLE -> UiText.Resource(R.string.media_linked_gif)
        REDDIT_LINKED_IMAGE_TITLE -> UiText.Resource(R.string.media_linked_image)
        else -> UiText.Plain(post.title)
    }
}

@Composable
internal fun mediaPostTitleShown(post: Post): String = mediaPostTitleText(post).asString()
