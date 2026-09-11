package com.wanderwildwood.kirinuki.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.background.runOnceRssSync
import com.wanderwildwood.kirinuki.background.schedulePeriodicOrphanedFilesCleanup
import com.wanderwildwood.kirinuki.base.DIAwareComponentActivity
import com.wanderwildwood.kirinuki.base.diAwareViewModel
import com.wanderwildwood.kirinuki.model.opml.exportOpml
import com.wanderwildwood.kirinuki.model.opml.importOpml
import com.wanderwildwood.kirinuki.notifications.NotificationsWorker
import com.wanderwildwood.kirinuki.ui.addfeed.AddFeedScreen
import com.wanderwildwood.kirinuki.ui.addfeed.AddFeedViewModel
import com.wanderwildwood.kirinuki.ui.article.ArticleScreen
import com.wanderwildwood.kirinuki.ui.article.ArticleViewModel
import com.wanderwildwood.kirinuki.ui.articles.ArticleListScreen
import com.wanderwildwood.kirinuki.ui.articles.ArticleListViewModel
import com.wanderwildwood.kirinuki.ui.compose.utils.withAllProviders
import com.wanderwildwood.kirinuki.ui.feeds.FeedsScreen
import com.wanderwildwood.kirinuki.ui.feeds.FeedsViewModel
import com.wanderwildwood.kirinuki.ui.settings.SettingsScreen
import com.wanderwildwood.kirinuki.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch
import org.kodein.di.instance
import java.time.LocalDate

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

    /**
     * The Kompakt has volume keys and a screen that does not like a finger dragged
     * across it, so they page the article.
     */
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        if (repository.isArticleOpen.value) {
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
                    runOnceRssSync(di = di, forceNetwork = false, triggeredByUser = false)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainActivityViewModel.ensurePeriodicSyncConfigured()
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
        val articleListState = rememberLazyListState()

        val importLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                lifecycleScope.launch { importOpml(di, uri) }
            }
        val exportLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/opml"),
            ) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                lifecycleScope.launch { exportOpml(di, uri) }
            }

        NavHost(navController, startDestination = Route.FEEDS) {
            composable(Route.FEEDS) {
                val viewModel: FeedsViewModel = diAwareViewModel()
                FeedsScreen(
                    onOpenFeed = { navController.navigate(Route.ARTICLES) },
                    onAddFeed = { navController.navigate(Route.ADD_FEED) },
                    onSettings = { navController.navigate(Route.SETTINGS) },
                    viewModel = viewModel,
                )
            }
            composable(Route.ARTICLES) {
                val viewModel: ArticleListViewModel = diAwareViewModel()
                ArticleListScreen(
                    onOpenArticle = { navController.navigate(Route.ARTICLE) },
                    onBack = { navController.popBackStack() },
                    viewModel = viewModel,
                )
            }
            composable(Route.ARTICLE) {
                val viewModel: ArticleViewModel = diAwareViewModel()
                ArticleScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = viewModel,
                    listState = articleListState,
                )
            }
            composable(Route.SETTINGS) {
                val viewModel: SettingsViewModel = diAwareViewModel()
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onImportOpml = { importLauncher.launch(arrayOf("*/*")) },
                    onExportOpml = { exportLauncher.launch("kirinuki-${LocalDate.now()}.opml") },
                    viewModel = viewModel,
                )
            }
            composable(Route.ADD_FEED) {
                val viewModel: AddFeedViewModel = diAwareViewModel()
                AddFeedScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    viewModel = viewModel,
                )
            }
        }
    }
}
