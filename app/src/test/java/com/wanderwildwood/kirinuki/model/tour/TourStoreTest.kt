package com.wanderwildwood.kirinuki.model.tour

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TourStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = TourStore(tmp.newFile(), tmp.newFolder())

    @Test
    fun `a queued page is pending until a sync has been`() {
        val tour = store()
        tour.add("gemini://example.space/one", "The first thing")

        assertEquals(1, tour.pending().size)
        assertFalse(tour.all().single().isReady)

        tour.markFetched("gemini://example.space/one", "# Hello")

        assertTrue(tour.pending().isEmpty())
        assertTrue(tour.all().single().isReady)
        assertEquals("# Hello", tour.cachedText("gemini://example.space/one"))
    }

    @Test
    fun `the order the reader chose stands`() {
        val tour = store()
        tour.add("gemini://a", "A")
        tour.add("gemini://b", "B")
        tour.add("gemini://a", "A again")

        assertEquals(listOf("gemini://a", "gemini://b"), tour.all().map { it.url })
        assertEquals("A", tour.all().first().title)
    }

    @Test
    fun `removing takes the cached copy with it`() {
        val tour = store()
        tour.add("gemini://a", "A")
        tour.markFetched("gemini://a", "text")

        tour.remove("gemini://a")

        assertTrue(tour.all().isEmpty())
        assertNull(tour.cachedText("gemini://a"))
    }

    @Test
    fun `the queue survives being reopened`() {
        val file = tmp.newFile()
        val cache = tmp.newFolder()

        TourStore(file, cache).apply {
            add("gemini://a", "A title with spaces")
            markFetched("gemini://a", "body")
        }

        val reopened = TourStore(file, cache)

        assertEquals(1, reopened.count())
        assertEquals("A title with spaces", reopened.all().single().title)
        assertTrue(reopened.all().single().isReady)
        assertEquals("body", reopened.cachedText("gemini://a"))
    }

    @Test
    fun `a title containing a tab does not corrupt the next field`() {
        // The file is tab separated and a page title is whatever the page said.
        val file = tmp.newFile()
        val cache = tmp.newFolder()
        TourStore(file, cache).add("gemini://a", "Before\tAfter")

        assertEquals("Before After", TourStore(file, cache).all().single().title)
    }

    @Test
    fun `an empty cached file counts as not cached`() {
        val tour = store()
        tour.add("gemini://a", "A")
        tour.markFetched("gemini://a", "")

        assertNull(tour.cachedText("gemini://a"))
    }
}
