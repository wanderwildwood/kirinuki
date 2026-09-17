package com.wanderwildwood.kirinuki.ui.feeds

import androidx.paging.PagingData
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import com.wanderwildwood.kirinuki.background.runOnceRssSync
import com.wanderwildwood.kirinuki.model.FeedUnreadCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance

class FeedsViewModel(
    di: DI,
) : DIAwareViewModel(di) {
    private val repository: Repository by instance()
    val items: Flow<PagingData<FeedUnreadCount>> =
        repository.getPagedNavDrawerItems().cachedIn(viewModelScope)

    val syncing: Flow<Boolean> = repository.currentlySyncing

    val expandedTags: StateFlow<Set<String>> = repository.expandedTags

    fun toggleTagExpansion(tag: String) = repository.toggleTagExpansion(tag)

    fun open(
        feedId: Long,
        tag: String,
    ) = repository.setCurrentFeedAndTag(feedId = feedId, tag = tag)

    fun refresh() {
        viewModelScope.launch {
            runOnceRssSync(di = di, forceNetwork = true, triggeredByUser = true)
        }
    }
}
