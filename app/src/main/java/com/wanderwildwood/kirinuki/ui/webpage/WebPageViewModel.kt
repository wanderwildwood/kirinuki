package com.wanderwildwood.kirinuki.ui.webpage

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.background.runOnceFullTextSync
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import com.wanderwildwood.kirinuki.model.FeedParserError
import com.wanderwildwood.kirinuki.model.FullTextParser
import com.wanderwildwood.kirinuki.model.FullTextTooLarge
import com.wanderwildwood.kirinuki.model.UnsupportedContentType
import com.wanderwildwood.kirinuki.model.html.HtmlLinearizer
import com.wanderwildwood.kirinuki.model.html.LinearArticle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.instance
import java.net.URL

sealed interface WebPageState {
    data object Loading : WebPageState

    data class Page(
        val title: String?,
        val article: LinearArticle,
    ) : WebPageState

    /** Said in words, because an empty screen is not an answer. */
    data class Problem(
        val message: String,
    ) : WebPageState
}

/**
 * One web page read on its own, from a shared link or a scanned code, without subscribing to
 * anything. Fetched and put through Readability the same way a feed's full article is, but held
 * only here: nothing goes into the database, so nothing turns up in the feed list, in the
 * unread counts or in a sync. Keeping it is a separate press, and it goes where Save the page
 * on Add feed has always put a page.
 */
class WebPageViewModel(
    di: DI,
) : DIAwareViewModel(di) {
    private val fullTextParser: FullTextParser by instance()
    private val repository: Repository by instance()

    private val _state = MutableStateFlow<WebPageState>(WebPageState.Loading)
    val state: StateFlow<WebPageState> = _state

    private val _kept = MutableStateFlow(false)
    val kept: StateFlow<Boolean> = _kept

    private var url = ""

    fun load(address: String) {
        if (url == address && _state.value is WebPageState.Page) return
        url = address
        _state.value = WebPageState.Loading
        viewModelScope.launch { _state.value = fetch(address) }
    }

    fun reload() {
        if (url.isBlank()) return
        _state.value = WebPageState.Loading
        viewModelScope.launch { _state.value = fetch(url) }
    }

    /** Keeps the page under Pages, starred, where Save the page on Add feed puts one. */
    fun keep() {
        if (url.isBlank() || _kept.value) return
        val address =
            try {
                URL(url)
            } catch (e: Exception) {
                return
            }
        _kept.value = true
        viewModelScope.launch {
            repository.savePage(address, pagesTitle = getApplication<Application>().getString(R.string.saved_pages_feed))
            runOnceFullTextSync(di = di, triggeredByUser = true)
        }
    }

    private suspend fun fetch(address: String): WebPageState {
        val app = getApplication<Application>()
        return try {
            fullTextParser.fetchReadable(address).fold(
                ifLeft = { WebPageState.Problem(app.getString(messageFor(it))) },
                ifRight = { (html, title) ->
                    if (html.isNullOrBlank()) {
                        WebPageState.Problem(app.getString(R.string.failed_to_fetch_full_article_missing_body))
                    } else {
                        val article =
                            withContext(Dispatchers.Default) {
                                HtmlLinearizer(
                                    tooLargeText = app.getString(R.string.failed_to_fetch_full_article_too_large),
                                ).linearize(html = html, baseUrl = address)
                            }
                        WebPageState.Page(title = title?.trim()?.ifEmpty { null }, article = article)
                    }
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WebPageState.Problem(app.getString(R.string.failed_to_fetch_full_article))
        }
    }

    private fun messageFor(error: FeedParserError): Int =
        when (error) {
            is UnsupportedContentType -> R.string.failed_to_fetch_full_article_not_html
            is FullTextTooLarge -> R.string.failed_to_fetch_full_article_too_large
            else -> R.string.failed_to_fetch_full_article
        }
}
