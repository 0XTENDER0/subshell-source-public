package io.github.brealorg.subshell.ui.error

import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import io.github.brealorg.subshell.data.error.FailureDetails

// ISSUE211_FORBIDDEN_COMMUNITY_SAYS_WHY_V1 (#211): a community feed that
// Reddit refuses with 403 carries Reddit's reason (read by the HTTP client).
// The owner's r/armatureporn showed "Reddit rejected the feed request" while
// Reddit said "private"; the notice now says what applies. Retry does not
// help a private or Premium-only community, so neither offers it. A
// quarantined one offers "View anyway", which opts in only on that tap.

internal enum class CommunityForbiddenReason { PRIVATE, QUARANTINED, PREMIUM_ONLY, BANNED }

internal fun communityForbiddenReason(details: FailureDetails?): CommunityForbiddenReason? {
    val reason = details?.providerCode?.value ?: return null
    return when (details.httpStatusCode) {
        403 -> when (reason) {
            "private" -> CommunityForbiddenReason.PRIVATE
            "quarantined" -> CommunityForbiddenReason.QUARANTINED
            "gold_only", "gold_restricted", "premium_only" -> CommunityForbiddenReason.PREMIUM_ONLY
            else -> null
        }
        // Reddit answers a banned community with 404 and reason "banned".
        404 -> CommunityForbiddenReason.BANNED.takeIf { reason == "banned" }
        else -> null
    }
}

internal fun communityForbiddenPresentation(
    reason: CommunityForbiddenReason,
    communityLabel: String,
    showingCachedContent: Boolean,
): ErrorPresentation = ErrorPresentation(
    kind = if (showingCachedContent) ErrorPresentationKind.DEGRADED else ErrorPresentationKind.BLOCKING,
    title = when (reason) {
        CommunityForbiddenReason.PRIVATE -> UiText.Resource(R.string.error_forbidden_is_private, communityLabel)
        CommunityForbiddenReason.QUARANTINED -> UiText.Resource(R.string.error_forbidden_is_quarantined, communityLabel)
        CommunityForbiddenReason.PREMIUM_ONLY -> UiText.Resource(R.string.error_forbidden_is_for_reddit_premium_members, communityLabel)
        CommunityForbiddenReason.BANNED -> UiText.Resource(R.string.error_forbidden_is_banned, communityLabel)
    },
    message = when (reason) {
        CommunityForbiddenReason.PRIVATE ->
            UiText.Resource(R.string.error_forbidden_only_members_the_moderators_have_approved_can)
        CommunityForbiddenReason.QUARANTINED ->
            UiText.Resource(R.string.error_forbidden_reddit_has_quarantined_this_community_for_content)
        CommunityForbiddenReason.PREMIUM_ONLY ->
            UiText.Resource(R.string.error_forbidden_only_reddit_premium_members_can_see_its)
        CommunityForbiddenReason.BANNED ->
            UiText.Resource(R.string.error_forbidden_reddit_has_banned_this_community_so_its)
    },
    primaryAction = if (reason == CommunityForbiddenReason.QUARANTINED) {
        // The tray's and the notice's one action button; it runs the opt-in.
        ErrorAction(ErrorActionKind.RETRY, UiText.Resource(R.string.error_forbidden_view_anyway))
    } else {
        null
    },
)

/**
 * ISSUE211_QUARANTINE_OPT_IN_RESULT_IS_SHOWN_V1: what "View anyway" says when
 * the opt-in did not go through. Null when it was sent or its answer was lost:
 * the reload that follows shows whether it took.
 */
internal fun quarantineOptInPresentation(
    result: io.github.brealorg.subshell.data.account.QuarantineOptInResult,
    communityLabel: String,
): ErrorPresentation? = when (result) {
    io.github.brealorg.subshell.data.account.QuarantineOptInResult.Sent,
    io.github.brealorg.subshell.data.account.QuarantineOptInResult.OutcomeUnknown,
    -> null
    io.github.brealorg.subshell.data.account.QuarantineOptInResult.MissingPermission -> ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = UiText.Resource(R.string.error_forbidden_couldn_t_open, communityLabel),
        message = UiText.Resource(R.string.error_forbidden_reddit_asks_for_permission_to_manage_your),
    )
    is io.github.brealorg.subshell.data.account.QuarantineOptInResult.Refused -> ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = UiText.Resource(R.string.error_forbidden_couldn_t_open, communityLabel),
        // Reddit's source refuses an account without a verified email with 403.
        message = if (result.httpStatusCode == 403) {
            UiText.Resource(R.string.error_forbidden_reddit_shows_quarantined_communities_only_to_accounts)
        } else {
            UiText.Resource(R.string.error_forbidden_reddit_didn_t_accept_the_request_to)
        },
    )
}
