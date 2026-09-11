package com.wanderwildwood.kirinuki.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.core.net.toUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wanderwildwood.kirinuki.base.DIAwareComponentActivity
import com.wanderwildwood.kirinuki.db.room.ID_ALL_FEEDS
import com.wanderwildwood.kirinuki.ui.compose.ompl.OpmlImportScreen
import com.wanderwildwood.kirinuki.ui.compose.utils.withAllProviders
import com.wanderwildwood.kirinuki.util.DEEP_LINK_BASE_URI
import com.wanderwildwood.kirinuki.util.logDebug

/**
 * This activity should only be started via a Open File Intent.
 */
class ImportOPMLFileActivity : DIAwareComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val uri = intent.data
        logDebug(LOG_TAG, "Uri: $uri")

        setContent {
            withAllProviders {
                val navController = rememberNavController()
                NavHost(navController, startDestination = "import") {
                    composable(
                        "import",
                        enterTransition = { fadeIn() },
                        exitTransition = { fadeOut() },
                        popEnterTransition = { fadeIn() },
                        popExitTransition = { fadeOut() },
                    ) {
                        OpmlImportScreen(
                            onNavigateUp = {
                                onNavigateUpFromIntentActivities()
                            },
                            uri = uri,
                            onDismiss = {
                                finish()
                            },
                            {
                                val deepLinkUri =
                                    "$DEEP_LINK_BASE_URI/feed?id=$ID_ALL_FEEDS"

                                val intent =
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        deepLinkUri.toUri(),
                                        this@ImportOPMLFileActivity,
                                        MainActivity::class.java,
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }

                                startActivity(intent)
                                finish()
                            },
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val LOG_TAG = "FEEDER_OPMLIMPORT"
    }
}
