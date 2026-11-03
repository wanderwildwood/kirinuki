package com.wanderwildwood.kirinuki.net

import java.net.URI
import java.net.URISyntaxException

/**
 * The address in what a QR code said, or null when it said something else.
 *
 * A code is made on a computer by whoever is reading, but it is still text from outside the
 * app, and it goes straight into the address box. So only what the box could ever fetch gets
 * through: a web address (https or http) or a capsule (gemini, gopher, spartan). Everything
 * else a code can carry -- `data:`, `javascript:`, `file:`, `content:`, `intent:`, a Wi-Fi
 * login, a contact card -- is refused, never passed on.
 *
 * Generators differ in what they put round the address, so it is trimmed, a headline in front
 * of it is skipped the way a share is read, and a bare `example.org/feed.xml` (some generators
 * drop the scheme) is taken as https, which is what the box itself assumes when one is typed.
 */
fun scannedAddress(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > MAX_SCANNED_LENGTH) return null
    return trimmed
        .split(Regex("\\s+"))
        .firstNotNullOfOrNull { addressOrNull(it) }
}

private fun addressOrNull(token: String): String? {
    if (token.any { it.isISOControl() }) return null
    val scheme = token.substringBefore(':', missingDelimiterValue = "").lowercase()
    val candidate =
        when {
            // "example.org:8080/feed" has a colon too, so a scheme is only what comes before
            // "://" -- or before a bare colon that is plainly not a port.
            token.contains("://") -> token
            scheme.isNotEmpty() && token.substringAfter(':').firstOrNull()?.isDigit() != true -> return null
            BARE_HOST.matches(token) -> "https://$token"
            else -> return null
        }
    val uri =
        try {
            URI(candidate)
        } catch (e: URISyntaxException) {
            return null
        }
    if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) return null
    val host = uri.host ?: return null
    if (host.isBlank()) return null
    // A host with no dot is a word, not a server; localhost is the one exception worth keeping.
    if (!host.contains('.') && !host.equals("localhost", ignoreCase = true)) return null
    return candidate
}

private val ALLOWED_SCHEMES = setOf("https", "http", "gemini", "gopher", "spartan")

/** A host name with at least one dot, an optional port, and an optional path after it. */
private val BARE_HOST = Regex("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+(:\\d{1,5})?(/\\S*)?$")

/** Far longer than any feed address; a code holding more than this is not one. */
const val MAX_SCANNED_LENGTH = 2048
