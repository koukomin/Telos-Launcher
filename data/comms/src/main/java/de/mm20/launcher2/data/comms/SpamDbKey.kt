package de.mm20.launcher2.data.comms

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.random.Random

internal object SpamDbKey {
    private const val ALIAS = "TelosSpamDbWrap"
    private const val FILE = "spam_db_key.bin"

    fun get(context: Context): ByteArray {
        val file = File(context.noBackupFilesDir, FILE)
        val wrap = wrapKey()
        if (file.exists()) {
            val packed = file.readBytes()
            val iv = packed.copyOfRange(0, 12)
            val ct = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrap, GCMParameterSpec(128, iv))
            return cipher.doFinal(ct)
        }
        val raw = Random.Default.nextBytes(32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrap)
        val ct = cipher.doFinal(raw)
        val iv = cipher.iv
        file.writeBytes(iv + ct)
        return raw
    }

    private fun wrapKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }
}
