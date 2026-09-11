package com.wanderwildwood.kirinuki.model.gopher

import com.wanderwildwood.kirinuki.model.html.LinearText
import com.wanderwildwood.kirinuki.model.html.LinearTextAnnotationLink
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GopherMenuParserTest {
    private val parser = GopherMenuParser()

    private fun parse(text: String) = parser.parse(text).elements

    private fun hrefOf(element: Any?): String? {
        val text = assertIs<LinearText>(element)
        return text.annotations
            .map { it.data }
            .filterIsInstance<LinearTextAnnotationLink>()
            .singleOrNull()
            ?.href
    }

    @Test
    fun `a menu entry becomes a link carrying its own type`() {
        // The type is the first character of the line, not a field -- and it has to be
        // carried into the link, because it is what says how to read what comes back.
        val elements = parse("1Phlog\t/phlog\texample.org\t70")

        assertEquals("Phlog", assertIs<LinearText>(elements.single()).text)
        assertEquals("gopher://example.org:70/1/phlog", hrefOf(elements.single()))
    }

    @Test
    fun `a text file entry keeps its own type too`() {
        val elements = parse("0About me\t/about.txt\texample.org\t70")

        assertEquals("gopher://example.org:70/0/about.txt", hrefOf(elements.single()))
    }

    @Test
    fun `informational lines are prose rather than links`() {
        // Type 'i' is in no RFC and on almost every menu written since.
        val elements = parse("iWelcome to my hole\t\terror.host\t1")

        assertEquals("Welcome to my hole", assertIs<LinearText>(elements.single()).text)
        assertNull(hrefOf(elements.single()))
    }

    @Test
    fun `a URL selector links off gopher entirely`() {
        val elements = parse("hMy website\tURL:https://example.com\texample.org\t70")

        assertEquals("https://example.com", hrefOf(elements.single()))
    }

    @Test
    fun `the menu stops at a line holding one period`() {
        val elements =
            parse(
                "1One\t/one\texample.org\t70\r\n.\r\n1Never\t/never\texample.org\t70",
            )

        assertEquals(1, elements.size)
    }

    @Test
    fun `a non standard port is kept`() {
        val elements = parse("1Elsewhere\t/x\texample.org\t7070")

        assertEquals("gopher://example.org:7070/1/x", hrefOf(elements.single()))
    }

    @Test
    fun `a missing port falls back to seventy`() {
        val elements = parse("1Thing\t/x\texample.org")

        assertEquals("gopher://example.org:70/1/x", hrefOf(elements.single()))
    }

    @Test
    fun `blank informational lines are dropped rather than becoming empty paragraphs`() {
        // Menus are padded with these for layout on an 80-column terminal.
        val elements = parse("i\t\terror.host\t1\niReal text\t\terror.host\t1\ni\t\terror.host\t1")

        assertEquals(1, elements.size)
    }

    @Test
    fun `an entry with no host is shown but is not a link`() {
        val elements = parse("1Broken\t/x\t\t70")

        assertEquals("Broken", assertIs<LinearText>(elements.single()).text)
        assertNull(hrefOf(elements.single()))
    }

    @Test
    fun `an empty menu parses to nothing`() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse(".\r\n").isEmpty())
    }
}
