package io.github.brealorg.subshell.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider

// TRANSLATE02_UITEXT_V1: tests that look at the words of an error read them
// through this property (English, the default locale of the unit tests).
// Ready text passes through without an Android context, so plain JVM tests can
// use it; a string resource needs a Robolectric test.
internal val UiText.s: String
    get() = when (this) {
        is UiText.Plain -> value
        is UiText.Resource, is UiText.Plural -> resolve(ApplicationProvider.getApplicationContext<Context>())
    }

// TRANSLATE07: the sort, source, scope and time-period enums hold string-resource
// ids; tests that ask for their English label read it here (Robolectric tests).
private fun resolvedLabel(id: Int): String =
    ApplicationProvider.getApplicationContext<Context>().getString(id)

internal val io.github.brealorg.subshell.ui.SensitiveMediaPreviewMode.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.media.VideoMediaCacheLimit.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.CommentActionPlacement.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.CommentTimeFormat.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.SubredditLabelStyle.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.ChromeBehavior.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.VideoAutoplay.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.VideoQuality.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.PlaybackOptionsPlacement.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.VideoSubtitleDefault.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.VideoAudioDefault.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.VideoTimelineSize.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.FeedPreviewState.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.InteractionSide.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.DrawerSidePreference.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.CommentActionPreference.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.NavigationLabelMode.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.RandomNsfwCommunityPlacement.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.PinnedPostsPresentation.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.CommunityMutePreset.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.CodeFont.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.FeedPositionAge.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.FeedPositionStart.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.PostLayout.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.ListThumbnailSide.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.TabletLayoutMode.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.CommunityDiscoveryLayout.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.MediaCaptionMode.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.FullscreenCaptionMode.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.CommentCollapseMode.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.CommentHierarchyStyle.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.CommentThreadColours.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.CommentIndentSize.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.DrawerCommunitiesMode.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.VoteColorPalette.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.VoteControlMode.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.PostSwipeAction.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.CommentSwipeAction.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.StartFeed.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.theme.AppUiScale.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.screens.InboxModSection.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.ui.screens.InboxPrimaryTab.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.inbox.InboxActivitySection.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.data.account.RedditModeratorQueueList.label: String get() = resolvedLabel(labelRes)
internal val FeedSort.label: String get() = resolvedLabel(labelRes)
internal val CommunitySort.label: String get() = resolvedLabel(labelRes)
internal val FeedSource.label: String get() = resolvedLabel(labelRes)
internal val SearchScope.label: String get() = resolvedLabel(labelRes)
internal val io.github.brealorg.subshell.data.RedditListingTimePeriod.label: String get() = resolvedLabel(labelRes)
