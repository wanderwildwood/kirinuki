package com.wanderwildwood.kirinuki.model.gopher

import com.wanderwildwood.kirinuki.model.html.LinearArticle
import com.wanderwildwood.kirinuki.model.html.LinearElement
import com.wanderwildwood.kirinuki.model.html.LinearText
import com.wanderwildwood.kirinuki.model.html.LinearTextAnnotation
import com.wanderwildwood.kirinuki.model.html.LinearTextAnnotationLink
import com.wanderwildwood.kirinuki.model.html.LinearTextBlockStyle

/**
 * A gopher menu into the same [LinearArticle] everything else here renders.
 *
 * Each line is `<type><display>\t<selector>\t<host>\t<port>`, where the type is the first
 * character of the display field rather than a field of its own — which is the single
 * most awkward thing about the format. A line holding one period ends the menu.
 */
class GopherMenuParser {
    fun parse(text: String): LinearArticle {
        val elements =
            text
                .replace("\r\n", "\n")
                .lineSequence()
                .takeWhile { it != "." }
                .mapNotNull { line -> elementOf(line) }
                .toList()

        return LinearArticle(elements = elements)
    }

    private fun elementOf(line: String): LinearElement? {
        if (line.isEmpty()) return null

        val type = line[0]
        val fields = line.drop(1).split('\t')
        val display = fields.getOrNull(0).orEmpty()

        // 'i' is not in RFC 1436 at all, and is on almost every menu written since: it is
        // how servers put a line of prose on a page. Treated as text, which is what it is.
        if (type == INFORMATIONAL) {
            return if (display.isBlank()) null else plain(display)
        }

        val selector = fields.getOrNull(1).orEmpty()
        val host = fields.getOrNull(2).orEmpty()
        val port = fields.getOrNull(3)?.trim()?.toIntOrNull() ?: DEFAULT_PORT

        // A menu can point anywhere, including off gopher entirely: type 'h' with a
        // selector of "URL:https://..." is how the format grew a way to link the web.
        val href =
            when {
                selector.startsWith("URL:", ignoreCase = true) -> selector.drop(4)
                host.isBlank() -> return if (display.isBlank()) null else plain(display)
                else -> "gopher://$host:$port/$type$selector"
            }

        val shown = display.ifBlank { href }

        return LinearText(
            ids = emptySet(),
            text = shown,
            blockStyle = LinearTextBlockStyle.TEXT,
            annotations =
                listOf(
                    LinearTextAnnotation(
                        data = LinearTextAnnotationLink(href),
                        start = 0,
                        end = (shown.length - 1).coerceAtLeast(0),
                    ),
                ),
        )
    }

    private fun plain(text: String) =
        LinearText(ids = emptySet(), text = text, blockStyle = LinearTextBlockStyle.TEXT)

    companion object {
        private const val INFORMATIONAL = 'i'
        private const val DEFAULT_PORT = 70
    }
}
