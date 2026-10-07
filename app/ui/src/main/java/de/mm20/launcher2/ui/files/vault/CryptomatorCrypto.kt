package de.mm20.launcher2.ui.files.vault

import android.util.Base64
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.engines.AESWrapEngine
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.macs.CMac
import org.bouncycastle.crypto.params.KeyParameter
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-SIV as in RFC 5297, which Cryptomator uses for file names. */
internal object Siv {
    private fun cmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = CMac(AESEngine())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        return ByteArray(16).also { mac.doFinal(it, 0) }
    }

    private fun dbl(block: ByteArray): ByteArray {
        val out = ByteArray(16)
        var carry = 0
        for (i in 15 downTo 0) {
            val b = block[i].toInt() and 0xFF
            out[i] = ((b shl 1) or carry).toByte()
            carry = b ushr 7
        }
        if (carry != 0) out[15] = (out[15].toInt() xor 0x87).toByte()
        return out
    }

    private fun xor(a: ByteArray, b: ByteArray) = ByteArray(a.size) { (a[it].toInt() xor b[it].toInt()).toByte() }

    private fun s2v(macKey: ByteArray, ad: List<ByteArray>, plaintext: ByteArray): ByteArray {
        var d = cmac(macKey, ByteArray(16))
        for (a in ad) d = xor(dbl(d), cmac(macKey, a))
        val t = if (plaintext.size >= 16) {
            val copy = plaintext.copyOf()
            val offset = copy.size - 16
            for (i in 0 until 16) copy[offset + i] = (copy[offset + i].toInt() xor d[i].toInt()).toByte()
            copy
        } else {
            val padded = ByteArray(16)
            System.arraycopy(plaintext, 0, padded, 0, plaintext.size)
            padded[plaintext.size] = 0x80.toByte()
            xor(dbl(d), padded)
        }
        return cmac(macKey, t)
    }

    private fun ctr(ctrKey: ByteArray, siv: ByteArray, data: ByteArray): ByteArray {
        val iv = siv.copyOf()
        iv[8] = (iv[8].toInt() and 0x7F).toByte()
        iv[12] = (iv[12].toInt() and 0x7F).toByte()
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ctrKey, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    fun encrypt(ctrKey: ByteArray, macKey: ByteArray, plaintext: ByteArray, vararg ad: ByteArray): ByteArray {
        val v = s2v(macKey, ad.toList(), plaintext)
        return v + ctr(ctrKey, v, plaintext)
    }

    /** Returns null when the data was changed or the key is wrong. */
    fun decrypt(ctrKey: ByteArray, macKey: ByteArray, input: ByteArray, vararg ad: ByteArray): ByteArray? {
        if (input.size < 16) return null
        val v = input.copyOfRange(0, 16)
        val plaintext = ctr(ctrKey, v, input.copyOfRange(16, input.size))
        return if (MessageDigest.isEqual(s2v(macKey, ad.toList(), plaintext), v)) plaintext else null
    }
}

internal object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    fun encode(data: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) { out.append(ALPHABET[(buffer shr (bits - 5)) and 31]); bits -= 5 }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }
}

class WrongPasswordException : IOException("Wrong password")

/**
 * What a Cryptomator vault (format 7 and 8) needs to read its names and files. Cryptomator is
 * open source (cryptomator.org); this reads vaults, it does not change them.
 */
class VaultCrypto(
    private val encKey: ByteArray,
    private val macKey: ByteArray,
    private val gcm: Boolean,
    val shorteningThreshold: Int,
) {
    private val utf8 = Charsets.UTF_8

    /** Folder of the vault where the entries of the directory with this ID are stored: d/AB/CDEF... */
    fun directoryPath(dirId: String): String {
        val hash = Base32.encode(MessageDigest.getInstance("SHA-1").digest(Siv.encrypt(encKey, macKey, dirId.toByteArray(utf8))))
        return "d/" + hash.substring(0, 2) + "/" + hash.substring(2)
    }

    /** The name as stored for [clearName] in the folder of [dirId] (without ".c9r") */
    fun encryptName(clearName: String, dirId: String): String =
        Base64.encodeToString(Siv.encrypt(encKey, macKey, clearName.toByteArray(utf8), dirId.toByteArray(utf8)), Base64.URL_SAFE or Base64.NO_WRAP)

    fun decryptName(stored: String, dirId: String): String? = runCatching {
        Siv.decrypt(encKey, macKey, Base64.decode(stored, Base64.URL_SAFE), dirId.toByteArray(utf8))?.toString(utf8)
    }.getOrNull()

    private val headerSize get() = if (gcm) 68 else 88
    private val chunkOverhead get() = if (gcm) 28 else 48
    private val chunkCipherSize get() = 32768 + chunkOverhead

    fun cleartextSize(cipherSize: Long): Long {
        val body = cipherSize - headerSize
        if (body <= 0) return 0
        val chunks = (body + chunkCipherSize - 1) / chunkCipherSize
        return (body - chunks * chunkOverhead).coerceAtLeast(0)
    }

    /** Decrypts a file while it is read. Wrong or changed data ends in an exception, never in garbage. */
    fun decrypting(input: InputStream): InputStream {
        val header = ByteArray(headerSize)
        readFully(input, header, headerSize)
        val nonce: ByteArray
        val contentKey: ByteArray
        if (gcm) {
            nonce = header.copyOfRange(0, 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(128, nonce))
            val payload = cipher.doFinal(header, 12, 56)
            contentKey = payload.copyOfRange(8, 40)
        } else {
            nonce = header.copyOfRange(0, 16)
            val payload = header.copyOfRange(16, 56)
            val expected = hmac(macKey, nonce, payload)
            if (!MessageDigest.isEqual(expected, header.copyOfRange(56, 88))) throw IOException("The file header is damaged")
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(nonce))
            contentKey = cipher.doFinal(payload).copyOfRange(8, 40)
        }
        return object : InputStream() {
            private var chunk = ByteArray(0)
            private var position = 0
            private var number = 0L
            private var finished = false

            private fun next(): Boolean {
                if (finished) return false
                val raw = ByteArray(chunkCipherSize)
                val n = readUpTo(input, raw)
                if (n <= chunkOverhead) { finished = true; return false }
                if (n < chunkCipherSize) finished = true
                chunk = decryptChunk(raw, n)
                position = 0
                number++
                return chunk.isNotEmpty()
            }

            private fun decryptChunk(raw: ByteArray, n: Int): ByteArray {
                val index = ByteBuffer.allocate(8).putLong(number).array()
                if (gcm) {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(contentKey, "AES"), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
                    cipher.updateAAD(index)
                    cipher.updateAAD(nonce)
                    return cipher.doFinal(raw, 12, n - 12)
                }
                val chunkNonce = raw.copyOfRange(0, 16)
                val cipherText = raw.copyOfRange(16, n - 32)
                val mac = hmac(macKey, nonce, index, chunkNonce, cipherText)
                if (!MessageDigest.isEqual(mac, raw.copyOfRange(n - 32, n))) throw IOException("The file is damaged")
                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(contentKey, "AES"), IvParameterSpec(chunkNonce))
                return cipher.doFinal(cipherText)
            }

            override fun read(): Int {
                if (position >= chunk.size && !next()) return -1
                return chunk[position++].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (position >= chunk.size && !next()) return -1
                val n = minOf(len, chunk.size - position)
                System.arraycopy(chunk, position, b, off, n)
                position += n
                return n
            }

            override fun close() { input.close() }
        }
    }

    private fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        parts.forEach { mac.update(it) }
        return mac.doFinal()
    }

    private fun readFully(input: InputStream, buffer: ByteArray, size: Int) {
        var read = 0
        while (read < size) {
            val n = input.read(buffer, read, size - read)
            if (n < 0) throw IOException("The file is too short")
            read += n
        }
    }

    private fun readUpTo(input: InputStream, buffer: ByteArray): Int {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    companion object {
        /** Opens a vault from the text of its masterkey file and its vault.cryptomator (a signed token, may be missing in old vaults). */
        fun unlock(masterkeyJson: String, vaultConfig: String?, password: String): VaultCrypto {
            val key = JSONObject(masterkeyJson)
            val salt = Base64.decode(key.getString("scryptSalt"), Base64.DEFAULT)
            val cost = key.getInt("scryptCostParam")
            val block = key.getInt("scryptBlockSize")
            val kek = SCrypt.generate(Normalizer.normalize(password, Normalizer.Form.NFC).toByteArray(Charsets.UTF_8), salt, cost, block, 1, 32)
            fun unwrap(field: String): ByteArray {
                val wrapped = Base64.decode(key.getString(field), Base64.DEFAULT)
                return try {
                    val engine = AESWrapEngine()
                    engine.init(false, KeyParameter(kek))
                    engine.unwrap(wrapped, 0, wrapped.size)
                } catch (e: Exception) {
                    throw WrongPasswordException()
                }
            }
            val enc = unwrap("primaryMasterKey")
            val mac = unwrap("hmacMasterKey")
            var gcm = false
            var threshold = 220
            if (!vaultConfig.isNullOrBlank()) {
                runCatching {
                    val payload = String(Base64.decode(vaultConfig.trim().split('.')[1], Base64.URL_SAFE), Charsets.UTF_8)
                    val json = JSONObject(payload)
                    gcm = json.optString("cipherCombo") == "SIV_GCM"
                    threshold = json.optInt("shorteningThreshold", 220)
                    val format = json.optInt("format", 8)
                    if (format > 8) throw IOException("This vault format ($format) is newer than Telos knows")
                }.onFailure { if (it is IOException) throw it }
            }
            return VaultCrypto(enc, mac, gcm, threshold)
        }
    }
}
