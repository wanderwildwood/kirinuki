package com.wanderwildwood.kirinuki.model.opml

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.archmodel.SettingsStore
import com.wanderwildwood.kirinuki.db.room.Feed
import com.wanderwildwood.kirinuki.background.runOnceRssSync
import com.wanderwildwood.kirinuki.db.room.FeedDao
import com.wanderwildwood.kirinuki.model.OPMLParserHandler
import com.wanderwildwood.kirinuki.podcast.isLocalFolder
import com.wanderwildwood.kirinuki.util.Either
import com.wanderwildwood.kirinuki.util.ToastMaker
import com.wanderwildwood.kirinuki.util.logDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance
import kotlin.system.measureTimeMillis

private const val LOG_TAG = "KIRINUKI_OPMLACTIONS"

/**
 * Exports OPML on a background thread
 */
suspend fun exportOpml(
    di: DI,
    uri: Uri,
): Either<OpmlExportError, Unit> =
    Either.catching(
        onCatch = {
            Log.e(LOG_TAG, "Failed to export OPML", it)
            val toastMaker = di.direct.instance<ToastMaker>()
            toastMaker.makeToast(R.string.failed_to_export_OPML)

            OpmlExportUnknownError(it)
        },
    ) {
        withContext(Dispatchers.IO) {
            val time =
                measureTimeMillis {
                    val contentResolver: ContentResolver by di.instance()
                    val feedDao: FeedDao by di.instance()
                    val settingsStore: SettingsStore by di.instance()
                    contentResolver.openOutputStream(uri)?.let {
                        writeOutputStream(
                            os = it,
                            settings = settingsStore.getAllSettings(),
                            blockedPatterns = settingsStore.blockListPreference.first(),
                            tags = feedDao.loadTags(),
                        ) { tag ->
                            // A folder on this phone means nothing on another, and its
                            // permission does not travel with the file.
                            feedDao.getFeedsByTitle(tag = tag).filterNot { it.isLocalFolder }
                        }
                    }
                }
            logDebug(LOG_TAG, "Exported OPML in $time ms on ${Thread.currentThread().name}")
        }
    }

/** What an OPML file would bring in: feeds not yet followed, feeds already here, and whether it carries settings. */
data class OpmlPreview(
    val newFeeds: Int,
    val knownFeeds: Int,
    val hasSettings: Boolean,
)

/**
 * Reads an OPML file without saving anything, to say what importing it would do. Null when the
 * file cannot be read as OPML.
 */
suspend fun previewOpml(
    di: DI,
    uri: Uri,
): OpmlPreview? =
    withContext(Dispatchers.IO) {
        val feedDao: FeedDao by di.instance()
        val contentResolver: ContentResolver by di.instance()
        val newUrls = mutableSetOf<String>()
        val knownUrls = mutableSetOf<String>()
        var hasSettings = false
        val counter =
            object : OPMLParserHandler {
                override suspend fun saveFeed(feed: Feed) {
                    val url = feed.url.toString()
                    if (feedDao.loadFeedWithUrl(feed.url) == null) newUrls += url else knownUrls += url
                }

                override suspend fun saveSetting(
                    key: String,
                    value: String,
                ) {
                    hasSettings = true
                }

                override suspend fun saveBlocklistPatterns(patterns: Iterable<String>) {
                    if (patterns.any()) hasSettings = true
                }
            }
        val parsed =
            try {
                contentResolver.openInputStream(uri)?.use { OpmlPullParser(counter).parseInputStreamWithFallback(it) }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to read OPML", e)
                null
            }
        if (parsed == null || parsed.isLeft()) null else OpmlPreview(newUrls.size, knownUrls.size, hasSettings)
    }

/**
 * Imports OPML on a background thread
 */
suspend fun importOpml(
    di: DI,
    uri: Uri,
) = withContext(Dispatchers.IO) {
    val opmlToRoom: OPMLParserHandler by di.instance()
    try {
        val time =
            measureTimeMillis {
                val parser = OpmlPullParser(opmlToRoom)
                val contentResolver: ContentResolver by di.instance()
                val result =
                    contentResolver.openInputStream(uri).use {
                        it?.let { stream ->
                            parser.parseInputStreamWithFallback(stream)
                        }
                    }
                runOnceRssSync(di = di, triggeredByUser = true)

                if (result?.isLeft() == true) {
                    val toastMaker = di.direct.instance<ToastMaker>()
                    toastMaker.makeToast(R.string.failed_to_import_OPML)
                }
            }
        logDebug(LOG_TAG, "Imported OPML in $time ms on ${Thread.currentThread().name}")
    } catch (e: Throwable) {
        Log.e(LOG_TAG, "Failed to import OPML", e)
        val toastMaker = di.direct.instance<ToastMaker>()
        toastMaker.makeToast(R.string.failed_to_import_OPML)
    }
}

sealed class OpmlExportError {
    abstract val throwable: Throwable?
}

data class OpmlExportUnknownError(
    override val throwable: Throwable,
) : OpmlExportError()
