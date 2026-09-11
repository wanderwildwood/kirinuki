package com.wanderwildwood.kirinuki.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.base.DIAwareComponentActivity
import com.wanderwildwood.kirinuki.model.opml.importOpml
import com.wanderwildwood.kirinuki.ui.compose.utils.withAllProviders
import com.wanderwildwood.kirinuki.util.logDebug
import kotlinx.coroutines.launch

/**
 * Started by opening an OPML file from somewhere else on the phone. It imports and
 * says so; there is nothing to decide, so there is nothing to ask.
 */
class ImportOPMLFileActivity : DIAwareComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val uri = intent.data
        logDebug(LOG_TAG, "Uri: $uri")

        setContent {
            withAllProviders {
                var done by remember { mutableStateOf(false) }

                LaunchedEffect(uri) {
                    if (uri == null) {
                        done = true
                        return@LaunchedEffect
                    }
                    lifecycleScope
                        .launch {
                            importOpml(di, uri)
                        }.join()
                    done = true
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                ) {
                    TextMMD(
                        text =
                            when {
                                done -> stringResource(R.string.imported_feeds_from_opml)
                                else -> stringResource(R.string.import_feeds_from_opml)
                            },
                    )
                    if (done) {
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

    companion object {
        private const val LOG_TAG = "KIRINUKI_OPMLIMPORT"
    }
}
