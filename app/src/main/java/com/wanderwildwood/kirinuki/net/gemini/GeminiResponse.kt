package com.wanderwildwood.kirinuki.net.gemini

/**
 * A Gemini reply. The status line is two digits, a space and a meaning that depends on
 * the first digit -- a mime type for success, a URL for a redirect, a message otherwise.
 */
sealed interface GeminiResponse {
    val url: String

    data class Body(
        override val url: String,
        val mimeType: String,
        val charset: String,
        val bytes: ByteArray,
    ) : GeminiResponse {
        val isGemtext: Boolean get() = mimeType == "text/gemini"

        fun text(): String = String(bytes, charset(charset))

        // ByteArray in a data class: equals/hashCode have to be written out.
        override fun equals(other: Any?): Boolean =
            this === other ||
                (
                    other is Body && url == other.url && mimeType == other.mimeType &&
                        charset == other.charset && bytes.contentEquals(other.bytes)
                )

        override fun hashCode(): Int =
            (((url.hashCode() * 31) + mimeType.hashCode()) * 31 + charset.hashCode()) * 31 +
                bytes.contentHashCode()
    }

    /** 1x -- the capsule is asking a question; the answer goes back as the query string. */
    data class Input(
        override val url: String,
        val prompt: String,
        val sensitive: Boolean,
    ) : GeminiResponse

    /** 6x -- the capsule wants a client certificate, which this app does not have. */
    data class CertificateRequired(
        override val url: String,
        val message: String,
    ) : GeminiResponse

    /**
     * 4x and 5x, and anything that did not parse. [tooManyRedirects] marks the one failure
     * this client makes up itself rather than hears, so the screen can word it; [message]
     * is then empty, because only the capsule's own words go there.
     */
    data class Failure(
        override val url: String,
        val status: Int,
        val message: String,
        val tooManyRedirects: Boolean = false,
    ) : GeminiResponse
}

class GeminiException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
