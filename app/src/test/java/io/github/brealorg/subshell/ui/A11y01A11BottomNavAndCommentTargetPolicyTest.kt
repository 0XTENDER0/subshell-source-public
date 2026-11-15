package io.github.brealorg.subshell.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class A11y01A11BottomNavAndCommentTargetPolicyTest {
    @Test
    fun communitiesUsesCompactVisualLabelOnlyAtLargeText() {
        assertEquals(io.github.brealorg.subshell.R.string.nav_communities, navigationVisualLabel(MainDestination.SUBS, 1.0f))
        assertEquals(io.github.brealorg.subshell.R.string.nav_communities, navigationVisualLabel(MainDestination.SUBS, 1.29f))
        assertEquals(io.github.brealorg.subshell.R.string.nav_subs, navigationVisualLabel(MainDestination.SUBS, 1.30f))
        assertEquals(io.github.brealorg.subshell.R.string.nav_subs, navigationVisualLabel(MainDestination.SUBS, 2.0f))
        assertEquals(io.github.brealorg.subshell.R.string.nav_home, navigationVisualLabel(MainDestination.HOME, 2.0f))
        assertEquals(io.github.brealorg.subshell.R.string.nav_inbox, navigationVisualLabel(MainDestination.INBOX, 2.0f))
        assertEquals(io.github.brealorg.subshell.R.string.nav_you, navigationVisualLabel(MainDestination.YOU, 2.0f))
    }
}
