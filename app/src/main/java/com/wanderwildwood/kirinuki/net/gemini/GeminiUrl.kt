package com.wanderwildwood.kirinuki.net.gemini

import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler

/**
 * `java.net.URL` only knows the protocols the JVM ships a handler for, and gemini is not
 * one: `URL("gemini://example.space/")` throws `MalformedURLException: unknown protocol`.
 *
 * That matters here because feed addresses are stored as `URL`, and the Room converter
 * turns anything unparseable into `URL("http://")` **without complaining** — so a
 * subscribed capsule would come back out of the database as a broken http address and
 * nobody would be told.
 *
 * Registering a handler globally (`URL.setURLStreamHandlerFactory`) can only be done once
 * per process and throws if anything else got there first, which is a poor thing to risk
 * at startup. The three-argument `URL` constructor takes a handler directly and skips the
 * lookup entirely, so the handler stays local to this app's parsing.
 */
object GeminiUrlHandler : URLStreamHandler() {
    /**
     * Nothing opens a Gemini connection this way -- [GeminiClient] owns its own socket,
     * because the protocol needs trust on first use rather than the JVM's trust store.
     */
    override fun openConnection(u: URL?): URLConnection =
        throw UnsupportedOperationException("Gemini is fetched by GeminiClient, not by URL")

    /**
     * The inherited implementations resolve the host to compare and hash, which means a
     * DNS lookup -- on whatever thread happens to put a feed in a set.
     */
    override fun hashCode(u: URL): Int = u.toString().hashCode()

    override fun equals(
        u1: URL,
        u2: URL,
    ): Boolean = u1.toString() == u2.toString()
}

/** True for an address this app can fetch but the JVM cannot parse unaided. */
fun isGeminiUrl(spec: String): Boolean = spec.startsWith("gemini://", ignoreCase = true)

/**
 * Parse an address, including the schemes `java.net.URL` does not know.
 */
fun parseUrlWithGemini(spec: String): URL =
    when {
        isGeminiUrl(spec) -> URL(null, spec, GeminiUrlHandler)
        else -> URL(spec)
    }
