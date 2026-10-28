# Feeds — Privacy Policy

Version 0.5.10. Last reviewed 23 October 2026.

## The short version

The developer collects nothing about you. There is no account, no sign-up, no analytics, no advertising and no crash reporting, and no server belonging to the developer that the app talks to. What the app stores, it stores on your phone. The app contacts the servers of the feeds, podcasts, pages and capsules you add, directly, and nothing else; the hosts are listed below.

## Who is responsible

wander wildwood, the author of this version of the app. Contact: email@wanderthe.dev

Feeds is a fork of Feeder by Jonas Kalderstam (GPL-3.0), reduced to a reader and rebuilt on Mudita's public MMD library. Feeder's other services are not part of it.

Because no personal data is transmitted to or held by the developer, there is no data controller relationship for the developer to administer. Where the app processes personal data, that processing happens on your own device, under your control.

## What the app stores, and where

In the app's own private storage on the phone: the feeds and capsules you subscribe to (their addresses, and the folder name you file a feed under), read and saved state, downloaded article text and pages you saved, podcast listening positions, the certificate fingerprint of each Gemini capsule you have visited (shown in Settings, Capsule certificates, where you can forget one), and settings. Downloaded podcast episodes are kept in the app's own external files folder, fetched by Android's download service.

The app allows Android's backup (`allowBackup="true"`) and sets no exclusions of its own, so on a phone with Android or Google backup switched on, Android may copy this storage into that backup. The developer holds no backup and cannot see yours. Uninstalling the app deletes what is in its private storage. OPML import and export read and write only the file you pick.

## Permissions, and what each is for

From `app/src/main/AndroidManifest.xml`:

- `INTERNET`: to fetch feeds, articles, podcasts and capsules.
- `ACCESS_NETWORK_STATE`: to fetch only on Wi-Fi when you ask for that.
- `POST_NOTIFICATIONS`: to say when a feed has something new, and for the player.
- `RECEIVE_BOOT_COMPLETED` and `RUN_USER_INITIATED_JOBS`: to keep the sync schedule across a restart and run a sync you start.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: so a podcast keeps playing with the screen off.
- `WAKE_LOCK`: so a sync or a podcast is not cut off while the screen is dark.
- `READ_EXTERNAL_STORAGE` (Android 9 and below) and `WRITE_EXTERNAL_STORAGE` (Android 10 and below): for OPML import and export. The Kompakt runs Android 12, so on it neither is ever asked for.

The store also lists `com.wanderwildwood.kirinuki.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` because it reads the APK. AndroidX declares it for its own internal receivers; it is signature-level and namespaced to this app, so nothing else can hold it and it grants no access to anything on the phone.

The manifest also lets the app see whether anything on the phone opens web pages, a barcode scanner, a text-to-speech engine, or other apps' text actions, so it can offer them. It reads nothing from them. Read aloud hands the article's text, a sentence at a time, to the phone's own text-to-speech engine, which speaks it; Feeds sends it nowhere else, and what the engine does with it is that engine's to say. Plain http is permitted (`usesCleartextTraffic="true"`), because some feeds are only served that way; anything fetched over http can be read by others on the network.

## Who else receives anything

Yes, necessarily. The app fetches each feed, page, episode or capsule directly from the server that publishes it, so those operators see the request: your IP address, the address asked for, and the app's name and version in the user agent. Nothing is routed through the developer, and no analytics, advertising or crash-reporting service is contacted.

Hosts contacted: only the ones you add or follow, and nothing at a fixed address.

- **Web feeds, saved pages and full article text**: over https or http, from the site you subscribed to or the page you asked to save, or an address you share into the app. When a feed's own page is fetched for its full text, that page's server sees the request.
- **Podcast episodes**: from the address in the feed, streamed or downloaded.
- **Gemini, gopher and Spartan capsules**: by direct connection to the capsule's host and port, for an address you open or a gemlog you subscribe to. Gemini connections are checked against the certificate fingerprint the app remembered from the first visit.
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
