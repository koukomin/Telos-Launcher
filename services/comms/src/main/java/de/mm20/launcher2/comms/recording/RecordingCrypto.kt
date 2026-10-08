package de.mm20.launcher2.comms.recording

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Call recordings are stored encrypted with a key that stays in the Android Keystore. A file starts
 * with [MAGIC] and the IV; a plain recording from an older version is recognised by the missing
 * header and encrypted the next time the list is opened.
 */
object RecordingCrypto {
    private const val ALIAS = "telos_recordings"
    private val MAGIC = "TLSREC1".toByteArray(Charsets.US_ASCII)
    private const val IV_SIZE = 12
    private const val SHARED_DIR = "shared_recordings"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun isEncrypted(file: File): Boolean = runCatching {
        FileInputStream(file).use { input ->
            val head = ByteArray(MAGIC.size)
            input.read(head) == MAGIC.size && head.contentEquals(MAGIC)
        }
    }.getOrDefault(false)

    /** Replaces [file] with its encrypted version. Returns false when that did not work. */
    @Synchronized
    fun encryptInPlace(file: File): Boolean = runCatching {
        if (!file.exists() || isEncrypted(file)) return true
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(MAGIC)
            out.write(cipher.iv)
            CipherOutputStream(out, cipher).use { enc -> file.inputStream().use { it.copyTo(enc) } }
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            return false
        }
        true
    }.getOrDefault(false)

    /**
     * A readable copy in the cache for a player or a share target, to be removed again with
     * [clearSharedCopies]. A recording that is not encrypted is returned as it is.
     */
    fun readableCopy(context: Context, file: File): File? = runCatching {
        if (!isEncrypted(file)) return file
        val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        val out = File(dir, file.name)
        DataInputStream(FileInputStream(file)).use { input ->
            input.skipBytes(MAGIC.size)
            val iv = ByteArray(IV_SIZE).also { input.readFully(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            CipherInputStream(input, cipher).use { dec -> out.outputStream().use { dec.copyTo(it) } }
        }
        out
    }.getOrNull()

    /** Removes the readable copies, except those made in the last [keepMinutes] minutes (a player may still read them). */
    fun clearSharedCopies(context: Context, keepMinutes: Long = 10) {
        val limit = System.currentTimeMillis() - keepMinutes * 60_000
        File(context.cacheDir, SHARED_DIR).listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
    }
}
