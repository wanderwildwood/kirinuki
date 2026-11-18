package com.wanderwildwood.kirinuki.blob

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

fun blobFile(
    itemId: Long,
    filesDir: File,
): File = File(filesDir, "$itemId.txt.gz")

@Throws(IOException::class)
fun blobInputStream(
    itemId: Long,
    filesDir: File,
): InputStream = GZIPInputStream(blobFile(itemId = itemId, filesDir = filesDir).inputStream())

/**
 * Stores an article's text. It is written aside and moved into place whole, so a write cut
 * short (the process killed mid-sync) leaves the old file or none, never a broken one: a
 * server article is written only once and would otherwise fail to load for good.
 */
@Throws(IOException::class)
fun writeBlob(
    itemId: Long,
    filesDir: File,
    text: String,
) {
    val target = blobFile(itemId = itemId, filesDir = filesDir)
    val partial = File(target.path + ".part")
    try {
        GZIPOutputStream(partial.outputStream()).bufferedWriter().use { it.write(text) }
        if (!partial.renameTo(target)) {
            throw IOException("could not move the article text into place")
        }
    } catch (e: Throwable) {
        partial.delete()
        throw e
    }
}

fun blobFullFile(
    itemId: Long,
    filesDir: File,
): File = File(filesDir, "$itemId.full.html.gz")

@Throws(IOException::class)
fun blobFullInputStream(
    itemId: Long,
    filesDir: File,
): InputStream = GZIPInputStream(blobFullFile(itemId = itemId, filesDir = filesDir).inputStream())

@Throws(IOException::class)
fun blobFullOutputStream(
    itemId: Long,
    filesDir: File,
): OutputStream = GZIPOutputStream(blobFullFile(itemId = itemId, filesDir = filesDir).outputStream())
