package com.wanderwildwood.kirinuki.model

import java.net.URL

/**
 * What an address turned out to be when it was fetched once, before anything was subscribed to.
 *
 * A shared link or a scanned code is far more often one story than a feed, and the app used to
 * find that out only by subscribing to it and getting a feed with nothing in it. This is the
 * evidence the add-feed screen shows instead of guessing.
 */
sealed interface AddressLook {
    /** The address is a feed itself. */
    data class Feed(
        val title: String?,
    ) : AddressLook

    /** A web page that names a feed, as a site's front page does and many story pages do too. */
    data class PageWithFeed(
        val feedUrl: URL,
        val pageTitle: String?,
    ) : AddressLook

    /** A web page that names no feed: a single story, most likely, to be read rather than followed. */
    data class Page(
        val pageTitle: String?,
    ) : AddressLook

    /** A plain text file: nothing to follow, but something to read. */
    data object TextFile : AddressLook

    /** It answered with something that is neither a page, a text file nor a feed. */
    data object Neither : AddressLook
}

/** Something to read rather than follow: Read this page goes first, and Subscribe asks twice. */
val AddressLook.isToRead: Boolean
    get() = this is AddressLook.Page || this is AddressLook.TextFile

/**
 * The decision itself, kept apart from the network so that it can be tested.
 *
 * Feed parsing is tried first, on anything that is not plainly a web page; only a page is looked
 * at for the feeds it names; and only a page that names none is called a story. The one feed
 * taken from a page is the first RSS or Atom link, the same one Subscribe has always taken.
 */
fun decideAddress(
    contentType: String?,
    body: String,
    feedTitleIn: (String) -> Result<String?>,
    feedLinksIn: (String) -> List<AlternateLink>,
    pageTitleIn: (String) -> String?,
    url: String = "",
): AddressLook {
    if (!looksLikeHtml(contentType, body)) {
        return feedTitleIn(body).fold(
            onSuccess = { AddressLook.Feed(title = it?.trim()?.ifEmpty { null }) },
            // Not a feed: a text file is still worth reading, in the page reader's text view.
            onFailure = {
                if (pageKindOf(contentType, url) == PageKind.TEXT && !body.contains('\u0000')) {
                    AddressLook.TextFile
                } else {
                    AddressLook.Neither
                }
            },
        )
    }
    val title = pageTitleIn(body)?.trim()?.ifEmpty { null }
    val feed = firstFeedOf(feedLinksIn(body))
    return if (feed != null) AddressLook.PageWithFeed(feedUrl = feed, pageTitle = title) else AddressLook.Page(pageTitle = title)
}

/** RSS or Atom ahead of anything else, since a JSON link on a page is as often oEmbed. */
fun firstFeedOf(links: List<AlternateLink>): URL? =
    (
        links.firstOrNull { it.type.contains("rss", ignoreCase = true) || it.type.contains("atom", ignoreCase = true) }
            ?: links.firstOrNull()
    )?.link

/**
 * A web page, as against a feed. The content type is believed when it says HTML, and when it says
 * XML or JSON; a server that says only text, or nothing, is judged by the start of what it sent.
 */
internal fun looksLikeHtml(
    contentType: String?,
    body: String,
): Boolean {
    val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    val start = body.trimStart().take(HTML_SNIFF_CHARS).lowercase()
    return when {
        // Some servers send their feed as text/html. What it starts with says otherwise.
        // An XHTML page may open with an XML declaration too, so the <html> after it decides.
        FEED_STARTS.any { start.startsWith(it) } && !start.contains("<html") -> false
        type == "text/html" || type == "application/xhtml+xml" -> true
        type.contains("xml") || type.contains("json") -> false
        else -> start.startsWith("<!doctype html") || start.contains("<html")
    }
}

private val FEED_STARTS = listOf("<?xml", "<rss", "<feed", "<rdf:rdf", "{")

private const val HTML_SNIFF_CHARS = 1024
