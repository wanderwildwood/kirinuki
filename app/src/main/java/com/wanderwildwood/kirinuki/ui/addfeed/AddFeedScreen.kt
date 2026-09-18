package com.wanderwildwood.kirinuki.ui.addfeed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.db.room.ID_UNSET
import com.wanderwildwood.kirinuki.net.isSmolnetUrl
import com.wanderwildwood.kirinuki.ui.compose.components.BarIcon
import com.wanderwildwood.kirinuki.ui.compose.theme.Icons
import kotlinx.coroutines.delay

/**
 * One screen for subscribing and for changing a feed afterwards, because the two hold the
 * same three things. Given a feed it arrives filled in and saves; given nothing it is
 * blank and subscribes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddFeedScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onRead: (String) -> Unit,
    viewModel: AddFeedViewModel,
    modifier: Modifier = Modifier,
    initialUrl: String = "",
    feedId: Long = ID_UNSET,
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var title by rememberSaveable { mutableStateOf("") }
    var folder by rememberSaveable { mutableStateOf("") }
    var fullText by rememberSaveable { mutableStateOf(false) }
    // The feed arrives after the first composition, and it must fill the boxes once and
    // then leave them alone: without this, a second emission would undo what was typed.
    var filledIn by rememberSaveable { mutableStateOf(false) }
    val error by viewModel.error.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle(initialValue = emptyList())
    val editing = feedId > ID_UNSET

    // Disarms itself after a few seconds, the same as the row it replaces used to.
    var armed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(ARMED_MILLIS)
            armed = false
        }
    }

    LaunchedEffect(feedId) { viewModel.load(feedId) }

    LaunchedEffect(feed) {
        val loaded = feed
        // The id has to match: the view model is reused, so the feed left over from the
        // last visit is what is in hand until this one's read comes back.
        if (loaded == null || loaded.id != feedId || filledIn) return@LaunchedEffect
        url = loaded.url.toString()
        // The name it goes by, which is a custom one only if it has been given one. An
        // empty box would be a feed that looks nameless, and there is nowhere else on this
        // screen to read the name off. Saving it back unchanged stores nothing: the view
        // model drops a custom title that only repeats what the feed calls itself.
        title = loaded.displayTitle
        folder = loaded.tag
        fullText = loaded.fullTextByDefault
        filledIn = true
    }

    LaunchedEffect(saved) {
        if (saved) {
            viewModel.savedHandled()
            onSaved()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarMMD(
                title = {
                    TextMMD(
                        text =
                            stringResource(
                                if (editing) R.string.edit_feed else R.string.add_feed,
                            ),
                    )
                },
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
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
        ) {
            TextFieldMMD(
                value = url,
                onValueChange = { url = it },
                label = { TextMMD(text = stringResource(R.string.add_feed_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextFieldMMD(
                value = title,
                onValueChange = { title = it },
                label = { TextMMD(text = stringResource(R.string.title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextFieldMMD(
                value = folder,
                onValueChange = { folder = it },
                label = { TextMMD(text = stringResource(R.string.feed_folder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // A folder is only a word the feeds agree on, so a folder typed a second time
            // slightly differently is a second folder. The ones already in use are here to
            // be tapped, which is also the only place in the app they can be read off.
            val existingFolders = folders.filter { it.isNotBlank() }
            if (existingFolders.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    existingFolders.forEach { existing ->
                        TextMMD(
                            text = existing,
                            modifier = Modifier.clickable { folder = existing },
                        )
                    }
                }
            }
            // The answer to a feed that carries only a paragraph. Before this it existed
            // and was reachable only by hand-editing an OPML file and importing it, which
            // is not a setting anybody can find.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { fullText = !fullText },
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    TextMMD(text = stringResource(R.string.full_text_by_default))
                    TextMMD(
                        text = stringResource(R.string.full_text_by_default_detail),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                SwitchMMD(checked = fullText, onCheckedChange = { fullText = it })
            }
            if (error != null) {
                TextMMD(text = stringResource(R.string.add_feed_url_invalid))
            }
            ButtonMMD(
                onClick =
                    {
                        viewModel.save(
                            url = url,
                            title = title,
                            folder = folder,
                            feedId = feedId,
                            fullTextByDefault = fullText,
                        )
                    },
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextMMD(
                    text =
                        stringResource(
                            if (editing) R.string.save else R.string.subscribe,
                        ),
                )
            }

            // A capsule address is as likely to be a page as a gemlog, and subscribing to
            // a page gets you a feed that can never have an entry in it. Offer the other
            // thing here rather than making that the only way in.
            if (isSmolnetUrl(url.trim())) {
                OutlinedButtonMMD(
                    onClick = { onRead(url.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextMMD(text = stringResource(R.string.read_it))
                }
            }

            // Last, after everything that only adjusts -- see STYLE.md. Unsubscribing cannot
            // be undone, so the button asks in its own face rather than stacking a dialog on
            // a screen, and it disarms itself, because a live delete left under a thumb is a
            // trap for whoever picks the phone up next.
            if (editing) {
                OutlinedButtonMMD(
                    onClick = { if (armed) viewModel.delete(feedId) else armed = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextMMD(
                        text =
                            stringResource(
                                if (armed) R.string.remove_feed_armed else R.string.remove_feed,
                            ),
                    )
                }
            }
        }
    }
}

/** Long enough to mean it, short enough not to leave a live delete under a thumb. */
private const val ARMED_MILLIS = 4000L
