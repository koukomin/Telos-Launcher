package de.mm20.launcher2.comms.ermis

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The account cache and credentials never leave app-private storage in plaintext. */
internal class ErmisVault(context: Context) {
    private val file = java.io.File(context.noBackupFilesDir, "ermis-vault")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("telos-ermis", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("telos-ermis", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): String? {
        if (!file.exists()) return null
        require(file.length() in 29..40_000_000) { "Invalid mail cache" }
        val data = file.readBytes()
        require(data.size in 29..40_000_000) { "Invalid mail cache" }
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
            String(doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
        }
    }
    fun write(text: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val atomic = android.util.AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(cipher.iv); output.write(cipher.doFinal(text.toByteArray(Charsets.UTF_8))); atomic.finishWrite(output) }
        catch (e: Exception) { atomic.failWrite(output); throw e }
    }
}
