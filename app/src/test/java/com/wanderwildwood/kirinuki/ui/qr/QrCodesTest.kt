package com.wanderwildwood.kirinuki.ui.qr

import com.google.zxing.common.BitMatrix
import com.wanderwildwood.kirinuki.net.scannedAddress
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QrCodesTest {
    /**
     * A code drawn the way a camera would see it: each module [scale] pixels square, black 0 and
     * white 255, set in the middle of a larger grey frame as the brightness plane of a picture.
     * [pad] bytes of slack on each row, as a real camera's row stride often has.
     */
    private fun frame(
        matrix: BitMatrix,
        scale: Double = 4.6,
        pad: Int = 0,
    ): Triple<ByteArray, Int, Pair<Int, Int>> {
        val width = 640
        val height = 480
        val stride = width + pad
        val luma = ByteArray(stride * height) { 0xA0.toByte() }
        val side = (matrix.width * scale).toInt()
        val left = (width - side) / 2
        val top = (height - side) / 2
        for (y in 0 until side) {
            for (x in 0 until side) {
                val mx = minOf(matrix.width - 1, (x / scale).toInt())
                val my = minOf(matrix.height - 1, (y / scale).toInt())
                luma[(top + y) * stride + left + x] = if (matrix[mx, my]) 0 else 0xFF.toByte()
            }
        }
        return Triple(luma, stride, width to height)
    }

    @Test
    fun `a code made here reads back as the same address`() {
        val url = "https://example.org/feed.xml"
        val (luma, stride, size) = frame(QrCodes.encode(url))

        assertEquals(url, QrCodes.decode(luma, stride, size.first, size.second))
    }

    @Test
    fun `codes read back at every size a camera might see them`() {
        val urls =
            listOf(
                "https://example.org/feed.xml",
                "https://example.net/rss",
                "http://example.com/atom.xml",
                "https://news.example.org/feeds/all.atom.xml",
                "gemini://example.space/log/",
                "https://example.org/blog/feed/",
            )
        for (url in urls) {
            for (scale in listOf(3.3, 4.0, 4.6, 5.0, 5.5, 6.25, 7.7)) {
                val (luma, stride, size) = frame(QrCodes.encode(url), scale = scale)
                assertEquals(url, QrCodes.decode(luma, stride, size.first, size.second), "$url at $scale")
            }
        }
    }

    @Test
    fun `a long article address survives too`() {
        val url = "https://example.org/2026/11/02/a-long-walk-through-the-valley-in-the-rain?ref=feed&utm_source=nowhere"
        val (luma, stride, size) = frame(QrCodes.encode(url), scale = 4.0, pad = 64)

        assertEquals(url, QrCodes.decode(luma, stride, size.first, size.second))
    }

    @Test
    fun `an address with letters outside ascii survives`() {
        val url = "https://example.org/ändern/feed.xml"
        val (luma, stride, size) = frame(QrCodes.encode(url))

        assertEquals(url, QrCodes.decode(luma, stride, size.first, size.second))
    }

    @Test
    fun `the code has its quiet zone`() {
        val matrix = QrCodes.encode("https://example.org/feed.xml")
        // Four white modules on every side, which a reader needs to find the code.
        for (i in 0 until matrix.width) {
            for (edge in 0 until 4) {
                assertTrue(!matrix[i, edge] && !matrix[edge, i])
            }
        }
    }

    @Test
    fun `a blank frame reads as nothing rather than throwing`() {
        val luma = ByteArray(320 * 240) { 0x80.toByte() }

        assertNull(QrCodes.decode(luma, 320, 320, 240))
    }

    @Test
    fun `a frame with the wrong size reads as nothing rather than throwing`() {
        assertNull(QrCodes.decode(ByteArray(10), 320, 320, 240))
    }

    @Test
    fun `a scanned code goes through the same check as a typed address`() {
        val (luma, stride, size) = frame(QrCodes.encode("javascript:alert(1)"))
        val text = QrCodes.decode(luma, stride, size.first, size.second)

        assertEquals("javascript:alert(1)", text)
        assertNull(scannedAddress(text!!))
    }
}
