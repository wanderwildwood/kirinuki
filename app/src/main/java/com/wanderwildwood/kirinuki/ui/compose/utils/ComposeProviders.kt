package com.wanderwildwood.kirinuki.ui.compose.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wanderwildwood.kirinuki.base.DIAwareComponentActivity
import com.wanderwildwood.kirinuki.base.diAwareViewModel
import com.wanderwildwood.kirinuki.ui.CommonActivityViewModel
import com.wanderwildwood.kirinuki.ui.compose.theme.KirinukiTheme
import com.wanderwildwood.kirinuki.ui.compose.theme.ProvideTypographySettings
import org.kodein.di.compose.withDI

@Composable
fun DIAwareComponentActivity.withAllProviders(content: @Composable () -> Unit) {
    withDI {
        val viewModel: CommonActivityViewModel = diAwareViewModel()
        val textScale by viewModel.textScale.collectAsStateWithLifecycle()
        withWindowMetrics {
            withWindowSize {
                ProvideTypographySettings(fontScale = textScale) {
                    KirinukiTheme {
                        WithKirinukiTextToolbar(content)
                    }
                }
            }
        }
    }
}
