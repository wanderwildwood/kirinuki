package com.wanderwildwood.kirinuki.sync.greader

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Why talking to the server did not work, in the few ways a person can act on. Each one is a
 * different sentence in Settings and on the pull line.
 */
sealed class GReaderException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Nothing answered: no network, a wrong address, the server down. */
    class Unreachable(cause: Throwable? = null) : GReaderException("unreachable", cause)

    /** The server answered and refused the login name and password. */
    class WrongPassword : GReaderException("wrong password")

    /** Something answered, but not as a Google Reader-style API does. */
    class NotGoogleReader(val code: Int) : GReaderException("not a Google Reader API ($code)")

    /** An http:// address on the open internet: the password would cross it unencrypted. */
    class Insecure : GReaderException("plain http to a public address")

    /** The server answered a request with an error of its own. */
    class ServerError(val code: Int, detail: String) : GReaderException("server error $code: $detail")
}

/** Where to send, who as. The password is only ever held here, never logged. */
data class GReaderCredentials(
    val baseUrl: String,
    val login: String,
    val password: String,
)

/**
 * The Google Reader API over HTTP, the dialect FreshRSS and Miniflux speak (docs/SYNC.md).
 * Blocking calls: they run on the sync's own thread, as every other fetch in the app does.
 *
 * Sign-in is ClientLogin with a login name and an API password, typed by the person, the only
 * way in this app supports: nothing to register with any service, no browser. The request
 * shapes follow Read You's GoogleReaderAPI (https://github.com/Ashinch/ReadYou, GPL-3.0).
 */
class GReaderApi(
    private val client: OkHttpClient,
    private val credentials: GReaderCredentials,
) : FeedServerApi {
    private var auth: String? = null
    private var actionToken: String? = null

    private val base: HttpUrl =
        credentials.baseUrl.toHttpUrlOrNull() ?: throw GReaderException.NotGoogleReader(0)

    /** Logs in if not already in. Throws [GReaderException]. */
    override fun login() {
        if (auth != null) return
        auth = clientLogin(client, base, credentials.login, credentials.password)
    }

    override fun subscriptions(): List<RemoteFeed> =
        get<GrSubscriptionList>("reader/api/0/subscription/list")
            .subscriptions.orEmpty()
            .mapNotNull { it.toRemoteFeed() }

    /** The folder names the server has. */
    fun labels(): List<String> =
        get<GrTagList>("reader/api/0/tag/list")
            .tags.orEmpty()
            .mapNotNull { GReaderIds.labelName(it.id) }

    /**
     * Every item id in [stream], following continuation tokens, newest first, at most [max].
     * [exclude] leaves out items carrying that state (`xt`); [since] only those newer (`ot`).
     */
    fun itemIds(
        stream: String,
        exclude: String? = null,
        since: Long? = null,
        max: Int = MAX_IDS,
    ): List<String> {
        val ids = mutableListOf<String>()
        var continuation: String? = null
        do {
            val params =
                buildList {
                    add("s" to stream)
                    exclude?.let { add("xt" to it) }
                    since?.let { add("ot" to it.toString()) }
                    add("n" to PAGE.toString())
                    continuation?.let { add("c" to it) }
                }
            val page = get<GrItemIds>("reader/api/0/stream/items/ids", params)
            page.itemRefs.orEmpty().mapNotNullTo(ids) { ref -> ref.id?.let { GReaderIds.toLong(it) } }
            // A server that hands back the same token would page forever.
            val next = page.continuation?.takeIf { it.isNotBlank() && it != continuation }
            continuation = next
        } while (continuation != null && ids.size < max && page.itemRefs.orEmpty().isNotEmpty())
        return ids.distinct().take(max)
    }

    /** Ids are cheap here, so the whole unread and starred lists are read every time. */
    override fun serverItems(
        known: List<GReaderMerge.ItemState>,
        since: Long,
        newStreams: Set<String>,
    ): ServerItems =
        ServerItems(
            unread = itemIds(GReaderIds.READING_LIST, exclude = GReaderIds.READ),
            starred = itemIds(GReaderIds.STARRED),
            recent = itemIds(GReaderIds.READING_LIST, since = since),
        )

    /** The articles for [ids], a hundred to a request. */
    override fun contents(ids: List<String>): List<RemoteItem> =
        ids.chunked(CONTENTS_CHUNK).flatMap { chunk ->
            post<GrContents>(
                "reader/api/0/stream/items/contents",
                chunk.map { "i" to it },
            ).items.orEmpty().mapNotNull { it.toRemoteItem() }
        }

    /** Adds [tag] to (or with [add] false, takes it from) every one of [ids]. */
    override fun editTag(
        ids: List<String>,
        tag: String,
        add: Boolean,
    ) {
        ids.chunked(EDIT_CHUNK).forEach { chunk ->
            postText(
                "reader/api/0/edit-tag",
                chunk.map { "i" to it } + ((if (add) "a" else "r") to tag),
            )
        }
    }

    /** Subscribes to [url]; the server's stream id for it, or null if it could not. */
    override fun quickAdd(url: String): String? =
        post<GrQuickAdd>(
            "reader/api/0/subscription/quickadd",
            listOf("quickadd" to url),
        ).streamId?.takeIf { it.isNotBlank() }

    /** Files [streamId] under [label] (blank for none), taking it out of [oldLabel]. */
    override fun setLabel(
        streamId: String,
        label: String,
        oldLabel: String,
    ) {
        val form =
            buildList {
                add("ac" to "edit")
                add("s" to streamId)
                if (label.isNotBlank()) add("a" to GReaderIds.labelStream(label))
                if (oldLabel.isNotBlank() && oldLabel != label) add("r" to GReaderIds.labelStream(oldLabel))
            }
        postText("reader/api/0/subscription/edit", form)
    }

    override fun unsubscribe(streamId: String) {
        postText("reader/api/0/subscription/edit", listOf("ac" to "unsubscribe", "s" to streamId))
    }

    private inline fun <reified T> get(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
    ): T = parse(call(path, params, form = null), T::class.java)

    private inline fun <reified T> post(
        path: String,
        form: List<Pair<String, String>>,
    ): T = parse(call(path, emptyList(), form), T::class.java)

    private fun postText(
        path: String,
        form: List<Pair<String, String>>,
    ): String = call(path, emptyList(), form)

    private fun <T> parse(
        body: String,
        type: Class<T>,
    ): T =
        try {
            moshi.adapter(type).fromJson(body) ?: throw GReaderException.NotGoogleReader(200)
        } catch (e: GReaderException) {
            throw e
        } catch (e: Exception) {
            throw GReaderException.NotGoogleReader(200)
        }

    /**
     * One request, logged in. A 401 logs in again once (the server may have forgotten the
     * session); a refused action token is fetched again once.
     */
    private fun call(
        path: String,
        params: List<Pair<String, String>>,
        form: List<Pair<String, String>>?,
        retried: Boolean = false,
    ): String {
        login()
        if (form != null && actionToken == null) actionToken = fetchToken()
        val url =
            base.newBuilder().addPathSegments(path).apply {
                addQueryParameter("output", "json")
                params.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
        val request =
            Request.Builder()
                .url(url)
                .header("Authorization", "GoogleLogin auth=$auth")
                .apply {
                    if (form != null) {
                        post(
                            FormBody.Builder().apply {
                                form.forEach { (k, v) -> add(k, v) }
                                actionToken?.let { add("T", it) }
                            }.build(),
                        )
                    }
                }.build()
        val response = execute(client, request)
        response.use {
            val body = it.body.string()
            when {
                it.isSuccessful -> return body
                it.code == 401 && !retried -> {
                    if (it.header("X-Reader-Google-Bad-Token") != null) {
                        actionToken = null
                    } else {
                        auth = null
                        actionToken = null
                    }
                    return call(path, params, form, retried = true)
                }
                it.code == 401 || it.code == 403 -> throw GReaderException.WrongPassword()
                it.code == 404 -> throw GReaderException.NotGoogleReader(404)
                else -> throw GReaderException.ServerError(it.code, body.take(200))
            }
        }
    }

    private fun fetchToken(): String? {
        val request =
            Request.Builder()
                .url(base.newBuilder().addPathSegments("reader/api/0/token").build())
                .header("Authorization", "GoogleLogin auth=$auth")
                .build()
        execute(client, request).use {
            // Not every server needs one for writing; a server without it simply takes none.
            return if (it.isSuccessful) it.body.string().trim().takeIf { t -> t.isNotEmpty() } else null
        }
    }

    companion object {
        /** Ids per page of stream/items/ids. FreshRSS and Miniflux both accept this many. */
        const val PAGE = 1000

        /** The most ids asked for in one stream: enough for any unread pile a phone can hold. */
        const val MAX_IDS = 20_000

        const val CONTENTS_CHUNK = 100
        const val EDIT_CHUNK = 250

        private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        @Suppress("unused")
        private val loginAdapter: JsonAdapter<GrLoginJson> = moshi.adapter(GrLoginJson::class.java)

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
         * The addresses worth trying for what was typed, best first: as typed, then with
         * FreshRSS's `/api/greader.php` added (its web address is what people know; the API
         * lives under it). `https://` is assumed when no scheme was typed.
         */
        fun candidates(typed: String): List<HttpUrl> {
            val trimmed = typed.trim().trimEnd('/')
            if (trimmed.isEmpty()) return emptyList()
            val withScheme =
                if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                    trimmed
                } else {
                    "https://$trimmed"
                }
            val asTyped = "$withScheme/".toHttpUrlOrNull() ?: return emptyList()
            return buildList {
                add(asTyped)
                if (!withScheme.endsWith("greader.php")) {
                    "$withScheme/api/greader.php/".toHttpUrlOrNull()?.let { add(it) }
                }
            }
        }

        /**
         * True for an address where plain http is acceptable: this phone, the home network
         * (private and link-local ranges, `.local`, `.lan`, `.home`, `.home.arpa`, `.internal`), and a Tailscale
         * address (100.64.0.0/10, already encrypted end to end). Anything else must be https.
         */
        fun isLocalAddress(host: String): Boolean {
            val h = host.lowercase().trim('[', ']')
            if (h == "localhost" || h == "::1") return true
            if (listOf(".local", ".lan", ".home", ".home.arpa", ".internal").any { h.endsWith(it) }) return true
            if (h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80:")) return h.contains(':')
            val parts = h.split('.').mapNotNull { it.toIntOrNull() }
            if (parts.size != 4 || h.count { it == '.' } != 3) return false
            val (a, b) = parts
            return a == 10 || a == 127 ||
                (a == 192 && b == 168) ||
                (a == 172 && b in 16..31) ||
                (a == 169 && b == 254) ||
                (a == 100 && b in 64..127)
        }

        /**
         * ClientLogin: the login name and API password for an `Auth` token. FreshRSS answers
         * in lines (`SID=…`, `Auth=…`), Miniflux the same; JSON is read too, for servers that
         * honour `output=json`.
         */
        fun clientLogin(
            client: OkHttpClient,
            base: HttpUrl,
            login: String,
            password: String,
        ): String {
            if (!base.isHttps && !isLocalAddress(base.host)) throw GReaderException.Insecure()
            val request =
                Request.Builder()
                    .url(base.newBuilder().addPathSegments("accounts/ClientLogin").build())
                    .post(
                        FormBody.Builder()
                            .add("Email", login)
                            .add("Passwd", password)
                            .add("service", "reader")
                            .add("accountType", "HOSTED_OR_GOOGLE")
                            .add("client", "Feeds")
                            .build(),
                    ).build()
            execute(client, request).use {
                val body = it.body.string()
                val token =
                    body.lineSequence().map { line -> line.trim() }
                        .firstOrNull { line -> line.startsWith("Auth=") }
                        ?.removePrefix("Auth=")
                        ?: runCatching { loginAdapter.fromJson(body)?.Auth }.getOrNull()
                return when {
                    it.isSuccessful && !token.isNullOrBlank() -> token
                    // FreshRSS and Miniflux say "Error=BadAuthentication" with 401 or 403.
                    it.code == 401 || it.code == 403 || body.contains("BadAuthentication") ->
                        throw GReaderException.WrongPassword()
                    else -> throw GReaderException.NotGoogleReader(it.code)
                }
            }
        }

        /**
         * The address that answers as a Google Reader API for what was typed, logged in, or
         * the reason none did. A refused password is the answer that matters most: it means
         * the address was right.
         */
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
                    clientLogin(client, url, login, password)
                    return url.toString()
                } catch (e: GReaderException) {
                    worst = moreTelling(worst, e)
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

        private fun moreTelling(
            a: GReaderException?,
            b: GReaderException,
        ): GReaderException = if (a == null || rank(b) > rank(a)) b else a
    }
}
