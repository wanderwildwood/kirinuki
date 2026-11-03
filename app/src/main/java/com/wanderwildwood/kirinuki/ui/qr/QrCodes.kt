package com.wanderwildwood.kirinuki.ui.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Making and reading QR codes, with ZXing and nothing else: no Google services, no network.
 * Plain functions over bytes and bits, so both directions are tested on the JVM.
 */
object QrCodes {
    /**
     * The modules of a QR code for [text], one bit each, with the four-module quiet zone the
     * standard asks for. Quartile error correction, because the code is read off a screen by
     * another phone's camera, which loses a little at the edges and to glare.
     *
     * The character set is named only when the address needs it. Naming it puts an ECI marker
     * in every code, and plain ASCII codes without one were read back more reliably in testing
     * (QrCodesTest): an address is nearly always ASCII, percent-encoded where it is not.
     */
    fun encode(text: String): BitMatrix {
        val hints =
            mutableMapOf<EncodeHintType, Any>(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q,
                EncodeHintType.MARGIN to 4,
            )
        if (text.any { it.code > 0x7F }) hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        return QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    }

    private val readHints =
        mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8",
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        )

    /**
     * The text of a QR code in one camera frame, or null when there is none to read. [luma] is
     * the frame's brightness plane, [rowStride] bytes to a row. Never throws: a frame that is
     * blurred, half a code, or not a code at all is the usual case, not an error.
     */
    fun decode(
        luma: ByteArray,
        rowStride: Int,
        width: Int,
        height: Int,
    ): String? =
        try {
            val source = PlanarYUVLuminanceSource(luma, rowStride, height, 0, 0, width, height, false)
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), readHints).text
        } catch (e: ReaderException) {
            null
        } catch (e: RuntimeException) {
            null
        }
}
