package de.mm20.launcher2.ui.camera

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

internal data class CameraCode(val text: String, val format: BarcodeFormat)

/** One analyzer-thread instance. Samples only luminance, including padded/interleaved camera planes. */
internal class CameraCodeDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE, BarcodeFormat.AZTEC, BarcodeFormat.DATA_MATRIX, BarcodeFormat.PDF_417,
            BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.CODABAR,
            BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.ITF,
        )))
    }

    fun decode(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): CameraCode? {
        if (width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0) return null
        val step = ((maxOf(width, height) + 959) / 960).coerceAtLeast(1)
        val w = (width + step - 1) / step
        val h = (height + step - 1) / step
        val base = buffer.position()
        val last = base.toLong() + (height - 1L) * rowStride + (width - 1L) * pixelStride
        if (last >= buffer.limit()) return null
        val data = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) data[y * w + x] = buffer.get(base + y * step * rowStride + x * step * pixelStride)
        var source: com.google.zxing.LuminanceSource = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
        repeat(2) {
            for (candidate in listOf(source, source.invert())) {
                try {
                    val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(candidate)))
                    return result.text?.takeIf { it.isNotBlank() && it.length <= 32_768 }?.let { CameraCode(it, result.barcodeFormat) }
                } catch (_: NotFoundException) { /* No readable code in this orientation. */ }
                finally { reader.reset() }
            }
            if (it == 0) {
                val rotated = ByteArray(data.size)
                for (y in 0 until h) for (x in 0 until w) rotated[(w - 1 - x) * h + y] = data[y * w + x]
                source = PlanarYUVLuminanceSource(rotated, h, w, 0, 0, h, w, false)
            }
        }
        return null
    }
}
