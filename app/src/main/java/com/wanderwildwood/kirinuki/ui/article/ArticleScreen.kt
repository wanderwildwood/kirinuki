package com.wanderwildwood.kirinuki.ui.article

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.archmodel.TextToDisplay
import com.wanderwildwood.kirinuki.net.isSmolnetUrl
import com.wanderwildwood.kirinuki.ui.compose.components.BarIcon
import com.wanderwildwood.kirinuki.ui.compose.components.rememberReaderScrollStep
import com.wanderwildwood.kirinuki.ui.compose.html.linearArticleContent
import com.wanderwildwood.kirinuki.ui.compose.theme.Icons
import com.wanderwildwood.kirinuki.ui.compose.theme.TextScaleDialog
import com.wanderwildwood.kirinuki.ui.compose.utils.ProvideScaledText
import com.wanderwildwood.kirinuki.util.hasWebBrowser
import com.wanderwildwood.kirinuki.util.openInBrowser

/**
 * The cutting itself. Title, where it came from, and the text -- the summary the feed
 * carried, or the whole article fetched from the page and kept.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    onBack: () -> Unit,
    onFollowGemini: (String) -> Unit,
    viewModel: ArticleViewModel,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val article by viewModel.article.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showingFullText by viewModel.showingFullText.collectAsStateWithLifecycle()
    val textScale by viewModel.textScale.collectAsStateWithLifecycle()
    val openTitleInBrowser by viewModel.openTitleInBrowser.collectAsStateWithLifecycle()
    var scaleOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    // Three things have to be true, and the default is that the first is not: the setting
    // is on, this phone has something that is not the WebView shell, and the cutting
    // remembers where it came from. Anything less and the title is what it always was.
    val articleLink = article?.link
    val titleOpensPage =
        openTitleInBrowser &&
            articleLink != null &&
            remember(context) { context.hasWebBrowser() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarMMD(
                title = {
                    TextMMD(
                        text = article?.feedDisplayTitle.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    BarIcon(
                        icon = Icons.Back,
                        contentDescription = stringResource(R.string.go_back),
                        onClick = onBack,
                    )
                },
                actions = {
                    // The setting is in settings, where it is set once. This is the same
                    // setting reached from the one screen where the size is a question you
                    // can actually answer, because the page you are judging is behind it.
                    BarIcon(
                        icon = Icons.TextScale,
                        contentDescription = stringResource(R.string.text_scale),
                        onClick = { scaleOpen = true },
                    )
                    BarIcon(
                        icon =
                            if (showingFullText) {
                                Icons.Subject
                            } else {
                                Icons.Article
                            },
                        contentDescription =
                            if (showingFullText) {
                                stringResource(R.string.show_summary)
                            } else {
                                stringResource(R.string.fetch_full_article)
                            },
                        onClick = { viewModel.toggleFullText() },
                    )
                    BarIcon(
                        icon =
                            if (article?.bookmarked == true) {
                                Icons.Star
                            } else {
                                Icons.StarBorder
                            },
                        contentDescription =
                            if (article?.bookmarked == true) {
                                stringResource(R.string.remove_bookmark)
                            } else {
                                stringResource(R.string.add_bookmark)
                            },
                        onClick = { viewModel.toggleBookmarked() },
                    )
                },
            )
        },
    ) { padding ->
        LazyColumnMMD(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // A swipe moves on to the paragraph the bottom of the screen cut through,
            // rather than MMD's four items -- four paragraphs are however tall they are,
            // and the ones that did not fit were being stepped straight past.
            scrollStep = rememberReaderScrollStep(listState),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            item {
                // The body is bodyLarge through ProvideScaledText, and the title was the
                // ambient style at no scale at all: a step smaller than the text under it
                // at 100%, and further adrift at every step of the text scale. Same style,
                // same scale, bold -- which is the only emphasis there is here.
                ProvideScaledText(style = MaterialTheme.typography.bodyLarge) {
                    TextMMD(
                        text = article?.title.orEmpty(),
                        fontWeight = FontWeight.Bold,
                        // Underlined only when it leads somewhere. An underline on a title
                        // that does nothing is the thing this app took out.
                        textDecoration =
                            if (titleOpensPage) TextDecoration.Underline else null,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (articleLink != null && titleOpensPage) {
                                        Modifier.clickable { context.openInBrowser(articleLink) }
                                    } else {
                                        Modifier
                                    },
                                ),
                    )
                }
            }

            when (state) {
                TextToDisplay.CONTENT ->
                    linearArticleContent(
                        articleContent = content,
                        // Clippings hands nothing to the system. A stock Kompakt has no
                        // browser -- only the AOSP WebView test shell is registered for http --
                        // so an external open is a crash waiting to happen rather than a way
                        // out. The renderer only makes followable links tappable; this is the
                        // guard that keeps that true if it ever stops being.
                        onLinkClick = { url, _ ->
                            if (isSmolnetUrl(url)) {
                                onFollowGemini(url)
                            }
                        },
                    )

                else ->
                    item {
                        TextMMD(
                            text = stringResource(state.messageRes()),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
            }
        }
    }

    if (scaleOpen) {
        TextScaleDialog(
            scale = textScale,
            onScale = viewModel::setTextScale,
            onDismiss = { scaleOpen = false },
        )
    }
}

/**
 * What went wrong is said plainly, rather than left as an empty page.
 */
private fun TextToDisplay.messageRes(): Int =
    when (this) {
        TextToDisplay.CONTENT -> R.string.empty_article
        TextToDisplay.LOADING_FULLTEXT -> R.string.fetching_full_article
        TextToDisplay.FAILED_TO_LOAD_FULLTEXT -> R.string.failed_to_fetch_full_article
        TextToDisplay.FAILED_MISSING_BODY -> R.string.failed_to_fetch_full_article_missing_body
        TextToDisplay.FAILED_MISSING_LINK -> R.string.failed_to_fetch_full_article_missing_link
        TextToDisplay.FAILED_NOT_HTML -> R.string.failed_to_fetch_full_article_not_html
        TextToDisplay.FAILED_FULLTEXT_TOO_LARGE -> R.string.failed_to_fetch_full_article_too_large
    }
