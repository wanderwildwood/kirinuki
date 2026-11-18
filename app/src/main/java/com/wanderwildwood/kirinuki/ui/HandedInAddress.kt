package com.wanderwildwood.kirinuki.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.ui.compose.theme.EInkDialog
import com.wanderwildwood.kirinuki.ui.compose.theme.HouseButton
import java.util.UUID

/**
 * An address another app handed in, shown whole before anything is fetched from it. A tapped
 * link or a share can come from any app, so nothing goes out until Open is pressed here.
 */
@Composable
fun HandedInAddressDialog(
    address: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(
            text = stringResource(R.string.handed_in_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        TextMMD(
            text = stringResource(R.string.handed_in_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        TextMMD(
            text = address,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HouseButton(
                label = stringResource(R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            HouseButton(
                label = stringResource(R.string.handed_in_open),
                onClick = onOpen,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Add feed, opened by a share, hands an address to the app only after Open was pressed there.
 * This token, made fresh each time the app starts and never written anywhere, marks such a
 * hand-over, so the app does not ask a second time. Another app cannot know it.
 */
internal object HandOver {
    const val EXTRA_TOKEN = "com.wanderwildwood.kirinuki.HAND_OVER_TOKEN"
    private val token: String = UUID.randomUUID().toString()

    fun mark(intent: Intent): Intent = intent.putExtra(EXTRA_TOKEN, token)

    fun isOurs(intent: Intent?): Boolean = intent?.getStringExtra(EXTRA_TOKEN) == token
}
