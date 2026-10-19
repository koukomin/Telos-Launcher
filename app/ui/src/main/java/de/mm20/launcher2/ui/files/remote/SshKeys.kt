package de.mm20.launcher2.ui.files.remote

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec

/** SSH key pairs made on the phone: the private key stays in Telos, the public line goes into the server's authorized_keys */
internal object SshKeys {
    private const val BEGIN = "-----BEGIN PRIVATE KEY-----"
    private const val END = "-----END PRIVATE KEY-----"

    /** A new RSA key as unencrypted PKCS#8 PEM, which sshj reads */
    fun generatePrivatePem(bits: Int = 3072): String {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()
        val body = Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP).chunked(64).joinToString("\n")
        return "$BEGIN\n$body\n$END\n"
    }

    /** The `ssh-rsa AAAA... comment` line of a PKCS#8 RSA key made by [generatePrivatePem], or null for other keys */
    fun publicLine(privatePem: String, comment: String): String? = runCatching {
        if (!privatePem.trimStart().startsWith(BEGIN)) return null
        val der = Base64.decode(privatePem.substringAfter(BEGIN).substringBefore(END).filterNot(Char::isWhitespace), Base64.DEFAULT)
        val factory = KeyFactory.getInstance("RSA")
        val private = factory.generatePrivate(PKCS8EncodedKeySpec(der)) as? RSAPrivateCrtKey ?: return null
        val public = factory.generatePublic(RSAPublicKeySpec(private.modulus, private.publicExponent)) as RSAPublicKey
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            fun field(data: ByteArray) { out.writeInt(data.size); out.write(data) }
            field("ssh-rsa".toByteArray())
            field(public.publicExponent.toByteArray())
            field(public.modulus.toByteArray())
        }
        "ssh-rsa ${Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)} $comment".trim()
    }.getOrNull()
}
