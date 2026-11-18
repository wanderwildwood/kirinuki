package com.wanderwildwood.kirinuki.model

import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FeedLoginTest {
    private val feed = "https://feeds.example.org/private.xml".toHttpUrl()
    private val login = Credentials.basic("ada", "pond-pass")

    private fun challenged(url: String) = Request.Builder().url(url).build()

    @Test
    fun theFeedsOwnHostIsGivenTheLogin() {
        val again = withFeedLogin(feed, challenged("https://feeds.example.org/private.xml"), login)
        assertEquals(login, again?.header("Authorization"))
    }

    @Test
    fun anotherHostAfterARedirectIsNot() {
        assertNull(withFeedLogin(feed, challenged("https://elsewhere.example.net/private.xml"), login))
    }

    @Test
    fun theSameHostOverPlainHttpIsNot() {
        assertNull(withFeedLogin(feed, challenged("http://feeds.example.org/private.xml"), login))
    }

    @Test
    fun anotherPortIsNot() {
        assertNull(withFeedLogin(feed, challenged("https://feeds.example.org:8443/private.xml"), login))
    }

    @Test
    fun aRequestThatAlreadyCarriedTheLoginIsNotTriedAgain() {
        val sent = Request.Builder().url("https://feeds.example.org/private.xml").header("Authorization", login).build()
        assertNull(withFeedLogin(feed, sent, login))
    }
}
