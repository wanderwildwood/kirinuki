package com.wanderwildwood.kirinuki.ui.compose.searchfeed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wanderwildwood.kirinuki.FeederApplication
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import com.wanderwildwood.kirinuki.data.suggestions.SuggestedFeedRepository
import com.wanderwildwood.kirinuki.model.FeedParser
import com.wanderwildwood.kirinuki.model.FeedParserError
import com.wanderwildwood.kirinuki.model.HttpError
import com.wanderwildwood.kirinuki.model.NoAlternateFeeds
import com.wanderwildwood.kirinuki.model.NotInitializedYet
import com.wanderwildwood.kirinuki.model.SiteMetaData
import com.wanderwildwood.kirinuki.util.Either
import com.wanderwildwood.kirinuki.util.flatMap
import com.wanderwildwood.kirinuki.util.sloppyLinkToStrictURLOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.instance
import java.net.URL
import java.time.Instant

class SearchFeedViewModel(
    di: DI,
) : DIAwareViewModel(di) {
    private val feedParser: FeedParser by instance()
    private val repository: Repository by instance()
    private val application = getApplication<FeederApplication>()
    private val suggestedFeedRepository: SuggestedFeedRepository by instance()

    suspend fun ensureSuggestionsLoaded() =
        withContext(Dispatchers.IO) {
            suggestedFeedRepository.preload()
        }

    private var siteMetaData: Either<FeedParserError, SiteMetaData> by mutableStateOf(
        Either.Left(
            NotInitializedYet,
        ),
    )

    fun searchForFeeds(initialUrl: URL): Flow<Either<FeedParserError, SearchResult>> =
        flow {
            siteMetaData = feedParser.getSiteMetaData(initialUrl)
            // Flow collection makes this block concurrent with map below
            val initialSiteMetaData = siteMetaData

            initialSiteMetaData
                .onRight { metaData ->
                    metaData.alternateFeedLinks.forEach {
                        val channelId = YOUTUBE_CHANNELID_REGEX.find(it.link.toString())?.groupValues?.getOrNull(1)
                        emit(Either.Right(it.link))
                        if (channelId != null) {
                            emit(Either.Right(URL("https://www.youtube.com/feeds/videos.xml?playlist_id=UULF$channelId")))
                            emit(Either.Right(URL("https://www.youtube.com/feeds/videos.xml?playlist_id=UUSH$channelId")))
                            emit(Either.Right(URL("https://www.youtube.com/feeds/videos.xml?playlist_id=UULV$channelId")))
                        }
                    }
                    if (metaData.alternateFeedLinks.isEmpty()) {
                        emit(Either.Left(NoAlternateFeeds(initialUrl.toString())))
                    }
                }.onLeft {
                    if (it is HttpError) {
                        handleHttpError(it)
                    }
                    emit(Either.Right(initialUrl))
                }
        }.map { urlResult ->
            urlResult.flatMap { url ->
                feedParser
                    .parseFeedUrl(url)
                    .onRight { feed ->
                        if (siteMetaData.isLeft()) {
                            feed.home_page_url?.let { pageLink ->
                                sloppyLinkToStrictURLOrNull(pageLink)?.let { pageUrl ->
                                    siteMetaData = feedParser.getSiteMetaData(pageUrl)
                                }
                            }
                        }
                    }.map { feed ->
                        SearchResult(
                            title = feed.title ?: "",
                            url = feed.feed_url ?: url.toString(),
                            description = feed.description ?: "",
                            feedImage = siteMetaData.getOrNull()?.feedImage ?: "",
                        )
                    }.onLeft {
                        if (it is HttpError) {
                            handleHttpError(it)
                        }
                    }
            }
        }.flowOn(Dispatchers.Default)

    private suspend fun handleHttpError(httpError: HttpError) {
        httpError.retryAfterSeconds?.let { retryAfterSeconds ->
            repository.setRetryAfterForFeedsWithBaseUrl(
                host = URL(httpError.url).host,
                retryAfter = Instant.now().plusSeconds(retryAfterSeconds),
            )
        }
    }

    companion object {
        const val LOG_TAG = "FEEDER_SearchFeed"
        val YOUTUBE_CHANNELID_REGEX = Regex("^https?://www\\.youtube\\.com/feeds/videos\\.xml\\?channel_id=UC([-_a-zA-Z0-9]{22})")
    }

    suspend fun suggestionsFor(query: String): List<SearchResult> {
        ensureSuggestionsLoaded()
        if (query.isBlank()) {
            return emptyList()
        }
        return withContext(Dispatchers.Default) {
            suggestedFeedRepository
                .search(query)
                .map { suggestedFeed ->
                    val author = suggestedFeed.authorName
                    val showAuthor =
                        author.isNotBlank() &&
                            !suggestedFeed.headline.contains(author, ignoreCase = true)
                    SearchResult(
                        title = suggestedFeed.headline,
                        url = suggestedFeed.feedUrl,
                        description = if (showAuthor) author else "",
                        feedImage = "",
                    )
                }
        }
    }
}
