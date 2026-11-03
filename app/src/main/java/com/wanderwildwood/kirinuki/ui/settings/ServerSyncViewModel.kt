package com.wanderwildwood.kirinuki.ui.settings

import androidx.lifecycle.viewModelScope
import com.wanderwildwood.kirinuki.background.runOnceRssSync
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import com.wanderwildwood.kirinuki.sync.ServerCheck
import com.wanderwildwood.kirinuki.sync.ServerKind
import com.wanderwildwood.kirinuki.sync.ServerSync
import com.wanderwildwood.kirinuki.sync.ServerSyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance

class ServerSyncViewModel(
    di: DI,
) : DIAwareViewModel(di) {
    private val serverSync: ServerSync by instance()

    val state: StateFlow<ServerSyncState> = serverSync.state

    private val _busy = MutableStateFlow(false)

    /** A Test or Sign in is under way; the buttons wait. */
    val busy: StateFlow<Boolean> = _busy

    private val _check = MutableStateFlow<ServerCheck?>(null)

    /** What the last Test or Sign in came to, until something is typed again. */
    val check: StateFlow<ServerCheck?> = _check

    fun clearCheck() {
        _check.value = null
    }

    fun test(
        kind: ServerKind,
        address: String,
        login: String,
        password: String,
    ) = run { serverSync.test(kind, address, login, password) }

    fun signIn(
        kind: ServerKind,
        address: String,
        login: String,
        password: String,
    ) = run {
        serverSync.signIn(kind, address, login, password).also {
            // The first sync starts straight away, the same as pulling the feed list.
            if (it is ServerCheck.Works) runOnceRssSync(di = di, forceNetwork = true, triggeredByUser = true)
        }
    }

    fun syncNow() {
        viewModelScope.launch { runOnceRssSync(di = di, forceNetwork = true, triggeredByUser = true) }
    }

    fun stop() {
        viewModelScope.launch {
            serverSync.stop()
            _check.value = null
        }
    }

    private fun run(block: suspend () -> ServerCheck) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                _check.value = block()
            } finally {
                _busy.value = false
            }
        }
    }
}
