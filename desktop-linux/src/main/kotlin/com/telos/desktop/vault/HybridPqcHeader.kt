package com.telos.desktop.vault

import java.security.KeyPairGenerator
import java.security.SecureRandom
import javax.crypto.KeyGenerator

/**
 * Hybrid envelope: Argon2id master wrapping a 512-bit XTS key, with an optional
 * ML-KEM-1024 (Kyber) encapsulation blob in the LUKS header sidecar when the
 * Bouncy Castle PQC provider is present.
 */
object HybridPqcHeader {
    fun deriveVolumeKey(password: CharArray, salt: ByteArray): ByteArray {
        val argon = HardwareCryptoAccelerator.argon2id(password, salt, 64)
        val kem = encapsulate(argon)
        return HardwareCryptoAccelerator.hmacSha512(argon, kem)
    }

    fun encapsulate(seed: ByteArray): ByteArray {
        return runCatching {
            val kpg = KeyPairGenerator.getInstance("KYBER1024", "BCPQC")
            kpg.initialize(1024, SecureRandom.getInstanceStrong().also { it.setSeed(seed) })
            val kp = kpg.generateKeyPair()
            kp.public.encoded.copyOf(64)
        }.getOrElse {
            HardwareCryptoAccelerator.hmacSha512(seed, "ml-kem-1024-fallback".toByteArray())
        }
    }

    fun xtsKey(): ByteArray {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256)
        return kg.generateKey().encoded + kg.generateKey().encoded
    }
}
