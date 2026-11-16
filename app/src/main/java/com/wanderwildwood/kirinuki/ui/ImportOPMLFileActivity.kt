package com.wanderwildwood.kirinuki.ui

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.base.DIAwareComponentActivity
import com.wanderwildwood.kirinuki.model.opml.OpmlPreview
import com.wanderwildwood.kirinuki.model.opml.importOpml
import com.wanderwildwood.kirinuki.model.opml.previewOpml
import com.wanderwildwood.kirinuki.ui.compose.theme.HouseButton
import com.wanderwildwood.kirinuki.ui.compose.utils.withAllProviders
import com.wanderwildwood.kirinuki.util.logDebug
import kotlinx.coroutines.launch

/**
 * Started by opening an OPML file from somewhere else on the phone, so by another app. It reads
 * the file first and asks: the file's name, how many feeds it would add, and whether it would
 * change settings, with Import and Cancel. Nothing is saved before Import. An import started
 * from Feeds' own settings goes straight in; the person chose the file there themselves.
 */
class ImportOPMLFileActivity : DIAwareComponentActivity() {
    private sealed interface Step {
        data object Reading : Step

        data class Ask(
            val name: String,
            val preview: OpmlPreview,
        ) : Step

        data object Unreadable : Step

        data object Importing : Step

        data object Done : Step
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdgeDarkIcons()

        // Content only, as the manifest's filter says: a file:// address sent straight here
        // could name one of this app's own files.
        val uri = intent.data?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
        logDebug(LOG_TAG, "Uri: $uri")

        setContent {
            withAllProviders {
                var step by remember { mutableStateOf<Step>(Step.Reading) }

                LaunchedEffect(uri) {
                    if (uri == null) {
                        step = Step.Unreadable
                        return@LaunchedEffect
                    }
                    val preview = previewOpml(di, uri)
                    step = if (preview == null) Step.Unreadable else Step.Ask(displayName(uri), preview)
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                ) {
                    when (val s = step) {
                        Step.Reading, Step.Importing ->
                            TextMMD(text = stringResource(R.string.import_feeds_from_opml))

                        is Step.Ask -> {
                            TextMMD(text = stringResource(R.string.opml_import_ask_title, s.name))
                            TextMMD(
                                text =
                                    if (s.preview.newFeeds > 0) {
                                        pluralStringResource(R.plurals.opml_import_ask_new, s.preview.newFeeds, s.preview.newFeeds)
                                    } else {
                                        stringResource(R.string.opml_import_ask_none_new)
                                    },
                            )
                            if (s.preview.knownFeeds > 0) {
                                TextMMD(
                                    text = pluralStringResource(R.plurals.opml_import_ask_known, s.preview.knownFeeds, s.preview.knownFeeds),
                                )
                            }
                            if (s.preview.hasSettings) {
                                TextMMD(text = stringResource(R.string.opml_import_ask_settings))
                            }
                            HouseButton(
                                label = stringResource(R.string.opml_import),
                                onClick = {
                                    step = Step.Importing
                                    lifecycleScope.launch {
                                        importOpml(di, uri!!)
                                        step = Step.Done
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HouseButton(
                                label = stringResource(R.string.cancel),
                                onClick = { finish() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Step.Unreadable -> {
                            TextMMD(text = stringResource(R.string.opml_import_ask_unreadable))
                            HouseButton(
                                label = stringResource(R.string.qr_close),
                                onClick = { finish() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Step.Done -> {
                            TextMMD(text = stringResource(R.string.imported_feeds_from_opml))
                            ButtonMMD(
                                onClick = { onNavigateUpFromIntentActivities() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                TextMMD(text = stringResource(R.string.open_feed))
                            }
                        }
                    }
                }
            }
        }
    }

    /** The file's name as the app that holds it gives it, or the last part of its address. */
    private fun displayName(uri: Uri): String =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty()

    companion object {
        private const val LOG_TAG = "KIRINUKI_OPMLIMPORT"
    }
}
