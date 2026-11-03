package com.wanderwildwood.kirinuki.model.known

import java.net.URI
import java.text.Normalizer
import java.util.Locale

/**
 * A feed that was checked by hand, so that typing a paper's or a magazine's name into Add feed
 * finds it without a search engine. The list is part of the app and is matched on the phone:
 * nothing is sent anywhere while a name is typed. docs/BUILTIN-FEEDS.md says when each address
 * was last seen to serve a feed.
 */
data class KnownFeed(
    val name: String,
    val site: String,
    val feedUrl: String,
    /** A language tag, with the country when the source is a country's own, e.g. de-AT. */
    val language: String,
    val keywords: List<String>,
)

/**
 * The known feeds that answer to what was typed, best first, at most [limit] of them.
 *
 * A name is typed loosely -- "low tech", "lowtech", "Low-tech", "sueddeutsche" -- so both sides
 * are folded the same way before they are compared: lower case, accents dropped, everything that
 * is not a letter or a digit taken out. A typed address is left to the address box: nothing is
 * suggested once there is a scheme in it.
 */
fun knownFeedsMatching(
    typed: String,
    feeds: List<KnownFeed> = KNOWN_FEEDS,
    limit: Int = MAX_SUGGESTIONS,
): List<KnownFeed> {
    val text = typed.trim()
    if (text.contains("://")) return emptyList()
    val query = folded(text)
    if (query.length < 2) return emptyList()
    return feeds
        .mapNotNull { feed -> scoreOf(feed, query).takeIf { it > 0 }?.let { feed to it } }
        .sortedWith(compareByDescending<Pair<KnownFeed, Int>> { it.second }.thenBy { it.first.name.length })
        .take(limit)
        .map { it.first }
}

private fun scoreOf(
    feed: KnownFeed,
    query: String,
): Int {
    val name = folded(feed.name)
    val nameWords = feed.name.split(' ', '-', ':', '(', ')').map(::folded).filter { it.isNotEmpty() }
    val keywords = feed.keywords.map(::folded)
    val host = folded(hostOf(feed.site))
    return when {
        name == query || keywords.any { it == query } -> 4
        // Two letters are too few to go looking inside words with: "uk" or "nl" only as itself.
        query.length < 3 -> 0
        name.startsWith(query) || nameWords.any { it.startsWith(query) } -> 3
        keywords.any { it.startsWith(query) } -> 2
        name.contains(query) || host.contains(query) || keywords.any { it.contains(query) } -> 1
        else -> 0
    }
}

private fun hostOf(site: String): String =
    try {
        URI(site).host.orEmpty().removePrefix("www.")
    } catch (e: Exception) {
        ""
    }

/** Lower case, no accents, letters and digits only. */
internal fun folded(text: String): String =
    Normalizer
        .normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .filter { it.isLetterOrDigit() }

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** As many as fit under the address box on the Kompakt without pushing the rest off the page. */
const val MAX_SUGGESTIONS = 5

/**
 * Checked 2026-11-03: every address answered with RSS or Atom carrying entries. Kept in the
 * same order as docs/BUILTIN-FEEDS.md, grouped by language.
 */
val KNOWN_FEEDS: List<KnownFeed> =
    listOf(
        KnownFeed("BBC News", "https://www.bbc.co.uk/news", "https://feeds.bbci.co.uk/news/rss.xml", "en-GB", listOf("bbc", "british", "britain", "uk", "world")),
        KnownFeed("The Guardian", "https://www.theguardian.com", "https://www.theguardian.com/international/rss", "en-GB", listOf("guardian", "uk", "britain")),
        KnownFeed("The Guardian: World", "https://www.theguardian.com/world", "https://www.theguardian.com/world/rss", "en-GB", listOf("guardian", "world", "international")),
        KnownFeed("Al Jazeera English", "https://www.aljazeera.com", "https://www.aljazeera.com/xml/rss/all.xml", "en", listOf("al jazeera", "aljazeera", "middle east", "qatar", "world")),
        KnownFeed("NPR News", "https://www.npr.org", "https://feeds.npr.org/1001/rss.xml", "en-US", listOf("npr", "radio", "america", "usa", "us")),
        KnownFeed("DW (English)", "https://www.dw.com/en", "https://rss.dw.com/rdf/rss-en-all", "en", listOf("dw", "deutsche welle", "germany")),
        KnownFeed("France 24 (English)", "https://www.france24.com/en", "https://www.france24.com/en/rss", "en", listOf("france 24", "france24", "france")),
        KnownFeed("Le Monde in English", "https://www.lemonde.fr/en", "https://www.lemonde.fr/en/rss/une.xml", "en", listOf("le monde", "lemonde", "france")),
        KnownFeed("The New York Times", "https://www.nytimes.com", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml", "en-US", listOf("new york times", "nytimes", "nyt", "america", "usa")),
        KnownFeed("The Washington Post: World", "https://www.washingtonpost.com/world", "https://www.washingtonpost.com/arcio/rss/category/world/", "en-US", listOf("washington post", "wapo", "america", "usa")),
        KnownFeed("ABC News (Australia)", "https://www.abc.net.au/news", "https://www.abc.net.au/news/feed/51120/rss.xml", "en-AU", listOf("abc", "australia")),
        KnownFeed("CBC News", "https://www.cbc.ca/news", "https://www.cbc.ca/webfeed/rss/rss-topstories", "en-CA", listOf("cbc", "canada")),
        KnownFeed("RNZ News", "https://www.rnz.co.nz", "https://www.rnz.co.nz/rss/national.xml", "en-NZ", listOf("rnz", "radio new zealand", "new zealand")),
        KnownFeed("RTÉ News", "https://www.rte.ie/news", "https://www.rte.ie/feeds/rss/?index=/news/", "en-IE", listOf("rte", "ireland", "irish")),
        KnownFeed("The Irish Times", "https://www.irishtimes.com", "https://www.irishtimes.com/arc/outboundfeeds/feed-irish-news/?outputType=xml", "en-IE", listOf("irish times", "ireland", "irish")),
        KnownFeed("Euronews", "https://www.euronews.com", "https://www.euronews.com/rss", "en", listOf("euronews", "europe", "eu")),
        KnownFeed("The Conversation", "https://theconversation.com", "https://theconversation.com/global/articles.atom", "en", listOf("conversation", "academics", "research")),
        KnownFeed("ProPublica", "https://www.propublica.org", "https://feeds.propublica.org/propublica/main", "en-US", listOf("propublica", "investigative", "america")),
        KnownFeed("The Kyiv Independent", "https://kyivindependent.com", "https://kyivindependent.com/news-archive/rss/", "en", listOf("kyiv", "kyiv independent", "ukraine")),
        KnownFeed("Notes from Poland", "https://notesfrompoland.com", "https://notesfrompoland.com/feed/", "en", listOf("notes from poland", "poland", "polska")),
        KnownFeed("Ars Technica", "https://arstechnica.com", "https://feeds.arstechnica.com/arstechnica/index", "en", listOf("ars technica", "technology", "science")),
        KnownFeed("Nature", "https://www.nature.com", "https://www.nature.com/nature.rss", "en", listOf("nature", "science", "journal")),
        KnownFeed("Quanta Magazine", "https://www.quantamagazine.org", "https://www.quantamagazine.org/feed/", "en", listOf("quanta", "mathematics", "physics", "biology", "science")),
        KnownFeed("Tagesschau", "https://www.tagesschau.de", "https://www.tagesschau.de/index~rss2.xml", "de", listOf("tagesschau", "ard", "deutschland", "nachrichten")),
        KnownFeed("Der Spiegel", "https://www.spiegel.de", "https://www.spiegel.de/schlagzeilen/index.rss", "de", listOf("spiegel", "deutschland", "nachrichten")),
        KnownFeed("Die Zeit", "https://www.zeit.de", "https://newsfeed.zeit.de/index", "de", listOf("zeit", "zeit online", "deutschland", "nachrichten")),
        KnownFeed("Süddeutsche Zeitung", "https://www.sueddeutsche.de", "https://rss.sueddeutsche.de/rss/Topthemen", "de", listOf("süddeutsche", "sueddeutsche", "sz", "deutschland", "nachrichten")),
        KnownFeed("Deutschlandfunk", "https://www.deutschlandfunk.de", "https://www.deutschlandfunk.de/nachrichten-100.rss", "de", listOf("deutschlandfunk", "dlf", "radio", "nachrichten")),
        KnownFeed("taz", "https://taz.de", "https://taz.de/!p4608;rss/", "de", listOf("taz", "tageszeitung", "deutschland", "nachrichten")),
        KnownFeed("Der Standard", "https://www.derstandard.at", "https://www.derstandard.at/rss", "de-AT", listOf("standard", "derstandard", "österreich", "austria", "nachrichten")),
        KnownFeed("heise online", "https://www.heise.de", "https://www.heise.de/rss/heise-atom.xml", "de", listOf("heise", "technik", "computer", "c't")),
        KnownFeed("NOS Nieuws", "https://nos.nl", "https://feeds.nos.nl/nosnieuwsalgemeen", "nl", listOf("nos", "nederland", "nieuws", "netherlands", "dutch")),
        KnownFeed("NRC", "https://www.nrc.nl", "https://www.nrc.nl/rss/", "nl", listOf("nrc", "nederland", "nieuws", "dutch")),
        KnownFeed("de Volkskrant", "https://www.volkskrant.nl", "https://www.volkskrant.nl/voorpagina/rss.xml", "nl", listOf("volkskrant", "nederland", "nieuws", "dutch")),
        KnownFeed("VRT NWS", "https://www.vrt.be/vrtnws/nl", "https://www.vrt.be/vrtnws/nl.rss.articles.xml", "nl-BE", listOf("vrt", "belgië", "belgie", "belgium", "vlaanderen", "nieuws")),
        KnownFeed("Le Monde", "https://www.lemonde.fr", "https://www.lemonde.fr/rss/une.xml", "fr", listOf("le monde", "lemonde", "france", "actualités")),
        KnownFeed("franceinfo", "https://www.francetvinfo.fr", "https://www.francetvinfo.fr/titres.rss", "fr", listOf("franceinfo", "france info", "france", "actualités")),
        KnownFeed("Libération", "https://www.liberation.fr", "https://www.liberation.fr/arc/outboundfeeds/rss-all/?outputType=xml", "fr", listOf("libération", "liberation", "libé", "france")),
        KnownFeed("RFI", "https://www.rfi.fr/fr", "https://www.rfi.fr/fr/rss", "fr", listOf("rfi", "radio france internationale", "afrique", "monde")),
        KnownFeed("El País", "https://elpais.com", "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada", "es", listOf("el país", "el pais", "elpais", "españa", "spain", "noticias")),
        KnownFeed("elDiario.es", "https://www.eldiario.es", "https://www.eldiario.es/rss/", "es", listOf("eldiario", "españa", "spain", "noticias")),
        KnownFeed("RTVE Noticias", "https://www.rtve.es/noticias", "https://api2.rtve.es/rss/temas_noticias.xml", "es", listOf("rtve", "españa", "spain", "noticias")),
        KnownFeed("BBC News Mundo", "https://www.bbc.com/mundo", "https://feeds.bbci.co.uk/mundo/rss.xml", "es", listOf("bbc mundo", "bbc", "noticias", "latinoamérica")),
        KnownFeed("Gazeta.pl Wiadomości", "https://wiadomosci.gazeta.pl", "https://rss.gazeta.pl/pub/rss/wiadomosci.xml", "pl", listOf("gazeta", "gazeta.pl", "polska", "poland", "wiadomości")),
        KnownFeed("TVN24", "https://tvn24.pl", "https://tvn24.pl/najnowsze.xml", "pl", listOf("tvn24", "polska", "poland", "wiadomości")),
        KnownFeed("Rzeczpospolita", "https://www.rp.pl", "https://www.rp.pl/rss_main", "pl", listOf("rzeczpospolita", "rp", "polska", "poland")),
        KnownFeed("OKO.press", "https://oko.press", "https://oko.press/feed", "pl", listOf("oko", "oko.press", "polska", "poland")),
        KnownFeed("Low-tech Magazine", "https://solar.lowtechmagazine.com", "https://solar.lowtechmagazine.com/index.xml", "en", listOf("low-tech", "low tech", "lowtech", "solar", "energy")),
        KnownFeed("Hundred Rabbits", "https://100r.co", "https://100r.co/links/rss.xml", "en", listOf("hundred rabbits", "100 rabbits", "100r", "sailing", "uxn")),
        KnownFeed("Emergence Magazine", "https://emergencemagazine.org", "https://emergencemagazine.org/feed/?post_type=essay", "en", listOf("emergence", "ecology", "nature", "culture")),
        KnownFeed("Orion Magazine", "https://orionmagazine.org", "https://orionmagazine.org/feed/?post_type=article", "en", listOf("orion", "nature", "culture", "environment")),
        KnownFeed("Aeon", "https://aeon.co", "https://aeon.co/feed.rss", "en", listOf("aeon", "essays", "philosophy", "ideas")),
        KnownFeed("Noema Magazine", "https://www.noemamag.com", "https://www.noemamag.com/feed/", "en", listOf("noema", "essays", "ideas")),
        KnownFeed("The Marginalian", "https://www.themarginalian.org", "https://www.themarginalian.org/feed/", "en", listOf("marginalian", "brain pickings", "maria popova", "books")),
        KnownFeed("The Public Domain Review", "https://publicdomainreview.org", "https://publicdomainreview.org/rss.xml", "en", listOf("public domain review", "history", "art", "books")),
        KnownFeed("Craig Mod", "https://craigmod.com", "https://craigmod.com/index.xml", "en", listOf("craig mod", "walking", "japan", "essays")),
        KnownFeed("Dark Mountain", "https://dark-mountain.net", "https://dark-mountain.net/feed/", "en", listOf("dark mountain", "uncivilisation", "ecology")),
        KnownFeed("Resilience", "https://www.resilience.org", "https://www.resilience.org/feed/", "en", listOf("resilience", "energy", "climate", "transition")),
        KnownFeed("Solarpunk Magazine", "https://solarpunkmagazine.com", "https://solarpunkmagazine.com/feed/", "en", listOf("solarpunk", "fiction", "climate")),
        KnownFeed("Reasons to be Cheerful", "https://reasonstobecheerful.world", "https://reasonstobecheerful.world/feed/", "en", listOf("reasons to be cheerful", "good news", "solutions")),
    )
