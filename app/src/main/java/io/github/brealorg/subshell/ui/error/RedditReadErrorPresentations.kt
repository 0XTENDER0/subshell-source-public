package io.github.brealorg.subshell.ui.error

import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import io.github.brealorg.subshell.data.account.CustomFeedPostsFailureKind
import io.github.brealorg.subshell.data.account.RedditCommentUiFailureKind
import io.github.brealorg.subshell.data.account.RedditCommunityDiscoveryUiFailureKind
import io.github.brealorg.subshell.data.account.RedditCommunityInfoUiFailureKind
import io.github.brealorg.subshell.data.account.RedditFeedUiFailureKind
import io.github.brealorg.subshell.data.account.RedditInboxUiFailureKind
import io.github.brealorg.subshell.data.account.RedditModerationUiFailureKind
import io.github.brealorg.subshell.data.account.RedditProfileCommentsUiFailureKind
import io.github.brealorg.subshell.data.account.RedditSubscriptionsUiFailureKind
import io.github.brealorg.subshell.data.error.FailureDetails

internal fun redditFeedErrorPresentation(
    failureKind: RedditFeedUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingCachedContent: Boolean,
): ErrorPresentation = readErrorPresentation(
    title = if (showingCachedContent) UiText.Resource(R.string.error_read_couldn_t_refresh_the_feed) else UiText.Resource(R.string.error_read_couldn_t_load_the_feed),
    reason = when (failureKind) {
        RedditFeedUiFailureKind.CLIENT_CONFIGURATION ->
            UiText.Resource(R.string.error_read_reddit_feed_access_isn_t_configured_for)
        RedditFeedUiFailureKind.SESSION ->
            UiText.Resource(R.string.error_read_the_reddit_sign_in_for_this_account)
        RedditFeedUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, UiText.Resource(R.string.error_read_reddit_rejected_the_feed_request))
        RedditFeedUiFailureKind.TRANSPORT ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_reach_reddit)
        RedditFeedUiFailureKind.PROTOCOL ->
            UiText.Resource(R.string.error_read_reddit_returned_a_feed_response_that_subshell)
        RedditFeedUiFailureKind.STORAGE ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_store_the_reddit_feed)
        RedditFeedUiFailureKind.END_REACHED ->
            UiText.Resource(R.string.error_read_there_are_no_more_posts_in_this)
    },
    cachedMessage = UiText.Resource(R.string.error_read_posts_already_loaded_remain_visible),
    details = details,
    retryable = retryable && failureKind != RedditFeedUiFailureKind.END_REACHED,
    showingCachedContent = showingCachedContent,
)

internal fun redditFeedAppendErrorPresentation(
    failureKind: RedditFeedUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
): ErrorPresentation = redditFeedErrorPresentation(
    failureKind = failureKind,
    details = details,
    retryable = retryable,
    showingCachedContent = true,
).copy(title = UiText.Resource(R.string.error_read_couldn_t_load_more_posts))

// FEEDS01_CUSTOM_FEED_APPEND_FAILURE_V1: a custom feed read that failed
// while posts are already shown goes to the viewport error tray.
internal fun customFeedPostsErrorPresentation(
    failureKind: CustomFeedPostsFailureKind,
    statusCode: Int?,
    retryable: Boolean,
    append: Boolean,
    details: FailureDetails? = null,
): ErrorPresentation = readErrorPresentation(
    title = if (append) UiText.Resource(R.string.error_read_couldn_t_load_more_posts) else UiText.Resource(R.string.error_read_couldn_t_refresh_the_feed),
    reason = when (failureKind) {
        CustomFeedPostsFailureKind.CLIENT_CONFIGURATION ->
            UiText.Resource(R.string.error_read_reddit_feed_access_isn_t_configured_for)
        CustomFeedPostsFailureKind.SESSION ->
            UiText.Resource(R.string.error_read_the_reddit_sign_in_for_this_account)
        CustomFeedPostsFailureKind.HTTP ->
            if (statusCode != null && !redditReadHttpStatusHasOwnReason(statusCode)) {
                UiText.Resource(R.string.error_read_reddit_returned_http, statusCode)
            } else {
                redditReadHttpReason(statusCode, UiText.Resource(R.string.error_read_reddit_rejected_the_feed_request))
            }
        CustomFeedPostsFailureKind.TRANSPORT ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_reach_reddit)
        CustomFeedPostsFailureKind.PROTOCOL ->
            UiText.Resource(R.string.error_read_reddit_returned_a_feed_response_that_subshell)
    },
    cachedMessage = UiText.Resource(R.string.error_read_loaded_posts_remain_visible),
    details = details,
    retryable = retryable,
    showingCachedContent = true,
)

internal fun redditCommentsErrorPresentation(
    failureKind: RedditCommentUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingCachedContent: Boolean,
): ErrorPresentation = readErrorPresentation(
    title = if (showingCachedContent) UiText.Resource(R.string.error_read_couldn_t_refresh_comments) else UiText.Resource(R.string.error_read_couldn_t_load_comments),
    reason = when (failureKind) {
        RedditCommentUiFailureKind.CLIENT_CONFIGURATION ->
            UiText.Resource(R.string.error_read_reddit_comment_access_isn_t_configured_for)
        RedditCommentUiFailureKind.SESSION ->
            UiText.Resource(R.string.error_read_the_reddit_sign_in_for_this_account)
        RedditCommentUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, UiText.Resource(R.string.error_read_reddit_rejected_the_comments_request))
        RedditCommentUiFailureKind.TRANSPORT ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_reach_reddit_for_comments)
        RedditCommentUiFailureKind.PROTOCOL ->
            UiText.Resource(R.string.error_read_reddit_returned_a_comments_response_that_subshell)
        RedditCommentUiFailureKind.STORAGE ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_read_or_store_the)
    },
    cachedMessage = UiText.Resource(R.string.error_read_saved_comments_remain_visible),
    details = details,
    retryable = retryable,
    showingCachedContent = showingCachedContent,
)

internal fun redditInboxErrorPresentation(
    failureKind: RedditInboxUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingCachedContent: Boolean,
): ErrorPresentation = readErrorPresentation(
    title = if (showingCachedContent) UiText.Resource(R.string.error_read_couldn_t_refresh_inbox) else UiText.Resource(R.string.error_read_couldn_t_load_inbox),
    reason = when (failureKind) {
        RedditInboxUiFailureKind.CLIENT_CONFIGURATION ->
            UiText.Resource(R.string.error_read_reddit_inbox_access_isn_t_configured_for)
        RedditInboxUiFailureKind.SESSION ->
            UiText.Resource(R.string.error_read_the_reddit_sign_in_for_this_account_2)
        RedditInboxUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, UiText.Resource(R.string.error_read_reddit_rejected_the_inbox_request))
        RedditInboxUiFailureKind.TRANSPORT ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_reach_reddit_for_inbox)
        RedditInboxUiFailureKind.PROTOCOL ->
            UiText.Resource(R.string.error_read_reddit_returned_an_inbox_response_that_subshell)
        RedditInboxUiFailureKind.STORAGE ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_read_or_store_the_2)
    },
    cachedMessage = UiText.Resource(R.string.error_read_saved_inbox_items_remain_visible),
    details = details,
    retryable = retryable,
    showingCachedContent = showingCachedContent,
)

internal fun redditSubscriptionsErrorPresentation(
    failureKind: RedditSubscriptionsUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingCachedContent: Boolean,
): ErrorPresentation = readErrorPresentation(
    title = if (showingCachedContent) UiText.Resource(R.string.error_read_couldn_t_refresh_communities) else UiText.Resource(R.string.error_read_couldn_t_load_communities),
    reason = when (failureKind) {
        RedditSubscriptionsUiFailureKind.CLIENT_CONFIGURATION ->
            UiText.Resource(R.string.error_read_reddit_community_access_isn_t_configured_for)
        RedditSubscriptionsUiFailureKind.SESSION ->
            UiText.Resource(R.string.error_read_the_reddit_sign_in_for_this_account)
        RedditSubscriptionsUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, UiText.Resource(R.string.error_read_reddit_rejected_the_community_list_request))
        RedditSubscriptionsUiFailureKind.TRANSPORT ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_reach_reddit_for_the)
        RedditSubscriptionsUiFailureKind.PROTOCOL ->
            UiText.Resource(R.string.error_read_reddit_returned_a_community_list_response_that)
        RedditSubscriptionsUiFailureKind.STORAGE ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_read_or_store_the_3)
    },
    cachedMessage = UiText.Resource(R.string.error_read_your_saved_community_list_remains_visible),
    details = details,
    retryable = retryable,
    showingCachedContent = showingCachedContent,
)

internal fun redditCommunityDiscoveryErrorPresentation(
    failureKind: RedditCommunityDiscoveryUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingCachedContent: Boolean,
): ErrorPresentation = readErrorPresentation(
    title = if (showingCachedContent) UiText.Resource(R.string.error_read_couldn_t_refresh_explore) else UiText.Resource(R.string.error_read_couldn_t_load_explore),
    reason = when (failureKind) {
        RedditCommunityDiscoveryUiFailureKind.CLIENT_CONFIGURATION ->
            UiText.Resource(R.string.error_read_reddit_community_discovery_isn_t_configured_for)
        RedditCommunityDiscoveryUiFailureKind.SESSION ->
            UiText.Resource(R.string.error_read_the_reddit_sign_in_for_this_account)
        RedditCommunityDiscoveryUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, UiText.Resource(R.string.error_read_reddit_rejected_the_community_discovery_request))
        RedditCommunityDiscoveryUiFailureKind.TRANSPORT ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_reach_reddit_for_community)
        RedditCommunityDiscoveryUiFailureKind.PROTOCOL ->
            UiText.Resource(R.string.error_read_reddit_returned_a_community_response_that_subshell)
        RedditCommunityDiscoveryUiFailureKind.STORAGE ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_read_or_store_the_4)
    },
    cachedMessage = UiText.Resource(R.string.error_read_saved_explore_results_remain_visible),
    details = details,
    retryable = retryable,
    showingCachedContent = showingCachedContent,
)

internal fun redditProfileCommentsErrorPresentation(
    failureKind: RedditProfileCommentsUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingCachedContent: Boolean,
): ErrorPresentation = readErrorPresentation(
    title = if (showingCachedContent) {
        UiText.Resource(R.string.error_read_couldn_t_refresh_profile_comments)
    } else {
        UiText.Resource(R.string.error_read_couldn_t_load_profile_comments)
    },
    reason = when (failureKind) {
        RedditProfileCommentsUiFailureKind.CLIENT_CONFIGURATION ->
            UiText.Resource(R.string.error_read_reddit_profile_comments_aren_t_configured_for)
        RedditProfileCommentsUiFailureKind.SESSION ->
            UiText.Resource(R.string.error_read_the_reddit_sign_in_for_this_account_3)
        RedditProfileCommentsUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, UiText.Resource(R.string.error_read_reddit_rejected_the_profile_comments_request))
        RedditProfileCommentsUiFailureKind.TRANSPORT ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_reach_reddit_for_profile)
        RedditProfileCommentsUiFailureKind.PROTOCOL ->
            UiText.Resource(R.string.error_read_reddit_returned_a_profile_comments_response_that)
        RedditProfileCommentsUiFailureKind.STORAGE ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_read_or_store_the_5)
    },
    cachedMessage = UiText.Resource(R.string.error_read_saved_profile_comments_remain_visible),
    details = details,
    retryable = retryable,
    showingCachedContent = showingCachedContent,
)

internal fun redditCommunityDiscoverySuggestionErrorPresentation(
    failureKind: RedditCommunityDiscoveryUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingFallbackResults: Boolean,
): ErrorPresentation {
    val reason = when (failureKind) {
        RedditCommunityDiscoveryUiFailureKind.CLIENT_CONFIGURATION ->
            UiText.Resource(R.string.error_read_reddit_community_discovery_isn_t_configured_for)
        RedditCommunityDiscoveryUiFailureKind.SESSION ->
            UiText.Resource(R.string.error_read_the_reddit_sign_in_for_this_account)
        RedditCommunityDiscoveryUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, UiText.Resource(R.string.error_read_reddit_rejected_the_community_discovery_request))
        RedditCommunityDiscoveryUiFailureKind.TRANSPORT ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_reach_reddit_for_community_2)
        RedditCommunityDiscoveryUiFailureKind.PROTOCOL ->
            UiText.Resource(R.string.error_read_reddit_returned_community_suggestions_that_subshell_couldn)
        RedditCommunityDiscoveryUiFailureKind.STORAGE ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_read_the_saved_community)
    }.let { text ->
        // HOSTILEREDDIT01_EVERY_READ_ERROR_NAMES_THE_BLOCK_PAGE_V1: as readErrorPresentation.
        if (details.isRedditBlockPage()) REDDIT_BLOCK_PAGE_MESSAGE else text
    }
    return ErrorPresentation(
        kind = if (showingFallbackResults) {
            ErrorPresentationKind.DEGRADED
        } else {
            ErrorPresentationKind.INLINE
        },
        title = if (showingFallbackResults) {
            UiText.Resource(R.string.error_read_broader_search_unavailable)
        } else {
            UiText.Resource(R.string.error_read_couldn_t_update_suggestions)
        },
        message = if (showingFallbackResults) {
            UiText.Resource(R.string.error_read_quick_suggestions_remain_visible, reason)
        } else {
            reason
        },
        primaryAction = if (retryable) {
            ErrorAction(ErrorActionKind.RETRY, UiText.Resource(R.string.error_read_retry))
        } else {
            null
        },
    ).withFailureDetails(details)
}

// ISSUE208_SERVER_FAILURES_ARE_NOT_REJECTIONS_V1: a 5xx is a problem on
// Reddit's side and a 429 its request limit; only other statuses are a
// rejection of the request.
internal val REDDIT_READ_SERVER_PROBLEM_REASON: UiText =
    UiText.Resource(R.string.error_read_reddit_had_a_server_problem_try_again)
internal val REDDIT_READ_RATE_LIMIT_REASON: UiText =
    UiText.Resource(R.string.error_read_reddit_s_request_limit_was_reached)
internal val REDDIT_READ_ACCESS_REFUSED_REASON: UiText =
    UiText.Resource(R.string.error_read_reddit_has_shut_out_this_app_s)

internal fun redditReadHttpStatusHasOwnReason(statusCode: Int?): Boolean =
    statusCode == 429 || statusCode in 500..599

internal fun redditReadHttpReason(statusCode: Int?, rejected: UiText): UiText = when {
    statusCode == 429 -> REDDIT_READ_RATE_LIMIT_REASON
    statusCode != null && statusCode in 500..599 -> REDDIT_READ_SERVER_PROBLEM_REASON
    else -> rejected
}

private fun readErrorPresentation(
    title: UiText,
    reason: UiText,
    cachedMessage: UiText,
    details: FailureDetails?,
    retryable: Boolean,
    showingCachedContent: Boolean,
): ErrorPresentation = ErrorPresentation(
    kind = if (showingCachedContent) {
        ErrorPresentationKind.DEGRADED
    } else {
        ErrorPresentationKind.BLOCKING
    },
    title = title,
    // ISSUE32_REDDIT_REFUSED_ACCESS_IS_ONE_NOTICE_V1: an empty screen says why.
    message = when {
        details.isClientAccessRefusal() -> REDDIT_READ_ACCESS_REFUSED_REASON
        // REDDITBLOCK01_REDDITS_BLOCK_PAGE_IS_NAMED_V1
        details.isRedditBlockPage() && showingCachedContent -> UiText.Resource(R.string.error_join_sentences, REDDIT_BLOCK_PAGE_MESSAGE, cachedMessage)
        details.isRedditBlockPage() -> REDDIT_BLOCK_PAGE_MESSAGE
        showingCachedContent -> UiText.Resource(R.string.error_join_sentences, reason, cachedMessage)
        else -> reason
    },
    primaryAction = if (retryable && !details.isClientAccessRefusal()) {
        ErrorAction(
            kind = ErrorActionKind.RETRY,
            label = UiText.Resource(R.string.error_read_retry),
        )
    } else {
        null
    },
).withFailureDetails(details).let { presentation ->
    // ISSUE32: the reason is the message; a provider-code line says nothing more.
    if (details.isClientAccessRefusal()) presentation.copy(technicalDetail = null) else presentation
}


internal fun redditCommunityInfoErrorPresentation(
    failureKind: RedditCommunityInfoUiFailureKind,
    details: FailureDetails?,
    retryable: Boolean,
    showingPartialContent: Boolean,
): ErrorPresentation = readErrorPresentation(
    title = if (showingPartialContent) {
        UiText.Resource(R.string.error_read_couldn_t_refresh_community_info)
    } else {
        UiText.Resource(R.string.error_read_couldn_t_load_community_info)
    },
    reason = when (failureKind) {
        RedditCommunityInfoUiFailureKind.CLIENT_CONFIGURATION ->
            UiText.Resource(R.string.error_read_reddit_community_information_isn_t_configured_for)
        RedditCommunityInfoUiFailureKind.SESSION ->
            UiText.Resource(R.string.error_read_the_reddit_sign_in_for_this_account)
        RedditCommunityInfoUiFailureKind.HTTP ->
            redditReadHttpReason(details?.httpStatusCode, UiText.Resource(R.string.error_read_reddit_rejected_the_community_information_request))
        RedditCommunityInfoUiFailureKind.TRANSPORT ->
            UiText.Resource(R.string.error_read_subshell_couldn_t_reach_reddit_for_community_3)
        RedditCommunityInfoUiFailureKind.PROTOCOL ->
            UiText.Resource(R.string.error_read_reddit_returned_community_information_that_subshell_couldn)
    },
    cachedMessage = UiText.Resource(R.string.error_read_community_details_already_loaded_remain_visible),
    details = details,
    retryable = retryable,
    showingCachedContent = showingPartialContent,
)

// BLOCKPAGE02_SMALLER_READ_SURFACES_NAME_THE_BLOCK_PAGE_V1 (2026-10-31): the
// reads with their own short texts (mod queue, mod log, Wiki, people search,
// a replies row) say Reddit is blocking the request when it answered with its
// block page, as the shared read errors do. The block page is a 403, so it is
// checked before the mod queue's "your moderator role" reading of a 403.

internal fun redditModQueueFailureMessage(
    failureKind: RedditModerationUiFailureKind,
    httpStatusCode: Int?,
    details: FailureDetails?,
): UiText = when {
    details.isRedditBlockPage() -> REDDIT_BLOCK_PAGE_MESSAGE
    failureKind == RedditModerationUiFailureKind.HTTP && httpStatusCode == 403 ->
        UiText.Resource(R.string.error_read_reddit_denied_this_mod_queue_your_moderator)
    failureKind == RedditModerationUiFailureKind.SESSION ->
        UiText.Resource(R.string.error_read_the_reddit_sign_in_for_this_account)
    // ISSUE208_SERVER_FAILURES_ARE_NOT_REJECTIONS_V1
    failureKind == RedditModerationUiFailureKind.HTTP ->
        redditReadHttpReason(httpStatusCode, UiText.Resource(R.string.error_read_reddit_rejected_the_mod_queue_request))
    failureKind == RedditModerationUiFailureKind.TRANSPORT ->
        UiText.Resource(R.string.error_read_subshell_couldn_t_reach_reddit_for_the_2)
    failureKind == RedditModerationUiFailureKind.PROTOCOL ->
        UiText.Resource(R.string.error_read_reddit_returned_a_mod_queue_response_that)
    else -> UiText.Resource(R.string.error_read_moderator_tools_aren_t_configured_for_the)
}

internal fun redditModQueueMoreFailedLabel(details: FailureDetails?): UiText =
    if (details.isRedditBlockPage()) UiText.Resource(R.string.error_read_reddit_is_blocking_the_rest_of_the) else UiText.Resource(R.string.error_read_couldn_t_load_more_of_the_queue)

/** [lead] says what was not loaded; the status follows, or the block page instead. */
internal fun redditModeratorLogFailureMessage(
    lead: UiText,
    httpStatusCode: Int?,
    details: FailureDetails?,
): UiText = when {
    details.isRedditBlockPage() -> REDDIT_BLOCK_PAGE_MESSAGE
    httpStatusCode != null -> UiText.Resource(R.string.error_read_http, lead, httpStatusCode)
    else -> lead
}

internal fun redditWikiHttpFailureMessage(httpStatusCode: Int?, details: FailureDetails?): UiText = when {
    details.isRedditBlockPage() -> REDDIT_BLOCK_PAGE_MESSAGE
    httpStatusCode != null -> UiText.Resource(R.string.error_read_reddit_returned_http_for_this_wiki_request, httpStatusCode)
    else -> UiText.Resource(R.string.error_read_reddit_rejected_this_wiki_request)
}

internal fun redditPeopleSearchFailureMessage(details: FailureDetails?): UiText =
    if (details.isRedditBlockPage()) {
        UiText.Resource(R.string.error_read_submit_the_search_again_to_retry, REDDIT_BLOCK_PAGE_MESSAGE)
    } else {
        UiText.Resource(R.string.error_read_couldn_t_load_reddit_people_submit_the)
    }

internal fun redditMoreRepliesFailedLabel(details: FailureDetails?): UiText =
    if (details.isRedditBlockPage()) UiText.Resource(R.string.error_read_reddit_is_blocking_these_replies_retry) else UiText.Resource(R.string.error_read_couldn_t_load_replies_retry)
