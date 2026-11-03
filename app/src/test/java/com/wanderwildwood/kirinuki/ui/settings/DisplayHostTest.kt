package com.wanderwildwood.kirinuki.ui.settings

import org.junit.Test
import kotlin.test.assertEquals

class DisplayHostTest {
    @Test
    fun theUsualPortIsLeftOut() {
        assertEquals("rss.example.org", displayHost("https://rss.example.org/api/greader.php/"))
        assertEquals("rss.example.org", displayHost("http://rss.example.org/"))
    }

    @Test
    fun anotherPortIsShown() {
        assertEquals("192.168.1.20:8080", displayHost("http://192.168.1.20:8080/"))
    }
}
