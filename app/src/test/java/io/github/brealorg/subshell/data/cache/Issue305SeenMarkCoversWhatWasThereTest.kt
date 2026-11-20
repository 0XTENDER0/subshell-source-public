package io.github.brealorg.subshell.data.cache

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.inbox.inboxBackgroundUnreadEventKeys
import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.model.MessageBubble
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #305: opening a PM thread whose mark-read failed (offline) kept a seen mark
 * on purpose (INBOXM3A2R2C1, MED4A: no read loop); the mark is per thread, so
 * a reply days later was never notified and never shown unread. A mark covers
 * what the thread held when it was seen (ISSUE305_A_SEEN_MARK_COVERS_WHAT_WAS_THERE_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue305SeenMarkCoversWhatWasThereTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(application, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val clock = MutableCacheClock(1_000_000_000L)
    private val cache = RoomSubshellCacheStore(database, clock = clock)
    private val account = "reddit:me"

    @After
    fun close() = cache.close()

    private fun thread(newestSeconds: Long) = InboxItem(
        id = "t4_root",
        kind = ActivityKind.MESSAGE,
        title = "Plans",
        sender = "alice",
        age = "now",
        preview = "Hi",
        unread = true,
        conversation = listOf(MessageBubble(id = "t4_m", sender = "alice", age = "now", body = "Hi", createdEpochSeconds = newestSeconds)),
        createdEpochSeconds = newestSeconds,
    )

    @Test
    fun aThreadSeenWhileItsReadFailedIsSilentUntilANewerMessageComes() {
        val seenAtSeconds = clock.epochMs / 1_000L
        cache.replaceLiveInboxSnapshot(account, listOf(thread(seenAtSeconds - 60)), nextCursor = null)
        cache.markLiveInboxSeen(account, "t4_root") // kept: the server read failed

        val before = cache.liveInboxSnapshot(account)!!
        assertEquals(setOf("t4_root"), before.locallySeenIds)
        assertTrue(inboxBackgroundUnreadEventKeys(before.items, before.locallySeenIds).isEmpty())

        // Days later the other person replies in the same thread.
        clock.epochMs += 3 * 24 * 3_600_000L
        cache.replaceLiveInboxSnapshot(account, listOf(thread(clock.epochMs / 1_000L - 5)), nextCursor = null)

        val after = cache.liveInboxSnapshot(account)!!
        assertTrue(after.locallySeenIds.isEmpty())
        assertEquals(1, inboxBackgroundUnreadEventKeys(after.items, after.locallySeenIds).size)
    }

    @Test
    fun aMessageSentJustBeforeTheThreadWasSeenStaysSeen() {
        // Reddit's clock may run a little ahead of the phone's.
        val seenAtSeconds = clock.epochMs / 1_000L
        cache.replaceLiveInboxSnapshot(account, listOf(thread(seenAtSeconds + 30)), nextCursor = null)
        cache.markLiveInboxSeen(account, "t4_root")

        assertEquals(setOf("t4_root"), cache.liveInboxSnapshot(account)!!.locallySeenIds)
    }
}
