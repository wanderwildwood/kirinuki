package com.wanderwildwood.kirinuki.podcast

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalFolderTest {
    @Test
    fun numbersSortAsNumbers() {
        val names = listOf("Part 10.mp3", "Part 2.mp3", "part 1.mp3", "Part 02b.mp3", "Intro.mp3")
        assertEquals(
            listOf("Intro.mp3", "part 1.mp3", "Part 2.mp3", "Part 02b.mp3", "Part 10.mp3"),
            names.sortedWith(naturalOrder),
        )
    }

    @Test
    fun foldersKeepTheirFilesTogether() {
        val paths = listOf("Disc 2/01 Rain.mp3", "Disc 1/02 Hill.mp3", "Disc 10/01 End.mp3", "Disc 1/01 Road.mp3")
        assertEquals(
            listOf("Disc 1/01 Road.mp3", "Disc 1/02 Hill.mp3", "Disc 2/01 Rain.mp3", "Disc 10/01 End.mp3"),
            paths.sortedWith(naturalOrder),
        )
    }

    @Test
    fun aShorterNameComesFirst() {
        assertTrue(naturalOrder.compare("Walk", "Walk 2") < 0)
        assertEquals(0, naturalOrder.compare("walk 3", "Walk 3"))
    }

    @Test
    fun theTitleIsTheNameWithoutItsEnding() {
        assertEquals("Episode 4 - The river", titleFromName("Episode 4 - The river.mp3"))
        assertEquals("no ending", titleFromName("no ending"))
        assertEquals(".hidden", titleFromName(".hidden"))
    }
}
