package io.github.brealorg.subshell.ui

import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.data.account.PostFollowUiState
import io.github.brealorg.subshell.data.auth.AuthCapabilityAuthorizationOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class FollowPost01A1BStatePolicyTest {
    @Test
    fun unknownServerStateStaysUnknownButOffersAbsoluteFollow() {
        assertNull(PostFollowUiState().followed)
        assertTrue(postFollowDesiredState(null))
        assertEquals(
            "Follow post",
            postFollowActionLabel(null).s,
        )
    }

    @Test
    fun acknowledgedFollowedStateOffersAbsoluteUnfollow() {
        assertFalse(postFollowDesiredState(true))
        assertEquals(
            "Unfollow post",
            postFollowActionLabel(true).s,
        )
        assertEquals(
            "Follow post",
            postFollowActionLabel(false).s,
        )
    }

    @Test
    fun controlSerializesDifferentTargetsButAllowsSameTargetLastIntent() {
        assertTrue(
            postFollowTargetControlEnabled(
                activeAccountIsAuthenticated = true,
                inFlightFullname = null,
                targetFullname = "t3_alpha",
            ),
        )
        assertTrue(
            postFollowTargetControlEnabled(
                activeAccountIsAuthenticated = true,
                inFlightFullname = "t3_alpha",
                targetFullname = "t3_alpha",
            ),
        )
        assertFalse(
            postFollowTargetControlEnabled(
                activeAccountIsAuthenticated = true,
                inFlightFullname = "t3_other",
                targetFullname = "t3_alpha",
            ),
        )
    }

    @Test
    fun successDispatchesOnlyQueuedStateThatStillDiffers() {
        assertEquals(
            false,
            queuedPostFollowFollowUp(
                acknowledgedFollowed = true,
                queuedDesiredFollowed = false,
            ),
        )
        assertNull(
            queuedPostFollowFollowUp(
                acknowledgedFollowed = true,
                queuedDesiredFollowed = true,
            ),
        )
    }

    @Test
    fun postFollowHasDedicatedPersistedAuthorizationOwner() {
        val owner =
            AuthCapabilityAuthorizationOwner.POST_FOLLOW

        assertEquals(
            "post_follow",
            owner.wireKey,
        )
        assertEquals(
            owner,
            AuthCapabilityAuthorizationOwner.fromWireKey(
                owner.wireKey,
            ),
        )
    }
}
