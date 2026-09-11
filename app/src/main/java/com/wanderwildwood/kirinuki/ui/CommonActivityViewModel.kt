package com.wanderwildwood.kirinuki.ui

import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import kotlinx.coroutines.flow.StateFlow
import org.kodein.di.DI
import org.kodein.di.instance

class CommonActivityViewModel(
    di: DI,
) : DIAwareViewModel(di) {
    private val repository: Repository by instance()

    val textScale: StateFlow<Float> = repository.textScale
}
