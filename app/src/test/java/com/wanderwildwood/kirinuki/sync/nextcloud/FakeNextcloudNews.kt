package com.wanderwildwood.kirinuki.sync.nextcloud

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Credentials
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

/**
 * A small Nextcloud News server for tests, mounted where Nextcloud puts the API
 * (`/index.php/apps/news/api/v1-3/`), answering the way News 25+ does: Basic auth with an app
 * password, `folderId: null` for no folder, items newest first with `offset` as an id cursor,
 * `items/updated` by last-modified time, 409 for a feed or folder already there.
 * Made-up feeds and articles only.
 */
class FakeNextcloudNews(
    private val login: String = "ada",
    private val password: String = "app-pass-1",
) : Dispatcher() {
    data class Feed(val id: Long, val url: String, var title: String, var folderId: Long?)

    data class Item(
        val id: Long,
        val feedId: Long,
        val title: String,
        var unread: Boolean = true,
        var starred: Boolean = false,
        var lastModified: Long = 0,
    )

    val folders = mutableMapOf<Long, String>()
    val feeds = mutableListOf<Feed>()
    val items = mutableListOf<Item>()
    val requests = mutableListOf<String>()

    /** How often the whole unread list was asked for (all feeds, unread only). */
    var fullUnreadLists = 0

    /** The server's clock, in seconds; every change stamps the item with it. */
    var now = 1_700_000_000L
    private var nextId = 10L

    fun folder(name: String): Long = folders.entries.firstOrNull { it.value == name }?.key ?: (nextId++).also { folders[it] = name }

    fun addFeed(
        title: String,
        url: String,
        folder: String? = null,
    ): Feed = Feed(nextId++, url, title, folder?.let(::folder)).also { feeds += it }

    fun addItem(
        feed: Feed,
        title: String,
    ): Item = Item(nextId++, feed.id, title, lastModified = now).also { items += it }

    fun item(id: Long): Item = items.first { it.id == id }

    fun touch(item: Item) {
        item.lastModified = now
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val url = request.requestUrl!!
        val prefix = "/index.php/apps/news/api/v1-3/"
        if (!url.encodedPath.startsWith(prefix)) return MockResponse().setResponseCode(404).setBody("<html>Not found</html>")
        val path = url.encodedPath.removePrefix(prefix)
        requests += "${request.method} $path"
        if (request.getHeader("Authorization") != Credentials.basic(login, password)) {
            return MockResponse().setResponseCode(401).setBody("""{"message":"Unauthorized"}""")
        }
        val body = request.body.readUtf8().takeIf { it.isNotBlank() }?.let { mapAdapter.fromJson(it) }.orEmpty()
        val parts = path.split('/')
        return when {
            request.method == "GET" && path == "folders" -> json(mapOf("folders" to folders.map { mapOf("id" to it.key, "name" to it.value) }))
            request.method == "POST" && path == "folders" -> {
                val name = body["name"] as String
                if (folders.containsValue(name)) {
                    MockResponse().setResponseCode(409)
                } else {
                    val id = folder(name)
                    json(mapOf("folders" to listOf(mapOf("id" to id, "name" to name))))
                }
            }
            request.method == "GET" && path == "feeds" -> json(mapOf("feeds" to feeds.map(::feedJson)))
            request.method == "POST" && path == "feeds" -> {
                val address = body["url"] as String
                when {
                    feeds.any { it.url == address } -> MockResponse().setResponseCode(409)
                    address.contains("unreadable") -> MockResponse().setResponseCode(422)
                    else -> {
                        val feed = Feed(nextId++, address, "Added $address", (body["folderId"] as Double?)?.toLong())
                        feeds += feed
                        json(mapOf("feeds" to listOf(feedJson(feed))))
                    }
                }
            }
            request.method == "DELETE" && parts.size == 2 && parts[0] == "feeds" -> {
                val id = parts[1].toLong()
                if (feeds.removeAll { it.id == id }) {
                    items.removeAll { it.feedId == id }
                    MockResponse()
                } else {
                    MockResponse().setResponseCode(404)
                }
            }
            request.method == "POST" && parts.size == 3 && parts[0] == "feeds" && parts[2] == "move" -> {
                feeds.first { it.id == parts[1].toLong() }.folderId = (body["folderId"] as Double?)?.toLong()
                MockResponse().setBody("[]")
            }
            request.method == "GET" && path == "items" -> {
                val type = url.queryParameter("type")!!.toInt()
                val id = url.queryParameter("id")!!.toLong()
                val getRead = url.queryParameter("getRead") == "true"
                val batch = url.queryParameter("batchSize")!!.toInt()
                val offset = url.queryParameter("offset")?.toLong() ?: 0
                if (type == 3 && !getRead && offset == 0L) fullUnreadLists++
                val list =
                    items.sortedByDescending { it.id }
                        .filter { type != 0 || it.feedId == id }
                        .filter { type != 2 || it.starred }
                        .filter { getRead || it.unread }
                        .filter { offset == 0L || it.id < offset }
                        .let { if (batch > 0) it.take(batch) else it }
                json(mapOf("items" to list.map(::itemJson)))
            }
            request.method == "GET" && path == "items/updated" -> {
                val since = url.queryParameter("lastModified")!!.toLong()
                json(mapOf("items" to items.filter { it.lastModified >= since }.map(::itemJson)))
            }
            request.method == "POST" && parts.size == 3 && parts[0] == "items" && parts[2] == "multiple" -> {
                @Suppress("UNCHECKED_CAST")
                val ids = (body["itemIds"] as List<Double>).map { it.toLong() }
                items.filter { it.id in ids }.forEach {
                    when (parts[1]) {
                        "read" -> it.unread = false
                        "unread" -> it.unread = true
                        "star" -> it.starred = true
                        "unstar" -> it.starred = false
                    }
                    touch(it)
                }
                MockResponse().setBody("[]")
            }
            else -> MockResponse().setResponseCode(405)
        }
    }

    private fun feedJson(feed: Feed) = mapOf("id" to feed.id, "url" to feed.url, "title" to feed.title, "folderId" to feed.folderId)

    private fun itemJson(item: Item) =
        mapOf(
            "id" to item.id,
            "feedId" to item.feedId,
            "title" to item.title,
            "url" to "https://example.org/${item.feedId}/${item.id}",
            "body" to "<p>${item.title}</p>",
            "pubDate" to item.lastModified,
            "unread" to item.unread,
            "starred" to item.starred,
            "lastModified" to item.lastModified * 1_000_000,
        )

    private fun json(value: Any): MockResponse = MockResponse().setBody(anyAdapter.toJson(value))

    companion object {
        private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        private val anyAdapter = moshi.adapter(Any::class.java).serializeNulls()
        private val mapAdapter = moshi.adapter<Map<String, Any?>>(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
    }
}
