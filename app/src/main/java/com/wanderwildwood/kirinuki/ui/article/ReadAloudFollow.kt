package com.wanderwildwood.kirinuki.ui.article

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.wanderwildwood.kirinuki.model.html.LinearArticle
import com.wanderwildwood.kirinuki.model.readaloud.ReadAloudScript
import com.wanderwildwood.kirinuki.model.readaloud.Spoken
import com.wanderwildwood.kirinuki.ui.ScrollDirection
import com.wanderwildwood.kirinuki.ui.compose.components.turnPage

/**
 * The page follows the voice: when the sentence being read runs off the foot, the page turns,
 * the same turn as a swipe or an edge tap, so it lands on a whole line. Checked once a sentence,
 * never in between.
 *
 * It follows only a reader who is following. If the sentence before was not on the screen -- the
 * reader has paged on ahead, or back -- the page is left where they put it. A press in the panel
 * that moves the voice ([jump]) brings the page to it wherever it was.
 */
@Composable
fun FollowSpoken(
    spoken: Spoken?,
    content: LinearArticle,
    title: String,
    listState: LazyListState,
    jump: () -> Boolean,
) {
    val previous = remember { arrayOfNulls<Spoken>(1) }
    LaunchedEffect(spoken) {
        val now = spoken ?: run {
            previous[0] = null
            return@LaunchedEffect
        }
        val before = previous[0]
        previous[0] = now
        val lengthOf = { s: Spoken -> blockLength(s, content, title) }
        val forced = jump()
        if (!forced && before != null && listState.whereIs(before, lengthOf(before)) != Where.SHOWING) return@LaunchedEffect

        // Through a block taller than the screen a turn at a time; a few at most, since a
        // sentence is never longer than a page or two.
        repeat(MAX_TURNS) {
            when (listState.whereIs(now, lengthOf(now))) {
                Where.SHOWING -> return@LaunchedEffect
                Where.JUST_BELOW -> listState.turnPage(ScrollDirection.DOWN)
                Where.ELSEWHERE -> {
                    listState.scrollToItem(itemOf(now))
                    return@LaunchedEffect
                }
            }
        }
    }
}

internal enum class Where { SHOWING, JUST_BELOW, ELSEWHERE }

/** The list item a sentence is drawn in: the title is the first, the article's blocks follow. */
internal fun itemOf(spoken: Spoken): Int = if (spoken.element == Spoken.TITLE) 0 else spoken.element + 1

private fun blockLength(
    spoken: Spoken,
    content: LinearArticle,
    title: String,
): Int =
    if (spoken.element == Spoken.TITLE) {
        title.length
    } else {
        content.elements.getOrNull(spoken.element)?.let { ReadAloudScript.speakable(it)?.length } ?: 0
    }

/**
 * Where a sentence is on the screen, reckoned from where it falls in its block's text: a
 * sentence half way through a paragraph is taken to be half way down it. Close enough to tell
 * whether it is on the page, which is all this is asked.
 */
private fun LazyListState.whereIs(
    spoken: Spoken,
    blockLength: Int,
): Where {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemOf(spoken) }
    val bottom = layoutInfo.viewportSize.height
    if (info == null) {
        // Not on screen at all. Just after the last block showing is the next page; anything
        // else is a jump.
        val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return Where.ELSEWHERE
        return if (itemOf(spoken) == last + 1) Where.JUST_BELOW else Where.ELSEWHERE
    }
    val (top, foot) = sentenceSpan(info.offset, info.size, spoken.start, spoken.end, blockLength)
    return when {
        top < 0 -> Where.ELSEWHERE
        foot <= bottom -> Where.SHOWING
        top < bottom -> Where.JUST_BELOW
        else -> Where.JUST_BELOW
    }
}

/** The pixels a sentence from [start] to [end] of a [length]-long text takes in a block at [offset]. */
internal fun sentenceSpan(
    offset: Int,
    size: Int,
    start: Int,
    end: Int,
    length: Int,
): Pair<Int, Int> {
    if (length <= 0) return offset to offset + size
    val top = offset + (size.toLong() * start / length).toInt()
    val foot = offset + (size.toLong() * end / length).toInt()
    return top to foot
}

private const val MAX_TURNS = 3
