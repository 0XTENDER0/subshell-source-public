package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.ui.MutedCommunityDirectViewMode
import io.github.brealorg.subshell.ui.components.MutedCommunityDirectViewLivePreview
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

internal enum class FeedsSettingsPreviewFocus {
    UNREAD,
    NSFW,
    MUTED_COMMUNITY,
}

internal fun feedsSettingsPreviewFocusAfterChange(
    currentFocus: FeedsSettingsPreviewFocus,
    previousShowUnreadMarker: Boolean,
    previousShowNsfwPosts: Boolean,
    previousMutedCommunityDirectViewMode: MutedCommunityDirectViewMode,
    showUnreadMarker: Boolean,
    showNsfwPosts: Boolean,
    mutedCommunityDirectViewMode: MutedCommunityDirectViewMode,
): FeedsSettingsPreviewFocus = when {
    mutedCommunityDirectViewMode != previousMutedCommunityDirectViewMode ->
        FeedsSettingsPreviewFocus.MUTED_COMMUNITY
    showNsfwPosts != previousShowNsfwPosts ->
        FeedsSettingsPreviewFocus.NSFW
    showUnreadMarker != previousShowUnreadMarker ->
        FeedsSettingsPreviewFocus.UNREAD
    else -> currentFocus
}

private data class FeedsSettingsPreviewSnapshot(
    val focus: FeedsSettingsPreviewFocus,
    val showUnreadMarker: Boolean,
    val showNsfwPosts: Boolean,
    val mutedCommunityDirectViewMode: MutedCommunityDirectViewMode,
)

@Composable
private fun feedsSettingsPreviewStatusLabel(
    snapshot: FeedsSettingsPreviewSnapshot,
): String = when (snapshot.focus) {
    FeedsSettingsPreviewFocus.UNREAD ->
        if (snapshot.showUnreadMarker) stringResource(R.string.all_unread_on) else stringResource(R.string.all_unread_off)
    FeedsSettingsPreviewFocus.NSFW ->
        if (snapshot.showNsfwPosts) stringResource(R.string.scr_nsfw_shown) else stringResource(R.string.scr_nsfw_hidden)
    FeedsSettingsPreviewFocus.MUTED_COMMUNITY ->
        stringResource(snapshot.mutedCommunityDirectViewMode.labelRes)
}

@Composable
internal fun FeedsSettingsPreview(
    showUnreadMarker: Boolean,
    showNsfwPosts: Boolean,
    mutedCommunityDirectViewMode: MutedCommunityDirectViewMode,
    modifier: Modifier = Modifier,
) {
    val previewFocus = remember {
        mutableStateOf(FeedsSettingsPreviewFocus.UNREAD)
    }
    val previousShowUnreadMarker = remember {
        mutableStateOf(showUnreadMarker)
    }
    val previousShowNsfwPosts = remember {
        mutableStateOf(showNsfwPosts)
    }
    val previousMutedCommunityDirectViewMode = remember {
        mutableStateOf(mutedCommunityDirectViewMode)
    }

    LaunchedEffect(
        showUnreadMarker,
        showNsfwPosts,
        mutedCommunityDirectViewMode,
    ) {
        previewFocus.value = feedsSettingsPreviewFocusAfterChange(
            currentFocus = previewFocus.value,
            previousShowUnreadMarker = previousShowUnreadMarker.value,
            previousShowNsfwPosts = previousShowNsfwPosts.value,
            previousMutedCommunityDirectViewMode =
                previousMutedCommunityDirectViewMode.value,
            showUnreadMarker = showUnreadMarker,
            showNsfwPosts = showNsfwPosts,
            mutedCommunityDirectViewMode = mutedCommunityDirectViewMode,
        )
        previousShowUnreadMarker.value = showUnreadMarker
        previousShowNsfwPosts.value = showNsfwPosts
        previousMutedCommunityDirectViewMode.value =
            mutedCommunityDirectViewMode
    }

    val snapshot = FeedsSettingsPreviewSnapshot(
        focus = previewFocus.value,
        showUnreadMarker = showUnreadMarker,
        showNsfwPosts = showNsfwPosts,
        mutedCommunityDirectViewMode = mutedCommunityDirectViewMode,
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        stringResource(R.string.all_live_preview),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.all_feed_and_community_behaviour),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.width(10.dp))

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        feedsSettingsPreviewStatusLabel(snapshot),
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 6.dp,
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }

            Crossfade(
                targetState = snapshot,
                animationSpec = tween(180),
                label = "feeds-settings-rich-context-preview",
            ) { target ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (target.focus) {
                        FeedsSettingsPreviewFocus.UNREAD -> {
                            FeedsPreviewHomeCanvas(
                                showUnreadMarker =
                                    target.showUnreadMarker,
                                nsfwMode = false,
                                showNsfwPosts =
                                    target.showNsfwPosts,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        FeedsSettingsPreviewFocus.NSFW -> {
                            FeedsPreviewHomeCanvas(
                                showUnreadMarker =
                                    target.showUnreadMarker,
                                nsfwMode = true,
                                showNsfwPosts =
                                    target.showNsfwPosts,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        FeedsSettingsPreviewFocus.MUTED_COMMUNITY -> {
                            MutedCommunityDirectViewLivePreview(
                                mode =
                                    target.mutedCommunityDirectViewMode,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(176.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedsPreviewHomeCanvas(
    showUnreadMarker: Boolean,
    nsfwMode: Boolean,
    showNsfwPosts: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "☰   Home",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    if (nsfwMode) stringResource(R.string.scr_nsfw_visibility) else stringResource(R.string.all_unread_marker),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (nsfwMode) {
                if (showNsfwPosts) {
                    FeedPreviewCanvasPost(
                        community = "r/pics",
                        age = "2h",
                        title = stringResource(R.string.set_behind_the_scenes_photo_from_the_set),
                        unread = false,
                        nsfw = true,
                    )
                } else {
                    FeedPreviewSuppressedPost()
                }

                FeedPreviewCanvasPost(
                    community = "r/gaming",
                    age = "5h",
                    title = stringResource(R.string.set_screenshot_saturday),
                    unread = false,
                    nsfw = false,
                )
            } else {
                FeedPreviewCanvasPost(
                    community = "r/android",
                    age = "2h",
                    title = stringResource(R.string.set_a_new_android_release_lands_this_week),
                    unread = showUnreadMarker,
                    nsfw = false,
                )
                FeedPreviewCanvasPost(
                    community = "r/space",
                    age = "4h",
                    title = stringResource(R.string.set_nasa_shares_new_images_from_deep_space),
                    unread = false,
                    nsfw = false,
                )
            }
        }
    }
}

@Composable
private fun FeedPreviewCanvasPost(
    community: String,
    age: String,
    title: String,
    unread: Boolean,
    nsfw: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    community,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Text(
                    "· $age",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (unread) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                CircleShape,
                            ),
                    )
                }
                if (nsfw) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            "NSFW",
                            modifier = Modifier.padding(
                                horizontal = 6.dp,
                                vertical = 1.dp,
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color =
                                MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FeedPreviewSuppressedPost() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.scr_nsfw_post_hidden),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                stringResource(R.string.all_hidden_posts_are_removed_from_the_feed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
