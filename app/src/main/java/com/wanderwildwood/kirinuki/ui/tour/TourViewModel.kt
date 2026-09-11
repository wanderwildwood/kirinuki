package com.wanderwildwood.kirinuki.ui.tour

import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import com.wanderwildwood.kirinuki.model.tour.TourEntry
import com.wanderwildwood.kirinuki.model.tour.TourStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.kodein.di.DI
import org.kodein.di.instance

class TourViewModel(
    di: DI,
) : DIAwareViewModel(di) {
    private val tourStore: TourStore by instance()

    private val _entries = MutableStateFlow(tourStore.all())
    val entries: StateFlow<List<TourEntry>> = _entries

    fun refresh() {
        _entries.value = tourStore.all()
    }

    fun remove(url: String) {
        tourStore.remove(url)
        refresh()
    }
}
