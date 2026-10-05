package com.wanderwildwood.kirinuki.podcast

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import java.io.File

/**
 * What the app remembers about an episode: where the listener stopped, and whether the sound
 * has been kept on the phone.
 *
 * Kept in its own preferences file rather than as columns on the feed item. A place in an
 * episode is a few numbers per episode heard, and a schema change for it would be a migration
 * every phone runs to hold something most feeds never need.
 */
class EpisodeStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Where the listener stopped, or 0 for an episode not started or heard to the end. */
    fun position(itemId: Long): Long = prefs.getLong(POSITION + itemId, 0L)

    /** How long the episode is, once the player has found out. 0 until then. */
    fun duration(itemId: Long): Long = prefs.getLong(DURATION + itemId, 0L)

    fun finished(itemId: Long): Boolean = prefs.getBoolean(FINISHED + itemId, false)

    fun savePosition(
        itemId: Long,
        positionMs: Long,
        durationMs: Long,
    ) {
        prefs.edit {
            putLong(POSITION + itemId, positionMs.coerceAtLeast(0L))
            if (durationMs > 0L) putLong(DURATION + itemId, durationMs)
            remove(FINISHED + itemId)
        }
    }

    /**
     * Heard to the end. The place goes back to the start, and the kept copy goes: an episode
     * once heard is rarely heard again, and it is tens of megabytes.
     */
    fun markFinished(itemId: Long) {
        prefs.edit {
            remove(POSITION + itemId)
            putBoolean(FINISHED + itemId, true)
        }
        removeDownload(itemId)
    }

    /** One speed for every episode: a listener who likes 1.5x likes it for all of them. */
    var speed: Float
        get() = prefs.getFloat(SPEED, 1f)
        set(value) = prefs.edit { putFloat(SPEED, value) }

    // ---- Keeping the sound on the phone ----------------------------------------------------

    /**
     * The kept copy. In the app's own external files, which is where [DownloadManager] can
     * write without a storage permission, and which goes with the app when it is uninstalled.
     */
    fun file(itemId: Long): File? = appContext.getExternalFilesDir(DIR)?.resolve(itemId.toString())

    /**
     * Where the episode stands. Asked for every time rather than remembered, because the
     * download runs in the system's download service, which finishes on its own schedule.
     */
    fun downloadState(itemId: Long): DownloadState {
        val pending = prefs.getLong(DOWNLOAD + itemId, NONE)
        if (pending != NONE) {
            val manager = appContext.getSystemService(DownloadManager::class.java)
            val status =
                manager?.query(DownloadManager.Query().setFilterById(pending))?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        null
                    } else {
                        val state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        Triple(state, done, total)
                    }
                }
            when (status?.first) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED,
                -> {
                    val (_, done, total) = status
                    return DownloadState.Downloading(
                        percent = if (total > 0) (done * 100 / total).toInt() else null,
                    )
                }

                DownloadManager.STATUS_SUCCESSFUL -> prefs.edit { remove(DOWNLOAD + itemId) }

                // Failed, or the download service has forgotten it. Either way what is on
                // disk is not a whole episode, and playing it would stop partway through.
                else -> {
                    prefs.edit { remove(DOWNLOAD + itemId) }
                    file(itemId)?.delete()
                    return if (status == null) DownloadState.None else DownloadState.Failed
                }
            }
        }
        return if (file(itemId)?.isFile == true) DownloadState.Kept else DownloadState.None
    }

    /** Hands the fetch to the system, which carries on with the app closed and the screen off. */
    fun download(
        itemId: Long,
        url: String,
        title: String,
        onlyOnWifi: Boolean,
    ) {
        val manager = appContext.getSystemService(DownloadManager::class.java) ?: return
        file(itemId)?.delete()
        val request =
            DownloadManager
                .Request(Uri.parse(url))
                .setTitle(title)
                .setDestinationInExternalFilesDir(appContext, DIR, itemId.toString())
                .setAllowedOverMetered(!onlyOnWifi)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        prefs.edit { putLong(DOWNLOAD + itemId, manager.enqueue(request)) }
    }

    fun removeDownload(itemId: Long) {
        val pending = prefs.getLong(DOWNLOAD + itemId, NONE)
        if (pending != NONE) {
            appContext.getSystemService(DownloadManager::class.java)?.remove(pending)
            prefs.edit { remove(DOWNLOAD + itemId) }
        }
        file(itemId)?.delete()
    }

    /**
     * Everything this store holds for an episode whose item is gone: its place, and its kept
     * copy. Called by the daily sweep, with the ids that still exist.
     */
    fun forgetAllBut(validIds: Set<Long>): Int {
        var removed = 0
        appContext.getExternalFilesDir(DIR)?.listFiles()?.forEach { file ->
            val id = file.name.toLongOrNull()
            if (id == null || id !in validIds) {
                if (file.delete()) removed++
            }
        }
        val stale =
            prefs.all.keys.filter { key ->
                val id = KEYED.firstOrNull { key.startsWith(it) }?.let { key.removePrefix(it).toLongOrNull() }
                id != null && id !in validIds
            }
        if (stale.isNotEmpty()) prefs.edit { stale.forEach { remove(it) } }
        return removed
    }

    companion object {
        private const val PREFS = "episodes"
        private const val DIR = "episodes"
        private const val NONE = -1L

        private const val POSITION = "position_"
        private const val DURATION = "duration_"
        private const val FINISHED = "finished_"
        private const val DOWNLOAD = "download_"
        private const val SPEED = "speed"
        private val KEYED = listOf(POSITION, DURATION, FINISHED, DOWNLOAD)
    }
}

sealed interface DownloadState {
    data object None : DownloadState

    data class Downloading(
        val percent: Int?,
    ) : DownloadState

    data object Kept : DownloadState

    data object Failed : DownloadState
}
