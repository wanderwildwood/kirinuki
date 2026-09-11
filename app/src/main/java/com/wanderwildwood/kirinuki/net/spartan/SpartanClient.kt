package com.wanderwildwood.kirinuki.net.spartan

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/**
 * Spartan: Gemini's simplifications taken further. Plain TCP on port 300, no TLS and no
 * certificates at all, which is the whole design — it trades confidentiality away rather
 * than pretending trust on first use is the same thing.
 *
 * The request is `<host> <path> <contentLength>\r\n`, so a client says which host it
 * wanted (there is no SNI to carry it) and how many bytes of body follow. The reply is
 * `<status> <meta>\r\n` and then the body. The body is gemtext, so everything after this
 * is already written.
 */
class SpartanClient(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxBodyBytes: Int = 2 * 1024 * 1024,
) {
    fun fetch(
        url: String,
        maxRedirects: Int = 5,
    ): SpartanResponse {
        var current = url
        repeat(maxRedirects + 1) { hop ->
            when (val step = fetchOnce(current)) {
                is Hop.Done -> return step.response
                is Hop.Redirect -> {
                    if (hop == maxRedirects) {
                        return SpartanResponse.Failure(current, 4, "Too many redirects")
                    }
                    current = URI(current).resolve(step.to).toString()
                }
            }
        }
        error("unreachable")
    }

    private sealed interface Hop {
        data class Done(
            val response: SpartanResponse,
        ) : Hop

        data class Redirect(
            val to: String,
        ) : Hop
    }

    private fun fetchOnce(url: String): Hop {
        val uri = URI(url)
        require(uri.scheme == "spartan") { "not a spartan URL: $url" }
        val host = uri.host ?: throw SpartanException("no host in $url")
        val port = if (uri.port == -1) DEFAULT_PORT else uri.port
        val path = uri.rawPath.orEmpty().ifBlank { "/" }

        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            // ⚠ A server that accepts and says nothing holds the thread without this.
            socket.soTimeout = readTimeoutMs

            // No body is ever sent from here: nothing in this app submits to a capsule.
            socket.getOutputStream().apply {
                write("$host $path 0\r\n".toByteArray(Charsets.UTF_8))
                flush()
            }

            val header = socket.getInputStream().readHeaderLine()
            val status = header.takeWhile { !it.isWhitespace() }.toIntOrNull()
                ?: throw SpartanException("$host answered with no status: ${header.take(40)}")
            val meta = header.dropWhile { !it.isWhitespace() }.trim()

            return when (status) {
                SUCCESS -> {
                    val mime = meta.substringBefore(';').trim().ifBlank { DEFAULT_MIME }
                    Hop.Done(
                        SpartanResponse.Body(
                            url = url,
                            mimeType = mime.lowercase(),
                            text = socket.getInputStream().readBody(),
                        ),
                    )
                }
                REDIRECT -> Hop.Redirect(meta)
                else -> Hop.Done(SpartanResponse.Failure(url, status, meta))
            }
        }
    }

    private fun InputStream.readHeaderLine(): String {
        val out = StringBuilder()
        var previous = -1
        while (out.length <= MAX_HEADER_BYTES) {
            val b = read()
            if (b == -1) throw SpartanException("connection closed before the status line")
            if (previous == '\r'.code && b == '\n'.code) return out.dropLast(1).toString()
            out.append(b.toChar())
            previous = b
        }
        throw SpartanException("status line never ended")
    }

    private fun InputStream.readBody(): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = read(buffer)
            if (n == -1) break
            if (out.size() + n > maxBodyBytes) {
                throw SpartanException("response is larger than ${maxBodyBytes / 1024}KB")
            }
            out.write(buffer, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    companion object {
        const val DEFAULT_PORT = 300
        private const val SUCCESS = 2
        private const val REDIRECT = 3
        private const val MAX_HEADER_BYTES = 1024
        private const val DEFAULT_MIME = "text/gemini"
    }
}

sealed interface SpartanResponse {
    val url: String

    data class Body(
        override val url: String,
        val mimeType: String,
        val text: String,
    ) : SpartanResponse {
        val isGemtext: Boolean get() = mimeType == "text/gemini"
    }

    data class Failure(
        override val url: String,
        val status: Int,
        val message: String,
    ) : SpartanResponse
}

class SpartanException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
