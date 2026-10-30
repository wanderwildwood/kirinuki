package com.wanderwildwood.kirinuki.ui.licences

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrariesTest {
    private fun asset(name: String): String =
        listOf("src/main/assets/$name", "app/src/main/assets/$name")
            .map(::File)
            .first { it.exists() }
            .readText()

    private fun words(text: String) = text.split(Regex("\\s+")).filter { it.isNotEmpty() }

    @Test
    fun `paragraphs keep every word of every licence in order`() {
        for (name in listOf(
            "LICENSE", "licences/Apache-2.0.txt", "licences/MIT.txt", "licences/MPL-2.0.txt",
            "licences/Unicode-3.0.txt", "licences/GPL-2.0-with-Classpath-Exception.txt",
        )) {
            val text = asset(name)
            assertEquals(name, words(text), words(licenceParagraphs(text).joinToString(" ")))
        }
    }

    @Test
    fun `the GPL text is the whole of version 3`() {
        val text = licenceParagraphs(asset("LICENSE")).joinToString(" ")
        assertTrue(text.startsWith("GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007"))
        assertTrue(text.contains("END OF TERMS AND CONDITIONS"))
        assertTrue(text.contains("How to Apply These Terms to Your New Programs"))
    }

    @Test
    fun `every library names its author and a licence`() {
        assertTrue(LIBRARIES.isNotEmpty())
        LIBRARIES.forEach {
            assertTrue(it.name, it.name.isNotBlank() && it.by.isNotBlank() && it.licence.isNotBlank())
        }
        assertEquals(LIBRARIES.size, LIBRARIES.map { it.name }.toSet().size)
    }

    @Test
    fun `the source link points at the repository`() {
        assertEquals("https://github.com/wanderwildwood/kirinuki", SOURCE_URL)
    }

    @Test
    fun `every licence named in the list has its text`() {
        val named = LIBRARIES.map { it.licence }.toSet()
        if ("MPL-2.0" in named) assertTrue(asset("licences/MPL-2.0.txt").contains("Mozilla Public License Version 2.0"))
        if ("Unicode-3.0" in named) assertTrue(asset("licences/Unicode-3.0.txt").contains("UNICODE LICENSE V3"))
        if (named.any { it.startsWith("GPL-2.0") }) {
            assertTrue(asset("licences/GPL-2.0-with-Classpath-Exception.txt").contains("CLASSPATH EXCEPTION"))
        }
    }
}
