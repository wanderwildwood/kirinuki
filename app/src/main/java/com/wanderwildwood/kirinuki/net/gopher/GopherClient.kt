package com.wanderwildwood.kirinuki.net.gopher

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/**
 * Gopher (RFC 1436), which is smaller than Gemini: open a socket, send the selector and a
 * CRLF, read until the server closes. There is no status line, no content type and no
 * encryption — what you asked for is what comes back, or nothing does.
 *
 * The type of the thing is carried in the *address* rather than the response: in
 * `gopher://host/1/foo` the `1` says "this is a menu". That is why [GopherResponse] is
 * decided before the fetch rather than after it.
 */
class GopherClient(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxBodyBytes: Int = 2 * 1024 * 1024,
) {
    fun fetch(url: String): GopherResponse {
        val uri = URI(url)
        require(uri.scheme == "gopher") { "not a gopher URL: $url" }
        val host = uri.host ?: throw GopherException("no host in $url")
        val port = if (uri.port == -1) DEFAULT_PORT else uri.port

        // The path is "/<type><selector>". An empty path means the root menu.
        val path = uri.path.orEmpty()
        val type = path.getOrNull(1) ?: MENU
        val selector = if (path.length > 2) path.substring(2) else ""

        val body =
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                // ⚠ Without this a server that accepts and then says nothing holds the
                // thread until the process dies.
                socket.soTimeout = readTimeoutMs
                socket.getOutputStream().apply {
                    write((selector + "\r\n").toByteArray(Charsets.UTF_8))
                    flush()
                }
                readAll(socket)
            }

        // Gopher predates character sets being declared. Latin-1 never throws and UTF-8
        // is what modern servers actually send, so try the strict one and fall back.
        val text =
            runCatching { String(body, Charsets.UTF_8) }
                .getOrElse { String(body, Charsets.ISO_8859_1) }

        return when (type) {
            MENU -> GopherResponse.Menu(url, text)
            TEXT -> GopherResponse.Text(url, stripPeriodEscaping(text))
            else -> GopherResponse.Unsupported(url, type)
        }
    }

    private fun readAll(socket: Socket): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        val input = socket.getInputStream()
        while (true) {
            val n = input.read(buffer)
            if (n == -1) break
            if (out.size() + n > maxBodyBytes) {
                throw GopherException("response is larger than ${maxBodyBytes / 1024}KB")
            }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /**
     * A text file ends with a line holding a single period, and any real line that began
     * with a period was sent doubled. Both are undone here rather than shown to a reader.
     */
    private fun stripPeriodEscaping(text: String): String =
        text
            .replace("\r\n", "\n")
            .lineSequence()
            .takeWhile { it != "." }
            .map { if (it.startsWith("..")) it.drop(1) else it }
            .joinToString("\n")

    companion object {
        const val DEFAULT_PORT = 70
        private const val MENU = '1'
        private const val TEXT = '0'
    }
}

sealed interface GopherResponse {
    val url: String

    data class Menu(
        override val url: String,
        val text: String,
    ) : GopherResponse

    data class Text(
        override val url: String,
        val text: String,
    ) : GopherResponse

    data class Unsupported(
        override val url: String,
        val type: Char,
    ) : GopherResponse
}

class GopherException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
