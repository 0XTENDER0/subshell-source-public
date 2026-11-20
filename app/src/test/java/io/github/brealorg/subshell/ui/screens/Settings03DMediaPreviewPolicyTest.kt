package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.ui.s
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.ui.FullscreenCaptionMode
import io.github.brealorg.subshell.ui.VideoAutoplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Settings03DMediaPreviewPolicyTest {
    @Test
    fun autoplayPreviewUsesProductMeaningInsteadOfTechnicalCopy() {
        assertEquals("Auto", mediaPreviewAutoplayLabel(VideoAutoplay.ALWAYS).s)
        assertEquals("Wi-Fi", mediaPreviewAutoplayLabel(VideoAutoplay.WIFI_ONLY).s)
        assertEquals("Manual", mediaPreviewAutoplayLabel(VideoAutoplay.NEVER).s)
    }

    @Test
    fun fullscreenCaptionsWithControlsHideWhenViewerControlsHide() {
        assertTrue(
            mediaPreviewFullscreenCaptionVisible(
                mode = FullscreenCaptionMode.WITH_CONTROLS,
                controlsVisible = true,
            ),
        )
        assertFalse(
            mediaPreviewFullscreenCaptionVisible(
                mode = FullscreenCaptionMode.WITH_CONTROLS,
                controlsVisible = false,
            ),
        )
    }

    @Test
    fun alwaysVisibleFullscreenCaptionsIgnoreViewerControls() {
        assertTrue(
            mediaPreviewFullscreenCaptionVisible(
                mode = FullscreenCaptionMode.ALWAYS_VISIBLE,
                controlsVisible = true,
            ),
        )
        assertTrue(
            mediaPreviewFullscreenCaptionVisible(
                mode = FullscreenCaptionMode.ALWAYS_VISIBLE,
                controlsVisible = false,
            ),
        )
    }
}
