package com.wanderwildwood.kirinuki.net

import com.wanderwildwood.kirinuki.util.sloppyLinkToStrictURLNoThrows
import org.junit.Test
import java.net.MalformedURLException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class UrlSchemesTest {
    @Test
    fun `the jvm really cannot parse a gemini address unaided`() {
        // The control. Without this failing, nothing below proves anything.
        assertFailsWith<MalformedURLException> {
            java.net.URL("gemini://example.space/log/")
        }
    }

    @Test
    fun `a gemini address survives being parsed`() {
        val url = parseUrlLeniently("gemini://example.space/log/")

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
    fun `a gopher address survives too`() {
        val url = parseUrlLeniently("gopher://example.org:70/1/phlog")

        assertEquals("gopher", url.protocol)
        assertEquals("example.org", url.host)
        assertEquals(70, url.port)
    }

    @Test
    fun `a gopher address survives the database round trip`() {
        assertEquals(
            "gopher://example.org/1/phlog",
            sloppyLinkToStrictURLNoThrows("gopher://example.org/1/phlog").toString(),
        )
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
        val one = parseUrlLeniently("gemini://example.space/a")
        val two = parseUrlLeniently("gemini://example.space/a")
        val three = parseUrlLeniently("gemini://example.space/b")

        assertEquals(one, two)
        assertEquals(one.hashCode(), two.hashCode())
        assertNotEquals(one, three)
    }
}
