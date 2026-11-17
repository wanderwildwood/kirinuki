package com.wanderwildwood.kirinuki.podcast

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import java.util.IdentityHashMap

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
    // The same wrapper is handed back on removal: ForwardingPlayer finds its own wrapper of a
    // listener by identity, so a fresh one would remove nothing.
    private val widened = IdentityHashMap<Player.Listener, WidenedCommands>()

    override fun addListener(listener: Player.Listener) =
        super.addListener(synchronized(widened) { widened.getOrPut(listener) { WidenedCommands(listener) } })

    override fun removeListener(listener: Player.Listener) {
        val wrapper = synchronized(widened) { widened.remove(listener) } ?: return
        super.removeListener(wrapper)
    }

    /**
     * Passes every event on, widening only the commands. Each callback is written out: every
     * one of Player.Listener's has a default body, and `Player.Listener by inner` does not pass
     * on a Java default method -- Kotlin compiles it to the interface's own empty body. That
     * silently dropped every event but this one (0.6.5 to 0.6.16): the session never heard the
     * episode was ready or playing, and every screen and controller showed it stuck at 0:00.
     */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    internal class WidenedCommands(
        val inner: Player.Listener,
    ) : Player.Listener {
        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) =
            inner.onAvailableCommandsChanged(withSkips(availableCommands))

        override fun onEvents(
            player: Player,
            events: Player.Events,
        ) = inner.onEvents(player, events)

        override fun onTimelineChanged(
            timeline: Timeline,
            reason: Int,
        ) = inner.onTimelineChanged(timeline, reason)

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) = inner.onMediaItemTransition(mediaItem, reason)

        override fun onTracksChanged(tracks: Tracks) = inner.onTracksChanged(tracks)

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = inner.onMediaMetadataChanged(mediaMetadata)

        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) =
            inner.onPlaylistMetadataChanged(mediaMetadata)

        override fun onIsLoadingChanged(isLoading: Boolean) = inner.onIsLoadingChanged(isLoading)

        override fun onLoadingChanged(isLoading: Boolean) = inner.onLoadingChanged(isLoading)

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) =
            inner.onTrackSelectionParametersChanged(parameters)

        override fun onPlayerStateChanged(
            playWhenReady: Boolean,
            playbackState: Int,
        ) = inner.onPlayerStateChanged(playWhenReady, playbackState)

        override fun onPlaybackStateChanged(playbackState: Int) = inner.onPlaybackStateChanged(playbackState)

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
        ) = inner.onPlayWhenReadyChanged(playWhenReady, reason)

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) =
            inner.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)

        override fun onIsPlayingChanged(isPlaying: Boolean) = inner.onIsPlayingChanged(isPlaying)

        override fun onRepeatModeChanged(repeatMode: Int) = inner.onRepeatModeChanged(repeatMode)

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) =
            inner.onShuffleModeEnabledChanged(shuffleModeEnabled)

        override fun onPlayerError(error: PlaybackException) = inner.onPlayerError(error)

        override fun onPlayerErrorChanged(error: PlaybackException?) = inner.onPlayerErrorChanged(error)

        override fun onPositionDiscontinuity(reason: Int) = inner.onPositionDiscontinuity(reason)

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) = inner.onPositionDiscontinuity(oldPosition, newPosition, reason)

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) =
            inner.onPlaybackParametersChanged(playbackParameters)

        override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) =
            inner.onSeekBackIncrementChanged(seekBackIncrementMs)

        override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) =
            inner.onSeekForwardIncrementChanged(seekForwardIncrementMs)

        override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) =
            inner.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)

        override fun onAudioSessionIdChanged(audioSessionId: Int) = inner.onAudioSessionIdChanged(audioSessionId)

        override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) =
            inner.onAudioAttributesChanged(audioAttributes)

        override fun onVolumeChanged(volume: Float) = inner.onVolumeChanged(volume)

        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) =
            inner.onSkipSilenceEnabledChanged(skipSilenceEnabled)

        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) = inner.onDeviceInfoChanged(deviceInfo)

        override fun onDeviceVolumeChanged(
            volume: Int,
            muted: Boolean,
        ) = inner.onDeviceVolumeChanged(volume, muted)

        override fun onVideoSizeChanged(videoSize: VideoSize) = inner.onVideoSizeChanged(videoSize)

        override fun onSurfaceSizeChanged(
            width: Int,
            height: Int,
        ) = inner.onSurfaceSizeChanged(width, height)

        override fun onRenderedFirstFrame() = inner.onRenderedFirstFrame()

        override fun onCues(cues: List<Cue>) = inner.onCues(cues)

        override fun onCues(cueGroup: CueGroup) = inner.onCues(cueGroup)

        override fun onMetadata(metadata: Metadata) = inner.onMetadata(metadata)

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
