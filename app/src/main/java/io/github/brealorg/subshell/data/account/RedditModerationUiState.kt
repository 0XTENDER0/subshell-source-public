package io.github.brealorg.subshell.data.account

import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.normalizeRedditCommunityName
import io.github.brealorg.subshell.model.Post

enum class RedditModerationUiFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
}

/**
 * ISSUE295_REMOVED_CONTENT_LISTS_V1 (#295 part 3): the moderator lists Mod tools
 * shows for one community. [path] is Reddit's /r/<name>/about/<path> listing;
 * "spam" holds what Reddit's filters and moderators removed.
 */
enum class RedditModeratorQueueList(@androidx.annotation.StringRes val labelRes: Int, val path: String) {
    QUEUE( io.github.brealorg.subshell.R.string.reddit_moderator_queue_list_queue, "modqueue"),
    REMOVED( io.github.brealorg.subshell.R.string.reddit_moderator_queue_list_removed, "spam"),
    EDITED( io.github.brealorg.subshell.R.string.reddit_moderator_queue_list_edited, "edited"),
    UNMODERATED( io.github.brealorg.subshell.R.string.reddit_moderator_queue_list_unmoderated, "unmoderated"),
}

enum class RedditModeratorQueueKind {
    POST,
    COMMENT,
}

data class RedditModeratorQueueItem(
    val fullname: String,
    val kind: RedditModeratorQueueKind,
    val communityName: String,
    val author: String,
    val summary: String,
    val reportCount: Int = 0,
    val permalink: String? = null,
    val reportDetails: RedditModeratorReportDetails = RedditModeratorReportDetails(),
    val contentState: RedditModeratorContentState = RedditModeratorContentState(),
    // ISSUE301_QUEUE_CARD_SHOWS_WHY_AND_WHAT_V1
    val details: RedditModeratorQueueItemDetails = RedditModeratorQueueItemDetails(),
) {
    init {
        require(fullname.matches(Regex("t[13]_[a-z0-9]+")))
        require(communityName.isNotBlank())
        require(author.isNotBlank())
        require(summary.isNotBlank())
        require(reportCount >= 0)
    }
}

/**
 * ISSUE301_QUEUE_CARD_SHOWS_WHY_AND_WHAT_V1 (#301, u/vpsj): what a queue card shows
 * besides the title, so a moderator can decide from the card. Moderator-only,
 * in-memory presentation data, like the report details.
 */
data class RedditModeratorQueueItemDetails(
    /** A post as the feed reads it (picture, gallery, video, flair), or null for a comment. */
    val post: Post? = null,
    /** The first picture in a comment, or null. */
    val commentImageUrl: String? = null,
    /** "3h", "2d" … */
    val ageLabel: String? = null,
    /** The first lines of a post's text. */
    val snippet: String? = null,
    val postFlair: String? = null,
    val authorFlair: String? = null,
    /** The title of the post a comment was written on. */
    val linkTitle: String? = null,
    /** The removal reason a moderator picked (Removed list). */
    val removalReasonTitle: String? = null,
    /** Who removed it, as Reddit names them (Removed list). */
    val removedBy: String? = null,
    /**
     * ISSUE301B_AUTOMOD_HELD_IS_NAMED_V1: AutoModerator filtered it into the queue
     * (`removed_by_category = automod_filtered`, or `banned_by = AutoModerator`
     * while `removed = false`); not removed. The rule's reason is in the mod log
     * ([heldReason]).
     */
    val heldByAutoModerator: Boolean = false,
    /**
     * ISSUE301C_REDDIT_FILTER_HELD_IS_NAMED_V1: Reddit's own filter (abuse and
     * harassment, spam, …) held it: `banned_by = true` while `removed = false`
     * (u/vpsj's comment in r/indianrailways, 2026-11-15).
     */
    val heldByRedditFilter: Boolean = false,
    /** When it was posted (`created_utc`): the mod log is read back to here (ISSUE301D). */
    val createdEpochSeconds: Long? = null,
    /** ISSUE301C_A_FILTER_REASON_COMES_FROM_THE_MOD_LOG_V1: why the filter held it, or null. */
    val heldReason: RedditModeratorHeldReason? = null,
)

/** A filter's own words from the mod log, and whether AutoModerator (not Reddit) wrote them. */
data class RedditModeratorHeldReason(
    val byAutoModerator: Boolean,
    val reason: String,
)

// MOD01A22_EXACT_MODERATION_TARGET_V1
data class RedditModeratorQueueTarget(
    val postId: String,
    val commentFullname: String? = null,
) {
    init {
        require(postId.matches(Regex("[a-z0-9]+")))
        require(
            commentFullname == null ||
                commentFullname.matches(Regex("t1_[a-z0-9]+")),
        )
    }
}

internal fun RedditModeratorQueueItem.canonicalModerationTarget():
    RedditModeratorQueueTarget? {
    return when (kind) {
        RedditModeratorQueueKind.POST -> {
            val postId = fullname
                .removePrefix("t3_")
                .takeIf { candidate -> candidate.matches(THING_ID_REGEX) }
                ?: return null
            RedditModeratorQueueTarget(postId = postId)
        }

        RedditModeratorQueueKind.COMMENT -> {
            val postId = permalink
                ?.let(MODERATION_PERMALINK_REGEX::find)
                ?.groupValues
                ?.getOrNull(1)
                ?.lowercase()
                ?.takeIf { candidate -> candidate.matches(THING_ID_REGEX) }
                ?: return null
            RedditModeratorQueueTarget(
                postId = postId,
                commentFullname = fullname,
            )
        }
    }
}

// MOD01A1_ACCOUNT_SCOPED_MODERATOR_DIRECTORY_AND_QUEUE_V1
// MOD01A22_CONDITIONAL_INBOX_MOD_TAB_UNIFIED_QUEUE_V1
data class RedditModerationUiState(
    val accountStableId: String? = null,
    val moderatedCommunities: Set<String> = emptySet(),
    val directoryLoading: Boolean = false,
    val directoryLoaded: Boolean = false,
    val directoryFailureKind: RedditModerationUiFailureKind? = null,
    val directoryFailureRetryable: Boolean = false,
    val directoryFailureDetails: FailureDetails? = null,
    val directoryFailureHttpStatusCode: Int? = null,
    val queueCommunityName: String? = null,
    val queueItems: List<RedditModeratorQueueItem> = emptyList(),
    val queueLoading: Boolean = false,
    val queueLoaded: Boolean = false,
    val queueNextCursor: String? = null,
    val queueFailureKind: RedditModerationUiFailureKind? = null,
    val queueFailureRetryable: Boolean = false,
    val queueFailureDetails: FailureDetails? = null,
    val queueFailureHttpStatusCode: Int? = null,
    val contentRevision: Long = 0L,
    // ISSUE295_REMOVED_CONTENT_LISTS_V1: which list the queue fields hold.
    val queueList: RedditModeratorQueueList = RedditModeratorQueueList.QUEUE,
) {
    init {
        require(accountStableId == null || accountStableId.isNotBlank())
        require(queueCommunityName == null || queueCommunityName.isNotBlank())
        require(directoryFailureHttpStatusCode == null || directoryFailureHttpStatusCode in 100..599)
        require(queueFailureHttpStatusCode == null || queueFailureHttpStatusCode in 100..599)
        require(contentRevision >= 0L)
    }

    fun matchesAccount(accountStableId: String): Boolean =
        this.accountStableId == accountStableId

    fun moderates(rawCommunityName: String): Boolean {
        val normalized = runCatching {
            normalizeRedditCommunityName(rawCommunityName)
        }.getOrNull() ?: return false
        return normalized in moderatedCommunities
    }

    fun queueMatches(accountStableId: String, rawCommunityName: String): Boolean {
        val normalized = runCatching {
            normalizeRedditCommunityName(rawCommunityName)
        }.getOrNull() ?: return false
        return this.accountStableId == accountStableId &&
            queueCommunityName == normalized
    }

    fun aggregateQueueMatches(accountStableId: String): Boolean =
        queueMatches(accountStableId, REDDIT_MODERATION_AGGREGATE_QUEUE)

    fun queueContainsContext(
        accountStableId: String,
        rawCommunityName: String,
    ): Boolean {
        val normalized = runCatching {
            normalizeRedditCommunityName(rawCommunityName)
        }.getOrNull() ?: return false
        return this.accountStableId == accountStableId &&
            (
                queueCommunityName == normalized ||
                    queueCommunityName == REDDIT_MODERATION_AGGREGATE_QUEUE
                )
    }
}

internal const val REDDIT_MODERATION_AGGREGATE_QUEUE = "mod"
private val THING_ID_REGEX = Regex("[a-z0-9]+")
private val MODERATION_PERMALINK_REGEX = Regex(
    pattern = "(?:^|/)comments/([a-z0-9]+)(?:/[^/?#]*)?(?:/[a-z0-9]+)?(?:[/?#]|$)",
    option = RegexOption.IGNORE_CASE,
)
