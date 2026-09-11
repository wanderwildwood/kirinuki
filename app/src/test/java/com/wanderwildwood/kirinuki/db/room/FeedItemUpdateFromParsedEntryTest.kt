package com.wanderwildwood.kirinuki.db.room

import com.wanderwildwood.kirinuki.model.ImageFromHTML
import com.wanderwildwood.kirinuki.model.MediaImage
import com.wanderwildwood.kirinuki.model.ParsedArticle
import com.wanderwildwood.kirinuki.model.ParsedFeed
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FeedItemUpdateFromParsedEntryTest {
    @Test
    fun keepsExistingNonBodyThumbnailWhenParsedEntryOnlyHasBodyImage() {
        val existingOgImage = MediaImage(url = "https://example.com/og.jpg")
        val feedItem = FeedItem(thumbnailImage = existingOgImage)

        feedItem.updateFromParsedEntry(
            entry =
                ParsedArticle(
                    id = "id-1",
                    image = ImageFromHTML(url = "https://example.com/body.jpg"),
                ),
            entryGuid = "guid-1",
            feed = ParsedFeed(title = "Feed", items = emptyList()),
        )

        assertEquals(existingOgImage, feedItem.thumbnailImage)
    }

    @Test
    fun keepsExistingNonBodyThumbnailWhenParsedEntryHasNoImage() {
        val existingOgImage = MediaImage(url = "https://example.com/og.jpg")
        val feedItem = FeedItem(thumbnailImage = existingOgImage)

        feedItem.updateFromParsedEntry(
            entry = ParsedArticle(id = "id-1"),
            entryGuid = "guid-1",
            feed = ParsedFeed(title = "Feed", items = emptyList()),
        )

        assertEquals(existingOgImage, feedItem.thumbnailImage)
    }

    @Test
    fun replacesExistingThumbnailWhenParsedEntryProvidesNonBodyImage() {
        val existingOgImage = MediaImage(url = "https://example.com/old-og.jpg")
        val feedProvidedImage = MediaImage(url = "https://example.com/feed.jpg")
        val feedItem = FeedItem(thumbnailImage = existingOgImage)

        feedItem.updateFromParsedEntry(
            entry =
                ParsedArticle(
                    id = "id-1",
                    image = feedProvidedImage,
                ),
            entryGuid = "guid-1",
            feed = ParsedFeed(title = "Feed", items = emptyList()),
        )

        assertEquals(feedProvidedImage, feedItem.thumbnailImage)
    }

    @Test
    fun clearsExistingBodyThumbnailWhenParsedEntryHasNoImage() {
        val feedItem = FeedItem(thumbnailImage = ImageFromHTML(url = "https://example.com/body.jpg"))

        feedItem.updateFromParsedEntry(
            entry = ParsedArticle(id = "id-1"),
            entryGuid = "guid-1",
            feed = ParsedFeed(title = "Feed", items = emptyList()),
        )

        assertNull(feedItem.thumbnailImage)
    }
}
