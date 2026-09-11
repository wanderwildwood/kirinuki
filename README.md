# 切抜 kirinuki — Clippings

A reader for the [Mudita Kompakt](https://mudita.com/products/kompakt/), built for its
E Ink screen: text on paper, no images, and everything kept on the phone. Feeds, and the
smolnet — Gemini, gopher and Spartan.

*Kirinuki* is 切抜 — a cutting, the thing you take out of a newspaper with scissors and
keep. 切 (to cut), 抜 (to take out). It is what this is: pieces cut from somewhere else and
kept, most of them only as much as the paper gave you.

Fork of [Feeder](https://github.com/spacecowboy/Feeder) by
[Jonas Kalderstam](https://github.com/spacecowboy), pared to a reader and rebuilt on
Mudita's MMD design system.

| | |
|---|---|
| ![The feeds](screenshots/1-feeds.png) | ![What is in one feed](screenshots/2-articles.png) |
| ![A cutting](screenshots/3-article.png) | ![Settings](screenshots/4-settings.png) |

## What it is

Three screens. The feeds, what is in one, and the thing itself.

- **No account, and no server of ours.** Feeds are fetched straight from the sites that
  publish them. Nothing is registered and nothing phones home.
- **Read with the radio off.** Everything is fetched on wifi and stored; the reading
  afterwards needs nothing.
- **Text only.** No images, no embedded web view. A photograph at sixteen greys costs a
  redraw to say less than the headline did, and a page that drags in a site's own CSS is
  unreadable on this screen.
- **OPML in and out.** How you arrive with feeds, and how you leave with them.
- **Volume keys page the article**, because dragging a finger down E Ink is a poor way to
  read.

## The smolnet

`gemini://`, `gopher://` and `spartan://` open in the app and read like anything else.
Nothing else on the phone opens them, so Clippings offers to — a capsule link tapped or
shared anywhere arrives here.

- **Subscribe to a gemlog.** Gemini has no feed format; the convention is that a page *is*
  a feed when its links carry dates. Subscribe to one and its entries arrive like any
  other feed's, with the text fetched at sync time, so a gemlog reads with the radio off.
- **The tour.** A page you cannot reach is the page worth keeping. That screen offers to
  put it on the tour; the next sync fetches it; it is there to read whenever. A capsule
  that was down when you queued something is not a reason to lose it.
- **Certificates.** Gemini has no authorities, so a capsule is trusted the first time it
  is seen and checked against that afterwards. A host presenting a different certificate
  before the known one has expired is refused, and the screen says why rather than
  quietly carrying on.
- **Gopher text is fitted rather than reflowed** — it was written against eighty columns,
  and reflowing it destroys tables and art. It shrinks until it fits, and scrolls sideways
  rather than shrinking past the point of being readable.

## What it will not do for you

Most feeds carry a summary rather than the whole article. Clippings will fetch the full
text from the page and keep it beside the summary — and that fetch is genuinely unreliable,
because it depends on a stranger's markup. When it fails, the screen says so and gives you
back the summary. It does not show you half an article as though it were whole.

## Installing it

The APK is on the [releases page](https://github.com/wanderwildwood/kirinuki/releases/latest).

```sh
curl -LO https://github.com/wanderwildwood/kirinuki/releases/latest/download/kirinuki.apk
curl -LO https://github.com/wanderwildwood/kirinuki/releases/latest/download/kirinuki.apk.sha256
sha256sum -c kirinuki.apk.sha256
adb install kirinuki.apk
```

Or point [Obtainium](https://github.com/ImranR98/Obtainium) at this repository and let it
track releases. Choose `kirinuki.apk` rather than `kirinuki-v*.apk` when it asks: the fixed
name keeps working after the next release.

Android 12 (API 31) or newer.

## Building it

```sh
./gradlew :app:assembleDebug     # or :app:assembleRelease
```

MMD comes from Mudita's own Artifactory repository, which is already declared in
`settings.gradle.kts`.

## What was taken out of Feeder

Feeder is a much larger app, and most of the work here was removal: the OpenAI integration,
the Bergamot translation engine, the device-to-device sync chain, the home screen widget,
read-aloud, the podcast player, image loading, and the bundled fonts. The reader's own
machinery — the feed parsers, the full-text extraction, the HTML-to-text pipeline, the
database — is Feeder's and is the reason this exists at all.

## Credits

- Built on [Feeder](https://github.com/spacecowboy/Feeder) by Jonas Kalderstam (GPL-3.0).
- The tour is [Offpunk](https://sr.ht/~lioploum/offpunk/)'s idea, by Ploum. No code is
  taken from it: Offpunk is AGPL-3.0 and this is GPL-3.0, so the protocols here are
  written against their specifications.
- Full-text extraction via [Readability4J](https://github.com/dankito/Readability4J).
- UI built with Mudita's [MMD](https://github.com/mudita/MMD) component library for Kompakt.

## Licence

GPL-3.0, the same as upstream Feeder. See [LICENSE](LICENSE).
