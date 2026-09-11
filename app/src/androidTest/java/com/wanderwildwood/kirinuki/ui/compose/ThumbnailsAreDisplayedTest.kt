package com.wanderwildwood.kirinuki.ui.compose

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.wanderwildwood.kirinuki.archmodel.FeedItemStyle
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.archmodel.SyncFrequency
import com.wanderwildwood.kirinuki.db.room.Feed
import com.wanderwildwood.kirinuki.db.room.FeedItem
import com.wanderwildwood.kirinuki.model.ImageFromHTML
import com.wanderwildwood.kirinuki.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.kodein.di.instance
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Ignore("Flaky")
class ThumbnailsAreDisplayedTest : BaseComposeTest {
    @get:Rule
    override val composeTestRule = createAndroidComposeRule<MainActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun thumbnailsAreShown() {
        val repository by (composeTestRule.activity).di.instance<Repository>()

        repository.setSyncFrequency(SyncFrequency.MANUAL)
        repository.setFeedItemStyle(FeedItemStyle.CARD)
        repository.setShowThumbnails(true)

        // Ensure we have feeds and items
        runBlocking {
            val feedId =
                repository.saveFeed(
                    Feed(
                        title = "Thumbnails are displayed",
                        url = URL("https://example.com/thumbnailsaredisplay"),
                    ),
                )

            repository.setCurrentFeedAndTag(feedId, "")

            repository.upsertFeedItems(
                listOf(
                    FeedItem(
                        guid = "guid anime2you",
                        plainTitle = "Item with image",
                        plainSnippet = "Snippet with image",
                        feedId = feedId,
                        // Truncate to milliseconds to match database precision
                        pubDate =
                            ZonedDateTime.ofInstant(
                                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                                ZoneOffset.UTC,
                            ),
                        primarySortTime = Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                        thumbnailImage =
                            ImageFromHTML(
                                url = "https://img.anime2you.de/2023/12/jujutsu-kaisen-6.jpg",
                                width = 700,
                                height = 350,
                            ),
                    ) to "",
                ),
            ) { _, _ -> }
        }

        composeTestRule.waitUntilExactlyOneExists(
            hasTestTag("feed_list"),
            5_000L,
        )

        composeTestRule
            .onNodeWithTag("card_image", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
