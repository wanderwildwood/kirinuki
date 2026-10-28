package com.wanderwildwood.kirinuki.ui.article

import androidx.compose.ui.unit.dp
import com.wanderwildwood.kirinuki.model.readaloud.Spoken
import org.junit.Test
import kotlin.test.assertEquals

class ReadAloudFollowTest {
    @Test
    fun aSentenceIsPlacedByWhereItFallsInItsBlock() {
        // A 1000 px block at 200 px, text 100 long: the sentence from 50 to 75 is half way down.
        assertEquals(700 to 950, sentenceSpan(offset = 200, size = 1000, start = 50, end = 75, length = 100))
        // An empty text is the whole block.
        assertEquals(10 to 60, sentenceSpan(offset = 10, size = 50, start = 0, end = 0, length = 0))
    }

    @Test
    fun theTitleIsTheFirstItemAndBlocksFollowIt() {
        assertEquals(0, itemOf(Spoken(Spoken.TITLE, 0, 3, "abc")))
        assertEquals(1, itemOf(Spoken(0, 0, 3, "abc")))
        assertEquals(8, itemOf(Spoken(7, 0, 3, "abc")))
    }

    @Test
    fun seekStepsAreNeverNarrowerThanAFinger() {
        assertEquals(4, notchesFor(100.dp))
        assertEquals(6, notchesFor(288.dp))
        assertEquals(6, notchesFor(333.dp))
        assertEquals(10, notchesFor(900.dp))
        for (w in 192..900 step 7) {
            val n = notchesFor(w.dp)
            assert(w / n >= 48) { "$w dp in $n steps" }
        }
    }
}
