package io.github.brealorg.subshell.ui.screens

import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.ui.label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// TRANSLATE08: reads string resources (enum labels), so it needs Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Modmail01A1InboxPolicyTest {
    @Test
    fun modSurfaceHasQueueAndModmailSecondaryDestinations() {
        assertEquals(listOf("Queue", "Modmail"), InboxModSection.entries.map { it.label })
        assertEquals("Modmail for 1 community", modmailCommunityCountLabel(1))
        assertEquals("Modmail for 3 communities", modmailCommunityCountLabel(3))
    }

}
