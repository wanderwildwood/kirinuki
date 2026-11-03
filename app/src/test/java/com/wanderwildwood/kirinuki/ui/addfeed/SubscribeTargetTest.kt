package com.wanderwildwood.kirinuki.ui.addfeed

import com.wanderwildwood.kirinuki.model.AddressLook
import org.junit.Test
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscribeTargetTest {
    @Test
    fun `a page naming a feed subscribes to that feed`() {
        val feed = URL("https://example.org/feed.xml")
        assertEquals(
            SubscribeTarget.Feed(feed),
            subscribeTarget(AddressLook.PageWithFeed(feedUrl = feed, pageTitle = null), anyway = false),
        )
    }

    @Test
    fun `a story stops Subscribe the first time, and goes ahead the second`() {
        assertEquals(SubscribeTarget.Stop, subscribeTarget(AddressLook.Page(pageTitle = "A walk"), anyway = false))
        assertEquals(SubscribeTarget.AsGiven, subscribeTarget(AddressLook.Page(pageTitle = "A walk"), anyway = true))
    }

    @Test
    fun `a feed, an odd answer or no answer subscribe as given, as before`() {
        assertEquals(SubscribeTarget.AsGiven, subscribeTarget(AddressLook.Feed(title = null), anyway = false))
        assertEquals(SubscribeTarget.AsGiven, subscribeTarget(AddressLook.Neither, anyway = false))
        assertEquals(SubscribeTarget.AsGiven, subscribeTarget(null, anyway = false))
    }

    @Test
    fun `the address key is what the box will fetch`() {
        assertEquals("https://example.org/a-walk", addressKey("  example.org/a-walk "))
        assertEquals("http://example.org/", addressKey("http://example.org/"))
        assertNull(addressKey("   "))
    }
}
