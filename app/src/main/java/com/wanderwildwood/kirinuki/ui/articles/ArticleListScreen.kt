package com.wanderwildwood.kirinuki.ui.articles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.wanderwildwood.kirinuki.archmodel.FeedType
import com.wanderwildwood.kirinuki.db.room.ID_UNSET
import com.wanderwildwood.kirinuki.model.FeedListItem
import com.wanderwildwood.kirinuki.ui.compose.components.BarIcon
import com.wanderwildwood.kirinuki.ui.compose.theme.Icons

/**
 * The cuttings in one feed. Title, where it came from, when -- and nothing else:
 * a snippet only where the title alone would not tell you whether to open it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    onOpenArticle: () -> Unit,
    onBack: () -> Unit,
    viewModel: ArticleListViewModel,
    modifier: Modifier = Modifier,
) {
    val items = viewModel.items.collectAsLazyPagingItems()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle(initialValue = false)
    val screenTitle by viewModel.screenTitle.collectAsStateWithLifecycle(initialValue = null)
    val (openFeedId, _) = viewModel.currentFeedAndTag.collectAsStateWithLifecycle().value
    val listState = rememberLazyListState()

    // One feed open: the bar above the list already says whose these are, and the name
    // under every title is that same word again, once per row. A tag and the two lists
    // that are not a feed keep it, because there it is the thing that tells them apart.
    val showFeedName = openFeedId <= ID_UNSET

    val title =
        when {
            syncing -> stringResource(R.string.syncing)
            screenTitle?.title != null -> screenTitle?.title.orEmpty()
            screenTitle?.type == FeedType.SAVED_ARTICLES -> stringResource(R.string.saved_articles)
            else -> stringResource(R.string.all_feeds)
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarMMD(
                title = {
                    TextMMD(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    BarIcon(
                        icon = Icons.Back,
                        contentDescription = stringResource(R.string.go_back),
                        onClick = onBack,
                    )
                },
                actions = {
                    BarIcon(
                        icon = Icons.DoneAll,
                        contentDescription = stringResource(R.string.mark_all_as_read),
                        onClick = { viewModel.markAllAsRead() },
                    )
                    BarIcon(
                        icon = Icons.Refresh,
                        contentDescription = stringResource(R.string.sync),
                        onClick = { viewModel.refresh() },
                    )
                },
            )
        },
    ) { padding ->
        if (items.itemCount == 0) {
            TextMMD(
                text = stringResource(R.string.nothing_here),
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
            items(
                count = items.itemCount,
                key = { index -> items.peek(index)?.id ?: index },
            ) { index ->
                val item = items[index] ?: return@items
                ArticleRow(
                    item = item,
                    showFeedName = showFeedName,
                    onClick = {
                        viewModel.open(item.id)
                        viewModel.markAsRead(item.id)
                        onOpenArticle()
                    },
                )
                HorizontalDividerMMD()
            }
        }
    }
}

@Composable
private fun ArticleRow(
    item: FeedListItem,
    showFeedName: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        TextMMD(
            text = item.title,
            fontWeight = if (item.unread) FontWeight.Bold else FontWeight.Normal,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showFeedName) {
                TextMMD(
                    text = item.feedTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            TextMMD(text = item.pubDate, maxLines = 1)
        }
    }
}
