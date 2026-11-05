package com.wanderwildwood.kirinuki.podcast

import android.view.KeyEvent

/**
 * What the skip buttons on headphones, a car or a Bluetooth remote do to an episode.
 *
 * A podcast is one long track, so "next track" means little inside it: by default a press
 * skips ahead or back, as the panel's own buttons do, and a long press leaves the episode.
 * The other choice turns that round, for someone who goes through a show one episode after
 * another.
 */
enum class EpisodeButtons {
    /** A press skips ahead or back; a long press goes to the next episode or the start. */
    SKIP,

    /** A press goes to the next or previous episode; a long press skips ahead or back. */
    EPISODES,
}

fun episodeButtonsFromString(value: String?): EpisodeButtons =
    EpisodeButtons.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: EpisodeButtons.SKIP

/** The two buttons, whatever the device calls them. */
enum class SkipButton { NEXT, PREVIOUS }

enum class EpisodeAction {
    SKIP_AHEAD,
    SKIP_BACK,
    NEXT_EPISODE,

    /** Back to the start of this one if it is under way, otherwise the episode before. */
    PREVIOUS_EPISODE,

    /** Back to the start of this one, wherever it is. */
    RESTART,
}

/** What one press does, given the setting. */
fun episodeAction(
    button: SkipButton,
    long: Boolean,
    mode: EpisodeButtons,
): EpisodeAction =
    when (mode) {
        EpisodeButtons.SKIP ->
            when (button) {
                SkipButton.NEXT -> if (long) EpisodeAction.NEXT_EPISODE else EpisodeAction.SKIP_AHEAD
                SkipButton.PREVIOUS -> if (long) EpisodeAction.RESTART else EpisodeAction.SKIP_BACK
            }
        EpisodeButtons.EPISODES ->
            when (button) {
                SkipButton.NEXT -> if (long) EpisodeAction.SKIP_AHEAD else EpisodeAction.NEXT_EPISODE
                SkipButton.PREVIOUS -> if (long) EpisodeAction.SKIP_BACK else EpisodeAction.PREVIOUS_EPISODE
            }
    }

/** One press, told apart from a hold. */
data class ButtonPress(
    val button: SkipButton,
    val long: Boolean,
)

/**
 * Tells a press from a long press, from the key events a headset or a Bluetooth device sends.
 *
 * Devices say "long press" in two ways, and both are met here:
 * - The key is held: one DOWN, then DOWNs with a repeat count (or a second DOWN before any
 *   UP), then an UP. The press is decided at the UP, so a hold never does the short thing
 *   first.
 * - A different key: most Bluetooth headphones send FAST_FORWARD or REWIND while the button
 *   is held, often over and over. The first one counts; the rest of the stream is the same
 *   hold, until the device has been quiet for [HOLD_GAP_MS].
 *
 * A sender that never sends the UP still gets its press, after [TAP_TIMEOUT_MS]: the caller
 * arms a timer when [Outcome.armTimeout] says so and calls [onTimeout] when it runs out.
 *
 * Plain Kotlin with the time passed in, so it can be tested without a phone.
 */
class ButtonPresses {
    data class Outcome(
        /** False: not a key this class deals with; let the session do what it does. */
        val handled: Boolean,
        val presses: List<ButtonPress> = emptyList(),
        val armTimeout: Boolean = false,
    )

    /** A tap key that is down and not yet decided. */
    private var pending: SkipButton? = null

    /** A tap key that has already done its long press; its repeats and UP do nothing more. */
    private var held: SkipButton? = null

    /** The last FAST_FORWARD or REWIND, so one hold is one press. */
    private var holdButton: SkipButton? = null
    private var lastHoldAt = 0L

    fun onKey(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
        longPress: Boolean,
        nowMs: Long,
    ): Outcome {
        tapButton(keyCode)?.let { return onTap(it, action, repeatCount, longPress) }
        holdButton(keyCode)?.let { return onHold(it, action, nowMs) }
        return Outcome(handled = false)
    }

    /** The DOWN never had its UP: it was a press. */
    fun onTimeout(): Outcome {
        val button = pending ?: return Outcome(handled = true)
        pending = null
        return Outcome(handled = true, presses = listOf(ButtonPress(button, long = false)))
    }

    private fun onTap(
        button: SkipButton,
        action: Int,
        repeatCount: Int,
        longPress: Boolean,
    ): Outcome {
        if (action == KeyEvent.ACTION_UP) {
            if (held == button) {
                held = null
                return Outcome(handled = true)
            }
            if (pending == button) {
                pending = null
                return Outcome(handled = true, presses = listOf(ButtonPress(button, long = false)))
            }
            return Outcome(handled = true)
        }
        if (action != KeyEvent.ACTION_DOWN) return Outcome(handled = true)

        if (held == button) return Outcome(handled = true)
        val heldDown = repeatCount > 0 || longPress || pending == button
        if (heldDown) {
            pending = null
            held = button
            return Outcome(handled = true, presses = listOf(ButtonPress(button, long = true)))
        }
        // A new press. One still undecided on the other button was a press: keep the order.
        val earlier = pending?.let { listOf(ButtonPress(it, long = false)) } ?: emptyList()
        held = null
        pending = button
        return Outcome(handled = true, presses = earlier, armTimeout = true)
    }

    private fun onHold(
        button: SkipButton,
        action: Int,
        nowMs: Long,
    ): Outcome {
        if (action != KeyEvent.ACTION_DOWN) return Outcome(handled = true)
        val sameHold = holdButton == button && nowMs - lastHoldAt < HOLD_GAP_MS
        holdButton = button
        lastHoldAt = nowMs
        return if (sameHold) {
            Outcome(handled = true)
        } else {
            Outcome(handled = true, presses = listOf(ButtonPress(button, long = true)))
        }
    }

    companion object {
        /** Longer than a press, and longer than Android waits before a held key repeats. */
        const val TAP_TIMEOUT_MS = 1_000L

        /** Quiet this long between FAST_FORWARDs, and the next one is a new hold. */
        const val HOLD_GAP_MS = 1_500L

        fun tapButton(keyCode: Int): SkipButton? =
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> SkipButton.NEXT
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> SkipButton.PREVIOUS
                else -> null
            }

        fun holdButton(keyCode: Int): SkipButton? =
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> SkipButton.NEXT
                KeyEvent.KEYCODE_MEDIA_REWIND -> SkipButton.PREVIOUS
                else -> null
            }
    }
}

/**
 * The episode next to [currentId] in a show, in the order its list shows them.
 *
 * [newestFirstIds] is the show's episodes newest first; [newestFirst] is the list's sort.
 * "Next" is the one below in the list, "previous" the one above. Null at either end, or if
 * the current episode is not in the list at all.
 */
fun neighborEpisodeId(
    newestFirstIds: List<Long>,
    currentId: Long,
    next: Boolean,
    newestFirst: Boolean,
): Long? {
    val ordered = if (newestFirst) newestFirstIds else newestFirstIds.asReversed()
    val at = ordered.indexOf(currentId)
    if (at < 0) return null
    return ordered.getOrNull(if (next) at + 1 else at - 1)
}
