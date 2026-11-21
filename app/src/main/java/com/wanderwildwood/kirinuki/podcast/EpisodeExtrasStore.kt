package com.wanderwildwood.kirinuki.podcast

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Chapters and transcripts, fetched from the addresses the feed gave and kept on the phone beside
 * the episode, so they open with no network once they have been read once (or the episode was
 * kept). Fetched with the app's own HTTP client, so whatever it is set to do, it does.
 *
 * A kept file starts with the address it came from, so a feed that moves its transcript gets the
 * new one rather than the old.
 */
class EpisodeExtrasStore(
    context: Context,
    private val client: OkHttpClient,
) {
    private val dir = File(context.applicationContext.filesDir, DIR)

    /** The chapters, from the phone if kept, else fetched and kept. Null if neither works. */
    suspend fun chapters(
        itemId: Long,
        url: String,
    ): List<Chapter>? = text(itemId, "chapters", url)?.let { parseChaptersJson(it) }

    suspend fun transcript(
        itemId: Long,
        url: String,
        type: String?,
    ): List<TranscriptLine>? = text(itemId, "transcript", url)?.let { parseTranscript(it, type) }

    private suspend fun text(
        itemId: Long,
        kind: String,
        url: String,
    ): String? =
        withContext(Dispatchers.IO) {
            val file = File(dir, "$itemId.$kind")
            kept(file, url)?.let { return@withContext it }
            val body = fetch(url) ?: return@withContext null
            try {
                dir.mkdirs()
                file.writeText(url + "\n" + body)
            } catch (_: Exception) {
                // Not kept: shown now, fetched again next time.
            }
            body
        }

    private fun kept(
        file: File,
        url: String,
    ): String? =
        try {
            if (!file.isFile) {
                null
            } else {
                val all = file.readText()
                val cut = all.indexOf('\n')
                if (cut > 0 && all.substring(0, cut) == url) all.substring(cut + 1) else null
            }
        } catch (_: Exception) {
            null
        }

    private fun fetch(url: String): String? =
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body
                // A transcript is some hundreds of kilobytes at the most; anything bigger is not one.
                if (body.contentLength() > MAX_BYTES) return@use null
                val out = java.io.ByteArrayOutputStream()
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (out.size() <= MAX_BYTES) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                    }
                }
                if (out.size() > MAX_BYTES) return@use null
                val bytes = out.toByteArray()
                String(bytes, body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }

    companion object {
        /** What is kept for an episode whose item is gone. Called by the daily sweep. */
        fun forgetAllBut(
            context: Context,
            validIds: Set<Long>,
        ): Int {
            var removed = 0
            File(context.applicationContext.filesDir, DIR).listFiles()?.forEach { file ->
                val id = file.name.substringBefore('.').toLongOrNull()
                if (id == null || id !in validIds) {
                    if (file.delete()) removed++
                }
            }
            return removed
        }

        private const val DIR = "episode-extras"
        private const val MAX_BYTES = 8L * 1024 * 1024
    }
}

/** What the episode screen knows of an episode's chapters and transcript. */
data class EpisodeExtras(
    val hasChapters: Boolean = false,
    val chapters: List<Chapter> = emptyList(),
    val chaptersLoading: Boolean = false,
    val hasTranscript: Boolean = false,
    val transcript: List<TranscriptLine> = emptyList(),
    val transcriptLoading: Boolean = false,
)
