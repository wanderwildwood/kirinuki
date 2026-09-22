package com.wanderwildwood.kirinuki.ui.compose.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.ui.compose.components.BarAction

/**
 * The text scale, asked for from the place the text is.
 *
 * It lives in settings as well, and that is still where you go to set it once and forget it.
 * This is for the other case: an article that arrived at a size you cannot read, when settings
 * is three presses and a return journey away, and when the thing to judge the size against is
 * the page in front of you rather than a row in a list.
 *
 * Nothing is applied on dismissal, because nothing is pending -- each press writes the setting
 * and the article behind the panel is already redrawing at the new size. That is also what the
 * dialog's one repaint buys: you watch the result while you decide.
 */
@Composable
fun TextScaleDialog(
    scale: Float,
    onScale: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(
            text = stringResource(R.string.text_scale),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextMMD(
                text = stringResource(R.string.text_scale_percent, (scale * 100).toInt().toString()),
                modifier = Modifier.weight(1f),
            )
            // The same bounds as the settings row, so the two cannot disagree about what
            // the scale is allowed to be.
            BarAction("−", { onScale((scale - 0.1f).coerceAtLeast(0.5f)) })
            BarAction("+", { onScale((scale + 0.1f).coerceAtMost(3.0f)) })
        }
    }
}
