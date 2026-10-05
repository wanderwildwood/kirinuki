package com.wanderwildwood.kirinuki.ui.article

import android.content.ComponentName
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.podcast.DownloadState
import com.wanderwildwood.kirinuki.podcast.EpisodeService
import com.wanderwildwood.kirinuki.podcast.EpisodeStore
import com.wanderwildwood.kirinuki.ui.compose.theme.Icons
import kotlinx.coroutines.delay
import java.util.Locale

/** What an episode is, as far as the panel needs to know. */
data class Episode(
    val itemId: Long,
    val url: String,
    val title: String,
    val show: String,
)

/**
 * The episode under the show notes: where it is, the buttons that move it, and whether it is
 * kept on the phone.
 *
 * Fixed below the text rather than scrolled with it, so the buttons are always where the
 * thumb left them -- and so they are clear of the page-turning edges, which take every tap
 * in the outer fifth of the text above.
 */
@Composable
fun EpisodePanel(
    episode: Episode,
    onlyOnWifi: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { EpisodeStore(context) }
    val controller = rememberEpisodeController()

    // Read from the player and the store once a second rather than followed by listener: the
    // clock is the only thing that changes while it plays, and on E Ink a second is as fine
    // as a clock is worth drawing.
    var playing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var isThis by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(store.position(episode.itemId)) }
    var durationMs by remember { mutableStateOf(store.duration(episode.itemId)) }
    var finished by remember { mutableStateOf(store.finished(episode.itemId)) }
    var speed by remember { mutableStateOf(store.speed) }
    var sleepAt by remember { mutableStateOf(0L) }
    var sleepChoice by remember { mutableIntStateOf(0) }
    var download by remember { mutableStateOf(store.downloadState(episode.itemId)) }
    var removeArmed by remember { mutableStateOf(false) }

    LaunchedEffect(controller, episode.itemId) {
        while (true) {
            val c = controller
            isThis = c?.currentMediaItem?.mediaId == episode.itemId.toString()
            if (c != null && isThis) {
                playing = c.isPlaying
                failed = c.playerError != null
                positionMs = c.currentPosition
                c.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { durationMs = it }
                speed = c.playbackParameters.speed
            } else {
                playing = false
                failed = false
                positionMs = store.position(episode.itemId)
                durationMs = store.duration(episode.itemId)
            }
            finished = store.finished(episode.itemId) && !(isThis && playing)
            val nowSleepAt = c?.sessionExtras?.getLong(EpisodeService.SLEEP_AT) ?: 0L
            // The timer ran out: the next press starts again from the shortest.
            if (sleepAt > 0L && nowSleepAt == 0L) sleepChoice = 0
            sleepAt = nowSleepAt
            download = store.downloadState(episode.itemId)
            delay(TICK_MS)
        }
    }

    LaunchedEffect(removeArmed) {
        if (removeArmed) {
            delay(ARMED_MILLIS)
            removeArmed = false
        }
    }

    fun playOrPause() {
        val c = controller ?: return
        if (isThis && c.playerError == null) {
            if (c.isPlaying) c.pause() else c.play()
            return
        }
        val kept = store.file(episode.itemId)?.takeIf { download == DownloadState.Kept }
        val item =
            MediaItem
                .Builder()
                .setMediaId(episode.itemId.toString())
                .setUri(kept?.toURI()?.toString() ?: episode.url)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(episode.title)
                        .setArtist(episode.show)
                        .build(),
                ).build()
        c.setMediaItem(item, store.position(episode.itemId))
        c.setPlaybackSpeed(store.speed)
        c.prepare()
        c.play()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDividerMMD()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextMMD(
                text =
                    if (failed) {
                        stringResource(R.string.episode_failed)
                    } else {
                        clockLine(positionMs, durationMs, finished, isThis)
                    },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextMMD(
                text =
                    when (val d = download) {
                        DownloadState.None -> stringResource(R.string.episode_keep)
                        is DownloadState.Downloading ->
                            d.percent?.let { stringResource(R.string.episode_downloading_percent, it) }
                                ?: stringResource(R.string.episode_downloading)
                        DownloadState.WaitingForWifi -> stringResource(R.string.episode_waiting_for_wifi)
                        DownloadState.Kept ->
                            if (removeArmed) {
                                stringResource(R.string.episode_remove_armed)
                            } else {
                                stringResource(R.string.episode_kept)
                            }
                        DownloadState.Failed -> stringResource(R.string.episode_download_failed)
                    },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.clickable {
                        when (download) {
                            DownloadState.None, DownloadState.Failed -> {
                                store.download(episode.itemId, episode.url, episode.title, onlyOnWifi)
                                download = store.downloadState(episode.itemId)
                            }
                            is DownloadState.Downloading, DownloadState.WaitingForWifi -> {
                                store.removeDownload(episode.itemId)
                                download = DownloadState.None
                            }
                            DownloadState.Kept ->
                                if (removeArmed) {
                                    // The one playing keeps playing from its open file; it
                                    // simply won't be there next time.
                                    store.removeDownload(episode.itemId)
                                    download = DownloadState.None
                                    removeArmed = false
                                } else {
                                    removeArmed = true
                                }
                        }
                    },
            )
        }

        Row(
            // Clear of the bottom edge: a Kompakt has no navigation bar under the app, so
            // without this the labels under the buttons sit on the very last row of pixels.
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PanelButton(
                icon = Icons.Speed,
                description = stringResource(R.string.episode_speed),
                size = 28.dp,
                label = speed.asSpeed(),
            ) {
                val next = SPEEDS.firstOrNull { it > speed + 0.01f } ?: SPEEDS.first()
                store.speed = next
                speed = next
                controller?.setPlaybackSpeed(next)
            }
            PanelButton(
                icon = Icons.Rewind,
                description = stringResource(R.string.episode_back_seconds, EpisodeService.SEEK_BACK_MS / 1000),
                size = 32.dp,
                label = (EpisodeService.SEEK_BACK_MS / 1000).toString(),
                enabled = isThis,
            ) { controller?.seekBack() }
            PanelButton(
                icon = if (playing) Icons.Pause else Icons.Play,
                description = stringResource(if (playing) R.string.episode_pause else R.string.episode_play),
                size = 48.dp,
                enabled = controller != null,
            ) { playOrPause() }
            PanelButton(
                icon = Icons.Forward,
                description = stringResource(R.string.episode_on_seconds, EpisodeService.SEEK_FORWARD_MS / 1000),
                size = 32.dp,
                label = (EpisodeService.SEEK_FORWARD_MS / 1000).toString(),
                enabled = isThis,
            ) { controller?.seekForward() }
            PanelButton(
                icon = if (sleepAt > 0L) Icons.SleepTimerOn else Icons.SleepTimerOff,
                description = stringResource(R.string.episode_sleep),
                size = 28.dp,
                label =
                    if (sleepAt > 0L) {
                        stringResource(R.string.episode_sleep_minutes, minutesLeft(sleepAt))
                    } else {
                        null
                    },
            ) {
                sleepChoice = (sleepChoice + 1) % SLEEP_MINUTES.size
                controller?.sendCustomCommand(
                    SessionCommand(EpisodeService.SLEEP, Bundle.EMPTY),
                    Bundle().apply { putInt(EpisodeService.SLEEP_MINUTES, SLEEP_MINUTES[sleepChoice]) },
                )
            }
        }
    }
}

/** A controller for the episode service, for as long as the panel is on screen. */
@Composable
private fun rememberEpisodeController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, EpisodeService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

@Composable
private fun PanelButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    label: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val ink = if (enabled) Color.Black else Color.Gray
    Box(
        modifier = Modifier.fillMaxHeight().width(56.dp).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = ink,
            modifier = Modifier.size(size),
        )
        if (label != null) {
            TextMMD(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                lineHeight = 14.sp,
                color = ink,
                softWrap = false,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun clockLine(
    positionMs: Long,
    durationMs: Long,
    finished: Boolean,
    isThis: Boolean,
): String =
    when {
        finished -> stringResource(R.string.episode_heard)
        positionMs <= 0L && !isThis && durationMs > 0L -> durationMs.asClock()
        positionMs <= 0L && !isThis -> stringResource(R.string.episode_not_started)
        durationMs > 0L -> stringResource(R.string.episode_position_of, positionMs.asClock(), durationMs.asClock())
        else -> positionMs.asClock()
    }

private fun minutesLeft(sleepAt: Long): Long = ((sleepAt - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)

private fun Float.asSpeed(): String = String.format(Locale.ROOT, "%.2f", this).trimEnd('0').trimEnd('.').let { if ('.' in it) it else "$it.0" } + "×"

private fun Long.asClock(): String {
    val total = (this / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "$m:${s.toString().padStart(2, '0')}"
    }
}

private val SPEEDS = listOf(1f, 1.25f, 1.5f, 1.75f, 2f)
private val SLEEP_MINUTES = listOf(0, 15, 30, 45, 60)
private const val TICK_MS = 1000L
private const val ARMED_MILLIS = 4000L
