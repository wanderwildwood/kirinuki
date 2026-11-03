package com.wanderwildwood.kirinuki.sync.greader

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The table from Read You's GoogleReaderAPI, both ways. */
class GReaderIdsTest {
    private val pairs =
        listOf(
            "tag:google.com,2005:reader/item/5d0cfa30041d4348" to "6705009029382226760",
            "tag:google.com,2005:reader/item/024025978b5e50d2" to "162170919393841362",
            "tag:google.com,2005:reader/item/fb115bd6d34a8e9f" to "-355401917359550817",
        )

    @Test
    fun longToShort() {
        pairs.forEach { (long, short) -> assertEquals(short, GReaderIds.toShort(long)) }
    }

    @Test
    fun shortToLong() {
        pairs.forEach { (long, short) -> assertEquals(long, GReaderIds.toLong(short)) }
    }

    @Test
    fun nonsenseIsNeither() {
        assertNull(GReaderIds.toLong("feed/12"))
        assertNull(GReaderIds.toShort("tag:google.com,2005:reader/item/xyz"))
    }

    @Test
    fun labelNames() {
        assertEquals("Blogs", GReaderIds.labelName("user/-/label/Blogs"))
        assertEquals("Blogs", GReaderIds.labelName("user/1005921515/label/Blogs"))
        assertNull(GReaderIds.labelName("user/-/state/com.google/starred"))
    }

    @Test
    fun aServersDefaultCategoryIsNoFolder() {
        fun label(vararg c: GrCategory) =
            GrSubscription(id = "feed/1", url = "https://example.org/feed", categories = c.toList()).toRemoteFeed()!!.label
        assertEquals("", label(GrCategory(id = "user/-/label/Uncategorized", label = "Uncategorized")))
        assertEquals("", label(GrCategory(id = "user/1/label/All", label = "All")))
        assertEquals("Nature", label(GrCategory(id = "user/-/label/Nature", label = "Nature")))
        assertEquals("Nature", label(GrCategory(label = "Uncategorized"), GrCategory(id = "user/-/label/Nature")))
    }
}
