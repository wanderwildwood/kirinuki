package com.wanderwildwood.kirinuki.sync.nextcloud

import com.wanderwildwood.kirinuki.sync.greader.FakeGReaderStore
import com.wanderwildwood.kirinuki.sync.greader.GReaderCredentials
import com.wanderwildwood.kirinuki.sync.greader.GReaderException
import com.wanderwildwood.kirinuki.sync.greader.GReaderSync
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The same sync as the Google Reader one, against Nextcloud News' own API. */
class NextcloudNewsSyncTest {
    private lateinit var server: MockWebServer
    private lateinit var fake: FakeNextcloudNews
    private lateinit var store: FakeGReaderStore
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        fake = FakeNextcloudNews()
        server = MockWebServer()
        server.dispatcher = fake
        server.start()
        store = FakeGReaderStore()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val apiUrl get() = server.url("/index.php/apps/news/api/v1-3/").toString()

    private fun sync(
        since: Long = 0,
        full: Boolean = true,
        password: String = "app-pass-1",
    ) = runTest {
        GReaderSync(NextcloudNewsApi(client, GReaderCredentials(apiUrl, "ada", password), fullUnread = full), store).sync(since)
    }

    private lateinit var pond: FakeNextcloudNews.Feed
    private lateinit var kettle: FakeNextcloudNews.Feed

    /** Pond Notes in Nature with Frogspawn (read) and Herons (starred); Kettle Weekly with Descaling. */
    private fun serverWithTwoFeeds(): Triple<Long, Long, Long> {
        pond = fake.addFeed("Pond Notes", "https://example.org/pond.xml", "Nature")
        kettle = fake.addFeed("Kettle Weekly", "https://example.org/kettle.xml")
        val frogspawn = fake.addItem(pond, "Frogspawn").apply { unread = false }
        val herons = fake.addItem(pond, "Herons").apply { starred = true }
        val descaling = fake.addItem(kettle, "Descaling")
        return Triple(frogspawn.id, herons.id, descaling.id)
    }

    private fun article(id: Long) = store.articleByRemote(id.toString())!!

    @Test
    fun firstSyncBringsFeedsFoldersArticlesAndTheirState() {
        val (frogspawn, herons, descaling) = serverWithTwoFeeds()
        sync()
        assertEquals(setOf("Pond Notes", "Kettle Weekly"), store.feeds.map { it.title }.toSet())
        assertEquals("Nature", store.feeds.first { it.title == "Pond Notes" }.tag)
        assertEquals("", store.feeds.first { it.title == "Kettle Weekly" }.tag)
        assertEquals(3, store.articles.size)
        assertTrue(article(frogspawn).read)
        assertFalse(article(herons).read)
        assertTrue(article(herons).starred)
        assertFalse(article(descaling).starred)
        assertEquals("<p>Descaling</p>", article(descaling).body)
    }

    @Test
    fun readAndStarredOnThePhoneReachTheServer() {
        val (frogspawn, _, descaling) = serverWithTwoFeeds()
        sync()
        article(descaling).read = true
        article(descaling).starred = true
        article(frogspawn).read = false
        sync(full = false)
        assertFalse(fake.item(descaling).unread)
        assertTrue(fake.item(descaling).starred)
        assertTrue(fake.item(frogspawn).unread)
    }

    @Test
    fun readAndStarredOnTheServerReachThePhoneWithoutTheWholeList() {
        val (_, herons, descaling) = serverWithTwoFeeds()
        sync()
        fake.now += 600
        fake.item(herons).apply { unread = false; starred = false }.also(fake::touch)
        fake.item(descaling).apply { starred = true }.also(fake::touch)
        sync(since = fake.now - 60, full = false)
        assertTrue(article(herons).read)
        assertFalse(article(herons).starred)
        assertTrue(article(descaling).starred)
        assertFalse(article(descaling).read)
        // Only the changes and the starred list were asked for, not every unread article.
        assertEquals(1, fake.fullUnreadLists)
    }

    @Test
    fun anArticleTheIncrementalSyncDidNotHearAboutKeepsItsState() {
        val (_, _, descaling) = serverWithTwoFeeds()
        sync()
        fake.now += 600
        sync(since = fake.now - 60, full = false)
        assertFalse(article(descaling).read)
    }

    @Test
    fun theDailyFullListCatchesAnArticleTheServerDroppedAfterReadingIt() {
        val (_, _, descaling) = serverWithTwoFeeds()
        sync()
        // Read and then purged on the server between two syncs: the changes list never says so.
        fake.items.removeAll { it.id == descaling }
        fake.now += 600
        sync(since = fake.now - 60, full = false)
        assertFalse(article(descaling).read)
        sync(since = fake.now - 60, full = true)
        assertTrue(article(descaling).read)
    }

    @Test
    fun changesMadeOfflineWaitAndAreSentOnTheNextSyncThatReaches() {
        val (_, _, descaling) = serverWithTwoFeeds()
        sync()
        article(descaling).read = true
        val dead = MockWebServer().apply { start() }
        val deadUrl = dead.url("/index.php/apps/news/api/v1-3/").toString()
        dead.shutdown()
        assertFailsWith<GReaderException.Unreachable> {
            runTest { GReaderSync(NextcloudNewsApi(client, GReaderCredentials(deadUrl, "ada", "app-pass-1")), store).sync(0) }
        }
        assertTrue(fake.item(descaling).unread)
        sync(full = false)
        assertFalse(fake.item(descaling).unread)
    }

    @Test
    fun theServerWinsForSubscriptions() {
        serverWithTwoFeeds()
        sync()
        fake.feeds.remove(kettle)
        fake.items.removeAll { it.feedId == kettle.id }
        pond.title = "Pond Notes (new)"
        pond.folderId = fake.folder("Water")
        sync(full = false)
        assertEquals(listOf("Pond Notes (new)"), store.feeds.map { it.title })
        assertEquals("Water", store.feeds.single().tag)
        assertEquals(2, store.articles.size)
    }

    @Test
    fun aStarredArticleOutlivesItsFeedGoingFromTheServer() {
        val (_, herons, _) = serverWithTwoFeeds()
        sync()
        fake.feeds.remove(pond)
        fake.items.removeAll { it.feedId == pond.id }
        sync(full = false)
        sync(full = true)
        val kept = store.articles.single { it.title == "Herons" }
        assertTrue(kept.starred)
        assertNull(store.articleByRemote(herons.toString()))
    }

    @Test
    fun aFeedNewOnTheServerBringsItsOlderUnreadArticles() {
        serverWithTwoFeeds()
        sync()
        val moss = fake.addFeed("Moss Diary", "https://example.org/moss.xml")
        val old = fake.addItem(moss, "Old moss")
        fake.now += 3 * 24 * 3600
        sync(since = fake.now - 60, full = false)
        assertEquals("Moss Diary", store.feeds.first { store.feedLinks[it.id]?.streamId == "feed/${moss.id}" }.title)
        assertFalse(article(old.id).read)
    }

    @Test
    fun feedsOnlyOnThePhoneAreAddedToTheServerOnceAndKeepTheirFolder() {
        serverWithTwoFeeds()
        store.addLocalFeed("https://example.org/moss.xml", "Moss Diary", tag = "Nature")
        store.addLocalFeed("https://example.org/unreadable.xml", "Broken")
        store.addLocalFeed("gemini://example.org/gemlog/", "A gemlog", syncable = false)
        sync()
        val moss = fake.feeds.single { it.url == "https://example.org/moss.xml" }
        assertEquals("Nature", fake.folders[moss.folderId])
        assertEquals(3, fake.feeds.size)
        assertNull(store.feedLinks[store.feeds.single { it.title == "Broken" }.id]!!.streamId)
        assertNull(store.feedLinks[store.feeds.single { it.title == "A gemlog" }.id]!!.streamId)
        sync(full = false)
        assertEquals(2, fake.requests.count { it == "POST feeds" })
    }

    @Test
    fun aFeedTheServerAlreadyHasIsLinkedNotAddedTwice() {
        serverWithTwoFeeds()
        // On the phone under another spelling of the same address.
        store.addLocalFeed("http://example.org/kettle.xml/", "Kettle")
        sync()
        assertEquals(2, fake.feeds.size)
        assertEquals(2, store.feeds.size)
    }

    @Test
    fun aFeedDeletedOnThePhoneIsUnsubscribedOnTheServer() {
        serverWithTwoFeeds()
        sync()
        val local = store.feeds.single { it.title == "Kettle Weekly" }
        runTest { store.deleteFeed(local.id) }
        sync(full = false)
        assertEquals(listOf("Pond Notes"), fake.feeds.map { it.title })
        assertEquals(1, store.feeds.size)
    }

    @Test
    fun aFolderChangedOnThePhoneIsSentToTheServer() {
        serverWithTwoFeeds()
        sync()
        store.feeds.single { it.title == "Kettle Weekly" }.tag = "Kitchen"
        store.feeds.single { it.title == "Pond Notes" }.tag = ""
        sync(full = false)
        assertEquals("Kitchen", fake.folders[kettle.folderId])
        assertNull(pond.folderId)
        assertEquals("Kitchen", store.feeds.single { it.title == "Kettle Weekly" }.tag)
    }

    @Test
    fun aCappedUnreadListDoesNotMarkOlderArticlesRead() {
        pond = fake.addFeed("Pond Notes", "https://example.org/pond.xml")
        val first = fake.addItem(pond, "Oldest")
        sync()
        assertFalse(article(first.id).read)
        repeat(NextcloudNewsApi.MAX_UNREAD + 10) { fake.addItem(pond, "Later $it") }
        // Nothing in the changes list either: only the capped full list speaks.
        sync(since = fake.now + 1, full = true)
        assertFalse(article(first.id).read)
    }

    @Test
    fun aWrongPasswordStopsTheSyncAndSaysSo() {
        serverWithTwoFeeds()
        assertFailsWith<GReaderException.WrongPassword> { sync(password = "nope") }
        assertTrue(store.feeds.isEmpty())
    }

    @Test
    fun theAddressPeopleKnowFindsTheApi() {
        val typed = server.url("/").toString()
        val found = NextcloudNewsApi.resolve(client, typed, "ada", "app-pass-1")
        assertEquals(apiUrl, found)
        assertFailsWith<GReaderException.WrongPassword> { NextcloudNewsApi.resolve(client, typed, "ada", "nope") }
    }

    @Test
    fun anAddressWithoutNewsSaysSo() {
        // Some other web server: every address is a page, none of it News.
        val other =
            MockWebServer().apply {
                dispatcher =
                    object : okhttp3.mockwebserver.Dispatcher() {
                        override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) =
                            okhttp3.mockwebserver.MockResponse().setResponseCode(404).setBody("<html>Not here</html>")
                    }
                start()
            }
        try {
            assertFailsWith<GReaderException.NotGoogleReader> {
                NextcloudNewsApi.resolve(client, other.url("/").toString(), "ada", "app-pass-1")
            }
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun candidatesCoverTheUsualShapes() {
        assertEquals(
            listOf("https://cloud.example.org/index.php/apps/news/api/v1-3/", "https://cloud.example.org/apps/news/api/v1-3/"),
            NextcloudNewsApi.candidates("cloud.example.org").map { it.toString() },
        )
        assertEquals(
            listOf("https://example.org/nextcloud/index.php/apps/news/api/v1-3/", "https://example.org/nextcloud/apps/news/api/v1-3/"),
            NextcloudNewsApi.candidates("https://example.org/nextcloud/index.php/apps/files/").map { it.toString() },
        )
        assertEquals(
            listOf("http://192.168.1.20/index.php/apps/news/api/v1-3/"),
            NextcloudNewsApi.candidates("http://192.168.1.20/index.php/apps/news/api/v1-3").map { it.toString() },
        )
    }

    @Test
    fun plainHttpToAPublicAddressIsRefusedBeforeThePasswordIsSent() {
        assertFailsWith<GReaderException.Insecure> {
            NextcloudNewsApi.resolve(client, "http://cloud.example.org", "ada", "app-pass-1")
        }
    }
}
