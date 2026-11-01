package com.wanderwildwood.kirinuki.ui.licences

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.ui.compose.components.BarIcon
import com.wanderwildwood.kirinuki.ui.compose.theme.Icons
import com.wanderwildwood.kirinuki.ui.rememberLicencesData
import com.wanderwildwood.kirinuki.ui.rememberLicencesLabels

/**
 * The licences page: this screen's own top bar over the shared page (source, licence, what
 * the app is built with, and a screen for each licence's full text).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicencesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.licences)) },
                navigationIcon = {
                    BarIcon(
                        icon = Icons.Back,
                        contentDescription = stringResource(R.string.go_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        com.wanderwildwood.kirinuki.ui.LicencesScreen(
            data = rememberLicencesData(),
            labels = rememberLicencesLabels(),
            onBack = onBack,
            modifier = Modifier.fillMaxSize().padding(padding),
            showBackButton = false,
            showTitle = false,
        )
    }
}
