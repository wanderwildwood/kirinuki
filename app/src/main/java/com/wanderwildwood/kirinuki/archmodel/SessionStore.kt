package com.wanderwildwood.kirinuki.archmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

class SessionStore {
    private val _resumeTime = MutableStateFlow(Instant.EPOCH)

    /**
     * Observe this value in compose to get actions to happen when the
     * activity returns to the foreground
     */
    val resumeTime: StateFlow<Instant> = _resumeTime.asStateFlow()

    fun setResumeTime(instant: Instant) {
        _resumeTime.update {
            instant
        }
    }

    private val _expandedTags = MutableStateFlow(emptySet<String>())
    val expandedTags: StateFlow<Set<String>> = _expandedTags.asStateFlow()

    fun toggleTagExpansion(tag: String) {
        _expandedTags.update {
            if (tag in expandedTags.value) {
                _expandedTags.value - tag
            } else {
                _expandedTags.value + tag
            }
        }
    }

    private val _syncWorkerRunning = MutableStateFlow(false)
    val syncWorkerRunning: StateFlow<Boolean> = _syncWorkerRunning.asStateFlow()

    fun setSyncWorkerRunning(running: Boolean) {
        _syncWorkerRunning.update {
            running
        }
    }

    private val _syncOutcome = MutableStateFlow<SyncOutcome?>(null)

    /** How the last sync of the feeds went; a new object each time, so a pull can tell its own. */
    val syncOutcome: StateFlow<SyncOutcome?> = _syncOutcome.asStateFlow()

    fun setSyncOutcome(outcome: SyncOutcome) {
        _syncOutcome.value = outcome
    }
}

/** What a sync of the feeds did: [tried] feeds asked for, [failed] of them not got, [ok] if it ran through. */
class SyncOutcome(
    val tried: Int,
    val failed: Int,
    val ok: Boolean = true,
    /** Titles of the feeds that were not got, so a pull can name them. */
    val failedTitles: List<String> = emptyList(),
    /** Why each of those was not got, so a line can say more than "could not fetch". */
    val failures: List<SyncFailure> = emptyList(),
)

/** Why a feed was not got, in the few ways a reader can act on. */
enum class FailKind { NO_CONNECTION, SERVER_ANSWERED, NOT_A_FEED, OTHER }

/** One feed that was not got: its name, why, and the server's code when it answered with one. */
data class SyncFailure(val title: String, val kind: FailKind, val code: Int = 0)
