package com.wanderwildwood.kirinuki.ui.compose.components

import androidx.compose.foundation.lazy.LazyListState
import com.wanderwildwood.kirinuki.ui.ScrollDirection
import com.wanderwildwood.kirinuki.ui.pageBack
import com.wanderwildwood.kirinuki.ui.pageForward

/**
 * What a volume key or an edge tap does: the same page turn as a swipe, on whole rows.
 *
 * A turn by pixels would not agree with the rows, which a page shares the foot's spare room
 * between and sets the ones that do not fit out of sight, and every line past the foot would be
 * lost to a page that started further on. Through a row taller than the screen, which has no whole
 * rows to turn on, the next page opens on the line the foot cut, flush with the top.
 */
suspend fun LazyListState.turnPage(direction: ScrollDirection) {
    when (direction) {
        ScrollDirection.DOWN ->
            pageForward(LineFence.of(this)?.let { fence -> { fence.stepToCut() } })
        ScrollDirection.UP -> pageBack()
    }
}
