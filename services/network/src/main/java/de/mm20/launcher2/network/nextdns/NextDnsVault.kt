package de.mm20.launcher2.network.nextdns

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.*

internal class NextDnsCredentials(val profileId: String, val key: String) {
    override fun toString() = "NextDnsCredentials(redacted)"
}

/** Separate from preferences/backups. Called only from the repository's IO dispatcher. */
internal class NextDnsVault(context: Context) {
    private val app = context.applicationContext
    private val file by lazy { AtomicFile(File(app.noBackupFilesDir, "nextdns-api-vault")) }
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): NextDnsCredentials? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val bytes = file.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (output.size() + n > 16_384) throw NextDnsException(NextDnsErrorKind.API)
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        }
        if (bytes.size < 29) throw NextDnsException(NextDnsErrorKind.API)
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            doFinal(bytes.copyOfRange(12, bytes.size))
        }
        val json = Json.parseToJsonElement(String(plain, Charsets.UTF_8)).jsonObject
        return NextDnsCredentials(NextDnsValidation.profileId(json.getValue("profile").jsonPrimitive.content),
            NextDnsValidation.apiKey(json.getValue("key").jsonPrimitive.content))
    }
    fun write(credentials: NextDnsCredentials) {
        val plain = buildJsonObject { put("profile", credentials.profileId); put("key", credentials.key) }.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val output = file.startWrite()
        try {
            output.write(cipher.iv)
            output.write(cipher.doFinal(plain))
            file.finishWrite(output)
        } catch (failure: Exception) { file.failWrite(output); throw failure }
    }
    fun clear() {
        file.delete()
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists())
            throw NextDnsException(NextDnsErrorKind.API)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(ALIAS) }
    }
    private companion object { const val ALIAS = "telos-nextdns-api-v1" }
}
