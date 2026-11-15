package io.github.brealorg.subshell.ui.error

import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.ui.s
import io.github.brealorg.subshell.media.VideoPlaybackFailure
import io.github.brealorg.subshell.media.VideoPlaybackPhase
import io.github.brealorg.subshell.media.VideoPlaybackUiState
import io.github.brealorg.subshell.media.VideoSourceFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

// TRANSLATE03: reads string resources through UiText.s, so it needs Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Issue11VideoErrorPresentationTest {
    private fun state(mediaId: String = "t3_issue11", phase: VideoPlaybackPhase = VideoPlaybackPhase.ERROR) =
        VideoPlaybackUiState(
            mediaId = mediaId, phase = phase, errorCode = 2004,
            failure = VideoPlaybackFailure(
                "t3_issue11", 1,
                listOf(VideoSourceFailure("t3_issue11", 1, 0, 2004, 403)),
            ),
        )

    @Test fun normalPresentationShowsHttpStatusAndExplanationWithRetry() {
        val presentation = videoPlaybackErrorPresentation(state())
        assertEquals(ErrorPresentationKind.DEGRADED, presentation.kind)
        assertEquals("HTTP 403 · Forbidden", presentation.title.s)
        assertEquals("Access denied by server.", presentation.message.s)
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
        assertEquals("Retry", presentation.primaryAction?.label?.s)
        assertNull(presentation.technicalDetail)
    }

    @Test fun wrongMediaCannotReusePreviousHttpStatus() {
        val presentation = videoPlaybackErrorPresentation(state(mediaId = "t3_other"))
        assertFalse(presentation.title.s.contains("403"))
    }

    @Test fun bufferingOrReadyStateCannotReuseTerminalHttpDetails() {
        for (phase in listOf(VideoPlaybackPhase.BUFFERING, VideoPlaybackPhase.READY)) {
            assertFalse(videoPlaybackErrorPresentation(state(phase = phase)).title.s.contains("403"))
        }
    }

    @Test fun missingHttpDetailsStillExposeTheAvailableMediaCode() {
        val presentation = videoPlaybackErrorPresentation(
            VideoPlaybackUiState(mediaId = "t3_issue11", phase = VideoPlaybackPhase.ERROR, errorCode = 2004),
        )
        assertEquals("Media3 2004 · HTTP request failed", presentation.title.s)
        assertNull(presentation.technicalDetail)
    }
}
