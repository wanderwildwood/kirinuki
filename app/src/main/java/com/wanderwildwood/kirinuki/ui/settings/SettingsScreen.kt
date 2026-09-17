package com.wanderwildwood.kirinuki.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.archmodel.SyncFrequency
import com.wanderwildwood.kirinuki.ui.compose.components.BarAction
import com.wanderwildwood.kirinuki.ui.compose.components.BarIcon
import com.wanderwildwood.kirinuki.ui.compose.theme.AboutDialog
import com.wanderwildwood.kirinuki.ui.compose.theme.Icons

/**
 * Everything worth deciding, on one screen. What is not here is not a setting:
 * the app has one look, and it is MMD's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onImportOpml: () -> Unit,
    onExportOpml: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val syncOnlyOnWifi by viewModel.syncOnlyOnWifi.collectAsStateWithLifecycle()
    val syncOnlyWhenCharging by viewModel.syncOnlyWhenCharging.collectAsStateWithLifecycle()
    val syncFrequency by viewModel.syncFrequency.collectAsStateWithLifecycle()
    val textScale by viewModel.textScale.collectAsStateWithLifecycle()
    val showReadArticles by viewModel.showReadArticles.collectAsStateWithLifecycle()

    var aboutOpen by remember { mutableStateOf(false) }


    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.action_settings)) },
                navigationIcon = {
                    BarIcon(
                        icon = Icons.Back,
                        contentDescription = stringResource(R.string.go_back),
                        onClick = onBack,
                    )
                },
                // About is not a setting, and it is the one thing a stranger looks for
                // before trusting an app. An i in the top right, as everywhere else here.
                actions = {
                    BarIcon(
                        icon = Icons.Info,
                        contentDescription = stringResource(R.string.about),
                        onClick = { aboutOpen = true },
                    )
                },
            )
        },
    ) { padding ->
        // MMD's list, not a scrolling Column: it steps four rows to a swipe and stops, and it
        // brings the chevron rail at both ends. A screen that coasts was the one screen in
        // the app that did not behave like the phone it is on.
        LazyColumnMMD(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            item {
                SwitchRow(
                    title = stringResource(R.string.only_on_wifi),
                    checked = syncOnlyOnWifi,
                    onCheckedChange = viewModel::setSyncOnlyOnWifi,
                )
            }
            item {
                HorizontalDividerMMD()
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.only_when_charging),
                    checked = syncOnlyWhenCharging,
                    onCheckedChange = viewModel::setSyncOnlyWhenCharging,
                )
            }
            item {
                HorizontalDividerMMD()
            }
            item {
                StepperRow(
                    title = stringResource(R.string.check_for_updates),
                    value = stringResource(syncFrequency.stringId),
                    onLess = { viewModel.setSyncFrequency(syncFrequency.previous()) },
                    onMore = { viewModel.setSyncFrequency(syncFrequency.next()) },
                )
            }
            item {
                HorizontalDividerMMD()
            }
            item {
                StepperRow(
                    title = stringResource(R.string.text_scale),
                    value = "${(textScale * 100).toInt()}%",
                    onLess = { viewModel.setTextScale((textScale - 0.1f).coerceAtLeast(0.5f)) },
                    onMore = { viewModel.setTextScale((textScale + 0.1f).coerceAtMost(3.0f)) },
                )
            }
            item {
                HorizontalDividerMMD()
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.show_read_articles),
                    checked = showReadArticles,
                    onCheckedChange = viewModel::setShowReadArticles,
                )
            }
            item {
                HorizontalDividerMMD()
            }
            item {
                ActionRow(title = stringResource(R.string.import_feeds_from_opml), onClick = onImportOpml)
            }
            item {
                HorizontalDividerMMD()
            }
            item {
                ActionRow(title = stringResource(R.string.export_feeds_to_opml), onClick = onExportOpml)
            }
            item {
                HorizontalDividerMMD()
            }
        }
    }

    if (aboutOpen) AboutDialog(onDismiss = { aboutOpen = false })
}

private fun SyncFrequency.next(): SyncFrequency {
    val values = SyncFrequency.entries
    return values[(values.indexOf(this) + 1).coerceAtMost(values.size - 1)]
}

private fun SyncFrequency.previous(): SyncFrequency {
    val values = SyncFrequency.entries
    return values[(values.indexOf(this) - 1).coerceAtLeast(0)]
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        TextMMD(text = title, modifier = Modifier.weight(1f))
        SwitchMMD(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TextMMD(text = title)
            TextMMD(text = value)
        }
        BarAction("−", onLess)
        BarAction("+", onMore)
    }

}

@Composable
private fun ActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextMMD(
        text = title,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
