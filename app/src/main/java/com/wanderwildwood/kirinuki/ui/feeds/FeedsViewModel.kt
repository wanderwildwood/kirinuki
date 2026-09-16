package com.wanderwildwood.kirinuki.ui.feeds

import androidx.paging.PagingData
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import com.wanderwildwood.kirinuki.background.runOnceRssSync
import com.wanderwildwood.kirinuki.model.FeedUnreadCount
import com.wanderwildwood.kirinuki.model.tour.TourStore
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
    private val tourStore: TourStore by instance()

    private val _tourCount = MutableStateFlow(tourStore.count())
    val tourCount: StateFlow<Int> = _tourCount

    val items: Flow<PagingData<FeedUnreadCount>> =
        repository.getPagedNavDrawerItems().cachedIn(viewModelScope)

    val syncing: Flow<Boolean> = repository.currentlySyncing

    val expandedTags: StateFlow<Set<String>> = repository.expandedTags

    fun toggleTagExpansion(tag: String) = repository.toggleTagExpansion(tag)

    fun open(
        feedId: Long,
        tag: String,
    ) = repository.setCurrentFeedAndTag(feedId = feedId, tag = tag)

    /** The count changes behind this screen's back, when a sync fills the tour in. */
    fun refreshTourCount() {
        _tourCount.value = tourStore.count()
    }

    /**
     * Unsubscribe. The articles go with it -- a feed you have removed keeping its unread
     * count in "All feeds" would be the app disagreeing with the reader about what they
     * are subscribed to.
     */
    fun remove(feedId: Long) {
        viewModelScope.launch {
            repository.deleteFeeds(listOf(feedId))
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runOnceRssSync(di = di, forceNetwork = true, triggeredByUser = true)
        }
    }
}
