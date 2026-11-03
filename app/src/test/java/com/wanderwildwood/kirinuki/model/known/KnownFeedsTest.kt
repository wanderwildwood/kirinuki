package com.wanderwildwood.kirinuki.model.known

import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KnownFeedsTest {
    private fun names(typed: String) = knownFeedsMatching(typed).map { it.name }

    @Test
    fun `a paper's name finds it, the main feed first`() {
        val found = names("guardian")
        assertEquals("The Guardian", found.first())
        assertTrue("The Guardian: World" in found)
    }

    @Test
    fun `case, hyphens and spaces do not matter`() {
        listOf("low-tech", "Low-tech", "lowtech", "low tech", "LOW TECH").forEach {
            assertEquals("Low-tech Magazine", names(it).firstOrNull(), it)
        }
    }

    @Test
    fun `accents are folded both ways`() {
        assertEquals("Süddeutsche Zeitung", names("sueddeutsche").firstOrNull())
        assertEquals("Süddeutsche Zeitung", names("Süddeutsche").firstOrNull())
        assertEquals("El País", names("el pais").firstOrNull())
        assertEquals("Libération", names("liberation").firstOrNull())
    }

    @Test
    fun `the start of a name is enough`() {
        assertEquals("Hundred Rabbits", names("hundred").firstOrNull())
        assertEquals("Tagesschau", names("tages").firstOrNull())
    }

    @Test
    fun `a site's own address finds it`() {
        assertTrue("NOS Nieuws" in names("nos.nl"))
        assertTrue("Emergence Magazine" in names("emergencemagazine.org"))
    }

    @Test
    fun `an address with a scheme is left to the address box`() {
        assertEquals(emptyList(), names("https://www.theguardian.com"))
        assertEquals(emptyList(), names("gemini://example.org"))
    }

    @Test
    fun `one letter suggests nothing, and two only match a whole word`() {
        assertEquals(emptyList(), names("g"))
        assertEquals(emptyList(), names(" "))
        // "uk" is a keyword of its own; "zz" is inside no keyword and not looked for inside words.
        assertTrue("BBC News" in names("uk"))
        assertEquals(emptyList(), names("zz"))
    }

    @Test
    fun `a name that is not on the list finds nothing`() {
        assertEquals(emptyList(), names("ada whitlock's garden diary"))
    }

    @Test
    fun `never more than the limit`() {
        assertTrue(knownFeedsMatching("the").size <= MAX_SUGGESTIONS)
        assertEquals(2, knownFeedsMatching("the", limit = 2).size)
    }

    @Test
    fun `the list is whole`() {
        assertTrue(KNOWN_FEEDS.size in 40..70, "size ${KNOWN_FEEDS.size}")
        assertEquals(KNOWN_FEEDS.size, KNOWN_FEEDS.map { it.feedUrl }.toSet().size, "a feed is listed twice")
        assertEquals(KNOWN_FEEDS.size, KNOWN_FEEDS.map { it.name }.toSet().size, "a name is listed twice")
        KNOWN_FEEDS.forEach { feed ->
            assertTrue(feed.feedUrl.startsWith("https://"), feed.feedUrl)
            assertTrue(feed.site.startsWith("https://"), feed.site)
            assertTrue(URI(feed.feedUrl).host.contains('.'), feed.feedUrl)
            assertTrue(feed.keywords.isNotEmpty(), feed.name)
            assertTrue(feed.language.isNotBlank(), feed.name)
        }
        // Kompakt readers are mostly in Europe: each of these has at least a few of its own.
        listOf("de", "nl", "fr", "es", "pl").forEach { lang ->
            assertTrue(KNOWN_FEEDS.count { it.language.startsWith(lang) } >= 3, lang)
        }
    }

    @Test
    fun `folding keeps letters and digits only`() {
        assertEquals("100rabbits", folded("100 Rabbits!"))
        assertEquals("osterreich", folded("Österreich"))
    }
}
