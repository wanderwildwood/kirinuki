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
    const val TOUR = "tour"
    const val ADD_FEED = "addfeed"
    const val ADD_FEED_ARG = "url"
    const val ADD_FEED_ROUTE = "$ADD_FEED?$ADD_FEED_ARG={$ADD_FEED_ARG}"

    fun addFeed(url: String): String =
        "$ADD_FEED?$ADD_FEED_ARG=" + android.net.Uri.encode(url)

    /** A page of a Gemini capsule. The URL rides in the route so back is history. */
    const val GEMINI = "gemini"
    const val GEMINI_ARG = "url"
    const val GEMINI_ROUTE = "$GEMINI/{$GEMINI_ARG}"

    fun gemini(url: String): String = "$GEMINI/" + android.net.Uri.encode(url)
}
