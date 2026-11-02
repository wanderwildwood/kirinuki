package com.wanderwildwood.kirinuki.sync.greader

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.net.URLDecoder

/**
 * A small Google Reader-style server for tests, mounted where FreshRSS puts it
 * (`/api/greader.php/`). It answers the way FreshRSS does: ids in short (decimal) form from
 * stream/items/ids, long form in contents, a write token from /token, continuation tokens,
 * and 401 for a bad login. Made-up feeds and articles only.
 */
class FakeGReaderServer(
    private val login: String = "ada",
    private val password: String = "api-pass-1",
    private val pageSize: Int = 2,
) : Dispatcher() {
    data class Sub(val id: String, var title: String, val url: String, var label: String)

    data class Item(
        val shortId: Long,
        val feed: String,
        val title: String,
        val link: String,
        val published: Long,
    )

    val subs = mutableListOf<Sub>()
    val items = mutableListOf<Item>()
    val read = mutableSetOf<Long>()
    val starred = mutableSetOf<Long>()
    val requests = mutableListOf<String>()
    var tokenChecks = 0
    private var nextFeed = 100

    fun addSub(
        title: String,
        url: String,
        label: String = "",
    ): Sub = Sub("feed/${nextFeed++}", title, url, label).also { subs += it }

    fun addItem(
        feed: Sub,
        shortId: Long,
        title: String,
        published: Long,
    ) {
        items += Item(shortId, feed.id, title, "https://example.org/${feed.id}/$shortId", published)
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.requestUrl!!.encodedPath.removePrefix("/api/greader.php/")
        requests += "${request.method} $path"
        if (!request.requestUrl!!.encodedPath.startsWith("/api/greader.php/")) return MockResponse().setResponseCode(404)
        if (path == "accounts/ClientLogin") {
            val form = form(request)
            return if (form.first("Email") == login && form.first("Passwd") == password) {
                MockResponse().setBody("SID=$login/abc\nLSID=null\nAuth=$login/abc\n")
            } else {
                MockResponse().setResponseCode(401).setBody("Error=BadAuthentication\n")
            }
        }
        if (request.getHeader("Authorization") != "GoogleLogin auth=$login/abc") {
            return MockResponse().setResponseCode(401).setBody("Unauthorized")
        }
        if (path == "reader/api/0/token") return MockResponse().setBody("tok-1\n")
        val form = if (request.method == "POST") form(request) else emptyMap()
        if (request.method == "POST") {
            tokenChecks++
            if (form.first("T") != "tok-1") {
                return MockResponse().setResponseCode(401).addHeader("X-Reader-Google-Bad-Token", "true")
            }
        }
        val url = request.requestUrl!!
        return when (path) {
            "reader/api/0/subscription/list" -> json(subscriptionList())
            "reader/api/0/tag/list" ->
                json(
                    """{"tags":[{"id":"user/-/state/com.google/starred"}""" +
                        subs.map { it.label }.filter { it.isNotBlank() }.distinct()
                            .joinToString("") { """,{"id":"user/-/label/$it","type":"folder"}""" } + "]}",
                )
            "reader/api/0/stream/items/ids" -> json(itemIds(url.queryParameter("s")!!, url.queryParameter("xt"), url.queryParameter("ot"), url.queryParameter("c")))
            "reader/api/0/stream/items/contents" -> json(contents(form["i"].orEmpty()))
            "reader/api/0/edit-tag" -> {
                val ids = form["i"].orEmpty().map { GReaderIds.toShort(it)!!.toLong() }
                form.first("a")?.let { tag -> target(tag).addAll(ids) }
                form.first("r")?.let { tag -> target(tag).removeAll(ids.toSet()) }
                MockResponse().setBody("OK")
            }
            "reader/api/0/subscription/quickadd" -> {
                val feedUrl = form.first("quickadd")!!
                val sub = subs.firstOrNull { it.url == feedUrl } ?: addSub("", feedUrl)
                json("""{"numResults":1,"query":"$feedUrl","streamId":"${sub.id}","streamName":""}""")
            }
            "reader/api/0/subscription/edit" -> {
                val sub = subs.firstOrNull { it.id == form.first("s") } ?: return MockResponse().setResponseCode(400)
                when (form.first("ac")) {
                    "unsubscribe" -> subs.remove(sub)
                    "edit" -> form.first("a")?.let { sub.label = GReaderIds.labelName(it)!! } ?: run {
                        if (form.first("r") != null) sub.label = ""
                    }
                }
                MockResponse().setBody("OK")
            }
            else -> MockResponse().setResponseCode(404)
        }
    }

    private fun target(tag: String): MutableSet<Long> =
        when (tag) {
            GReaderIds.READ -> read
            GReaderIds.STARRED -> starred
            else -> error("unexpected tag $tag")
        }

    private fun subscriptionList(): String =
        subs.joinToString(",", """{"subscriptions":[""", "]}") { sub ->
            val categories = if (sub.label.isBlank()) "[]" else """[{"id":"user/-/label/${sub.label}","label":"${sub.label}"}]"""
            """{"id":"${sub.id}","title":"${sub.title}","categories":$categories,"url":"${sub.url}","htmlUrl":"${sub.url}","iconUrl":""}"""
        }

    private fun itemIds(
        stream: String,
        exclude: String?,
        since: String?,
        continuation: String?,
    ): String {
        val all =
            items.filter { item ->
                when (stream) {
                    GReaderIds.READING_LIST -> true
                    GReaderIds.STARRED -> item.shortId in starred
                    else -> item.feed == stream
                }
            }.filter { exclude != GReaderIds.READ || it.shortId !in read }
                .filter { since == null || it.published > since.toLong() }
                .sortedByDescending { it.published }
        val from = continuation?.toInt() ?: 0
        val page = all.drop(from).take(pageSize)
        val next = if (from + pageSize < all.size) ""","continuation":"${from + pageSize}"""" else ""
        return page.joinToString(",", """{"itemRefs":[""", "]$next}") { """{"id":"${it.shortId}"}""" }
    }

    private fun contents(ids: List<String>): String {
        val wanted = ids.map { GReaderIds.toShort(it)!!.toLong() }.toSet()
        return items.filter { it.shortId in wanted }.joinToString(",", """{"id":"user/-/state/com.google/reading-list","items":[""", "]}") { item ->
            val categories =
                buildList {
                    add(GReaderIds.READING_LIST)
                    if (item.shortId in read) add(GReaderIds.READ)
                    if (item.shortId in starred) add(GReaderIds.STARRED)
                }.joinToString(",") { "\"$it\"" }
            """{"id":"${GReaderIds.toLong(item.shortId.toString())}","published":${item.published},"title":"${item.title}",""" +
                """"canonical":[{"href":"${item.link}"}],"alternate":[{"href":"${item.link}"}],"categories":[$categories],""" +
                """"origin":{"streamId":"${item.feed}","title":"x","htmlUrl":"https://example.org/"},""" +
                """"summary":{"content":"<p>Body of ${item.title}</p>"},"author":"Tomas Reyes"}"""
        }
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun form(request: RecordedRequest): Map<String, List<String>> =
        request.body.readUtf8().split('&').filter { it.contains('=') }.groupBy(
            { URLDecoder.decode(it.substringBefore('='), "UTF-8") },
            { URLDecoder.decode(it.substringAfter('='), "UTF-8") },
        )

    private fun Map<String, List<String>>.first(key: String): String? = this[key]?.firstOrNull()
}
