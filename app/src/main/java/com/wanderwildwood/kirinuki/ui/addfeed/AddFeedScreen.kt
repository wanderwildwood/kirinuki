package com.wanderwildwood.kirinuki.ui.addfeed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.net.gemini.isGeminiUrl
import com.wanderwildwood.kirinuki.ui.compose.components.BarIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeedScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onRead: (String) -> Unit,
    viewModel: AddFeedViewModel,
    modifier: Modifier = Modifier,
    initialUrl: String = "",
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var title by rememberSaveable { mutableStateOf("") }
    val error by viewModel.error.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    LaunchedEffect(saved) {
        if (saved) {
            viewModel.savedHandled()
            onSaved()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.add_feed)) },
                navigationIcon = {
                    BarIcon(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.go_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
        ) {
            TextFieldMMD(
                value = url,
                onValueChange = { url = it },
                label = { TextMMD(text = stringResource(R.string.add_feed_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextFieldMMD(
                value = title,
                onValueChange = { title = it },
                label = { TextMMD(text = stringResource(R.string.title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                TextMMD(text = stringResource(R.string.add_feed_url_invalid))
            }
            ButtonMMD(
                onClick = { viewModel.save(url = url, title = title) },
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextMMD(text = stringResource(R.string.subscribe))
            }

            // A capsule address is as likely to be a page as a gemlog, and subscribing to
            // a page gets you a feed that can never have an entry in it. Offer the other
            // thing here rather than making that the only way in.
            if (isGeminiUrl(url.trim())) {
                OutlinedButtonMMD(
                    onClick = { onRead(url.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextMMD(text = stringResource(R.string.read_it))
                }
            }
        }
    }
}
