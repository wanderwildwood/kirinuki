package com.wanderwildwood.kirinuki.di

import android.app.Application
import com.wanderwildwood.kirinuki.archmodel.FeedItemStore
import com.wanderwildwood.kirinuki.archmodel.FeedStore
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.archmodel.SessionStore
import com.wanderwildwood.kirinuki.archmodel.SettingsStore
import com.wanderwildwood.kirinuki.archmodel.SyncRemoteStore
import com.wanderwildwood.kirinuki.base.bindWithActivityViewModelScope
import com.wanderwildwood.kirinuki.base.bindWithComposableViewModelScope
import com.wanderwildwood.kirinuki.data.suggestions.SuggestedFeedRepository
import com.wanderwildwood.kirinuki.model.OPMLParserHandler
import com.wanderwildwood.kirinuki.model.opml.OPMLImporter
import com.wanderwildwood.kirinuki.ui.CommonActivityViewModel
import com.wanderwildwood.kirinuki.ui.MainActivityViewModel
import com.wanderwildwood.kirinuki.ui.NavigationDeepLinkViewModel
import com.wanderwildwood.kirinuki.ui.OpenLinkInDefaultActivityViewModel
import kotlinx.serialization.json.Json
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.bindFactory
import org.kodein.di.factory
import org.kodein.di.instance
import org.kodein.di.singleton
import java.util.Locale

val archModelModule =
    DI.Module(name = "arch models") {
        bind<Repository>() with singleton { Repository(di) }
        bind<SessionStore>() with singleton { SessionStore() }
        bind<SettingsStore>() with singleton { SettingsStore(di) }
        bind<FeedStore>() with singleton { FeedStore(di) }
        bind<FeedItemStore>() with singleton { FeedItemStore(di) }
        bind<SyncRemoteStore>() with singleton { SyncRemoteStore(di) }
        bind<OPMLParserHandler>() with singleton { OPMLImporter(di) }
        bind<SuggestedFeedRepository>() with
            singleton {
                SuggestedFeedRepository(
                    resources = instance<Application>().resources,
                    json =
                        Json {
                            ignoreUnknownKeys = true
                        },
                )
            }

        bindWithActivityViewModelScope<MainActivityViewModel>()
        bindWithActivityViewModelScope<OpenLinkInDefaultActivityViewModel>()
        bindWithActivityViewModelScope<CommonActivityViewModel>()

        bindWithComposableViewModelScope<NavigationDeepLinkViewModel>()
    }
