package com.wanderwildwood.kirinuki.ui.webpage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.net.isSmolnetUrl
import com.wanderwildwood.kirinuki.ui.ReaderColumn
import com.wanderwildwood.kirinuki.ui.compose.components.BarIcon
import com.wanderwildwood.kirinuki.ui.compose.components.LineFenceCover
import com.wanderwildwood.kirinuki.ui.compose.components.LocalLineFence
import com.wanderwildwood.kirinuki.ui.compose.components.ReaderEdges
import com.wanderwildwood.kirinuki.ui.compose.components.rememberLineFence
import com.wanderwildwood.kirinuki.ui.compose.html.linearArticleContent
import com.wanderwildwood.kirinuki.ui.compose.theme.HouseButton
import com.wanderwildwood.kirinuki.ui.compose.theme.Icons
import com.wanderwildwood.kirinuki.ui.compose.utils.ProvideScaledText
import com.wanderwildwood.kirinuki.ui.compose.utils.textActions
import com.wanderwildwood.kirinuki.ui.qr.QrLinkDialog
import java.net.URI

/**
 * A single story from the web, read without subscribing: what a shared link or a scanned code
 * leads to when the address is a page rather than a feed. The same renderer, page edges and
 * line fence as the article reader, and the same QR code at the foot to finish it elsewhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPageScreen(
    url: String,
    onBack: () -> Unit,
    onFollowCapsule: (String) -> Unit,
    viewModel: WebPageViewModel,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val kept by viewModel.kept.collectAsStateWithLifecycle()
    val lineFence = rememberLineFence(listState)
    var qrOpen by remember { mutableStateOf(false) }

    LaunchedEffect(url) { viewModel.load(url) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarMMD(
                title = {
                    TextMMD(
                        text = hostOf(url),
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
                    // Read and gone unless it is kept; the star keeps it under Pages, as
                    // Save the page on Add feed does, and stays filled once it has.
                    BarIcon(
                        icon = if (kept) Icons.Star else Icons.StarBorder,
                        contentDescription = stringResource(if (kept) R.string.web_page_kept else R.string.web_page_keep),
                        onClick = { viewModel.keep() },
                    )
                    BarIcon(
                        icon = Icons.Refresh,
                        contentDescription = stringResource(R.string.sync),
                        onClick = { viewModel.reload() },
                    )
                },
            )
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            CompositionLocalProvider(LocalLineFence provides lineFence) {
                SelectionContainer(modifier = Modifier.textActions()) {
                    ReaderColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                        rowStep = { lineFence.stepToCut() },
                    ) {
                        when (val current = state) {
                            is WebPageState.Loading ->
                                item {
                                    TextMMD(
                                        text = stringResource(R.string.fetching_full_article),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                            is WebPageState.Problem ->
                                item {
                                    TextMMD(text = current.message, modifier = Modifier.fillMaxWidth())
                                }

                            is WebPageState.Page -> {
                                current.title?.let { title ->
                                    item {
                                        ProvideScaledText(style = MaterialTheme.typography.bodyLarge) {
                                            TextMMD(
                                                text = title,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                                linearArticleContent(
                                    articleContent = current.article,
                                    // As in the article reader: only a capsule link is followed;
                                    // the web is not something this phone can open.
                                    onLinkClick = { target, _ ->
                                        if (isSmolnetUrl(target)) onFollowCapsule(target)
                                    },
                                )
                            }
                        }

                        // At the foot, as in the article reader: the story's address as a QR
                        // code, to finish it on a computer or another phone. Shown whatever the
                        // fetch made of the page, since a page this app cannot read is exactly
                        // the one worth opening somewhere else.
                        item {
                            HouseButton(
                                label = stringResource(R.string.qr_show_link),
                                onClick = { qrOpen = true },
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            )
                        }
                    }
                }
            }

            // The line the bottom edge cuts is covered, so the page stops on a whole one.
            LineFenceCover(fence = lineFence, insetTop = 8.dp, insetBottom = 8.dp)

            // Last, so it is on top: the edges take their taps before the text under them does.
            ReaderEdges(listState = listState)
        }
    }

    if (qrOpen) {
        QrLinkDialog(link = url, onDismiss = { qrOpen = false })
    }
}

private fun hostOf(url: String): String =
    try {
        URI(url).host?.removePrefix("www.") ?: url
    } catch (e: Exception) {
        url
    }
