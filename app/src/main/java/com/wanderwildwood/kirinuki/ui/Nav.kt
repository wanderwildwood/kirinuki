package com.wanderwildwood.kirinuki.ui

/**
 * Three screens and the two that serve them. The feeds, the cuttings in one feed,
 * and the cutting itself; adding a feed and the settings hang off the first.
 */
object Route {
    const val FEEDS = "feeds"
    const val ARTICLES = "articles"
    const val ARTICLE = "article"
    const val SETTINGS = "settings"
    const val SERVER_SYNC = "serversync"

    /**
     * One screen, two jobs: with an address it subscribes, with a feed it edits that feed.
     * Both arguments are optional, so either way in matches the same route.
     */
    const val ADD_FEED = "addfeed"
    const val ADD_FEED_ARG = "url"
    const val ADD_FEED_FEED_ARG = "feed"
    const val ADD_FEED_ROUTE =
        "$ADD_FEED?$ADD_FEED_ARG={$ADD_FEED_ARG}&$ADD_FEED_FEED_ARG={$ADD_FEED_FEED_ARG}"

    fun addFeed(url: String): String =
        "$ADD_FEED?$ADD_FEED_ARG=" + android.net.Uri.encode(url)

    fun editFeed(feedId: Long): String = "$ADD_FEED?$ADD_FEED_FEED_ARG=$feedId"

    /** A page of a Gemini capsule. The URL rides in the route so back is history. */
    const val GEMINI = "gemini"
    const val GEMINI_ARG = "url"
    const val GEMINI_ROUTE = "$GEMINI/{$GEMINI_ARG}"

    fun gemini(url: String): String = "$GEMINI/" + android.net.Uri.encode(url)

    /**
     * One web page read on its own, from a shared link or a scanned code. Held only by the
     * screen, never stored, so it leaves nothing in the feed list behind it.
     */
    const val WEB_PAGE = "page"
    const val WEB_PAGE_ARG = "url"
    const val WEB_PAGE_ROUTE = "$WEB_PAGE/{$WEB_PAGE_ARG}"

    fun webPage(url: String): String = "$WEB_PAGE/" + android.net.Uri.encode(url)
}
