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
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            moderatorQueueItemHeldExplanation(item, held)?.let { explanation ->
                Text(
                    explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            moderatorQueueItemRemovalLine(item)?.let { line ->
                Text(
                    line,
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
            ModeratorMetadataPill(if (item.kind == RedditModeratorQueueKind.POST) "Post" else "Comment")
            if (held) {
                ModeratorMetadataPill(HELD_FOR_REVIEW_LABEL, emphasized = true)
            } else if (showsReportCount || (item.reportDetails.totalCount ?: 0) > 0) {
                ModeratorMetadataPill(moderatorReportCountLabel(item), emphasized = true)
            }
            if (item.reportDetails.ignored == true) ModeratorMetadataPill("Reports ignored")
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

// ISSUE301_QUEUE_CARD_SHOWS_WHY_AND_WHAT_V1
internal const val HELD_FOR_REVIEW_LABEL = "Held for review"

/**
 * Reddit's queue API says nothing about why an unreported item is held (checked
 * 2026-11-13 against a post Reddit's post guidance sent to the queue: every field
 * equals an ordinary post's); reddit.com shows it from data apps do not get.
 * AutoModerator is named on its own (ISSUE301B_AUTOMOD_HELD_IS_NAMED_V1).
 */
internal const val HELD_WITHOUT_REPORTS_EXPLANATION =
    "Held by Reddit's filters or post guidance. Reddit doesn't tell apps which."

/** ISSUE301B_AUTOMOD_HELD_IS_NAMED_V1: AutoModerator held it; its rule is not sent to apps. */
internal const val HELD_BY_AUTOMODERATOR_EXPLANATION =
    "Held by AutoModerator. Reddit doesn't tell apps which rule."

/** Why the item waits: AutoModerator wherever the card is shown, otherwise the unnamed filter for an unreported queue item. */
internal fun moderatorQueueItemHeldExplanation(item: RedditModeratorQueueItem, heldWithoutReports: Boolean): String? =
    when {
        item.details.heldByAutoModerator -> HELD_BY_AUTOMODERATOR_EXPLANATION
        heldWithoutReports -> HELD_WITHOUT_REPORTS_EXPLANATION
        else -> null
    }

/** In the mod queue without a report: held by a filter, not reported. */
internal fun moderatorQueueItemIsHeldWithoutReports(item: RedditModeratorQueueItem): Boolean =
    item.reportDetails.totalCount == 0 &&
        !item.reportDetails.incomplete &&
        item.reportDetails.userReports.isNullOrEmpty() &&
        item.reportDetails.moderatorReports.isNullOrEmpty()

/** r/community · u/author · age · flair */
internal fun moderatorQueueItemByline(item: RedditModeratorQueueItem): String =
    listOfNotNull(
        "r/${item.communityName}",
        "u/${item.author}" + (item.details.authorFlair?.let { flair -> " ($flair)" } ?: ""),
        item.details.ageLabel,
        item.details.postFlair,
    ).joinToString(" · ")

/** One line per report reason: user reports with their count, moderator and AutoModerator reports with who made them. */
internal fun moderatorQueueItemReasons(item: RedditModeratorQueueItem): List<String> {
    val user = item.reportDetails.userReports.orEmpty().map { report ->
        val count = report.count?.takeIf { it > 1 }?.let { " ($it)" } ?: ""
        "Reported: ${report.reason}$count"
    }
    val mod = item.reportDetails.moderatorReports.orEmpty().map { report ->
        val by = report.moderatorName?.let { name -> if (name.equals("AutoModerator", ignoreCase = true)) "AutoModerator" else "u/$name" }
        if (by == null) "Moderator report: ${report.reason}" else "$by: ${report.reason}"
    }
    return (mod + user).take(MAX_REASON_LINES)
}

/** Removed by u/x · Reason (the Removed list). */
internal fun moderatorQueueItemRemovalLine(item: RedditModeratorQueueItem): String? {
    val by = item.details.removedBy?.let { name ->
        if (name.equals("AutoModerator", ignoreCase = true)) "AutoModerator" else "u/$name"
    }
    val reason = item.details.removalReasonTitle
    return when {
        by != null && reason != null -> "Removed by $by · $reason"
        by != null -> "Removed by $by"
        reason != null -> "Removal reason: $reason"
        else -> null
    }
}

internal fun moderatorQueueItemHasPicture(item: RedditModeratorQueueItem): Boolean =
    item.details.commentImageUrl != null ||
        item.details.post?.let { post -> postThumbnailUri(post) != null } == true

private const val MAX_REASON_LINES = 4
