package de.mm20.launcher2.network.impl.wg

import java.math.BigInteger
import java.security.SecureRandom

/**
 * X25519 (RFC 7748) with BigInteger. Only used as a fallback when the native engine cannot be
 * loaded (for example in tests), to create a key pair. Not constant time, which is acceptable for
 * a key that is generated once on the user's own device.
 */
internal object WgCurve25519 {
    private val P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val A24 = BigInteger.valueOf(121665)
    private val BASE = ByteArray(32).also { it[0] = 9 }

    /** 32 random bytes with the bits cleared/set that WireGuard expects ("clamped"). */
    fun newPrivateKey(): ByteArray {
        val k = ByteArray(32)
        SecureRandom().nextBytes(k)
        return clamp(k)
    }

    fun clamp(k: ByteArray): ByteArray {
        val c = k.copyOf()
        c[0] = (c[0].toInt() and 248).toByte()
        c[31] = (c[31].toInt() and 127).toByte()
        c[31] = (c[31].toInt() or 64).toByte()
        return c
    }

    fun publicKey(privateKey: ByteArray): ByteArray = scalarMult(privateKey, BASE)

    private fun decodeLe(b: ByteArray): BigInteger = BigInteger(1, b.reversedArray())

    private fun encodeLe(v: BigInteger): ByteArray {
        val be = v.toByteArray() // may have a leading zero byte or be shorter than 32
        val out = ByteArray(32)
        for (i in be.indices) {
            val src = be.size - 1 - i
            if (i < 32) out[i] = be[src]
        }
        return out
    }

    fun scalarMult(k: ByteArray, u: ByteArray): ByteArray {
        require(k.size == 32 && u.size == 32)
        val scalar = decodeLe(clamp(k))
        val uc = u.copyOf().also { it[31] = (it[31].toInt() and 127).toByte() }
        val x1 = decodeLe(uc).mod(P)
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = x1
        var z3 = BigInteger.ONE
        var swap = false
        for (t in 254 downTo 0) {
            val kt = scalar.testBit(t)
            if (swap != kt) {
                val tx = x2; x2 = x3; x3 = tx
                val tz = z2; z2 = z3; z3 = tz
            }
            swap = kt
            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)
            val s = da.add(cb).mod(P)
            val df = da.subtract(cb).mod(P)
            x3 = s.multiply(s).mod(P)
            z3 = x1.multiply(df.multiply(df).mod(P)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e)).mod(P)).mod(P)
        }
        if (swap) {
            val tx = x2; x2 = x3; x3 = tx
            val tz = z2; z2 = z3; z3 = tz
        }
        return encodeLe(x2.multiply(z2.modPow(P.subtract(BigInteger.valueOf(2)), P)).mod(P))
    }
}
