package com.wanderwildwood.kirinuki.net.gemini

import com.wanderwildwood.kirinuki.util.sloppyLinkToStrictURLNoThrows
import org.junit.Test
import java.net.MalformedURLException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class GeminiUrlTest {
    @Test
    fun `the jvm really cannot parse a gemini address unaided`() {
        // The control. Without this failing, nothing below proves anything.
        assertFailsWith<MalformedURLException> {
            java.net.URL("gemini://example.space/log/")
        }
    }

    @Test
    fun `a gemini address survives being parsed`() {
        val url = parseUrlWithGemini("gemini://example.space/log/")

        assertEquals("gemini", url.protocol)
        assertEquals("example.space", url.host)
        assertEquals("gemini://example.space/log/", url.toString())
    }

    @Test
    fun `a gemini address survives the round trip the database puts it through`() {
        // Room stores the string and reads it back through this. Before the handler it
        // came back as http:// -- a subscribed capsule silently turning into nothing.
        val restored = sloppyLinkToStrictURLNoThrows("gemini://example.space/log/")

        assertEquals("gemini://example.space/log/", restored.toString())
        assertNotEquals("http://", restored.toString())
    }

    @Test
    fun `ordinary addresses are untouched`() {
        assertEquals(
            "https://example.com/feed.xml",
            sloppyLinkToStrictURLNoThrows("https://example.com/feed.xml").toString(),
        )
    }

    @Test
    fun `comparing two gemini addresses does not need the network`() {
        // The inherited handler resolves the host to compare, which is a DNS lookup on
        // whichever thread happened to put a feed in a set.
        val one = parseUrlWithGemini("gemini://example.space/a")
        val two = parseUrlWithGemini("gemini://example.space/a")
        val three = parseUrlWithGemini("gemini://example.space/b")

        assertEquals(one, two)
        assertEquals(one.hashCode(), two.hashCode())
        assertNotEquals(one, three)
    }
}
