package com.wanderwildwood.kirinuki.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wanderwildwood.kirinuki.background.runOnceRssSync
import com.wanderwildwood.kirinuki.background.schedulePeriodicOrphanedFilesCleanup
import com.wanderwildwood.kirinuki.base.DIAwareComponentActivity
import com.wanderwildwood.kirinuki.base.diAwareViewModel
import com.wanderwildwood.kirinuki.db.room.ID_UNSET
import com.wanderwildwood.kirinuki.model.opml.exportOpml
import com.wanderwildwood.kirinuki.model.opml.importOpml
import com.wanderwildwood.kirinuki.notifications.NotificationsWorker
import com.wanderwildwood.kirinuki.ui.addfeed.AddFeedScreen
import com.wanderwildwood.kirinuki.ui.addfeed.AddFeedViewModel
import com.wanderwildwood.kirinuki.ui.article.ArticleScreen
import com.wanderwildwood.kirinuki.ui.article.ArticleViewModel
import com.wanderwildwood.kirinuki.ui.articles.ArticleListScreen
import com.wanderwildwood.kirinuki.ui.articles.ArticleListViewModel
import com.wanderwildwood.kirinuki.ui.compose.components.turnPage
import com.wanderwildwood.kirinuki.ui.compose.utils.withAllProviders
import com.wanderwildwood.kirinuki.ui.feeds.FeedsScreen
import com.wanderwildwood.kirinuki.ui.feeds.FeedsViewModel
import com.wanderwildwood.kirinuki.ui.gemini.GeminiPageScreen
import com.wanderwildwood.kirinuki.ui.gemini.GeminiPageViewModel
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

    /**
     * Whether a reader is the screen in front, which is the only time the volume keys are
     * ours to take. Set by the composition, because the composition is what knows.
     *
     * It used to ask the repository, which kept an `isArticleOpen` flag in the preferences.
     * Nothing has set that flag since the interface was rebuilt, so it sat at false and the
     * volume keys did nothing at all -- on a reader that says in its own README that they
     * turn the page.
     */
    private val readerOpen = mutableStateOf(false)

    private val notificationsWorker: NotificationsWorker by instance()
    private val mainActivityViewModel: MainActivityViewModel by instance(arg = this)

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
        if (readerOpen.value) {
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

    /**
     * The release of the same key, taken as well.
     *
     * Only the press turns a page, but a volume key that is not answered on the way up is
     * still the system's, and it puts the volume panel over the page that was just turned --
     * a second full repaint of an E Ink screen to say something nobody asked about.
     */
    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        if (readerOpen.value &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
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
            ?.takeIf { it.scheme in setOf("gemini", "gopher", "spartan") }
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

        // Whichever reader is on screen puts its list here while it is, and the volume keys
        // page that one. A capsule page keeps its own list rather than sharing this one, so
        // that going back lands where you left off rather than at the top.
        val readerState = remember { mutableStateOf<LazyListState?>(null) }
        val reader by readerState
        LaunchedEffect(reader) {
            readerOpen.value = reader != null
            val list = reader ?: return@LaunchedEffect
            mainActivityViewModel.scrollCommand.collect { list.turnPage(it) }
        }

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

        // ⚠ Every view model here is taken from its own NavBackStackEntry rather than
        // from the activity. Scoped to the activity they share one SavedStateRegistry,
        // and returning to a destination registers the same key twice --
        // "SavedStateProvider with the given key is already registered" -- which is a
        // crash on the second visit, not the first, so it survives a quick look.
        NavHost(navController, startDestination = Route.FEEDS) {
            composable(Route.FEEDS) { entry ->
                val viewModel: FeedsViewModel = entry.diAwareViewModel()
                FeedsScreen(
                    onOpenFeed = { navController.navigate(Route.ARTICLES) },
                    onAddFeed = { navController.navigate(Route.addFeed("")) },
                    onEditFeed = { navController.navigate(Route.editFeed(it)) },
                    onSettings = { navController.navigate(Route.SETTINGS) },
                    viewModel = viewModel,
                )
            }
            composable(Route.ARTICLES) { entry ->
                val viewModel: ArticleListViewModel = entry.diAwareViewModel()
                ArticleListScreen(
                    onOpenArticle = { navController.navigate(Route.ARTICLE) },
                    onBack = { navController.popBackStack() },
                    viewModel = viewModel,
                )
            }
            composable(Route.ARTICLE) { entry ->
                val viewModel: ArticleViewModel = entry.diAwareViewModel()
                // This screen's list is the one the volume keys page while it is up. The
                // identity check on the way out is for going from one capsule to the next:
                // the screen arriving may take the keys before the screen leaving gives
                // them back, and the one that left would take them from the one in front.
                DisposableEffect(articleListState) {
                    readerState.value = articleListState
                    onDispose {
                        if (readerState.value === articleListState) readerState.value = null
                    }
                }
                ArticleScreen(
                    onBack = { navController.popBackStack() },
                    onFollowGemini = { navController.navigate(Route.gemini(it)) },
                    viewModel = viewModel,
                    listState = articleListState,
                )
            }
            composable(Route.SETTINGS) { entry ->
                val viewModel: SettingsViewModel = entry.diAwareViewModel()
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
                val viewModel: GeminiPageViewModel = entry.diAwareViewModel()
                val capsuleListState = rememberLazyListState()
                DisposableEffect(capsuleListState) {
                    readerState.value = capsuleListState
                    onDispose {
                        if (readerState.value === capsuleListState) readerState.value = null
                    }
                }
                GeminiPageScreen(
                    url = entry.arguments?.getString(Route.GEMINI_ARG).orEmpty(),
                    onBack = { navController.popBackStack() },
                    onFollow = { navController.navigate(Route.gemini(it)) },
                    onSubscribe = { navController.navigate(Route.addFeed(it)) },
                    viewModel = viewModel,
                    listState = capsuleListState,
                )
            }
            composable(
                Route.ADD_FEED_ROUTE,
                arguments =
                    listOf(
                        navArgument(Route.ADD_FEED_ARG) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument(Route.ADD_FEED_FEED_ARG) {
                            type = NavType.LongType
                            defaultValue = ID_UNSET
                        },
                    ),
            ) { entry ->
                val viewModel: AddFeedViewModel = entry.diAwareViewModel()
                AddFeedScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onRead = { navController.navigate(Route.gemini(it)) },
                    viewModel = viewModel,
                    initialUrl = entry.arguments?.getString(Route.ADD_FEED_ARG).orEmpty(),
                    feedId = entry.arguments?.getLong(Route.ADD_FEED_FEED_ARG) ?: ID_UNSET,
                )
            }
        }
    }
}
