# Feeds — Privacy Policy

Version 0.5.14. Last reviewed 29 October 2026.

## The short version

The developer collects nothing about you. There is no account with the developer, no sign-up, no analytics, no advertising and no crash reporting, and no server belonging to the developer that the app talks to. What the app stores, it stores on your phone. The app contacts the servers of the feeds, podcasts, pages and capsules you add, directly, and, only if you sign in to one in Settings, the one feed server whose address you typed; nothing else. The hosts are listed below.

## Who is responsible

wander wildwood, the author of this version of the app. Contact: email@wanderthe.dev

Feeds is a fork of Feeder by Jonas Kalderstam (GPL-3.0), reduced to a reader and rebuilt on Mudita's public MMD library. Feeder's other services are not part of it.

Because no personal data is transmitted to or held by the developer, there is no data controller relationship for the developer to administer. Where the app processes personal data, that processing happens on your own device, under your control.

## What the app stores, and where

In the app's own private storage on the phone: the feeds and capsules you subscribe to (their addresses, and the folder name you file a feed under), read and saved state, downloaded article text and pages you saved, podcast listening positions, the feed server's address, your login name and API password if you sign in to one (the password sealed with a key in the phone's Android Keystore, which never leaves the phone), which feeds and articles came from that server, the certificate fingerprint of each Gemini capsule you have visited (shown in Settings, Capsule certificates, where you can forget one), and settings. Downloaded podcast episodes are kept in the app's own external files folder, fetched by Android's download service.

The app allows Android's backup (`allowBackup="true"`) and sets no exclusions of its own, so on a phone with Android or Google backup switched on, Android may copy this storage into that backup. The developer holds no backup and cannot see yours. Uninstalling the app deletes what is in its private storage. OPML import and export read and write only the file you pick.

## Permissions, and what each is for

From `app/src/main/AndroidManifest.xml`:

- `INTERNET`: to fetch feeds, articles, podcasts and capsules.
- `ACCESS_NETWORK_STATE`: to fetch only on Wi-Fi when you ask for that.
- `POST_NOTIFICATIONS`: to say when a feed has something new, and for the player.
- `RECEIVE_BOOT_COMPLETED` and `RUN_USER_INITIATED_JOBS`: to keep the sync schedule across a restart and run a sync you start.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: so a podcast keeps playing with the screen off.
- `WAKE_LOCK`: so a sync or a podcast is not cut off while the screen is dark.
- `CAMERA`: to read a QR code holding a feed's, site's or story's address, and for nothing else. It is asked for only when you press Scan a QR code, and the camera is held only while the scanner is open. The picture is read on the phone (with ZXing) for the code and nothing else: no frame is kept, saved or sent anywhere, and the camera is let go when the scanner closes. Refusing it leaves everything else in the app as it was; the address can still be typed.
- `READ_EXTERNAL_STORAGE` (Android 9 and below) and `WRITE_EXTERNAL_STORAGE` (Android 10 and below): for OPML import and export. The Kompakt runs Android 12, so on it neither is ever asked for.

The store also lists `com.wanderwildwood.kirinuki.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` because it reads the APK. AndroidX declares it for its own internal receivers; it is signature-level and namespaced to this app, so nothing else can hold it and it grants no access to anything on the phone.

The manifest also lets the app see whether anything on the phone opens web pages, a text-to-speech engine, or other apps' text actions, so it can offer them. It reads nothing from them. Read aloud hands the article's text, a sentence at a time, to the phone's own text-to-speech engine, which speaks it; Feeds sends it nowhere else, and what the engine does with it is that engine's to say. Plain http is permitted; see "Plain http (cleartext traffic)" below.

### Plain http (cleartext traffic)

The manifest sets `usesCleartextTraffic="true"`, and Android scanners flag it. It is on on purpose: some feeds, and many older sites, are only served over plain http, and gopher is a bare, unencrypted socket by design. Feeds served over plain http are fetched as the site serves them, with no encryption, so anyone on the same network can read the feed text on its way, and could alter it. Only the feed or page you asked for is requested, with the app's name and version as the user agent; nothing else about you or your phone is sent. An address that starts with https:// is always fetched encrypted, Gemini capsules are encrypted and pinned to the certificate seen on the first visit. Gopher and Spartan, like plain http, are not encrypted.

## Licences

Settings, About, Licences shows the licence of the app (GNU General Public License, version 3, in full), the open-source libraries it is built on and the licence of each, and a link to the source code. The terms are also in About, in the app, as two short paragraphs.

## Who else receives anything

Yes, necessarily. The app fetches each feed, page, episode or capsule directly from the server that publishes it, so those operators see the request: your IP address, the address asked for, and the app's name and version in the user agent. Nothing is routed through the developer, and no analytics, advertising or crash-reporting service is contacted.

Hosts contacted: only the ones you add or follow, and nothing at a fixed address.

- **A feed server, only if you sign in to one** (Settings, Sync with a feed server; off by default): the server at the address you typed, and no other. The app sends it your login name and API password (to sign in), the addresses and folders of your feeds, and which articles you have read or starred; it receives your subscriptions and their articles from it. While sync is on, the feeds that server has are fetched from it instead of from their sites. An `http://` address is accepted only on your own network (private address ranges, `.local`, `.lan`, `.home`, `.home.arpa`, `.internal`, Tailscale); anything else must be `https://`. Stop syncing forgets the address, login and password.

- **Web feeds, saved pages and full article text**: over https or http, from the site you subscribed to or the page you asked to save, or an address you share into the app. When a feed's own page is fetched for its full text, that page's server sees the request.
- **Podcast episodes**: from the address in the feed, streamed or downloaded.
- **Gemini, gopher and Spartan capsules**: by direct connection to the capsule's host and port, for an address you open or a gemlog you subscribe to. Gemini connections are checked against the certificate fingerprint the app remembered from the first visit.
- **A site's page, when you add it by its address**: an address shared into the app or scanned from a QR code is fetched once as soon as it arrives, and a typed one when you press Subscribe, to see whether it is a feed, a page that links a feed, or a single story. A linked feed is then fetched like any other; a story is fetched again only if you choose Read this page, and is then shown and not stored unless you keep it.
- **Finding a feed by name**: a name typed into Add feed is matched against a short list of sources built into the app, on the phone. Nothing is sent while you type; only the feed you then subscribe to is fetched.
- **YouTube**: only when the address you add is a YouTube channel page. That page is fetched to find the channel's public feed (`www.youtube.com/feeds/videos.xml`), which is then fetched like any other feed.

In About, "feed the llamas" opens a donation page (square.link) and wanderthe.dev opens the author's website, in your browser and only when you press them. The app does not fetch either page and learns nothing about whether you went.

No data is sold, rented, or shared for advertising. There is no profiling, no personalization, and no automated decision-making about you.

## Legal basis (EU / EEA and UK)

Processing on the device is carried out for your own purposes and at your own direction. Where the app contacts an outside service, that request is made at your request and is necessary to provide the feature you asked for (Article 6(1)(b) GDPR); that operator is an independent controller for what it then does, under its own policy.

## Your rights

Under the GDPR and UK GDPR you have rights of access, rectification, erasure, restriction, portability and objection. Under the CCPA/CPRA and comparable North American laws you have rights to know, delete, correct and opt out of sale or sharing.

The developer holds no data about you, so there is nothing to request from them, and nothing has ever been sold or shared. You can exercise every one of these rights directly: the data is on your phone, and OPML export takes your subscriptions with you. Clear it in the app or in Android's app settings, or uninstall to remove all of it at once.

## Children

The app is suitable for all ages and does not knowingly collect anything from anyone, of any age, because the developer does not collect anything.

## Changes

If a later version changes what the app stores or which hosts it contacts, this policy is updated in the same release and the change is described in the release notes. The source is public, so any claim here can be checked against the code: <https://github.com/wanderwildwood/kirinuki>. GitHub reports its licence as GPL-3.0.
