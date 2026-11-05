/* SPDX-License-Identifier: GPL-3.0-or-later
 * Encrypted software-key storage follows Shizuku-Next's AdbKey design (Apache-2.0).
 */
package de.mm20.launcher2.appmanagement.shizuku

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

internal object TelosAdbIdentity {
    @Synchronized
    fun load(context: Context): Pair<PrivateKey, X509Certificate> {
        val app = context.applicationContext
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "telos_adb_envelope_v1"
        val envelope = store.getKey(alias, null) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
        val file = AtomicFile(File(app.noBackupFilesDir, "wireless-adb-identity-v1"))
        val aad = app.packageName.toByteArray(Charsets.UTF_8)
        if (file.baseFile.exists()) {
            val encrypted = file.readFully()
            check(encrypted.size in 29..32768) { "Invalid encrypted ADB identity" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, envelope, GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
            cipher.updateAAD(aad)
            val plaintext = cipher.doFinal(encrypted, 12, encrypted.size - 12)
            try {
                DataInputStream(ByteArrayInputStream(plaintext)).use { input ->
                    val length = input.readInt()
                    check(length in 1..8192 && length < input.available()) { "Invalid ADB key length" }
                    val encoded = ByteArray(length).also(input::readFully)
                    val key = try {
                        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(encoded))
                    } finally { encoded.fill(0) }
                    val cert = CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate
                    return key to cert
                }
            } finally { plaintext.fill(0) }
        }
        val pair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
        }.generateKeyPair()
        val subject = X500Name("CN=00")
        val certificate = X509v3CertificateBuilder(subject, BigInteger.ONE, Date(0), Date(4102444800000L),
            subject, SubjectPublicKeyInfo.getInstance(pair.public.encoded))
            .build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private))
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certificate.encoded)) as X509Certificate
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            val encoded = pair.private.encoded
            try { output.writeInt(encoded.size); output.write(encoded); output.write(cert.encoded) }
            finally { encoded.fill(0) }
        }
        val plaintext = bytes.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, envelope)
        cipher.updateAAD(aad)
        val encrypted = try { cipher.iv + cipher.doFinal(plaintext) } finally { plaintext.fill(0) }
        val output = file.startWrite()
        try { output.write(encrypted); file.finishWrite(output) }
        catch (error: Throwable) { file.failWrite(output); throw error }
        return pair.private to cert
    }
}
