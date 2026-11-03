package com.wanderwildwood.kirinuki.ui.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.ui.compose.theme.EInkDialog
import com.wanderwildwood.kirinuki.ui.compose.theme.HouseButton

/**
 * A story's address as a QR code, for a computer or another phone to open: the Kompakt has no
 * browser of its own, and typing an address off this screen is the slow way. Drawn here from
 * the address alone; nothing is fetched or sent.
 */
@Composable
fun QrLinkDialog(
    link: String,
    onDismiss: () -> Unit,
) {
    val matrix = remember(link) { runCatching { QrCodes.encode(link) }.getOrNull() }
    val description = stringResource(R.string.qr_show_link)
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(
            text = stringResource(R.string.qr_show_link_hint),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        if (matrix != null) {
            // Black squares on white, each the same whole width, and no smoothing between them:
            // the panel shows hard edges best, and a camera reads them best.
            Canvas(
                modifier =
                    Modifier
                        .padding(vertical = 12.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .semantics { contentDescription = description },
            ) {
                drawRect(Color.White)
                val cell = (minOf(size.width, size.height) / matrix.width).toInt().toFloat().coerceAtLeast(1f)
                val side = cell * matrix.width
                val left = ((size.width - side) / 2).toInt().toFloat()
                val top = ((size.height - side) / 2).toInt().toFloat()
                for (y in 0 until matrix.height) {
                    for (x in 0 until matrix.width) {
                        if (matrix[x, y]) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(left + x * cell, top + y * cell),
                                size = Size(cell, cell),
                            )
                        }
                    }
                }
            }
        } else {
            TextMMD(
                text = stringResource(R.string.qr_too_long),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        }
        // The address in words as well, for checking where it goes and for copying by hand.
        SelectionContainer {
            TextMMD(
                text = link,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }
        HouseButton(
            label = stringResource(R.string.qr_close),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
