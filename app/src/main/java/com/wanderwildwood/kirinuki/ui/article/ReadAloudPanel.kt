package com.wanderwildwood.kirinuki.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.model.readaloud.ReadAloudPlayer
import com.wanderwildwood.kirinuki.model.readaloud.ReadAloudTimeline
import com.wanderwildwood.kirinuki.ui.compose.theme.Icons

/**
 * Read aloud, under the text: where it is, a row of steps to go to another part of the article,
 * and the buttons. Fixed below the page like the episode panel, and for the same reasons.
 *
 * Nothing in it moves while the voice reads a sentence. The line and the steps change when the
 * next sentence starts, which on E Ink is one refresh every few seconds and none in between: no
 * clock, no sliding thumb.
 */
@Composable
fun ReadAloudPanel(
    state: ReadAloudPlayer.State,
    timeline: ReadAloudTimeline,
    onPlayOrPause: () -> Unit,
    onSeek: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = state.index.coerceIn(0, (timeline.size - 1).coerceAtLeast(0))
    val usable = state.engine == ReadAloudPlayer.Engine.READY && timeline.size > 0

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDividerMMD()

        TextMMD(
            text =
                when {
                    timeline.size == 0 -> stringResource(R.string.read_aloud_nothing)
                    state.engine == ReadAloudPlayer.Engine.STARTING -> stringResource(R.string.read_aloud_starting)
                    state.engine == ReadAloudPlayer.Engine.NONE -> stringResource(R.string.read_aloud_no_engine)
                    state.finished -> stringResource(R.string.read_aloud_finished)
                    else -> whereLine(index, timeline)
                },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (usable) {
            SeekSteps(
                index = index,
                timeline = timeline,
                onSeek = onSeek,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Row(
            // Clear of the bottom edge: a Kompakt has no navigation bar under the app.
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PanelIcon(
                icon = Icons.Rewind,
                description = stringResource(R.string.read_aloud_back),
                size = 32.dp,
                enabled = usable,
            ) { onSeek(timeline.step(index, -1)) }
            PanelIcon(
                icon = if (state.speaking) Icons.Pause else Icons.Play,
                description = stringResource(if (state.speaking) R.string.read_aloud_pause else R.string.read_aloud_play),
                size = 48.dp,
                enabled = usable,
                onClick = onPlayOrPause,
            )
            PanelIcon(
                icon = Icons.Forward,
                description = stringResource(R.string.read_aloud_on),
                size = 32.dp,
                enabled = usable,
            ) { onSeek(timeline.step(index, 1)) }
        }
    }
}

/**
 * The article in equal parts of its time, as many as fit at 48 dp each, up to [NOTCHES_MAX]:
 * filled up to the part being read, outlined after it. A press on a part goes to the first
 * sentence that starts in it. Steps rather than a slider, because a slider is dragged, and a
 * drag on E Ink is a smear of half-drawn thumbs.
 */
@Composable
private fun SeekSteps(
    index: Int,
    timeline: ReadAloudTimeline,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(48.dp)) {
        val notches = notchesFor(maxWidth)
        val here = timeline.notchOf(index, notches)
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            for (n in 0 until notches) {
                val label = stringResource(R.string.read_aloud_seek, n + 1, notches)
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics { contentDescription = label }
                            .clickable { onSeek(timeline.indexOfNotch(n, notches)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 3.dp)
                                .height(16.dp)
                                .border(1.dp, Color.Black)
                                .then(if (n <= here) Modifier.background(Color.Black) else Modifier),
                    )
                }
            }
        }
    }
}

/** How many 48 dp steps fit across [width], at least [NOTCHES_MIN] and at most [NOTCHES_MAX]. */
internal fun notchesFor(width: Dp): Int = (width.value / 48f).toInt().coerceIn(NOTCHES_MIN, NOTCHES_MAX)

@Composable
private fun whereLine(
    index: Int,
    timeline: ReadAloudTimeline,
): String {
    val minutes = (timeline.remainingMs(index) / 60_000L).toInt()
    return if (minutes < 1) {
        stringResource(R.string.read_aloud_where_last, index + 1, timeline.size)
    } else {
        stringResource(R.string.read_aloud_where, index + 1, timeline.size, minutes)
    }
}

@Composable
private fun PanelIcon(
    icon: ImageVector,
    description: String,
    size: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // Black either way, as in the episode panel: a button that can do nothing yet does nothing.
    Box(
        modifier = Modifier.fillMaxHeight().width(56.dp).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.Black,
            modifier = Modifier.size(size),
        )
    }
}

private const val NOTCHES_MIN = 4
private const val NOTCHES_MAX = 10
