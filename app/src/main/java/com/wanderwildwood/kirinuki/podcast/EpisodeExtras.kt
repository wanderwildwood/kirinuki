package com.wanderwildwood.kirinuki.podcast

import com.squareup.moshi.Moshi

/** One chapter of an episode, from a Podcasting 2.0 chapters file. */
data class Chapter(
    val startMs: Long,
    val title: String,
)

/**
 * One line of a transcript. [startMs] is null for a transcript that carries no times (plain text
 * or a web page): such a line is read, and a tap on it does nothing.
 */
data class TranscriptLine(
    val startMs: Long?,
    val text: String,
)

// What the feed says about an episode's extras: the addresses only. Nothing is fetched until the
// episode is opened.
private val moshi: Moshi = Moshi.Builder().build()

private fun parseJson(text: String): Any? =
    try {
        moshi.adapter(Any::class.java).lenient().fromJson(text.trim().removePrefix("﻿"))
    } catch (_: Exception) {
        null
    }

private fun Any?.asSeconds(): Double? =
    when (this) {
        is Number -> toDouble()
        is String -> trim().toDoubleOrNull()
        else -> null
    }

private fun Double.toMs(): Long = (this * 1000.0).toLong().coerceAtLeast(0L)

/**
 * Chapters from a Podcasting 2.0 JSON chapters file: `{"version":"1.2.0","chapters":[{"startTime":0,"title":"..."}]}`.
 * A chapter marked `"toc": false` is left out, as the spec says (it is for artwork or links, not
 * for the list of contents). Sorted by start; entries with no start or no title are dropped.
 * Anything that is not such a file gives an empty list.
 */
fun parseChaptersJson(text: String): List<Chapter> {
    val root = parseJson(text)
    val entries =
        when (root) {
            is Map<*, *> -> root["chapters"] as? List<*>
            is List<*> -> root
            else -> null
        } ?: return emptyList()
    return entries
        .mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            if (map["toc"] == false) return@mapNotNull null
            val start = map["startTime"].asSeconds() ?: return@mapNotNull null
            val title = (map["title"] as? String)?.trim().orEmpty()
            if (title.isEmpty()) null else Chapter(start.toMs(), title)
        }.sortedBy { it.startMs }
}

/** The chapter playing at [positionMs]: the last one that has started. -1 before the first. */
fun chapterIndexAt(
    chapters: List<Chapter>,
    positionMs: Long,
): Int {
    var low = 0
    var high = chapters.size - 1
    var found = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (chapters[mid].startMs <= positionMs) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}

/** The line being spoken at [positionMs]: the last timed line that has started. -1 if none has. */
fun lineIndexAt(
    lines: List<TranscriptLine>,
    positionMs: Long,
): Int {
    var low = 0
    var high = lines.size - 1
    var found = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        // Untimed lines (only in a transcript that has no times at all) never count as started.
        val start = lines[mid].startMs
        if (start != null && start <= positionMs) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}

// h:mm:ss.mmm, mm:ss.mmm or mm:ss,mmm. No braces in these on purpose: a bare closing brace in a
// regex passes the JVM tests and crashes on the phone.
private val TIMESTAMP = Regex("""(?:(\d+):)?(\d\d?):(\d\d)(?:[.,](\d+))?""")
private val TAGS = Regex("<[^>]*>")
private val BLANK_LINES = Regex("\n[ \t]*\n+")

private fun parseTimestamp(text: String): Long? {
    val m = TIMESTAMP.find(text.trim()) ?: return null
    val hours = m.groupValues[1].toLongOrNull() ?: 0L
    val minutes = m.groupValues[2].toLong()
    val seconds = m.groupValues[3].toLong()
    val fraction = m.groupValues[4]
    // ".5" is half a second, ".05" is fifty milliseconds: pad to three places, cut beyond.
    val millis = if (fraction.isEmpty()) 0L else fraction.padEnd(3, '0').take(3).toLong()
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
}

private fun String.unescapeBasic(): String =
    replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

/**
 * WebVTT or SubRip: blocks separated by a blank line, each with a "start --> end" line and the
 * words under it. The two differ only in the header, the comma or full stop before the
 * milliseconds, and an optional cue name, so one reader does both. Markup inside a cue is
 * dropped. A block with no readable time is skipped rather than failing the file.
 */
fun parseCueTranscript(text: String): List<TranscriptLine> {
    val normal = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
    val lines = mutableListOf<TranscriptLine>()
    for (block in normal.split(BLANK_LINES)) {
        val rows = block.lines()
        val at = rows.indexOfFirst { "-->" in it }
        if (at < 0) continue
        val start = parseTimestamp(rows[at].substringBefore("-->")) ?: continue
        val words =
            rows
                .drop(at + 1)
                .joinToString(" ") { it.trim() }
                .replace(TAGS, "")
                .unescapeBasic()
                .replace(Regex("\\s+"), " ")
                .trim()
        if (words.isNotEmpty()) lines += TranscriptLine(start, words)
    }
    return lines
}

/**
 * The Podcasting 2.0 JSON transcript: `{"version":"1.0.0","segments":[{"speaker":"..","startTime":0,"endTime":4,"body":".."}]}`.
 * The speaker is put before the words when it changes from the line above.
 */
fun parseJsonTranscript(text: String): List<TranscriptLine> {
    val root = parseJson(text)
    val segments =
        when (root) {
            is Map<*, *> -> root["segments"] as? List<*>
            is List<*> -> root
            else -> null
        } ?: return emptyList()
    var lastSpeaker: String? = null
    val lines = mutableListOf<TranscriptLine>()
    for (entry in segments) {
        val map = entry as? Map<*, *> ?: continue
        val start = map["startTime"].asSeconds() ?: continue
        val body = (map["body"] as? String)?.trim().orEmpty()
        if (body.isEmpty()) continue
        val speaker = (map["speaker"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val said = if (speaker != null && speaker != lastSpeaker) "$speaker: $body" else body
        if (speaker != null) lastSpeaker = speaker
        lines += TranscriptLine(start.toMs(), said)
    }
    return lines.sortedBy { it.startMs }
}

/** Text or a web page with no times: one line for each paragraph (or each line, if there are no gaps). */
fun parsePlainTranscript(
    text: String,
    html: Boolean = false,
): List<TranscriptLine> {
    var body = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
    if (html) {
        body =
            body
                .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
                .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</li>|</h[1-6]>"), "\n\n")
                .replace(TAGS, "")
                .unescapeBasic()
    }
    val paragraphs = body.split(BLANK_LINES).map { it.lines().joinToString(" ") { l -> l.trim() }.replace(Regex("\\s+"), " ").trim() }
    val kept = paragraphs.filter { it.isNotEmpty() }
    // A file with no blank lines at all is one line per line.
    val units = if (kept.size == 1 && body.trim().contains('\n')) body.lines().map { it.trim() }.filter { it.isNotEmpty() } else kept
    return units.map { TranscriptLine(null, it) }
}

/** What a transcript file is, from its type and then from its first words; never from the type alone. */
fun parseTranscript(
    text: String,
    type: String?,
): List<TranscriptLine> {
    val head = text.removePrefix("﻿").trimStart()
    val kind = type?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    return when {
        head.startsWith("WEBVTT") -> parseCueTranscript(text)
        head.startsWith("{") || head.startsWith("[") -> parseJsonTranscript(text).ifEmpty { if (kind.contains("json")) emptyList() else parsePlainTranscript(text) }
        "-->" in head && (kind.contains("vtt") || kind.contains("srt") || kind.contains("subrip") || kind.isEmpty() || kind.startsWith("text/") || kind.startsWith("application/")) ->
            parseCueTranscript(text).ifEmpty { parsePlainTranscript(text) }
        kind == "text/html" || head.take(200).contains("<html", ignoreCase = true) -> parsePlainTranscript(text, html = true)
        else -> parsePlainTranscript(text)
    }
}

/** How much a transcript type is liked: lower is better. Times matter most, so cue files come first. */
private fun transcriptRank(
    type: String?,
    url: String?,
): Int {
    val t = type?.lowercase().orEmpty()
    val path = url?.substringBefore('?')?.lowercase().orEmpty()
    return when {
        "vtt" in t || path.endsWith(".vtt") -> 0
        "srt" in t || "subrip" in t || path.endsWith(".srt") -> 1
        "json" in t || path.endsWith(".json") -> 2
        t == "text/plain" || path.endsWith(".txt") -> 3
        t == "text/html" || path.endsWith(".html") -> 4
        else -> 5
    }
}

/** From the several a feed may offer for one episode, the one worth fetching. */
fun <T> bestTranscript(
    offers: List<T>,
    urlOf: (T) -> String?,
    typeOf: (T) -> String?,
): T? =
    offers
        .filter { urlOf(it)?.startsWith("http", ignoreCase = true) == true }
        .minByOrNull { transcriptRank(typeOf(it), urlOf(it)) }
