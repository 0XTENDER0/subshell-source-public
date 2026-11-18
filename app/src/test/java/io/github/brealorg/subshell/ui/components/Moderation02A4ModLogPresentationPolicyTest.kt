package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.ui.s
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(qualifiers = "en")
class Moderation02A4ModLogPresentationPolicyTest {
    @Test
    fun commonActionLabelsAreProductFacing() {
        assertTrue(moderationLogActionLabel("approvelink").s == "Approved post")
        assertTrue(moderationLogActionLabel("removecomment").s == "Removed comment")
        assertTrue(moderationLogActionLabel("ignorereports").s == "Ignored reports")
    }
}
