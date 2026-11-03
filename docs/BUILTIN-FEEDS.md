# Built-in feeds

The short list that Add feed matches a typed name against ("guardian", "low-tech"). It lives in
`app/src/main/java/com/wanderwildwood/kirinuki/model/known/KnownFeeds.kt`, is matched on the phone,
and nothing is sent anywhere while a name is typed. Prune or add here and there together; the
unit test `KnownFeedsTest` checks the list is whole (40 to 70 entries, no duplicates, https only,
at least three each in German, Dutch, French, Spanish and Polish).

## Check, 2026-11-03

Every feed address was fetched once with curl from the workstation, with the app's own user agent,
following redirects. Kept only if it answered 200 with RSS, RDF or Atom carrying at least one entry.

| Name | Lang | Feed | HTTP | Content type | Format | Entries |
|---|---|---|---|---|---|---|
| BBC News | en-GB | https://feeds.bbci.co.uk/news/rss.xml | 200 | text/xml; charset=utf-8 | rss | 40 |
| The Guardian | en-GB | https://www.theguardian.com/international/rss | 200 | text/xml; charset=UTF-8 | rss | 118 |
| The Guardian: World | en-GB | https://www.theguardian.com/world/rss | 200 | text/xml; charset=UTF-8 | rss | 45 |
| Al Jazeera English | en | https://www.aljazeera.com/xml/rss/all.xml | 200 | application/rss+xml; charset=UTF-8 | rss | 25 |
| NPR News | en-US | https://feeds.npr.org/1001/rss.xml | 200 | text/xml;charset=UTF-8 | rss | 10 |
| DW (English) | en | https://rss.dw.com/rdf/rss-en-all | 200 | text/xml; charset=UTF-8 | rdf:RDF | 124 |
| France 24 (English) | en | https://www.france24.com/en/rss | 200 | application/rss+xml; charset=utf-8 | rss | 24 |
| Le Monde in English | en | https://www.lemonde.fr/en/rss/une.xml | 200 | application/xml; charset=UTF-8 | rss | 14 |
| The New York Times | en-US | https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml | 200 | application/xml | rss | 20 |
| The Washington Post: World | en-US | https://www.washingtonpost.com/arcio/rss/category/world/ | 200 | text/xml; charset=utf-8 | rss | 10 |
| ABC News (Australia) | en-AU | https://www.abc.net.au/news/feed/51120/rss.xml | 200 | application/rss+xml | rss | 25 |
| CBC News | en-CA | https://www.cbc.ca/webfeed/rss/rss-topstories | 200 | application/xml;charset=UTF-8 | rss | 20 |
| RNZ News | en-NZ | https://www.rnz.co.nz/rss/national.xml | 200 | application/xml; charset=utf-8 | rss | 44 |
| RTÉ News | en-IE | https://www.rte.ie/feeds/rss/?index=/news/ | 200 | application/rss+xml; charset=utf-8 | rss | 60 |
| The Irish Times | en-IE | https://www.irishtimes.com/arc/outboundfeeds/feed-irish-news/?outputType=xml | 200 | application/xml; charset=utf-8 | rss | 100 |
| Euronews | en | https://www.euronews.com/rss | 200 | text/xml; charset=UTF-8 | rss | 50 |
| The Conversation | en | https://theconversation.com/global/articles.atom | 200 | application/atom+xml; charset=utf-8 | feed | 46 |
| ProPublica | en-US | https://feeds.propublica.org/propublica/main | 200 | application/xml; charset=UTF-8 | rss | 20 |
| The Kyiv Independent | en | https://kyivindependent.com/news-archive/rss/ | 200 | text/xml; charset=utf-8 | rss | 15 |
| Notes from Poland | en | https://notesfrompoland.com/feed/ | 200 | application/rss+xml; charset=UTF-8 | rss | 12 |
| Ars Technica | en | https://feeds.arstechnica.com/arstechnica/index | 200 | text/xml; charset=utf-8 | rss | 20 |
| Nature | en | https://www.nature.com/nature.rss | 200 | application/rss+xml; charset=utf-8 | rdf:RDF | 75 |
| Quanta Magazine | en | https://www.quantamagazine.org/feed/ | 200 | application/rss+xml; charset=UTF-8 | rss | 5 |
| Tagesschau | de | https://www.tagesschau.de/index~rss2.xml | 200 | application/xml;charset=UTF-8 | rss | 67 |
| Der Spiegel | de | https://www.spiegel.de/schlagzeilen/index.rss | 200 | application/rss+xml; charset=utf-8 | rss | 30 |
| Die Zeit | de | https://newsfeed.zeit.de/index | 200 | application/rss+xml; charset=utf-8 | rss | 15 |
| Süddeutsche Zeitung | de | https://rss.sueddeutsche.de/rss/Topthemen | 200 | application/xml; charset=utf-8 | rss | 15 |
| Deutschlandfunk | de | https://www.deutschlandfunk.de/nachrichten-100.rss | 200 | application/rss+xml;charset=UTF-8 | rss | 31 |
| taz | de | https://taz.de/!p4608;rss/ | 200 | text/xml;charset=utf-8 | rss | 50 |
| Der Standard | de-AT | https://www.derstandard.at/rss | 200 | application/xml; charset=utf-8 | rss | 157 |
| heise online | de | https://www.heise.de/rss/heise-atom.xml | 200 | application/atom+xml; charset=UTF-8 | feed | 156 |
| NOS Nieuws | nl | https://feeds.nos.nl/nosnieuwsalgemeen | 200 | text/xml; charset=utf-8 | rss | 20 |
| NRC | nl | https://www.nrc.nl/rss/ | 200 | application/rss+xml;charset=utf-8 | rss | 167 |
| de Volkskrant | nl | https://www.volkskrant.nl/voorpagina/rss.xml | 200 | application/rss+xml; charset=utf-8 | rss | 71 |
| VRT NWS | nl-BE | https://www.vrt.be/vrtnws/nl.rss.articles.xml | 200 | application/atom+xml;charset=utf-8 | feed | 50 |
| Le Monde | fr | https://www.lemonde.fr/rss/une.xml | 200 | application/xml; charset=UTF-8 | rss | 16 |
| franceinfo | fr | https://www.francetvinfo.fr/titres.rss | 200 | text/xml; charset=UTF-8 | rss | 31 |
| Libération | fr | https://www.liberation.fr/arc/outboundfeeds/rss-all/?outputType=xml | 200 | application/xml; charset=utf-8 | rss | 50 |
| RFI | fr | https://www.rfi.fr/fr/rss | 200 | application/rss+xml; charset=utf-8 | rss | 23 |
| El País | es | https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada | 200 | application/xml | rss | 150 |
| elDiario.es | es | https://www.eldiario.es/rss/ | 200 | text/xml; charset=UTF-8 | rss | 100 |
| RTVE Noticias | es | https://api2.rtve.es/rss/temas_noticias.xml | 200 | text/xml | rss | 40 |
| BBC News Mundo | es | https://feeds.bbci.co.uk/mundo/rss.xml | 200 | text/xml; charset=utf-8 | rss | 35 |
| Gazeta.pl Wiadomości | pl | https://rss.gazeta.pl/pub/rss/wiadomosci.xml | 200 | application/xml | rss | 30 |
| TVN24 | pl | https://tvn24.pl/najnowsze.xml | 200 | text/xml; charset=utf-8 | rss | 26 |
| Rzeczpospolita | pl | https://www.rp.pl/rss_main | 200 | xml | rss | 20 |
| OKO.press | pl | https://oko.press/feed | 200 | text/xml; charset=utf-8 | rss | 126 |
| Low-tech Magazine | en | https://solar.lowtechmagazine.com/index.xml | 200 | text/xml; charset=UTF-8 | rss | 3 |
| Hundred Rabbits | en | https://100r.co/links/rss.xml | 200 | application/xml | rss | 92 |
| Emergence Magazine | en | https://emergencemagazine.org/feed/?post_type=essay | 200 | application/rss+xml; charset=UTF-8 | rss | 10 |
| Orion Magazine | en | https://orionmagazine.org/feed/?post_type=article | 200 | application/rss+xml; charset=UTF-8 | rss | 24 |
| Aeon | en | https://aeon.co/feed.rss | 200 | application/rss+xml; charset=utf-8 | rss | 20 |
| Noema Magazine | en | https://www.noemamag.com/feed/ | 200 | application/rss+xml; charset=UTF-8 | rss | 10 |
| The Marginalian | en | https://www.themarginalian.org/feed/ | 200 | application/rss+xml; charset=UTF-8 | rss | 20 |
| The Public Domain Review | en | https://publicdomainreview.org/rss.xml | 200 | application/xml | rss | 100 |
| Craig Mod | en | https://craigmod.com/index.xml | 200 | application/xml | rss | 38 |
| Dark Mountain | en | https://dark-mountain.net/feed/ | 200 | application/rss+xml; charset=UTF-8 | rss | 12 |
| Resilience | en | https://www.resilience.org/feed/ | 200 | application/rss+xml; charset=UTF-8 | rss | 11 |
| Solarpunk Magazine | en | https://solarpunkmagazine.com/feed/ | 200 | application/rss+xml; charset=UTF-8 | rss | 10 |
| Reasons to be Cheerful | en | https://reasonstobecheerful.world/feed/ | 200 | application/rss+xml; charset=UTF-8 | rss | 10 |

60 kept. Low-tech Magazine carries only its latest three posts; that is the site's own choice.
Gazeta.pl's feed is ISO-8859-2, which the feed parser reads from the XML declaration.

## Dropped or replaced

| Source | Address tried | What it did |
|---|---|---|
| The Washington Post | https://feeds.washingtonpost.com/rss/world | 502; replaced by the arcio address above, which works |
| Politico Europe | https://www.politico.eu/feed/ (and /rss) | 403 to a feed reader |
| Associated Press | https://apnews.com/index.rss | 403 |
| Reuters | https://www.reuters.com/world/rss | 401; Reuters has no public feed now |
| De Correspondent | https://decorrespondent.nl/feed (and /rss) | 404, or an HTML page; the site names no feed |
| Polskie Radio 24 | https://polskieradio24.pl/rss | an HTML page, not a feed; the site names none |
| Emergence Magazine | https://emergencemagazine.org/feed/ | a valid feed with no entries; replaced by ?post_type=essay (10 entries) |
| Orion Magazine | https://orionmagazine.org/feed/ | a valid feed with no entries; replaced by ?post_type=article (24 entries) |
| Low-tech Magazine | https://solar.lowtechmagazine.com/posts/index.xml | works, but the site's own page names /index.xml, so that is listed |

Checked and working but left out to keep the list near 60 (any can go back in): BBC News World,
The Guardian Environment/Science/Books, The Independent, Sky News, Euractiv, DutchNews.nl, The Local
(Germany), DW (Deutsch), NZZ, SRF, Trouw, La Vanguardia, Polsat News, Bankier.pl, la Repubblica,
ANSA, SVT, Yle, The Atlantic, The New Yorker, London Review of Books, Wired, Hacker News, Atlas
Obscura, Kottke, Futurism, Grist, YES! Magazine, Positive News, Mongabay, Front Porch Republic, Low
Impact, RNZ is in.

## To decide before release

- Emergence and Orion are listed by a filtered WordPress address (`?post_type=`), because their
  plain feeds were empty on the day. If either site fixes its main feed, switch back.
- The Washington Post address is an Arc publishing endpoint rather than a documented feed.

