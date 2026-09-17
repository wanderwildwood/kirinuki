package com.wanderwildwood.kirinuki.ui.addfeed

import androidx.lifecycle.viewModelScope
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import com.wanderwildwood.kirinuki.background.runOnceRssSync
import com.wanderwildwood.kirinuki.db.room.Feed
import com.wanderwildwood.kirinuki.db.room.ID_UNSET
import com.wanderwildwood.kirinuki.net.parseUrlLeniently
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import java.net.MalformedURLException

class AddFeedViewModel(
    di: DI,
) : DIAwareViewModel(di) {
    private val repository: Repository by instance()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    /** The feed being edited, once it has been read. Null while adding one. */
    private val _feed = MutableStateFlow<Feed?>(null)
    val feed: StateFlow<Feed?> = _feed

    /** The folders that exist, so that putting a feed in one is a tap and not a spelling test. */
    val folders: Flow<List<String>> = repository.allTags

    /**
     * Unsubscribes, and takes the articles that came with the feed with it.
     *
     * This lived on the feeds screen as a bin on every row. It is here because it belongs
     * with the other two things you can do to a feed, and because a list of names and
     * counts is a better list than one carrying a live delete on every line.
     */
    fun delete(feedId: Long) {
        if (feedId <= ID_UNSET) return
        viewModelScope.launch {
            repository.deleteFeeds(listOf(feedId))
            _saved.value = true
        }
    }

    fun load(feedId: Long) {
        _feed.value = null
        if (feedId <= ID_UNSET) return
        viewModelScope.launch {
            _feed.value = repository.getFeed(feedId)
        }
    }

    /**
     * The view model outlives the screen -- it is scoped to the activity -- so "saved"
     * has to be taken back once it has been acted on. Left set, opening Add feed a
     * second time closes it again before anything can be typed.
     */
    fun savedHandled() {
        _saved.value = false
    }

    /**
     * A bare hostname is what people actually type, so it is treated as one rather
     * than rejected: https is assumed, and the feed says what its own title is.
     */
    fun save(
        url: String,
        title: String,
        folder: String,
        feedId: Long = ID_UNSET,
    ) {
        _error.value = null
        val parsed =
            try {
                // A bare hostname is what people type, so https is assumed -- but never
                // over a scheme that was given, including gemini, which java.net.URL
                // cannot parse without a handler of its own.
                parseUrlLeniently(if (url.contains("://")) url else "https://$url")
            } catch (e: MalformedURLException) {
                _error.value = url
                return
            }

        viewModelScope.launch {
            // Editing knows which row it is editing. Adding has only the address to go on,
            // and the address may already be here -- in which case this is that feed, not
            // a second copy of it.
            val existing =
                when {
                    feedId > ID_UNSET -> repository.getFeed(feedId)
                    else -> repository.getFeed(parsed)
                }
            // A feed with no title yet is a blank row in the list, and it stays blank if
            // the first fetch fails. Stand the host in until the feed says its own name:
            // this is `title`, not `customTitle`, so the sync overwrites it.
            val placeholder = parsed.host.removePrefix("www.")
            // A blank box means two different things in the two screens. Editing loaded
            // the boxes from the feed, so emptying one is how a rename or a move is taken
            // back -- the feed's own title returns, and it leaves the folder. Adding never
            // loaded anything into them, so a blank box there is no opinion, and it must
            // not wipe the name and folder of a feed that turns out to be already here.
            val editing = feedId > ID_UNSET
            // A custom title that only repeats what the feed calls itself is not a custom
            // title. The edit screen arrives with the name in the box, so this is what
            // keeps opening it and saving it from pinning a name the feed may later change.
            val newCustomTitle =
                when {
                    editing || existing == null ->
                        title.trim().takeUnless { it == existing?.title }.orEmpty()
                    else -> title.trim().ifBlank { existing.customTitle }
                }
            val newTag =
                when {
                    editing || existing == null -> folder.trim()
                    else -> folder.trim().ifBlank { existing.tag }
                }
            val savedId =
                repository.saveFeed(
                    existing?.copy(
                        url = parsed,
                        customTitle = newCustomTitle,
                        tag = newTag,
                    )
                        ?: Feed(
                            url = parsed,
                            title = placeholder,
                            customTitle = newCustomTitle,
                            tag = newTag,
                        ),
                )
            // A rename or a move is not a reason to go to the network. A new feed is, and
            // so is one whose address just changed, because nothing here came from it.
            // ⚠ Compared as text: java.net.URL.equals resolves both hosts, and this runs
            // on the main dispatcher.
            if (existing == null || existing.url.toString() != parsed.toString()) {
                runOnceRssSync(di = di, feedId = savedId, forceNetwork = true, triggeredByUser = true)
            }
            _saved.value = true
        }
    }
}
