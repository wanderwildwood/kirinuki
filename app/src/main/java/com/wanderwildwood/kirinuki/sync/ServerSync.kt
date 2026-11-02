package com.wanderwildwood.kirinuki.sync

import android.app.Application
import android.content.Context
import android.util.Log
import com.wanderwildwood.kirinuki.archmodel.FailKind
import com.wanderwildwood.kirinuki.archmodel.SyncFailure
import com.wanderwildwood.kirinuki.db.room.GReaderDao
import com.wanderwildwood.kirinuki.model.UserAgentInterceptor
import com.wanderwildwood.kirinuki.sync.greader.GReaderApi
import com.wanderwildwood.kirinuki.sync.greader.GReaderCredentials
import com.wanderwildwood.kirinuki.sync.greader.GReaderException
import com.wanderwildwood.kirinuki.sync.greader.GReaderSync
import com.wanderwildwood.kirinuki.sync.greader.RoomGReaderStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Why the feed server could not be used, one sentence each in Settings. */
enum class ServerProblem { UNREACHABLE, WRONG_PASSWORD, NOT_GOOGLE_READER, INSECURE, SERVER_ERROR, PASSWORD_LOST }

data class ServerAccount(
    /** The API address that answered, which may be longer than what was typed. */
    val apiUrl: String,
    val login: String,
)

data class ServerSyncState(
    val account: ServerAccount? = null,
    val lastSync: Instant? = null,
    val problem: ServerProblem? = null,
    val problemCode: Int = 0,
)

/** What a Test or Sign in came to. */
sealed class ServerCheck {
    data class Works(val apiUrl: String) : ServerCheck()

    data class Failed(val problem: ServerProblem, val code: Int = 0) : ServerCheck()
}

fun GReaderException.toProblem(): ServerProblem =
    when (this) {
        is GReaderException.Unreachable -> ServerProblem.UNREACHABLE
        is GReaderException.WrongPassword -> ServerProblem.WRONG_PASSWORD
        is GReaderException.NotGoogleReader -> ServerProblem.NOT_GOOGLE_READER
        is GReaderException.Insecure -> ServerProblem.INSECURE
        is GReaderException.ServerError -> ServerProblem.SERVER_ERROR
    }

fun GReaderException.code(): Int =
    when (this) {
        is GReaderException.ServerError -> code
        is GReaderException.NotGoogleReader -> code
        else -> 0
    }

/**
 * Sync with a feed server: off until someone signs in, in Settings, with an address, a
 * login name and an API password. Then every refresh (pull, button, background) first syncs
 * with that server, and the feeds that are on it are no longer fetched from their sites.
 * Stopping forgets the server and keeps every feed and article where it is.
 *
 * The server is the only place this talks to, and only what was typed is used.
 */
class ServerSync(
    override val di: DI,
) : DIAware {
    private val application: Application by instance()
    private val dao: GReaderDao by instance()
    private val store by lazy { RoomGReaderStore(di) }
    private val prefs by lazy { application.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val client: OkHttpClient by lazy {
        // Its own client: the app's shared one keeps an HTTP cache and, in debug builds, logs
        // request headers, and this one carries a login token in every request.
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addNetworkInterceptor(UserAgentInterceptor)
            .build()
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<ServerSyncState> = _state

    val isOn: Boolean get() = _state.value.account != null

    private fun load(): ServerSyncState {
        val url = prefs.getString(KEY_URL, null) ?: return ServerSyncState()
        val login = prefs.getString(KEY_LOGIN, null) ?: return ServerSyncState()
        return ServerSyncState(
            account = ServerAccount(url, login),
            lastSync = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 }?.let { Instant.ofEpochSecond(it) },
        )
    }

    /** Tries the address, login name and password, and says what happened; stores nothing. */
    suspend fun test(
        address: String,
        login: String,
        password: String,
    ): ServerCheck =
        withContext(Dispatchers.IO) {
            try {
                ServerCheck.Works(GReaderApi.resolve(client, address, login.trim(), password))
            } catch (e: GReaderException) {
                ServerCheck.Failed(e.toProblem(), e.code())
            }
        }

    /** [test], and if it works, keeps the account (the password sealed) and turns sync on. */
    suspend fun signIn(
        address: String,
        login: String,
        password: String,
    ): ServerCheck {
        val check = test(address, login, password)
        if (check is ServerCheck.Works) {
            val sealed = Secrets.seal(password) ?: return ServerCheck.Failed(ServerProblem.PASSWORD_LOST)
            prefs.edit()
                .putString(KEY_URL, check.apiUrl)
                .putString(KEY_LOGIN, login.trim())
                .putString(KEY_PASSWORD, sealed)
                .remove(KEY_LAST_SYNC)
                .apply()
            _state.value = ServerSyncState(account = ServerAccount(check.apiUrl, login.trim()))
        }
        return check
    }

    /**
     * Stops syncing: forgets the address, login and password, and which feeds and articles
     * came from the server. Every feed and article stays; the feeds are fetched from their
     * sites again from the next refresh.
     */
    suspend fun stop() {
        prefs.edit().clear().apply()
        _state.value = ServerSyncState()
        withContext(Dispatchers.IO) { store.forgetServer() }
    }

    /** The feeds whose articles now come from the server; empty when sync is off. */
    suspend fun serverFeedIds(): Set<Long> = if (isOn) dao.serverFeedIds().toSet() else emptySet()

    /**
     * One sync, run inside the app's refresh. Null when it worked or sync is off; otherwise
     * the failure, worded for the pull line under the server's own name.
     */
    suspend fun syncNow(keepReadPerFeed: Int): SyncFailure? {
        val account = _state.value.account ?: return null
        val host = account.apiUrl.toHttpUrlOrNull()?.host ?: account.apiUrl
        val password = Secrets.open(prefs.getString(KEY_PASSWORD, null))
        if (password == null) {
            _state.value = _state.value.copy(problem = ServerProblem.PASSWORD_LOST, problemCode = 0)
            return SyncFailure(title = host, kind = FailKind.SIGN_IN_REFUSED)
        }
        val started = Instant.now().epochSecond
        val since = (prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 } ?: (started - FIRST_SYNC_SECONDS)) - OVERLAP_SECONDS
        return try {
            val api = GReaderApi(client, GReaderCredentials(account.apiUrl, account.login, password))
            val report = GReaderSync(api, store, keepReadPerFeed = keepReadPerFeed).sync(since)
            Log.i(LOG_TAG, "Synced with the feed server: $report")
            prefs.edit().putLong(KEY_LAST_SYNC, started).apply()
            _state.value = _state.value.copy(lastSync = Instant.ofEpochSecond(started), problem = null, problemCode = 0)
            null
        } catch (e: GReaderException) {
            Log.w(LOG_TAG, "Feed server sync failed: ${e.message}")
            _state.value = _state.value.copy(problem = e.toProblem(), problemCode = e.code())
            SyncFailure(
                title = host,
                kind =
                    when (e) {
                        is GReaderException.Unreachable -> FailKind.NO_CONNECTION
                        is GReaderException.WrongPassword -> FailKind.SIGN_IN_REFUSED
                        is GReaderException.ServerError -> FailKind.SERVER_ANSWERED
                        else -> FailKind.OTHER
                    },
                code = e.code(),
            )
        }
    }

    companion object {
        private const val LOG_TAG = "KIRINUKI_SERVER_SYNC"
        private const val PREFS = "server_sync"
        private const val KEY_URL = "api_url"
        private const val KEY_LOGIN = "login"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LAST_SYNC = "last_sync"

        /** How far back the first sync looks for articles already read (unread ones all come). */
        private const val FIRST_SYNC_SECONDS = 14L * 24 * 3600

        /** Each sync looks an hour further back than the last one finished, for late arrivals. */
        private const val OVERLAP_SECONDS = 3600L
    }
}
