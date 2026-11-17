package com.wanderwildwood.kirinuki.podcast

import com.squareup.moshi.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.BufferedSource

/** One show from the search: what it is called, who makes it, and the feed to subscribe to. */
data class FoundPodcast(
    val title: String,
    val author: String,
    val feedUrl: String,
)

/**
 * Finding a podcast by name, through Apple's public podcast directory: the one most podcasts are
 * listed in, and searchable with no account or key. Only the words typed are sent, and only when
 * Search is pressed; the answer names each show's own feed, which is then subscribed to like any
 * other, straight from its publisher.
 */
class PodcastSearch(
    private val client: OkHttpClient,
) {
    suspend fun search(term: String): List<FoundPodcast> =
        withContext(Dispatchers.IO) {
            val url =
                SEARCH_URL
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("media", "podcast")
                    .addQueryParameter("entity", "podcast")
                    .addQueryParameter("limit", RESULTS.toString())
                    .addQueryParameter("term", term.trim())
                    .build()
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw java.io.IOException("Search answered ${response.code}")
                parsePodcastSearch(response.body.source())
            }
        }

    companion object {
        const val SEARCH_URL = "https://itunes.apple.com/search"
        private const val RESULTS = 50
    }
}

fun parsePodcastSearch(json: String): List<FoundPodcast> = parsePodcastSearch(Buffer().writeUtf8(json))

/**
 * The shows in the answer, in its order. A result with no feed cannot be subscribed to (Apple
 * lists some shows it only plays itself), so it is left out; a feed listed twice is shown once.
 */
fun parsePodcastSearch(source: BufferedSource): List<FoundPodcast> {
    val found = mutableListOf<FoundPodcast>()
    val reader = JsonReader.of(source)
    reader.beginObject()
    while (reader.hasNext()) {
        if (reader.nextName() == "results" && reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
            reader.beginArray()
            while (reader.hasNext()) readResult(reader)?.let { found += it }
            reader.endArray()
        } else {
            reader.skipValue()
        }
    }
    reader.endObject()
    return found.distinctBy { it.feedUrl }
}

private fun readResult(reader: JsonReader): FoundPodcast? {
    if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
        reader.skipValue()
        return null
    }
    var collection: String? = null
    var track: String? = null
    var artist: String? = null
    var feed: String? = null
    reader.beginObject()
    while (reader.hasNext()) {
        val name = reader.nextName()
        if (reader.peek() != JsonReader.Token.STRING) {
            reader.skipValue()
            continue
        }
        when (name) {
            "collectionName" -> collection = reader.nextString()
            "trackName" -> track = reader.nextString()
            "artistName" -> artist = reader.nextString()
            "feedUrl" -> feed = reader.nextString()
            else -> reader.skipValue()
        }
    }
    reader.endObject()
    val feedUrl = feed?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
    val title = (collection ?: track)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return FoundPodcast(title = title, author = artist?.trim().orEmpty(), feedUrl = feedUrl)
}
