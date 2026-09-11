package com.wanderwildwood.kirinuki.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * One thing to do, in the bar at the top.
 *
 * Words here are expensive: three of them and there is no room left to say which feed
 * you are in. The cog is the house's settings door (STYLE.md), and the rest keep it
 * company rather than the bar being half words and half glyphs.
 */
@Composable
fun BarIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onBackground,
        modifier =
            modifier
                .clickable(onClick = onClick)
                .padding(8.dp)
                .size(24.dp),
    )
}
