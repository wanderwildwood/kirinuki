package com.wanderwildwood.kirinuki.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlainTextPageTest {
    private val txt = "https://example.org/etc/notes.c.txt"

    @Test
    fun `html is a page and text plain is text, whatever the address`() {
        assertEquals(PageKind.HTML, pageKindOf("text/html; charset=utf-8", txt))
        assertEquals(PageKind.TEXT, pageKindOf("text/plain; charset=utf-8", "https://example.org/readme"))
        assertEquals(PageKind.TEXT, pageKindOf("TEXT/PLAIN", "https://example.org/a"))
    }

    @Test
    fun `a generic type is taken at the address's word only for txt`() {
        assertEquals(PageKind.TEXT, pageKindOf("application/octet-stream", txt))
        assertEquals(PageKind.TEXT, pageKindOf(null, txt))
        assertEquals(PageKind.TEXT, pageKindOf("application/octet-stream", "$txt?raw=1#top"))
        assertEquals(PageKind.OTHER, pageKindOf("application/octet-stream", "https://example.org/a.zip"))
        assertEquals(PageKind.OTHER, pageKindOf(null, "https://example.org/a"))
    }

    @Test
    fun `everything else keeps today's message`() {
        assertEquals(PageKind.OTHER, pageKindOf("application/pdf", txt))
        assertEquals(PageKind.OTHER, pageKindOf("image/png", "https://example.org/a.png"))
        assertEquals(PageKind.OTHER, pageKindOf("text/css", "https://example.org/a.css"))
    }

    @Test
    fun `text comes through with plain line ends`() {
        val shown = plainTextShown("one\r\ntwo\rthree\n\n", cut = false)!!
        assertEquals("one\ntwo\nthree", shown.text)
        assertFalse(shown.cut)
    }

    @Test
    fun `a cut file ends on its last whole line`() {
        val shown = plainTextShown("first line\nsecond line\nhalf a li", cut = true)!!
        assertEquals("first line\nsecond line", shown.text)
        assertTrue(shown.cut)
    }

    @Test
    fun `a binary file sent as text is not shown`() {
        assertNull(plainTextShown("PK\u0003\u0004\u0000\u0000", cut = false))
    }

    @Test
    fun `markdown, csv and source types are text too`() {
        assertEquals(PageKind.TEXT, pageKindOf("text/markdown; charset=utf-8", "https://example.org/a"))
        assertEquals(PageKind.TEXT, pageKindOf("text/csv", "https://example.org/a"))
        assertEquals(PageKind.TEXT, pageKindOf("text/x-c", "https://example.org/a"))
        assertEquals(PageKind.TEXT, pageKindOf("text/x-python", "https://example.org/a"))
        assertEquals(PageKind.TEXT, pageKindOf("application/octet-stream", "https://example.org/notes.md"))
        assertEquals(PageKind.TEXT, pageKindOf(null, "https://example.org/rain.csv"))
        assertEquals(PageKind.TEXT, pageKindOf("application/octet-stream", "https://example.org/uxnmin.c"))
        assertEquals(PageKind.OTHER, pageKindOf("application/octet-stream", "https://example.org/a.exe"))
    }

    @Test
    fun `text is split at blank lines and never into a run longer than the cap`() {
        assertEquals(listOf("a\nb", "c"), textBlocks("a\nb\n\n\nc\n"))
        val long = (1..30).joinToString("\n") { "line $it" }
        val blocks = textBlocks(long, maxLines = 12)
        assertEquals(listOf(12, 12, 6), blocks.map { it.lines().size })
        assertEquals(long, blocks.joinToString("\n"))
        assertEquals(emptyList(), textBlocks(""))
    }
}
