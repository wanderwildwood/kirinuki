package com.wanderwildwood.kirinuki.model.gopher

import com.wanderwildwood.kirinuki.model.ParsedArticle
import com.wanderwildwood.kirinuki.model.ParsedFeed
import java.net.URI
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * A phlog is to gopher what a gemlog is to Gemini: an ordinary menu is a feed when its text-file
 * lines are dated.
 *
 *     iMy phlog
 *     02026-09-10 The second thing<TAB>/log/two<TAB>example.org<TAB>70
 *     02026-09-02 - The first thing<TAB>/log/one<TAB>example.org<TAB>70
 *
 * A menu qualifies when at least one line of type 0 (a text file) has a label that begins with an
 * ISO date. The same menu a person reads is what a reader subscribes to, and the entries are
 * the files it links to.
 */
class GopherLogParser {
    fun parse(
        url: String,
        menu: String,
    ): ParsedFeed? {
        val lines =
            menu
                .replace("\r\n", "\n")
                .lineSequence()
                .takeWhile { it != "." }
                .toList()

        val items = lines.mapNotNull { entryOf(url, it) }
        // No dated text files means this is a menu to read, not a log to follow.
        if (items.isEmpty()) return null

        val title =
            lines
                .firstOrNull { it.startsWith("i") && it.drop(1).substringBefore('\t').isNotBlank() }
                ?.drop(1)
                ?.substringBefore('\t')
                ?.trim()

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
        if (!line.startsWith(TEXT_FILE)) return null
        val fields = line.drop(1).split('\t')
        val label = fields.getOrNull(0).orEmpty().trim()
        val selector = fields.getOrNull(1).orEmpty()

        val date =
            try {
                LocalDate.parse(label.take(ISO_LENGTH))
            } catch (e: DateTimeParseException) {
                return null
            }

        val title =
            label
                .drop(ISO_LENGTH)
                .trim()
                .removePrefix("-")
                .removePrefix(":")
                .trim()

        val base = runCatching { URI(baseUrl) }.getOrNull()
        val host = fields.getOrNull(2)?.trim().orEmpty().ifBlank { base?.host.orEmpty() }
        if (host.isBlank()) return null
        val port = fields.getOrNull(3)?.trim()?.toIntOrNull() ?: DEFAULT_PORT
        val link = "gopher://$host:$port/$TEXT_FILE$selector"

        return ParsedArticle(
            // The address is the identity, as it is for a gemlog: one line is one entry.
            id = link,
            url = link,
            title = title.ifBlank { link },
            date_published = date.atStartOfDay().atOffset(ZoneOffset.UTC).toString(),
            // The text is fetched with the sync, so it can be read with the radio off.
            content_text = "",
        )
    }

    private fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull() ?: url

    companion object {
        private const val TEXT_FILE = '0'
        private const val ISO_LENGTH = 10
        private const val DEFAULT_PORT = 70
    }
}
