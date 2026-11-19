package com.wanderwildwood.kirinuki.sync

import android.app.Application
import android.content.Context
import android.util.Log
import com.wanderwildwood.kirinuki.archmodel.FailKind
import com.wanderwildwood.kirinuki.archmodel.SyncFailure
import com.wanderwildwood.kirinuki.db.room.GReaderDao
import com.wanderwildwood.kirinuki.model.ServerUserAgentInterceptor
import com.wanderwildwood.kirinuki.model.syncMutex
import com.wanderwildwood.kirinuki.sync.greader.GReaderApi
import com.wanderwildwood.kirinuki.sync.greader.GReaderCredentials
import com.wanderwildwood.kirinuki.sync.greader.GReaderException
import com.wanderwildwood.kirinuki.sync.greader.GReaderSync
import com.wanderwildwood.kirinuki.sync.greader.RoomGReaderStore
import com.wanderwildwood.kirinuki.sync.nextcloud.NextcloudNewsApi
import com.wanderwildwood.kirinuki.util.logDebug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Why the feed server could not be used, one sentence each in Settings. */
enum class ServerProblem { UNREACHABLE, WRONG_PASSWORD, NOT_GOOGLE_READER, INSECURE, SERVER_ERROR, PASSWORD_LOST, NOT_HOME_NETWORK, HOME_SIGNED_OUT }

/** The kinds of feed server Feeds syncs with. The name is what is stored. */
enum class ServerKind {
    /** The Google Reader API: FreshRSS, Miniflux and others (sync/greader). */
    GOOGLE_READER,

    /** Nextcloud News' own API (sync/nextcloud). */
    NEXTCLOUD_NEWS,
}

data class ServerAccount(
    /** The API address that answered, which may be longer than what was typed. */
    val apiUrl: String,
    val login: String,
    val kind: ServerKind = ServerKind.GOOGLE_READER,
)

/**
 * The phone's Nextcloud account, taken from Home (HomeAccount) for Nextcloud News: whose it is,
 * and whether Home is signed out of it just now (then nothing syncs until it is back).
 */
data class HomeUse(
    val server: String,
    val user: String,
    val signedOut: Boolean,
)

data class ServerSyncState(
    /** The account syncing now; null while signed out, Home's account included. */
    val account: ServerAccount? = null,
    /** Set while the account is Home's, or was and Home is signed out of it. */
    val home: HomeUse? = null,
    val lastSync: Instant? = null,
    val problem: ServerProblem? = null,
    val problemCode: Int = 0,
)

/** What a Test or Sign in came to. */
sealed class ServerCheck {
    data class Works(val apiUrl: String, val kind: ServerKind = ServerKind.GOOGLE_READER) : ServerCheck()

    data class Failed(val problem: ServerProblem, val code: Int = 0) : ServerCheck()
}

fun GReaderException.toProblem(): ServerProblem =
    when (this) {
        is GReaderException.Unreachable -> if (cause is NotOnHomeNetwork) ServerProblem.NOT_HOME_NETWORK else ServerProblem.UNREACHABLE
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
 * Sync with a feed server: off until someone signs in, in Settings, with the kind of server
 * (Google Reader API or Nextcloud News), an address, a login name and an API or app password. Then every refresh (pull, button, background) first syncs
 * with that server, and the feeds that are on it are no longer fetched from their sites.
 * Stopping forgets the server and keeps every feed and article where it is.
 *
 * The server is the only place this talks to, and only what was typed is used.
 *
 * Or, for Nextcloud News, the phone's Nextcloud account that Home holds (HomeAccount): then
 * only the choice is kept here (whose account, and the stamp last taken). The password is
 * asked of Home before each sync and on each start, resume and change notice, held in memory
 * only, and let go when Home signs out, is gone or holds another account.
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
        // request headers, and this one carries a login token in every request. A plain http
        // home server is sent nothing unless the phone is on a network that fits (HomeNetwork).
        val gate = HomeNetworkGate { HomeNetwork.now(application) }
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(gate)
            .addNetworkInterceptor(gate)
            .addNetworkInterceptor(ServerUserAgentInterceptor)
            .build()
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<ServerSyncState> = _state

    val isOn: Boolean get() = _state.value.account != null

    /** Home's password while its account is in use: in memory only, never stored or logged. */
    @Volatile
    private var homePassword: String? = null

    /** Each answer from Home is followed one at a time. Taken after syncMutex, never before it. */
    private val homeMutex = Mutex()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun load(): ServerSyncState {
        val url = prefs.getString(KEY_URL, null) ?: return ServerSyncState()
        val login = prefs.getString(KEY_LOGIN, null) ?: return ServerSyncState()
        // Accounts made before there was a choice are Google Reader ones.
        val kind = ServerKind.entries.firstOrNull { it.name == prefs.getString(KEY_KIND, null) } ?: ServerKind.GOOGLE_READER
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 }?.let { Instant.ofEpochSecond(it) }
        val who = homeWho()
        if (who != null) {
            // Home's account: on while a stamp is held, signed out while it is empty.
            val holding = homeStamp().isNotEmpty()
            return ServerSyncState(
                account = if (holding) ServerAccount(url, login, ServerKind.NEXTCLOUD_NEWS) else null,
                home = homeUse(who, signedOut = !holding),
                lastSync = lastSync,
            )
        }
        return ServerSyncState(account = ServerAccount(url, login, kind), lastSync = lastSync)
    }

    private fun homeWho(): String? = prefs.getString(KEY_HOME_WHO, null)?.takeIf { it.isNotEmpty() }

    /** [who] as HomeAccount writes it now (server in lower case, no default port, no slash at the end). */
    private fun sameWho(who: String): String = HomeAccount.who(who.substringBefore('\n'), who.substringAfter('\n'))

    private fun homeStamp(): String = prefs.getString(KEY_HOME_STAMP, null).orEmpty()

    private fun homeUse(
        who: String,
        signedOut: Boolean,
    ) = HomeUse(server = who.substringBefore('\n'), user = who.substringAfter('\n'), signedOut = signedOut)

    /**
     * Switches to Home's Nextcloud account for Nextcloud News: asks Home for it, finds the News
     * API on that server with it, and if that works, drops this app's own sign-in and keeps
     * only whose account it is and its stamp.
     */
    suspend fun useHome(): ServerCheck {
        val held =
            withContext(Dispatchers.IO) { HomeAccount.read(application) }
                ?: return ServerCheck.Failed(ServerProblem.HOME_SIGNED_OUT)
        val apiUrl =
            try {
                withContext(Dispatchers.IO) { NextcloudNewsApi.resolve(client, held.server, held.user, held.password) }
            } catch (e: GReaderException) {
                return ServerCheck.Failed(e.toProblem(), e.code())
            }
        withContext(NonCancellable + Dispatchers.IO) {
            syncMutex.withLock {
                homeMutex.withLock {
                    val before = _state.value.account
                    // The same News account as before keeps its links and its last sync; any
                    // other starts with nothing linked, as a new sign-in does.
                    val same = before != null && before.kind == ServerKind.NEXTCLOUD_NEWS && before.apiUrl == apiUrl && before.login == held.user
                    if (!same) store.forgetServer()
                    val edit =
                        prefs.edit()
                            .putString(KEY_URL, apiUrl)
                            .putString(KEY_LOGIN, held.user)
                            .putString(KEY_KIND, ServerKind.NEXTCLOUD_NEWS.name)
                            .remove(KEY_PASSWORD)
                            .putString(KEY_HOME_WHO, held.who)
                            .putString(KEY_HOME_STAMP, held.stamp)
                    if (!same) edit.remove(KEY_LAST_SYNC).remove(KEY_LAST_FULL)
                    edit.apply()
                    homePassword = held.password
                    _state.value =
                        ServerSyncState(
                            account = ServerAccount(apiUrl, held.user, ServerKind.NEXTCLOUD_NEWS),
                            home = homeUse(held.who, signedOut = false),
                            lastSync = if (same) _state.value.lastSync else null,
                        )
                }
            }
        }
        return ServerCheck.Works(apiUrl, ServerKind.NEXTCLOUD_NEWS)
    }

    /**
     * Asks Home again, when its account is the one chosen, and does what HomeAccount.follow
     * says. Called on each start and resume of the app and on each change notice from Home.
     */
    fun refreshFromHome() {
        if (homeWho() == null) return
        scope.launch {
            if (followHome(holdingSync = false)) {
                // Home now holds another account: its links go, after any sync still running.
                withContext(NonCancellable) { syncMutex.withLock { store.forgetServer() } }
            }
        }
    }

    /**
     * Listens for Home's change notices while the app is in front; returns what stops it.
     * Call in onStart, and the returned stop in onStop.
     */
    fun watchHome(): () -> Unit = HomeAccount.observe(application) { refreshFromHome() }

    /**
     * Asks Home and follows its answer. True when the account was let go for another one and
     * its links are still to be forgotten (by the caller, which may already hold syncMutex).
     */
    private suspend fun followHome(holdingSync: Boolean): Boolean {
        val answer = withContext(Dispatchers.IO) { HomeAccount.ask(application) }
        return homeMutex.withLock {
            val who = homeWho() ?: return@withLock false
            when (val step = HomeAccount.follow(who, homeStamp(), answer)) {
                is HomeAccount.Step.Keep -> {
                    // The same account and stamp: the password may not be in memory yet (a new process).
                    // A who kept by an older copy of HomeAccount is written as it writes it now.
                    (answer as? HomeAccount.Answer.Has)?.held?.takeIf { it.who == sameWho(who) }?.let {
                        homePassword = it.password
                        if (it.who != who) prefs.edit().putString(KEY_HOME_WHO, it.who).apply()
                    }
                    false
                }
                is HomeAccount.Step.Take -> {
                    homePassword = step.held.password
                    prefs.edit().putString(KEY_HOME_WHO, step.held.who).putString(KEY_HOME_STAMP, step.held.stamp).apply()
                    _state.value = load().copy(problem = null, problemCode = 0)
                    false
                }
                is HomeAccount.Step.Drop -> {
                    homePassword = null
                    if (step.following) {
                        // Signed out, or Home is gone: nothing syncs, the choice stays, so the
                        // same account is taken again when Home has it back. What is on the
                        // phone stays, and so do its links to that account.
                        prefs.edit().putString(KEY_HOME_STAMP, "").apply()
                        _state.value = load()
                        false
                    } else {
                        // Home holds another account: forget this one, as Stop using it does.
                        prefs.edit().clear().apply()
                        _state.value = ServerSyncState()
                        if (holdingSync) store.forgetServer()
                        !holdingSync
                    }
                }
            }
        }
    }

    /** Tries the address, login name and password, and says what happened; stores nothing. */
    suspend fun test(
        kind: ServerKind,
        address: String,
        login: String,
        password: String,
    ): ServerCheck =
        withContext(Dispatchers.IO) {
            try {
                val apiUrl =
                    when (kind) {
                        ServerKind.GOOGLE_READER -> GReaderApi.resolve(client, address, login.trim(), password)
                        ServerKind.NEXTCLOUD_NEWS -> NextcloudNewsApi.resolve(client, address, login.trim(), password)
                    }
                ServerCheck.Works(apiUrl, kind)
            } catch (e: GReaderException) {
                ServerCheck.Failed(e.toProblem(), e.code())
            }
        }

    /** [test], and if it works, keeps the account (the password sealed) and turns sync on. */
    suspend fun signIn(
        kind: ServerKind,
        address: String,
        login: String,
        password: String,
    ): ServerCheck {
        val check = test(kind, address, login, password)
        if (check is ServerCheck.Works) {
            val sealed = Secrets.seal(password) ?: return ServerCheck.Failed(ServerProblem.PASSWORD_LOST)
            // A new account starts with no feeds or articles linked: links left from another
            // server (a sync still running when Stop was pressed) would read as feeds that
            // server dropped, or as feeds it already has.
            withContext(Dispatchers.IO) { store.forgetServer() }
            prefs.edit()
                .putString(KEY_URL, check.apiUrl)
                .putString(KEY_LOGIN, login.trim())
                .putString(KEY_PASSWORD, sealed)
                .putString(KEY_KIND, kind.name)
                .remove(KEY_HOME_WHO)
                .remove(KEY_HOME_STAMP)
                .remove(KEY_LAST_SYNC)
                .remove(KEY_LAST_FULL)
                .apply()
            homePassword = null
            _state.value = ServerSyncState(account = ServerAccount(check.apiUrl, login.trim(), kind))
        }
        return check
    }

    /**
     * Stops syncing: forgets the address, login and password (or the choice of Home's account),
     * and which feeds and articles came from the server. Every feed and article stays; the feeds are fetched from their
     * sites again from the next refresh.
     */
    suspend fun stop() {
        prefs.edit().clear().apply()
        homePassword = null
        _state.value = ServerSyncState()
        // After any sync already running, which would otherwise write its links back; and
        // finished even if the screen that asked is closed meanwhile.
        withContext(NonCancellable + Dispatchers.IO) { syncMutex.withLock { store.forgetServer() } }
    }

    /** The feeds whose articles now come from the server; empty when sync is off. */
    suspend fun serverFeedIds(): Set<Long> = if (isOn) dao.serverFeedIds().toSet() else emptySet()

    /**
     * One sync, run inside the app's refresh. Null when it worked or sync is off; otherwise
     * the failure, worded for the pull line under the server's own name.
     */
    suspend fun syncNow(keepReadPerFeed: Int): SyncFailure? {
        // Home's account is asked for before each sync. Signed out or gone, nothing syncs and
        // nothing is said: the screen shows it signed out. This runs inside syncMutex.
        val fromHome = homeWho() != null
        if (fromHome) followHome(holdingSync = true)
        val account = _state.value.account ?: return null
        val host = account.apiUrl.toHttpUrlOrNull()?.host ?: account.apiUrl
        val password =
            if (fromHome) {
                // Null only when Home could not be asked just now and nothing was in memory: try next time.
                homePassword ?: return null
            } else {
                Secrets.open(prefs.getString(KEY_PASSWORD, null))
            }
        if (password == null) {
            _state.value = _state.value.copy(problem = ServerProblem.PASSWORD_LOST, problemCode = 0)
            return SyncFailure(title = host, kind = FailKind.SIGN_IN_REFUSED)
        }
        val started = Instant.now().epochSecond
        val since = (prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 } ?: (started - FIRST_SYNC_SECONDS)) - OVERLAP_SECONDS
        return try {
            val credentials = GReaderCredentials(account.apiUrl, account.login, password)
            // Nextcloud News sends unread articles with their text, so its whole unread list
            // is read once a day; between those, only what changed (NextcloudNewsApi).
            val full = started - prefs.getLong(KEY_LAST_FULL, 0L) >= FULL_LIST_SECONDS
            val api =
                when (account.kind) {
                    ServerKind.GOOGLE_READER -> GReaderApi(client, credentials)
                    ServerKind.NEXTCLOUD_NEWS -> NextcloudNewsApi(client, credentials, fullUnread = full)
                }
            val report = GReaderSync(api, store, keepReadPerFeed = keepReadPerFeed).sync(since)
            logDebug(LOG_TAG, "Synced with the feed server: $report")
            val edit = prefs.edit().putLong(KEY_LAST_SYNC, started)
            if (full) edit.putLong(KEY_LAST_FULL, started)
            edit.apply()
            _state.value = _state.value.copy(lastSync = Instant.ofEpochSecond(started), problem = null, problemCode = 0)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (other: Exception) {
            // Anything else (a connection dropped while an answer was being read, an address
            // the server lists that cannot be parsed) is this sync's failure, named under the
            // server, and the feeds fetched from their own sites still go ahead.
            val e = other as? GReaderException ?: (other as? IOException)?.let { GReaderException.Unreachable(it) }
            Log.w(LOG_TAG, "Feed server sync failed: ${other.javaClass.simpleName}")
            logDebug(LOG_TAG, "Feed server sync failed: ${other.message}")
            _state.value =
                _state.value.copy(problem = e?.toProblem() ?: ServerProblem.SERVER_ERROR, problemCode = e?.code() ?: 0)
            SyncFailure(
                title = host,
                kind =
                    when (e) {
                        is GReaderException.Unreachable ->
                            if (e.cause is NotOnHomeNetwork) FailKind.NOT_HOME_NETWORK else FailKind.NO_CONNECTION
                        is GReaderException.WrongPassword -> FailKind.SIGN_IN_REFUSED
                        is GReaderException.ServerError -> FailKind.SERVER_ANSWERED
                        else -> FailKind.OTHER
                    },
                code = e?.code() ?: 0,
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
        private const val KEY_KIND = "kind"
        private const val KEY_LAST_FULL = "last_full_list"

        /** Home's account chosen: whose it is (HomeAccount.Held.who: server and user, no password). */
        private const val KEY_HOME_WHO = "home_who"

        /** The stamp of Home's account last taken; empty while Home is signed out of it. */
        private const val KEY_HOME_STAMP = "home_stamp"

        /** How often a Nextcloud News sync reads the whole unread list rather than the changes. */
        private const val FULL_LIST_SECONDS = 24L * 3600

        /** How far back the first sync looks for articles already read (unread ones all come). */
        private const val FIRST_SYNC_SECONDS = 14L * 24 * 3600

        /** Each sync looks an hour further back than the last one finished, for late arrivals. */
        private const val OVERLAP_SECONDS = 3600L
    }
}
