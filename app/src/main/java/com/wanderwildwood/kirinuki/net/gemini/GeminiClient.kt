package com.wanderwildwood.kirinuki.net.gemini

import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * Gemini, which is a small enough protocol to hold in your head: open TLS to port 1965,
 * send the URL and a CRLF, read back two digits, a space, a line of meaning, and — if the
 * digits began with 2 — a body.
 *
 * ⚠ This builds its **own** [SSLContext] for every connection, holding a
 * [TrustOnFirstUse] for exactly one host. It shares nothing with the app's OkHttpClient,
 * and it must stay that way: see the warning on [TrustOnFirstUse].
 */
class GeminiClient(
    private val knownHosts: KnownHosts,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxBodyBytes: Int = 2 * 1024 * 1024,
) {
    /**
     * Follows redirects, which Gemini has plenty of, up to [maxRedirects]. A capsule that
     * redirects in a loop is a capsule that never answers, so the count is the whole
     * defence against it.
     */
    fun fetch(
        url: String,
        maxRedirects: Int = 5,
    ): GeminiResponse {
        var current = url
        repeat(maxRedirects + 1) { hop ->
            when (val step = fetchOnce(current)) {
                is Hop.Done -> return step.response
                is Hop.Redirect -> {
                    if (hop == maxRedirects) {
                        return GeminiResponse.Failure(current, 53, "", tooManyRedirects = true)
                    }
                    current = resolve(current, step.to)
                }
            }
        }
        error("unreachable")
    }

    /**
     * A redirect is not one of the answers a caller can be given — [fetch] follows it —
     * so it is kept out of [GeminiResponse] entirely. Putting it in there made every
     * `when` over a response carry a branch that could never happen.
     */
    private sealed interface Hop {
        data class Done(
            val response: GeminiResponse,
        ) : Hop

        data class Redirect(
            val to: String,
        ) : Hop
    }

    private fun fetchOnce(url: String): Hop {
        val uri = URI(url)
        require(uri.scheme == "gemini") { "not a gemini URL: $url" }
        val host = uri.host ?: throw GeminiException("no host in $url")
        val port = if (uri.port == -1) DEFAULT_PORT else uri.port

        // A request is one line and at most 1024 bytes, including the CRLF.
        val request = (url + "\r\n").toByteArray(Charsets.UTF_8)
        if (request.size > MAX_REQUEST_BYTES) {
            throw GeminiException("URL is longer than Gemini allows (${request.size} bytes)")
        }

        openSocket(host, port).use { socket ->
            socket.outputStream.write(request)
            socket.outputStream.flush()

            val header = socket.inputStream.readHeaderLine()
            val status = header.take(2).toIntOrNull()
                ?: throw GeminiException("$host answered with no status: ${header.take(40)}")
            val meta = header.drop(2).trimStart()

            return when (status / 10) {
                1 -> Hop.Done(GeminiResponse.Input(url, meta, sensitive = status == 11))
                2 -> {
                    val (mime, charset) = parseMeta(meta)
                    Hop.Done(
                        GeminiResponse.Body(url, mime, charset, socket.inputStream.readBody()),
                    )
                }
                3 -> Hop.Redirect(meta)
                6 -> Hop.Done(GeminiResponse.CertificateRequired(url, meta))
                else -> Hop.Done(GeminiResponse.Failure(url, status, meta))
            }
        }
    }

    private fun openSocket(
        host: String,
        port: Int,
    ): SSLSocket {
        val context =
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(TrustOnFirstUse(host, knownHosts)), null)
            }

        val plain = Socket()
        plain.connect(InetSocketAddress(host, port), connectTimeoutMs)

        val socket = context.socketFactory.createSocket(plain, host, port, true) as SSLSocket
        // ⚠ Without a read timeout a capsule that opens a connection and says nothing
        // holds the thread for good.
        socket.soTimeout = readTimeoutMs
        // Gemini requires SNI: shared hosting cannot pick the right certificate without it,
        // and TOFU would then pin whichever certificate happened to answer.
        socket.sslParameters =
            socket.sslParameters.apply {
                serverNames = listOf(SNIHostName(host))
            }
        socket.startHandshake()
        return socket
    }

    /** Reads up to the first CRLF. The header is capped, so a server that never sends one ends. */
    private fun InputStream.readHeaderLine(): String {
        val out = StringBuilder()
        var previous = -1
        while (out.length <= MAX_HEADER_BYTES) {
            val b = read()
            if (b == -1) throw GeminiException("connection closed before the status line")
            if (previous == '\r'.code && b == '\n'.code) {
                return out.dropLast(1).toString()
            }
            out.append(b.toChar())
            previous = b
        }
        throw GeminiException("status line never ended")
    }

    private fun InputStream.readBody(): ByteArray {
        val buffer = ByteArray(16 * 1024)
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val n = read(buffer)
            if (n == -1) break
            if (out.size() + n > maxBodyBytes) {
                throw GeminiException("response is larger than ${maxBodyBytes / 1024}KB")
            }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** `text/gemini; charset=utf-8; lang=en` -> the type and the charset. */
    private fun parseMeta(meta: String): Pair<String, String> {
        if (meta.isBlank()) return DEFAULT_MIME to DEFAULT_CHARSET
        val parts = meta.split(';')
        val mime = parts.first().trim().lowercase().ifBlank { DEFAULT_MIME }
        val charset =
            parts
                .drop(1)
                .map { it.trim() }
                .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
                ?.substringAfter('=')
                ?.trim()
                ?.trim('"')
                ?: DEFAULT_CHARSET
        return mime to charset
    }

    companion object {
        const val DEFAULT_PORT = 1965
        private const val MAX_REQUEST_BYTES = 1024
        private const val MAX_HEADER_BYTES = 1024
        private const val DEFAULT_MIME = "text/gemini"
        private const val DEFAULT_CHARSET = "utf-8"

        fun resolve(
            base: String,
            target: String,
        ): String = URI(base).resolve(target).toString()
    }
}
