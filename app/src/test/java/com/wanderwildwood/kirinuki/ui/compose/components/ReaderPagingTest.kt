package com.wanderwildwood.kirinuki.ui.compose.components

import com.mudita.mmd.components.lazy.LazyDefaultsMMD
import org.junit.Test
import kotlin.test.assertEquals

/**
 * A screen 800 tall, written down: each block is where it ends, and the step is how many
 * blocks a swipe moves on. What is being defended is that no block is ever stepped over
 * before it has been read.
 */
class ReaderPagingTest {
    @Test
    fun `stops on the block the screen cut in half`() {
        val blocks = listOf(Block(0, 200), Block(1, 500), Block(2, 900))

        assertEquals(2, readerScrollStep(blocks, VIEWPORT), "Should open on the cut block")
    }

    @Test
    fun `steps past every block when they all fit`() {
        val blocks = listOf(Block(3, 200), Block(4, 500), Block(5, 800))

        assertEquals(3, readerScrollStep(blocks, VIEWPORT), "Should open on the next one")
    }

    @Test
    fun `one tall block still moves`() {
        val blocks = listOf(Block(7, 2400))

        assertEquals(1, readerScrollStep(blocks, VIEWPORT), "A swipe that does nothing is worse")
    }

    @Test
    fun `four paragraphs of three screens is not a step`() {
        // What MMD's fixed four did, and the whole of the complaint: blocks 1 and 2 are on
        // the far side of a step that took four, and were never drawn on any page.
        val blocks = listOf(Block(0, 700), Block(1, 1500))

        assertEquals(1, readerScrollStep(blocks, VIEWPORT), "Should not skip what is unread")
    }

    @Test
    fun `an unmeasured list answers what MMD would have`() {
        assertEquals(
            LazyDefaultsMMD.SCROLL_STEP,
            readerScrollStep(emptyList(), VIEWPORT),
            "Nothing better to say before the first layout",
        )
    }
}

private const val VIEWPORT = 800
