package com.wanderwildwood.kirinuki.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wanderwildwood.kirinuki.base.DIAwareComponentActivity
import com.wanderwildwood.kirinuki.base.diAwareViewModel
import com.wanderwildwood.kirinuki.ui.compose.navigation.SyncScreenDestination
import com.wanderwildwood.kirinuki.ui.compose.navigation.TextSettingsDestination
import com.wanderwildwood.kirinuki.ui.compose.settings.SettingsScreen
import com.wanderwildwood.kirinuki.ui.compose.utils.withAllProviders

/**
 * Should only be opened from the MANAGE SETTINGS INTENT
 */
class ManageSettingsActivity : DIAwareComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            withAllProviders {
                SettingsScreen(
                    onNavigateUp = {
                        onNavigateUpFromIntentActivities()
                    },
                    onNavigateToSyncScreen = {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(SyncScreenDestination.deepLinks.first().uriPattern),
                                this,
                                MainActivity::class.java,
                            ),
                        )
                        finish()
                    },
                    onNavigateToTextSettingsScreen = {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(TextSettingsDestination.deepLinks.first().uriPattern),
                                this,
                                MainActivity::class.java,
                            ),
                        )
                        finish()
                    },
                    settingsViewModel = diAwareViewModel(),
                )
            }
        }
    }
}
