package com.wanderwildwood.kirinuki.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The route builders are the only part of navigation with anything in it: each one puts a
 * value someone typed into a string the navigation graph then takes apart again. An address
 * carries the characters that route matching is made of -- a colon, slashes, an ampersand --
 * so what matters is that they survive the trip as one argument rather than becoming
 * structure.
 *
 * Instrumented rather than a plain unit test because android.net.Uri is a real Android class;
 * the JVM stub returns null for everything.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class RouteTest {
    @Test
    fun addFeedEncodesTheAddress() {
        assertEquals(
            "addfeed?url=https%3A%2F%2Fexample.com%2Ffeed.xml",
            Route.addFeed("https://example.com/feed.xml"),
        )
    }

    @Test
    fun addFeedEncodesAnAmpersandRatherThanStartingASecondArgument() {
        // Unencoded this would read as a second query parameter and the address would
        // arrive truncated at the ampersand.
        assertEquals(
            "addfeed?url=https%3A%2F%2Fexample.com%2Ffeed%3Fa%3D1%26b%3D2",
            Route.addFeed("https://example.com/feed?a=1&b=2"),
        )
    }

    @Test
    fun editFeedCarriesTheRowItEdits() {
        assertEquals("addfeed?feed=42", Route.editFeed(42L))
    }

    @Test
    fun addFeedAndEditFeedBothMatchTheOneRoute() {
        // Both ways in are the same screen, so both have to begin with the route it is
        // registered under.
        assertEquals(true, Route.addFeed("https://example.com").startsWith(Route.ADD_FEED))
        assertEquals(true, Route.editFeed(1L).startsWith(Route.ADD_FEED))
    }

    @Test
    fun geminiEncodesTheCapsuleAddressIntoOnePathSegment() {
        // This one rides in the path rather than the query, so an unencoded slash would
        // split it into segments the route has no placeholders for.
        assertEquals(
            "gemini/gemini%3A%2F%2Fexample.org%2Fpage.gmi",
            Route.gemini("gemini://example.org/page.gmi"),
        )
    }
}
