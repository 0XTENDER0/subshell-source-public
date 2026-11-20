package io.github.brealorg.subshell.ui

import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// TRANSLATE14: reads string resources through UiText.s, so it needs Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Crosspost01A1E3DestinationSupportPolicyTest {
    @Test
    fun explicitFalseStillBlocks() {
        val destination = ComposerCrosspostDestination(
            accountStableId = "account-1",
            community = "subshell",
            allowCrossposts = false,
        )

        assertEquals(
            "This community does not allow crossposts",
            crosspostDestinationSupportBlockingMessage(destination)?.s,
        )
    }

    @Test
    fun explicitTrueDoesNotBlock() {
        val destination = ComposerCrosspostDestination(
            accountStableId = "account-1",
            community = "subshell",
            allowCrossposts = true,
        )

        assertNull(
            crosspostDestinationSupportBlockingMessage(destination),
        )
    }

    @Test
    fun missingLegacySignalIsUnknownNotDenied() {
        val destination = ComposerCrosspostDestination(
            accountStableId = "account-1",
            community = "subshell",
            allowCrossposts = null,
        )

        assertNull(
            crosspostDestinationSupportBlockingMessage(destination),
        )
    }
}
