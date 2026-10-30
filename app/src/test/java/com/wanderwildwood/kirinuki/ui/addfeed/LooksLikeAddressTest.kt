package com.wanderwildwood.kirinuki.ui.addfeed

import com.wanderwildwood.kirinuki.net.parseUrlLeniently
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LooksLikeAddressTest {
    private fun ok(spec: String) = looksLikeAddress(parseUrlLeniently(spec))

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
