package com.wanderwildwood.kirinuki.ui.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.WeakHashMap

/**
 * Where every line of text on the reader's screen is, so the bottom edge can stop on a whole one.
 *
 * A list cuts whatever reaches its edge, and in an article that is a line sliced through its
 * middle -- half a line of letters nobody can read, and on E Ink a stripe that stays until the
 * next full redraw. Each paragraph reports its lines here, in screen coordinates, and the
 * reader covers the one the edge cut. A swipe or a page turn then brings that line up whole.
 */
class LineFence {
    internal val lines = mutableStateMapOf<Any, List<ClosedFloatingPointRange<Float>>>()

    /**
     * How far down the reader the covered line starts, as last drawn, or null when no line is
     * cut. A page turn goes exactly this far, so the covered line opens the next page whole --
     * rather than a fixed nine tenths, which cut a line at the top edge instead.
     */
    internal var coveredFrom: Float? = null

    /** The top of the first line the given bottom edge cuts through, or null if none is cut. */
    internal fun cutAt(bottom: Float): Float? =
        lines.values
            .asSequence()
            .flatten()
            .filter { it.start < bottom && it.endInclusive > bottom + SLACK }
            .minOfOrNull { it.start }

    companion object {
        /** A line that overhangs the edge by less than this is taken as whole. */
        private const val SLACK = 1f

        /** Which fence belongs to which reader's list, for the page turn to ask. */
        private val byList = WeakHashMap<LazyListState, LineFence>()

        internal fun of(list: LazyListState): LineFence? = byList[list]

        internal fun attach(
            list: LazyListState,
            fence: LineFence,
        ) {
            byList[list] = fence
        }

        internal fun detach(list: LazyListState) {
            byList.remove(list)
        }
    }
}

val LocalLineFence = staticCompositionLocalOf<LineFence?> { null }

/**
 * What a paragraph needs in order to report its lines: the layout callback for its text, and a
 * modifier that knows where the paragraph is on the screen. Does nothing outside a reader.
 */
class FencedText internal constructor(
    val onTextLayout: (TextLayoutResult) -> Unit,
    val modifier: Modifier,
)

/** A fence for [list], known to the page turn for as long as the reader is on screen. */
@Composable
fun rememberLineFence(list: LazyListState): LineFence {
    val fence = remember { LineFence() }
    DisposableEffect(list, fence) {
        LineFence.attach(list, fence)
        onDispose { LineFence.detach(list) }
    }
    return fence
}

@Composable
fun rememberFencedText(): FencedText {
    val fence = LocalLineFence.current
    val key = remember { Any() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun publish() {
        val f = fence ?: return
        val l = layout ?: return
        val c = coordinates?.takeIf { it.isAttached } ?: return
        val top = c.positionInRoot().y
        f.lines[key] = (0 until l.lineCount).map { top + l.getLineTop(it)..top + l.getLineBottom(it) }
    }

    DisposableEffect(fence, key) {
        onDispose { fence?.lines?.remove(key) }
    }

    return FencedText(
        onTextLayout = {
            layout = it
            publish()
        },
        modifier =
            if (fence == null) {
                Modifier
            } else {
                Modifier.onGloballyPositioned {
                    coordinates = it
                    publish()
                }
            },
    )
}

/**
 * Covers the line the bottom of the reader cuts, leaving the scrollbar's column alone.
 *
 * Drawn only: a Canvas takes no touches, so a link above it is still a link and the page-turning
 * edges still turn the page.
 */
@Composable
fun BoxScope.LineFenceCover(
    fence: LineFence,
    scrollbarColumn: Dp = 40.dp,
) {
    var box by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val background = MaterialTheme.colorScheme.background
    val scrollbarPx = with(LocalDensity.current) { scrollbarColumn.toPx() }
    Canvas(
        modifier =
            Modifier
                .matchParentSize()
                .onGloballyPositioned { box = it },
    ) {
        val c = box?.takeIf { it.isAttached } ?: return@Canvas
        val top = c.positionInRoot().y
        val bottom = top + size.height
        val cut = fence.cutAt(bottom)
        fence.coveredFrom = cut?.let { (it - top).coerceAtLeast(0f) }
        val from = fence.coveredFrom ?: return@Canvas
        drawRect(
            color = background,
            topLeft = Offset(0f, from),
            size = Size((size.width - scrollbarPx).coerceAtLeast(0f), size.height - from),
        )
    }
}
