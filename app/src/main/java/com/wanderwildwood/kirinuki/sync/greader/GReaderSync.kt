package com.wanderwildwood.kirinuki.sync.greader

/** A feed on the phone, as the sync sees it. */
data class LocalFeed(
    val id: Long,
    val url: String,
    val title: String,
    val tag: String,
    /** False for what a feed server cannot fetch: saved pages, gemlogs, phlogs. */
    val syncable: Boolean,
)

/**
 * The phone's record of one feed and the server. [streamId] null means the server would not
 * take it, so it stays on the phone only and is fetched from its site as before.
 * [syncedLabel] is the folder both sides last agreed on.
 */
data class FeedLink(
    val feedId: Long,
    val streamId: String?,
    val syncedLabel: String,
)

/** The phone side of a sync, so the sync itself can be run against a fake in tests. */
interface GReaderStore {
    suspend fun localFeeds(): List<LocalFeed>

    suspend fun feedLinks(): List<FeedLink>

    suspend fun setFeedLink(link: FeedLink)

    suspend fun removeFeedLink(feedId: Long)

    /** A new feed on the phone for a server subscription; its id. */
    suspend fun addFeed(
        url: String,
        title: String,
        tag: String,
    ): Long

    suspend fun updateFeed(
        feedId: Long,
        title: String,
        tag: String,
    )

    /** The feed and everything in it, gone (the server no longer has it). */
    suspend fun deleteFeed(feedId: Long)

    suspend fun itemStates(): List<GReaderMerge.ItemState>

    /** Records that phone and server now agree on these items' read and starred. */
    suspend fun setBases(items: List<GReaderMerge.ItemState>)

    /** Sets read and starred on the phone (and the base with them). */
    suspend fun apply(changes: List<GReaderMerge.Apply>)

    /**
     * New articles from the server into feed [feedId], each read or starred as the server
     * has it. An article the phone already holds from fetching the site itself (the same
     * link, not yet linked to the server) is taken over rather than doubled.
     */
    suspend fun insertItems(
        feedId: Long,
        items: List<RemoteItem>,
        unreadIds: Set<String>,
        starredIds: Set<String>,
    )

    /** Leaves at most [keep] read, unstarred articles in [feedId]; unread and starred stay. */
    suspend fun prune(
        feedId: Long,
        keep: Int,
    )
}

/** What one sync did, for the pull line and the tests. */
data class GReaderReport(
    val feedsAdded: Int = 0,
    val feedsRemoved: Int = 0,
    val feedsSent: Int = 0,
    val itemsFetched: Int = 0,
    val changesSent: Int = 0,
    val changesApplied: Int = 0,
)

/**
 * One sync with a feed server (Google Reader-style or Nextcloud News), in the order that keeps both sides whole:
 *
 * 1. Feeds deleted on the phone are unsubscribed; folders changed on the phone are sent.
 * 2. The server's subscription list is taken as it is (the server wins for subscriptions):
 *    new ones added here, gone ones removed here, names and folders updated.
 * 3. Feeds on the phone the server has never seen are subscribed to there, so signing in
 *    loses nothing; one the server refuses stays on the phone and is fetched as before.
 * 4. Read and starred changes made on the phone since the last sync are sent.
 * 5. The server's unread and starred lists are read and applied (see [GReaderMerge]).
 * 6. Articles the phone lacks are fetched from the server, not from each site: every
 *    unread one up to [maxNewUnread], every starred one, and anything new since [sinceEpochSeconds].
 *
 * Adapted in outline from Read You's GoogleReaderRssService.sync
 * (https://github.com/Ashinch/ReadYou, GPL-3.0), which takes it from Reeder's strategy.
 */
class GReaderSync(
    private val api: FeedServerApi,
    private val store: GReaderStore,
    private val keepReadPerFeed: Int = 100,
    private val maxNewUnread: Int = 1000,
) {
    /** Feeds linked in this sync: the server may hold older articles of theirs the phone never saw. */
    private val newStreams = mutableSetOf<String>()

    suspend fun sync(sinceEpochSeconds: Long): GReaderReport {
        newStreams.clear()
        api.login()
        var report = syncFeeds()
        report = syncItems(report, sinceEpochSeconds)
        return report
    }

    private suspend fun syncFeeds(): GReaderReport {
        var added = 0
        var removed = 0
        var sent = 0
        val local = store.localFeeds().associateBy { it.id }
        val links = store.feedLinks().associateBy { it.feedId }.toMutableMap()

        // 1. The phone's own changes to feeds already on the server.
        for (link in links.values.toList()) {
            val stream = link.streamId ?: continue
            val feed = local[link.feedId]
            if (feed == null) {
                tolerant { api.unsubscribe(stream) }
                store.removeFeedLink(link.feedId)
                links.remove(link.feedId)
                sent++
            } else if (feed.tag != link.syncedLabel) {
                api.setLabel(stream, feed.tag, link.syncedLabel)
                val updated = link.copy(syncedLabel = feed.tag)
                store.setFeedLink(updated)
                links[link.feedId] = updated
                sent++
            }
        }
        // Rows left from feeds deleted while they were phone-only.
        links.values.filter { it.streamId == null && it.feedId !in local }.forEach {
            store.removeFeedLink(it.feedId)
            links.remove(it.feedId)
        }

        // 2. The server's list, as it is.
        val remote = api.subscriptions()
        val remoteByStream = remote.associateBy { it.streamId }
        val linkedByStream = links.values.filter { it.streamId != null }.associateBy { it.streamId!! }
        val unlinkedByUrl =
            local.values
                .filter { it.id !in links }
                .associateBy { normalUrl(it.url) }
        for (sub in remote) {
            val link = linkedByStream[sub.streamId]
            if (link != null) {
                val feed = local[link.feedId] ?: continue
                if (sub.label != link.syncedLabel || (sub.title.isNotBlank() && sub.title != feed.title)) {
                    store.updateFeed(feed.id, sub.title.ifBlank { feed.title }, sub.label)
                    store.setFeedLink(link.copy(syncedLabel = sub.label))
                }
            } else {
                val match = unlinkedByUrl[normalUrl(sub.url)]
                val feedId =
                    if (match != null) {
                        store.updateFeed(match.id, sub.title.ifBlank { match.title }, sub.label)
                        match.id
                    } else {
                        added++
                        store.addFeed(sub.url, sub.title, sub.label)
                    }
                val newLink = FeedLink(feedId, sub.streamId, sub.label)
                store.setFeedLink(newLink)
                newStreams += sub.streamId
                links[feedId] = newLink
            }
        }
        for (link in linkedByStream.values) {
            if (link.streamId !in remoteByStream) {
                store.deleteFeed(link.feedId)
                store.removeFeedLink(link.feedId)
                links.remove(link.feedId)
                removed++
            }
        }

        // 3. Feeds only the phone has: offered to the server once.
        for (feed in store.localFeeds()) {
            if (feed.id in links) continue
            if (!feed.syncable) {
                store.setFeedLink(FeedLink(feed.id, null, feed.tag))
                continue
            }
            val stream =
                try {
                    api.quickAdd(feed.url)
                } catch (e: GReaderException.Unreachable) {
                    throw e
                } catch (e: GReaderException) {
                    null
                }
            if (stream == null) {
                store.setFeedLink(FeedLink(feed.id, null, feed.tag))
            } else {
                if (feed.tag.isNotBlank()) tolerant { api.setLabel(stream, feed.tag, "") }
                store.setFeedLink(FeedLink(feed.id, stream, feed.tag))
                newStreams += stream
                sent++
            }
        }
        return GReaderReport(feedsAdded = added, feedsRemoved = removed, feedsSent = sent)
    }

    private suspend fun syncItems(
        report: GReaderReport,
        sinceEpochSeconds: Long,
    ): GReaderReport {
        // 4. The phone's changes first.
        val before = store.itemStates()
        val push = GReaderMerge.push(before)
        if (!push.isEmpty) {
            if (push.markRead.isNotEmpty()) api.editTag(push.markRead, GReaderIds.READ, add = true)
            if (push.markUnread.isNotEmpty()) api.editTag(push.markUnread, GReaderIds.READ, add = false)
            if (push.star.isNotEmpty()) api.editTag(push.star, GReaderIds.STARRED, add = true)
            if (push.unstar.isNotEmpty()) api.editTag(push.unstar, GReaderIds.STARRED, add = false)
            store.setBases(GReaderMerge.afterPush(before.filter { it.differs() }))
        }
        val sentCount = push.markRead.size + push.markUnread.size + push.star.size + push.unstar.size

        // 5. Then the server's.
        val known = GReaderMerge.afterPush(before)
        val (unread, starred, recent) = api.serverItems(known, sinceEpochSeconds, newStreams)
        val unreadSet = unread.toSet()
        val starredSet = starred.toSet()
        val changes = GReaderMerge.pull(known, unreadSet, starredSet)
        if (changes.isNotEmpty()) store.apply(changes)

        // 6. What the phone has not got.
        val knownIds = known.mapTo(HashSet()) { it.remoteId }
        val wanted =
            (unread.take(maxNewUnread) + starred + recent)
                .distinct()
                .filterNot { it in knownIds }
        val feedByStream =
            store.feedLinks()
                .filter { it.streamId != null }
                .associate { it.streamId!! to it.feedId }
        var fetched = 0
        if (wanted.isNotEmpty()) {
            val items = api.contents(wanted)
            items.groupBy { it.feedStreamId }.forEach { (stream, inFeed) ->
                val feedId = feedByStream[stream] ?: return@forEach
                store.insertItems(feedId, inFeed, unreadSet, starredSet)
                fetched += inFeed.size
            }
        }
        feedByStream.values.forEach { store.prune(it, keepReadPerFeed) }

        return report.copy(itemsFetched = fetched, changesSent = sentCount, changesApplied = changes.size)
    }

    private fun GReaderMerge.ItemState.differs(): Boolean = localRead != baseRead || localStarred != baseStarred

    /** A refusal the next sync cannot fix either (the feed is already gone there) is let go. */
    private inline fun tolerant(block: () -> Unit) {
        try {
            block()
        } catch (e: GReaderException.Unreachable) {
            throw e
        } catch (e: GReaderException.WrongPassword) {
            throw e
        } catch (e: GReaderException) {
            // Nothing to do: see above.
        }
    }

    companion object {
        /** Addresses that differ only in scheme or a trailing slash are the same feed. */
        fun normalUrl(url: String): String =
            url.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('/')
                .lowercase()
    }
}
