package com.wanderwildwood.kirinuki.model.tour

import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * One thing you meant to read.
 *
 * [fetchedAt] is null until a sync has been and got it, which is the difference between
 * a queued page and a page you can read on a train.
 */
data class TourEntry(
    val url: String,
    val title: String,
    val addedAt: Instant,
    val fetchedAt: Instant? = null,
) {
    val isReady: Boolean get() = fetchedAt != null
}

/**
 * The tour: pages queued while you were reading, fetched on the next sync, read whenever.
 *
 * This is the idea Offpunk is built around — you are offline, you see something worth
 * reading, you put it in the queue, and it is there when you next connect. It is written
 * from the description rather than from that code, which is AGPL-3.0 to this app's
 * GPL-3.0.
 *
 * A file rather than a table: the queue is short, it is a list in an order the reader
 * chose, and a Room entity would have meant a schema migration for something no query
 * ever needs to join against.
 */
class TourStore(
    private val file: File,
    private val cacheDir: File,
) {
    private val entries: MutableList<TourEntry> by lazy { read() }

    @Synchronized
    fun all(): List<TourEntry> = entries.toList()

    @Synchronized
    fun contains(url: String): Boolean = entries.any { it.url == url }

    @Synchronized
    fun count(): Int = entries.size

    /** Adding something already queued moves nothing: the order the reader chose stands. */
    @Synchronized
    fun add(
        url: String,
        title: String,
    ) {
        if (entries.any { it.url == url }) return
        entries.add(TourEntry(url = url, title = title, addedAt = Instant.now()))
        write()
    }

    @Synchronized
    fun remove(url: String) {
        if (entries.removeAll { it.url == url }) {
            cacheFileFor(url).delete()
            write()
        }
    }

    @Synchronized
    fun markFetched(
        url: String,
        text: String,
    ) {
        cacheDir.mkdirs()
        cacheFileFor(url).writeText(text)
        val index = entries.indexOfFirst { it.url == url }
        if (index >= 0) {
            entries[index] = entries[index].copy(fetchedAt = Instant.now())
            write()
        }
    }

    /** What a sync still has to go and get. */
    @Synchronized
    fun pending(): List<TourEntry> = entries.filterNot { it.isReady }

    @Synchronized
    fun cachedText(url: String): String? =
        cacheFileFor(url).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }

    private fun cacheFileFor(url: String): File =
        File(cacheDir, MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) })

    private fun read(): MutableList<TourEntry> {
        if (!file.isFile) return mutableListOf()
        return file
            .readLines()
            .mapNotNull { line ->
                // url \t added \t fetched-or-empty \t title. The title is last because it
                // is the only field that can contain anything.
                val parts = line.split('\t')
                if (parts.size < 4) return@mapNotNull null
                val added = parts[1].toLongOrNull() ?: return@mapNotNull null
                TourEntry(
                    url = parts[0],
                    addedAt = Instant.ofEpochSecond(added),
                    fetchedAt = parts[2].toLongOrNull()?.let { Instant.ofEpochSecond(it) },
                    title = parts.drop(3).joinToString("\t"),
                )
            }.toMutableList()
    }

    private fun write() {
        file.parentFile?.mkdirs()
        file.writeText(
            entries.joinToString("\n") {
                listOf(
                    it.url,
                    it.addedAt.epochSecond.toString(),
                    it.fetchedAt?.epochSecond?.toString().orEmpty(),
                    it.title.replace('\t', ' ').replace('\n', ' '),
                ).joinToString("\t")
            },
        )
    }
}
