package com.wanderwildwood.kirinuki.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
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
    /**
     * The address that came in. A state, not a value read once, because this activity is
     * singleTask: a second share while it is open arrives through [onNewIntent], and the form
     * starts again with the new address rather than keeping the old one.
     */
    private val sharedUrl = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdgeDarkIcons()

        sharedUrl.value = addressOf(intent)

        setContent {
            withAllProviders {
                val url by sharedUrl
                // A new address is a new form: everything remembered for the last one goes.
                key(url) {
                    // The address came from another app: it is shown first, and fetched only
                    // once Open is pressed. Cancel goes back to where it came from.
                    var opened by rememberSaveable { mutableStateOf(url.isBlank()) }
                    if (!opened) {
                        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {}
                        HandedInAddressDialog(
                            address = url,
                            onOpen = { opened = true },
                            onDismiss = { finish() },
                        )
                    } else {
                        AddFeedScreen(
                            onBack = { onNavigateUpFromIntentActivities() },
                            onSaved = { finish() },
                            // This activity has no nav graph of its own, so reading hands the
                            // address back to the app the same way any other capsule link arrives.
                            onRead = { address ->
                                handOverToApp(
                                    HandOver.mark(Intent(Intent.ACTION_VIEW, address.toUri(), this, MainActivity::class.java)),
                                )
                            },
                            // The same hand-over for a web page read on its own, flagged so the app
                            // knows it was asked to read it and not merely shown an address.
                            onReadPage = { address ->
                                handOverToApp(
                                    HandOver.mark(
                                        Intent(Intent.ACTION_VIEW, address.toUri(), this, MainActivity::class.java)
                                            .putExtra(EXTRA_READ_PAGE, true),
                                    ),
                                )
                            },
                            viewModel = diAwareViewModel(key = url),
                            initialUrl = url,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUrl.value = addressOf(intent)
    }

    private fun addressOf(intent: Intent?): String =
        addressIn((intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT)).orEmpty())
}

/**
 * Goes on in the app's own task and closes this one. FLAG_ACTIVITY_NEW_TASK finds the task by
 * the app's affinity -- the one the launcher opens -- rather than staying in whichever task
 * this activity was started in, which is a share's or another app's.
 */
fun Activity.handOverToApp(intent: Intent) {
    startActivity(intent.addFlags(HAND_OVER_FLAGS))
    finish()
}

/** Kept apart so a test can say what the hand-over asks for. */
const val HAND_OVER_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK

/**
 * Backing out of an activity that was opened by a share or a file leaves you in the
 * app itself rather than wherever the intent came from.
 */
fun Activity.onNavigateUpFromIntentActivities() {
    handOverToApp(Intent(this, MainActivity::class.java))
}
