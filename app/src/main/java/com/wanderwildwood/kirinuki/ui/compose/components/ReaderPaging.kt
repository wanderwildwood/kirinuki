package com.wanderwildwood.kirinuki.ui.compose.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.mudita.mmd.components.lazy.LazyDefaultsMMD
import com.wanderwildwood.kirinuki.ui.ScrollDirection

/**
 * How far one swipe moves a page of text: to the block the bottom of the screen cut in half.
 *
 * MMD's list steps a fixed four items per swipe, which is the right measure for the lists it
 * was built for, where an item is a row of a known height. A reader's items are the article's
 * own paragraphs, and four of those are as likely to be three screens as half of one -- so a
 * swipe stepped past whatever did not fit, and the text in between was never shown on any
 * page. That is the whole of the complaint: the article reads with holes in it.
 *
 * The measure is taken from the screen rather than written down. The next page opens on the
 * first block that is not wholly on this one, so the paragraph the bottom edge cut through is
 * the first thing under the top edge afterwards, whole. Nothing is skipped and nothing is
 * read twice, which is the same bargain a printed page strikes.
 *
 * ⚠ **A block taller than the screen is the one case this cannot serve.** Stepping by index
 * can only ever put a block's *top* under the top edge, so the rest of an over-long paragraph
 * -- a code block, a wall of gopher text -- is not reachable by swiping at all, and the step
 * falls back to one so that the swipe at least moves. The volume keys are the way through
 * those: they page by pixels and stop wherever the text does. See [turnPage].
 */
@Composable
fun rememberReaderScrollStep(state: LazyListState): Int {
    val step by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            readerScrollStep(
                blocks = info.visibleItemsInfo.map { Block(it.index, it.offset + it.size) },
                viewportEnd = info.viewportEndOffset,
            )
        }
    }
    return step
}

/** A block of the article as the screen has it: which one it is, and where it ends. */
internal data class Block(
    val index: Int,
    val bottom: Int,
)

/**
 * The step itself, apart from Compose so it can be tested against a screen written down.
 *
 * [LazyDefaultsMMD.SCROLL_STEP] is what MMD would have done, and is what this answers before
 * the list has measured itself -- there is nothing better to say at that point, and by the
 * time a finger arrives there always is.
 */
internal fun readerScrollStep(
    blocks: List<Block>,
    viewportEnd: Int,
): Int {
    val first = blocks.firstOrNull() ?: return LazyDefaultsMMD.SCROLL_STEP
    val lastWhole = blocks.lastOrNull { it.bottom <= viewportEnd }
    val target = (lastWhole?.index ?: first.index) + 1
    return (target - first.index).coerceAtLeast(1)
}

/**
 * What a volume key does: a screenful of pixels, less a tenth of one to read on from.
 *
 * By pixels rather than by blocks, so this reaches into the middle of a paragraph that is
 * taller than the screen -- the one place [rememberReaderScrollStep] cannot go. The overlap
 * is there because the line the edge cut through is easier to find again than to reconstruct.
 */
suspend fun LazyListState.turnPage(direction: ScrollDirection) {
    val info = layoutInfo
    val viewport = info.viewportEndOffset - info.viewportStartOffset
    if (viewport <= 0) return
    val page = viewport * PAGE
    scrollBy(
        when (direction) {
            ScrollDirection.DOWN -> page
            ScrollDirection.UP -> -page
        },
    )
}

/** How much of the screen a volume key turns. The tenth left behind is the overlap. */
private const val PAGE = 0.9f
