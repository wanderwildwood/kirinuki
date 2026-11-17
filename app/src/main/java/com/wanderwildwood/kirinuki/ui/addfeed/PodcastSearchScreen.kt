package com.wanderwildwood.kirinuki.ui.addfeed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.podcast.FoundPodcast
import com.wanderwildwood.kirinuki.ui.PagedColumn
import com.wanderwildwood.kirinuki.ui.compose.components.BarIcon
import com.wanderwildwood.kirinuki.ui.compose.components.DottedRule
import com.wanderwildwood.kirinuki.ui.compose.theme.HouseButton
import com.wanderwildwood.kirinuki.ui.compose.theme.Icons

/** Where a search stands: nothing asked yet, asked and waiting, answered, or not answered. */
sealed interface PodcastSearchState {
    data object Idle : PodcastSearchState

    data class Searching(val term: String) : PodcastSearchState

    data class Found(val term: String, val shows: List<FoundPodcast>) : PodcastSearchState

    data class Failed(val term: String) : PodcastSearchState
}

/**
 * Find a podcast by name. The words go out only when Search is pressed; a tap on a show
 * subscribes to its feed, as if its address had been typed into Add feed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastSearchScreen(
    state: PodcastSearchState,
    onSearch: (String) -> Unit,
    onPick: (FoundPodcast) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var term by rememberSaveable { mutableStateOf((state as? PodcastSearchState.Found)?.term.orEmpty()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val search = {
        if (term.isNotBlank()) {
            keyboard?.hide()
            onSearch(term)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.find_podcast)) },
                navigationIcon = {
                    BarIcon(
                        icon = Icons.Back,
                        contentDescription = stringResource(R.string.go_back),
                        onClick = onClose,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                TextFieldMMD(
                    value = term,
                    onValueChange = { term = it },
                    label = { TextMMD(text = stringResource(R.string.find_podcast_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                HouseButton(
                    label = stringResource(R.string.find_podcast_search),
                    onClick = search,
                    enabled = term.isNotBlank() && state !is PodcastSearchState.Searching,
                    modifier = Modifier.fillMaxWidth(),
                )
                val line =
                    when (state) {
                        PodcastSearchState.Idle -> stringResource(R.string.find_podcast_where)
                        is PodcastSearchState.Searching -> stringResource(R.string.find_podcast_searching)
                        is PodcastSearchState.Failed -> stringResource(R.string.find_podcast_failed)
                        is PodcastSearchState.Found ->
                            if (state.shows.isEmpty()) stringResource(R.string.find_podcast_none, state.term) else null
                    }
                if (line != null) {
                    TextMMD(text = line, style = MaterialTheme.typography.bodySmall)
                }
            }
            val shows = (state as? PodcastSearchState.Found)?.shows.orEmpty()
            if (shows.isNotEmpty()) {
                DottedRule(start = 16.dp)
                PagedColumn(
                    showCutRow = true,
                    modifier = Modifier.weight(1f),
                ) {
                    items(count = shows.size, key = { shows[it].feedUrl }) { index ->
                        val show = shows[index]
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(show) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            TextMMD(
                                text = show.title,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (show.author.isNotBlank()) {
                                TextMMD(text = show.author, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        DottedRule(start = 16.dp)
                    }
                }
            }
        }
    }
}
