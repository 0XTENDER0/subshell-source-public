package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #303 phone test (Pixel 11, 2026-11-14): Reddit filed a Direct Chat text sent
 * right after an image under the image message (`first_message_name` = the
 * image's id); the Inbox showed the chat twice and the text above the image
 * it followed. It is one row, image first (ISSUE303_A_CHAT_THREAD_IS_ONE_ROW_V1).
 * The ids and times are the ones Reddit sent.
 */
class Issue303ChatThreadIsOneRowTest {
    private fun message(id: String, first: String, created: Long, author: String, dest: String, body: String) =
        """{"kind":"t4","data":{"id":"$id","name":"t4_$id","author":"$author","dest":"$dest","body":"$body",""" +
            """"new":false,"created_utc":$created,"subject":"[direct chat room]","first_message_name":"$first"}}"""

    private val listing = """{"kind":"Listing","data":{"after":null,"children":[""" +
        listOf(
            message("822yukt", "t4_73lvw4d", 1794688566, "Punk-Tum", "brealorg", "I'm cold"),
            message("822vpg1", "t4_822vpfy", 1794688066, "brealorg", "Punk-Tum", "Test10b"),
            message("822vpfy", "t4_73lvw4d", 1794688066, "brealorg", "Punk-Tum", "sent an [image](https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/abc)"),
            message("822iogb", "t4_73lvw4d", 1794686038, "Punk-Tum", "brealorg", "Bitch"),
        ).joinToString(",") + "]}}"

    @Test
    fun aTextFiledUnderTheImageIsInTheChatsOneRowAfterTheImage() {
        val page = SubshellNetworkJson.providerJson
            .decodeFromString(RedditInboxListingDto.serializer(), listing)
            .toNetworkPage(nowEpochSeconds = 1794689000, currentUsername = "brealorg")

        assertEquals(listOf("t4_73lvw4d"), page.items.map { it.id })
        val bodies = page.items.single().conversation.map { it.body }
        assertEquals(4, bodies.size)
        assertEquals("Bitch", bodies.first())
        assertEquals("I'm cold", bodies.last())
        assertEquals("the image comes before the text sent after it", true, bodies.indexOfFirst { it.contains("image") } < bodies.indexOf("Test10b"))
    }
}
