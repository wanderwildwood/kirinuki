package com.wanderwildwood.kirinuki.net

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
object SmolnetUrlHandler : URLStreamHandler() {
    /**
     * Nothing opens one of these this way. Gemini needs trust on first use rather than the
     * JVM's trust store, and gopher is a bare socket; both own their own connection.
     */
    override fun openConnection(u: URL?): URLConnection =
        throw UnsupportedOperationException("Fetched by its own client, not through URL")

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

fun isGeminiUrl(spec: String): Boolean = spec.startsWith("gemini://", ignoreCase = true)

fun isGopherUrl(spec: String): Boolean = spec.startsWith("gopher://", ignoreCase = true)

/** An address this app can fetch but the JVM cannot be relied on to parse. */
fun isSmolnetUrl(spec: String): Boolean = isGeminiUrl(spec) || isGopherUrl(spec)

/**
 * Parse an address, including the schemes `java.net.URL` does not know.
 *
 * Gopher is the awkward one: the JDK used to ship a handler and dropped it, and Android
 * never had one, so whether `URL("gopher://...")` works depends on where the code is
 * running. Handling it here rather than finding out at runtime.
 */
fun parseUrlLeniently(spec: String): URL =
    when {
        isSmolnetUrl(spec) -> URL(null, spec, SmolnetUrlHandler)
        else -> URL(spec)
    }
