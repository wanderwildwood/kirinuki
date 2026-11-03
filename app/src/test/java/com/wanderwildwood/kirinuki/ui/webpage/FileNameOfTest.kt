package com.wanderwildwood.kirinuki.ui.webpage

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileNameOfTest {
    @Test
    fun `the title of a text file is its own name`() {
        assertEquals("notes.c.txt", fileNameOf("https://example.org/etc/notes.c.txt"))
        assertEquals("a walk.txt", fileNameOf("https://example.org/a%20walk.txt?raw=1#top"))
        assertNull(fileNameOf("https://example.org/"))
    }
}
