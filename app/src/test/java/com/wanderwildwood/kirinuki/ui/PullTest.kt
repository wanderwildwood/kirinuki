package com.wanderwildwood.kirinuki.ui

import com.wanderwildwood.kirinuki.archmodel.FailKind
import com.wanderwildwood.kirinuki.archmodel.SyncFailure
import com.wanderwildwood.kirinuki.archmodel.SyncOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class PullTest {
    @Test
    fun nothingFromTheSyncMeansItNeverStarted() {
        assertEquals(Pulled.NotStarted, pulledAfter(null))
    }

    @Test
    fun aCleanSyncIsUpToDate() {
        assertEquals(Pulled.UpToDate, pulledAfter(SyncOutcome(tried = 12, failed = 0)))
        assertEquals(Pulled.UpToDate, pulledAfter(SyncOutcome(tried = 0, failed = 0)))
    }

    @Test
    fun everyFeedFailingIsUnreachable() {
        assertEquals(Pulled.Unreachable, pulledAfter(SyncOutcome(tried = 3, failed = 3)))
        assertEquals(Pulled.Unreachable, pulledAfter(SyncOutcome(tried = 3, failed = 0, ok = false)))
    }

    @Test
    fun someFailingIsSaidSo() {
        assertEquals(Pulled.SomeFailed(emptyList()), pulledAfter(SyncOutcome(tried = 5, failed = 2)))
        assertEquals(
            Pulled.SomeFailed(listOf("Alpha", "Beta")),
            pulledAfter(SyncOutcome(tried = 5, failed = 2, failedTitles = listOf("Alpha", "Beta"))),
        )
    }

    @Test
    fun oneFeedFailingSaysWhy() {
        val failure = SyncFailure("Alpha", FailKind.NO_CONNECTION)
        assertEquals(
            Pulled.OneFailed(failure),
            pulledAfter(SyncOutcome(tried = 1, failed = 1, failedTitles = listOf("Alpha"), failures = listOf(failure))),
        )
        assertEquals(
            Pulled.OneFailed(SyncFailure("Beta", FailKind.SERVER_ANSWERED, 404)),
            pulledAfter(
                SyncOutcome(
                    tried = 4,
                    failed = 1,
                    failedTitles = listOf("Beta"),
                    failures = listOf(SyncFailure("Beta", FailKind.SERVER_ANSWERED, 404)),
                ),
            ),
        )
    }
}
