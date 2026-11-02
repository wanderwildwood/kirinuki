package com.wanderwildwood.kirinuki.sync.greader

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GReaderMergeTest {
    private fun item(
        id: Long,
        baseRead: Boolean = false,
        baseStarred: Boolean = false,
        localRead: Boolean = baseRead,
        localStarred: Boolean = baseStarred,
    ) = GReaderMerge.ItemState(id, "r$id", baseRead, baseStarred, localRead, localStarred)

    @Test
    fun onlyWhatThePhoneChangedIsSent() {
        val push =
            GReaderMerge.push(
                listOf(
                    item(1, localRead = true),
                    item(2, baseRead = true, localRead = false),
                    item(3, localStarred = true),
                    item(4, baseStarred = true, localStarred = false),
                    item(5),
                ),
            )
        assertEquals(listOf("r1"), push.markRead)
        assertEquals(listOf("r2"), push.markUnread)
        assertEquals(listOf("r3"), push.star)
        assertEquals(listOf("r4"), push.unstar)
    }

    @Test
    fun theServersChangesApplyWhereThePhoneMadeNone() {
        val changes =
            GReaderMerge.pull(
                listOf(item(1), item(2, baseRead = true), item(3, baseStarred = true, baseRead = true)),
                unreadIds = setOf("r2"),
                starredIds = emptySet(),
            )
        assertEquals(
            listOf(
                GReaderMerge.Apply(1, read = true, starred = null),
                GReaderMerge.Apply(2, read = false, starred = null),
                GReaderMerge.Apply(3, read = null, starred = false),
            ),
            changes,
        )
    }

    @Test
    fun aPhoneChangeNotYetSentIsNotOverwritten() {
        val changes = GReaderMerge.pull(listOf(item(1, localRead = true)), unreadIds = setOf("r1"), starredIds = emptySet())
        assertTrue(changes.isEmpty())
    }

    @Test
    fun nothingChangedNothingToDo() {
        assertTrue(GReaderMerge.push(listOf(item(1), item(2, baseRead = true))).isEmpty)
        assertTrue(GReaderMerge.pull(listOf(item(1)), setOf("r1"), emptySet()).isEmpty())
    }
}
