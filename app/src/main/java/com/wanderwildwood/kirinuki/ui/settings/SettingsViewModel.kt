package com.wanderwildwood.kirinuki.ui.settings

import androidx.lifecycle.viewModelScope
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.archmodel.SyncFrequency
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val openTitleInBrowser: StateFlow<Boolean> = repository.openTitleInBrowser

    /**
     * One face of the list filter, which is the only face of it this app has ever shown.
     * Off leaves what you have read in the database and out of the list; it comes back
     * the moment this is on again.
     */
    val showReadArticles: StateFlow<Boolean> =
        repository.feedListFilter
            .map { it.read }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = repository.feedListFilter.value.read,
            )

    fun setSyncOnlyOnWifi(value: Boolean) = repository.setSyncOnlyOnWifi(value)

    fun setSyncOnlyWhenCharging(value: Boolean) = repository.setSyncOnlyWhenCharging(value)

    fun setSyncFrequency(value: SyncFrequency) = repository.setSyncFrequency(value)

    fun setTextScale(value: Float) = repository.setTextScale(value)

    fun setMaxCountPerFeed(value: Int) = repository.setMaxCountPerFeed(value)

    fun setShowReadArticles(value: Boolean) = repository.setFeedListFilterRead(value)

    fun setOpenTitleInBrowser(value: Boolean) = repository.setOpenTitleInBrowser(value)
}
