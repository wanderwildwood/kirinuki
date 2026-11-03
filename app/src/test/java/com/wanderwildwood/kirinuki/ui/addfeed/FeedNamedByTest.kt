package com.wanderwildwood.kirinuki.ui.addfeed

import com.wanderwildwood.kirinuki.model.AlternateLink
import com.wanderwildwood.kirinuki.model.SiteMetaData
import org.junit.Test
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FeedNamedByTest {
    private fun site(vararg links: Pair<String, String>) =
        SiteMetaData(
            url = URL("https://example.org/"),
            alternateFeedLinks = links.map { (type, link) -> AlternateLink(link = URL(link), type = type) },
            feedImage = null,
        )

    @Test
    fun `a page that names no feed gives nothing, and the address stays as given`() {
        assertNull(feedNamedBy(site()))
    }

    @Test
    fun `the first rss or atom feed a page names is the one`() {
        assertEquals(
            URL("https://example.org/feed.xml"),
            feedNamedBy(
                site(
                    "application/rss+xml" to "https://example.org/feed.xml",
                    "application/atom+xml" to "https://example.org/atom.xml",
                ),
            ),
        )
    }

    @Test
    fun `rss or atom is taken ahead of a json link listed first`() {
        assertEquals(
            URL("https://example.org/atom.xml"),
            feedNamedBy(
                site(
                    "application/json" to "https://example.org/feed.json",
                    "application/atom+xml" to "https://example.org/atom.xml",
                ),
            ),
        )
    }

    @Test
    fun `a json feed is taken when it is the only one`() {
        assertEquals(
            URL("https://example.org/feed.json"),
            feedNamedBy(site("application/json" to "https://example.org/feed.json")),
        )
    }
}
