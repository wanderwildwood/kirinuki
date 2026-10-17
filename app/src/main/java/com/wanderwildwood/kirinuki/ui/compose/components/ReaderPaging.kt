package com.wanderwildwood.kirinuki.ui.compose.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import com.wanderwildwood.kirinuki.ui.ScrollDirection

/**
 * What a volume key does: a screenful of pixels, less a tenth of one to read on from.
 *
 * By pixels rather than by blocks, so it stops wherever the text does, even in the middle of
 * a paragraph. The overlap is there because the line the edge cut through is easier to find
 * again than to reconstruct. A swipe afterwards goes on from the block the foot cut.
 */
suspend fun LazyListState.turnPage(direction: ScrollDirection) {
    val info = layoutInfo
    val viewport = info.viewportEndOffset - info.viewportStartOffset
    if (viewport <= 0) return
    val page = viewport * PAGE
    // Going on, a reader that covered a cut line opens the next page on exactly that line --
    // flush with the top, since lines touch and any gap above shows a sliver of the one
    // before. Never less than half a page, so one very tall cut block cannot make it crawl.
    val toCovered =
        LineFence.of(this)?.coveredFrom
            ?.takeIf { it >= viewport / 2f }
    scrollBy(
        when (direction) {
            ScrollDirection.DOWN -> toCovered ?: page
            ScrollDirection.UP -> -page
        },
    )
}

/** How much of the screen a volume key turns. The tenth left behind is the overlap. */
private const val PAGE = 0.9f
