package com.wanderwildwood.kirinuki.ui.articles

import androidx.paging.PagingData
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import com.wanderwildwood.kirinuki.background.runOnceRssSync
import com.wanderwildwood.kirinuki.model.FeedListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance

class ArticleListViewModel(
    di: DI,
) : DIAwareViewModel(di) {
    private val repository: Repository by instance()

    val items: Flow<PagingData<FeedListItem>> =
        repository.getCurrentFeedListItems().cachedIn(viewModelScope)

    val screenTitle = repository.getScreenTitleForCurrentFeedOrTag()

    val syncing: Flow<Boolean> = repository.currentlySyncing

    val currentFeedAndTag: StateFlow<Pair<Long, String>> = repository.currentFeedAndTag

    fun open(itemId: Long) = repository.setCurrentArticle(itemId)

    fun markAsRead(itemId: Long) {
        viewModelScope.launch {
            repository.markAsReadAndNotified(itemId = itemId)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            val (feedId, tag) = repository.currentFeedAndTag.value
            repository.markAllAsReadInFeedOrTag(feedId = feedId, tag = tag)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val (feedId, tag) = repository.currentFeedAndTag.value
            runOnceRssSync(
                di = di,
                feedId = feedId,
                feedTag = tag,
                forceNetwork = true,
                triggeredByUser = true,
            )
        }
    }
}
