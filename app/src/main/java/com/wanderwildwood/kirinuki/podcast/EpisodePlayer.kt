package com.wanderwildwood.kirinuki.podcast

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * The player as everything outside the app sees it: the notification, headphones, a car, a
 * watch, another app's lock-screen panel.
 *
 * One episode is loaded at a time, so ExoPlayer has no "next" to offer, and Media3 quietly
 * drops every next-track press before the player hears it; "previous" only went back to the
 * start. Here both are always on offer while an episode is loaded, and what they do is the
 * setting's: skip ahead and back, or step through the show.
 */
@OptIn(UnstableApi::class)
class EpisodePlayer(
    player: Player,
    private val mode: () -> EpisodeButtons,
    private val toEpisode: (next: Boolean) -> Unit,
) : ForwardingPlayer(player) {
    /** A press of next or previous, from wherever it came. */
    fun press(press: ButtonPress) {
        when (episodeAction(press.button, press.long, mode())) {
            EpisodeAction.SKIP_AHEAD -> seekForward()
            EpisodeAction.SKIP_BACK -> seekBack()
            EpisodeAction.NEXT_EPISODE -> toEpisode(true)
            EpisodeAction.RESTART -> seekTo(0L)
            EpisodeAction.PREVIOUS_EPISODE ->
                if (currentPosition > maxSeekToPreviousPosition) seekTo(0L) else toEpisode(false)
        }
    }

    override fun seekToNext() = press(ButtonPress(SkipButton.NEXT, long = false))

    override fun seekToPrevious() = press(ButtonPress(SkipButton.PREVIOUS, long = false))

    override fun getAvailableCommands(): Player.Commands = withSkips(super.getAvailableCommands())

    override fun isCommandAvailable(command: Int): Boolean = getAvailableCommands().contains(command)

    // The session hears of changed commands through its listener, and takes the commands it is
    // handed there rather than asking again; so those are widened on the way through as well.
    override fun addListener(listener: Player.Listener) = super.addListener(WidenedCommands(listener))

    override fun removeListener(listener: Player.Listener) = super.removeListener(WidenedCommands(listener))

    private class WidenedCommands(
        val inner: Player.Listener,
    ) : Player.Listener by inner {
        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) =
            inner.onAvailableCommandsChanged(withSkips(availableCommands))

        override fun equals(other: Any?): Boolean = other is WidenedCommands && other.inner == inner

        override fun hashCode(): Int = inner.hashCode()
    }

    companion object {
        /** Next and previous whenever an episode is loaded that can be moved about in. */
        fun withSkips(commands: Player.Commands): Player.Commands =
            if (commands.contains(Player.COMMAND_SEEK_BACK) || commands.contains(Player.COMMAND_SEEK_FORWARD)) {
                commands
                    .buildUpon()
                    .addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS)
                    .build()
            } else {
                commands
            }
    }
}

/** An episode as the player takes it, from the panel or from a skip to the next one. */
fun episodeMediaItem(
    itemId: Long,
    uri: String,
    title: String,
    show: String,
): MediaItem =
    MediaItem
        .Builder()
        .setMediaId(itemId.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata
                .Builder()
                .setTitle(title)
                .setArtist(show)
                .build(),
        ).build()
