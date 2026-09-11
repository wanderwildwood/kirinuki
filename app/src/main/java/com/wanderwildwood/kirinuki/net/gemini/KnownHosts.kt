package com.wanderwildwood.kirinuki.net.gemini

import java.io.File
import java.time.Instant

/**
 * What certificate each Gemini host showed us the first time, and until when.
 *
 * Gemini has no certificate authorities. A capsule signs its own certificate, so the
 * only question a client can usefully ask is "is this the same one as last time".
 */
data class KnownHost(
    val host: String,
    val fingerprint: String,
    val expiresAt: Instant,
)

class KnownHosts(
    private val file: File,
) {
    private val hosts: MutableMap<String, KnownHost> by lazy { read() }

    @Synchronized
    fun get(host: String): KnownHost? = hosts[host]

    @Synchronized
    fun remember(entry: KnownHost) {
        hosts[entry.host] = entry
        write()
    }

    @Synchronized
    fun forget(host: String) {
        if (hosts.remove(host) != null) write()
    }

    @Synchronized
    fun all(): List<KnownHost> = hosts.values.sortedBy { it.host }

    private fun read(): MutableMap<String, KnownHost> {
        if (!file.isFile) return mutableMapOf()
        return file
            .readLines()
            .mapNotNull { line ->
                val parts = line.trim().split(' ')
                if (parts.size != 3) return@mapNotNull null
                val expiry = parts[2].toLongOrNull() ?: return@mapNotNull null
                KnownHost(parts[0], parts[1], Instant.ofEpochSecond(expiry))
            }.associateBy { it.host }
            .toMutableMap()
    }

    private fun write() {
        file.parentFile?.mkdirs()
        file.writeText(
            hosts.values
                .sortedBy { it.host }
                .joinToString("\n") { "${it.host} ${it.fingerprint} ${it.expiresAt.epochSecond}" },
        )
    }
}
