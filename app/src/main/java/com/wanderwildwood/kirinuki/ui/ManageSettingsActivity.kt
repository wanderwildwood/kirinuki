package com.wanderwildwood.kirinuki.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.wanderwildwood.kirinuki.base.DIAwareComponentActivity
import com.wanderwildwood.kirinuki.base.diAwareViewModel
import com.wanderwildwood.kirinuki.ui.compose.utils.withAllProviders
import com.wanderwildwood.kirinuki.ui.settings.ServerSyncScreen
import com.wanderwildwood.kirinuki.ui.settings.ServerSyncViewModel
import com.wanderwildwood.kirinuki.ui.settings.SettingsScreen

/**
 * Should only be opened from the MANAGE SETTINGS INTENT.
 *
 * OPML is handled from the app's own settings screen rather than here, because the
 * document picker wants an activity that is going to stay on screen afterwards.
 */
class ManageSettingsActivity : DIAwareComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdgeDarkIcons()

        setContent {
            withAllProviders {
                // No navigation here: the feed server screen is shown in place, and Back
                // returns to the settings.
                var serverSync by rememberSaveable { mutableStateOf(false) }
                if (serverSync) {
                    BackHandler { serverSync = false }
                    ServerSyncScreen(
                        onBack = { serverSync = false },
                        viewModel = diAwareViewModel<ServerSyncViewModel>(),
                    )
                } else {
                    SettingsScreen(
                        onBack = { onNavigateUpFromIntentActivities() },
                        onImportOpml = {},
                        onExportOpml = {},
                        onServerSync = { serverSync = true },
                        viewModel = diAwareViewModel(),
                    )
                }
            }
        }
    }
}
