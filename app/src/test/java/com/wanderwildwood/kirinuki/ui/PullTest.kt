package com.wanderwildwood.kirinuki.ui

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
        assertEquals(Pulled.SomeFailed, pulledAfter(SyncOutcome(tried = 5, failed = 2)))
    }
}
