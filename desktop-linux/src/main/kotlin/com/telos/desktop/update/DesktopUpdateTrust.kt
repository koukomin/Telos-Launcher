package com.telos.desktop.update

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Telos Desktop update trust roots (VOS-5 foundations).
 *
 * Offline Ed25519 public key shipped in the jar. The matching private key is
 * held out-of-band for release signing — never embedded here.
 */
object DesktopUpdateTrust {
    const val ALGORITHM = "Ed25519"

    /**
     * SubjectPublicKeyInfo (X.509) for the Telos desktop update Ed25519 key.
     * Raw public key: 14655afeaea2a8c70ab53549d6b3760e333451b414363305485146b094dcf719
     */
    private const val EMBEDDED_SPKI_B64 =
        "MCowBQYDK2VwAyEAFGVa/q6iqMcKtTVJ1rN2DjM0UbQUNjMFSFFGsJTc9xk="

    fun embeddedPublicKey(): PublicKey {
        val der = Base64.getDecoder().decode(EMBEDDED_SPKI_B64)
        return KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(der))
    }

    fun publicKeyFromSpkiBase64(spkiB64: String): PublicKey {
        val der = Base64.getDecoder().decode(spkiB64.trim())
        return KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(der))
    }

    fun verify(payload: ByteArray, signature: ByteArray, publicKey: PublicKey = embeddedPublicKey()): Boolean {
        if (signature.isEmpty() || payload.isEmpty()) return false
        val verifier = Signature.getInstance(ALGORITHM)
        verifier.initVerify(publicKey)
        verifier.update(payload)
        return runCatching { verifier.verify(signature) }.getOrDefault(false)
    }
}
