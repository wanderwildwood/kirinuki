package com.wanderwildwood.kirinuki.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.activity.compose.setContent
import com.wanderwildwood.kirinuki.base.DIAwareComponentActivity
import com.wanderwildwood.kirinuki.base.diAwareViewModel
import com.wanderwildwood.kirinuki.net.addressIn
import com.wanderwildwood.kirinuki.ui.addfeed.AddFeedScreen
import com.wanderwildwood.kirinuki.ui.compose.utils.withAllProviders

/**
 * Started by a share or an opened feed URL. There is no search step: the address
 * arrives filled in, and the feed says what its own title is once it is fetched.
 */
class AddFeedFromShareActivity : DIAwareComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdgeDarkIcons()

        val initialFeedUrl =
            addressIn((intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT)).orEmpty())

        setContent {
            withAllProviders {
                AddFeedScreen(
                    onBack = { onNavigateUpFromIntentActivities() },
                    onSaved = { finish() },
                    // This activity has no nav graph of its own, so reading hands the
                    // address back to the app the same way any other capsule link arrives.
                    onRead = { url ->
                        startActivity(
                            Intent(Intent.ACTION_VIEW, url.toUri(), this, MainActivity::class.java),
                        )
                        finish()
                    },
                    // The same hand-over for a web page read on its own, flagged so the app
                    // knows it was asked to read it and not merely shown an address.
                    onReadPage = { url ->
                        startActivity(
                            Intent(Intent.ACTION_VIEW, url.toUri(), this, MainActivity::class.java)
                                .putExtra(EXTRA_READ_PAGE, true),
                        )
                        finish()
                    },
                    viewModel = diAwareViewModel(),
                    initialUrl = initialFeedUrl,
                )
            }
        }
    }
}

/**
 * Backing out of an activity that was opened by a share or a file leaves you in the
 * app itself rather than wherever the intent came from.
 */
fun Activity.onNavigateUpFromIntentActivities() {
    startActivity(
        Intent(
            this,
            MainActivity::class.java,
        ),
    )
    finish()
}
