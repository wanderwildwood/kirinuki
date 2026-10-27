package com.wanderwildwood.kirinuki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.kirinuki.R
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
    /** Some did, some did not. */
    data class SomeFailed(val titles: List<String>) : Pulled
}

/** What a finished sync, which left [outcome] behind (null: nothing did), has to say to the one who pulled. */
fun pulledAfter(outcome: SyncOutcome?): Pulled = when {
    outcome == null -> Pulled.NotStarted
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

    /** Runs the same sync as the refresh button, and reports it. A second pull meanwhile is the same pull. */
    fun pull() {
        if (job?.isActive == true) return
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
            pulled = pulledAfter(done)
            delay(PULLED_LINE_MS)
            pulled = null
        }
    }
}

@Composable
fun rememberPull(refresh: () -> Unit, outcome: StateFlow<SyncOutcome?>, running: StateFlow<Boolean>): PullState {
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(refresh)
    return remember { PullState(scope, { latest.value() }, outcome, running) }
}

/** [pulled] in words. */
@Composable
fun pulledWords(pulled: Pulled?): String? = when (pulled) {
    null -> null
    Pulled.Syncing -> stringResource(R.string.pull_syncing)
    Pulled.UpToDate -> stringResource(R.string.pull_up_to_date)
    Pulled.NotStarted -> stringResource(R.string.pull_not_started)
    Pulled.Unreachable -> stringResource(R.string.pull_unreachable)
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
