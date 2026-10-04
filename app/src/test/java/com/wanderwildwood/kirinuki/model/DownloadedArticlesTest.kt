package com.wanderwildwood.kirinuki.model

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadedArticlesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun article(
        dir: File,
        id: Long,
        daysOld: Long = 0,
    ): File =
        File(dir, "$id.full.html.gz").apply {
            writeText("article $id")
            setLastModified(Instant.now().minus(daysOld, ChronoUnit.DAYS).toEpochMilli())
        }

    @Test
    fun clearingTakesEverythingButTheKeptOnes() {
        val dir = tmp.newFolder("full_articles")
        val kept = article(dir, 1)
        val other = article(dir, 2)
        val notAnArticle = File(dir, "notes.txt").apply { writeText("x") }

        assertEquals(1, DownloadedArticles.remove(dir, keep = setOf(1L)))

        assertTrue(kept.exists())
        assertFalse(other.exists())
        assertTrue(notAnArticle.exists())
    }

    @Test
    fun theSweepOnlyTakesWhatIsOlderThanTheSetting() {
        val dir = tmp.newFolder("full_articles")
        val recent = article(dir, 1, daysOld = 3)
        val old = article(dir, 2, daysOld = 40)
        val oldButKept = article(dir, 3, daysOld = 40)

        val removed =
            DownloadedArticles.remove(
                dir,
                keep = setOf(3L),
                downloadedBefore = Instant.now().minus(30, ChronoUnit.DAYS),
            )

        assertEquals(1, removed)
        assertTrue(recent.exists())
        assertFalse(old.exists())
        assertTrue(oldButKept.exists())
    }

    @Test
    fun whatWasInTheCacheMovesAndTheCacheFolderGoes() {
        val cache = tmp.newFolder("cache", "full_articles")
        val files = tmp.newFolder("files")
        article(cache, 1)
        article(cache, 2)
        val to = File(files, "full_articles")

        DownloadedArticles.moveOutOfCache(from = cache, to = to)

        assertFalse(cache.exists())
        assertEquals(setOf("1.full.html.gz", "2.full.html.gz"), to.list()!!.toSet())
        assertEquals("article 2", File(to, "2.full.html.gz").readText())
        assertEquals(DownloadedArticles.size(to), File(to, "1.full.html.gz").length() * 2)
    }

    @Test
    fun movingTwiceOrWithNothingToMoveIsHarmless() {
        val to = File(tmp.root, "files/full_articles")
        DownloadedArticles.moveOutOfCache(from = File(tmp.root, "missing"), to = to)
        assertFalse(to.exists())
    }
}
