# Sync with a feed server

Feeds fetches every feed itself and keeps read state on the phone. This adds an optional,
general sync with a feed server that speaks the **Google Reader API**, the dialect most
self-hosted and many hosted feed services share. It is off until someone signs in, in
Settings, Sync with a feed server.

What it syncs, both ways: subscriptions, folders (Google Reader "labels"), read and unread,
starred ("saved" in some servers). While it is on, the articles of every feed on the server
come from the server, not from each site.

Sign-in is typed only: a server address, a login name and an API password. Nothing has to
be registered with any service, by the user or by the developer, and no browser is involved.
(Why typed, not a browser: see Recipes' docs/NEXTCLOUD-LOGIN.md; on e-ink phones the browser
hand-over is where sign-in failed.)

## The API, as FreshRSS and Miniflux speak it

Sources: FreshRSS's developer page on the Google Reader API
(freshrss.github.io/FreshRSS/en/developers/06_GoogleReader_API.html), Miniflux's
docs/google_reader and its handler (github.com/miniflux/v2, internal/googlereader/handler.go),
and Read You's client (github.com/Ashinch/ReadYou, GPL-3.0), which is known to work against
both. The old Google documentation is gone; these are the de-facto reference.

**Where it lives.** FreshRSS: `https://host/api/greader.php/` (the web address plus
`/api/greader.php`). Miniflux: the web address itself (`https://host/`). Feeds tries the
address as typed first, then with `/api/greader.php` added, so either form works.

**Credentials.** Neither server takes the web password. FreshRSS: enable API access in
Administration, Authentication; then Profile, API management sets the user's API password.
Miniflux: Settings, Integrations, Google Reader: switch on, choose a user name and password.

**Login.** `POST accounts/ClientLogin` with form `Email=<login>&Passwd=<api password>`.
Success: 200 and text lines `SID=…`, `LSID=…`, `Auth=<token>` (JSON with the same keys when
`output=json` is honoured). A wrong password: 401 (FreshRSS adds `Error=BadAuthentication`).
Every later request carries `Authorization: GoogleLogin auth=<token>`.

**Write token.** `GET reader/api/0/token` returns a short token; every POST sends it as form
field `T`. A stale one is refused with 401 and header `X-Reader-Google-Bad-Token: true`.

**Endpoints used** (all with `output=json`):

| What | Request | Notes |
|---|---|---|
| subscriptions | `GET reader/api/0/subscription/list` | `subscriptions[]`: `id` (`feed/12`), `title`, `url`, `htmlUrl`, `categories[]` (`id` `user/-/label/Name`, `label`) |
| folders | `GET reader/api/0/tag/list` | `tags[]`: label streams plus state streams |
| ids | `GET reader/api/0/stream/items/ids?s=…&n=…&c=…&xt=…&ot=…` | `itemRefs[].id` in short form; `continuation` while more remain |
| articles | `POST reader/api/0/stream/items/contents` form `i=<id>` repeated | `items[]`: `id` long form, `title`, `published` (s), `crawlTimeMsec`, `canonical[]/alternate[].href`, `summary.content` or `content.content`, `origin.streamId`, `author`, `enclosure[]` |
| read/unread | `POST reader/api/0/edit-tag` form `i=…&a=user/-/state/com.google/read` (or `r=` to unread) | many `i` per request |
| starred | same, tag `user/-/state/com.google/starred` | |
| subscribe | `POST reader/api/0/subscription/quickadd` form `quickadd=<feed url>` | answers `streamId` |
| folder / unsubscribe | `POST reader/api/0/subscription/edit` form `ac=edit&s=feed/12&a=user/-/label/New&r=user/-/label/Old`, or `ac=unsubscribe&s=feed/12` | Miniflux ignores `r` (a feed is always in one category) |
| mark all read | `POST reader/api/0/mark-all-as-read` form `s=<stream>&ts=<µs>` | not used: Feeds sends the ids it marked instead, which every server takes |

Streams: `user/-/state/com.google/reading-list` (everything), `…/read`, `…/starred`,
`feed/<id>`, `user/-/label/<name>`. Parameters on ids: `s` stream, `xt` exclude a state
(`xt=…/read` is "unread"), `ot` only newer than (seconds), `n` page size, `c` continuation.

**Item ids** come in two spellings: long `tag:google.com,2005:reader/item/<16 hex>` and
short signed decimal. ids answer short, contents answer long, edit-tag takes either. Feeds
stores the long form. Conversion and its table come from Read You (GReaderIds.kt).

## Inoreader: left out

Inoreader's API requires a registered application. Its own pages say so: "Before you can use
the API, you must first register your application", the App ID and App Key "must be sent in
every API request", users should not be asked to enter them, and new apps should use OAuth 2.0
rather than ClientLogin (inoreader.com/developers/app-auth). API access is granted to publicly
available apps; other uses need a paid plan. Rate limits are 100 requests a day per zone
(reads, writes) on the Pro plan default (inoreader.com/developers/rate-limiting), which a
phone syncing on every refresh would exhaust.

Decided 2 Nov: no developer registration of any kind, so no App ID or App Key
fields and no OAuth client. **Feeds does not support Inoreader.** A user who wants their
Inoreader subscriptions on the phone can export OPML from Inoreader and import it in Feeds
(feeds only, not read state), or move to a server that takes typed credentials.

Only FreshRSS and Miniflux were designed against. Any other server that speaks the same API
and accepts ClientLogin with a typed password may work; that is not checked or promised.

## Upstream Feeder's own sync

Feeder (and so this fork) carries "Feeder sync": device-to-device sync through Feeder's own
server, end-to-end encrypted with a shared key (archmodel/SyncRemoteStore.kt,
crypto/SyncKey.kt, the `sync_remote`, `read_status_synced`, `remote_read_mark`, `remote_feed`
and `sync_device` tables). This fork removed its network half; the tables and a few
repository calls remain and do nothing. Its model (per-device encrypted read marks posted to a
relay) does not map onto a Google Reader server, so none of it is reused. Its tables are left
as they are.

## Design

Code: `sync/greader/` (API, ids, merge, the sync itself, the Room-backed store),
`sync/ServerSync.kt` (account, sealed password, the hook the refresh calls),
`ui/settings/ServerSyncScreen.kt`. Two new tables (migration 40 to 41): `greader_feeds`
(feed id, server stream id, last agreed folder) and `greader_items` (article id, server item
id, last agreed read and starred).

**When.** Every refresh: pull-to-refresh, the refresh button and the background refresh all
run `RssLocalSync.syncFeeds`, which syncs with the server first and then fetches only the
feeds the server does not have (gemlogs, phlogs, saved pages, any feed the server refused).

**Order of one sync.**
1. Feeds deleted on the phone are unsubscribed on the server; folders changed on the phone are
   sent. (A deleted feed's row in `greader_feeds` has no foreign key on purpose: the row left
   behind is the record of the deletion.)
2. The server's subscription list is applied: the server wins for subscriptions. New ones are
   added, ones gone from the server are removed (starred articles are kept, as when a feed is
   deleted by hand), names and folders follow the server.
3. Feeds on the phone that the server has never seen are subscribed to there once (with their
   folder), so signing in loses nothing. One the server refuses, or one it cannot fetch
   (gemini, gopher, saved pages), stays on the phone and is fetched from its site as before.
4. Read and starred changes made on the phone since the last sync are sent (edit-tag).
5. The server's unread and starred id lists are read in full (paged by continuation) and
   applied to articles the phone did not change.
6. Articles the phone lacks are fetched from the server: every starred one, the newest 1000
   unread, and anything new since the last sync (the first sync looks back 14 days). An article
   the phone already had from fetching the site itself (same feed, same link) is taken over,
   not doubled, and what either side had read or starred stays so.
7. Read, unstarred articles beyond the per-feed limit (Settings' maximum per feed) are pruned
   on the phone; unread and starred ones stay until they are read or unstarred.

**Conflicts.** Subscriptions: the server wins. Read and starred: each article keeps a base,
the state both sides last agreed on. A side that differs from the base has changed it since.
The phone's changes are sent first, then the server's are applied where the phone made none.
Read and starred are each one yes/no, so from one base both sides can only ever change to the
same value: there is no case where the two disagree, and whichever changed last is what both
hold (GReaderMerge.kt).

**Offline queue.** The base is the queue: a change made with no network stays a difference
from the base until a sync reaches the server, however many syncs fail first. Nothing is held
in memory, nothing is lost by a restart.

**Errors.** Test and Sign in say which of these it was: nothing answered (unreachable),
the server refused the login (wrong password), something answered but not as a Google
Reader-style API (wrong address), an http:// address on the open internet (refused before
anything is sent), the server's own error code. During a refresh the same failure is one
line on the pull, under the server's host name.

**Credentials.** The API password is sealed with an AES key generated in the Android Keystore
(the same `Secrets` as Recipes, Gallery and Music Box, under its own alias). The address and
login name are in the app's private preferences. The password is held in memory only while a
sync or a test runs. Requests go through their own OkHttp client, not the app's shared one
(which keeps an HTTP cache and, in debug builds, logs request headers).

**http.** Plain http is accepted only for addresses on the phone or the home network (private
and link-local ranges, `.local`, `.lan`, `.home`, `.home.arpa`, `.internal`) and Tailscale's 100.64.0.0/10. Any
other http:// address is refused with a message, before the password is sent.

**Stop syncing** forgets the address, login, password and both link tables. Every feed and
article stays; from the next refresh the feeds are fetched from their sites again.

## What came from where

- Read You (GPL-3.0, Ashinch and contributors): the item id table and conversion, the request
  shapes (ClientLogin, token, ids, contents, edit-tag, quickadd, subscription/edit) and the
  outline of the sync order (unread and starred id sets first, then contents of what is
  missing). Rewritten for OkHttp, Moshi and this app's database; credited in Licences.
- Recipes (same author): `Secrets` (Keystore-sealed password).
- No code from Feeder's own sync.

## Live check, 2 Nov 2026

FreshRSS 1.30.4 and Miniflux 2.3.5 (official images, on the build box, bound to it only), a
made-up user, made-up feeds served by a local nginx, the debug build on an API 31 emulator
signing in with typed credentials over the home-network http rule (10.0.2.2). Seen on both:
sign-in, first sync (feeds, folders, articles, read and starred), read and starred from the
phone reaching the server and from the server (its own API or web API) reaching the phone, a
feed added on the server appearing on the phone, a feed removed on the server leaving it, a
feed added on the phone subscribed on the server in its folder, a feed removed on the phone
unsubscribed there, and switching servers (Stop syncing, sign in to the other): the phone's
read and starred articles were taken over by link and sent. Seen on FreshRSS: the wrong
password and nothing-answering messages. Miniflux answers a wrong password with 401 as well.

Server set-up notes, for whoever repeats it: FreshRSS and Miniflux both refuse to fetch feeds
on private addresses by default (FreshRSS `internal_host_allowlist` with `host:port` entries,
Miniflux `FETCHER_ALLOW_PRIVATE_NETWORKS=1`). Miniflux does not answer stream/contents (only
ids plus items/contents, which is what Feeds uses).

## Not done / open

- Every feed on these servers sits in a category, so the phone shows the server's default one
  as a folder: "Uncategorized" on FreshRSS, "All" on Miniflux. Server wins, so it stays.
- The Room migration 40 to 41 was checked against the generated schema, not run on a phone
  holding a version 40 database.
- Renaming or deleting a folder as a whole is not sent (a folder exists on the phone only as
  a word feeds share; moving each feed is sent).
- A feed renamed on the phone keeps its name on the phone only (Feeds' own custom title).
- No per-feed "fetch from site instead" switch while sync is on.
