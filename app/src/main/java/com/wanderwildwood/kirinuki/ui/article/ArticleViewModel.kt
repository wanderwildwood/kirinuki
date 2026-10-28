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
import com.wanderwildwood.kirinuki.podcast.EpisodeStore
import com.wanderwildwood.kirinuki.db.room.FeedItemIdWithLink
import com.wanderwildwood.kirinuki.model.FullTextParser
import com.wanderwildwood.kirinuki.model.html.LinearArticle
import com.wanderwildwood.kirinuki.model.html.HtmlLinearizer
import com.wanderwildwood.kirinuki.model.html.withoutRepeatedTitle
import com.wanderwildwood.kirinuki.model.readaloud.ReadAloudPlayer
import com.wanderwildwood.kirinuki.model.readaloud.ReadAloudScript
import com.wanderwildwood.kirinuki.model.readaloud.Spoken
import com.wanderwildwood.kirinuki.util.FilePathProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    private val _readAloudScript = MutableStateFlow<List<Spoken>>(emptyList())

    /** The sentences read aloud would say, in order. */
    val readAloudScript: StateFlow<List<Spoken>> = _readAloudScript

    /** Made the first time it is opened, so an article only read never starts a speech engine. */
    private var readAloudPlayer: ReadAloudPlayer? = null

    private val _readAloudOpen = MutableStateFlow(false)
    val readAloudOpen: StateFlow<Boolean> = _readAloudOpen

    private val _readAloud = MutableStateFlow<StateFlow<ReadAloudPlayer.State>?>(null)

    /** Where read aloud is: which sentence, and whether it is speaking. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val readAloudState: StateFlow<ReadAloudPlayer.State> =
        _readAloud
            .flatMapLatest { it ?: flowOf(ReadAloudPlayer.State()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ReadAloudPlayer.State())

    init {
        viewModelScope.launch {
            combine(article, showingFullText) { article, fullText -> article to fullText }
                .collect { (article, fullText) ->
                    if (article == null || article.id <= 0L) return@collect
                    _content.value =
                        parseArticleContent(article, fullText).withoutRepeatedTitle(article.title)
                }
        }
        // Another article, or the same one switched between summary and whole text, is another
        // script: what was being read stops, and the next Play starts at its top.
        viewModelScope.launch {
            combine(article, content) { article, content -> article?.title to content }
                .collect { (title, content) ->
                    val script = withContext(Dispatchers.Default) { ReadAloudScript.of(content, title) }
                    _readAloudScript.value = script
                    readAloudPlayer?.load(script)
                }
        }
    }

    /** Opens the panel and starts reading; a second press closes it and stops. */
    fun toggleReadAloud() {
        if (_readAloudOpen.value) {
            closeReadAloud()
            return
        }
        val player =
            readAloudPlayer ?: ReadAloudPlayer(getApplication<Application>()).also {
                readAloudPlayer = it
                _readAloud.value = it.state
            }
        player.load(_readAloudScript.value)
        _readAloudOpen.value = true
        player.play()
    }

    fun closeReadAloud() {
        readAloudPlayer?.pause()
        _readAloudOpen.value = false
    }

    fun playOrPauseReadAloud() {
        val player = readAloudPlayer ?: return
        if (player.state.value.speaking) player.pause() else player.play()
    }

    fun seekReadAloud(index: Int) {
        readAloudPlayer?.seekTo(index)
    }

    override fun onCleared() {
        readAloudPlayer?.release()
        readAloudPlayer = null
        super.onCleared()
    }

    /** Settings owns this; the reader only needs to read it and nudge it. */
    val textScale: StateFlow<Float> = repository.textScale

    /** Off unless someone has turned it on in settings. See the setting itself for why. */
    val openTitleInBrowser: StateFlow<Boolean> = repository.openTitleInBrowser

    /** Settings has its own switch for episodes, apart from the feeds'. */
    val onlyOnWifi: StateFlow<Boolean> = repository.episodesOnlyOnWifi

    fun setTextScale(value: Float) = repository.setTextScale(value)

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
     * An episode played or not, by hand. Unplayed also forgets that it was heard to the end,
     * so the panel offers it from the start again rather than saying "Heard".
     */
    fun togglePlayed() {
        val current = article.value ?: return
        viewModelScope.launch {
            repository.setPlayed(itemId = current.id, played = current.unread)
            if (!current.unread) EpisodeStore(getApplication<Application>()).clearFinished(current.id)
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
