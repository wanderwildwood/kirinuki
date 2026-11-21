package com.wanderwildwood.kirinuki.ui.article

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.podcast.Chapter
import com.wanderwildwood.kirinuki.podcast.TranscriptLine
import com.wanderwildwood.kirinuki.ui.LocalListBleed
import com.wanderwildwood.kirinuki.ui.PagedColumn
import com.wanderwildwood.kirinuki.ui.compose.components.DottedRule
import com.wanderwildwood.kirinuki.ui.compose.theme.CodeBlockBackground
import com.wanderwildwood.kirinuki.ui.compose.theme.DarkWindow
import com.wanderwildwood.kirinuki.ui.compose.theme.HouseButton
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * A whole-screen window for a list of the episode's, with no dimmed backdrop and no animation
 * (EInkDialog's reasons). Nothing in it moves on its own: the list turns a page when asked.
 */
@Composable
private fun ExtrasWindow(
    title: String,
    onClose: () -> Unit,
    extraButton: (@Composable (Modifier) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        DarkWindow()
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(0f)
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxSize(),
        ) {
            CompositionLocalProvider(LocalListBleed provides 0.dp) {
                Column(Modifier.fillMaxSize()) {
                    TextMMD(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    DottedRule(start = 16.dp)
                    content()
                    DottedRule(start = 16.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        extraButton?.invoke(Modifier.weight(1f))
                        HouseButton(
                            label = stringResource(R.string.episode_extras_close),
                            onClick = onClose,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The chapters, as title and start time. The one playing is shaded and bold; [nowIndex] is read
 * only here, so the list is drawn again when the chapter changes and not as the seconds go by.
 * A tap goes to the chapter and closes the list.
 */
@Composable
fun ChaptersWindow(
    chapters: List<Chapter>,
    loading: Boolean,
    nowIndex: () -> Int,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
) {
    ExtrasWindow(title = stringResource(R.string.episode_chapters), onClose = onClose) {
        if (chapters.isEmpty()) {
            EmptyNote(loading, R.string.episode_chapters_failed)
        } else {
            val now = nowIndex()
            val state = rememberLazyListState(initialFirstVisibleItemIndex = now.coerceAtLeast(0))
            PagedColumn(showCutRow = true, state = state, modifier = Modifier.weight(1f)) {
                items(count = chapters.size, key = { it }) { index ->
                    val chapter = chapters[index]
                    TimedRow(
                        time = chapter.startMs.asClockText(),
                        text = chapter.title,
                        current = index == now,
                        onClick = {
                            onSeek(chapter.startMs)
                            onClose()
                        },
                    )
                }
            }
        }
    }
}

/**
 * The transcript, a page at a time. A timed line shows its time, and a tap goes there; the one
 * being spoken is shaded. The list never follows the sound by itself: "Go to now" takes the
 * reader there when they ask. It opens at the current line.
 */
@Composable
fun TranscriptWindow(
    lines: List<TranscriptLine>,
    loading: Boolean,
    nowIndex: () -> Int,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val now = nowIndex()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = now.coerceAtLeast(0))
    val timed = lines.any { it.startMs != null }
    ExtrasWindow(
        title = stringResource(R.string.episode_transcript),
        onClose = onClose,
        extraButton =
            if (timed && lines.isNotEmpty()) {
                { modifier ->
                    HouseButton(
                        label = stringResource(R.string.episode_transcript_now),
                        onClick = { scope.launch { state.scrollToItem(nowIndex().coerceAtLeast(0)) } },
                        modifier = modifier,
                    )
                }
            } else {
                null
            },
    ) {
        if (lines.isEmpty()) {
            EmptyNote(loading, R.string.episode_transcript_failed)
        } else {
            PagedColumn(showCutRow = true, state = state, modifier = Modifier.weight(1f)) {
                items(count = lines.size, key = { it }) { index ->
                    val line = lines[index]
                    TimedRow(
                        time = line.startMs?.asClockText(),
                        text = line.text,
                        current = timed && index == now,
                        onClick = line.startMs?.let { start -> { onSeek(start) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.EmptyNote(
    loading: Boolean,
    failedRes: Int,
) {
    TextMMD(
        text = stringResource(if (loading) R.string.episode_extras_loading else failedRes),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f).padding(16.dp),
    )
}

@Composable
private fun TimedRow(
    time: String?,
    text: String,
    current: Boolean,
    onClick: (() -> Unit)?,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (current) Modifier.background(CodeBlockBackground()) else Modifier)
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (time != null) {
                TextMMD(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (current) FontWeight.Bold else null,
                    modifier = Modifier.width(64.dp),
                )
            }
            TextMMD(
                text = text,
                fontWeight = if (current) FontWeight.Bold else null,
                modifier = Modifier.weight(1f),
            )
        }
        DottedRule(start = 16.dp)
    }
}

private fun Long.asClockText(): String {
    val total = (this / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}
