package com.wanderwildwood.kirinuki.sync.nextcloud

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.wanderwildwood.kirinuki.sync.greader.FeedServerApi
import com.wanderwildwood.kirinuki.sync.greader.GReaderApi
import com.wanderwildwood.kirinuki.sync.greader.GReaderCredentials
import com.wanderwildwood.kirinuki.sync.greader.GReaderException
import com.wanderwildwood.kirinuki.sync.greader.GReaderIds
import com.wanderwildwood.kirinuki.sync.greader.GReaderMerge
import com.wanderwildwood.kirinuki.sync.greader.GReaderSync
import com.wanderwildwood.kirinuki.sync.greader.RemoteFeed
import com.wanderwildwood.kirinuki.sync.greader.RemoteItem
import com.wanderwildwood.kirinuki.sync.greader.ServerItems
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/*
 * The JSON shapes of the Nextcloud News API v1.3 (github.com/nextcloud/news,
 * docs/api/api-v1-3.md). Every field is optional: what a server leaves out is not needed.
 */

data class NcFeedList(
    val feeds: List<NcFeed>? = null,
)

data class NcFeed(
    val id: Long? = null,
    val url: String? = null,
    val title: String? = null,
    /** Null (v1.3) or 0 (older servers) for a feed in no folder. */
    val folderId: Long? = null,
    val link: String? = null,
)

data class NcFolderList(
    val folders: List<NcFolder>? = null,
)

data class NcFolder(
    val id: Long? = null,
    val name: String? = null,
)

data class NcItemList(
    val items: List<NcItem>? = null,
)

data class NcItem(
    val id: Long? = null,
    val url: String? = null,
    val title: String? = null,
    val author: String? = null,
    val pubDate: Long? = null,
    val body: String? = null,
    val enclosureMime: String? = null,
    val enclosureLink: String? = null,
    val feedId: Long? = null,
    val unread: Boolean? = null,
    val starred: Boolean? = null,
)

/**
 * Nextcloud News over its own REST API (v1.3), the second kind of feed server Feeds syncs
 * with. Nextcloud News does not speak the Google Reader API (docs/SYNC.md), so this maps its
 * API onto what [GReaderSync] needs, and the sync, its order and its guarantees stay the same.
 *
 * Sign-in is HTTP Basic with the login name and a Nextcloud app password, typed by the person:
 * nothing to register, no browser. Feeds are `feed/<id>` streams and articles are their
 * decimal ids, so the phone's link tables hold them as they hold a Google Reader server's.
 *
 * The API has no way to list only ids, so unread articles come with their text. To keep a
 * sync small, the whole unread list is read only when [fullUnread] is set (the first sync,
 * and then once a day, see ServerSync) or the phone holds nothing from the server yet;
 * otherwise the phone's agreed state is brought up to date with what changed since the last
 * sync (`items/updated`), plus the unread articles of feeds linked in this sync. The starred
 * list is read in full every time: it is short, and it is what must never drift.
 */
class NextcloudNewsApi(
    private val client: OkHttpClient,
    private val credentials: GReaderCredentials,
    private val fullUnread: Boolean = true,
) : FeedServerApi {
    private val base: HttpUrl =
        credentials.baseUrl.toHttpUrlOrNull() ?: throw GReaderException.NotGoogleReader(0)

    private var loggedIn = false

    /** Articles read from the server in this sync, by id, for [contents]. */
    private val fetched = LinkedHashMap<Long, NcItem>()

    override fun login() {
        if (loggedIn) return
        probe(client, base, credentials.login, credentials.password)
        loggedIn = true
    }

    override fun subscriptions(): List<RemoteFeed> {
        val folders = folders()
        return get<NcFeedList>("feeds").feeds.orEmpty().mapNotNull { feed ->
            val id = feed.id ?: return@mapNotNull null
            val address = feed.url ?: feed.link ?: return@mapNotNull null
            RemoteFeed(
                streamId = streamOf(id),
                url = address,
                title = feed.title.orEmpty(),
                label = feed.folderId?.let { folders[it] }.orEmpty(),
            )
        }
    }

    override fun unsubscribe(streamId: String) {
        val id = feedIdOf(streamId) ?: return
        call("feeds/$id", method = "DELETE")
    }

    override fun setLabel(
        streamId: String,
        label: String,
        oldLabel: String,
    ) {
        val id = feedIdOf(streamId) ?: return
        val folderId = if (label.isBlank()) null else folderIdFor(label.trim())
        call("feeds/$id/move", method = "POST", json = mapOf("folderId" to folderId))
    }

    override fun quickAdd(url: String): String? {
        val body =
            try {
                call("feeds", method = "POST", json = mapOf("url" to url, "folderId" to null))
            } catch (e: GReaderException.ServerError) {
                return when (e.code) {
                    // Already there: the phone links to the server's copy.
                    409 -> findFeed(url)
                    // 422: the server could not read it. It stays on the phone only.
                    else -> null
                }
            }
        val id = parse(body, NcFeedList::class.java).feeds?.firstOrNull()?.id ?: return findFeed(url)
        return streamOf(id)
    }

    override fun editTag(
        ids: List<String>,
        tag: String,
        add: Boolean,
    ) {
        val path =
            when (tag) {
                GReaderIds.READ -> if (add) "items/read/multiple" else "items/unread/multiple"
                GReaderIds.STARRED -> if (add) "items/star/multiple" else "items/unstar/multiple"
                else -> return
            }
        ids.mapNotNull { it.toLongOrNull() }.chunked(EDIT_CHUNK).forEach { chunk ->
            val json = mapOf("itemIds" to chunk)
            try {
                call(path, method = "POST", json = json)
            } catch (e: GReaderException.ServerError) {
                // API v1.2 servers took these as PUT; v1.3 takes POST.
                if (e.code == 405) call(path, method = "PUT", json = json) else throw e
            }
        }
    }

    override fun serverItems(
        known: List<GReaderMerge.ItemState>,
        since: Long,
        newStreams: Set<String>,
    ): ServerItems {
        fetched.clear()
        val starred = items(type = TYPE_STARRED, id = 0, getRead = true, batch = -1)
        val recent = updatedSince(since)
        val unread = mutableSetOf<Long>()
        if (fullUnread || known.isEmpty()) {
            val (all, complete) = allUnread()
            unread += all
            if (!complete) {
                // Capped: older unread articles the phone holds were not listed, which does
                // not make them read. They keep the state both sides last agreed on.
                val oldest = all.minOrNull() ?: Long.MAX_VALUE
                known.filter { !it.baseRead }.mapNotNullTo(unread) { s -> s.remoteId.toLongOrNull()?.takeIf { it < oldest } }
            }
        } else {
            known.filter { !it.baseRead }.mapNotNullTo(unread) { it.remoteId.toLongOrNull() }
            newStreams.mapNotNull(::feedIdOf).forEach { feed ->
                items(type = TYPE_FEED, id = feed, getRead = false, batch = MAX_UNREAD).mapNotNullTo(unread) { it.id }
            }
        }
        // Everything read in this sync is the server's state as it is now.
        for (item in fetched.values) {
            val id = item.id ?: continue
            if (item.unread == true) unread += id else unread -= id
        }
        return ServerItems(
            unread = unread.sortedDescending().map { it.toString() },
            starred = starred.filter { it.starred != false }.mapNotNull { it.id?.toString() },
            recent = recent.mapNotNull { it.id?.toString() },
        )
    }

    override fun contents(ids: List<String>): List<RemoteItem> = ids.mapNotNull { id -> id.toLongOrNull()?.let { fetched[it] }?.toRemoteItem() }

    /** Every unread article, newest first, a page at a time; and whether that was all of them. */
    private fun allUnread(): Pair<List<Long>, Boolean> {
        val ids = mutableListOf<Long>()
        var offset = 0L
        while (true) {
            val page = items(type = TYPE_ALL, id = 0, getRead = false, batch = PAGE, offset = offset)
            page.mapNotNullTo(ids) { it.id }
            val lowest = page.mapNotNull { it.id }.minOrNull()
            if (page.size < PAGE || lowest == null) return ids to true
            if (ids.size >= MAX_UNREAD) return ids to false
            offset = lowest
        }
    }

    private fun items(
        type: Int,
        id: Long,
        getRead: Boolean,
        batch: Int,
        offset: Long = 0,
    ): List<NcItem> =
        get<NcItemList>(
            "items",
            listOf(
                "type" to type.toString(),
                "id" to id.toString(),
                "getRead" to getRead.toString(),
                "batchSize" to batch.toString(),
                "offset" to offset.toString(),
                "oldestFirst" to "false",
            ),
        ).items.orEmpty().also(::remember)

    /** Articles new, read, unread, starred or unstarred since [since] (seconds). */
    private fun updatedSince(since: Long): List<NcItem> =
        get<NcItemList>(
            "items/updated",
            listOf("lastModified" to since.coerceAtLeast(0).toString(), "type" to TYPE_ALL.toString(), "id" to "0"),
        ).items.orEmpty().also(::remember)

    private fun remember(items: List<NcItem>) {
        items.forEach { item -> item.id?.let { fetched[it] = item } }
    }

    private fun folders(): Map<Long, String> =
        get<NcFolderList>("folders").folders.orEmpty()
            .mapNotNull { f -> f.id?.let { id -> f.name?.let { id to it } } }
            .toMap()

    private fun folderIdFor(name: String): Long {
        folders().entries.firstOrNull { it.value == name }?.let { return it.key }
        try {
            val made = parse(call("folders", method = "POST", json = mapOf("name" to name)), NcFolderList::class.java)
            made.folders?.firstOrNull()?.id?.let { return it }
        } catch (e: GReaderException.ServerError) {
            if (e.code != 409) throw e
        }
        return folders().entries.firstOrNull { it.value == name }?.key ?: throw GReaderException.ServerError(409, "folder $name")
    }

    private fun findFeed(url: String): String? =
        get<NcFeedList>("feeds").feeds.orEmpty()
            .firstOrNull { it.url != null && GReaderSync.normalUrl(it.url) == GReaderSync.normalUrl(url) }
            ?.id?.let(::streamOf)

    private inline fun <reified T> get(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
    ): T = parse(call(path, params = params), T::class.java)

    /** One request, signed in with Basic. Throws [GReaderException]; 409 and 422 as [GReaderException.ServerError]. */
    private fun call(
        path: String,
        method: String = "GET",
        params: List<Pair<String, String>> = emptyList(),
        json: Map<String, Any?>? = null,
    ): String {
        login()
        val url =
            base.newBuilder().addPathSegments(path).apply {
                params.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
        val body = json?.let { mapAdapter.toJson(it).toRequestBody(JSON) }
        val request =
            Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(credentials.login, credentials.password, Charsets.UTF_8))
                .header("Accept", "application/json")
                .method(method, body ?: if (method == "POST" || method == "PUT") "".toRequestBody(JSON) else null)
                .build()
        execute(client, request).use {
            val text = it.body.string()
            return when {
                it.isSuccessful -> text
                it.code == 401 || it.code == 403 -> throw GReaderException.WrongPassword()
                it.code == 404 && method == "GET" -> throw GReaderException.NotGoogleReader(404)
                else -> throw GReaderException.ServerError(it.code, text.take(200))
            }
        }
    }

    companion object {
        const val API_PATH = "apps/news/api/v1-3"

        const val TYPE_FEED = 0
        const val TYPE_STARRED = 2
        const val TYPE_ALL = 3

        /** Unread articles per page of the full list. */
        const val PAGE = 500

        /** The most unread articles read with their text in one sync. */
        const val MAX_UNREAD = 5000

        const val EDIT_CHUNK = 250

        private val JSON = "application/json; charset=utf-8".toMediaType()

        private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        // Nulls are sent: `"folderId": null` is how v1.3 says "no folder".
        private val mapAdapter =
            moshi.adapter<Map<String, Any?>>(
                Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
            ).serializeNulls()

        fun streamOf(feedId: Long): String = "feed/$feedId"

        fun feedIdOf(streamId: String): Long? = streamId.removePrefix("feed/").toLongOrNull()

        private fun <T> parse(
            body: String,
            type: Class<T>,
        ): T =
            try {
                moshi.adapter(type).fromJson(body) ?: throw GReaderException.NotGoogleReader(200)
            } catch (e: GReaderException) {
                throw e
            } catch (e: Exception) {
                // A login page, a router's page, another app: something answered, not News.
                throw GReaderException.NotGoogleReader(200)
            }

        private fun execute(
            client: OkHttpClient,
            request: Request,
        ): Response =
            try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw GReaderException.Unreachable(e)
            }

        /**
         * The API addresses worth trying for what was typed, best first. People know their
         * Nextcloud's web address (`https://cloud.example.org`, or with `/nextcloud` after
         * it); the API is under `index.php/apps/news/api/v1-3/`, or without `index.php` where
         * the server has pretty addresses. An address already ending in the API path is used
         * as typed. `https://` is assumed when no scheme was typed.
         */
        fun candidates(typed: String): List<HttpUrl> {
            var trimmed = typed.trim().trimEnd('/')
            if (trimmed.isEmpty()) return emptyList()
            if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                trimmed = "https://$trimmed"
            }
            if (trimmed.contains("/apps/news/api/", ignoreCase = true)) {
                return listOfNotNull("$trimmed/".toHttpUrlOrNull())
            }
            // The address of a page inside Nextcloud, pasted from the browser, is cut back.
            trimmed = trimmed.substringBefore("/index.php").substringBefore("/apps/")
            return listOfNotNull(
                "$trimmed/index.php/$API_PATH/".toHttpUrlOrNull(),
                "$trimmed/$API_PATH/".toHttpUrlOrNull(),
            )
        }

        /** Signs in once: the folder list, the smallest thing that needs the password. */
        fun probe(
            client: OkHttpClient,
            base: HttpUrl,
            login: String,
            password: String,
        ) {
            if (!base.isHttps && !GReaderApi.isLocalAddress(base.host)) throw GReaderException.Insecure()
            val request =
                Request.Builder()
                    .url(base.newBuilder().addPathSegments("folders").build())
                    .header("Authorization", Credentials.basic(login, password, Charsets.UTF_8))
                    .header("Accept", "application/json")
                    .build()
            execute(client, request).use {
                val body = it.body.string()
                when {
                    it.isSuccessful -> parse(body, NcFolderList::class.java).folders ?: throw GReaderException.NotGoogleReader(200)
                    it.code == 401 || it.code == 403 -> throw GReaderException.WrongPassword()
                    it.code >= 500 -> throw GReaderException.ServerError(it.code, body.take(200))
                    else -> throw GReaderException.NotGoogleReader(it.code)
                }
            }
        }

        /** The API address that answers for what was typed, signed in, or the reason none did. */
        fun resolve(
            client: OkHttpClient,
            typed: String,
            login: String,
            password: String,
        ): String {
            val tries = candidates(typed)
            if (tries.isEmpty()) throw GReaderException.NotGoogleReader(0)
            var worst: GReaderException? = null
            for (url in tries) {
                try {
                    probe(client, url, login, password)
                    return url.toString()
                } catch (e: GReaderException) {
                    if (worst == null || rank(e) > rank(worst)) worst = e
                    if (e is GReaderException.WrongPassword || e is GReaderException.Insecure) break
                }
            }
            throw worst ?: GReaderException.NotGoogleReader(0)
        }

        private fun rank(e: GReaderException): Int =
            when (e) {
                is GReaderException.Insecure -> 5
                is GReaderException.WrongPassword -> 4
                is GReaderException.ServerError -> 3
                is GReaderException.NotGoogleReader -> 2
                is GReaderException.Unreachable -> 1
            }
    }
}

fun NcItem.toRemoteItem(): RemoteItem? {
    val itemId = id ?: return null
    val feed = feedId ?: return null
    return RemoteItem(
        id = itemId.toString(),
        feedStreamId = NextcloudNewsApi.streamOf(feed),
        title = title.orEmpty(),
        link = url?.takeIf { it.isNotBlank() },
        html = body.orEmpty(),
        author = author?.takeIf { it.isNotBlank() },
        publishedEpochSeconds = pubDate,
        enclosureUrl = enclosureLink?.takeIf { it.isNotBlank() },
        enclosureType = enclosureMime?.takeIf { it.isNotBlank() },
    )
}
