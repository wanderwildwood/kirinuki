package com.wanderwildwood.kirinuki.sync.greader

import android.util.Log
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.blob.blobFile
import com.wanderwildwood.kirinuki.blob.blobFullFile
import com.wanderwildwood.kirinuki.blob.blobOutputStream
import com.wanderwildwood.kirinuki.db.room.Feed
import com.wanderwildwood.kirinuki.db.room.FeedDao
import com.wanderwildwood.kirinuki.db.room.FeedItem
import com.wanderwildwood.kirinuki.db.room.FeedItemDao
import com.wanderwildwood.kirinuki.db.room.GReaderDao
import com.wanderwildwood.kirinuki.db.room.GReaderFeedRow
import com.wanderwildwood.kirinuki.db.room.GReaderItemRow
import com.wanderwildwood.kirinuki.db.room.ID_UNSET
import com.wanderwildwood.kirinuki.db.room.isSavedPages
import com.wanderwildwood.kirinuki.db.room.upsertFeedItems
import com.wanderwildwood.kirinuki.model.ParsedArticle
import com.wanderwildwood.kirinuki.model.ParsedAuthor
import com.wanderwildwood.kirinuki.model.ParsedEnclosure
import com.wanderwildwood.kirinuki.model.ParsedFeed
import com.wanderwildwood.kirinuki.util.FilePathProvider
import org.jsoup.Jsoup
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** The phone side of [GReaderSync], on the app's own database and article files. */
class RoomGReaderStore(
    override val di: DI,
) : DIAware,
    GReaderStore {
    private val feedDao: FeedDao by instance()
    private val itemDao: FeedItemDao by instance()
    private val dao: GReaderDao by instance()
    private val repository: Repository by instance()
    private val files: FilePathProvider by instance()

    override suspend fun localFeeds(): List<LocalFeed> =
        feedDao.getFeedsOrderedByUrl().map { feed ->
            LocalFeed(
                id = feed.id,
                url = feed.url.toString(),
                title = feed.title,
                tag = feed.tag,
                syncable = !feed.isSavedPages && feed.url.protocol.lowercase() in setOf("http", "https"),
            )
        }

    override suspend fun feedLinks(): List<FeedLink> = dao.feedRows().map { FeedLink(it.feedId, it.streamId, it.syncedLabel) }

    override suspend fun setFeedLink(link: FeedLink) = dao.upsertFeedRow(GReaderFeedRow(link.feedId, link.streamId, link.syncedLabel))

    override suspend fun removeFeedLink(feedId: Long) = dao.deleteFeedRow(feedId)

    override suspend fun addFeed(
        url: String,
        title: String,
        tag: String,
    ): Long {
        val address = URL(url)
        feedDao.loadFeedWithUrl(address)?.let { existing ->
            feedDao.updateFeed(existing.copy(title = title.ifBlank { existing.title }, tag = tag))
            return existing.id
        }
        return feedDao.insertFeed(Feed(url = address, title = title, tag = tag))
    }

    override suspend fun updateFeed(
        feedId: Long,
        title: String,
        tag: String,
    ) {
        val feed = feedDao.getFeed(feedId) ?: return
        if (feed.title != title || feed.tag != tag) feedDao.updateFeed(feed.copy(title = title, tag = tag))
    }

    override suspend fun deleteFeed(feedId: Long) = repository.deleteFeeds(listOf(feedId))

    override suspend fun itemStates(): List<GReaderMerge.ItemState> =
        dao.itemStates().map {
            GReaderMerge.ItemState(
                itemId = it.itemId,
                remoteId = it.remoteId,
                baseRead = it.syncedRead,
                baseStarred = it.syncedStarred,
                localRead = it.isRead,
                localStarred = it.bookmarked,
            )
        }

    override suspend fun setBases(items: List<GReaderMerge.ItemState>) {
        items.forEach { dao.setBase(it.itemId, it.baseRead, it.baseStarred) }
    }

    override suspend fun apply(changes: List<GReaderMerge.Apply>) {
        for (change in changes) {
            when (change.read) {
                true -> itemDao.markAsRead(listOf(change.itemId))
                false -> itemDao.markAsUnread(change.itemId)
                null -> Unit
            }
            change.starred?.let { itemDao.setBookmarked(change.itemId, it) }
            val now = itemDao.loadFeedItem(change.itemId) ?: continue
            dao.setBase(change.itemId, read = !now.unread, starred = now.bookmarked)
        }
    }

    override suspend fun insertItems(
        feedId: Long,
        items: List<RemoteItem>,
        unreadIds: Set<String>,
        starredIds: Set<String>,
    ) {
        val now = Instant.now()
        val emptyFeed = ParsedFeed(title = null, items = null)
        val rows = mutableListOf<Pair<FeedItem, String>>()
        val bases = java.util.IdentityHashMap<FeedItem, Pair<String, Pair<Boolean, Boolean>>>()
        for (remote in items) {
            val serverRead = remote.id !in unreadIds
            val serverStarred = remote.id in starredIds
            val adopted =
                remote.link?.let { dao.unlinkedItemWithLink(feedId, it) }?.let { itemDao.loadFeedItem(it) }
                    ?: itemDao.loadFeedItem(guid = remote.id, feedId = feedId)
            val item = adopted ?: FeedItem(firstSyncedTime = now)
            item.updateFromParsedEntry(remote.toParsedArticle(), adopted?.guid ?: remote.id, emptyFeed)
            item.feedId = feedId
            if (adopted == null) {
                item.readTime = if (serverRead) now else null
                item.notified = serverRead
                item.bookmarked = serverStarred
            } else {
                // Taken over from the phone's own fetch: what either side has read or starred
                // stays so, and the base is the server's, so the phone's part is sent next time.
                if (serverRead && item.readTime == null) {
                    item.readTime = now
                    item.notified = true
                }
                item.bookmarked = item.bookmarked || serverStarred
            }
            rows += item to remote.html
            bases[item] = remote.id to (serverRead to serverStarred)
        }
        val links = mutableListOf<GReaderItemRow>()
        itemDao.upsertFeedItems(rows) { item, text ->
            if (item.id <= ID_UNSET) return@upsertFeedItems
            files.articleDir.mkdirs()
            blobOutputStream(item.id, files.articleDir).bufferedWriter().use { it.write(text) }
            bases[item]?.let { (remoteId, base) ->
                links += GReaderItemRow(item.id, remoteId, syncedRead = base.first, syncedStarred = base.second)
            }
        }
        if (links.isNotEmpty()) dao.upsertItemRows(links)
    }

    override suspend fun prune(
        feedId: Long,
        keep: Int,
    ) {
        val ids = dao.prunable(feedId, keep).filterNot { it == repository.currentArticleId.value }
        if (ids.isEmpty()) return
        for (id in ids) {
            runCatching { blobFile(id, files.articleDir).takeIf { it.isFile }?.delete() }
                .onFailure { Log.e(LOG_TAG, "Could not delete an article file", it) }
            runCatching { blobFullFile(id, files.fullArticleDir).takeIf { it.isFile }?.delete() }
                .onFailure { Log.e(LOG_TAG, "Could not delete a full article file", it) }
        }
        itemDao.deleteFeedItems(ids)
    }

    /** Forgets every link to the server; feeds and articles stay as they are. */
    suspend fun forgetServer() {
        dao.deleteAllItemRows()
        dao.deleteAllFeedRows()
    }

    companion object {
        private const val LOG_TAG = "KIRINUKI_GREADER"

        fun RemoteItem.toParsedArticle(): ParsedArticle =
            ParsedArticle(
                id = id,
                url = link,
                title = plainTitle(title),
                content_html = html,
                date_published =
                    publishedEpochSeconds?.let {
                        ZonedDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneOffset.UTC).toString()
                    },
                author = author?.let { ParsedAuthor(name = it) },
                attachments = enclosureUrl?.let { listOf(ParsedEnclosure(url = it, mime_type = enclosureType)) },
            )

        /** Servers send titles HTML-escaped (`&amp;`); the phone keeps plain text. */
        fun plainTitle(title: String): String =
            if (title.contains('&') || title.contains('<')) Jsoup.parse(title).text() else title
    }
}
