package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.R
// FOLLOWPOST01A1B_LAST_INTENT_POLICY_V1
internal fun postFollowTargetControlEnabled(
    activeAccountIsAuthenticated: Boolean,
    inFlightFullname: String?,
    targetFullname: String,
): Boolean =
    activeAccountIsAuthenticated &&
        targetFullname.isNotBlank() &&
        (
            inFlightFullname == null ||
                inFlightFullname == targetFullname
            )

internal fun queuedPostFollowFollowUp(
    acknowledgedFollowed: Boolean,
    queuedDesiredFollowed: Boolean?,
): Boolean? =
    queuedDesiredFollowed
        ?.takeIf { desired ->
            desired != acknowledgedFollowed
        }

internal fun postFollowDesiredState(
    currentKnownState: Boolean?,
): Boolean =
    currentKnownState != true

internal fun postFollowActionLabel(
    currentKnownState: Boolean?,
): UiText =
    if (currentKnownState == true) {
        UiText.Resource(R.string.post_unfollow)
    } else {
        UiText.Resource(R.string.post_follow)
    }
