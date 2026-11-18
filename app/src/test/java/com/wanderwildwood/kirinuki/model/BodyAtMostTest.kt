package com.wanderwildwood.kirinuki.model

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BodyAtMostTest {
    @Test
    fun aBodyWithinTheCapIsReadWhole() {
        val body = "<rss><channel><title>Pond Notes</title></channel></rss>".toResponseBody("application/rss+xml".toMediaType())
        assertEquals("<rss><channel><title>Pond Notes</title></channel></rss>", body.bytesAtMost(maxBytes = 1_000).decodeToString())
    }

    @Test
    fun aBodyPastTheCapIsRefused() {
        val body = "x".repeat(5_000).toResponseBody("text/html".toMediaType())
        assertFailsWith<IOException> { body.bytesAtMost(maxBytes = 1_000) }
    }

    @Test
    fun textIsReadInTheCharsetTheServerNames() {
        val bytes = "Café by the river".toByteArray(Charsets.ISO_8859_1)
        val body = bytes.toResponseBody("text/html; charset=ISO-8859-1".toMediaType())
        assertEquals("Café by the river", body.stringAtMost(maxBytes = 1_000))
    }

    @Test
    fun textPastTheCapIsRefused() {
        val body = "y".repeat(5_000).toResponseBody("text/plain".toMediaType())
        assertFailsWith<IOException> { body.stringAtMost(maxBytes = 1_000) }
    }
}
