package de.mm20.launcher2.ui.camera

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class CameraCodeDecoderTest {
    @Test fun paddedInterleavedPlaneAndNonzeroBufferPositionDecodeQr() {
        val text = "https://example.test/ελληνικά"
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 240, 240,
            mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"))
        val stride = 240 * 2 + 16
        val buffer = ByteBuffer.allocate(stride * 240 + 7)
        buffer.position(7)
        for (y in 0 until 240) for (x in 0 until 240) buffer.put(7 + y * stride + x * 2, if (matrix[x,y]) 0 else 255.toByte())
        val result = CameraCodeDecoder().decode(buffer, 240, 240, stride, 2)
        assertEquals(text, result?.text)
        assertEquals(BarcodeFormat.QR_CODE, result?.format)
        assertEquals(7, buffer.position())
    }

    @Test fun rotatedLinearBarcodeDecodesWithoutCameraRotationMetadata() {
        val matrix = MultiFormatWriter().encode("5901234123457", BarcodeFormat.EAN_13, 300, 120)
        val bytes = ByteArray(300 * 120)
        for (y in 0 until 120) for (x in 0 until 300) bytes[x * 120 + (119-y)] = if(matrix[x,y]) 0 else 255.toByte()
        assertEquals("5901234123457", CameraCodeDecoder().decode(ByteBuffer.wrap(bytes), 120, 300, 120, 1)?.text)
    }

    @Test fun blankOrTruncatedFramesReturnNoResult() {
        val decoder = CameraCodeDecoder()
        assertNull(decoder.decode(ByteBuffer.wrap(ByteArray(100 * 100) { 255.toByte() }),100,100,100,1))
        assertNull(decoder.decode(ByteBuffer.allocate(10),100,100,100,1))
    }
}
