package com.telos.desktop.vault

import com.telos.desktop.linux.Sys
import java.io.File
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.SecureRandom
import java.security.Security

data class HwCryptoStatus(
    val aesNi: Boolean,
    val vaes: Boolean,
    val avx2: Boolean,
    val avx512: Boolean,
    val shaNi: Boolean,
    val kernelAes: Boolean,
    val kernelXts: Boolean,
    val label: String,
)

object HardwareCryptoAccelerator {
    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        runCatching {
            val clazz = Class.forName("org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider")
            val inst = clazz.getDeclaredConstructor().newInstance() as java.security.Provider
            if (Security.getProvider(inst.name) == null) Security.addProvider(inst)
        }
    }

    fun probe(): HwCryptoStatus {
        val cpu = File("/proc/cpuinfo").takeIf { it.isFile }?.readText().orEmpty().lowercase()
        val flags = cpu.lineSequence().firstOrNull { it.startsWith("flags") }.orEmpty()
        val crypto = File("/proc/crypto").takeIf { it.isFile }?.readText().orEmpty()
        val aesNi = " aes " in " $flags " || flags.contains("aes")
        val vaes = flags.contains("vaes")
        val avx2 = flags.contains("avx2")
        val avx512 = flags.contains("avx512f")
        val shaNi = flags.contains("sha_ni") || flags.contains("sha-ni")
        val kernelAes = crypto.contains("aesni") || crypto.contains("aes-aesni") || crypto.contains("name         : xts(aes)")
        val kernelXts = crypto.contains("xts(aes)") || crypto.contains("xts-aes")
        val parts = buildList {
            if (aesNi) add("AES-NI")
            if (vaes) add("VAES")
            if (avx2) add("AVX2")
            if (avx512) add("AVX512")
            if (shaNi) add("SHA-NI")
        }
        val label = if (parts.isEmpty()) "HW CRYPTO: SOFTWARE" else "HW CRYPTO: ${parts.joinToString(" + ")} ACTIVE"
        return HwCryptoStatus(aesNi, vaes, avx2, avx512, shaNi, kernelAes, kernelXts, label)
    }

    fun argon2id(password: CharArray, salt: ByteArray, outLen: Int = 64): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withIterations(3)
            .withMemoryAsKB(64 * 1024)
            .withParallelism(4)
            .build()
        val gen = Argon2BytesGenerator()
        gen.init(params)
        val out = ByteArray(outLen)
        gen.generateBytes(password, out)
        return out
    }

    fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(key, "HmacSHA512"))
        return mac.doFinal(data)
    }

    fun random(n: Int): ByteArray {
        val b = ByteArray(n)
        SecureRandom().nextBytes(b)
        return b
    }

    fun cryptsetupPerfFlags(): List<String> = listOf(
        "--perf-no_read_workqueue",
        "--perf-no_write_workqueue",
        "--perf-same_cpu_crypt",
        "--persistent",
    )

    fun cipherSpec(): String = "aes-xts-plain64"

    fun keySizeBits(): Int = 512

    fun pkcsOpen(dev: String, name: String, password: String): Boolean {
        val args = mutableListOf("cryptsetup", "open", dev, name, "--type", "luks", "--cipher", cipherSpec())
        args += cryptsetupPerfFlags()
        val r = Sys.run(*(args.toTypedArray()), timeoutSec = 40, input = password + "\n")
        if (r.ok) return true
        return Sys.run(*((listOf("pkexec") + args).toTypedArray()), timeoutSec = 40, input = password + "\n").ok
    }
}
