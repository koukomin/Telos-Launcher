package de.mm20.launcher2.ui.comms

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import de.mm20.launcher2.ui.R

@Composable
internal fun ContactQrImage(payload: String, modifier: Modifier = Modifier) {
    val bitmap = remember(payload) { encodeQr(payload, 512) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.hc_qr_code),
            modifier = modifier.size(200.dp),
        )
    } else {
        Text(
            text = stringResource(R.string.au_phonea_qr_too_large),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    } catch (_: Exception) {
        null
    }
}

/** Escapes the characters that have a meaning inside a vCard text value */
private fun vcardText(value: String): String = value
    .replace("\\", "\\\\")
    .replace(";", "\;")
    .replace(",", "\\,")
    .replace("\r\n", "\\n")
    .replace("\n", "\\n")

internal fun contactVcard(name: String, phones: List<String>, emails: List<String>): String {
    return buildString {
        appendLine("BEGIN:VCARD")
        appendLine("VERSION:3.0")
        appendLine("FN:${vcardText(name)}")
        phones.forEach { appendLine("TEL:${vcardText(it)}") }
        emails.forEach { appendLine("EMAIL:${vcardText(it)}") }
        appendLine("END:VCARD")
    }
}
