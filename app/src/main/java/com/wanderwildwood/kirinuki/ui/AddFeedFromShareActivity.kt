package com.wanderwildwood.kirinuki.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wanderwildwood.kirinuki.base.DIAwareComponentActivity
import com.wanderwildwood.kirinuki.base.diAwareViewModel
import com.wanderwildwood.kirinuki.ui.addfeed.AddFeedScreen
import com.wanderwildwood.kirinuki.ui.compose.utils.withAllProviders

/**
 * Started by a share or an opened feed URL. There is no search step: the address
 * arrives filled in, and the feed says what its own title is once it is fetched.
 */
class AddFeedFromShareActivity : DIAwareComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val initialFeedUrl =
            (intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT))?.trim().orEmpty()

        setContent {
            withAllProviders {
                AddFeedScreen(
                    onBack = { onNavigateUpFromIntentActivities() },
                    onSaved = { finish() },
                    viewModel = diAwareViewModel(),
                    initialUrl = initialFeedUrl,
                )
            }
        }
    }
}
