package com.wanderwildwood.kirinuki.ui.addfeed

import com.wanderwildwood.kirinuki.net.parseUrlLeniently
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceholderTitleTest {
    private fun title(spec: String) = placeholderTitle(parseUrlLeniently(spec))

    @Test
    fun theHostAloneWhenThereIsNoPath() {
        assertEquals("example.com", title("https://www.example.com/"))
        assertEquals("example.com", title("https://example.com"))
    }

    @Test
    fun thePathTellsTwoFeedsOnOneServerApart() {
        assertEquals("10.0.2.2/nope.xml", title("http://10.0.2.2:8099/nope.xml"))
        assertEquals("10.0.2.2/bad.xml", title("http://10.0.2.2:8099/bad.xml"))
        assertEquals("example.org/1/phlog", title("gopher://example.org:7070/1/phlog"))
    }

    @Test
    fun aLongAddressIsCut() {
        val long = title("https://example.com/" + "a".repeat(80))
        assertEquals(40, long.length)
    }
}
