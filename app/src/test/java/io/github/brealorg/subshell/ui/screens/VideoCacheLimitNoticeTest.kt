package io.github.brealorg.subshell.ui.screens

import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.ui.s
import io.github.brealorg.subshell.media.VideoMediaCacheLimit
import io.github.brealorg.subshell.ui.error.ErrorPresentationKind
import org.junit.Assert.assertEquals
import org.junit.Test

// TRANSLATE08: reads string resources (enum labels), so it needs Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class VideoCacheLimitNoticeTest {
    @Test
    fun theRestartNoticeNamesTheChosenSizeInTheTrayStyle() {
        val notice = videoCacheLimitRestartNotice(VideoMediaCacheLimit.GB_2)
        assertEquals(ErrorPresentationKind.DEGRADED, notice.kind)
        assertEquals("Restart to apply", notice.title.s)
        assertEquals("The 2 GB video cache is used the next time subshell starts", notice.message.s)
    }
}
