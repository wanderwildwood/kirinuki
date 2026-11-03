package com.wanderwildwood.kirinuki.model

import org.junit.Test
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddressLookTest {
    private val rss = """<?xml version="1.0"?><rss version="2.0"><channel><title>Ada's Notes</title></channel></rss>"""
    private val story = "<!DOCTYPE html><html><head><title>A walk in the rain</title></head><body><p>Words</p></body></html>"

    /** Stands in for the feed parser: anything with <rss or <feed in it parses, with this title. */
    private val parses: (String) -> Result<String?> = { body ->
        if (body.contains("<rss") || body.contains("<feed")) Result.success("Ada's Notes") else Result.failure(Exception("not a feed"))
    }
    private val noLinks: (String) -> List<AlternateLink> = { emptyList() }
    private val titleOf: (String) -> String? = { Regex("<title>(.*?)</title>").find(it)?.groupValues?.get(1) }

    private fun links(vararg pairs: Pair<String, String>): (String) -> List<AlternateLink> =
        { pairs.map { (type, link) -> AlternateLink(link = URL(link), type = type) } }

    @Test
    fun `an address that serves a feed is a feed`() {
        assertEquals(
            AddressLook.Feed(title = "Ada's Notes"),
            decideAddress("application/rss+xml; charset=utf-8", rss, parses, noLinks, titleOf),
        )
    }

    @Test
    fun `a feed sent as text html is still a feed`() {
        assertEquals(
            AddressLook.Feed(title = "Ada's Notes"),
            decideAddress("text/html", rss, parses, noLinks, titleOf),
        )
    }

    @Test
    fun `xml that does not parse is neither a feed nor a page`() {
        assertEquals(
            AddressLook.Neither,
            decideAddress("application/xml", "<?xml version=\"1.0\"?><sitemap/>", parses, noLinks, titleOf),
        )
    }

    @Test
    fun `a page that names a feed keeps today's subscribe, and the rss link is the one`() {
        assertEquals(
            AddressLook.PageWithFeed(feedUrl = URL("https://example.org/feed.xml"), pageTitle = "A walk in the rain"),
            decideAddress(
                "text/html; charset=utf-8",
                story,
                parses,
                links(
                    "application/json" to "https://example.org/oembed.json",
                    "application/rss+xml" to "https://example.org/feed.xml",
                ),
                titleOf,
            ),
        )
    }

    @Test
    fun `a page that names no feed is a story`() {
        assertEquals(
            AddressLook.Page(pageTitle = "A walk in the rain"),
            decideAddress("text/html", story, parses, noLinks, titleOf),
        )
    }

    @Test
    fun `a story with no title is still a story`() {
        assertEquals(
            AddressLook.Page(pageTitle = null),
            decideAddress("text/html", "<html><head></head><body>x</body></html>", parses, noLinks, { "  " }),
        )
    }

    @Test
    fun `a page is never put through the feed parser`() {
        var asked = false
        decideAddress("text/html", story, { asked = true; Result.failure(Exception()) }, noLinks, titleOf)
        assertFalse(asked)
    }

    @Test
    fun `with no content type the body decides`() {
        assertTrue(looksLikeHtml(null, "  <!doctype html><html>"))
        assertFalse(looksLikeHtml(null, rss))
        assertFalse(looksLikeHtml("text/plain", "{\"version\": \"https://jsonfeed.org/version/1.1\"}"))
    }

    @Test
    fun `xhtml with an xml declaration is a page`() {
        assertTrue(
            looksLikeHtml(
                "application/xhtml+xml",
                "<?xml version=\"1.0\"?><!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head></head></html>",
            ),
        )
    }

    @Test
    fun `rss and atom go ahead of json`() {
        assertEquals(
            URL("https://example.org/atom.xml"),
            firstFeedOf(
                listOf(
                    AlternateLink(URL("https://example.org/feed.json"), "application/json"),
                    AlternateLink(URL("https://example.org/atom.xml"), "application/atom+xml"),
                ),
            ),
        )
        assertEquals(null, firstFeedOf(emptyList()))
    }

    @Test
    fun `a text file is something to read`() {
        val text = "int main(void) { return 0; }\n"
        assertEquals(
            AddressLook.TextFile,
            decideAddress("text/plain; charset=utf-8", text, parses, noLinks, titleOf, url = "https://example.org/a.c.txt"),
        )
        assertEquals(
            AddressLook.TextFile,
            decideAddress("application/octet-stream", text, parses, noLinks, titleOf, url = "https://example.org/a.c.txt"),
        )
        assertTrue(AddressLook.TextFile.isToRead)
        assertFalse(AddressLook.Neither.isToRead)
    }

    @Test
    fun `a binary file or an unknown type is still neither`() {
        assertEquals(
            AddressLook.Neither,
            decideAddress("text/plain", "PK\u0003\u0000", parses, noLinks, titleOf, url = "https://example.org/a.txt"),
        )
        assertEquals(
            AddressLook.Neither,
            decideAddress("application/octet-stream", "x", parses, noLinks, titleOf, url = "https://example.org/a.bin"),
        )
    }
}
