package com.wanderwildwood.kirinuki.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wanderwildwood.kirinuki.base.DIAwareComponentActivity
import com.wanderwildwood.kirinuki.base.diAwareViewModel
import com.wanderwildwood.kirinuki.ui.compose.utils.withAllProviders
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

        enableEdgeToEdge()

        setContent {
            withAllProviders {
                SettingsScreen(
                    onBack = { onNavigateUpFromIntentActivities() },
                    onImportOpml = {},
                    onExportOpml = {},
                    viewModel = diAwareViewModel(),
                )
            }
        }
    }
}
