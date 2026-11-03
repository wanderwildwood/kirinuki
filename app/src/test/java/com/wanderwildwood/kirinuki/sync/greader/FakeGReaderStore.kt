package com.wanderwildwood.kirinuki.sync.greader

/** The phone's side in memory: feeds, articles with read and starred, and the links. */
class FakeGReaderStore : GReaderStore {
    data class Feed(val id: Long, var url: String, var title: String, var tag: String, val syncable: Boolean = true)

    data class Article(
        val id: Long,
        val feedId: Long,
        var title: String,
        val link: String?,
        var read: Boolean,
        var starred: Boolean,
        var body: String = "",
    )

    val feeds = mutableListOf<Feed>()
    val articles = mutableListOf<Article>()
    val feedLinks = mutableMapOf<Long, FeedLink>()

    /** item id to (remote id, base read, base starred) */
    val itemLinks = mutableMapOf<Long, Triple<String, Boolean, Boolean>>()
    private var nextId = 1L

    fun addLocalFeed(
        url: String,
        title: String,
        tag: String = "",
        syncable: Boolean = true,
    ): Feed = Feed(nextId++, url, title, tag, syncable).also { feeds += it }

    fun addLocalArticle(
        feedId: Long,
        title: String,
        link: String,
        read: Boolean = false,
    ): Article = Article(nextId++, feedId, title, link, read, false).also { articles += it }

    fun article(remoteShortId: Long): Article? {
        val remote = GReaderIds.toLong(remoteShortId.toString())
        val itemId = itemLinks.entries.firstOrNull { it.value.first == remote }?.key ?: return null
        return articles.first { it.id == itemId }
    }

    /** The article the server knows as [remoteId], as stored (Nextcloud News: its decimal id). */
    fun articleByRemote(remoteId: String): Article? {
        val itemId = itemLinks.entries.firstOrNull { it.value.first == remoteId }?.key ?: return null
        return articles.first { it.id == itemId }
    }

    override suspend fun localFeeds(): List<LocalFeed> = feeds.map { LocalFeed(it.id, it.url, it.title, it.tag, it.syncable) }

    override suspend fun feedLinks(): List<FeedLink> = feedLinks.values.toList()

    override suspend fun setFeedLink(link: FeedLink) {
        feedLinks[link.feedId] = link
    }

    override suspend fun removeFeedLink(feedId: Long) {
        feedLinks.remove(feedId)
    }

    override suspend fun addFeed(
        url: String,
        title: String,
        tag: String,
    ): Long = addLocalFeed(url, title, tag).id

    override suspend fun updateFeed(
        feedId: Long,
        title: String,
        tag: String,
    ) {
        feeds.first { it.id == feedId }.apply {
            this.title = title
            this.tag = tag
        }
    }

    override suspend fun deleteFeed(feedId: Long) {
        feeds.removeAll { it.id == feedId }
        val gone = articles.filter { it.feedId == feedId && !it.starred }.map { it.id }.toSet()
        articles.removeAll { it.id in gone }
        gone.forEach { itemLinks.remove(it) }
        // Kept ones stay, belonging to no feed, and leave the sync (itemStates skips them).
        articles.filter { it.feedId == feedId }.forEach { itemLinks.remove(it.id) }
    }

    override suspend fun itemStates(): List<GReaderMerge.ItemState> =
        itemLinks.map { (itemId, link) ->
            val article = articles.first { it.id == itemId }
            GReaderMerge.ItemState(itemId, link.first, link.second, link.third, article.read, article.starred)
        }

    override suspend fun setBases(items: List<GReaderMerge.ItemState>) {
        items.forEach { itemLinks[it.itemId] = Triple(it.remoteId, it.baseRead, it.baseStarred) }
    }

    override suspend fun apply(changes: List<GReaderMerge.Apply>) {
        changes.forEach { change ->
            val article = articles.first { it.id == change.itemId }
            change.read?.let { article.read = it }
            change.starred?.let { article.starred = it }
            itemLinks[change.itemId] = Triple(itemLinks[change.itemId]!!.first, article.read, article.starred)
        }
    }

    override suspend fun insertItems(
        feedId: Long,
        items: List<RemoteItem>,
        unreadIds: Set<String>,
        starredIds: Set<String>,
    ) {
        for (remote in items) {
            val serverRead = remote.id !in unreadIds
            val serverStarred = remote.id in starredIds
            val adopted = articles.firstOrNull { it.feedId == feedId && it.link == remote.link && it.id !in itemLinks }
            val article =
                if (adopted != null) {
                    adopted.read = adopted.read || serverRead
                    adopted.starred = adopted.starred || serverStarred
                    adopted
                } else {
                    Article(nextId++, feedId, remote.title, remote.link, serverRead, serverStarred, remote.html).also { articles += it }
                }
            itemLinks[article.id] = Triple(remote.id, serverRead, serverStarred)
        }
    }

    override suspend fun prune(
        feedId: Long,
        keep: Int,
    ) {
        val doomed = articles.filter { it.feedId == feedId && it.read && !it.starred }.drop(keep).map { it.id }.toSet()
        articles.removeAll { it.id in doomed }
        doomed.forEach { itemLinks.remove(it) }
    }
}
