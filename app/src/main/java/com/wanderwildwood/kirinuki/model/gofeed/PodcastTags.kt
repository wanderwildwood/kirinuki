package com.wanderwildwood.kirinuki.model.gofeed

import com.wanderwildwood.kirinuki.podcast.bestTranscript

/** The addresses a feed gives for an episode's chapters and transcript (Podcasting 2.0). */
data class PodcastLinks(
    val chaptersUrl: String?,
    val transcriptUrl: String?,
    val transcriptType: String?,
)

/**
 * Reads `<podcast:chapters url=".." type="application/json+chapters"/>` and
 * `<podcast:transcript url=".." type=".."/>` from an item's extensions. gofeed files them under
 * the namespace prefix the feed used, usually "podcast", so every prefix is looked at and the tag
 * is taken only where it carries a web address. Of several transcripts, the best one is kept.
 */
fun podcastLinksFrom(extensions: GoExtensions?): PodcastLinks {
    if (extensions == null) return PodcastLinks(null, null, null)
    val chapters = mutableListOf<GoExtension>()
    val transcripts = mutableListOf<GoExtension>()
    extensions.forEach { (prefix, byName) ->
        byName["chapters"]?.let { list ->
            chapters += list.filter { prefix == "podcast" || it.attrs?.get("type")?.contains("chapters", ignoreCase = true) == true }
        }
        byName["transcript"]?.let { list -> if (prefix == "podcast") transcripts += list }
    }
    val chaptersUrl =
        chapters
            .mapNotNull { it.attrs?.get("url")?.trim() }
            .firstOrNull { it.startsWith("http", ignoreCase = true) }
    val transcript = bestTranscript(transcripts, { it.attrs?.get("url")?.trim() }, { it.attrs?.get("type") })
    return PodcastLinks(
        chaptersUrl = chaptersUrl,
        transcriptUrl = transcript?.attrs?.get("url")?.trim(),
        transcriptType = transcript?.attrs?.get("type")?.trim()?.lowercase(),
    )
}
