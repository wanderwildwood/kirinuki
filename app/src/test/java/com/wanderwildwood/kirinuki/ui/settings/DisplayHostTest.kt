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

    @Test
    fun homesServerIsShownWithoutTheScheme() {
        assertEquals("cloud.example.org", shownServer("https://cloud.example.org"))
        assertEquals("example.org", shownServer("https://example.org/nextcloud/"))
        assertEquals("10.0.2.2:8080", shownServer("http://10.0.2.2:8080"))
        assertEquals("cloud.example.org", shownServer("https://cloud.example.org:443/"))
        assertEquals(displayHost("http://192.168.1.20:8080/index.php/apps/news/api/v1-3"), shownServer("http://192.168.1.20:8080"))
    }
}
