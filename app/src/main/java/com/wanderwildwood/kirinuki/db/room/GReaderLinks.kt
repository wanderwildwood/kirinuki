package com.wanderwildwood.kirinuki.db.room

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.wanderwildwood.kirinuki.db.COL_ID

/**
 * A feed and its subscription on the feed server (sync with a feed server, docs/SYNC.md).
 *
 * Deliberately no foreign key to feeds: when a feed is deleted on the phone this row stays
 * behind, and that left-over row is how the next sync knows to unsubscribe on the server.
 * Feed ids are never reused (AUTOINCREMENT), so a left-over row cannot be mistaken for a
 * later feed. A null [streamId] is a feed the server would not take, kept on the phone only.
 */
@Entity(
    tableName = "greader_feeds",
    indices = [Index(value = ["stream_id"], unique = true)],
)
data class GReaderFeedRow(
    @PrimaryKey
    @ColumnInfo(name = "feed_id") val feedId: Long,
    @ColumnInfo(name = "stream_id") val streamId: String?,
    @ColumnInfo(name = "synced_label") val syncedLabel: String,
)

/**
 * An article that came from the feed server: its id there, and read and starred as phone and
 * server last agreed on them (the base [com.wanderwildwood.kirinuki.sync.greader.GReaderMerge]
 * compares against). Goes with the article when the article goes.
 */
@Entity(
    tableName = "greader_items",
    indices = [Index(value = ["remote_id"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = FeedItem::class,
            parentColumns = [COL_ID],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GReaderItemRow(
    @PrimaryKey
    @ColumnInfo(name = "item_id") val itemId: Long,
    @ColumnInfo(name = "remote_id") val remoteId: String,
    @ColumnInfo(name = "synced_read") val syncedRead: Boolean,
    @ColumnInfo(name = "synced_starred") val syncedStarred: Boolean,
)

/** One linked article with its state on the phone now, for the merge. */
data class GReaderItemState(
    @ColumnInfo(name = "item_id") val itemId: Long,
    @ColumnInfo(name = "remote_id") val remoteId: String,
    @ColumnInfo(name = "synced_read") val syncedRead: Boolean,
    @ColumnInfo(name = "synced_starred") val syncedStarred: Boolean,
    @ColumnInfo(name = "is_read") val isRead: Boolean,
    @ColumnInfo(name = "bookmarked") val bookmarked: Boolean,
)

@Dao
interface GReaderDao {
    @Query("SELECT * FROM greader_feeds")
    suspend fun feedRows(): List<GReaderFeedRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFeedRow(row: GReaderFeedRow)

    @Query("DELETE FROM greader_feeds WHERE feed_id = :feedId")
    suspend fun deleteFeedRow(feedId: Long)

    /** The feeds whose articles come from the server, not from their sites. */
    @Query("SELECT feed_id FROM greader_feeds WHERE stream_id IS NOT NULL")
    suspend fun serverFeedIds(): List<Long>

    @Query(
        """
        SELECT g.item_id, g.remote_id, g.synced_read, g.synced_starred,
               (fi.read_time IS NOT NULL) AS is_read, fi.bookmarked AS bookmarked
        FROM greader_items g JOIN feed_items fi ON fi.id = g.item_id
        """,
    )
    suspend fun itemStates(): List<GReaderItemState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItemRows(rows: List<GReaderItemRow>)

    @Query("UPDATE greader_items SET synced_read = :read, synced_starred = :starred WHERE item_id = :itemId")
    suspend fun setBase(
        itemId: Long,
        read: Boolean,
        starred: Boolean,
    )

    /** An article in [feedId] with [link] that the server has not been linked to yet. */
    @Query(
        """
        SELECT fi.id FROM feed_items fi
        WHERE fi.feed_id = :feedId AND fi.link = :link
          AND fi.id NOT IN (SELECT item_id FROM greader_items)
        ORDER BY fi.id LIMIT 1
        """,
    )
    suspend fun unlinkedItemWithLink(
        feedId: Long,
        link: String,
    ): Long?

    /** Read, unstarred articles in [feedId] beyond the newest [keep]. */
    @Query(
        """
        SELECT id FROM feed_items
        WHERE feed_id = :feedId AND bookmarked = 0 AND read_time IS NOT NULL
        ORDER BY primary_sort_time DESC, pub_date DESC
        LIMIT -1 OFFSET :keep
        """,
    )
    suspend fun prunable(
        feedId: Long,
        keep: Int,
    ): List<Long>

    @Query("DELETE FROM greader_feeds")
    suspend fun deleteAllFeedRows()

    @Query("DELETE FROM greader_items")
    suspend fun deleteAllItemRows()
}
