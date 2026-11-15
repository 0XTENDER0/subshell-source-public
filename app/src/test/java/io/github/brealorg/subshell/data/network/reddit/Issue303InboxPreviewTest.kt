package io.github.brealorg.subshell.data.network.reddit

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #303 phone test (Pixel 11, 2026-11-14): Reddit sends a Direct Chat image as
 * "sent an [image](https://matrix…)", and the Inbox row showed that markdown
 * as it is. A link in a preview reads as its label
 * (ISSUE303_A_CHAT_IMAGE_PREVIEW_READS_AS_TEXT_V1).
 */
class Issue303InboxPreviewTest {
    @Test
    fun aChatImageReadsAsSentAnImage() {
        assertEquals(
            "sent an image",
            "sent an [image](https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/abc123)".toInboxPreview(),
        )
    }

    @Test
    fun aLinkWithParenthesesInItsAddressReadsAsItsLabel() {
        assertEquals(
            "see Foo (bar) here",
            "see [Foo (bar)](https://en.wikipedia.org/wiki/Foo_(bar)) here".toInboxPreview(),
        )
    }

    @Test
    fun textWithoutLinksIsUnchanged() {
        assertEquals("Nice :* [not a link]", "Nice :*\n[not a link]".toInboxPreview())
    }
}
