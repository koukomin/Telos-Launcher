package de.mm20.launcher2.keepasstl

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.util.Random

@RunWith(RobolectricTestRunner::class)
class SteganographyEngineTest {

    @Test
    fun testEmbedAndExtract() {
        val originalFile = File.createTempFile("original", ".png")
        val outputFile = File.createTempFile("steg", ".png")

        // Create a dummy image
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        FileOutputStream(originalFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val random = Random()
        val payload = ByteArray(100 * 1024) // 100KB payload
        random.nextBytes(payload)

        val embedResult = SteganographyEngine.embedInImage(originalFile, payload, outputFile)
        assertTrue("Embedding should succeed", embedResult)

        val extracted = SteganographyEngine.extractFromImage(outputFile)
        assertNotNull("Extraction should not be null", extracted)
        assertArrayEquals("Payload should remain identical byte-for-byte", payload, extracted)

        originalFile.delete()
        outputFile.delete()
    }
}
