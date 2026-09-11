package com.wanderwildwood.kirinuki.model.gemtext

import com.wanderwildwood.kirinuki.model.html.LinearArticle
import com.wanderwildwood.kirinuki.model.html.LinearBlockQuote
import com.wanderwildwood.kirinuki.model.html.LinearElement
import com.wanderwildwood.kirinuki.model.html.LinearListItem
import com.wanderwildwood.kirinuki.model.html.LinearText
import com.wanderwildwood.kirinuki.model.html.LinearTextAnnotation
import com.wanderwildwood.kirinuki.model.html.LinearTextAnnotationH1
import com.wanderwildwood.kirinuki.model.html.LinearTextAnnotationH2
import com.wanderwildwood.kirinuki.model.html.LinearTextAnnotationH3
import com.wanderwildwood.kirinuki.model.html.LinearTextAnnotationLink
import com.wanderwildwood.kirinuki.model.html.LinearTextBlockStyle
import java.net.URI

/**
 * Gemtext into the same [LinearArticle] the HTML pipeline produces, so the article screen
 * renders a gemlog without knowing it is one.
 *
 * The whole format is line-based and context-free apart from the preformatted toggle:
 * a line means what its first characters say it means, and nothing spans lines. Written
 * against the specification rather than adapted from any implementation — Offpunk, the
 * obvious reference, is AGPL-3.0 and this is GPL-3.0.
 */
class GemtextParser(
    /** Used to make relative links absolute, so a click has somewhere to go. */
    private val baseUrl: String,
) {
    fun parse(text: String): LinearArticle {
        val elements = mutableListOf<LinearElement>()
        val lines = text.replace("\r\n", "\n").split('\n')

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith(PREFORMAT_TOGGLE) -> {
                    // Everything up to the next toggle, or to the end if it never comes:
                    // an unterminated block is the author's mistake, not a reason to lose
                    // the rest of the page.
                    val body = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].startsWith(PREFORMAT_TOGGLE)) {
                        body.add(lines[i])
                        i++
                    }
                    i++ // step over the closing toggle
                    if (body.isNotEmpty()) {
                        elements.add(
                            LinearText(
                                ids = emptySet(),
                                text = body.joinToString("\n"),
                                blockStyle = LinearTextBlockStyle.PRE_FORMATTED,
                            ),
                        )
                    }
                    continue
                }

                line.startsWith(LIST_ITEM) -> {
                    // Items go into the stream one by one, the way the HTML linearizer
                    // emits them -- there is no wrapping list element in this model.
                    // Gemtext has only unordered lists, so orderedIndex is always null.
                    while (i < lines.size && lines[i].startsWith(LIST_ITEM)) {
                        elements.add(
                            LinearListItem(
                                ids = emptySet(),
                                orderedIndex = null,
                                plainText(lines[i].removePrefix(LIST_ITEM).trim()),
                            ),
                        )
                        i++
                    }
                    continue
                }

                line.startsWith(QUOTE) -> {
                    val quoted = mutableListOf<LinearElement>()
                    while (i < lines.size && lines[i].startsWith(QUOTE)) {
                        quoted.add(plainText(lines[i].removePrefix(QUOTE).trim()))
                        i++
                    }
                    elements.add(LinearBlockQuote(ids = emptySet(), cite = null, content = quoted))
                    continue
                }

                line.startsWith(LINK) -> {
                    linkElement(line)?.let { elements.add(it) }
                }

                // Longest first: ### before ## before #.
                line.startsWith(H3) -> elements.add(heading(line, H3))
                line.startsWith(H2) -> elements.add(heading(line, H2))
                line.startsWith(H1) -> elements.add(heading(line, H1))

                // A blank line is a gap between paragraphs, not a paragraph.
                line.isBlank() -> Unit

                // Every other line is its own paragraph. Gemtext has no line continuation:
                // two text lines in a row are two paragraphs, not one wrapped over two.
                else -> elements.add(plainText(line.trim()))
            }
            i++
        }

        return LinearArticle(elements = elements)
    }

    private fun heading(
        line: String,
        marker: String,
    ): LinearText {
        val text = line.removePrefix(marker).trim()
        val annotation =
            when (marker) {
                H3 -> LinearTextAnnotationH3
                H2 -> LinearTextAnnotationH2
                else -> LinearTextAnnotationH1
            }
        return LinearText(
            ids = emptySet(),
            text = text,
            blockStyle = LinearTextBlockStyle.TEXT,
            annotations =
                listOf(
                    LinearTextAnnotation(data = annotation, start = 0, end = lastIndexOf(text)),
                ),
        )
    }

    /**
     * `=> URL label` — the URL is required, the label is not. A link line with no label
     * shows the URL, because a link with nothing to click is worse than an ugly one.
     */
    private fun linkElement(line: String): LinearText? {
        val rest = line.removePrefix(LINK).trim()
        if (rest.isEmpty()) return null

        val split = rest.indexOfFirst { it.isWhitespace() }
        val target = if (split == -1) rest else rest.substring(0, split)
        val label = if (split == -1) "" else rest.substring(split).trim()

        val href = absolute(target)
        val shown = label.ifBlank { target }

        return LinearText(
            ids = emptySet(),
            text = shown,
            blockStyle = LinearTextBlockStyle.TEXT,
            annotations =
                listOf(
                    LinearTextAnnotation(
                        data = LinearTextAnnotationLink(href),
                        start = 0,
                        end = lastIndexOf(shown),
                    ),
                ),
        )
    }

    private fun absolute(target: String): String =
        try {
            URI(baseUrl).resolve(target).toString()
        } catch (e: IllegalArgumentException) {
            // A link the page got wrong stays as written rather than taking the page down.
            target
        }

    private fun plainText(text: String) =
        LinearText(ids = emptySet(), text = text, blockStyle = LinearTextBlockStyle.TEXT)

    /** Annotations are inclusive at both ends, so an empty string has no valid span. */
    private fun lastIndexOf(text: String): Int = (text.length - 1).coerceAtLeast(0)

    companion object {
        private const val LINK = "=>"
        private const val PREFORMAT_TOGGLE = "```"
        private const val LIST_ITEM = "* "
        private const val QUOTE = ">"
        private const val H1 = "#"
        private const val H2 = "##"
        private const val H3 = "###"
    }
}
