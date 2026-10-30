package com.wanderwildwood.kirinuki.ui.addfeed

import com.wanderwildwood.kirinuki.net.parseUrlLeniently
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LooksLikeAddressTest {
    // An address the parser itself refuses is not an address either, which is how the form treats it.
    private fun ok(spec: String) =
        try {
            looksLikeAddress(parseUrlLeniently(spec))
        } catch (e: java.net.MalformedURLException) {
            false
        }

    @Test
    fun addressesTheAppCanFetchPass() {
        assertTrue(ok("https://example.com/feed.xml"))
        assertTrue(ok("http://example.com:8080/rss"))
        assertTrue(ok("gemini://example.space/log/"))
        assertTrue(ok("gopher://example.org:7070/1/phlog"))
        assertTrue(ok("spartan://example.net/"))
        assertTrue(ok("http://localhost:8099/x.xml"))
    }

    @Test
    fun whatIsNotAnAddressFails() {
        assertFalse(ok("https://word"))
        assertFalse(ok("https://what is this"))
        assertFalse(ok("https://"))
        assertFalse(ok("ftp://example.com/feed"))
        assertFalse(ok("file://example.com/etc/passwd"))
    }
}
