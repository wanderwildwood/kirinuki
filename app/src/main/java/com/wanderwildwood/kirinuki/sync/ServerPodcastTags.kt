package com.wanderwildwood.kirinuki.sync

import android.app.Application
import android.content.Context
import android.util.Log
import com.wanderwildwood.kirinuki.ApplicationCoroutineScope
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.db.room.FeedDao
import com.wanderwildwood.kirinuki.db.room.FeedItemDao
import com.wanderwildwood.kirinuki.model.FeedParser
import com.wanderwildwood.kirinuki.model.ParsedArticle
import com.wanderwildwood.kirinuki.model.getResponse
import com.wanderwildwood.kirinuki.util.currentlyUnmetered
import com.wanderwildwood.kirinuki.util.logDebug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

/** One stored episode, as far as matching goes. */
data class StoredEpisode(
    val id: Long,
    val enclosureLink: String?,
    val link: String?,
)

/** The three addresses to write onto one stored episode; null parts are left as they are. */
data class EpisodeTagUpdate(
    val itemId: Long,
    val chaptersUrl: String?,
    val transcriptUrl: String?,
    val transcriptType: String?,
)

/**
 * Pairs the episodes read from a feed's own XML with the episodes stored from the feed server.
 * The server's guid is its own id, not the feed's, so a guid is never used: an episode is found
 * by its audio file's address, and failing that by its page link. An episode of the feed that
 * brings no chapters and no transcript, and one with no match, make no update.
 */
fun matchPodcastTags(
    parsed: List<ParsedArticle>,
    stored: List<StoredEpisode>,
): List<EpisodeTagUpdate> {
    val byEnclosure = HashMap<String, MutableList<StoredEpisode>>()
    val byLink = HashMap<String, MutableList<StoredEpisode>>()
    for (episode in stored) {
        episode.enclosureLink?.trim()?.takeIf { it.isNotEmpty() }?.let { byEnclosure.getOrPut(it) { mutableListOf() }.add(episode) }
        episode.link?.trim()?.takeIf { it.isNotEmpty() }?.let { byLink.getOrPut(it) { mutableListOf() }.add(episode) }
    }
    val updates = LinkedHashMap<Long, EpisodeTagUpdate>()
    for (article in parsed) {
        if (article.chaptersUrl == null && article.transcriptUrl == null) continue
        val hits =
            article.attachments
                .orEmpty()
                .mapNotNull { it.url?.trim() }
                .firstNotNullOfOrNull { byEnclosure[it] }
                ?: article.url?.trim()?.let { byLink[it] }
                ?: continue
        for (episode in hits) {
            updates[episode.id] =
                EpisodeTagUpdate(
                    itemId = episode.id,
                    chaptersUrl = article.chaptersUrl,
                    transcriptUrl = article.transcriptUrl,
                    transcriptType = if (article.transcriptUrl != null) article.transcriptType else null,
                )
        }
    }
    return updates.values.toList()
}

/**
 * Feeds that come through a feed server are never read from their sites, and the server hands on
 * no podcast tags, so their episodes would never get chapters or transcripts. After a server
 * sync this reads each such podcast's own feed (the app's own client, so the same proxy,
 * user agent and cache) once a day and writes only the chapters and transcript addresses onto
 * episodes already stored. Nothing else about an episode changes and nothing is downloaded.
 * Every failure stays here: it is logged and tried again tomorrow-ish (the next sync).
 */
class ServerPodcastTags(
    override val di: DI,
) : DIAware {
    private val application: Application by instance()
    private val okHttpClient: OkHttpClient by instance()
    private val feedParser: FeedParser by instance()
    private val feedDao: FeedDao by instance()
    private val itemDao: FeedItemDao by instance()
    private val repository: Repository by instance()
    private val scope: ApplicationCoroutineScope by instance()
    private val prefs by lazy { application.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val running = Mutex()

    /** Starts the pass in the background and returns at once; one pass at a time. */
    fun launchAfterSync(serverFeedIds: Set<Long>) {
        if (serverFeedIds.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            if (!running.tryLock()) return@launch
            try {
                run(serverFeedIds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(LOG_TAG, "Podcast tags pass failed: ${e.javaClass.simpleName}")
            } finally {
                running.unlock()
            }
        }
    }

    private suspend fun run(serverFeedIds: Set<Long>) {
        // The same setting as the sync itself: on metered data with "only on Wi-Fi" nothing is fetched.
        if (repository.syncOnlyOnWifi.value && !currentlyUnmetered(application)) return
        val now = System.currentTimeMillis()
        val podcasts = itemDao.podcastFeedIds().toSet()
        val due = serverFeedIds.filter { it in podcasts && isDue(prefs.getLong(key(it), 0L), now) }
        for (feedId in due) {
            try {
                val feed = feedDao.getFeed(feedId) ?: continue
                if (feed.url.protocol.lowercase() !in setOf("http", "https")) continue
                // Stamped first: a feed that fails is tried again tomorrow, not on every refresh.
                prefs.edit().putLong(key(feedId), now).apply()
                val parsed =
                    okHttpClient.getResponse(feed.url).use { response ->
                        if (!response.isSuccessful) return@use null
                        val body = response.body
                        feedParser.parseFeedResponse(response.request.url.toUrl(), body).getOrNull()
                    } ?: continue
                val updates = matchPodcastTags(parsed.items.orEmpty(), itemDao.storedEpisodes(feedId).map { StoredEpisode(it.id, it.enclosureLink, it.link) })
                withContext(Dispatchers.IO) {
                    for (u in updates) itemDao.setPodcastTags(u.itemId, u.chaptersUrl, u.transcriptUrl, u.transcriptType)
                }
                logDebug(LOG_TAG, "Feed $feedId: ${updates.size} episodes got chapter or transcript addresses")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(LOG_TAG, "Podcast tags for feed $feedId failed: ${e.javaClass.simpleName}")
            }
        }
    }

    companion object {
        private const val LOG_TAG = "KIRINUKI_SERVER_PODCAST"
        private const val PREFS = "server_podcast_tags"
        private const val ONE_DAY_MS = 24L * 3600 * 1000

        private fun key(feedId: Long) = "feed_$feedId"

        /** No stamp (a feed just seen) is due at once; otherwise a day after the last try. */
        fun isDue(
            lastMs: Long,
            nowMs: Long,
        ): Boolean = lastMs <= 0L || nowMs - lastMs >= ONE_DAY_MS || nowMs < lastMs
    }
}
