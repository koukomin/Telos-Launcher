package de.mm20.launcher2.comms.sms

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optional Telos-to-Telos end-to-end SMS. Handshake and payloads ride on ordinary SMS.
 * Compatible only with Telos (not Signal or SMSecure wire format). Text only.
 */
object EncryptedSms {
    const val PREFIX_HS = "TELOS1:HS:"
    const val PREFIX_MSG = "TELOS1:M:"

    fun isHandshake(body: String) = body.startsWith(PREFIX_HS)
    fun isPayload(body: String) = body.startsWith(PREFIX_MSG)
    fun isTelosE2e(body: String) = isHandshake(body) || isPayload(body)

    fun hasSession(context: Context, address: String): Boolean =
        peerPublic(context, digits(address)) != null

    fun safetyNumber(context: Context, address: String): String? {
        val peer = peerPublic(context, digits(address)) ?: return null
        val mine = identityPublic(context)
        val a = mine.encoded
        val b = peer.encoded
        val first = if (a.concatToString() < b.concatToString()) a + b else b + a
        val hash = MessageDigest.getInstance("SHA-256").digest(first)
        return hash.joinToString(" ") { "%02X".format(it) }.chunked(15).take(4).joinToString("\n")
    }

    fun handshakeSms(context: Context): String =
        PREFIX_HS + b64(identityPublic(context).encoded)

    fun acceptHandshake(context: Context, address: String, body: String): String? {
        if (!isHandshake(body)) return null
        val raw = runCatching { Base64.decode(body.removePrefix(PREFIX_HS), Base64.NO_WRAP) }.getOrNull() ?: return null
        val peer = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(raw))
        val key = digits(address)
        val existed = peerPublic(context, key)
        savePeer(context, key, peer)
        if (existed != null && existed.encoded.contentEquals(peer.encoded)) return null
        return handshakeSms(context)
    }

    fun encrypt(context: Context, address: String, plain: String): String? {
        val secret = sharedSecret(context, digits(address)) ?: return null
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(128, iv))
        val out = iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX_MSG + b64(out)
    }

    fun decrypt(context: Context, address: String, body: String): String? {
        if (!isPayload(body)) return null
        val secret = sharedSecret(context, digits(address)) ?: return null
        val raw = runCatching { Base64.decode(body.removePrefix(PREFIX_MSG), Base64.NO_WRAP) }.getOrNull() ?: return null
        if (raw.size < 13) return null
        val iv = raw.copyOfRange(0, 12)
        val ct = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv))
        return runCatching { String(cipher.doFinal(ct), Charsets.UTF_8) }.getOrNull()
    }

    private fun sharedSecret(context: Context, key: String): SecretKey? {
        val peer = peerPublic(context, key) ?: return null
        val mine = identityPrivate(context)
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(mine)
        ka.doPhase(peer, true)
        val digest = MessageDigest.getInstance("SHA-256").digest(ka.generateSecret())
        return SecretKeySpec(digest, "AES")
    }

    private fun identityPublic(context: Context): PublicKey = identity(context).first
    private fun identityPrivate(context: Context): PrivateKey = identity(context).second

    private fun identity(context: Context): Pair<PublicKey, PrivateKey> {
        val prefs = prefs(context)
        val pubB = prefs.getString("id_pub", null)
        val privB = prefs.getString("id_priv", null)
        val kf = KeyFactory.getInstance("EC")
        if (pubB != null && privB != null) {
            val pub = kf.generatePublic(X509EncodedKeySpec(unwrap(context, Base64.decode(pubB, Base64.NO_WRAP))))
            val priv = kf.generatePrivate(PKCS8EncodedKeySpec(unwrap(context, Base64.decode(privB, Base64.NO_WRAP))))
            return pub to priv
        }
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val pair = kpg.generateKeyPair()
        prefs.edit()
            .putString("id_pub", b64(wrap(context, pair.public.encoded)))
            .putString("id_priv", b64(wrap(context, pair.private.encoded)))
            .apply()
        return pair.public to pair.private
    }

    private fun peerPublic(context: Context, key: String): PublicKey? {
        val raw = prefs(context).getString("peer_$key", null) ?: return null
        return runCatching {
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(raw, Base64.NO_WRAP)))
        }.getOrNull()
    }

    private fun savePeer(context: Context, key: String, peer: PublicKey) {
        prefs(context).edit().putString("peer_$key", b64(peer.encoded)).apply()
    }

    private fun wrap(context: Context, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey(context), GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(data)
    }

    private fun unwrap(context: Context, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrapKey(context), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        return cipher.doFinal(data.copyOfRange(12, data.size))
    }

    private fun wrapKey(context: Context): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("telos_sms_e2e_wrap", null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder("telos_sms_e2e_wrap", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("telos_sms_e2e", Context.MODE_PRIVATE)
    private fun digits(address: String) = address.filter { it.isDigit() }
    private fun b64(data: ByteArray) = Base64.encodeToString(data, Base64.NO_WRAP)
    private fun ByteArray.concatToString() = joinToString("") { "%02x".format(it) }
}
