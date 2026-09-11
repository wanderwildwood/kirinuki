package com.wanderwildwood.kirinuki.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import com.wanderwildwood.kirinuki.ui.gemini.GeminiPageScreen
import com.wanderwildwood.kirinuki.ui.gemini.GeminiPageViewModel
import com.wanderwildwood.kirinuki.ui.feeds.FeedsViewModel
import com.wanderwildwood.kirinuki.ui.settings.SettingsScreen
import com.wanderwildwood.kirinuki.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch
import org.kodein.di.instance
import java.time.LocalDate

class MainActivity : DIAwareComponentActivity() {
    /**
     * A gemini:// link tapped in another app, waiting to be navigated to once the
     * composition exists. Held rather than acted on, because the intent arrives before
     * there is a nav controller to act with.
     */
    private val pendingCapsule = mutableStateOf<String?>(null)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        capsuleFrom(intent)?.let { pendingCapsule.value = it }
    }

    private fun capsuleFrom(intent: Intent?): String? =
        intent
            ?.takeIf { it.action == Intent.ACTION_VIEW }
            ?.data
            ?.takeIf { it.scheme == "gemini" }
            ?.toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingCapsule.value = capsuleFrom(intent)

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

        val capsule by pendingCapsule
        LaunchedEffect(capsule) {
            capsule?.let {
                pendingCapsule.value = null
                navController.navigate(Route.gemini(it))
            }
        }

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
                    onFollowGemini = { navController.navigate(Route.gemini(it)) },
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
            composable(
                Route.GEMINI_ROUTE,
                arguments = listOf(navArgument(Route.GEMINI_ARG) { type = NavType.StringType }),
            ) { entry ->
                val viewModel: GeminiPageViewModel = diAwareViewModel(
                    key = entry.arguments?.getString(Route.GEMINI_ARG),
                )
                GeminiPageScreen(
                    url = entry.arguments?.getString(Route.GEMINI_ARG).orEmpty(),
                    onBack = { navController.popBackStack() },
                    onFollow = { navController.navigate(Route.gemini(it)) },
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
