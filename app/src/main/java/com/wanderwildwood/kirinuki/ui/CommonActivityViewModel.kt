package com.wanderwildwood.kirinuki.ui

import com.wanderwildwood.kirinuki.archmodel.DarkThemePreferences
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.archmodel.ThemeOptions
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import com.wanderwildwood.kirinuki.ui.compose.settings.FontSelection
import kotlinx.coroutines.flow.StateFlow
import org.kodein.di.DI
import org.kodein.di.instance

class CommonActivityViewModel(
    di: DI,
) : DIAwareViewModel(di) {
    private val repository: Repository by instance()

    val currentTheme: StateFlow<ThemeOptions> =
        repository.currentTheme

    val darkThemePreference: StateFlow<DarkThemePreferences> =
        repository.preferredDarkTheme

    val dynamicColors: StateFlow<Boolean> =
        repository.useDynamicTheme

    val textScale: StateFlow<Float> =
        repository.textScale

    val font: StateFlow<FontSelection> =
        repository.font
}
