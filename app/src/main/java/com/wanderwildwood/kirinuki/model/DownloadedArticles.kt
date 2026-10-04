package com.wanderwildwood.kirinuki.model

import android.util.Log
import java.io.File
import java.time.Instant

/**
 * The whole articles fetched from their pages. They live in filesDir, which Android never
 * clears on its own -- they were in the cache once, inherited from Feeder, and a phone short
 * of space could empty that and take a saved page with it. So what clears them now is this:
 * the daily sweep, after the number of days in settings, and the button beside that setting.
 *
 * A kept (starred) article is never removed by either. Keeping is the one thing you did on
 * purpose, and it is what the Pages feed relies on.
 */
object DownloadedArticles {
    private const val LOG_TAG = "KIRINUKI_DOWNLOADED"
    private const val SUFFIX = ".full.html.gz"

    /** The days a setting can be, in the order the stepper walks them. 0 is never. */
    val REMOVE_AFTER_DAYS = listOf(7, 30, 90, 0)
    const val DEFAULT_REMOVE_AFTER_DAYS = 30

    fun size(dir: File): Long = files(dir).sumOf { it.length() }

    /**
     * Deletes every downloaded article but the kept ones -- or, given [downloadedBefore],
     * only those fetched before it. Returns how many went.
     */
    fun remove(
        dir: File,
        keep: Set<Long>,
        downloadedBefore: Instant? = null,
    ): Int =
        files(dir).count { file ->
            val id = file.name.removeSuffix(SUFFIX).toLongOrNull()
            id !in keep &&
                (downloadedBefore == null || file.lastModified() < downloadedBefore.toEpochMilli()) &&
                file.delete()
        }

    /**
     * Once, on the first start after the move: what is in the old cache folder goes to the
     * new one. Copied rather than renamed: a renamed file keeps the cache's group, and
     * Android goes on counting it as cache in the app's storage. Anything that cannot be
     * moved is left behind and fetched again when it is next opened.
     */
    fun moveOutOfCache(
        from: File,
        to: File,
    ) {
        if (!from.isDirectory) return
        try {
            to.mkdirs()
            from.listFiles()?.forEach { file ->
                val dest = File(to, file.name)
                if (!dest.exists()) file.copyTo(dest)
                file.delete()
            }
            from.delete()
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Could not move the downloaded articles out of the cache", e)
        }
    }

    private fun files(dir: File): List<File> = dir.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }?.toList().orEmpty()
}
