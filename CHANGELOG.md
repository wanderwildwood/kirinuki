# Changelog

## 0.2.0

Gemini, gopher and Spartan, and a tour.

- **Capsules.** `gemini://`, `gopher://` and `spartan://` open in the app and read like
  anything else — nothing else on the phone opens them, so Clippings offers to. A capsule
  address typed into Add feed offers **Read it** as well as **Subscribe**, because an
  address is as likely to be a page as a gemlog.
- **Gemlogs as feeds.** Gemini has no feed format; the convention is that a page is a feed
  when its links are dated. Subscribe to one and its entries arrive like any other feed's,
  with the text fetched at sync time so a gemlog reads with the radio off.
- **The tour.** A page you cannot reach is the page worth keeping: that screen offers to
  put it on the tour, the next sync fetches it, and it is there to read whenever. Failures
  stay queued rather than being dropped.
- **Certificates.** Gemini has no authorities, so a capsule is trusted the first time and
  checked against that afterwards. A host presenting a different certificate before the
  known one expires is refused, and the screen says why.
- Gopher text is fitted rather than reflowed: monospace, shrunk until it fits, scrolling
  sideways rather than shrinking past readable. Reflowing destroys tables and art.

Fixed: returning to a screen could crash the app, because its view model was scoped to the
activity rather than to where you had navigated.

## 0.1.0

First release. A feed reader for the Kompakt, forked from Feeder 2.23.1 and pared
down to three screens.

- Feeds, the cuttings in one feed, and the cutting itself, on Mudita's MMD.
- OPML import and export.
- Full text fetched from the page where the feed carries only a summary — and the
  screen says which of the two it is showing.
- Volume keys page the article.
- Text only: no images, no embedded web view.

Removed from upstream: the OpenAI integration, the Bergamot translation engine,
device-to-device sync, the home screen widget, read-aloud, the podcast player, image
loading, user fonts, and the first-run subscription to upstream's own news feed.
