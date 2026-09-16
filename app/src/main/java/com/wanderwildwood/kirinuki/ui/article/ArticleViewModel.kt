package com.wanderwildwood.kirinuki.ui.article

import android.app.Application
import android.util.Log
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.archmodel.Article
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.archmodel.TextToDisplay
import androidx.lifecycle.viewModelScope
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import com.wanderwildwood.kirinuki.blob.blobFile
import com.wanderwildwood.kirinuki.blob.blobFullFile
import com.wanderwildwood.kirinuki.blob.blobFullInputStream
import com.wanderwildwood.kirinuki.blob.blobInputStream
import com.wanderwildwood.kirinuki.blob.blobOutputStream
import com.wanderwildwood.kirinuki.model.gemtext.GemtextParser
import com.wanderwildwood.kirinuki.net.gemini.GeminiClient
import com.wanderwildwood.kirinuki.net.gemini.GeminiResponse
import com.wanderwildwood.kirinuki.net.isGeminiUrl
import com.wanderwildwood.kirinuki.db.room.FeedItemIdWithLink
import com.wanderwildwood.kirinuki.model.FullTextParser
import com.wanderwildwood.kirinuki.model.html.LinearArticle
import com.wanderwildwood.kirinuki.model.html.HtmlLinearizer
import com.wanderwildwood.kirinuki.util.FilePathProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.instance

class ArticleViewModel(
    di: DI,
) : DIAwareViewModel(di) {
    private val repository: Repository by instance()
    private val filePathProvider: FilePathProvider by instance()
    private val fullTextParser: FullTextParser by instance()
    private val geminiClient: GeminiClient by instance()

    private val displayFullTextOverride = MutableStateFlow<Boolean?>(null)
    private val textToDisplay = MutableStateFlow(TextToDisplay.CONTENT)

    val article: StateFlow<Article?> =
        repository.currentArticle.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<TextToDisplay> = textToDisplay

    val showingFullText: StateFlow<Boolean> =
        combine(article, displayFullTextOverride) { article, override ->
            override ?: article?.fullTextByDefault ?: false
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _content = MutableStateFlow(LinearArticle(elements = emptyList()))
    val content: StateFlow<LinearArticle> = _content

    init {
        viewModelScope.launch {
            combine(article, showingFullText) { article, fullText -> article to fullText }
                .collect { (article, fullText) ->
                    if (article == null || article.id <= 0L) return@collect
                    _content.value = parseArticleContent(article, fullText)
                }
        }
    }

    fun toggleFullText() {
        textToDisplay.update { TextToDisplay.LOADING_FULLTEXT }
        displayFullTextOverride.value =
            displayFullTextOverride.value?.not() ?: article.value?.fullTextByDefault?.not() ?: true
    }

    fun toggleBookmarked() {
        val current = article.value ?: return
        viewModelScope.launch {
            repository.setBookmarked(itemId = current.id, bookmarked = !current.bookmarked)
        }
    }

    /**
     * The feed gives a summary; the full text is fetched from the page and kept beside it.
     * When the fetch fails the screen says so rather than showing an empty page.
     */
    private suspend fun parseArticleContent(
        article: Article,
        fullText: Boolean,
    ): LinearArticle =
        withContext(Dispatchers.IO) {
            val app = getApplication<Application>()
            val linearizer =
                HtmlLinearizer(
                    tooLargeText = app.getString(R.string.failed_to_fetch_full_article_too_large),
                )

            // A gemlog entry is a link rather than a document: the feed carried only its
            // title and date, so the text has to be fetched from the capsule. It is kept
            // in the same blob the feed pipeline uses, so the second read needs nothing.
            if (isGeminiUrl(article.link.orEmpty())) {
                return@withContext gemtextFor(article, linearizer = null)
            }

            val dir = if (fullText) filePathProvider.fullArticleDir else filePathProvider.articleDir
            val file = if (fullText) blobFullFile(article.id, dir) else blobFile(article.id, dir)

            if (fullText && !file.isFile) {
                val link = article.link
                if (link == null) {
                    textToDisplay.update { TextToDisplay.FAILED_MISSING_LINK }
                    return@withContext LinearArticle(elements = emptyList())
                }
                fullTextParser
                    .parseFullArticleIfMissing(
                        FeedItemIdWithLink(id = article.id, link = link),
                    ).leftOrNull()
                    ?.let {
                        textToDisplay.update { TextToDisplay.FAILED_TO_LOAD_FULLTEXT }
                        return@withContext LinearArticle(elements = emptyList())
                    }
            }

            if (!file.isFile) {
                textToDisplay.update { TextToDisplay.FAILED_MISSING_BODY }
                return@withContext LinearArticle(elements = emptyList())
            }

            try {
                val stream =
                    if (fullText) {
                        blobFullInputStream(article.id, dir)
                    } else {
                        blobInputStream(article.id, dir)
                    }
                stream
                    .use {
                        linearizer.linearize(inputStream = it, baseUrl = article.feedUrl ?: "")
                    }.also {
                        textToDisplay.update { TextToDisplay.CONTENT }
                    }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Could not open the stored article", e)
                textToDisplay.update { TextToDisplay.FAILED_TO_LOAD_FULLTEXT }
                LinearArticle(elements = emptyList())
            }
        }

    /**
     * The text of a gemlog entry, from the blob if it has been read before and from the
     * capsule if it has not. ⚠ The first read of an entry needs the network -- unlike a
     * feed article, whose body arrives with the feed.
     */
    private suspend fun gemtextFor(
        article: Article,
        @Suppress("UNUSED_PARAMETER") linearizer: Any?,
    ): LinearArticle {
        val link = article.link ?: return LinearArticle(elements = emptyList())
        val cached = blobFile(article.id, filePathProvider.articleDir)

        val gemtext =
            if (cached.isFile) {
                try {
                    blobInputStream(article.id, filePathProvider.articleDir)
                        .use { it.readBytes().toString(Charsets.UTF_8) }
                        // ⚠ The sync writes a blob for every entry, and a gemlog entry
                        // arrives with no body -- so the file exists and holds nothing.
                        // Present-but-empty has to count as a miss or the page never loads.
                        .takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Could not read the stored capsule page", e)
                    null
                }
            } else {
                null
            } ?: when (val response = geminiClient.fetch(link)) {
                is GeminiResponse.Body ->
                    if (response.isGemtext) {
                        response.text().also { text ->
                            try {
                                blobOutputStream(article.id, filePathProvider.articleDir)
                                    .use { it.write(text.toByteArray(Charsets.UTF_8)) }
                            } catch (e: Exception) {
                                // Losing the cache costs a refetch, not the page.
                                Log.e(LOG_TAG, "Could not store the capsule page", e)
                            }
                        }
                    } else {
                        textToDisplay.update { TextToDisplay.FAILED_NOT_HTML }
                        return LinearArticle(elements = emptyList())
                    }

                else -> {
                    textToDisplay.update { TextToDisplay.FAILED_TO_LOAD_FULLTEXT }
                    return LinearArticle(elements = emptyList())
                }
            }

        textToDisplay.update { TextToDisplay.CONTENT }
        return GemtextParser(link).parse(gemtext)
    }

    companion object {
        private const val LOG_TAG = "KIRINUKI_ARTICLE"
    }
}
