package com.wanderwildwood.kirinuki.ui.compose.utils

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import com.wanderwildwood.kirinuki.ui.compose.theme.LocalTypographySettings

/**
 * Scale the text the reader is about to read by whatever they set in settings.
 *
 * ⚠ A size may be Unspecified, and multiplying one throws — "Cannot perform operation
 * for Unspecified type" — which takes the whole screen down at composition. MMD's
 * typography leaves sizes unspecified where it means "inherit", which upstream's own
 * typography never did, so this arithmetic was safe in Feeder and is not here. Scale
 * what is there, leave the rest alone.
 */
@Composable
fun ProvideScaledText(
    style: TextStyle = LocalTextStyle.current,
    content: @Composable () -> Unit,
) {
    val fontScale = LocalTypographySettings.current.fontScale

    ProvideTextStyle(
        style.merge(
            TextStyle(
                fontSize = style.fontSize.scaledBy(fontScale),
                lineHeight = style.lineHeight.scaledBy(fontScale),
            ),
        ),
    ) {
        content()
    }
}

private fun TextUnit.scaledBy(factor: Float): TextUnit =
    when {
        isSpecified -> this * factor
        else -> TextUnit.Unspecified
    }
