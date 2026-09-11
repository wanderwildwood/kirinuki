package com.wanderwildwood.kirinuki.model

import androidx.compose.runtime.Immutable
import com.wanderwildwood.kirinuki.db.room.FeedItemCursor
import java.time.Instant
import java.time.ZonedDateTime

/**
 * One cutting in the list: what is shown of an article before it is opened.
 *
 * No image. The list is type on paper, and a thumbnail at sixteen greys costs a
 * redraw to say less than the title already does.
 */
@Immutable
data class FeedListItem(
    val id: Long,
    val title: String,
    val snippet: String,
    val feedTitle: String,
    val unread: Boolean,
    val pubDate: String,
    val link: String?,
    val bookmarked: Boolean,
    val primarySortTime: Instant,
    val rawPubDate: ZonedDateTime?,
    val wordCount: Int,
) {
    val cursor: FeedItemCursor
        get() =
            object : FeedItemCursor {
                override val primarySortTime: Instant = this@FeedListItem.primarySortTime
                override val pubDate: ZonedDateTime? = this@FeedListItem.rawPubDate
                override val id: Long = this@FeedListItem.id
            }
}
