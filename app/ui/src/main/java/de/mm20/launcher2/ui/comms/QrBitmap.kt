package de.mm20.launcher2.ui.comms

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@Composable
internal fun ContactQrImage(payload: String, modifier: Modifier = Modifier) {
    val bitmap = remember(payload) { encodeQr(payload, 512) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR",
            modifier = modifier.size(200.dp),
        )
    }
}

internal fun encodeQr(payload: String, size: Int): Bitmap? {
    if (payload.isBlank()) return null
    return try {
        val matrix = QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 1),
        )
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        bmp
    } catch (_: Exception) {
        null
    }
}

internal fun contactVcard(name: String, phones: List<String>, emails: List<String>): String {
    return buildString {
        appendLine("BEGIN:VCARD")
        appendLine("VERSION:3.0")
        appendLine("FN:$name")
        phones.forEach { appendLine("TEL:$it") }
        emails.forEach { appendLine("EMAIL:$it") }
        appendLine("END:VCARD")
    }
}
