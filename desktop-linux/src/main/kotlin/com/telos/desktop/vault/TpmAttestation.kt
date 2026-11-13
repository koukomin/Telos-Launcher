package com.telos.desktop.vault

import com.telos.desktop.linux.Sys
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat

/**
 * VOS-6 TPM attestation light: local PCR 0/7 digest summary via host `tpm2-tools`.
 * Optional `tpm2_quote` when a persisted AK handle path exists under vault config.
 * This is **not** remote attestation (RAP); no private keys are stored — only hex digests.
 */
object TpmAttestation {
    /** Relative path under `~/.config/telos/` for the AK handle / context reference. */
    const val AK_HANDLE_REL = "tpm/ak.handle"

    interface CommandRunner {
        fun which(name: String): String?
        fun run(vararg args: String, timeoutSec: Long = 6): Sys.Result
    }

    val SysRunner: CommandRunner = object : CommandRunner {
        override fun which(name: String): String? = Sys.which(name)
        override fun run(vararg args: String, timeoutSec: Long): Sys.Result =
            Sys.run(*args, timeoutSec = timeoutSec)
    }

    data class Snapshot(
        /** `unavailable`, `pcr`, `quote`, or `error`. */
        val status: String,
        val fields: Map<String, String>,
    ) {
        fun statusLine(): String {
            val pcr0 = fields["tpm_pcr0"]
            val pcr7 = fields["tpm_pcr7"]
            val quote = fields["tpm_quote"]
            return when (status) {
                "unavailable" -> "unavailable"
                "pcr" -> buildString {
                    append("pcr")
                    if (!pcr0.isNullOrBlank()) append(" 0=").append(shortHex(pcr0))
                    if (!pcr7.isNullOrBlank()) append(" 7=").append(shortHex(pcr7))
                }
                "quote" -> buildString {
                    append("quote")
                    if (!pcr0.isNullOrBlank()) append(" 0=").append(shortHex(pcr0))
                    if (!pcr7.isNullOrBlank()) append(" 7=").append(shortHex(pcr7))
                    if (!quote.isNullOrBlank()) append(" q=").append(shortHex(quote))
                }
                else -> fields["tpm"] ?: status
            }
        }
    }

    /**
     * Collects TPM fields for [TelosOpSecGuard.hostIntegritySnapshot].
     * Never throws; missing tools / TPM → `tpm=unavailable`.
     */
    fun collect(
        configRoot: File? = null,
        runner: CommandRunner = SysRunner,
        workDir: File? = null,
    ): Snapshot {
        return runCatching {
            collectInternal(configRoot, runner, workDir)
        }.getOrElse { err ->
            Snapshot(
                status = "error",
                fields = linkedMapOf("tpm" to "error:${err.javaClass.simpleName}"),
            )
        }
    }

    private fun collectInternal(
        configRoot: File?,
        runner: CommandRunner,
        workDir: File?,
    ): Snapshot {
        val pcrread = runner.which("tpm2_pcrread")
        val getcap = runner.which("tpm2_getcap")
        if (pcrread == null && getcap == null) {
            return Snapshot("unavailable", linkedMapOf("tpm" to "unavailable"))
        }
        if (pcrread == null) {
            return Snapshot("unavailable", linkedMapOf("tpm" to "unavailable"))
        }

        // Best-effort presence probe; ignore failures (no TPM device, permissions).
        if (getcap != null) {
            runner.run(getcap, "properties-fixed", timeoutSec = 4)
        }

        val pcrResult = runner.run(pcrread, "sha256:0,7", timeoutSec = 8)
        if (!pcrResult.ok) {
            // Tools exist but TPM unreadable → still unavailable, do not fail unlock.
            return Snapshot("unavailable", linkedMapOf("tpm" to "unavailable"))
        }
        val digests = parsePcrRead(pcrResult.stdout)
        val pcr0 = digests[0]
        val pcr7 = digests[7]
        if (pcr0.isNullOrBlank() && pcr7.isNullOrBlank()) {
            return Snapshot("unavailable", linkedMapOf("tpm" to "unavailable"))
        }

        val fields = linkedMapOf<String, String>()
        fields["tpm"] = "pcr"
        if (!pcr0.isNullOrBlank()) fields["tpm_pcr0"] = normalizeHex(pcr0)
        if (!pcr7.isNullOrBlank()) fields["tpm_pcr7"] = normalizeHex(pcr7)

        val quoteHex = maybeQuote(configRoot, runner, workDir)
        if (!quoteHex.isNullOrBlank()) {
            fields["tpm"] = "quote"
            fields["tpm_quote"] = quoteHex
            return Snapshot("quote", fields)
        }
        return Snapshot("pcr", fields)
    }

    /**
     * Parses `tpm2_pcrread` stdout for PCR indices → digest hex (with or without 0x).
     */
    fun parsePcrRead(stdout: String): Map<Int, String> {
        val out = linkedMapOf<Int, String>()
        // Matches: "  0 : 0xABCD…" / "0: ABCD…" / "  7 : ABCD…"
        val lineRe = Regex(
            """(?m)^\s*(\d+)\s*:\s*(?:0x)?([0-9A-Fa-f]{16,})\s*$""",
        )
        for (m in lineRe.findAll(stdout)) {
            val idx = m.groupValues[1].toIntOrNull() ?: continue
            out[idx] = m.groupValues[2]
        }
        return out
    }

    fun resolveAkHandle(configRoot: File?): String? {
        if (configRoot == null) return null
        val handleFile = File(configRoot, AK_HANDLE_REL)
        if (!handleFile.isFile) return null
        val content = runCatching { handleFile.readText().trim() }.getOrNull().orEmpty()
        if (content.isBlank()) return null
        // Content may be a persistent handle (0x81…) or a path to a context file.
        if (content.startsWith("0x") || content.startsWith("0X")) return content
        val asPath = File(content)
        if (asPath.isFile) return asPath.absolutePath
        // Treat non-path content as a raw handle/context name for -c.
        if (!content.contains('/') && !content.contains('\\')) return content
        return null
    }

    private fun maybeQuote(
        configRoot: File?,
        runner: CommandRunner,
        workDir: File?,
    ): String? {
        val quoteBin = runner.which("tpm2_quote") ?: return null
        val ak = resolveAkHandle(configRoot) ?: return null
        val dir = workDir ?: runCatching {
            File(System.getProperty("java.io.tmpdir"), "telos-tpm-${ProcessHandle.current().pid()}")
                .apply { mkdirs() }
        }.getOrNull() ?: return null
        val msg = File(dir, "quote.msg")
        val sig = File(dir, "quote.sig")
        // Fixed empty nonce keeps this local-only; digest of message is what we persist.
        val nonce = "00"
        val result = runner.run(
            quoteBin,
            "-c", ak,
            "-l", "sha256:0,7",
            "-q", nonce,
            "-m", msg.absolutePath,
            "-s", sig.absolutePath,
            timeoutSec = 12,
        )
        // Prefer hashing the quote message blob; fall back to stdout digest.
        val digestSource = when {
            msg.isFile && msg.length() > 0L -> msg.readBytes()
            result.ok && result.stdout.isNotBlank() -> result.stdout.toByteArray()
            else -> {
                runCatching { msg.delete(); sig.delete() }
                return null
            }
        }
        runCatching { msg.delete(); sig.delete() }
        return sha256Hex(digestSource)
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return HexFormat.of().formatHex(digest)
    }

    fun normalizeHex(raw: String): String =
        raw.removePrefix("0x").removePrefix("0X").lowercase()

    fun shortHex(hex: String, keep: Int = 12): String {
        val n = normalizeHex(hex)
        return if (n.length <= keep) n else n.take(keep) + "…"
    }

    fun defaultConfigRoot(): File? {
        val home = PortableEnvironmentIsolator.vaultHome
            ?: runCatching { PhantomVault.engine.mountPoint.takeIf { it.exists() } }.getOrNull()
            ?: File(System.getProperty("user.home"))
        return File(home, ".config/telos")
    }
}
