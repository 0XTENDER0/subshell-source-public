package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.account.moderatorReportCountLabel
import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.ui.symbols.Symbols

// MODERATION02A2R3_SHARED_EXPRESSIVE_REVIEW_PRIMITIVES_V1
// Presentation only: these components cannot issue reads or moderation writes.
@Composable
internal fun ModeratorQueueItemSummary(
    item: RedditModeratorQueueItem,
    inProgress: Boolean,
    modifier: Modifier = Modifier,
    // ISSUE295_REMOVED_CONTENT_LISTS_V1: Removed, Edited and Unmoderated are not
    // about reports; their cards say nothing about reports unless there are some.
    showsReportCount: Boolean = true,
    // ISSUE301_QUEUE_CARD_SHOWS_WHY_AND_WHAT_V1: the post's picture or the
    // comment's first picture, drawn by the caller (it follows the reader's
    // sensitive-media setting and opens the media viewer, as in the feed).
    thumbnail: (@Composable (Modifier) -> Unit)? = null,
) {
    val details = item.details
    val held = showsReportCount && moderatorQueueItemIsHeldWithoutReports(item)
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (thumbnail != null && moderatorQueueItemHasPicture(item)) {
            thumbnail(Modifier.size(64.dp).clip(MaterialTheme.shapes.medium))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.summary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                moderatorQueueItemByline(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            details.linkTitle?.let { title ->
                Text(
                    stringResource(R.string.all_on, title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            details.snippet?.let { snippet ->
                Text(
                    snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            moderatorQueueItemReasons(item).forEach { reason ->
                Text(
                    reason.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            moderatorQueueItemHeldExplanation(item, held)?.let { explanation ->
                Text(
                    explanation.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            moderatorQueueItemRemovalLine(item)?.let { line ->
                Text(
                    line.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            modifier = Modifier.widthIn(max = 112.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (inProgress) {
                InlineProgressIndicator()
            }
            ModeratorMetadataPill(if (item.kind == RedditModeratorQueueKind.POST) stringResource(R.string.comp_pill_post) else stringResource(R.string.comp_pill_comment))
            if (held) {
                ModeratorMetadataPill(stringResource(R.string.comp_held_for_review), emphasized = true)
            } else if (showsReportCount || (item.reportDetails.totalCount ?: 0) > 0) {
                ModeratorMetadataPill(moderatorReportCountLabel(item), emphasized = true)
            }
            if (item.reportDetails.ignored == true) ModeratorMetadataPill(stringResource(R.string.comp_reports_ignored))
        }
    }
}

@Composable
internal fun ModeratorItemGlyph(isPost: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(36.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (isPost) Symbols.FactCheck else Symbols.Forum,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun ModeratorMetadataPill(text: String, emphasized: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (emphasized) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun ModeratorActionIcon(imageVector: ImageVector) {
    Icon(imageVector, contentDescription = null, modifier = Modifier.size(18.dp))
}

// ISSUE301_QUEUE_CARD_SHOWS_WHY_AND_WHAT_V1: "Held for review" is R.string.comp_held_for_review.

/**
 * Why the item waits (ISSUE301B_AUTOMOD_HELD_IS_NAMED_V1,
 * ISSUE301C_REDDIT_FILTER_HELD_IS_NAMED_V1): the filter's own words from the
 * mod log when there are some (ISSUE301C_A_FILTER_REASON_COMES_FROM_THE_MOD_LOG_V1),
 * else who held it. An unreported queue item with no sign on it was held by
 * Reddit's filters or post guidance (post guidance leaves no field, checked
 * 2026-11-13). Nothing is claimed about what Reddit tells apps.
 */
internal fun moderatorQueueItemHeldExplanation(item: RedditModeratorQueueItem, heldWithoutReports: Boolean): UiText? {
    val details = item.details
    details.heldReason?.let { held ->
        return UiText.Resource(
            if (held.byAutoModerator) R.string.comp_held_by_automoderator_reason else R.string.comp_held_by_reddit_reason,
            held.reason,
        )
    }
    return when {
        details.heldByAutoModerator -> UiText.Resource(R.string.comp_held_by_automoderator)
        details.heldByRedditFilter -> UiText.Resource(R.string.comp_held_by_reddit)
        // ISSUE301E_A_NAMED_REMOVER_IS_NOT_CALLED_A_FILTER_V1: an item a moderator
        // or a moderator app removed into the queue (u/vpsj's monitor-test-app,
        // 2026-11-16) says "Removed by …", not "Held by Reddit's filters".
        heldWithoutReports && details.removedBy == null ->
            UiText.Resource(R.string.comp_held_by_filters_or_guidance)
        else -> null
    }
}

/** In the mod queue without a report: held by a filter, not reported. */
internal fun moderatorQueueItemIsHeldWithoutReports(item: RedditModeratorQueueItem): Boolean =
    item.reportDetails.totalCount == 0 &&
        !item.reportDetails.incomplete &&
        item.reportDetails.userReports.isNullOrEmpty() &&
        item.reportDetails.moderatorReports.isNullOrEmpty()

/** r/community · age · u/author (user flair) · post flair */
// QUEUEBYLINE01_THE_AGE_COMES_BEFORE_THE_FLAIR_V1: a long user flair
// ("Platform Ticket Holder :platform_ticket:" in r/astronotyet) pushed the age
// past the two lines the card shows; the age now follows the community.
internal fun moderatorQueueItemByline(item: RedditModeratorQueueItem): String =
    listOfNotNull(
        "r/${item.communityName}",
        item.details.ageLabel,
        "u/${item.author}" + (item.details.authorFlair?.let { flair -> " ($flair)" } ?: ""),
        item.details.postFlair,
    ).joinToString(" · ")

/** One line per report reason: user reports with their count, moderator and AutoModerator reports with who made them. */
internal fun moderatorQueueItemReasons(item: RedditModeratorQueueItem): List<UiText> {
    val user = item.reportDetails.userReports.orEmpty().map { report ->
        val count = report.count?.takeIf { it > 1 }?.let { " ($it)" } ?: ""
        UiText.Resource(R.string.comp_reported_reason, report.reason + count)
    }
    val mod = item.reportDetails.moderatorReports.orEmpty().map { report ->
        val by = report.moderatorName?.let { name -> if (name.equals("AutoModerator", ignoreCase = true)) "AutoModerator" else "u/$name" }
        if (by == null) UiText.Resource(R.string.comp_moderator_report_reason, report.reason) else UiText.Plain("$by: ${report.reason}")
    }
    return (mod + user).take(MAX_REASON_LINES)
}

/** Removed by u/x · Reason (the Removed list). */
internal fun moderatorQueueItemRemovalLine(item: RedditModeratorQueueItem): UiText? {
    val by = item.details.removedBy?.let { name ->
        if (name.equals("AutoModerator", ignoreCase = true)) "AutoModerator" else "u/$name"
    }
    val reason = item.details.removalReasonTitle
    return when {
        by != null && reason != null -> UiText.Resource(R.string.comp_removed_by_reason, by, reason)
        by != null -> UiText.Resource(R.string.comp_removed_by, by)
        reason != null -> UiText.Resource(R.string.comp_removal_reason_line, reason)
        else -> null
    }
}

internal fun moderatorQueueItemHasPicture(item: RedditModeratorQueueItem): Boolean =
    item.details.commentImageUrl != null ||
        item.details.post?.let { post -> postThumbnailUri(post) != null } == true

private const val MAX_REASON_LINES = 4
