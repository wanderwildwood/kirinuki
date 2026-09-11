package com.wanderwildwood.kirinuki.ui.settings

import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.archmodel.SyncFrequency
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import kotlinx.coroutines.flow.StateFlow
import org.kodein.di.DI
import org.kodein.di.instance

class SettingsViewModel(
    di: DI,
) : DIAwareViewModel(di) {
    private val repository: Repository by instance()

    val syncOnlyOnWifi: StateFlow<Boolean> = repository.syncOnlyOnWifi
    val syncOnlyWhenCharging: StateFlow<Boolean> = repository.syncOnlyWhenCharging
    val syncFrequency: StateFlow<SyncFrequency> = repository.syncFrequency
    val textScale: StateFlow<Float> = repository.textScale
    val maximumCountPerFeed: StateFlow<Int> = repository.maximumCountPerFeed

    fun setSyncOnlyOnWifi(value: Boolean) = repository.setSyncOnlyOnWifi(value)

    fun setSyncOnlyWhenCharging(value: Boolean) = repository.setSyncOnlyWhenCharging(value)

    fun setSyncFrequency(value: SyncFrequency) = repository.setSyncFrequency(value)

    fun setTextScale(value: Float) = repository.setTextScale(value)

    fun setMaxCountPerFeed(value: Int) = repository.setMaxCountPerFeed(value)
}
