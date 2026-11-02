package com.wanderwildwood.kirinuki.sync.greader

/*
 * The two spellings of a Google Reader item id, and the stream names the API is built on.
 *
 * Adapted from Read You (https://github.com/Ashinch/ReadYou, GPL-3.0),
 * app/src/main/java/me/ash/reader/infrastructure/rss/provider/greader/GoogleReaderAPI.kt,
 * whose table of the two forms is kept here as it was written:
 *
 * || Long form                                          || Short form           || notes ||
 * || `tag:google.com,2005:reader/item/5d0cfa30041d4348` || `6705009029382226760` ||       ||
 * || `tag:google.com,2005:reader/item/024025978b5e50d2` || `162170919393841362`  || Long form needs 0-padding ||
 * || `tag:google.com,2005:reader/item/fb115bd6d34a8e9f` || `-355401917359550817` || Short form ends up being negative ||
 *
 * stream/items/ids answers in the short form, stream/items/contents in the long form, and
 * edit-tag takes either. This app keeps the long form, the one the contents carry.
 */
object GReaderIds {
    const val ITEM_PREFIX = "tag:google.com,2005:reader/item/"

    const val READING_LIST = "user/-/state/com.google/reading-list"
    const val READ = "user/-/state/com.google/read"
    const val STARRED = "user/-/state/com.google/starred"
    const val LABEL_PREFIX = "user/-/label/"

    private val labelStream = "user/[^/]+/label/".toRegex()

    fun isLong(id: String): Boolean = id.startsWith(ITEM_PREFIX) && id.removePrefix(ITEM_PREFIX).length == 16

    fun isShort(id: String): Boolean = id.toLongOrNull() != null

    fun isValid(id: String): Boolean = isLong(id) || isShort(id)

    /** Either form in, the long form out; null for something that is neither. */
    fun toLong(id: String): String? =
        when {
            isLong(id) -> id.lowercase().let { ITEM_PREFIX + it.removePrefix(ITEM_PREFIX) }
            isShort(id) -> ITEM_PREFIX + "%016x".format(id.toLong())
            else -> null
        }

    /** Either form in, the short (signed decimal) form out; null for something that is neither. */
    fun toShort(id: String): String? =
        when {
            isShort(id) -> id
            isLong(id) -> java.lang.Long.parseUnsignedLong(id.removePrefix(ITEM_PREFIX), 16).toString()
            else -> null
        }

    /** `user/-/label/Blogs` or `user/1005921515/label/Blogs` to `Blogs`; anything else to null. */
    fun labelName(streamId: String?): String? =
        streamId?.takeIf { labelStream.containsMatchIn(it) }?.replace(labelStream, "")

    fun labelStream(name: String): String = LABEL_PREFIX + name
}
