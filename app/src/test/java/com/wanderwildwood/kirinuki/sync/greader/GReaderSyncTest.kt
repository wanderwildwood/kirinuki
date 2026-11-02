package com.wanderwildwood.kirinuki.sync.greader

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GReaderSyncTest {
    private lateinit var server: MockWebServer
    private lateinit var fake: FakeGReaderServer
    private lateinit var store: FakeGReaderStore
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        fake = FakeGReaderServer()
        server = MockWebServer()
        server.dispatcher = fake
        server.start()
        store = FakeGReaderStore()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sync(
        since: Long = 0,
        baseUrl: String = server.url("/api/greader.php/").toString(),
    ) = runTest {
        GReaderSync(GReaderApi(client, GReaderCredentials(baseUrl, "ada", "api-pass-1")), store).sync(since)
    }

    private fun serverWithTwoFeeds(): Pair<FakeGReaderServer.Sub, FakeGReaderServer.Sub> {
        val pond = fake.addSub("Pond Notes", "https://example.org/pond.xml", "Nature")
        val kettle = fake.addSub("Kettle Weekly", "https://example.org/kettle.xml")
        fake.addItem(pond, 1, "Frogspawn", 1_700_000_001)
        fake.addItem(pond, 2, "Herons", 1_700_000_002)
        fake.addItem(kettle, 3, "Descaling", 1_700_000_003)
        fake.read += 1L
        fake.starred += 2L
        return pond to kettle
    }

    @Test
    fun firstSyncBringsFeedsFoldersArticlesAndTheirState() {
        serverWithTwoFeeds()
        sync()
        assertEquals(setOf("Pond Notes", "Kettle Weekly"), store.feeds.map { it.title }.toSet())
        assertEquals("Nature", store.feeds.first { it.title == "Pond Notes" }.tag)
        assertEquals(3, store.articles.size)
        assertTrue(store.article(1)!!.read)
        assertFalse(store.article(2)!!.read)
        assertTrue(store.article(2)!!.starred)
        assertFalse(store.article(3)!!.starred)
    }

    @Test
    fun readAndStarredOnThePhoneReachTheServer() {
        serverWithTwoFeeds()
        sync()
        store.article(3)!!.read = true
        store.article(3)!!.starred = true
        store.article(1)!!.read = false
        sync()
        assertEquals(setOf(3L), fake.read)
        assertEquals(setOf(2L, 3L), fake.starred)
    }

    @Test
    fun readAndStarredOnTheServerReachThePhone() {
        serverWithTwoFeeds()
        sync()
        fake.read += 2L
        fake.starred -= 2L
        fake.starred += 3L
        sync()
        assertTrue(store.article(2)!!.read)
        assertFalse(store.article(2)!!.starred)
        assertTrue(store.article(3)!!.starred)
    }

    @Test
    fun changesMadeOfflineWaitAndAreSentOnTheNextSyncThatReaches() {
        serverWithTwoFeeds()
        sync()
        store.article(3)!!.read = true
        val dead = MockWebServer().apply { start() }
        val deadUrl = dead.url("/api/greader.php/").toString()
        dead.shutdown()
        assertFailsWith<GReaderException.Unreachable> { sync(baseUrl = deadUrl) }
        // Still waiting, not lost.
        assertTrue(fake.read == setOf(1L))
        sync()
        assertEquals(setOf(1L, 3L), fake.read)
    }

    @Test
    fun theLatestChangeWinsWhenBothSidesChangedSinceTheLastSync() {
        serverWithTwoFeeds()
        sync()
        // The phone reads 3; meanwhile the server stars 3 and reads 2.
        store.article(3)!!.read = true
        fake.starred += 3L
        fake.read += 2L
        sync()
        assertEquals(setOf(1L, 2L, 3L), fake.read)
        assertTrue(store.article(3)!!.starred)
        assertTrue(store.article(2)!!.read)
    }

    @Test
    fun theServerWinsForSubscriptions() {
        val (pond, kettle) = serverWithTwoFeeds()
        sync()
        fake.subs.remove(kettle)
        pond.label = "Water"
        pond.title = "Pond Notes (new)"
        sync()
        assertEquals(listOf("Pond Notes (new)"), store.feeds.map { it.title })
        assertEquals("Water", store.feeds.single().tag)
        assertEquals(2, store.articles.size)
    }

    @Test
    fun feedsOnlyOnThePhoneAreAddedToTheServerOnceAndKeepTheirFolder() {
        serverWithTwoFeeds()
        store.addLocalFeed("https://example.org/moss.xml", "Moss Diary", tag = "Nature")
        store.addLocalFeed("gemini://example.org/gemlog/", "A gemlog", syncable = false)
        sync()
        val moss = fake.subs.single { it.url == "https://example.org/moss.xml" }
        assertEquals("Nature", moss.label)
        assertEquals(3, fake.subs.size)
        val gemlog = store.feeds.single { it.title == "A gemlog" }
        assertEquals(null, store.feedLinks[gemlog.id]!!.streamId)
        sync()
        assertEquals(1, fake.requests.count { it.endsWith("quickadd") })
    }

    @Test
    fun aFeedDeletedOnThePhoneIsUnsubscribedOnTheServer() {
        serverWithTwoFeeds()
        sync()
        val kettle = store.feeds.single { it.title == "Kettle Weekly" }
        runTest { store.deleteFeed(kettle.id) }
        sync()
        assertEquals(listOf("Pond Notes"), fake.subs.map { it.title })
        assertEquals(1, store.feeds.size)
    }

    @Test
    fun aFolderChangedOnThePhoneIsSentToTheServer() {
        val (_, kettle) = serverWithTwoFeeds()
        sync()
        store.feeds.single { it.title == "Kettle Weekly" }.tag = "Kitchen"
        sync()
        assertEquals("Kitchen", kettle.label)
        assertEquals("Kitchen", store.feeds.single { it.title == "Kettle Weekly" }.tag)
    }

    @Test
    fun anArticleTheSiteAlreadyGaveIsTakenOverNotDoubled() {
        val local = store.addLocalFeed("https://example.org/pond.xml", "Pond Notes")
        store.addLocalArticle(local.id, "Herons", "https://example.org/feed/100/2", read = true)
        serverWithTwoFeeds()
        sync()
        assertEquals(1, store.feeds.count { it.url == "https://example.org/pond.xml" })
        assertEquals(1, store.articles.count { it.link == "https://example.org/feed/100/2" })
        val herons = store.article(2)
        assertNotNull(herons)
        // Read on the phone before signing in: kept, and sent.
        assertTrue(herons.read)
        sync()
        assertTrue(2L in fake.read)
    }

    @Test
    fun onlyNewArticlesAreFetchedAgain() {
        serverWithTwoFeeds()
        sync()
        val before = fake.requests.count { it.endsWith("stream/items/contents") }
        sync()
        assertEquals(before, fake.requests.count { it.endsWith("stream/items/contents") })
    }

    @Test
    fun aWrongPasswordStopsTheSyncAndSaysSo() {
        serverWithTwoFeeds()
        assertFailsWith<GReaderException.WrongPassword> {
            runTest {
                GReaderSync(GReaderApi(client, GReaderCredentials(server.url("/api/greader.php/").toString(), "ada", "nope")), store).sync(0)
            }
        }
        assertTrue(store.feeds.isEmpty())
    }
}
