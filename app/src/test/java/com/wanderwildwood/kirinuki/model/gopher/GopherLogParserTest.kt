package com.wanderwildwood.kirinuki.model.gopher

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GopherLogParserTest {
    private val parser = GopherLogParser()
    private val url = "gopher://example.org:7070/1/phlog"

    private val menu =
        listOf(
            "iMy phlog\t\t\t",
            "0" + "2026-09-10 The second thing\t/log/two\texample.org\t7070",
            "0" + "2026-09-02 - The first thing\t/log/one\texample.org\t7070",
            "1About\t/about\texample.org\t7070",
            ".",
        ).joinToString("\r\n")

    @Test
    fun `a menu of dated text files is a feed`() {
        val feed = parser.parse(url, menu)
        assertNotNull(feed)
        assertEquals("My phlog", feed.title)
        assertEquals(2, feed.items?.size)
        assertEquals("The second thing", feed.items?.first()?.title)
        assertEquals("gopher://example.org:7070/0/log/two", feed.items?.first()?.url)
        assertEquals("The first thing", feed.items?.last()?.title)
    }

    @Test
    fun `an undated menu is not a feed`() {
        assertNull(parser.parse(url, "iHello\t\t\t\r\n1Sub\t/sub\texample.org\t70\r\n.\r\n"))
    }

    @Test
    fun `lines after the period are not read`() {
        val feed = parser.parse(url, menu + "\r\n02026-01-01 Late\t/x\texample.org\t7070")
        assertEquals(2, feed?.items?.size)
    }

    @Test
    fun `a missing host falls back to the menu's own`() {
        val feed = parser.parse(url, "02026-09-10 Here\t/log/x\t\t\r\n")
        assertEquals("gopher://example.org:70/0/log/x", feed?.items?.single()?.url)
    }
}
