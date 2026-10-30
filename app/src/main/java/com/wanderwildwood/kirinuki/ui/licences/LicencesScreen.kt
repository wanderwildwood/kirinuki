package com.wanderwildwood.kirinuki.ui.licences

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.ui.PagedColumn
import com.wanderwildwood.kirinuki.ui.compose.components.BarIcon
import com.wanderwildwood.kirinuki.ui.compose.theme.Icons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The legal pieces in one place: where the source is, the libraries and their licences, and the
 * full text of the licences. Reached from About. The texts are read from the app's assets and
 * paged a row to a paragraph, like every list here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicencesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val gpl by produceTexts(context, "LICENSE")
    val apache by produceTexts(context, "licences/Apache-2.0.txt")
    val mit by produceTexts(context, "licences/MIT.txt")

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.licences)) },
                navigationIcon = {
                    BarIcon(
                        icon = Icons.Back,
                        contentDescription = stringResource(R.string.go_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        PagedColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                TextMMD(
                    text = stringResource(R.string.licences_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item { SourceLink() }
            item { Heading(stringResource(R.string.licences_libraries)) }
            LIBRARIES.forEach { library ->
                item {
                    TextMMD(
                        text = "${library.name}, ${library.by}: ${library.licence}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item { Heading(stringResource(R.string.licences_gpl)) }
            gpl.forEach { item { Paragraph(it) } }
            item { Heading(stringResource(R.string.licences_apache)) }
            apache.forEach { item { Paragraph(it) } }
            item { Heading(stringResource(R.string.licences_mit)) }
            mit.forEach { item { Paragraph(it) } }
        }
    }
}

@Composable
private fun produceTexts(context: Context, asset: String): State<List<String>> =
    produceState(initialValue = emptyList<String>(), asset) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    licenceParagraphs(context.assets.open(asset).bufferedReader().use { it.readText() })
                }.getOrDefault(emptyList())
            }
    }

@Composable
private fun Heading(text: String) {
    TextMMD(text = text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun Paragraph(text: String) {
    TextMMD(text = text, style = MaterialTheme.typography.bodyMedium)
}

/** The source address, which opens in a browser; a phone with none says so rather than doing nothing. */
@Composable
fun SourceLink(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val none = stringResource(R.string.about_no_browser)
    TextMMD(
        text = stringResource(R.string.licences_source, SOURCE_URL.removePrefix("https://")),
        style = MaterialTheme.typography.bodyMedium,
        textDecoration = TextDecoration.Underline,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) }
                        .onFailure { Toast.makeText(context, none, Toast.LENGTH_SHORT).show() }
                }
                .wrapContentHeight(Alignment.CenterVertically),
    )
}
