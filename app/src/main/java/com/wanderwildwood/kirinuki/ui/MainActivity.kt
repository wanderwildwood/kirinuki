package com.wanderwildwood.kirinuki.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.util.Consumer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.background.runOnceRssSync
import com.wanderwildwood.kirinuki.background.runOnceSyncChainGetUpdates
import com.wanderwildwood.kirinuki.background.schedulePeriodicOrphanedFilesCleanup
import com.wanderwildwood.kirinuki.base.DIAwareComponentActivity
import com.wanderwildwood.kirinuki.notifications.NotificationsWorker
import com.wanderwildwood.kirinuki.ui.compose.navigation.AddFeedDestination
import com.wanderwildwood.kirinuki.ui.compose.navigation.ArticleDestination
import com.wanderwildwood.kirinuki.ui.compose.navigation.EditFeedDestination
import com.wanderwildwood.kirinuki.ui.compose.navigation.FeedDestination
import com.wanderwildwood.kirinuki.ui.compose.navigation.SearchFeedDestination
import com.wanderwildwood.kirinuki.ui.compose.navigation.SettingsDestination
import com.wanderwildwood.kirinuki.ui.compose.navigation.SyncScreenDestination
import com.wanderwildwood.kirinuki.ui.compose.navigation.TextSettingsDestination
import com.wanderwildwood.kirinuki.ui.compose.utils.withAllProviders
import kotlinx.coroutines.launch
import org.kodein.di.instance

class MainActivity : DIAwareComponentActivity() {
    private val notificationsWorker: NotificationsWorker by instance()
    private val mainActivityViewModel: MainActivityViewModel by instance(arg = this)
    private val repository: Repository by instance()

    override fun onStart() {
        super.onStart()
        notificationsWorker.runForever()
    }

    override fun onStop() {
        notificationsWorker.stopForever()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        mainActivityViewModel.setResumeTime()
        maybeRequestSync()
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        if (mainActivityViewModel.isPagingMode.value && repository.isArticleOpen.value) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    mainActivityViewModel.emitScrollCommand(ScrollDirection.UP)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    mainActivityViewModel.emitScrollCommand(ScrollDirection.DOWN)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun maybeRequestSync() =
        lifecycleScope.launch {
            if (mainActivityViewModel.shouldSyncOnResume) {
                if (mainActivityViewModel.isOkToSyncAutomatically()) {
                    runOnceSyncChainGetUpdates(di)
                    runOnceRssSync(
                        di = di,
                        forceNetwork = false,
                        triggeredByUser = false,
                    )
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainActivityViewModel.ensurePeriodicSyncConfigured()

        // Configure daily cleanup of orphaned article files
        schedulePeriodicOrphanedFilesCleanup(di)

        enableEdgeToEdge()

        setContent {
            withAllProviders {
                AppContent()
            }
        }
    }

    @Composable
    fun AppContent() {
        val navController = rememberNavController()
        val navDrawerListState = rememberLazyListState()

        NavHost(navController, startDestination = FeedDestination.route) {
            FeedDestination.register(this, navController, navDrawerListState, mainActivityViewModel)
            ArticleDestination.register(this, navController, navDrawerListState, mainActivityViewModel)
            // Feed editing
            EditFeedDestination.register(this, navController, navDrawerListState, mainActivityViewModel)
            SearchFeedDestination.register(this, navController, navDrawerListState, mainActivityViewModel)
            AddFeedDestination.register(this, navController, navDrawerListState, mainActivityViewModel)
            // Settings
            SettingsDestination.register(this, navController, navDrawerListState, mainActivityViewModel)
            // Sync settings
            SyncScreenDestination.register(this, navController, navDrawerListState, mainActivityViewModel)
            // Add Fonts
            TextSettingsDestination.register(this, navController, navDrawerListState, mainActivityViewModel)
        }

        DisposableEffect(navController) {
            val listener =
                Consumer<Intent> { intent ->
                    if (!navController.handleDeepLink(intent)) {
                        Log.e(LOG_TAG, "NavController rejected intent: $intent")
                    }
                }
            addOnNewIntentListener(listener)
            onDispose { removeOnNewIntentListener(listener) }
        }
    }

    companion object {
        private const val LOG_TAG = "FEEDER_MAIN"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
    }
}
