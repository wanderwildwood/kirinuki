package com.wanderwildwood.kirinuki.podcast

import android.view.KeyEvent
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaButtonsTest {
    // ---- What a press and a long press do ----------------------------------------------

    @Test
    fun skipModeIsTheDefaultAndSkipsOnAPress() {
        assertEquals(EpisodeButtons.SKIP, episodeButtonsFromString(null))
        assertEquals(EpisodeButtons.SKIP, episodeButtonsFromString("nonsense"))
        assertEquals(EpisodeButtons.EPISODES, episodeButtonsFromString("episodes"))
        assertEquals(EpisodeButtons.SKIP, episodeButtonsFromString("skip"))
    }

    @Test
    fun skipModeMapping() {
        val m = EpisodeButtons.SKIP
        assertEquals(EpisodeAction.SKIP_AHEAD, episodeAction(SkipButton.NEXT, long = false, mode = m))
        assertEquals(EpisodeAction.SKIP_BACK, episodeAction(SkipButton.PREVIOUS, long = false, mode = m))
        assertEquals(EpisodeAction.NEXT_EPISODE, episodeAction(SkipButton.NEXT, long = true, mode = m))
        assertEquals(EpisodeAction.RESTART, episodeAction(SkipButton.PREVIOUS, long = true, mode = m))
    }

    @Test
    fun episodesModeMapping() {
        val m = EpisodeButtons.EPISODES
        assertEquals(EpisodeAction.NEXT_EPISODE, episodeAction(SkipButton.NEXT, long = false, mode = m))
        assertEquals(EpisodeAction.PREVIOUS_EPISODE, episodeAction(SkipButton.PREVIOUS, long = false, mode = m))
        assertEquals(EpisodeAction.SKIP_AHEAD, episodeAction(SkipButton.NEXT, long = true, mode = m))
        assertEquals(EpisodeAction.SKIP_BACK, episodeAction(SkipButton.PREVIOUS, long = true, mode = m))
    }

    // ---- Telling a press from a long press ----------------------------------------------

    private val down = KeyEvent.ACTION_DOWN
    private val up = KeyEvent.ACTION_UP

    private fun ButtonPresses.key(
        code: Int,
        action: Int,
        repeat: Int = 0,
        long: Boolean = false,
        at: Long = 0L,
    ) = onKey(code, action, repeat, long, at)

    @Test
    fun aPressIsDecidedAtTheUp() {
        val p = ButtonPresses()
        val d = p.key(KeyEvent.KEYCODE_MEDIA_NEXT, down)
        assertTrue(d.handled)
        assertTrue(d.presses.isEmpty())
        assertTrue(d.armTimeout)
        val u = p.key(KeyEvent.KEYCODE_MEDIA_NEXT, up)
        assertEquals(listOf(ButtonPress(SkipButton.NEXT, long = false)), u.presses)
        assertFalse(u.armTimeout)
    }

    @Test
    fun previousAndTheSkipKeysAreButtonsToo() {
        val p = ButtonPresses()
        p.key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, down)
        assertEquals(listOf(ButtonPress(SkipButton.PREVIOUS, false)), p.key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, up).presses)
        p.key(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, down)
        assertEquals(listOf(ButtonPress(SkipButton.NEXT, false)), p.key(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, up).presses)
        p.key(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, down)
        assertEquals(
            listOf(ButtonPress(SkipButton.PREVIOUS, false)),
            p.key(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, up).presses,
        )
    }

    @Test
    fun aHeldKeyIsOneLongPressAndNeverAShortOne() {
        val p = ButtonPresses()
        assertTrue(p.key(KeyEvent.KEYCODE_MEDIA_NEXT, down).presses.isEmpty())
        val first = p.key(KeyEvent.KEYCODE_MEDIA_NEXT, down, repeat = 1)
        assertEquals(listOf(ButtonPress(SkipButton.NEXT, long = true)), first.presses)
        assertFalse(first.armTimeout)
        assertTrue(p.key(KeyEvent.KEYCODE_MEDIA_NEXT, down, repeat = 2, long = true).presses.isEmpty())
        assertTrue(p.key(KeyEvent.KEYCODE_MEDIA_NEXT, down, repeat = 3).presses.isEmpty())
        val u = p.key(KeyEvent.KEYCODE_MEDIA_NEXT, up)
        assertTrue(u.handled)
        assertTrue(u.presses.isEmpty())
        // And the next press is a press again.
        p.key(KeyEvent.KEYCODE_MEDIA_NEXT, down)
        assertEquals(listOf(ButtonPress(SkipButton.NEXT, false)), p.key(KeyEvent.KEYCODE_MEDIA_NEXT, up).presses)
    }

    @Test
    fun theLongPressFlagCountsAsHeld() {
        val p = ButtonPresses()
        p.key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, down)
        assertEquals(
            listOf(ButtonPress(SkipButton.PREVIOUS, long = true)),
            p.key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, down, long = true).presses,
        )
    }

    @Test
    fun aSecondDownWithNoUpBetweenIsAHold() {
        // Some Bluetooth stacks send each repeat as a fresh DOWN with no repeat count.
        val p = ButtonPresses()
        p.key(KeyEvent.KEYCODE_MEDIA_NEXT, down)
        assertEquals(listOf(ButtonPress(SkipButton.NEXT, true)), p.key(KeyEvent.KEYCODE_MEDIA_NEXT, down).presses)
        assertTrue(p.key(KeyEvent.KEYCODE_MEDIA_NEXT, down).presses.isEmpty())
        assertTrue(p.key(KeyEvent.KEYCODE_MEDIA_NEXT, up).presses.isEmpty())
    }

    @Test
    fun aDownThatNeverGetsItsUpIsAPressAfterTheTimeout() {
        val p = ButtonPresses()
        assertTrue(p.key(KeyEvent.KEYCODE_MEDIA_NEXT, down).armTimeout)
        assertEquals(listOf(ButtonPress(SkipButton.NEXT, false)), p.onTimeout().presses)
        // Its late UP, if it comes, does nothing more.
        assertTrue(p.key(KeyEvent.KEYCODE_MEDIA_NEXT, up).presses.isEmpty())
        assertTrue(p.onTimeout().presses.isEmpty())
    }

    @Test
    fun anUndecidedPressIsKeptInOrderWhenTheOtherButtonComes() {
        val p = ButtonPresses()
        p.key(KeyEvent.KEYCODE_MEDIA_NEXT, down)
        val other = p.key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, down)
        assertEquals(listOf(ButtonPress(SkipButton.NEXT, false)), other.presses)
        assertEquals(listOf(ButtonPress(SkipButton.PREVIOUS, false)), p.key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, up).presses)
    }

    @Test
    fun fastForwardAndRewindAreLongPresses() {
        val p = ButtonPresses()
        assertEquals(
            listOf(ButtonPress(SkipButton.NEXT, long = true)),
            p.key(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, down, at = 0L).presses,
        )
        assertEquals(
            listOf(ButtonPress(SkipButton.PREVIOUS, long = true)),
            p.key(KeyEvent.KEYCODE_MEDIA_REWIND, down, at = 10_000L).presses,
        )
    }

    @Test
    fun aStreamOfFastForwardsIsOneHold() {
        val p = ButtonPresses()
        var t = 0L
        val presses = mutableListOf<ButtonPress>()
        // Down and up every 200 ms for three seconds, as a held button can arrive.
        repeat(15) {
            presses += p.key(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, down, at = t).presses
            presses += p.key(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, up, at = t + 50).presses
            t += 200
        }
        assertEquals(listOf(ButtonPress(SkipButton.NEXT, true)), presses)
        // A pause, and the next hold is a new one.
        t += ButtonPresses.HOLD_GAP_MS
        assertEquals(
            listOf(ButtonPress(SkipButton.NEXT, true)),
            p.key(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, down, at = t).presses,
        )
    }

    @Test
    fun otherKeysAreLeftToTheSession() {
        val p = ButtonPresses()
        assertFalse(p.key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, down).handled)
        assertFalse(p.key(KeyEvent.KEYCODE_HEADSETHOOK, down).handled)
        assertFalse(p.key(KeyEvent.KEYCODE_MEDIA_STOP, down).handled)
    }

    // ---- The next episode in a show -----------------------------------------------------

    private val newestFirst = listOf(50L, 40L, 30L, 20L)

    @Test
    fun nextIsTheOneBelowInTheList() {
        assertEquals(30L, neighborEpisodeId(newestFirst, 40L, next = true, newestFirst = true))
        assertEquals(50L, neighborEpisodeId(newestFirst, 40L, next = false, newestFirst = true))
        assertEquals(50L, neighborEpisodeId(newestFirst, 40L, next = true, newestFirst = false))
        assertEquals(30L, neighborEpisodeId(newestFirst, 40L, next = false, newestFirst = false))
    }

    @Test
    fun theEndsAndAStrangerHaveNoNeighbor() {
        assertNull(neighborEpisodeId(newestFirst, 20L, next = true, newestFirst = true))
        assertNull(neighborEpisodeId(newestFirst, 50L, next = false, newestFirst = true))
        assertNull(neighborEpisodeId(newestFirst, 99L, next = true, newestFirst = true))
        assertNull(neighborEpisodeId(emptyList(), 1L, next = true, newestFirst = true))
    }

    // ---- The player everything outside the app talks to ---------------------------------

    private fun playerAt(positionMs: Long): Player =
        mockk(relaxed = true) {
            every { currentPosition } returns positionMs
            every { maxSeekToPreviousPosition } returns 3_000L
        }

    @Test
    fun nextTrackSkipsAheadByDefault() {
        val inner = playerAt(60_000L)
        val steps = mutableListOf<Boolean>()
        val p = EpisodePlayer(inner, mode = { EpisodeButtons.SKIP }, toEpisode = { steps += it })
        p.seekToNext()
        p.seekToPrevious()
        verify(exactly = 1) { inner.seekForward() }
        verify(exactly = 1) { inner.seekBack() }
        verify(exactly = 0) { inner.seekToNext() }
        verify(exactly = 0) { inner.seekToPrevious() }
        assertTrue(steps.isEmpty())
    }

    @Test
    fun aLongPressLeavesTheEpisodeOrRestartsIt() {
        val inner = playerAt(60_000L)
        val steps = mutableListOf<Boolean>()
        val p = EpisodePlayer(inner, mode = { EpisodeButtons.SKIP }, toEpisode = { steps += it })
        p.press(ButtonPress(SkipButton.NEXT, long = true))
        p.press(ButtonPress(SkipButton.PREVIOUS, long = true))
        assertEquals(listOf(true), steps)
        verify(exactly = 1) { inner.seekTo(0L) }
    }

    @Test
    fun episodesModeStepsThroughTheShow() {
        val steps = mutableListOf<Boolean>()
        // Under way: previous goes back to the start first.
        val underWay = playerAt(60_000L)
        val p = EpisodePlayer(underWay, mode = { EpisodeButtons.EPISODES }, toEpisode = { steps += it })
        p.seekToNext()
        p.seekToPrevious()
        verify(exactly = 1) { underWay.seekTo(0L) }
        assertEquals(listOf(true), steps)
        // At the start: previous is the episode before.
        val atStart = playerAt(1_000L)
        val q = EpisodePlayer(atStart, mode = { EpisodeButtons.EPISODES }, toEpisode = { steps += it })
        q.seekToPrevious()
        assertEquals(listOf(true, false), steps)
        // A long press skips.
        q.press(ButtonPress(SkipButton.NEXT, long = true))
        q.press(ButtonPress(SkipButton.PREVIOUS, long = true))
        verify(exactly = 1) { atStart.seekForward() }
        verify(exactly = 1) { atStart.seekBack() }
    }

    @Test
    fun theSettingIsReadAtEachPress() {
        var mode = EpisodeButtons.SKIP
        val inner = playerAt(60_000L)
        val steps = mutableListOf<Boolean>()
        val p = EpisodePlayer(inner, mode = { mode }, toEpisode = { steps += it })
        p.seekToNext()
        mode = EpisodeButtons.EPISODES
        p.seekToNext()
        verify(exactly = 1) { inner.seekForward() }
        assertEquals(listOf(true), steps)
    }
}
