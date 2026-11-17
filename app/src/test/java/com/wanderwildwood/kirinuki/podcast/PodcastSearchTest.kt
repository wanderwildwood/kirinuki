package com.wanderwildwood.kirinuki.podcast

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PodcastSearchTest {
    @Test
    fun readsTitleAuthorAndFeed() {
        val json =
            """
            {"resultCount":2,"results":[
              {"wrapperType":"track","kind":"podcast","collectionId":101,"trackId":101,
               "artistName":"Ada Whitlock","collectionName":"Field Notes from the Ridge",
               "trackName":"Field Notes from the Ridge","feedUrl":"https://example.org/ridge.xml",
               "trackCount":12,"genres":["Nature","Podcasts"],"artworkUrl100":"https://example.org/a.jpg"},
              {"wrapperType":"track","kind":"podcast","artistName":"Tomas Reyes",
               "trackName":"Lanterns","feedUrl":"http://example.net/lanterns/rss"}
            ]}
            """.trimIndent()
        assertEquals(
            listOf(
                FoundPodcast("Field Notes from the Ridge", "Ada Whitlock", "https://example.org/ridge.xml"),
                FoundPodcast("Lanterns", "Tomas Reyes", "http://example.net/lanterns/rss"),
            ),
            parsePodcastSearch(json),
        )
    }

    @Test
    fun aShowWithNoFeedIsLeftOut() {
        val json =
            """{"resultCount":2,"results":[
              {"collectionName":"Only on Apple","artistName":"Someone"},
              {"collectionName":"Has a feed","feedUrl":"https://example.org/f.xml"}
            ]}"""
        assertEquals(listOf(FoundPodcast("Has a feed", "", "https://example.org/f.xml")), parsePodcastSearch(json))
    }

    @Test
    fun theSameFeedTwiceIsShownOnce() {
        val json =
            """{"results":[
              {"collectionName":"Twice","feedUrl":"https://example.org/t.xml"},
              {"collectionName":"Twice again","feedUrl":"https://example.org/t.xml"}
            ]}"""
        assertEquals(1, parsePodcastSearch(json).size)
    }

    @Test
    fun oddValuesAreSkipped() {
        val json =
            """{"resultCount":3,"results":[
              {"collectionName":null,"trackName":"Named by track","feedUrl":"https://example.org/n.xml","collectionId":5},
              {"collectionName":"Not a web feed","feedUrl":"itpc://example.org/x"},
              "not an object",
              {"collectionName":"  ","feedUrl":"https://example.org/blank.xml"}
            ]}"""
        assertEquals(listOf(FoundPodcast("Named by track", "", "https://example.org/n.xml")), parsePodcastSearch(json))
    }

    @Test
    fun nothingFoundIsAnEmptyList() {
        assertTrue(parsePodcastSearch("""{"resultCount":0,"results":[]}""").isEmpty())
    }
}
