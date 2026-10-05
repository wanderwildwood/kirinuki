package com.wanderwildwood.kirinuki.podcast

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.ui.MainActivity

/**
 * Plays an episode, with the screen off and the app closed.
 *
 * The notification, the lock screen, headset buttons, a call arriving and another app wanting
 * the speaker are all MediaSession's and ExoPlayer's to handle. What is this app's own is
 * remembering where the listener stopped -- on every pause, every few seconds while playing,
 * and at the end -- and the sleep timer.
 *
 * The same shape as mimidoku's player, cut down to what one episode at a time needs.
 */
class EpisodeService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private lateinit var store: EpisodeStore

    private val handler = Handler(Looper.getMainLooper())

    /** While playing, the place is written down this often, so a killed app loses little. */
    private val saver =
        object : Runnable {
            override fun run() {
                savePosition()
                handler.postDelayed(this, SAVE_EVERY_MS)
            }
        }

    /** Wall-clock time the sleep timer pauses at, or 0 when it is off. */
    private var sleepAt = 0L
    private val sleeper = Runnable { fallAsleep() }

    override fun onCreate() {
        super.onCreate()
        store = EpisodeStore(this)

        val exoPlayer =
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        // Speech, not music: it changes how the system ducks this against
                        // other sound.
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    // Let ExoPlayer take and give up audio focus, so a call pauses the episode
                    // rather than talking over it.
                    true,
                )
                // Headphones pulled out pause it, instead of carrying on out of the speaker.
                .setHandleAudioBecomingNoisy(true)
                // The screen is off for nearly all of an episode. Streaming needs the wifi
                // awake as well as the processor.
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setSeekBackIncrementMs(SEEK_BACK_MS)
                .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
                .build()
        exoPlayer.setPlaybackSpeed(store.speed)

        exoPlayer.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    handler.removeCallbacks(saver)
                    if (isPlaying) {
                        handler.postDelayed(saver, SAVE_EVERY_MS)
                    } else {
                        savePosition()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        currentItemId()?.let(store::markFinished)
                    }
                }
            },
        )

        player = exoPlayer
        session =
            MediaSession
                .Builder(this, exoPlayer)
                .setCallback(Commands())
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).build()
        publishSleep()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_stat_f)
            },
        )
    }

    /** The sleep timer, which a MediaController has no word for, is asked for by name. */
    private inner class Commands : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult
                .AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        .buildUpon()
                        .add(SessionCommand(SLEEP, Bundle.EMPTY))
                        .build(),
                ).build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != SLEEP) {
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            setSleep(args.getInt(SLEEP_MINUTES))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun setSleep(minutes: Int) {
        handler.removeCallbacks(sleeper)
        sleepAt =
            if (minutes > 0) {
                val delay = minutes * 60_000L
                handler.postDelayed(sleeper, delay)
                System.currentTimeMillis() + delay
            } else {
                0L
            }
        publishSleep()
    }

    private fun fallAsleep() {
        player?.pause()
        sleepAt = 0L
        publishSleep()
    }

    /** The screen reads when the timer ends from the session, rather than keeping a copy. */
    private fun publishSleep() {
        session?.setSessionExtras(Bundle().apply { putLong(SLEEP_AT, sleepAt) })
    }

    private fun currentItemId(): Long? = player?.currentMediaItem?.mediaId?.toLongOrNull()

    private fun savePosition() {
        val current = player ?: return
        val id = currentItemId() ?: return
        if (current.playbackState == Player.STATE_ENDED) return
        store.savePosition(id, current.currentPosition, current.duration.takeIf { it != C.TIME_UNSET } ?: 0L)
        store.speed = current.playbackParameters.speed
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away while paused should not leave a service with nothing to play. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        savePosition()
        handler.removeCallbacksAndMessages(null)
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }

    companion object {
        const val SLEEP = "com.wanderwildwood.kirinuki.SLEEP"
        const val SLEEP_MINUTES = "minutes"
        const val SLEEP_AT = "sleepAt"

        /** Back a little further than on: going back is usually "I missed that". */
        const val SEEK_BACK_MS = 15_000L
        const val SEEK_FORWARD_MS = 30_000L

        private const val SAVE_EVERY_MS = 10_000L
    }
}
