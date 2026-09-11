package com.wanderwildwood.kirinuki.model.gemtext

import com.wanderwildwood.kirinuki.model.ParsedArticle
import com.wanderwildwood.kirinuki.model.ParsedFeed
import java.net.URI
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Gemini has no feed format. Instead there is a convention — "subscribing to Gemini
 * pages" — under which an ordinary page *is* a feed if its link lines are dated:
 *
 *     # My gemlog
 *     => two.gmi   2026-09-10 The second thing
 *     => one.gmi   2026-09-02 - The first thing
 *
 * A page qualifies when it has a top-level heading for a title and at least one link
 * whose label begins with an ISO date. Everything else on the page is ignored, which is
 * the point: the same page a person reads is the thing a reader subscribes to.
 *
 * The output is the same [Feed] the RSS and Atom parsers produce, so a gemlog is stored,
 * counted, listed and read by machinery that knows nothing about Gemini.
 */
class GemsubParser {
    fun parse(
        url: String,
        gemtext: String,
    ): ParsedFeed? {
        val lines = gemtext.replace("\r\n", "\n").split('\n')

        val title =
            lines
                .firstOrNull { it.startsWith("# ") }
                ?.removePrefix("# ")
                ?.trim()

        val items =
            lines
                .asSequence()
                .filter { it.startsWith("=>") }
                .mapNotNull { entryOf(url, it) }
                .toList()

        // No dated links means this is a page, not a gemlog. Saying so is better than
        // subscribing someone to something that will never produce an entry.
        if (items.isEmpty()) return null

        return ParsedFeed(
            title = title ?: hostOf(url),
            home_page_url = url,
            feed_url = url,
            items = items,
        )
    }

    private fun entryOf(
        baseUrl: String,
        line: String,
    ): ParsedArticle? {
        val rest = line.removePrefix("=>").trim()
        if (rest.isEmpty()) return null

        val split = rest.indexOfFirst { it.isWhitespace() }
        if (split == -1) return null // a bare link carries no date, so no entry
        val target = rest.substring(0, split)
        val label = rest.substring(split).trim()

        val date =
            try {
                LocalDate.parse(label.take(ISO_LENGTH))
            } catch (e: DateTimeParseException) {
                return null
            }

        // "2026-09-10 - Title", "2026-09-10: Title" and "2026-09-10 Title" all occur.
        val title =
            label
                .drop(ISO_LENGTH)
                .trim()
                .removePrefix("-")
                .removePrefix(":")
                .trim()

        val absolute = absolute(baseUrl, target)

        return ParsedArticle(
            // The URL is the identity. A gemlog has no guids, and the convention's whole
            // premise is that one link line is one entry.
            id = absolute,
            url = absolute,
            title = title.ifBlank { absolute },
            date_published = date.atStartOfDay().atOffset(ZoneOffset.UTC).toString(),
            // No content: the entry is a link, and the text is fetched when it is opened.
            content_text = "",
        )
    }

    private fun absolute(
        baseUrl: String,
        target: String,
    ): String =
        try {
            URI(baseUrl).resolve(target).toString()
        } catch (e: IllegalArgumentException) {
            target
        }

    private fun hostOf(url: String): String =
        try {
            URI(url).host ?: url
        } catch (e: IllegalArgumentException) {
            url
        }

    companion object {
        private const val ISO_LENGTH = 10 // YYYY-MM-DD
    }
}
