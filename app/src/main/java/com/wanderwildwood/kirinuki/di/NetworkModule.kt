package com.wanderwildwood.kirinuki.di

import com.wanderwildwood.kirinuki.model.FeedParser
import com.wanderwildwood.kirinuki.model.FullTextParser
import com.wanderwildwood.kirinuki.model.RssLocalSync
import com.wanderwildwood.kirinuki.net.gemini.GeminiClient
import com.wanderwildwood.kirinuki.net.gopher.GopherClient
import com.wanderwildwood.kirinuki.net.gemini.KnownHosts
import com.wanderwildwood.kirinuki.util.FilePathProvider
import com.nononsenseapps.jsonfeed.Feed
import com.nononsenseapps.jsonfeed.JsonFeedParser
import com.nononsenseapps.jsonfeed.feedAdapter
import com.squareup.moshi.JsonAdapter
import okhttp3.OkHttpClient
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.provider
import org.kodein.di.singleton

val networkModule =
    DI.Module(name = "network") {
        // Parsers can carry state so safer to use providers
        bind<JsonAdapter<Feed>>() with provider { feedAdapter() }
        bind<JsonFeedParser>() with provider { JsonFeedParser(instance<OkHttpClient>(), instance()) }
        bind<FeedParser>() with provider { FeedParser(di) }
        // These don't have state issues
        bind<RssLocalSync>() with singleton { RssLocalSync(di) }
        bind<FullTextParser>() with singleton { FullTextParser(di) }

        // ⚠ Gemini shares nothing with the OkHttpClient above, and must not: its trust
        // manager accepts certificates no authority signed. See TrustOnFirstUse.
        bind<KnownHosts>() with
            singleton {
                KnownHosts(instance<FilePathProvider>().filesDir.resolve("gemini-known-hosts"))
            }
        bind<GeminiClient>() with singleton { GeminiClient(instance()) }
        // Gopher has no transport security at all -- there is nothing to configure and
        // nothing to share with anything else.
        bind<GopherClient>() with singleton { GopherClient() }
    }
