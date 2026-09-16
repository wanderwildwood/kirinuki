package com.wanderwildwood.kirinuki.model.gemtext

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GemsubParserTest {
    private val parser = GemsubParser()
    private val url = "gemini://example.space/log/"

    @Test
    fun `a page of dated links is a feed`() {
        val feed =
            parser.parse(
                url,
                """
                # My gemlog
                Some words about it.
                => two.gmi 2026-09-10 The second thing
                => one.gmi 2026-09-02 The first thing
                """.trimIndent(),
            )

        assertNotNull(feed)
        assertEquals("My gemlog", feed.title)
        assertEquals(2, feed.items?.size)
        assertEquals("The second thing", feed.items?.first()?.title)
        assertEquals("gemini://example.space/log/two.gmi", feed.items?.first()?.url)
    }

    @Test
    fun `a page with no dated links is not a feed`() {
        // Subscribing to this would produce entries forever, which is to say never.
        assertNull(
            parser.parse(
                url,
                """
                # Just a page
                => somewhere.gmi Somewhere
                => elsewhere.gmi Elsewhere
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `undated links on a gemlog are left out rather than dated today`() {
        val feed =
            parser.parse(
                url,
                """
                # Gemlog
                => index.gmi Home
                => post.gmi 2026-09-10 A post
                => atom.xml Subscribe
                """.trimIndent(),
            )

        assertEquals(1, feed?.items?.size)
        assertEquals("A post", feed?.items?.single()?.title)
    }

    @Test
    fun `the separators people actually use are all accepted`() {
        val feed =
            parser.parse(
                url,
                """
                # Gemlog
                => a.gmi 2026-09-10 Plain
                => b.gmi 2026-09-09 - Dashed
                => c.gmi 2026-09-08: Coloned
                """.trimIndent(),
            )

        assertEquals(
            listOf("Plain", "Dashed", "Coloned"),
            feed?.items?.map { it.title },
        )
    }

    @Test
    fun `the date becomes the publication date`() {
        val feed = parser.parse(url, "# Log\n=> a.gmi 2026-09-10 A post")

        assertTrue(
            feed?.items?.single()?.date_published?.startsWith("2026-09-10") == true,
            "expected the link's own date, got ${feed?.items?.single()?.date_published}",
        )
    }

    @Test
    fun `an entry is identified by its url, because a gemlog has no guids`() {
        val feed = parser.parse(url, "# Log\n=> a.gmi 2026-09-10 A post")
        val item = feed?.items?.single()

        assertEquals(item?.url, item?.id)
    }

    @Test
    fun `a gemlog with no heading falls back to its host`() {
        val feed = parser.parse(url, "=> a.gmi 2026-09-10 A post")

        assertEquals("example.space", feed?.title)
    }

    @Test
    fun `a dated link with no title shows its address rather than nothing`() {
        val feed = parser.parse(url, "# Log\n=> a.gmi 2026-09-10")

        assertEquals("gemini://example.space/log/a.gmi", feed?.items?.single()?.title)
    }

    @Test
    fun `something that is not a date does not become one`() {
        assertNull(parser.parse(url, "# Log\n=> a.gmi 2026-13-45 Not a date"))
        assertNull(parser.parse(url, "# Log\n=> a.gmi 20260910 Not a date"))
    }

    @Test
    fun `windows line endings do not break the date`() {
        val feed = parser.parse(url, "# Log\r\n=> a.gmi 2026-09-10 A post\r\n")

        assertEquals("A post", feed?.items?.single()?.title)
    }

    @Test
    fun `link lines separated by a tab are still entries`() {
        // geminiprotocol.net's own news capsule separates the target from the label with a
        // TAB rather than a space. It is the most canonical gemsub there is, and subscribing
        // to it on the phone produced no entries at all.
        val feed =
            parser.parse(
                "gemini://geminiprotocol.net/news/",
                "# Official Project Gemini news feed\n" +
                    "=> atom.xml\tAtom feed\n" +
                    "=> 2026_06_20.gmi\t2026-06-20 - Seven years of Gemini!\n" +
                    "=> 2026_01_14.gmi\t2026-01-14 - Yet more downtime\n",
            )

        assertNotNull(feed)
        assertEquals(2, feed.items?.size)
        assertEquals("Seven years of Gemini!", feed.items?.first()?.title)
        assertEquals(
            "gemini://geminiprotocol.net/news/2026_06_20.gmi",
            feed.items?.first()?.url,
        )
    }
}
