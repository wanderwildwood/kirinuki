package com.wanderwildwood.kirinuki.db.room

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * A phone on 0.5.x has a version 40 database full of feeds and articles. Going to 41 adds the two
 * sync tables and must leave everything already there as it was.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class TestMigrationFrom40To41 {
    private val dbName = "testDb"

    @Rule
    @JvmField
    val testHelper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate() {
        testHelper.createDatabase(dbName, FROM_VERSION).use { oldDB ->
            oldDB.execSQL(
                """
                INSERT INTO feeds(id, title, url, custom_title, tag, notify, image_url, last_sync, response_hash, fulltext_by_default, open_articles_with, alternate_id, currently_syncing, when_modified, site_fetched, skip_duplicates, retry_after, summarize_on_open, fetch_og_images, block_rules, allow_rules)
                VALUES(1, 'feed', 'https://example.com/feed.xml', 'Custom', 'News', 0, null, 0, 666, 0, '', 0, 0, 0, 0, 0, 0, 0, 0, 'blocked', '')
                """.trimIndent(),
            )
            oldDB.execSQL(
                """
                INSERT INTO feed_items(id, guid, title, plain_title, plain_snippet, image_url, image_from_body, enclosure_link, enclosure_type, author, pub_date, link, unread, notified, feed_id, first_synced_time, primary_sort_time, pinned, bookmarked, fulltext_downloaded, read_time, word_count, word_count_full, block_time)
                VALUES(7, 'guid-7', 'An article', 'An article', 'snippet', null, 0, null, null, 'Ada Whitlock', null, 'https://example.com/7', 0, 0, 1, 10, 20, 0, 1, 0, 30, 120, 0, null)
                """.trimIndent(),
            )
        }

        val db = testHelper.runMigrationsAndValidate(dbName, TO_VERSION, true, MIGRATION_40_41)

        db.query("SELECT custom_title, tag, block_rules FROM feeds WHERE id = 1").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("Custom", it.getString(0))
            assertEquals("News", it.getString(1))
            assertEquals("blocked", it.getString(2))
        }
        db.query("SELECT title, feed_id, bookmarked, read_time FROM feed_items WHERE id = 7").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("An article", it.getString(0))
            assertEquals(1L, it.getLong(1))
            assertEquals(1, it.getInt(2))
            assertEquals(30L, it.getLong(3))
        }
        db.query("SELECT COUNT(*) FROM greader_feeds").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM greader_items").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }

        // The new tables take rows, and an article's link goes with the article.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("INSERT INTO greader_feeds(feed_id, stream_id, synced_label) VALUES(1, 'feed/1', '')")
        db.execSQL("INSERT INTO greader_items(item_id, remote_id, synced_read, synced_starred) VALUES(7, 'r7', 1, 1)")
        db.execSQL("DELETE FROM feed_items WHERE id = 7")
        db.query("SELECT COUNT(*) FROM greader_items").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }

    companion object {
        private const val FROM_VERSION = 40
        private const val TO_VERSION = 41
    }
}
