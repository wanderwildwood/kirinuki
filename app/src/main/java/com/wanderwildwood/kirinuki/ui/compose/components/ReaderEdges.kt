package com.wanderwildwood.kirinuki.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.ui.ScrollDirection
import kotlinx.coroutines.launch

/** The outer fifth of each side turns the page. The three fifths between are left alone. */
private const val EDGE_OF_A_SCREEN = 0.2f

/**
 * The column MMD's scrollbar draws itself in, kept out of the right-hand edge.
 *
 * ⚠ Without this the edge covers the scrollbar's own arrows -- they are about 28px from the
 * right of a 480px panel, well inside a fifth of it -- and every tap on one is taken here
 * instead. The arrows still turned the page, so nothing looked wrong; what was gone was the
 * measured step that steps to the next whole block, which is the better page of the two and
 * the one the release is for. Two page turns live on this screen and they are not rivals:
 * the arrow moves a block, the edge moves a screenful.
 */
private val SCROLLBAR_COLUMN = 40.dp

/**
 * Tap the left edge to go back a page, the right edge to go on.
 *
 * The scrollbar can only put a block's top under the top edge, so the one thing it cannot
 * reach is the inside of a block taller than the screen -- a long paragraph at a large text
 * scale, a code block, a wall of gopher text. The volume keys page by pixels and go there,
 * but not everything this runs on has volume keys: the reader who found the paging bug reads
 * on a Supernote, which has none. These are the volume keys for a screen that hasn't any.
 *
 * See [turnPage] for the measure, which is the same one the keys use.
 */
@Composable
fun BoxScope.ReaderEdges(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Row(modifier = Modifier.matchParentSize().then(modifier)) {
        PageEdge(stringResource(R.string.page_back)) {
            scope.launch { listState.turnPage(ScrollDirection.UP) }
        }
        // A Spacer takes no touches, so a link in the middle of a line is still a link.
        Spacer(modifier = Modifier.weight(1f - 2 * EDGE_OF_A_SCREEN))
        PageEdge(stringResource(R.string.page_on)) {
            scope.launch { listState.turnPage(ScrollDirection.DOWN) }
        }
        Spacer(modifier = Modifier.width(SCROLLBAR_COLUMN))
    }
}

@Composable
private fun RowScope.PageEdge(
    label: String,
    onTurn: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .weight(EDGE_OF_A_SCREEN)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    // No ripple: on E Ink a highlight costs a refresh and says nothing the
                    // page turn has not already said.
                    indication = null,
                    onClickLabel = label,
                    onClick = onTurn,
                ),
    )
}
