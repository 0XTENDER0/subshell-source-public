package io.github.brealorg.subshell.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.ui.SubshellState

/**
 * ISSUE301_QUEUE_CARD_SHOWS_WHY_AND_WHAT_V1 (#301, u/vpsj): the picture on a mod
 * queue card, so a low-effort or inappropriate picture can be removed from the
 * queue. It is the feed's thumbnail (PostThumbnail): sensitive media follows the
 * reader's setting and a tap opens the media viewer.
 */
@Composable
internal fun ModeratorQueueItemThumbnail(
    item: RedditModeratorQueueItem,
    state: SubshellState,
    modifier: Modifier = Modifier,
) {
    val post = remember(item) { moderatorQueueThumbnailPost(item) } ?: return
    PostThumbnail(post = post, state = state, modifier = modifier)
}

/** The post the thumbnail shows: the post itself, or a picture post made of a comment's first picture. */
internal fun moderatorQueueThumbnailPost(item: RedditModeratorQueueItem): Post? {
    item.details.post?.let { post -> return post.takeIf { postThumbnailUri(it) != null } }
    val image = item.details.commentImageUrl ?: return null
    return Post(
        id = item.fullname,
        community = item.communityName,
        author = item.author,
        age = item.details.ageLabel.orEmpty(),
        title = item.summary,
        score = 0,
        comments = 0,
        kind = if (image.substringBefore('?').endsWith(".gif", ignoreCase = true)) PostKind.GIF else PostKind.IMAGE,
        mediaLabel = "Picture in a comment by u/${item.author}",
        remoteImageUri = image,
        permalink = item.permalink,
    )
}
