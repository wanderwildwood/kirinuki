package com.wanderwildwood.kirinuki.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import com.mudita.mmd.ThemeMMD

/**
 * MMD decides type, switches, buttons and dividers, so there is no palette and no
 * typography of our own here -- only the few styles the article renderer asks for
 * by name, and the scale the reader can change.
 */
@Composable
fun KirinukiTheme(content: @Composable () -> Unit) {
    ThemeMMD(colorScheme = monochrome, content = content)
}

@Immutable
data class TypographySettings(
    val fontScale: Float,
    val sansFontFamily: FontFamily = FontFamily.Default,
    val monoFontFamily: FontFamily = FontFamily.Monospace,
    val serifFontFamily: FontFamily = FontFamily.Serif,
)

val LocalTypographySettings: ProvidableCompositionLocal<TypographySettings> =
    compositionLocalOf { TypographySettings(fontScale = 1f) }

/**
 * The app ships no fonts of its own: MMD bundles Lato, and a second copy of a
 * typeface is weight in the APK to say the same thing twice. Only the scale is ours.
 */
@Composable
fun ProvideTypographySettings(
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTypographySettings provides TypographySettings(fontScale = fontScale),
        content = content,
    )
}

@Composable
fun LinkTextStyle(): TextStyle =
    TextStyle(
        color = MaterialTheme.colorScheme.onBackground,
        textDecoration = TextDecoration.Underline,
    )

@Composable
fun CodeInlineStyle(): SpanStyle =
    SpanStyle(
        fontFamily = LocalTypographySettings.current.monoFontFamily,
        background = CodeBlockBackground(),
    )

/**
 * Sixteen greys and a slow redraw: a code block is marked by a light ground, not by a colour.
 */
@Composable
fun CodeBlockBackground(): Color = if (isSystemInDarkTheme()) Color(0xFF2B2B2B) else Color(0xFFEDEDED)

@Composable
fun OnCodeBlockBackground(): Color = MaterialTheme.colorScheme.onBackground
