package io.github.brealorg.subshell.ui

import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A11y01A2ExpandedMainShellPolicyTest {
    @Test
    fun expandedMainShellStartsAtMaterialExpandedWidth() {
        // ISSUE247: the edge is Google's expanded window size class.
        fun expanded(widthDp: Int) = useExpandedMainLayout(
            WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp.toFloat(), 800f),
        )
        assertFalse(expanded(0))
        assertFalse(expanded(839))
        assertTrue(expanded(840))
        assertTrue(expanded(1200))
    }

    @Test
    fun railKeepsCompactSubsVisualLabelAndFullDestinationIdentity() {
        // #224 (adapt-08): the rail names the destination as the bottom bar does.
        assertEquals(io.github.brealorg.subshell.R.string.nav_communities, navigationRailVisualLabel(MainDestination.SUBS))
        assertEquals(io.github.brealorg.subshell.R.string.nav_home, navigationRailVisualLabel(MainDestination.HOME))
        assertEquals(io.github.brealorg.subshell.R.string.nav_inbox, navigationRailVisualLabel(MainDestination.INBOX))
        assertEquals(io.github.brealorg.subshell.R.string.nav_you, navigationRailVisualLabel(MainDestination.YOU))
    }

    /** SubshellApp plus the composables it extracts into under ui/app, and its layout policy (#213). */
}
