package com.wanderwildwood.kirinuki.model

import android.util.Log
import com.ibm.icu.text.CharsetDetector
import com.ibm.icu.text.CharsetMatch
import com.wanderwildwood.kirinuki.archmodel.Repository
import com.wanderwildwood.kirinuki.blob.blobFullFile
import com.wanderwildwood.kirinuki.blob.blobFullOutputStream
import com.wanderwildwood.kirinuki.db.room.FeedItemForFetching
import com.wanderwildwood.kirinuki.db.room.MAX_TITLE_LENGTH
import com.wanderwildwood.kirinuki.db.room.estimateWordCount
import com.wanderwildwood.kirinuki.ui.text.HtmlToPlainTextConverter
import com.wanderwildwood.kirinuki.util.Either
import com.wanderwildwood.kirinuki.util.FilePathProvider
import com.wanderwildwood.kirinuki.util.flatMap
import com.wanderwildwood.kirinuki.util.flatten
import com.wanderwildwood.kirinuki.util.left
import com.wanderwildwood.kirinuki.util.logDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.OkHttpClient
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.net.URL
import java.nio.charset.Charset

class FullTextParser(
    override val di: DI,
) : DIAware {
    private val repository: Repository by instance()
    private val okHttpClient: OkHttpClient by instance()
    private val filePathProvider: FilePathProvider by instance()

    suspend fun parseFullArticlesForMissing(): Boolean {
        logDebug(LOG_TAG, "Parsing full texts for missing")
        val itemsToSync: List<FeedItemForFetching> =
            repository
                .getFeedsItemsWithDefaultFullTextNeedingDownload()
                .firstOrNull()
                ?: emptyList()

        return itemsToSync
            .map { feedItem ->
                parseFullArticleIfMissing(
                    feedItem = feedItem,
                ).isRight()
            }.fold(true) { acc, value ->
                acc && value
            }
    }

    suspend fun parseFullArticleIfMissing(feedItem: FeedItemForFetching): Either<FeedParserError, Unit> {
        val fullArticleFile =
            blobFullFile(itemId = feedItem.id, filesDir = filePathProvider.fullArticleDir)

        return try {
            if (fullArticleFile.isFile) {
                Either.Right(Unit)
            } else {
                parseFullArticle(feedItem = feedItem)
            }
        } catch (t: Throwable) {
            FullTextDecodingFailure(feedItem.link ?: "", t).left()
        } finally {
            repository.markAsFullTextDownloaded(feedItem.id)
        }.onLeft {
            Log.w(LOG_TAG, "Failed to parse missing full article: $it", it.throwable)
        }
    }

    private suspend fun parseFullArticle(feedItem: FeedItemForFetching): Either<FeedParserError, Unit> =
        withContext(Dispatchers.Default) {
            logDebug(LOG_TAG, "Fetching full page ${feedItem.link}, ${feedItem.id}")
            val url = feedItem.link ?: return@withContext Either.Left(NoUrl())

            fetchReadable(url).map { (article, pageTitle) ->
                logDebug(LOG_TAG, "Writing article ${feedItem.link}")
                withContext(Dispatchers.IO) {
                    article?.let { articleContent ->
                        filePathProvider.fullArticleDir.mkdirs()
                        blobFullOutputStream(feedItem.id, filePathProvider.fullArticleDir)
                            .bufferedWriter()
                            .use { writer ->
                                writer.write(articleContent)
                            }

                        // Update word count on item
                        val converter = HtmlToPlainTextConverter()
                        val plainText = converter.convert(articleContent)
                        val wordCount = estimateWordCount(plainText)

                        repository.updateWordCountFull(feedItem.id, wordCount)
                    }
                    // A saved page arrived with only its address for a title.
                    pageTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { title ->
                        repository.replaceAddressTitle(feedItem.id, title.take(MAX_TITLE_LENGTH))
                    }
                }
                Unit
            }
        }

    /**
     * One page fetched and put through Readability: the article as HTML, and what the page calls
     * itself. Nothing is stored -- the feed pipeline above keeps what it gets, and a page read
     * straight from a shared link or a scanned code is only shown.
     */
    suspend fun fetchReadable(url: String): Either<FeedParserError, Pair<String?, String?>> =
        fetchPage(url).flatMap { page ->
            when (page) {
                is FetchedPage.Article -> Either.Right(page.html to page.title)
                is FetchedPage.PlainText -> Either.Left(UnsupportedContentType(url = url, mimeType = "text/plain"))
            }
        }

    /**
     * One address fetched for the page reader: an HTML page put through Readability, or a plain
     * text file kept as it is (its first [MAX_PLAIN_TEXT_BYTES], cut at a line end). Anything
     * else is [UnsupportedContentType], as it always was.
     */
    suspend fun fetchPage(url: String): Either<FeedParserError, FetchedPage> =
        withContext(Dispatchers.Default) {
            okHttpClient
                .curlAndOnResponse(URL(url)) { response ->
                    Either.catching<FeedParserError, Either<FeedParserError, FetchedPage>>(
                        onCatch = { t ->
                            FullTextDecodingFailure(url, t)
                        },
                    ) {
                        val body = response.body
                        val contentType = body.contentType()

                        when (pageKindOf(contentType?.toString(), url)) {
                            PageKind.OTHER ->
                                return@catching UnsupportedContentType(
                                    url = url,
                                    mimeType = contentType?.toString() ?: "null",
                                ).left()

                            PageKind.TEXT -> {
                                // A text file is shown, not parsed, so a long one is cut rather
                                // than refused: the start of it is still worth reading.
                                val buffer = okio.Buffer()
                                body.use {
                                    val source = body.source()
                                    while (buffer.size <= MAX_PLAIN_TEXT_BYTES) {
                                        if (source.read(buffer, 8_192) == -1L) break
                                    }
                                }
                                val bytes = buffer.readByteArray()
                                val charset =
                                    contentType?.charset()
                                        ?: detectCharset(bytes)?.let { runCatching { toJavaCharset(it) }.getOrNull() }
                                        ?: java.nio.charset.StandardCharsets.UTF_8
                                val text =
                                    plainTextShown(String(bytes, charset), cut = bytes.size > MAX_PLAIN_TEXT_BYTES)
                                        ?: return@catching UnsupportedContentType(
                                            url = url,
                                            mimeType = contentType?.toString() ?: "null",
                                        ).left()
                                return@catching Either.Right(text)
                            }

                            PageKind.HTML -> Unit
                        }

                        val contentLength = body.contentLength()
                        logDebug(LOG_TAG, "contentLength: $contentLength")
                        if (contentLength > MAX_FULL_TEXT_BYTES) {
                            Log.i(LOG_TAG, "contentLength exceeds MAX LIMIT ($MAX_FULL_TEXT_BYTES) bytes")
                            return@catching FullTextTooLarge(url = url, maxBytes = MAX_FULL_TEXT_BYTES).left()
                        }

                        val maxBytes = MAX_FULL_TEXT_BYTES.toLong()
                        val buffer = okio.Buffer()

                        body.use {
                            val source = body.source()
                            while (buffer.size <= maxBytes) {
                                val read = source.read(buffer, 8_192)
                                if (read == -1L) break
                            }
                        }

                        logDebug(LOG_TAG, "buffer size: ${buffer.size}")
                        if (buffer.size > maxBytes) {
                            Log.i(LOG_TAG, "buffer exceeds MAX LIMIT ($MAX_FULL_TEXT_BYTES) bytes")
                            return@catching FullTextTooLarge(url = url, maxBytes = MAX_FULL_TEXT_BYTES).left()
                        }

                        val bytes = buffer.readByteArray()

                        var charset: Charset? = contentType?.charset()

                        if (charset == null) {
                            charset = findMetaCharset(bytes)?.let { toJavaCharset(it) }
                            logDebug(LOG_TAG, "No charset in content type, meta charset: $charset")
                        }

                        if (charset == null) {
                            charset = detectCharset(bytes)?.let { toJavaCharset(it) }
                            logDebug(LOG_TAG, "No charset in content type, guessing charset: $charset")
                        }

                        logDebug(LOG_TAG, "Full article charset: $charset")

                        val html = String(bytes, charset ?: java.nio.charset.StandardCharsets.UTF_8)
                        logDebug(LOG_TAG, "Parsing article $url")
                        val (article, title) = parseFullArticleAndTitle(url, html)
                        Either.Right(FetchedPage.Article(html = article, title = title))
                    }
                }.flatten()
        }

    companion object {
        internal const val LOG_TAG = "KIRINUKI_FULLTEXT"

        // giving a bit of leeway to allow for inline images and junk like inline CSS and javscript
        // but this is way higher than is possible to render if actual HTML.
        // It will be truncated by HtmlLinearizer in that case.
        const val MAX_FULL_TEXT_BYTES = 3 * 1024 * 1024

        // A plain text file is laid out whole, in one block, on a phone; 256 KB is a long
        // program or a short book, and about as much as the Kompakt lays out without a wait.
        const val MAX_PLAIN_TEXT_BYTES = 256 * 1024
    }
}

/**
 * A text file in pieces for the reader's list: runs of lines split at blank lines, and no run
 * longer than [maxLines], so the text starts under the title on the first page and every page
 * ends on a whole line. The space between list items stands in for the blank line.
 */
fun textBlocks(
    text: String,
    maxLines: Int = TEXT_BLOCK_LINES,
): List<String> {
    val blocks = mutableListOf<String>()
    val run = mutableListOf<String>()
    fun close() {
        if (run.isNotEmpty()) blocks += run.joinToString("\n")
        run.clear()
    }
    text.lines().forEach { line ->
        when {
            line.isBlank() -> close()
            else -> {
                run += line
                if (run.size >= maxLines) close()
            }
        }
    }
    close()
    return blocks
}

const val TEXT_BLOCK_LINES = 12

/** What the page reader got from an address. */
sealed interface FetchedPage {
    data class Article(
        val html: String?,
        val title: String?,
    ) : FetchedPage

    /** A text file as it was sent; [cut] when only its start is here. */
    data class PlainText(
        val text: String,
        val cut: Boolean,
    ) : FetchedPage
}

enum class PageKind { HTML, TEXT, OTHER }

/**
 * Whether a response is a page to put through Readability, a text file to show as it is, or
 * something this app does not read. A text file is text/plain, Markdown, CSV or a text/x-*
 * source type; a server that sends a generic type (octet-stream, or none at all) for an
 * address ending in one of [TEXT_EXTENSIONS] is taken at the address's word. A NUL byte in
 * what arrives still turns it away (see [plainTextShown]).
 */
fun pageKindOf(
    contentType: String?,
    url: String,
): PageKind {
    val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    val path =
        url.substringBefore('#').substringBefore('?').lowercase()
    return when {
        type == "text/html" -> PageKind.HTML
        type in TEXT_TYPES || type.startsWith("text/x-") -> PageKind.TEXT
        type in GENERIC_TYPES && TEXT_EXTENSIONS.any { path.endsWith(".$it") } -> PageKind.TEXT
        else -> PageKind.OTHER
    }
}

private val GENERIC_TYPES = setOf("", "application/octet-stream", "binary/octet-stream", "application/unknown")

/** Text meant to be read as it is: prose, tables of values, and source code. */
private val TEXT_TYPES = setOf("text/plain", "text/markdown", "text/x-markdown", "text/csv")

/** Extensions believed when a server sends only a generic type. */
private val TEXT_EXTENSIONS =
    setOf(
        "txt", "text", "md", "markdown", "csv", "log",
        "c", "h", "cc", "cpp", "hpp", "py", "rb", "sh", "rs", "go", "java", "kt", "lua", "pl",
        "hs", "ml", "lisp", "scm", "el", "tal", "diff", "patch",
    )

/**
 * The text as it will be shown, or null when it is not text after all (a NUL byte is the sign
 * of a binary file sent as text). When the fetch was [cut], the last, partial line is dropped
 * so the page ends on a whole one. Line ends are made plain \n.
 */
fun plainTextShown(
    decoded: String,
    cut: Boolean,
): FetchedPage.PlainText? {
    if (decoded.contains('\u0000')) return null
    val text = decoded.replace("\r\n", "\n").replace('\r', '\n')
    val whole = if (cut && text.contains('\n')) text.substringBeforeLast('\n') else text
    return FetchedPage.PlainText(text = whole.trimEnd(), cut = cut)
}

fun parseFullArticle(
    uri: String,
    html: String,
): String? = parseFullArticleAndTitle(uri, html).first

/** The article, and what the page calls itself -- which a saved page needs for its title. */
fun parseFullArticleAndTitle(
    uri: String,
    html: String,
): Pair<String?, String?> {
    val article = Readability4JExtended(uri, html).parse()

    val dir = article.dir

    // Ensure dir is set on the outermost element
    val content =
        article.contentWithUtf8Encoding?.let { fullHtml ->
            if (dir?.isNotBlank() == true) {
                fullHtml.replaceFirst("<html".toRegex(), "<html dir=\"$dir\"")
            } else {
                fullHtml
            }
        }
    return content to article.title
}

/**
 * For sites which don't use UTF-8 like http://www.muhasebetr.com/rss/
 */
fun findMetaCharset(html: ByteArray): String? {
    var state = HtmlParserState.INIT
    var tagName = StringBuilder()
    var attrName = StringBuilder()
    val attrValue = StringBuilder()

    html.forEach { b ->
        val char = b.toInt().toChar()
        state = nextHtmlParseState(char, state)

        when (state) {
            HtmlParserState.TAG_NAME -> tagName.append(char)
            HtmlParserState.TAG_ATTR_NAME -> attrName.append(char)
            HtmlParserState.TAG_ATTR_VALUE -> attrValue.append(char)
            HtmlParserState.TAG_START -> {
                tagName.clear()
                attrName.clear()
                attrValue.clear()
            }

            HtmlParserState.TAG_NAME_POST -> {
                // This is the state after a attribute has been parsed (but also before)

                if (tagName.toString().equals("meta", ignoreCase = true)) {
                    val name = attrName.toString()
                    val value = attrValue.toString()
                    when {
                        name.equals("content", ignoreCase = true) && value.contains("charset=") -> {
                            return attrValue.toString().substringAfter("charset=").substringBefore(';')
                        }

                        name.equals("charset", ignoreCase = true) -> {
                            return value
                        }
                    }
                }

                attrName.clear()
                attrValue.clear()
            }

            else -> {
                // Nothing to do
            }
        }
    }
    return null
}

private fun nextHtmlParseState(
    char: Char,
    currentState: HtmlParserState,
): HtmlParserState =
    when (currentState) {
        HtmlParserState.INIT ->
            when (char) {
                '<' -> HtmlParserState.TAG_START
                else -> HtmlParserState.INIT
            }

        HtmlParserState.TAG_START ->
            when (char) {
                ' ' -> HtmlParserState.TAG_START
                '/' -> HtmlParserState.TAG_START
                '>' -> HtmlParserState.INIT
                else -> HtmlParserState.TAG_NAME
            }

        HtmlParserState.TAG_NAME ->
            when (char) {
                ' ' -> HtmlParserState.TAG_NAME_POST
                '/' -> HtmlParserState.TAG_START
                '>' -> HtmlParserState.INIT
                else -> HtmlParserState.TAG_NAME
            }

        HtmlParserState.TAG_NAME_POST ->
            when (char) {
                ' ' -> HtmlParserState.TAG_NAME_POST
                '/' -> HtmlParserState.TAG_START
                '>' -> HtmlParserState.INIT
                else -> HtmlParserState.TAG_ATTR_NAME
            }

        HtmlParserState.TAG_ATTR_NAME ->
            when (char) {
                ' ' -> HtmlParserState.TAG_NAME_POST
                '/' -> HtmlParserState.TAG_START
                '>' -> HtmlParserState.INIT
                '=' -> HtmlParserState.TAG_ATTR_EQUALS
                else -> HtmlParserState.TAG_ATTR_NAME
            }

        HtmlParserState.TAG_ATTR_EQUALS ->
            when (char) {
                ' ' -> HtmlParserState.TAG_ATTR_EQUALS
                '/' -> HtmlParserState.TAG_START
                '>' -> HtmlParserState.INIT
                '"' -> HtmlParserState.TAG_ATTR_QUOTE_START
                else -> HtmlParserState.TAG_START
            }

        HtmlParserState.TAG_ATTR_QUOTE_START ->
            when (char) {
                '"' -> HtmlParserState.TAG_NAME_POST
                else -> HtmlParserState.TAG_ATTR_VALUE
            }

        HtmlParserState.TAG_ATTR_VALUE ->
            when (char) {
                '"' -> HtmlParserState.TAG_NAME_POST
                else -> HtmlParserState.TAG_ATTR_VALUE
            }
    }

enum class HtmlParserState {
    INIT,
    TAG_START,
    TAG_NAME,
    TAG_NAME_POST,
    TAG_ATTR_NAME,
    TAG_ATTR_EQUALS,
    TAG_ATTR_QUOTE_START,
    TAG_ATTR_VALUE,
}

/**
 * For sites which don't use UTF-8 like http://www.muhasebetr.com/rss/
 */
fun detectCharset(html: ByteArray): String? {
    val detector = CharsetDetector()
    detector.setText(html)
    val matches: Array<CharsetMatch> = detector.detectAll()

    return matches.firstOrNull { it.confidence > 50 }?.name
}

private fun toJavaCharset(icuName: String): Charset {
    // Handle ICU's charset name variations
    return when (icuName.uppercase()) {
        "GB2312" -> Charset.forName("GBK") // Android maps GB2312 to GBK
        "BIG5-HKSCS" -> Charset.forName("Big5_HKSCS")
        "Shift-JIS" -> Charset.forName("Shift_JIS")
        else -> Charset.forName(icuName)
    }
}
