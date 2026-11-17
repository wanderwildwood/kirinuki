package com.wanderwildwood.kirinuki.podcast

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import android.webkit.MimeTypeMap
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.blob.blobFile
import com.wanderwildwood.kirinuki.blob.blobOutputStream
import com.wanderwildwood.kirinuki.db.room.Feed
import com.wanderwildwood.kirinuki.db.room.FeedItem
import com.wanderwildwood.kirinuki.util.FilePathProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A folder of sound files on the phone, followed as one podcast.
 *
 * A feed needs an address, so the folder gets one nothing answers at, as the saved pages do:
 * `.invalid` never resolves, and the folder's own address, as the system's file picker gave
 * it, rides along in the query. Nothing here goes to the network; the sync leaves these out
 * and reads the folder instead.
 */
private const val LOCAL_FOLDER_HOST = "folder.kirinuki.invalid"
private const val TREE_PARAM = "tree="

fun localFolderUrl(tree: Uri): URL = URL("https://$LOCAL_FOLDER_HOST/?$TREE_PARAM" + URLEncoder.encode(tree.toString(), "UTF-8"))

/** ⚠ Read off the text: java.net.URL.host is fine, but equals would resolve it. */
val Feed.isLocalFolder: Boolean
    get() = url.host == LOCAL_FOLDER_HOST

val Feed.localFolderTree: Uri?
    get() =
        url.query
            ?.takeIf { isLocalFolder && it.startsWith(TREE_PARAM) }
            ?.let { Uri.parse(URLDecoder.decode(it.removePrefix(TREE_PARAM), "UTF-8")) }

/** The picker's answer kept past a restart; without this the folder is unreadable tomorrow. */
fun keepFolderPermission(
    context: Context,
    tree: Uri,
) {
    context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/** Given back when the folder is unsubscribed, so the app holds on to nothing it no longer reads. */
fun releaseFolderPermission(
    context: Context,
    tree: Uri,
) {
    try {
        context.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (e: SecurityException) {
        // Not held any more; nothing to give back.
    }
}

/** One sound file in the folder, as the picker's provider describes it. */
internal data class FolderFile(
    val documentId: String,
    val uri: Uri,
    /** Inside the chosen folder, so a book split into CD 1 and CD 2 sorts as one. */
    val path: String,
    val name: String,
    val type: String,
    val modified: Long,
)

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav")

/** Deep enough for an author / book / disc layout, and no deeper. */
private const val MAX_DEPTH = 4

/**
 * Natural order: "Part 2" before "Part 10", case aside. Digit runs compare as numbers.
 */
internal val naturalOrder: Comparator<String> =
    Comparator { a, b ->
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ei = i
                while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j
                while (ej < b.length && b[ej].isDigit()) ej++
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return@Comparator na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return@Comparator c
                i = ei
                j = ej
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return@Comparator c
                i++
                j++
            }
        }
        (a.length - i) - (b.length - j)
    }

/** The file's name without its ending, which is what most people name an episode by. */
internal fun titleFromName(name: String): String = name.substringBeforeLast('.', name).ifBlank { name }

/** A type the Podcasts row will count as sound, even where the provider only says "a file". */
internal fun audioType(
    name: String,
    given: String?,
): String? {
    if (given?.startsWith("audio/") == true) return given
    val ext = name.substringAfterLast('.', "").lowercase()
    if (ext !in AUDIO_EXTENSIONS) return null
    return MimeTypeMap
        .getSingleton()
        .getMimeTypeFromExtension(ext)
        ?.takeIf { it.startsWith("audio/") }
        ?: "audio/$ext"
}

/**
 * Reads the folders into their feeds. Every file is an episode, named by its own title tag
 * or else its file name, in the folder's name order: the last name counts as the newest, so
 * a list newest first starts at the end of the folder, as a podcast does.
 *
 * A file that is gone takes its episode with it. A folder that cannot be read at all (the
 * card taken out, the permission gone) leaves everything as it was.
 */
class LocalFolderSync(
    override val di: DI,
) : DIAware {
    private val repository: Repository by instance()
    private val contentResolver: ContentResolver by instance()
    private val context: android.app.Application by instance()
    private val filePathProvider: FilePathProvider by instance()

    // The job and a pull-to-refresh can both arrive at once; one reading at a time, or both
    // would insert the same new file.
    private val mutex = Mutex()

    /** Every folder followed. True if all of them could be read. */
    suspend fun syncAll(): Boolean =
        repository
            .getFeedsOrderedByUrl()
            .filter { it.isLocalFolder }
            .map { sync(it) }
            .all { it }

    suspend fun sync(feed: Feed): Boolean =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val tree = feed.localFolderTree ?: return@withContext false
                val files =
                    try {
                        listAudio(tree)
                    } catch (e: Exception) {
                        // SecurityException when the permission is gone, IllegalArgumentException
                        // or FileNotFoundException when the folder is.
                        Log.w(LOG_TAG, "Could not read the folder of feed ${feed.id}", e)
                        return@withContext false
                    }
                store(feed, tree, files)
                true
            }
        }

    private suspend fun store(
        feed: Feed,
        tree: Uri,
        files: List<FolderFile>,
    ) {
        val sorted = files.sortedWith(compareBy(naturalOrder) { it.path })
        val now = Instant.now()
        // The newest file's time, at most now, is where the last name sits; each name before
        // it a second earlier. Its order among the other podcasts is then roughly its own age.
        val top =
            sorted
                .maxOfOrNull { it.modified }
                ?.takeIf { it > 0L }
                ?.let { minOf(Instant.ofEpochMilli(it), now) }
                ?: now
        val folderName = folderName(tree) ?: feed.title
        val items =
            sorted.mapIndexed { index, file ->
                val item =
                    repository.loadFeedItem(guid = file.documentId, feedId = feed.id)
                        ?: FeedItem(guid = file.documentId, firstSyncedTime = now).apply {
                            // Read once, when the file first turns up: a tag is a slow read
                            // on a big file, and it does not change.
                            plainTitle = (metadataTitle(file.uri) ?: titleFromName(file.name)).take(200)
                            @Suppress("DEPRECATION")
                            title = plainTitle
                        }
                item.feedId = feed.id
                item.enclosureLink = file.uri.toString()
                item.enclosureType = file.type
                item.pubDate =
                    ZonedDateTime.ofInstant(
                        if (file.modified > 0L) Instant.ofEpochMilli(file.modified) else item.firstSyncedTime,
                        ZoneId.systemDefault(),
                    )
                item.primarySortTime = top.minusSeconds((sorted.size - 1 - index).toLong())
                item to file.path
            }
        repository.upsertFeedItems(items) { item, text ->
            filePathProvider.articleDir.mkdirs()
            blobOutputStream(item.id, filePathProvider.articleDir).bufferedWriter().use { it.write(text.htmlEscaped()) }
        }

        val present = items.map { (item, _) -> item.guid }.toSet()
        val gone = repository.feedItemIdsNotIn(feedId = feed.id, guids = present)
        if (gone.isNotEmpty()) {
            gone.forEach { id ->
                try {
                    blobFile(itemId = id, filesDir = filePathProvider.articleDir).delete()
                } catch (e: Exception) {
                    Log.w(LOG_TAG, "Could not delete the text of $id", e)
                }
            }
            repository.deleteFeedItems(gone)
        }

        if (feed.title != folderName || feed.lastSync < now) {
            repository.upsertFeed(feed.copy(title = folderName, lastSync = now))
        }
    }

    private fun folderName(tree: Uri): String? =
        try {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            contentResolver.query(doc, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        } catch (e: Exception) {
            null
        }

    private fun metadataTitle(uri: Uri): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Nothing to do.
            }
        }
    }

    /** Throws when the folder cannot be read at all, so that nothing is taken away for it. */
    private fun listAudio(tree: Uri): List<FolderFile> {
        val found = mutableListOf<FolderFile>()

        fun walk(
            parentId: String,
            prefix: String,
            depth: Int,
        ) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            val cursor =
                contentResolver.query(
                    children,
                    arrayOf(
                        Document.COLUMN_DOCUMENT_ID,
                        Document.COLUMN_DISPLAY_NAME,
                        Document.COLUMN_MIME_TYPE,
                        Document.COLUMN_LAST_MODIFIED,
                    ),
                    null,
                    null,
                    null,
                ) ?: throw IllegalStateException("No answer for $children")
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    // Hidden files and the ._ shadows a Mac leaves on a card.
                    if (name.startsWith(".")) continue
                    val mime = c.getString(2)
                    val modified = if (c.isNull(3)) 0L else c.getLong(3)
                    if (mime == Document.MIME_TYPE_DIR) {
                        if (depth < MAX_DEPTH) walk(id, "$prefix$name/", depth + 1)
                        continue
                    }
                    val type = audioType(name, mime) ?: continue
                    found +=
                        FolderFile(
                            documentId = id,
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                            path = prefix + name,
                            name = name,
                            type = type,
                            modified = modified,
                        )
                }
            }
        }

        walk(DocumentsContract.getTreeDocumentId(tree), "", 0)
        return found
    }

    companion object {
        private const val LOG_TAG = "KIRINUKI_FOLDER"
    }
}

private fun String.htmlEscaped(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
