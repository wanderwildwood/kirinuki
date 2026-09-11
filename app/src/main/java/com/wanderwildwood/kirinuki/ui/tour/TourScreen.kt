package com.wanderwildwood.kirinuki.ui.tour

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.model.tour.TourEntry
import com.wanderwildwood.kirinuki.ui.compose.components.BarIcon

/**
 * What you meant to read. Things put here while you could not reach them are fetched by
 * the next sync, and are then readable with the radio off like anything else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TourScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: TourViewModel,
    modifier: Modifier = Modifier,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // A sync may have filled some of these in while the screen was away.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.tour)) },
                navigationIcon = {
                    BarIcon(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.go_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            TextMMD(
                text = stringResource(R.string.tour_empty),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
            )
            return@Scaffold
        }

        LazyColumnMMD(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            items(count = entries.size, key = { entries[it].url }) { index ->
                TourRow(
                    entry = entries[index],
                    onOpen = { onOpen(entries[index].url) },
                    onRemove = { viewModel.remove(entries[index].url) },
                )
                HorizontalDividerMMD()
            }
        }
    }
}

@Composable
private fun TourRow(
    entry: TourEntry,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TextMMD(text = entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Says plainly whether this one is actually readable yet.
            if (!entry.isReady) {
                TextMMD(text = stringResource(R.string.tour_waiting))
            }
        }
        BarIcon(
            icon = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.tour_remove),
            onClick = onRemove,
        )
    }
}
