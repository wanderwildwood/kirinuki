package com.wanderwildwood.kirinuki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.archmodel.FailKind
import com.wanderwildwood.kirinuki.archmodel.SyncFailure
import com.wanderwildwood.kirinuki.archmodel.SyncOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the line above a list says once a swipe down at its top has fetched the feeds again:
 * "Syncing…" while it runs, then "Up to date" or what went wrong, for a few seconds.
 */
sealed interface Pulled {
    data object Syncing : Pulled
    data object UpToDate : Pulled
    /** The sync never got going: no network to wait for, or nothing came of it. */
    data object NotStarted : Pulled
    /** Every feed asked for failed. */
    data object Unreachable : Pulled
    /** Exactly one feed failed, and the reason is known. */
    data class OneFailed(val failure: SyncFailure) : Pulled
    /** Some did, some did not. */
    data class SomeFailed(val titles: List<String>) : Pulled
}

/** What a finished sync, which left [outcome] behind (null: nothing did), has to say to the one who pulled. */
fun pulledAfter(outcome: SyncOutcome?): Pulled = when {
    outcome == null -> Pulled.NotStarted
    outcome.ok && outcome.failures.size == 1 && outcome.failed == 1 -> Pulled.OneFailed(outcome.failures.single())
    !outcome.ok -> Pulled.Unreachable
    outcome.tried > 0 && outcome.failed >= outcome.tried -> Pulled.Unreachable
    outcome.failed > 0 -> Pulled.SomeFailed(outcome.failedTitles)
    else -> Pulled.UpToDate
}

/** How long "Up to date" or the trouble stays above the list after a pull, in milliseconds. */
const val PULLED_LINE_MS = 6_000L

/** How long a pull waits for the sync to begin, and then to finish, before it gives up on it. */
/** How many failed feeds the line names before it says "and N more". */
private const val PULLED_NAMES_SHOWN = 3

const val PULL_START_MS = 10_000L
const val PULL_FINISH_MS = 120_000L

class PullState internal constructor(
    private val scope: CoroutineScope,
    private val refresh: () -> Unit,
    private val outcome: StateFlow<SyncOutcome?>,
    private val running: StateFlow<Boolean>,
) {
    var pulled by mutableStateOf<Pulled?>(null)
        private set
    private var job: Job? = null

    /**
     * A sync that nobody pulled for -- the refresh button, a feed just added, the schedule --
     * says how it went the same way, once it has finished. Each outcome is said once:
     * wherever the screen was when it arrived.
     */
    fun finished(done: SyncOutcome?) {
        if (done == null || done === lastReported || job?.isActive == true) return
        lastReported = done
        reported?.cancel()
        reported = scope.launch {
            pulled = pulledAfter(done)
            delay(PULLED_LINE_MS)
            pulled = null
        }
    }

    private var reported: Job? = null

    /** Runs the same sync as the refresh button, and reports it. A second pull meanwhile is the same pull. */
    fun pull() {
        if (job?.isActive == true) return
        reported?.cancel()
        job = scope.launch {
            pulled = Pulled.Syncing
            val before = outcome.value
            refresh()
            val started = withTimeoutOrNull(PULL_START_MS) {
                combine(running, outcome) { r, o -> r || o !== before }.first { it }
            }
            val done = if (started == null) null else withTimeoutOrNull(PULL_FINISH_MS) {
                outcome.first { it !== before }
            }
            lastReported = done
            pulled = pulledAfter(done)
            delay(PULLED_LINE_MS)
            pulled = null
        }
    }
}

/** The last sync outcome a line was shown for, so each one is said once. */
private var lastReported: SyncOutcome? = null

@Composable
fun rememberPull(refresh: () -> Unit, outcome: StateFlow<SyncOutcome?>, running: StateFlow<Boolean>): PullState {
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(refresh)
    val state = remember { PullState(scope, { latest.value() }, outcome, running) }
    val done by outcome.collectAsState()
    LaunchedEffect(done) { state.finished(done) }
    return state
}

/** [pulled] in words. */
@Composable
fun pulledWords(pulled: Pulled?): String? = when (pulled) {
    null -> null
    Pulled.Syncing -> stringResource(R.string.pull_syncing)
    Pulled.UpToDate -> stringResource(R.string.pull_up_to_date)
    Pulled.NotStarted -> stringResource(R.string.pull_not_started)
    Pulled.Unreachable -> stringResource(R.string.pull_unreachable)
    is Pulled.OneFailed -> pulled.failure.let { f ->
        when (f.kind) {
            FailKind.NO_CONNECTION -> stringResource(R.string.pull_one_no_connection, f.title)
            FailKind.SERVER_ANSWERED -> stringResource(R.string.pull_one_server_answered, f.title, f.code)
            FailKind.NOT_A_FEED -> stringResource(R.string.pull_one_not_a_feed, f.title)
            FailKind.OTHER -> stringResource(R.string.pull_failed_named, f.title)
        }
    }
    is Pulled.SomeFailed -> {
        val shown = pulled.titles.take(PULLED_NAMES_SHOWN)
        val more = pulled.titles.size - shown.size
        when {
            shown.isEmpty() -> stringResource(R.string.pull_some_failed)
            more > 0 -> stringResource(R.string.pull_failed_named_more, shown.joinToString(", "), more)
            else -> stringResource(R.string.pull_failed_named, shown.joinToString(", "))
        }
    }
}
