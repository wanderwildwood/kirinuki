package com.wanderwildwood.kirinuki.model.readaloud

import com.wanderwildwood.kirinuki.model.html.LinearArticle
import com.wanderwildwood.kirinuki.model.html.LinearBlockQuote
import com.wanderwildwood.kirinuki.model.html.LinearElement
import com.wanderwildwood.kirinuki.model.html.LinearImage
import com.wanderwildwood.kirinuki.model.html.LinearListItem
import com.wanderwildwood.kirinuki.model.html.LinearText
import com.wanderwildwood.kirinuki.model.html.LinearTextBlockStyle
import java.text.BreakIterator
import java.util.Locale

/**
 * One sentence of an article, as it is spoken. [element] is the article element it came from,
 * or [TITLE] for the title; [start] and [end] are where it sits in that element's text, so the
 * reader can underline it. In a list item or a quote, whose text is gathered from more than one
 * piece, the range is into the gathered text and only says which block is being read.
 */
data class Spoken(
    val element: Int,
    val start: Int,
    val end: Int,
    val text: String,
) {
    companion object {
        const val TITLE = -1
    }
}

/**
 * What read aloud says, sentence by sentence: the title, then each block of the article that is
 * prose. Code and tables are passed over, since read out they are noise; a picture is read by
 * its caption, where it has one.
 *
 * The sentence is the unit of everything: speech is queued a sentence at a time, the reader's
 * underline and its page follow a sentence at a time, and a step back or on is a sentence. On
 * E Ink that is the right grain, since nothing on screen changes between two sentences.
 */
object ReadAloudScript {
    /** A text-to-speech engine takes about 4,000 characters at once; a sentence past this is cut. */
    const val MAX_PIECE = 1000

    fun of(
        article: LinearArticle,
        title: String?,
        locale: Locale = Locale.getDefault(),
    ): List<Spoken> {
        val out = mutableListOf<Spoken>()
        title?.takeIf { it.isNotBlank() }?.let { out += sentences(Spoken.TITLE, it, locale) }
        article.elements.forEachIndexed { index, element ->
            val text = speakable(element) ?: return@forEachIndexed
            out += sentences(index, text, locale)
        }
        return out
    }

    /** The words of one block, or null where there is nothing in it to say. */
    internal fun speakable(element: LinearElement): String? =
        when (element) {
            is LinearText ->
                element.text.takeIf { element.blockStyle == LinearTextBlockStyle.TEXT }
            is LinearListItem -> element.content.mapNotNull(::speakable).joinToString("\n")
            is LinearBlockQuote -> element.content.mapNotNull(::speakable).joinToString("\n")
            is LinearImage -> element.caption?.text
            else -> null
        }?.takeIf { it.isNotBlank() }

    /** [text] cut into sentences, each trimmed of the space around it, none longer than [MAX_PIECE]. */
    fun sentences(
        element: Int,
        text: String,
        locale: Locale = Locale.getDefault(),
    ): List<Spoken> {
        val out = mutableListOf<Spoken>()
        val breaks = BreakIterator.getSentenceInstance(locale)
        breaks.setText(text)
        var from = breaks.first()
        var to = breaks.next()
        while (to != BreakIterator.DONE) {
            pieces(text, from, to).forEach { (s, e) -> out += Spoken(element, s, e, text.substring(s, e)) }
            from = to
            to = breaks.next()
        }
        return out
    }

    /** One sentence's range, trimmed, and cut at a space where it runs past [MAX_PIECE]. */
    private fun pieces(
        text: String,
        from: Int,
        to: Int,
    ): List<Pair<Int, Int>> {
        var s = from
        var e = to
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        if (s >= e) return emptyList()
        val out = mutableListOf<Pair<Int, Int>>()
        while (e - s > MAX_PIECE) {
            val space = text.lastIndexOf(' ', s + MAX_PIECE).takeIf { it > s } ?: (s + MAX_PIECE)
            out += s to space
            s = space
            while (s < e && text[s].isWhitespace()) s++
        }
        if (s < e) out += s to e
        return out
    }
}

/**
 * Where read aloud is, in time as well as in sentences. The engine says nothing about how long a
 * sentence takes until it has said it, so each one is reckoned from its length at an ordinary
 * pace; that is what the time left and the seek steps are measured in. A step lands on the start
 * of a sentence, never inside one.
 */
class ReadAloudTimeline(
    script: List<Spoken>,
    private val charsPerSecond: Float = CHARS_PER_SECOND,
    private val pauseMs: Long = PAUSE_MS,
) {
    val size: Int = script.size

    /** When each sentence starts, in reckoned milliseconds, and after the last, the whole. */
    private val starts: LongArray =
        LongArray(script.size + 1).also { at ->
            for (i in script.indices) {
                at[i + 1] = at[i] + msFor(script[i].text)
            }
        }

    val totalMs: Long get() = starts[size]

    private fun msFor(text: String): Long =
        // A pause between sentences is part of what a sentence costs.
        ((text.length / charsPerSecond) * 1000f).toLong() + pauseMs

    fun startMs(index: Int): Long = starts[index.coerceIn(0, size)]

    fun remainingMs(index: Int): Long = totalMs - startMs(index)

    /** The sentence playing at [ms]. */
    fun indexAt(ms: Long): Int {
        if (size == 0) return 0
        if (ms <= 0L) return 0
        if (ms >= totalMs) return size - 1
        // The last start at or before ms.
        var lo = 0
        var hi = size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= ms) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** [by] sentences on from [index] (back where negative), held inside the article. */
    fun step(
        index: Int,
        by: Int,
    ): Int = if (size == 0) 0 else (index + by).coerceIn(0, size - 1)

    /** Which of [notches] equal parts of the time [index] starts in. */
    fun notchOf(
        index: Int,
        notches: Int,
    ): Int {
        if (size == 0 || totalMs == 0L) return 0
        return ((startMs(index) * notches) / totalMs).toInt().coerceIn(0, notches - 1)
    }

    /**
     * The sentence a press on [notch] goes to: the one playing at the start of that part, so
     * the first notch is the beginning and every notch is a place a sentence starts.
     */
    fun indexOfNotch(
        notch: Int,
        notches: Int,
    ): Int {
        if (size == 0) return 0
        val at = (totalMs * notch.coerceIn(0, notches - 1)) / notches
        val i = indexAt(at)
        // indexAt gives the sentence under that moment; the notch means the next to start there,
        // unless it starts exactly on it. Never past the last.
        return if (startMs(i) < at && i + 1 < size) i + 1 else i
    }

    companion object {
        /**
         * A timeline at the pace the voice has been heard to keep, once it has kept one: a
         * measured pace already holds the pauses between sentences.
         */
        fun at(
            script: List<Spoken>,
            measuredCharsPerSecond: Float?,
        ): ReadAloudTimeline =
            if (measuredCharsPerSecond != null && measuredCharsPerSecond > 0f) {
                ReadAloudTimeline(script, measuredCharsPerSecond, pauseMs = 0L)
            } else {
                ReadAloudTimeline(script)
            }

        /** How fast a voice went: [chars] said in [ms], rounded to a half so it settles. */
        fun paceOf(
            chars: Int,
            ms: Long,
        ): Float? {
            if (chars < MIN_CHARS_TO_MEASURE || ms <= 0L) return null
            val cps = chars * 1000f / ms
            return (Math.round(cps * 2f) / 2f).takeIf { it > 0f }
        }

        /** A couple of sentences before the pace is trusted over the reckoning. */
        const val MIN_CHARS_TO_MEASURE = 100

        /** About 150 words a minute, which is where most engines speak at their own rate. */
        const val CHARS_PER_SECOND = 15f
        const val PAUSE_MS = 300L
    }
}
