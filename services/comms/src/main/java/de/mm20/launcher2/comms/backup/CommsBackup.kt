package de.mm20.launcher2.comms.backup

import android.util.Base64
import de.mm20.launcher2.comms.repository.SpamRepository
import de.mm20.launcher2.preferences.CommsGroup
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class CommsBackupPayload(
    val version: Int = 1,
    val comms: CommsGroup,
    val blockedNumbers: List<String> = emptyList(),
)

class CommsBackupManager(
    private val settings: CommsSettings,
    private val spam: SpamRepository,
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun exportEncrypted(password: String): String = withContext(Dispatchers.IO) {
        val payload = CommsBackupPayload(
            comms = settings.snapshot.first(),
            blockedNumbers = spam.getAllBlocked(),
        )
        encrypt(json.encodeToString(CommsBackupPayload.serializer(), payload), password)
    }

    suspend fun importEncrypted(cipherText: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val plain = decrypt(cipherText.trim(), password)
            val payload = json.decodeFromString(CommsBackupPayload.serializer(), plain)
            settings.replaceFromBackup(payload.comms)
            payload.blockedNumbers.forEach { spam.setBlocked(it, true) }
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val ITERATIONS = 120_000

        fun encrypt(plain: String, password: String): String {
            val random = SecureRandom()
            val salt = ByteArray(16).also { random.nextBytes(it) }
            val iv = ByteArray(12).also { random.nextBytes(it) }
            val key = derive(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = salt + iv + encrypted
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        }

        fun decrypt(cipherText: String, password: String): String {
            val combined = Base64.decode(cipherText, Base64.DEFAULT)
            require(combined.size > 28)
            val salt = combined.copyOfRange(0, 16)
            val iv = combined.copyOfRange(16, 28)
            val encrypted = combined.copyOfRange(28, combined.size)
            val key = derive(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            return String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }

        private fun derive(password: String, salt: ByteArray): SecretKeySpec {
            val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        }
    }
}
