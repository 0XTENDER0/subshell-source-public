package io.github.brealorg.subshell.ui.components

import androidx.compose.ui.res.pluralStringResource
import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.ui.ListThumbnailSide
import io.github.brealorg.subshell.ui.PostLayout
import io.github.brealorg.subshell.ui.SensitiveMediaPreviewMode
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.externalPostUrl
import io.github.brealorg.subshell.ui.symbols.Symbols

/**
 * ISSUE234_FEED_POST_LAYOUTS_V1 (owner 2026-10-18): Compact, List and Grid. They
 * share PostCard's state, swipe actions, menu and dialogs; only the drawing
 * differs. No vote buttons (owner): voting and saving go through swipe actions,
 * the menu and TalkBack's custom actions. Media is a still picture here; only
 * Large plays video and animates GIFs.
 */
@Composable
internal fun FeedPostLayoutContent(
    post: Post,
    state: SubshellState,
    layout: PostLayout,
    vote: Int,
    saved: Boolean,
    read: Boolean,
    onOpen: () -> Unit,
    onComments: () -> Unit,
    onOpenCommunity: () -> Unit,
    overflowMenu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = feedPostAccessibilityActions(post, state, vote, saved, onComments)
    val openModifier = Modifier
        .clickable(onClickLabel = stringResource(R.string.feed_open_post), role = Role.Button, onClick = onOpen)
        .semantics(mergeDescendants = true) { customActions = actions }
    when (layout) {
        PostLayout.COMPACT -> CompactPostCard(post, state, vote, saved, read, onOpenCommunity, overflowMenu, modifier, openModifier)
        PostLayout.LIST -> ListPostRow(post, state, vote, saved, read, overflowMenu, modifier, openModifier)
        PostLayout.GRID -> GridPostTile(post, state, read, overflowMenu, modifier, openModifier)
        PostLayout.LARGE -> Unit
    }
}

@Composable
private fun feedPostAccessibilityActions(
    post: Post,
    state: SubshellState,
    vote: Int,
    saved: Boolean,
    onComments: () -> Unit,
): List<CustomAccessibilityAction> = buildList {
    if (state.postVoteControlEnabled(post)) {
        add(CustomAccessibilityAction(stringResource(if (vote == 1) R.string.feed_action_remove_upvote else R.string.feed_action_upvote)) { state.vote(post, 1); true })
        add(CustomAccessibilityAction(stringResource(if (vote == -1) R.string.feed_action_remove_downvote else R.string.feed_action_downvote)) { state.vote(post, -1); true })
    }
    if (state.postSaveControlEnabled(post)) {
        add(CustomAccessibilityAction(stringResource(if (saved) R.string.feed_action_unsave else R.string.feed_action_save)) { state.toggleSaved(post); true })
    }
    add(CustomAccessibilityAction(stringResource(R.string.feed_action_comments)) { onComments(); true })
}

@Composable
private fun CompactPostCard(
    post: Post,
    state: SubshellState,
    vote: Int,
    saved: Boolean,
    read: Boolean,
    onOpenCommunity: () -> Unit,
    overflowMenu: @Composable () -> Unit,
    modifier: Modifier,
    openModifier: Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(openModifier.padding(start = 12.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClickLabel = stringResource(R.string.feed_open_community), role = Role.Button, onClick = onOpenCommunity),
                ) {
                    PostMetaLine(post, state, read)
                }
                overflowMenu()
            }
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp),
            )
            if (postThumbnailAvailable(post)) {
                PostThumbnail(
                    post = post,
                    state = state,
                    modifier = Modifier
                        .padding(top = 8.dp, end = 12.dp)
                        .fillMaxWidth()
                        .height(CompactMediaHeight)
                        .clip(MaterialTheme.shapes.small),
                )
            }
            PostStatsLine(post, state, vote, saved, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ListPostRow(
    post: Post,
    state: SubshellState,
    vote: Int,
    saved: Boolean,
    read: Boolean,
    overflowMenu: @Composable () -> Unit,
    modifier: Modifier,
    openModifier: Modifier,
) {
    val thumbnailLeft = state.listThumbnailSide == ListThumbnailSide.LEFT
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = openModifier
                .heightIn(min = 72.dp)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hasThumbnail = postThumbnailAvailable(post, linkTile = true)
            val thumbnail: @Composable () -> Unit = {
                if (hasThumbnail) {
                    PostThumbnail(
                        post = post,
                        state = state,
                        modifier = Modifier
                            .size(ListThumbnailSize)
                            .clip(MaterialTheme.shapes.small),
                    )
                }
            }
            if (thumbnailLeft && hasThumbnail) {
                thumbnail()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                PostMetaLine(post, state, read)
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                PostStatsLine(post, state, vote, saved, Modifier.padding(top = 4.dp))
            }
            if (!thumbnailLeft && hasThumbnail) {
                Spacer(Modifier.width(12.dp))
                thumbnail()
            }
            Box(Modifier.align(Alignment.Top)) { overflowMenu() }
        }
    }
}

@Composable
private fun GridPostTile(
    post: Post,
    state: SubshellState,
    read: Boolean,
    overflowMenu: @Composable () -> Unit,
    modifier: Modifier,
    openModifier: Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(openModifier) {
            // Every tile has the same picture shape and a two-line title, so a
            // row's tiles line up (owner chose even rows).
            if (postThumbnailAvailable(post)) {
                PostThumbnail(post = post, state = state, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            } else {
                val accent = state.communityFor(post.community)?.let { Color(it.accent) } ?: accentFor(post.community)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(accent.copy(alpha = 0.16f))
                        .padding(12.dp),
                ) {
                    Text(
                        text = post.body.ifBlank { post.title },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = post.title,
                style = MaterialTheme.typography.labelLarge,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 10.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        text = "${state.subredditLabel(post.community)} · ${compactNumber(state.effectivePostScore(post))}" +
                            if (shouldShowUnreadMarker(state.showUnreadMarker, read)) " · new" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                overflowMenu()
            }
        }
    }
}

/** Community, age, unread dot and the labels a reader must see before opening. */
@Composable
private fun PostMetaLine(post: Post, state: SubshellState, read: Boolean) {
    val identity = state.communityFor(post.community)
    val accent = identity?.let { Color(it.accent) } ?: accentFor(post.community)
    Row(
        modifier = Modifier.heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteIdentityImage(
            uri = identity?.iconImageUri,
            contentDescription = null,
            modifier = Modifier.size(20.dp).clip(CircleShape),
        ) {
            CommunityAvatar(name = post.community, accent = accent, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.feed_text, state.subredditLabel(post.community), post.age),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (shouldShowUnreadMarker(state.showUnreadMarker, read)) {
            Spacer(Modifier.width(6.dp))
            val unreadDescription = stringResource(R.string.feed_unread_post)
            Box(
                Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .semantics { contentDescription = unreadDescription },
            )
        }
        postLabels(post, showRemoved = postShowsRemoved(post, LocalModeratorQuickActions.current)).forEach { label ->
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (label == "NSFW" || label == "REMOVED") {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                maxLines = 1,
            )
        }
    }
}

internal fun postLabels(post: Post, showRemoved: Boolean = false): List<String> = buildList {
    // ISSUE295_REMOVED_POST_IS_MARKED_V1
    if (showRemoved) add("REMOVED")
    if (post.pinned) add("PINNED")
    if (post.moderatorPost) add("MOD")
    if (post.spoiler) add("SPOILER")
    if (post.nsfw) add("NSFW")
}

@Composable
private fun PostStatsLine(post: Post, state: SubshellState, vote: Int, saved: Boolean, modifier: Modifier = Modifier) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            imageVector = if (vote == -1) Symbols.ArrowDownward else Symbols.ArrowUpward,
            contentDescription = when (vote) {
                1 -> stringResource(R.string.all_upvoted)
                -1 -> stringResource(R.string.all_downvoted)
                else -> null
            },
            modifier = Modifier.size(16.dp),
            tint = when (vote) {
                1 -> MaterialTheme.colorScheme.primary
                -1 -> MaterialTheme.colorScheme.tertiary
                else -> quiet
            },
        )
        val pointsDescription = pluralStringResource(
            R.plurals.points_count,
            state.effectivePostScore(post),
            state.effectivePostScore(post),
        )
        Text(
            text = compactNumber(state.effectivePostScore(post)),
            style = MaterialTheme.typography.labelMedium,
            color = quiet,
            modifier = Modifier.semantics { contentDescription = pointsDescription },
        )
        Spacer(Modifier.width(8.dp))
        Icon(Symbols.Comment, contentDescription = null, modifier = Modifier.size(16.dp), tint = quiet)
        val commentsDescription = pluralStringResource(R.plurals.comments_count, post.comments, post.comments)
        Text(
            text = compactNumber(post.comments),
            style = MaterialTheme.typography.labelMedium,
            color = quiet,
            modifier = Modifier.semantics { contentDescription = commentsDescription },
        )
        if (saved) {
            Spacer(Modifier.width(8.dp))
            Icon(Symbols.BookmarkFilled, contentDescription = stringResource(R.string.feed_saved), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
        if (post.kind == PostKind.LINK) {
            post.domain?.takeIf(String::isNotBlank)?.let { domain ->
                Spacer(Modifier.width(8.dp))
                Text(domain, style = MaterialTheme.typography.labelMedium, color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Whether a post has a picture to show. A link without a preview has none; List
 * still gives it a small link tile ([linkTile]), Compact and Grid show text.
 */
internal fun postThumbnailAvailable(post: Post, linkTile: Boolean = false): Boolean = when {
    postThumbnailUri(post) != null || (post.mediaAsset ?: post.gallery.firstOrNull()?.asset) != null -> true
    post.kind in MediaViewerKinds -> true
    post.kind == PostKind.LINK -> linkTile
    else -> false
}

internal fun postThumbnailUri(post: Post): String? =
    post.remoteImageUri?.takeIf(String::isNotBlank)
        ?: post.gallery.firstOrNull()?.remoteImageUri?.takeIf(String::isNotBlank)

/**
 * A still picture of a post. Sensitive media follows the reader's setting
 * exactly as Large does: hidden shows nothing, load-on-tap loads nothing until
 * tapped, blurred stays blurred until revealed.
 */
@Composable
internal fun PostThumbnail(post: Post, state: SubshellState, modifier: Modifier = Modifier) {
    val sensitive = (post.spoiler || post.nsfw) && post.kind != PostKind.TEXT
    // NSFWMEDIA01_SHOW_NSFW_MEDIA_SETTING_V1
    val revealed = !sensitive || post.id in state.revealedMediaIds || state.nsfwMediaShownUncovered(post)
    val mode = state.sensitiveMediaPreviewMode
    val hidden = sensitive && !revealed && mode == SensitiveMediaPreviewMode.HIDE_PREVIEW
    val coverOnly = sensitive && !revealed && mode == SensitiveMediaPreviewMode.LOAD_ON_TAP
    // LINKLABEL01_TALKBACK_READS_THE_DOMAIN_NOT_THE_ADDRESS_V1
    val label = postMediaDescription(post).asString()
    val uriHandler = LocalUriHandler.current
    val cleanLinks = LocalCleanLinks.current
    // A tap on a still opens what Large's media opens; a post kind without
    // its own media viewer leaves the tap to the row (it opens the post).
    val onClick: (() -> Unit)? = when {
        hidden -> ({ state.openMedia(post) })
        sensitive && !revealed -> ({ state.revealMedia(post) })
        post.kind == PostKind.LINK -> externalPostUrl(post, cleanLinks)?.let { url -> fun() { runCatching { uriHandler.openUri(url) } } }
        post.kind in MediaViewerKinds -> ({ state.openMedia(post) })
        else -> null
    }
    Box(
        modifier = modifier
            .testTag(POST_THUMBNAIL_TEST_TAG)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        onClickLabel = when {
                            hidden -> stringResource(R.string.all_open_hidden_media)
                            sensitive && !revealed -> stringResource(R.string.all_show_sensitive_media)
                            post.kind == PostKind.LINK -> stringResource(R.string.feed_open_link)
                            else -> stringResource(R.string.all_open_media)
                        },
                        role = Role.Button,
                        onClick = onClick,
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            hidden -> Icon(Symbols.VisibilityOff, contentDescription = stringResource(R.string.feed_sensitive_media_hidden), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            coverOnly -> Icon(Symbols.Visibility, contentDescription = stringResource(R.string.feed_sensitive_media_tap_to_load), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                val blurModifier = if (revealed) Modifier else Modifier.blur(16.dp)
                val uri = postThumbnailUri(post)
                val asset = post.mediaAsset ?: post.gallery.firstOrNull()?.asset
                when {
                    uri != null -> RemoteImage(
                        uri = uri,
                        label = label,
                        modifier = Modifier.fillMaxSize().then(blurModifier),
                        contentScale = ContentScale.Crop,
                        aspectRatio = null,
                        showFailureDetails = false,
                    )
                    asset != null -> LocalAssetStill(asset, label, Modifier.fillMaxSize().then(blurModifier))
                    else -> Unit
                }
                if (uri == null && asset == null && post.kind == PostKind.LINK) {
                    Icon(Symbols.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    thumbnailBadge(post)?.let { icon -> ThumbnailBadge(icon, Modifier.align(Alignment.Center)) }
                }
            }
        }
    }
}

private fun thumbnailBadge(post: Post): ImageVector? = when (post.kind) {
    PostKind.VIDEO, PostKind.GIF -> Symbols.PlayArrowFilled
    PostKind.GALLERY -> Symbols.PhotoLibrary
    PostKind.LINK -> Symbols.Link
    else -> null
}

@Composable
private fun ThumbnailBadge(icon: ImageVector, modifier: Modifier) {
    Surface(modifier = modifier, shape = CircleShape, color = Color.Black.copy(alpha = 0.5f), contentColor = Color.White) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(4.dp).size(18.dp))
    }
}

/** First frame of a bundled picture or GIF (sample posts), decoded once. */
@Composable
private fun LocalAssetStill(asset: io.github.brealorg.subshell.model.MediaAsset, label: String, modifier: Modifier) {
    val resource = presentationMediaResource(asset) ?: return
    val resources = LocalResources.current
    val bitmap = remember(resources, resource) { BitmapFactory.decodeResource(resources, resource)?.asImageBitmap() } ?: return
    Image(bitmap = bitmap, contentDescription = label, contentScale = ContentScale.Crop, modifier = modifier)
}

internal const val POST_THUMBNAIL_TEST_TAG = "post-thumbnail"
private val MediaViewerKinds = setOf(PostKind.IMAGE, PostKind.VIDEO, PostKind.GIF, PostKind.GALLERY)
internal val PostLayoutMenuWidth = 200.dp
private val CompactMediaHeight = 160.dp
private val ListThumbnailSize = 72.dp

/**
 * ISSUE234_LAYOUT_CHOICE_BESIDE_THE_FEED_V1 (owner 2026-10-18): the layout is
 * chosen where the feed is read, beside its sort (Drive and Files put their
 * list/grid choice in the toolbar). The icon shows the current layout. It is the
 * same setting as Settings → Feeds → Post layout.
 */
@Composable
internal fun PostLayoutMenuButton(state: SubshellState, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(postLayoutIcon(state.postLayout), contentDescription = stringResource(R.string.feed_post_layout, stringResource(state.postLayout.labelRes)))
        }
        // One width for every choice: the menu was as wide as its longest row,
        // so "Compact ✓" and List's thumbnail rows moved it sideways (owner, phone).
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(PostLayoutMenuWidth),
        ) {
            PostLayout.entries.forEach { layout ->
                DropdownMenuItem(
                    text = { Text(stringResource(layout.labelRes)) },
                    modifier = Modifier.semantics { selected = layout == state.postLayout },
                    leadingIcon = { Icon(postLayoutIcon(layout), contentDescription = null) },
                    trailingIcon = if (layout == state.postLayout) {
                        { Icon(Symbols.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        state.updatePostLayout(layout)
                        if (layout != PostLayout.LIST) expanded = false
                    },
                )
            }
            if (state.postLayout == PostLayout.LIST) {
                HorizontalDivider()
                ListThumbnailSide.entries.forEach { side ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feed_thumbnail, stringResource(side.labelRes).lowercase())) },
                        modifier = Modifier.semantics { selected = side == state.listThumbnailSide },
                        trailingIcon = if (side == state.listThumbnailSide) {
                            { Icon(Symbols.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            state.updateListThumbnailSide(side)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

internal fun postLayoutIcon(layout: PostLayout): ImageVector = when (layout) {
    PostLayout.LARGE -> Symbols.ViewAgenda
    PostLayout.COMPACT -> Symbols.ViewDay
    PostLayout.LIST -> Symbols.ViewList
    PostLayout.GRID -> Symbols.GridView
}
