package com.wanderwildwood.kirinuki.ui.compose.editfeed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.wanderwildwood.kirinuki.archmodel.PREF_VAL_OPEN_WITH_BROWSER
import com.wanderwildwood.kirinuki.archmodel.PREF_VAL_OPEN_WITH_CUSTOM_TAB
import com.wanderwildwood.kirinuki.archmodel.PREF_VAL_OPEN_WITH_READER
import com.wanderwildwood.kirinuki.archmodel.PREF_VAL_OPEN_WITH_WEBVIEW
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.background.runOnceRssSync
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import com.wanderwildwood.kirinuki.db.room.Feed
import com.wanderwildwood.kirinuki.ui.compose.utils.mutableSavedStateOf
import com.wanderwildwood.kirinuki.util.sloppyLinkToStrictURLOrNull
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import java.net.URL
import java.time.Instant

class CreateFeedScreenViewModel(
    di: DI,
    state: SavedStateHandle,
) : DIAwareViewModel(di),
    EditFeedScreenState {
    private val repository: Repository by instance()

    // These three are updated as a result of url/uri updating
    override var isNotValidUrl by mutableStateOf(false)
    override var isOkToSave: Boolean by mutableStateOf(false)

    override var feedUrl: String by mutableSavedStateOf(state, "") { value ->
        isNotValidUrl = !isValidUrlOrUri(value)
        isOkToSave = isValidUrlOrUri(value)
    }
    override var feedTitle: String by mutableSavedStateOf(state, "")
    override var feedTag: String by mutableSavedStateOf(state, "")
    override var fullTextByDefault: Boolean by mutableSavedStateOf(state, false)
    override var skipDuplicates: Boolean by mutableSavedStateOf(state, false)
    override var notify: Boolean by mutableSavedStateOf(state, false)
    override var articleOpener: String by mutableSavedStateOf(state, "")
    override var alternateId: Boolean by mutableSavedStateOf(state, false)
    override var summarizeOnOpen: Boolean by mutableSavedStateOf(state, false)
    override var fetchOgImages: Boolean by mutableSavedStateOf(state, false)
    override var allTags: List<String> by mutableStateOf(emptyList())
    override var defaultTitle: String by mutableStateOf(state["feedTitle"] ?: "")
    override var feedImage: String by mutableStateOf(state["feedImage"] ?: "")

    override val isOpenItemWithBrowser: Boolean
        get() = articleOpener == PREF_VAL_OPEN_WITH_BROWSER

    override val isOpenItemWithCustomTab: Boolean
        get() = articleOpener == PREF_VAL_OPEN_WITH_CUSTOM_TAB

    override val isOpenItemWithReader: Boolean
        get() = articleOpener == PREF_VAL_OPEN_WITH_READER

    override val isOpenItemWithAppDefault: Boolean
        get() =
            when (articleOpener) {
                PREF_VAL_OPEN_WITH_READER,
                PREF_VAL_OPEN_WITH_WEBVIEW,
                PREF_VAL_OPEN_WITH_BROWSER,
                PREF_VAL_OPEN_WITH_CUSTOM_TAB,
                -> false

                else -> true
            }

    init {
        viewModelScope.launch {
            repository.allTags
                .collect { value ->
                    allTags = value
                }
        }
    }

    fun saveAndRequestSync(action: (Long) -> Unit) =
        viewModelScope.launch {
            val feedId =
                repository.saveFeed(
                    Feed(
                        url = URL(feedUrl),
                        title = feedTitle,
                        customTitle = feedTitle,
                        tag = feedTag,
                        fullTextByDefault = fullTextByDefault,
                        notify = notify,
                        skipDuplicates = skipDuplicates,
                        openArticlesWith = articleOpener,
                        alternateId = alternateId,
                        whenModified = Instant.now(),
                        imageUrl = sloppyLinkToStrictURLOrNull(feedImage),
                        fetchOgImages = fetchOgImages,
                    ),
                )

            runOnceRssSync(
                di = di,
                feedId = feedId,
                triggeredByUser = false,
            )

            action(feedId)
        }
}
