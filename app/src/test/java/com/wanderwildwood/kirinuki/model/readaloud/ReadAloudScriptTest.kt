package com.wanderwildwood.kirinuki.model.readaloud

import com.wanderwildwood.kirinuki.model.html.LinearArticle
import com.wanderwildwood.kirinuki.model.html.LinearListItem
import com.wanderwildwood.kirinuki.model.html.LinearTable
import com.wanderwildwood.kirinuki.model.html.LinearText
import com.wanderwildwood.kirinuki.model.html.LinearTextBlockStyle
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadAloudScriptTest {
    private fun text(
        s: String,
        style: LinearTextBlockStyle = LinearTextBlockStyle.TEXT,
    ) = LinearText(ids = emptySet(), text = s, annotations = emptyList(), blockStyle = style)

    @Test
    fun sentencesKeepWhereTheySitInTheirBlock() {
        val body = "The river was high. It rained all week!  Then it stopped?"
        val out = ReadAloudScript.sentences(3, body, Locale.ENGLISH)
        assertEquals(listOf("The river was high.", "It rained all week!", "Then it stopped?"), out.map { it.text })
        out.forEach { assertEquals(it.text, body.substring(it.start, it.end)) }
        assertTrue(out.all { it.element == 3 })
    }

    @Test
    fun titleFirstThenProseAndCodeAndTablesPassedOver() {
        val article =
            LinearArticle(
                elements =
                    listOf(
                        text("First block."),
                        text("val x = 1", LinearTextBlockStyle.CODE_BLOCK),
                        LinearTable(ids = emptySet(), rowCount = 1, colCount = 1, cells = listOf(), leftToRight = true),
                        LinearListItem(ids = emptySet(), orderedIndex = 1, content = listOf(text("A listed thing."))),
                        text("   "),
                        text("Last. Really last."),
                    ),
            )
        val out = ReadAloudScript.of(article, "A walk in the hills", Locale.ENGLISH)
        assertEquals(
            listOf(Spoken.TITLE, 0, 3, 5, 5),
            out.map { it.element },
        )
        assertEquals("A walk in the hills", out.first().text)
    }

    @Test
    fun aSentenceTooLongForTheEngineIsCutAtASpace() {
        val word = "word "
        val body = word.repeat(600).trim() + "."
        val out = ReadAloudScript.sentences(0, body, Locale.ENGLISH)
        assertTrue(out.size >= 3)
        assertTrue(out.all { it.text.length <= ReadAloudScript.MAX_PIECE })
        // Nothing lost, nothing doubled.
        assertEquals(body.replace(" ", ""), out.joinToString("") { it.text.replace(" ", "") })
        out.forEach { assertEquals(it.text, body.substring(it.start, it.end)) }
    }

    private fun script(vararg lengths: Int) =
        lengths.mapIndexed { i, n -> Spoken(element = i, start = 0, end = n, text = "x".repeat(n)) }

    @Test
    fun timeIsReckonedFromLength() {
        // 15 characters a second and 300 ms between: 30 chars = 2300 ms, 15 = 1300 ms.
        val t = ReadAloudTimeline(script(30, 15, 45))
        assertEquals(0L, t.startMs(0))
        assertEquals(2300L, t.startMs(1))
        assertEquals(3600L, t.startMs(2))
        assertEquals(3600L + 3300L, t.totalMs)
        assertEquals(t.totalMs - 2300L, t.remainingMs(1))
    }

    @Test
    fun theSentenceAtAMoment() {
        val t = ReadAloudTimeline(script(30, 15, 45))
        assertEquals(0, t.indexAt(-5))
        assertEquals(0, t.indexAt(0))
        assertEquals(0, t.indexAt(2299))
        assertEquals(1, t.indexAt(2300))
        assertEquals(2, t.indexAt(3600))
        assertEquals(2, t.indexAt(t.totalMs + 1000))
    }

    @Test
    fun stepsStayInsideTheArticle() {
        val t = ReadAloudTimeline(script(10, 10, 10))
        assertEquals(0, t.step(0, -1))
        assertEquals(1, t.step(0, 1))
        assertEquals(2, t.step(2, 1))
        assertEquals(0, ReadAloudTimeline(emptyList()).step(0, 1))
    }

    @Test
    fun everyNotchLandsOnASentenceThatStartsInIt() {
        // Forty equal sentences, ten parts: four sentences a part.
        val t = ReadAloudTimeline(script(*IntArray(40) { 30 }))
        for (n in 0 until 10) {
            val i = t.indexOfNotch(n, 10)
            assertEquals(n * 4, i)
            assertEquals(n, t.notchOf(i, 10))
        }
    }

    @Test
    fun notchesGoForwardAndNeverPastTheEnd() {
        val t = ReadAloudTimeline(script(5, 200, 7, 90, 3, 400, 12, 60))
        val landed = (0 until 6).map { t.indexOfNotch(it, 6) }
        assertEquals(landed.sorted(), landed)
        assertTrue(landed.all { it in 0 until t.size })
        assertEquals(0, landed.first())
        // A notch past the end is the last part, never out of the article.
        assertTrue(t.indexOfNotch(99, 6) < t.size)
        assertEquals(0, ReadAloudTimeline(emptyList()).indexOfNotch(3, 6))
    }

    @Test
    fun aPaceIsMeasuredOnlyOnceThereIsEnoughToMeasure() {
        assertEquals(null, ReadAloudTimeline.paceOf(chars = 40, ms = 2000))
        assertEquals(null, ReadAloudTimeline.paceOf(chars = 400, ms = 0))
        // 240 characters in 10 s is 24 a second; 250 in 10 s rounds to the half, 25.
        assertEquals(24f, ReadAloudTimeline.paceOf(chars = 240, ms = 10_000))
        assertEquals(25f, ReadAloudTimeline.paceOf(chars = 252, ms = 10_000))
    }

    @Test
    fun aMeasuredPaceHoldsItsOwnPauses() {
        val s = script(30, 30)
        // Reckoned: 30 chars at 15/s plus 300 ms each.
        assertEquals(4600L, ReadAloudTimeline.at(s, null).totalMs)
        // Heard at 30 a second, pauses and all: a second each.
        assertEquals(2000L, ReadAloudTimeline.at(s, 30f).totalMs)
    }
}
