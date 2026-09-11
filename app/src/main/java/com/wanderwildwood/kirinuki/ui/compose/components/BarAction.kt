package com.wanderwildwood.kirinuki.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD

/**
 * A tappable word. Used where the thing to do has no glyph worth drawing at sixteen
 * greys -- a step up or down beside the value it changes.
 */
@Composable
fun BarAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextMMD(
        text = label,
        modifier =
            modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
