package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.account.RedditModeratorHeldReason
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.RedditModeratorQueueList

/**
 * ISSUE301C_A_FILTER_REASON_COMES_FROM_THE_MOD_LOG_V1 (#301, u/vpsj): a filter
 * puts nothing about its reason on the item it holds, but it writes the reason
 * to the mod log. AutoModerator writes its rule's `action_reason` as `details`
 * (r/astronotyet, 2026-11-15); Reddit's own filters write as mod "reddit"
 * with `details` "Automatic Filter" or "Banned Domain" and the reason in
 * `description` ("Identified by the abuse and harassment filter"; u/vpsj's
 * r/indianrailways log as the API returns it, 2026-11-16). reddit.com's mod
 * log page does not list these entries; the API does.
 *
 * The log is read only when the page holds such items: AutoModerator's
 * entries for items AutoModerator held, the latest entries for the others
 * (Reddit's filters write as "reddit"). ISSUE301D_THE_LOG_IS_READ_BACK_TO_THE_OLDEST_HELD_ITEM_V1:
 * a busy community's AutoModerator fills a page of 100 in hours, so further
 * pages are read until every item has its entry, the log is older than the
 * oldest item, or [MAX_LOG_PAGES] pages were read. [readLog] returns null
 * when the read failed; the items then keep what was found.
 */
internal suspend fun moderatorQueueItemsWithFilterReasons(
    items: List<RedditModeratorQueueItem>,
    list: RedditModeratorQueueList,
    readLog: suspend (moderator: String?, after: String?) -> ModeratorLogPage?,
): List<RedditModeratorQueueItem> {
    val waiting = items.filter { item -> item.waitsForFilterReason(list) }
    if (waiting.isEmpty()) return items
    val (heldByAutoModerator, others) = waiting.partition { item -> item.details.heldByAutoModerator }
    val entries = readLogUntilCovered(AUTOMODERATOR, heldByAutoModerator, readLog) +
        readLogUntilCovered(null, others, readLog)
    if (entries.isEmpty()) return items
    val waitingNames = waiting.mapTo(HashSet()) { item -> item.fullname }
    return items.map { item ->
        if (item.fullname !in waitingNames) return@map item
        val reason = moderatorLogHeldReason(entries, item) ?: return@map item
        item.copy(details = item.details.copy(heldReason = reason))
    }
}

/** One page of the mod log as the queue needs it. */
internal data class ModeratorLogPage(
    val entries: List<RedditModerationLogDataDto>,
    val after: String?,
)

private suspend fun readLogUntilCovered(
    moderator: String?,
    items: List<RedditModeratorQueueItem>,
    readLog: suspend (moderator: String?, after: String?) -> ModeratorLogPage?,
): List<RedditModerationLogDataDto> {
    if (items.isEmpty()) return emptyList()
    val oldestItem = items.mapNotNull { item -> item.details.createdEpochSeconds }.minOrNull()
    val entries = mutableListOf<RedditModerationLogDataDto>()
    var after: String? = null
    repeat(MAX_LOG_PAGES) {
        val page = readLog(moderator, after) ?: return entries
        entries += page.entries
        if (items.all { item -> moderatorLogEntryFor(entries, item) != null }) return entries
        val oldestEntry = page.entries.mapNotNull { entry -> entry.createdUtc }.minOrNull() ?: return entries
        // Without a time on every item there is no point to read back to.
        if (oldestItem == null || items.any { item -> item.details.createdEpochSeconds == null }) return entries
        if (oldestEntry < oldestItem) return entries
        after = page.after?.trim()?.takeIf(String::isNotEmpty) ?: return entries
    }
    return entries
}

private fun RedditModeratorQueueItem.waitsForFilterReason(list: RedditModeratorQueueList): Boolean =
    details.heldByAutoModerator ||
        details.heldByRedditFilter ||
        (
            list == RedditModeratorQueueList.QUEUE &&
                // ISSUE301E: a named remover needs no filter reason.
                details.removedBy == null &&
                reportDetails.totalCount == 0 &&
                reportDetails.userReports.isNullOrEmpty() &&
                reportDetails.moderatorReports.isNullOrEmpty()
            )

/** The newest removal of this item by AutoModerator or Reddit, with its words. */
internal fun moderatorLogHeldReason(
    entries: List<RedditModerationLogDataDto>,
    item: RedditModeratorQueueItem,
): RedditModeratorHeldReason? {
    val entry = moderatorLogEntryFor(entries, item) ?: return null
    val reason = moderatorLogReasonText(entry.description, entry.details) ?: return null
    return RedditModeratorHeldReason(
        byAutoModerator = entry.mod?.trim().equals(AUTOMODERATOR, ignoreCase = true),
        reason = reason,
    )
}

private fun moderatorLogEntryFor(
    entries: List<RedditModerationLogDataDto>,
    item: RedditModeratorQueueItem,
): RedditModerationLogDataDto? =
    entries.firstOrNull { entry ->
        val moderator = entry.mod?.trim().orEmpty()
        entry.targetFullname?.trim() == item.fullname &&
            entry.action?.trim()?.lowercase() in FILTER_ACTIONS &&
            (
                moderator.equals(AUTOMODERATOR, ignoreCase = true) ||
                    (!item.details.heldByAutoModerator && moderator.equals(REDDIT, ignoreCase = true))
                )
    }

/**
 * The entry's own words, as old reddit shows them (`details : description`):
 * "Automatic Filter – Identified by the abuse and harassment filter",
 * "Ban Evasion – Higher accuracy …" (the description alone says too little,
 * ISSUE301F); AutoModerator's rule reason is the details alone. Details that
 * are only an action word ("remove", "confirm_spam") are left out.
 */
internal fun moderatorLogReasonText(description: String?, details: String?): String? {
    val what = details.cleanLogText()?.takeUnless { text -> BARE_ACTION_WORD.matches(text) }
    val why = description.cleanLogText()
    return when {
        what != null && why != null && !why.equals(what, ignoreCase = true) -> "$what – $why".take(MAX_HELD_REASON_CHARS)
        else -> why ?: what
    }
}

private fun String?.cleanLogText(): String? =
    this?.replace(Regex("\\s+"), " ")?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_HELD_REASON_CHARS)

private const val AUTOMODERATOR = "AutoModerator"
private const val REDDIT = "reddit"
private val FILTER_ACTIONS = setOf("removelink", "removecomment", "spamlink", "spamcomment")
private val BARE_ACTION_WORD = Regex("[a-z_]+")
private const val MAX_HELD_REASON_CHARS = 300
private const val MAX_LOG_PAGES = 5
