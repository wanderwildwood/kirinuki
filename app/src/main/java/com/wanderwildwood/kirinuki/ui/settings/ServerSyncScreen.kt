package com.wanderwildwood.kirinuki.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.sync.ServerCheck
import com.wanderwildwood.kirinuki.sync.ServerKind
import com.wanderwildwood.kirinuki.sync.ServerProblem
import com.wanderwildwood.kirinuki.ui.PagedColumn
import com.wanderwildwood.kirinuki.ui.compose.components.BarIcon
import com.wanderwildwood.kirinuki.ui.compose.theme.HouseButton
import com.wanderwildwood.kirinuki.ui.compose.theme.Icons
import com.wanderwildwood.kirinuki.ui.compose.theme.rememberArmedFlag
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected

/**
 * Sync with a feed server, its own screen because it holds a form. Signed out, it asks which
 * kind of server (Google Reader API or Nextcloud News) and the three things either needs,
 * typed: no browser, nothing to register.
 * Signed in, it says where and as whom, when it last synced, what went wrong if something did,
 * and offers Sync now and Stop syncing.
 *
 * When Home holds the phone's Nextcloud account for this app, a row offers it for Nextcloud
 * News. While it is in use the screen says whose it is, from Home, and Stop using it goes back
 * to this app's own sign-in, empty. When Home signs out, so does this, without an error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSyncScreen(
    onBack: () -> Unit,
    viewModel: ServerSyncViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val check by viewModel.check.collectAsStateWithLifecycle()
    val homeAvailable by viewModel.homeAvailable.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // Chosen above the form, and read by the result line too, so it lives here.
    var kind by rememberSaveable { mutableStateOf(ServerKind.GOOGLE_READER) }
    // What Test or Sign in came to is said at the top, above the fields it is about: the
    // buttons are at the foot of a form taller than the panel, and a line under them was
    // off the screen when it appeared.
    LaunchedEffect(busy, check) {
        if (state.account == null && state.home == null && (busy || check != null)) listState.scrollToItem(if (homeAvailable) 2 else 1)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.server_sync_title)) },
                navigationIcon = {
                    BarIcon(
                        icon = Icons.Back,
                        contentDescription = stringResource(R.string.go_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        PagedColumn(
            state = listState,
            // The keyboard takes the foot of the panel: the list gives it the room, so the field
            // being typed in stays above the keys instead of under them.
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            showCutRow = true,
        ) {
            val account = state.account
            val home = state.home
            if (account == null && home != null) {
                // Home's account was chosen and Home is signed out of it: nothing syncs, and it
                // comes back on its own when Home signs in to the same account again.
                item(key = "home_signed_out") {
                    TextMMD(
                        text = stringResource(R.string.home_account_signed_out, stringResource(R.string.home_account_kept)),
                    )
                }
                item(key = "home_stop") {
                    StopButton(label = R.string.home_account_stop, onStop = viewModel::stop)
                }
            } else if (account == null) {
                // Keyed: the result line comes and goes above the form, and without keys the
                // form would take the result's slot and lose what was typed into it.
                item(key = "intro") { TextMMD(text = stringResource(R.string.server_sync_intro)) }
                if (homeAvailable) {
                    item(key = "home_use") {
                        HomeUseButton(
                            enabled = !busy,
                            onUse = {
                                // Home's account is a Nextcloud: the result line speaks of News.
                                kind = ServerKind.NEXTCLOUD_NEWS
                                viewModel.useHome()
                            },
                        )
                    }
                }
                item(key = "kind") {
                    KindChoice(
                        kind = kind,
                        enabled = !busy,
                        onKind = {
                            kind = it
                            viewModel.clearCheck()
                        },
                    )
                }
                val result = check
                if (busy || result != null) {
                    item(key = "result") {
                        when {
                            busy -> TextMMD(text = stringResource(R.string.server_sync_checking))
                            result is ServerCheck.Works ->
                                TextMMD(text = stringResource(R.string.server_sync_works, displayHost(result.apiUrl)))
                            result is ServerCheck.Failed -> TextMMD(text = problemWords(result.problem, result.code, kind))
                        }
                    }
                }
                item(key = "form") {
                    SignInForm(
                        kind = kind,
                        busy = busy,
                        onEdit = viewModel::clearCheck,
                        onTest = viewModel::test,
                        onSignIn = viewModel::signIn,
                    )
                }
            } else {
                item {
                    Column {
                        TextMMD(text = stringResource(kindName(account.kind)))
                        TextMMD(
                            text =
                                if (home != null) {
                                    stringResource(R.string.home_account_in_use, home.user, shownServer(home.server))
                                } else {
                                    stringResource(
                                        R.string.server_sync_signed_in_as,
                                        displayHost(account.apiUrl),
                                        account.login,
                                    )
                                },
                        )
                        TextMMD(
                            text =
                                state.lastSync?.let {
                                    stringResource(R.string.server_sync_last, lastSyncFormat.format(it.atZone(ZoneId.systemDefault())))
                                } ?: stringResource(R.string.server_sync_never),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                state.problem?.let { problem ->
                    item { TextMMD(text = problemWords(problem, state.problemCode, account.kind)) }
                }
                // Switching from this app's own sign-in to Home's: what the switch came to.
                val result = check
                if (home == null && (busy || result is ServerCheck.Failed)) {
                    item {
                        TextMMD(
                            text =
                                if (result is ServerCheck.Failed && !busy) {
                                    problemWords(result.problem, result.code, ServerKind.NEXTCLOUD_NEWS)
                                } else {
                                    stringResource(R.string.server_sync_checking)
                                },
                        )
                    }
                }
                item {
                    HouseButton(
                        label = stringResource(R.string.server_sync_now),
                        onClick = viewModel::syncNow,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (home == null && homeAvailable) {
                    item { HomeUseButton(enabled = !busy, onUse = viewModel::useHome) }
                }
                if (home != null) {
                    item { StopButton(label = R.string.home_account_stop, onStop = viewModel::stop) }
                } else {
                    item { StopButton(label = R.string.server_sync_stop, onStop = viewModel::stop) }
                }
            }
        }
    }
}

/** The two kinds of server, side by side; the chosen one is drawn filled. */
@Composable
private fun KindChoice(
    kind: ServerKind,
    enabled: Boolean,
    onKind: (ServerKind) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextMMD(text = stringResource(R.string.server_sync_kind), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ServerKind.entries.forEach { k ->
                HouseButton(
                    label = stringResource(kindName(k)),
                    onClick = { onKind(k) },
                    enabled = enabled,
                    on = k == kind,
                    maxLines = 2,
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton; selected = k == kind },
                )
            }
        }
    }
}

private fun kindName(kind: ServerKind): Int =
    when (kind) {
        ServerKind.GOOGLE_READER -> R.string.server_sync_kind_greader
        ServerKind.NEXTCLOUD_NEWS -> R.string.server_sync_kind_nextcloud
    }

@Composable
private fun SignInForm(
    kind: ServerKind,
    busy: Boolean,
    onEdit: () -> Unit,
    onTest: (ServerKind, String, String, String) -> Unit,
    onSignIn: (ServerKind, String, String, String) -> Unit,
) {
    val nextcloud = kind == ServerKind.NEXTCLOUD_NEWS
    var address by rememberSaveable { mutableStateOf("") }
    var login by rememberSaveable { mutableStateOf("") }
    // Held only while the screen is open; never saved with the screen's state.
    var password by androidx.compose.runtime.remember { mutableStateOf("") }
    var shown by rememberSaveable { mutableStateOf(false) }
    val ready = address.isNotBlank() && login.isNotBlank() && password.isNotEmpty() && !busy

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextFieldMMD(
            value = address,
            onValueChange = {
                address = it
                onEdit()
            },
            label = { TextMMD(text = stringResource(R.string.server_sync_address)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        TextFieldMMD(
            value = login,
            onValueChange = {
                login = it
                onEdit()
            },
            label = { TextMMD(text = stringResource(R.string.server_sync_login)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        TextFieldMMD(
            value = password,
            onValueChange = {
                password = it
                onEdit()
            },
            label = { TextMMD(text = stringResource(if (nextcloud) R.string.server_sync_app_password else R.string.server_sync_password)) },
            singleLine = true,
            visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            // Done on the keyboard is Sign in, once everything it needs is typed.
            keyboardActions = KeyboardActions(onDone = { if (ready) onSignIn(kind, address, login, password) }),
            trailingIcon = {
                HouseButton(
                    label = stringResource(if (shown) R.string.server_sync_hide else R.string.server_sync_show),
                    onClick = { shown = !shown },
                    compact = true,
                    modifier = Modifier.padding(end = 4.dp),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        TextMMD(
            text = stringResource(if (nextcloud) R.string.server_sync_nextcloud_note else R.string.server_sync_password_note),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            HouseButton(
                label = stringResource(R.string.server_sync_test),
                onClick = { onTest(kind, address, login, password) },
                enabled = ready,
                modifier = Modifier.weight(1f),
            )
            HouseButton(
                label = stringResource(R.string.server_sync_sign_in),
                onClick = { onSignIn(kind, address, login, password) },
                enabled = ready,
                on = true,
                modifier = Modifier.weight(1f),
            )
        }
        TextMMD(
            text = stringResource(R.string.server_sync_first_time),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** "Use the phone's Nextcloud account": shown only while Home has one for this app. */
@Composable
private fun HomeUseButton(
    enabled: Boolean,
    onUse: () -> Unit,
) {
    HouseButton(
        label = stringResource(R.string.home_account_use),
        onClick = onUse,
        enabled = enabled,
        maxLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The armed-row pattern: the first press only says what the second will do; it stays armed
 * until the next other touch, Back or leaving the screen.
 */
@Composable
private fun StopButton(
    label: Int,
    onStop: () -> Unit,
) {
    var armed by rememberArmedFlag()
    HouseButton(
        label = stringResource(if (armed) R.string.server_sync_stop_armed else label),
        onClick = {
            if (armed) {
                armed = false
                onStop()
            } else {
                armed = true
            }
        },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun problemWords(
    problem: ServerProblem,
    code: Int,
    kind: ServerKind,
): String =
    when (problem) {
        ServerProblem.UNREACHABLE -> stringResource(R.string.server_sync_unreachable)
        ServerProblem.WRONG_PASSWORD ->
            stringResource(
                if (kind == ServerKind.NEXTCLOUD_NEWS) R.string.server_sync_wrong_app_password else R.string.server_sync_wrong_password,
            )
        ServerProblem.NOT_GOOGLE_READER ->
            stringResource(
                if (kind == ServerKind.NEXTCLOUD_NEWS) R.string.server_sync_not_nextcloud_news else R.string.server_sync_not_greader,
            )
        ServerProblem.INSECURE -> stringResource(R.string.server_sync_insecure)
        ServerProblem.SERVER_ERROR -> stringResource(R.string.server_sync_server_error, code)
        ServerProblem.PASSWORD_LOST -> stringResource(R.string.server_sync_password_lost)
        ServerProblem.NOT_HOME_NETWORK -> stringResource(R.string.server_sync_not_home_network)
        ServerProblem.HOME_SIGNED_OUT ->
            stringResource(R.string.home_account_signed_out, stringResource(R.string.home_account_kept))
    }

/** The server's host, with its port when it is not the usual one for the scheme. */
internal fun displayHost(url: String): String {
    val parsed = url.toHttpUrlOrNull() ?: return url
    val usual = okhttp3.HttpUrl.defaultPort(parsed.scheme)
    return if (parsed.port == usual) parsed.host else "${parsed.host}:${parsed.port}"
}

/**
 * Home's server as a person reads it, the same way as this app's own server ([displayHost]):
 * without http:// or https://, with its port only when it is not the usual one.
 */
internal fun shownServer(server: String): String =
    server.trim().trimEnd('/').let { if (it.toHttpUrlOrNull() != null) displayHost(it) else it.substringAfter("://") }

private val lastSyncFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

