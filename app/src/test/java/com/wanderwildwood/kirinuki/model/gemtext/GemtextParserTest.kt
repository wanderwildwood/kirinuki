package com.wanderwildwood.kirinuki.model.gemtext

import com.wanderwildwood.kirinuki.model.html.LinearBlockQuote
import com.wanderwildwood.kirinuki.model.html.LinearListItem
import com.wanderwildwood.kirinuki.model.html.LinearText
import com.wanderwildwood.kirinuki.model.html.LinearTextAnnotationH1
import com.wanderwildwood.kirinuki.model.html.LinearTextAnnotationH2
import com.wanderwildwood.kirinuki.model.html.LinearTextAnnotationH3
import com.wanderwildwood.kirinuki.model.html.LinearTextAnnotationLink
import com.wanderwildwood.kirinuki.model.html.LinearTextBlockStyle
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GemtextParserTest {
    private val parser = GemtextParser("gemini://example.space/log/one.gmi")

    private fun parse(text: String) = parser.parse(text).elements

    private fun hrefOf(element: Any?): String {
        val text = assertIs<LinearText>(element)
        val link = text.annotations.map { it.data }.filterIsInstance<LinearTextAnnotationLink>()
        assertEquals(1, link.size, "expected exactly one link annotation")
        return link.single().href
    }

    @Test
    fun `each text line is its own paragraph`() {
        // Gemtext has no line continuation: two lines are two paragraphs.
        val elements = parse("First line.\nSecond line.")

        assertEquals(2, elements.size)
        assertEquals("First line.", assertIs<LinearText>(elements[0]).text)
        assertEquals("Second line.", assertIs<LinearText>(elements[1]).text)
    }

    @Test
    fun `blank lines are gaps rather than paragraphs`() {
        assertEquals(2, parse("One.\n\n\n\nTwo.").size)
    }

    @Test
    fun `the three heading levels are recognised longest first`() {
        val elements = parse("# One\n## Two\n### Three")

        assertEquals(LinearTextAnnotationH1, assertIs<LinearText>(elements[0]).annotations.single().data)
        assertEquals(LinearTextAnnotationH2, assertIs<LinearText>(elements[1]).annotations.single().data)
        assertEquals(LinearTextAnnotationH3, assertIs<LinearText>(elements[2]).annotations.single().data)
        assertEquals("One", assertIs<LinearText>(elements[0]).text)
        assertEquals("Three", assertIs<LinearText>(elements[2]).text)
    }

    @Test
    fun `a link line with a label shows the label and points at the url`() {
        val elements = parse("=> gemini://example.space/other.gmi  The other page")

        assertEquals("The other page", assertIs<LinearText>(elements.single()).text)
        assertEquals("gemini://example.space/other.gmi", hrefOf(elements.single()))
    }

    @Test
    fun `a link line with no label shows the url itself`() {
        val elements = parse("=>gemini://example.space/bare.gmi")

        assertEquals("gemini://example.space/bare.gmi", assertIs<LinearText>(elements.single()).text)
        assertEquals("gemini://example.space/bare.gmi", hrefOf(elements.single()))
    }

    @Test
    fun `relative links resolve against the page they were found on`() {
        assertEquals(
            "gemini://example.space/log/two.gmi",
            hrefOf(parse("=> two.gmi Next").single()),
        )
        assertEquals(
            "gemini://example.space/index.gmi",
            hrefOf(parse("=> /index.gmi Home").single()),
        )
    }

    @Test
    fun `a link to the web keeps its scheme`() {
        assertEquals(
            "https://example.com/thing",
            hrefOf(parse("=> https://example.com/thing A web page").single()),
        )
    }

    @Test
    fun `list items each become an element, and gemtext lists are never ordered`() {
        val elements = parse("* one\n* two\n* three\nAnd text.")

        assertEquals(4, elements.size)
        val items = elements.take(3).map { assertIs<LinearListItem>(it) }
        assertTrue(items.all { it.orderedIndex == null })
        assertEquals("And text.", assertIs<LinearText>(elements[3]).text)
    }

    @Test
    fun `an asterisk without a space is not a list item`() {
        // The spec's marker is asterisk-space. *emphasis* is ordinary text.
        val elements = parse("*not a list*")
        assertEquals("*not a list*", assertIs<LinearText>(elements.single()).text)
    }

    @Test
    fun `consecutive quote lines become one quotation`() {
        val elements = parse("> first\n> second\nAfter.")

        assertEquals(2, elements.size)
        assertEquals(2, assertIs<LinearBlockQuote>(elements[0]).content.size)
    }

    @Test
    fun `preformatted blocks keep their line breaks and lose their toggles`() {
        val elements = parse("```\n  /\\\n /  \\\n```\nAfter.")

        val pre = assertIs<LinearText>(elements[0])
        assertEquals(LinearTextBlockStyle.PRE_FORMATTED, pre.blockStyle)
        assertEquals("  /\\\n /  \\", pre.text)
        assertEquals("After.", assertIs<LinearText>(elements[1]).text)
    }

    @Test
    fun `alt text on the toggle is not rendered as content`() {
        val elements = parse("```an ascii cat\nmeow\n```")

        assertEquals(1, elements.size)
        assertEquals("meow", assertIs<LinearText>(elements.single()).text)
    }

    @Test
    fun `nothing inside a preformatted block is interpreted`() {
        val elements = parse("```\n# not a heading\n=> not a link\n* not a list\n```")

        val pre = assertIs<LinearText>(elements.single())
        assertEquals(LinearTextBlockStyle.PRE_FORMATTED, pre.blockStyle)
        assertEquals("# not a heading\n=> not a link\n* not a list", pre.text)
    }

    @Test
    fun `an unterminated preformatted block does not swallow an error`() {
        // The author forgot the closing toggle. Keep what is there rather than losing it.
        val elements = parse("Before.\n```\nstill going")

        assertEquals(2, elements.size)
        assertEquals("still going", assertIs<LinearText>(elements[1]).text)
    }

    @Test
    fun `carriage returns from a windows capsule do not become content`() {
        val elements = parse("# Heading\r\nText.\r\n")

        assertEquals("Heading", assertIs<LinearText>(elements[0]).text)
        assertEquals("Text.", assertIs<LinearText>(elements[1]).text)
    }

    @Test
    fun `an empty link line is dropped rather than becoming an empty link`() {
        assertEquals(0, parse("=>").size)
        assertEquals(0, parse("=>   ").size)
    }

    @Test
    fun `an empty document parses to nothing`() {
        assertEquals(0, parse("").size)
    }

    @Test
    fun `a heading with no text does not produce a broken annotation span`() {
        // Annotations are inclusive at both ends, so an empty string has no valid span.
        val heading = assertIs<LinearText>(parse("#").single())
        assertEquals("", heading.text)
        assertEquals(0, heading.annotations.single().start)
    }
}
