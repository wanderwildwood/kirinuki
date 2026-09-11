package com.wanderwildwood.kirinuki.ui.gemini

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.net.isSmolnetUrl
import com.wanderwildwood.kirinuki.ui.compose.components.BarIcon
import com.wanderwildwood.kirinuki.ui.compose.html.linearArticleContent
import java.net.URI

/**
 * A page of a capsule. The same renderer the article screen uses, because gemtext is
 * parsed into the same model — which is most of why this is a small screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiPageScreen(
    url: String,
    onBack: () -> Unit,
    onFollow: (String) -> Unit,
    onSubscribe: (String) -> Unit,
    viewModel: GeminiPageViewModel,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.go_back),
                        onClick = onBack,
                    )
                },
                actions = {
                    // Following a capsule usually occurs to you while reading it.
                    BarIcon(
                        icon = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.subscribe_to_this),
                        onClick = { onSubscribe(url) },
                    )
                    BarIcon(
                        icon = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.sync),
                        onClick = { viewModel.reload() },
                    )
                },
            )
        },
    ) { padding ->
        LazyColumnMMD(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            when (val current = state) {
                is GeminiPageState.Loading ->
                    item {
                        TextMMD(
                            text = stringResource(R.string.fetching_full_article),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                is GeminiPageState.Preformatted ->
                    item {
                        FittedPreformattedText(text = current.text)
                    }

                is GeminiPageState.Problem ->
                    item {
                        TextMMD(text = current.message, modifier = Modifier.fillMaxWidth())
                        current.detail?.let { TextMMD(text = it, modifier = Modifier.fillMaxWidth()) }
                    }

                is GeminiPageState.Page ->
                    linearArticleContent(
                        articleContent = current.article,
                        onLinkClick = { target, _ ->
                            // A smolnet link stays in the app. Anything else is somebody
                            // else's protocol and goes to whatever handles it -- a gopher
                            // menu can point at the web, and often does.
                            if (isSmolnetUrl(target)) {
                                onFollow(target)
                            } else {
                                context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
                            }
                        },
                    )
            }
        }
    }
}

private fun hostOf(url: String): String =
    try {
        URI(url).host ?: url
    } catch (e: IllegalArgumentException) {
        url
    }
