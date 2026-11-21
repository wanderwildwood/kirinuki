package com.wanderwildwood.kirinuki.sync

import com.wanderwildwood.kirinuki.model.ParsedArticle
import com.wanderwildwood.kirinuki.model.ParsedEnclosure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerPodcastTagsTest {
    private fun article(
        guid: String,
        link: String?,
        audio: String?,
        chapters: String? = null,
        transcript: String? = null,
        type: String? = null,
    ) = ParsedArticle(
        id = guid,
        url = link,
        attachments = audio?.let { listOf(ParsedEnclosure(url = it, mime_type = "audio/mpeg")) },
        chaptersUrl = chapters,
        transcriptUrl = transcript,
        transcriptType = type,
    )

    private val stored =
        listOf(
            StoredEpisode(1, "https://example.com/lamp/1.mp3", "https://example.com/lamp/1"),
            StoredEpisode(2, "https://example.com/lamp/2.mp3", "https://example.com/lamp/2"),
            StoredEpisode(3, null, "https://example.com/lamp/3"),
        )

    @Test
    fun matchesByAudioAddressNotByGuid() {
        val ups =
            matchPodcastTags(
                // The guid is the feed's own and means nothing to the stored rows.
                listOf(article("1", "https://example.com/other", "https://example.com/lamp/2.mp3", chapters = "https://example.com/c2.json")),
                stored,
            )
        assertEquals(listOf(EpisodeTagUpdate(2, "https://example.com/c2.json", null, null)), ups)
    }

    @Test
    fun fallsBackToTheLinkWhenThereIsNoAudioMatch() {
        val ups =
            matchPodcastTags(
                listOf(article("g", "https://example.com/lamp/3", null, transcript = "https://example.com/t3.vtt", type = "text/vtt")),
                stored,
            )
        assertEquals(listOf(EpisodeTagUpdate(3, null, "https://example.com/t3.vtt", "text/vtt")), ups)
    }

    @Test
    fun audioAddressWinsOverALinkThatPointsAtAnotherEpisode() {
        val ups =
            matchPodcastTags(
                listOf(article("g", "https://example.com/lamp/1", "https://example.com/lamp/2.mp3", chapters = "https://example.com/c.json")),
                stored,
            )
        assertEquals(listOf(2L), ups.map { it.itemId })
    }

    @Test
    fun unmatchedAndTaglessEpisodesMakeNoUpdate() {
        val ups =
            matchPodcastTags(
                listOf(
                    article("a", "https://example.com/nowhere", "https://example.com/nowhere.mp3", chapters = "https://example.com/c.json"),
                    article("b", "https://example.com/lamp/1", "https://example.com/lamp/1.mp3"),
                ),
                stored,
            )
        assertTrue(ups.isEmpty())
        assertTrue(matchPodcastTags(emptyList(), stored).isEmpty())
        assertTrue(matchPodcastTags(listOf(article("a", null, null, chapters = "https://example.com/c.json")), stored).isEmpty())
    }

    @Test
    fun aTypeIsOnlyKeptWithItsTranscript() {
        val ups = matchPodcastTags(listOf(article("a", null, "https://example.com/lamp/1.mp3", chapters = "https://example.com/c.json", type = "text/vtt")), stored)
        assertNull(ups.single().transcriptType)
    }

    @Test
    fun onceADayPerFeed() {
        val day = 24L * 3600 * 1000
        assertTrue(ServerPodcastTags.isDue(0, 1_000))
        assertFalse(ServerPodcastTags.isDue(1_000, 1_000 + day - 1))
        assertTrue(ServerPodcastTags.isDue(1_000, 1_000 + day))
        // A clock set back is not a reason to wait.
        assertTrue(ServerPodcastTags.isDue(5_000, 1_000))
    }
}
