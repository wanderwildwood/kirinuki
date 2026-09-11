package com.wanderwildwood.kirinuki.ui.addfeed

import com.wanderwildwood.kirinuki.archmodel.Repository
import androidx.lifecycle.viewModelScope
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import com.wanderwildwood.kirinuki.background.runOnceRssSync
import com.wanderwildwood.kirinuki.db.room.Feed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import java.net.MalformedURLException
import java.net.URL

class AddFeedViewModel(
    di: DI,
) : DIAwareViewModel(di) {
    private val repository: Repository by instance()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    /**
     * A bare hostname is what people actually type, so it is treated as one rather
     * than rejected: https is assumed, and the feed says what its own title is.
     */
    fun save(
        url: String,
        title: String,
    ) {
        val parsed =
            try {
                URL(if (url.contains("://")) url else "https://$url")
            } catch (e: MalformedURLException) {
                _error.value = url
                return
            }

        viewModelScope.launch {
            val existing = repository.getFeed(parsed)
            val feedId =
                repository.saveFeed(
                    existing?.copy(title = title.ifBlank { existing.title })
                        ?: Feed(url = parsed, title = title, customTitle = title),
                )
            runOnceRssSync(di = di, feedId = feedId, forceNetwork = true, triggeredByUser = true)
            _saved.value = true
        }
    }
}
