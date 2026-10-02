// === TELOS_PENDING_REVIEW_START: telephony_encryption_suite ===
package de.mm20.launcher2.comms.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class RecordingShareManager(private val context: Context) {

    companion object {
        private const val TAG = "RecordingShareManager"
        private const val KEY_ALIAS = "TelosCallRecorderKey"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val IV_LENGTH = 12
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            null
        }
    }

    suspend fun prepareForSharing(encryptedFile: File): Uri? = withContext(Dispatchers.IO) {
        if (!encryptedFile.exists()) return@withContext null

        val secretKey = getSecretKey() ?: return@withContext null

        try {
            val cacheDir = File(context.cacheDir, "shared_recordings").apply { mkdirs() }
            val decryptedFile = File(cacheDir, "${encryptedFile.nameWithoutExtension}.wav")

            FileInputStream(encryptedFile).use { fis ->
                val iv = ByteArray(IV_LENGTH)
                val bytesRead = fis.read(iv)
                if (bytesRead != IV_LENGTH) throw IllegalStateException("Invalid IV length")

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                CipherInputStream(fis, cipher).use { cis ->
                    FileOutputStream(decryptedFile).use { fos ->
                        // Prepend WAV header (44 bytes)
                        // We will write a placeholder header and update it after reading the payload
                        val header = ByteArray(44)
                        fos.write(header)

                        var payloadSize = 0
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (cis.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                            payloadSize += read
                        }

                        // Update WAV header
                        writeWavHeader(fos.channel, payloadSize)
                    }
                }
            }

            // Expose the temporary file via FileProvider
            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, decryptedFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare recording for sharing", e)
            null
        }
    }

    private fun writeWavHeader(channel: FileChannel, payloadSize: Int) {
        val totalDataLen = payloadSize + 36
        val byteRate = 44100 * 1 * 16 / 8 // sampleRate * channels * bitsPerSample / 8

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // AudioFormat chunk size
        header.putShort(1.toShort()) // AudioFormat (1 = PCM)
        header.putShort(1.toShort()) // NumChannels (1 = Mono)
        header.putInt(44100) // SampleRate
        header.putInt(byteRate) // ByteRate
        header.putShort((1 * 16 / 8).toShort()) // BlockAlign
        header.putShort(16.toShort()) // BitsPerSample
        header.put("data".toByteArray())
        header.putInt(payloadSize)

        header.position(0)
        channel.position(0)
        channel.write(header)
    }

    suspend fun cleanupSharedRecordings() = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "shared_recordings")
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }
}
// === TELOS_PENDING_REVIEW_END: telephony_encryption_suite ===
