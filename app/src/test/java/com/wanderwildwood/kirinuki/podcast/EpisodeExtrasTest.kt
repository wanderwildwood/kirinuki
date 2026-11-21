package com.wanderwildwood.kirinuki.podcast

import com.wanderwildwood.kirinuki.model.gofeed.GoExtension
import com.wanderwildwood.kirinuki.model.gofeed.podcastLinksFrom
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// All text here is made up: a show called "The Lamp Hour" and nobody real.
class EpisodeExtrasTest {
    // ---- Chapters ----------------------------------------------------------------------

    @Test
    fun chaptersJsonIsReadInOrder() {
        val json =
            """
            {"version":"1.2.0","chapters":[
              {"startTime":125.5,"title":"The listener mail"},
              {"startTime":0,"title":"Opening"},
              {"startTime":600,"title":"Closing notes","url":"https://example.org/notes"}
            ]}
            """.trimIndent()
        val chapters = parseChaptersJson(json)
        assertEquals(listOf("Opening", "The listener mail", "Closing notes"), chapters.map { it.title })
        assertEquals(listOf(0L, 125_500L, 600_000L), chapters.map { it.startMs })
    }

    @Test
    fun chaptersLeftOutOfTheContentsAreDropped() {
        val json = """{"chapters":[{"startTime":0,"title":"Start"},{"startTime":30,"title":"Picture","toc":false}]}"""
        assertEquals(listOf("Start"), parseChaptersJson(json).map { it.title })
    }

    @Test
    fun chaptersWithNoTitleOrStartAreSkipped() {
        val json = """{"chapters":[{"startTime":0,"title":"  "},{"title":"No start"},{"startTime":"12","title":"Text start"},7,null]}"""
        val chapters = parseChaptersJson(json)
        assertEquals(1, chapters.size)
        assertEquals(12_000L, chapters[0].startMs)
    }

    @Test
    fun chaptersMalformedGiveNothing() {
        assertTrue(parseChaptersJson("").isEmpty())
        assertTrue(parseChaptersJson("not json at all").isEmpty())
        assertTrue(parseChaptersJson("""{"chapters":""").isEmpty())
        assertTrue(parseChaptersJson("""{"chapters":"nope"}""").isEmpty())
        assertTrue(parseChaptersJson("""{"version":"1"}""").isEmpty())
        assertTrue(parseChaptersJson("<html>an error page</html>").isEmpty())
    }

    @Test
    fun chapterAtAPosition() {
        val chapters = listOf(Chapter(0, "A"), Chapter(60_000, "B"), Chapter(120_000, "C"))
        assertEquals(0, chapterIndexAt(chapters, 0))
        assertEquals(0, chapterIndexAt(chapters, 59_999))
        assertEquals(1, chapterIndexAt(chapters, 60_000))
        assertEquals(2, chapterIndexAt(chapters, 9_999_999))
        assertEquals(-1, chapterIndexAt(listOf(Chapter(5_000, "Late")), 1_000))
        assertEquals(-1, chapterIndexAt(emptyList(), 1_000))
    }

    // ---- Transcripts -------------------------------------------------------------------

    @Test
    fun vttIsReadWithItsMarkupAndNotesLeftOut() {
        val vtt =
            "WEBVTT\n\nNOTE made up for a test\n\n" +
                "intro\n00:00:01.000 --> 00:00:04.000\nWelcome to <v Ada>The Lamp Hour.</v>\n\n" +
                "00:05.500 --> 00:08.000 align:start\nToday: lamps &amp; shades.\nAnd a second line.\n\n" +
                "01:02:03.250 --> 01:02:05.000\nLate.\n"
        val lines = parseTranscript(vtt, "text/vtt")
        assertEquals(3, lines.size)
        assertEquals(TranscriptLine(1_000, "Welcome to The Lamp Hour."), lines[0])
        assertEquals(TranscriptLine(5_500, "Today: lamps & shades. And a second line."), lines[1])
        assertEquals(3_723_250L, lines[2].startMs)
    }

    @Test
    fun srtIsRead() {
        val srt = "﻿1\r\n00:00:02,500 --> 00:00:04,000\r\nGood evening.\r\n\r\n2\r\n00:00:05,000 --> 00:00:07,000\r\nThe kettle is on.\r\n"
        val lines = parseTranscript(srt, "application/x-subrip")
        assertEquals(listOf(2_500L, 5_000L), lines.map { it.startMs })
        assertEquals(listOf("Good evening.", "The kettle is on."), lines.map { it.text })
    }

    @Test
    fun vttFractionsArePlaces() {
        assertEquals(500L, parseCueTranscript("00:00:00.5 --> 00:00:01.0\nx")[0].startMs)
        assertEquals(50L, parseCueTranscript("00:00:00.05 --> 00:00:01.0\nx")[0].startMs)
        assertEquals(7_000L, parseCueTranscript("00:07 --> 00:09\nx")[0].startMs)
    }

    @Test
    fun jsonTranscriptNamesTheSpeakerWhenItChanges() {
        val json =
            """
            {"version":"1.0.0","segments":[
              {"speaker":"Host","startTime":0,"endTime":3,"body":"Hello."},
              {"speaker":"Host","startTime":3.5,"endTime":6,"body":"Welcome."},
              {"speaker":"Guest","startTime":7,"endTime":9,"body":"Thanks."},
              {"startTime":10,"body":""}
            ]}
            """.trimIndent()
        val lines = parseTranscript(json, "application/json")
        assertEquals(listOf("Host: Hello.", "Welcome.", "Guest: Thanks."), lines.map { it.text })
        assertEquals(listOf(0L, 3_500L, 7_000L), lines.map { it.startMs })
    }

    @Test
    fun plainTextIsParagraphsWithNoTimes() {
        val lines = parseTranscript("First paragraph\nstill first.\n\nSecond paragraph.\n", "text/plain")
        assertEquals(listOf("First paragraph still first.", "Second paragraph."), lines.map { it.text })
        assertTrue(lines.all { it.startMs == null })
    }

    @Test
    fun htmlIsStrippedToText() {
        val html = "<html><head><style>p { color: red; }</style></head><body><p>One &amp; two.</p><p>Three.</p><script>var x = {};</script></body></html>"
        val lines = parseTranscript(html, "text/html")
        assertEquals(listOf("One & two.", "Three."), lines.map { it.text })
    }

    @Test
    fun theFileIsTrustedOverTheType() {
        // Served as text/plain, but it is a WebVTT file.
        val lines = parseTranscript("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHi.\n", "text/plain")
        assertEquals(1_000L, lines.single().startMs)
    }

    @Test
    fun transcriptsMalformedGiveNothingOrPlainText() {
        assertTrue(parseTranscript("", "text/vtt").isEmpty())
        assertTrue(parseTranscript("   \n\n  ", null).isEmpty())
        assertTrue(parseTranscript("WEBVTT\n\nno timing here\n", "text/vtt").isEmpty())
        assertTrue(parseTranscript("{\"segments\":", "application/json").isEmpty())
        assertTrue(parseTranscript("""{"segments":"x"}""", "application/json").isEmpty())
        assertTrue(parseCueTranscript("00:xx --> 00:01\nwords").isEmpty())
    }

    @Test
    fun lineAtAPosition() {
        val lines = listOf(TranscriptLine(1_000, "a"), TranscriptLine(5_000, "b"), TranscriptLine(9_000, "c"))
        assertEquals(-1, lineIndexAt(lines, 500))
        assertEquals(0, lineIndexAt(lines, 1_000))
        assertEquals(1, lineIndexAt(lines, 8_999))
        assertEquals(2, lineIndexAt(lines, 99_000))
        assertEquals(-1, lineIndexAt(listOf(TranscriptLine(null, "x")), 5_000))
    }

    // ---- The feed's tags ---------------------------------------------------------------

    private fun tag(vararg attrs: Pair<String, String>) = GoExtension("x", "", mapOf(*attrs), emptyMap())

    @Test
    fun feedTagsAreFoundAndTheBestTranscriptChosen() {
        val ext =
            mapOf(
                "podcast" to
                    mapOf(
                        "chapters" to listOf(tag("url" to "https://example.org/lamp/1/chapters.json", "type" to "application/json+chapters")),
                        "transcript" to
                            listOf(
                                tag("url" to "https://example.org/lamp/1.html", "type" to "text/html"),
                                tag("url" to "https://example.org/lamp/1.srt", "type" to "application/x-subrip"),
                                tag("url" to "https://example.org/lamp/1.vtt", "type" to "text/vtt"),
                            ),
                    ),
            )
        val links = podcastLinksFrom(ext)
        assertEquals("https://example.org/lamp/1/chapters.json", links.chaptersUrl)
        assertEquals("https://example.org/lamp/1.vtt", links.transcriptUrl)
        assertEquals("text/vtt", links.transcriptType)
    }

    @Test
    fun feedTagsWithoutAnAddressAreIgnored() {
        val ext =
            mapOf(
                "podcast" to
                    mapOf(
                        "chapters" to listOf(tag("type" to "application/json+chapters"), tag("url" to "ftp://example.org/c")),
                        "transcript" to listOf(tag("type" to "text/vtt")),
                    ),
                "other" to mapOf("transcript" to listOf(tag("url" to "https://example.org/t.vtt"))),
            )
        val links = podcastLinksFrom(ext)
        assertNull(links.chaptersUrl)
        assertNull(links.transcriptUrl)
        assertNull(podcastLinksFrom(null).chaptersUrl)
    }
}
