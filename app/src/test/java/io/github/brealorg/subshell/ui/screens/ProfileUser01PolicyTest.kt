package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.ui.s
import io.github.brealorg.subshell.ui.ProfileActivityEvidence
import io.github.brealorg.subshell.ui.isUnavailablePublicProfileHttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(qualifiers = "en")
class ProfileUser01PolicyTest {
    @Test
    fun privacyPresentationIsExplicitlyProbabilistic() {
        val presentation = profilePrivacyEmptyPresentation()

        assertEquals("Profile privacy may be enabled", presentation.title.s)
        assertTrue(presentation.description.s.contains("may have enabled"))
        assertTrue(presentation.description.s.contains("privacy settings"))
        assertFalse(presentation.description.s.contains("Reddit isn't showing"))
    }

    @Test
    fun neutralEmptyPresentationsDoNotClaimPrivacy() {
        val posts = publicProfileEmptyPresentation(
            username = "Example_User",
            activity = ProfileActivityEvidence.POST,
        )
        val comments = publicProfileEmptyPresentation(
            username = "Example_User",
            activity = ProfileActivityEvidence.COMMENT,
        )

        assertEquals("No public profile posts", posts.title.s)
        assertEquals("No public profile comments", comments.title.s)
        assertFalse(posts.description.s.contains("privacy", ignoreCase = true))
        assertFalse(comments.description.s.contains("privacy", ignoreCase = true))
    }

    @Test
    fun unavailableIdentityStatusIsNarrowAndCopyDoesNotOverclaimCause() {
        assertTrue(isUnavailablePublicProfileHttpStatus(404))
        assertFalse(isUnavailablePublicProfileHttpStatus(403))
        assertFalse(isUnavailablePublicProfileHttpStatus(410))
        assertFalse(isUnavailablePublicProfileHttpStatus(500))
        assertFalse(isUnavailablePublicProfileHttpStatus(null))

        val presentation = profileUnavailablePresentation()
        assertEquals("Profile unavailable", presentation.title.s)
        assertTrue(presentation.description.s.contains("could not be found"))
        assertTrue(presentation.description.s.contains("may have been deleted"))
        assertTrue(presentation.description.s.contains("suspended"))
        assertFalse(presentation.description.s.contains("404"))
    }
}
