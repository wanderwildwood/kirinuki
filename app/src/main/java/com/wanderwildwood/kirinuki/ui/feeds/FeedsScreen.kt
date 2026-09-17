package com.wanderwildwood.kirinuki.ui.feeds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.db.room.ID_ALL_FEEDS
import com.wanderwildwood.kirinuki.db.room.ID_SAVED_ARTICLES
import com.wanderwildwood.kirinuki.db.room.ID_UNSET
import com.wanderwildwood.kirinuki.model.FeedUnreadCount
import com.wanderwildwood.kirinuki.ui.compose.components.BarIcon
import com.wanderwildwood.kirinuki.ui.compose.theme.Icons
import kotlinx.coroutines.delay

/**
 * The feeds, and above them the two rows that are not feeds: everything, and what was kept.
 *
 * A tag holding feeds is a row that opens; tapping the tag itself reads the whole tag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedsScreen(
    onOpenFeed: () -> Unit,
    onAddFeed: () -> Unit,
    onSettings: () -> Unit,
    onTour: () -> Unit,
    viewModel: FeedsViewModel,
    modifier: Modifier = Modifier,
) {
    val items = viewModel.items.collectAsLazyPagingItems()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle(initialValue = false)
    val expandedTags by viewModel.expandedTags.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val tourCount by viewModel.tourCount.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarMMD(
                title = {
                    TextMMD(
                        text =
                            when {
                                syncing -> stringResource(R.string.syncing)
                                else -> stringResource(R.string.app_name)
                            },
                    )
                },
                actions = {
                    // Ahead of the two that were already here, so nothing moved out from
                    // under a thumb that had learnt where it was.
                    BarIcon(
                        icon = Icons.Refresh,
                        contentDescription = stringResource(R.string.sync),
                        onClick = { viewModel.refresh() },
                    )
                    BarIcon(
                        icon = Icons.Plus,
                        contentDescription = stringResource(R.string.add_feed),
                        onClick = onAddFeed,
                    )
                    BarIcon(
                        icon = Icons.Settings,
                        contentDescription = stringResource(R.string.action_settings),
                        onClick = onSettings,
                    )
                },
            )
        },
    ) { padding ->
        if (items.itemCount == 0) {
            TextMMD(
                text = stringResource(R.string.no_feeds_yet),
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
            // First, because it is what you came back for: the tour is the only row here
            // holding things you asked for by hand rather than subscribed to.
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onTour)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    TextMMD(
                        text = stringResource(R.string.tour),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (tourCount > 0) TextMMD(text = tourCount.toString())
                }
                HorizontalDividerMMD()
            }

            items(
                count = items.itemCount,
                key = { index -> items.peek(index)?.let { "${it.id}/${it.tag}" } ?: index },
            ) { index ->
                val item = items[index] ?: return@items
                FeedRow(
                    item = item,
                    expanded = item.tag in expandedTags,
                    onClick = {
                        when {
                            item.id == ID_UNSET && item.tag.isNotEmpty() -> {
                                viewModel.open(feedId = ID_UNSET, tag = item.tag)
                                onOpenFeed()
                            }
                            else -> {
                                viewModel.open(feedId = item.id, tag = item.tag)
                                onOpenFeed()
                            }
                        }
                    },
                    onToggleTag = { viewModel.toggleTagExpansion(item.tag) },
                    // "All feeds", "Saved articles" and a tag are not subscriptions, so
                    // there is nothing to unsubscribe from and no icon on those rows.
                    onRemove =
                        when {
                            item.id == ID_ALL_FEEDS -> null
                            item.id == ID_SAVED_ARTICLES -> null
                            item.id == ID_UNSET -> null
                            else -> ({ viewModel.remove(item.id) })
                        },
                )
                HorizontalDividerMMD()
            }
        }
    }
}

@Composable
private fun FeedRow(
    item: FeedUnreadCount,
    expanded: Boolean,
    onClick: () -> Unit,
    onToggleTag: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Removing a feed cannot be undone, so it asks in the row rather than in a dialog --
    // see STYLE.md. It disarms itself, because a row left armed under a thumb is a trap.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(ARMED_MILLIS)
            armed = false
        }
    }
    val isTag = item.id == ID_UNSET && item.tag.isNotEmpty()
    val title =
        when (item.id) {
            ID_ALL_FEEDS -> stringResource(R.string.all_feeds)
            ID_SAVED_ARTICLES -> stringResource(R.string.saved_articles)
            else -> item.displayTitle
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { if (armed) armed = false else onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (isTag) {
            Box(
                modifier = Modifier.clickable(onClick = onToggleTag),
            ) {
                TextMMD(text = if (expanded) "−" else "+")
            }
        }
        TextMMD(
            text = if (armed) stringResource(R.string.remove_feed_armed) else title,
            // Tour, All feeds and Saved articles are not feeds. They sat in the same
            // column drawn the same way, so they read as subscriptions. Weight separates
            // the kinds without a second type size or a row of its own.
            fontWeight = if (onRemove == null && !isTag) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.unreadCount > 0 && !armed) {
            TextMMD(text = item.unreadCount.toString())
        }
        if (onRemove != null) {
            BarIcon(
                icon = Icons.Delete,
                contentDescription =
                    stringResource(
                        if (armed) R.string.remove_feed_armed else R.string.remove_feed,
                    ),
                onClick = { if (armed) onRemove() else armed = true },
            )
        }
    }
}

/** Long enough to mean it, short enough not to leave a live delete under a thumb. */
private const val ARMED_MILLIS = 4000L
