package com.wanderwildwood.kirinuki.di

import com.wanderwildwood.kirinuki.archmodel.AndroidSystemStore
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

val androidModule =
    DI.Module(name = "android module") {
        bind<AndroidSystemStore>() with singleton { AndroidSystemStore(di) }
    }
