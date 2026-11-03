package com.wanderwildwood.kirinuki.net

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScannedAddressTest {
    @Test
    fun `a feed address comes through as it is`() {
        assertEquals("https://example.org/feed.xml", scannedAddress("https://example.org/feed.xml"))
        assertEquals("http://example.org/rss", scannedAddress("http://example.org/rss"))
    }

    @Test
    fun `space and line ends round it are trimmed`() {
        assertEquals("https://example.org/feed.xml", scannedAddress("  https://example.org/feed.xml\r\n"))
    }

    @Test
    fun `a headline in front of the address is skipped`() {
        assertEquals(
            "https://example.org/2026/11/a-walk.html",
            scannedAddress("A walk in the rain https://example.org/2026/11/a-walk.html"),
        )
    }

    @Test
    fun `a bare site address is taken as https`() {
        assertEquals("https://example.org", scannedAddress("example.org"))
        assertEquals("https://example.org/feed.xml", scannedAddress("example.org/feed.xml"))
        assertEquals("https://example.org:8080/feed", scannedAddress("example.org:8080/feed"))
    }

    @Test
    fun `capsules the app can read come through`() {
        assertEquals("gemini://example.space/log/", scannedAddress("gemini://example.space/log/"))
        assertEquals("gopher://example.org:70/1/phlog", scannedAddress("gopher://example.org:70/1/phlog"))
    }

    @Test
    fun `the scheme is matched whatever its case`() {
        assertEquals("HTTPS://example.org/feed", scannedAddress("HTTPS://example.org/feed"))
    }

    @Test
    fun `other schemes are refused`() {
        assertNull(scannedAddress("javascript:alert(1)"))
        assertNull(scannedAddress("javascript://example.org/%0Aalert(1)"))
        assertNull(scannedAddress("file:///sdcard/Download/feeds.opml"))
        assertNull(scannedAddress("content://com.example.provider/feed"))
        assertNull(scannedAddress("intent://example.org#Intent;scheme=https;end"))
        assertNull(scannedAddress("ftp://example.org/feed.xml"))
        assertNull(scannedAddress("mailto:ada.whitlock@example.org"))
        assertNull(scannedAddress("tel:+15550100123"))
    }

    @Test
    fun `data uris are refused`() {
        assertNull(scannedAddress("data:text/html,<script>alert(1)</script>"))
        assertNull(scannedAddress("data:application/rss+xml;base64,PHJzcz48L3Jzcz4="))
    }

    @Test
    fun `codes that are not addresses are refused`() {
        assertNull(scannedAddress(""))
        assertNull(scannedAddress("   "))
        assertNull(scannedAddress("WIFI:T:WPA;S:Example;P:not-a-real-one;;"))
        assertNull(scannedAddress("BEGIN:VCARD\nFN:Tomas Reyes\nEND:VCARD"))
        assertNull(scannedAddress("hello"))
        assertNull(scannedAddress("https://"))
        assertNull(scannedAddress("https://word"))
    }

    @Test
    fun `control characters inside an address are refused`() {
        assertNull(scannedAddress("https://example.org/fe\u0000ed"))
    }

    @Test
    fun `an absurdly long code is refused`() {
        assertNull(scannedAddress("https://example.org/" + "a".repeat(MAX_SCANNED_LENGTH)))
    }
}
