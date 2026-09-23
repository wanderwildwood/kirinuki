package com.wanderwildwood.kirinuki.ui.settings

import androidx.lifecycle.viewModelScope
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.archmodel.SyncFrequency
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import com.wanderwildwood.kirinuki.net.gemini.KnownHost
import com.wanderwildwood.kirinuki.net.gemini.KnownHosts
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val knownHosts: KnownHosts by instance()

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

    private val _capsuleCertificates = MutableStateFlow(knownHosts.all())

    /**
     * The certificate each Gemini capsule showed the first time, which is what a later visit
     * is checked against. Listed so one can be let go: the refusal for a changed certificate
     * tells the reader to forget it here, and until now there was nowhere to do it -- a
     * capsule that renewed its key early stayed unreachable for good.
     */
    val capsuleCertificates: StateFlow<List<KnownHost>> = _capsuleCertificates

    /** The next visit to [host] takes whatever certificate it shows, and remembers that one. */
    fun forgetCapsuleCertificate(host: String) {
        knownHosts.forget(host)
        _capsuleCertificates.value = knownHosts.all()
    }
}
