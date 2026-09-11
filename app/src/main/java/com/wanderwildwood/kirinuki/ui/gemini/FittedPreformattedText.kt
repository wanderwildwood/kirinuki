package com.wanderwildwood.kirinuki.ui.gemini

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD
import kotlin.math.floor
import kotlin.math.min

/**
 * Fixed-width text, shrunk until it fits.
 *
 * ⚠ A deliberate suspension of the house rule that there is one type size and no size
 * jumps. That rule is about a designer choosing emphasis; this is content that arrives
 * carrying its own measure. A gopher text file was written against 80 columns, and
 * reflowing it destroys the tables, the art and anything aligned — so the page is fitted
 * to the screen rather than the text to the page.
 *
 * Below [MIN_SIZE_SP] it stops shrinking and scrolls sideways instead: type too small to
 * read is not a fit, it is a different failure.
 */
@Composable
fun FittedPreformattedText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val widestLine = remember(text) { text.lineSequence().maxOfOrNull { it.length } ?: 0 }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableSp = with(LocalDensity.current) { maxWidth.toSp().value }

        // A monospace advance is near enough 0.6em across the faces this will meet that
        // measuring each one is not worth the layout pass.
        val fitted =
            when {
                widestLine <= 0 -> DEFAULT_SIZE_SP
                else -> floor(availableSp / (widestLine * MONO_ADVANCE))
            }

        val size = min(DEFAULT_SIZE_SP, fitted)
        val tooSmallToRead = size < MIN_SIZE_SP

        TextMMD(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = (if (tooSmallToRead) MIN_SIZE_SP else size).sp,
            softWrap = false,
            modifier =
                when {
                    tooSmallToRead -> Modifier.horizontalScroll(rememberScrollState())
                    else -> Modifier
                },
        )
    }
}

private const val DEFAULT_SIZE_SP = 16f
private const val MIN_SIZE_SP = 9f
private const val MONO_ADVANCE = 0.6f
