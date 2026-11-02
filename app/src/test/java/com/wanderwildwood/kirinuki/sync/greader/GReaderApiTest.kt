package com.wanderwildwood.kirinuki.sync.greader

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GReaderApiTest {
    private lateinit var server: MockWebServer
    private lateinit var fake: FakeGReaderServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        fake = FakeGReaderServer()
        server = MockWebServer()
        server.dispatcher = fake
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun root() = server.url("/").toString().trimEnd('/')

    private fun api(password: String = "api-pass-1") =
        GReaderApi(client, GReaderCredentials(server.url("/api/greader.php/").toString(), "ada", password))

    @Test
    fun signInFindsTheFreshRssPathUnderTheTypedAddress() {
        val resolved = GReaderApi.resolve(client, root(), "ada", "api-pass-1")
        assertEquals(server.url("/api/greader.php/").toString(), resolved)
    }

    @Test
    fun signInTakesTheFullApiAddressAsTyped() {
        val resolved = GReaderApi.resolve(client, root() + "/api/greader.php", "ada", "api-pass-1")
        assertEquals(server.url("/api/greader.php/").toString(), resolved)
    }

    @Test
    fun wrongPasswordSaysSo() {
        assertFailsWith<GReaderException.WrongPassword> {
            GReaderApi.resolve(client, root(), "ada", "nope")
        }
    }

    @Test
    fun aServerThatIsNotGoogleReaderSaysSo() {
        val other = MockWebServer()
        other.enqueue(MockResponse().setBody("<html>a blog</html>"))
        other.enqueue(MockResponse().setResponseCode(404))
        other.start()
        try {
            assertFailsWith<GReaderException.NotGoogleReader> {
                GReaderApi.resolve(client, other.url("/").toString(), "ada", "api-pass-1")
            }
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun nothingAnsweringIsUnreachable() {
        val gone = MockWebServer()
        gone.start()
        val address = gone.url("/").toString()
        gone.shutdown()
        assertFailsWith<GReaderException.Unreachable> {
            GReaderApi.resolve(client, address, "ada", "api-pass-1")
        }
    }

    @Test
    fun plainHttpToAPublicAddressIsRefusedBeforeAnythingIsSent() {
        assertFailsWith<GReaderException.Insecure> {
            GReaderApi.resolve(client, "http://feeds.example.org", "ada", "api-pass-1")
        }
    }

    @Test
    fun plainHttpIsAcceptedOnTheHomeNetwork() {
        assertTrue(GReaderApi.isLocalAddress("192.168.1.20"))
        assertTrue(GReaderApi.isLocalAddress("10.0.2.2"))
        assertTrue(GReaderApi.isLocalAddress("freshrss.lan"))
        assertTrue(GReaderApi.isLocalAddress("100.101.102.103"))
        assertTrue(!GReaderApi.isLocalAddress("203.0.113.9"))
        assertTrue(!GReaderApi.isLocalAddress("feeds.example.org"))
        assertTrue(!GReaderApi.isLocalAddress("fdroid.org"))
    }

    @Test
    fun noSchemeMeansHttps() {
        assertEquals("https://rss.example.org/", GReaderApi.candidates("rss.example.org").first().toString())
    }

    @Test
    fun subscriptionsAndFoldersAreRead() {
        fake.addSub("Pond Notes", "https://example.org/pond.xml", "Nature")
        fake.addSub("Kettle Weekly", "https://example.org/kettle.xml")
        val subs = api().subscriptions()
        assertEquals(2, subs.size)
        assertEquals(RemoteFeed("feed/100", "https://example.org/pond.xml", "Pond Notes", "Nature"), subs[0])
        assertEquals("", subs[1].label)
        assertEquals(listOf("Nature"), api().labels())
    }

    @Test
    fun idsFollowContinuationTokensToTheEnd() {
        val feed = fake.addSub("Pond Notes", "https://example.org/pond.xml")
        (1L..5L).forEach { fake.addItem(feed, it, "Entry $it", 1_700_000_000 + it) }
        val ids = api().itemIds(GReaderIds.READING_LIST)
        assertEquals((5L downTo 1L).map { GReaderIds.toLong(it.toString()) }, ids)
        // Three pages of two.
        assertEquals(3, fake.requests.count { it.endsWith("stream/items/ids") })
    }

    @Test
    fun contentsBecomeArticles() {
        val feed = fake.addSub("Pond Notes", "https://example.org/pond.xml")
        fake.addItem(feed, 42, "Frogs &amp; herons", 1_700_000_000)
        val items = api().contents(listOf(GReaderIds.toLong("42")!!))
        assertEquals(1, items.size)
        with(items[0]) {
            assertEquals(GReaderIds.toLong("42"), id)
            assertEquals("feed/100", feedStreamId)
            assertEquals("https://example.org/feed/100/42", link)
            assertEquals("<p>Body of Frogs &amp; herons</p>", html)
            assertEquals(1_700_000_000L, publishedEpochSeconds)
        }
    }

    @Test
    fun editTagSendsTheWriteTokenAndChangesTheServer() {
        val feed = fake.addSub("Pond Notes", "https://example.org/pond.xml")
        fake.addItem(feed, 7, "Seven", 1_700_000_000)
        val api = api()
        api.editTag(listOf(GReaderIds.toLong("7")!!), GReaderIds.READ, add = true)
        api.editTag(listOf(GReaderIds.toLong("7")!!), GReaderIds.STARRED, add = true)
        assertEquals(setOf(7L), fake.read)
        assertEquals(setOf(7L), fake.starred)
        api.editTag(listOf(GReaderIds.toLong("7")!!), GReaderIds.READ, add = false)
        assertEquals(emptySet(), fake.read)
    }

    @Test
    fun aForgottenSessionLogsInAgainOnce() {
        val api = api()
        api.login()
        // The server forgets the session: the next request is refused once, then logged in again.
        server.dispatcher =
            object : okhttp3.mockwebserver.Dispatcher() {
                var refused = false

                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse =
                    if (!refused && !request.path!!.contains("ClientLogin")) {
                        refused = true
                        MockResponse().setResponseCode(401)
                    } else {
                        fake.dispatch(request)
                    }
            }
        assertEquals(emptyList(), api.subscriptions())
    }

    @Test
    fun quickAddReturnsTheNewStream() {
        val stream = api().quickAdd("https://example.org/new.xml")
        assertEquals("feed/100", stream)
        assertEquals("https://example.org/new.xml", fake.subs.single().url)
    }
}
