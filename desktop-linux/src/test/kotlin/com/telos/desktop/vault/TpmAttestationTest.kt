package com.telos.desktop.vault

import com.telos.desktop.linux.Sys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TpmAttestationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeRunner(
        private val whichMap: Map<String, String?> = emptyMap(),
        private val responses: MutableMap<String, Sys.Result> = mutableMapOf(),
    ) : TpmAttestation.CommandRunner {
        val calls = mutableListOf<List<String>>()

        fun respond(firstArgContains: String, result: Sys.Result) {
            responses[firstArgContains] = result
        }

        override fun which(name: String): String? = whichMap[name]

        override fun run(vararg args: String, timeoutSec: Long): Sys.Result {
            calls += args.toList()
            val joined = args.joinToString(" ")
            for ((key, value) in responses) {
                if (joined.contains(key)) return value
            }
            return Sys.Result(1, "no fake response")
        }
    }

    @Test
    fun parsePcrReadExtractsZeroAndSeven() {
        val sample = """
            sha256:
              0 : 0xBFCABE7B0CD140CD01A66379CB071FFBE6A77FC4910204D624386C0C06842FAD
              1 : 0x1111111111111111111111111111111111111111111111111111111111111111
              7 : 0x349BCA4071DBF843127300E2D8BD1F07AAD9037533BB52DD82A011DCBC1FE1EE
        """.trimIndent()
        val map = TpmAttestation.parsePcrRead(sample)
        assertEquals(
            "bfcabe7b0cd140cd01a66379cb071ffbe6a77fc4910204d624386c0c06842fad",
            TpmAttestation.normalizeHex(map.getValue(0)),
        )
        assertEquals(
            "349bca4071dbf843127300e2d8bd1f07aad9037533bb52dd82a011dcbc1fe1ee",
            TpmAttestation.normalizeHex(map.getValue(7)),
        )
        assertEquals(3, map.size)
    }

    @Test
    fun parsePcrReadAcceptsCompactFormWithout0x() {
        val sample = """
            sha256:
            0: AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899
            7: 00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF
        """.trimIndent()
        val map = TpmAttestation.parsePcrRead(sample)
        assertEquals(2, map.size)
        assertTrue(map[0]!!.startsWith("AABB"))
        assertTrue(map[7]!!.startsWith("0011"))
    }

    @Test
    fun unavailableWhenToolsMissing() {
        val runner = FakeRunner(whichMap = emptyMap())
        val snap = TpmAttestation.collect(configRoot = tmp.root, runner = runner)
        assertEquals("unavailable", snap.status)
        assertEquals("unavailable", snap.fields["tpm"])
        assertTrue(runner.calls.isEmpty())
    }

    @Test
    fun pcrSnapshotWhenTpm2PcrreadSucceeds() {
        val runner = FakeRunner(
            whichMap = mapOf(
                "tpm2_pcrread" to "/usr/bin/tpm2_pcrread",
                "tpm2_getcap" to "/usr/bin/tpm2_getcap",
            ),
        )
        runner.respond(
            "tpm2_getcap",
            Sys.Result(0, "TPM2_PT_MANUFACTURER:\n  raw: 0x49424D00"),
        )
        runner.respond(
            "tpm2_pcrread",
            Sys.Result(
                0,
                """
                sha256:
                  0 : 0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
                  7 : 0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB
                """.trimIndent(),
            ),
        )
        val snap = TpmAttestation.collect(configRoot = tmp.root, runner = runner)
        assertEquals("pcr", snap.status)
        assertEquals("pcr", snap.fields["tpm"])
        assertEquals(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            snap.fields["tpm_pcr0"],
        )
        assertEquals(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            snap.fields["tpm_pcr7"],
        )
        assertNull(snap.fields["tpm_quote"])
        assertTrue(snap.statusLine().contains("pcr"))
        assertTrue(snap.statusLine().contains("0="))
        assertTrue(snap.statusLine().contains("7="))
    }

    @Test
    fun pcrReadFailureRecordsUnavailableWithoutThrowing() {
        val runner = FakeRunner(
            whichMap = mapOf("tpm2_pcrread" to "/usr/bin/tpm2_pcrread"),
        )
        runner.respond("tpm2_pcrread", Sys.Result(1, "ERROR:tcti:No TPM"))
        val snap = TpmAttestation.collect(configRoot = tmp.root, runner = runner)
        assertEquals("unavailable", snap.status)
        assertEquals("unavailable", snap.fields["tpm"])
    }

    @Test
    fun optionalQuoteStoresHexDigestOnlyWhenAkHandlePresent() {
        val config = tmp.newFolder("telos-config")
        File(config, "tpm").mkdirs()
        File(config, TpmAttestation.AK_HANDLE_REL).writeText("0x8101000A\n")
        val work = tmp.newFolder("tpm-work")

        val runner = FakeRunner(
            whichMap = mapOf(
                "tpm2_pcrread" to "/usr/bin/tpm2_pcrread",
                "tpm2_quote" to "/usr/bin/tpm2_quote",
            ),
        )
        runner.respond(
            "tpm2_pcrread",
            Sys.Result(
                0,
                """
                sha256:
                  0 : 0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC
                  7 : 0xDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD
                """.trimIndent(),
            ),
        )
        val writingRunner = object : TpmAttestation.CommandRunner {
            override fun which(name: String): String? = runner.which(name)
            override fun run(vararg args: String, timeoutSec: Long): Sys.Result {
                if (args.any { it.contains("tpm2_quote") }) {
                    val mIdx = args.indexOf("-m")
                    if (mIdx >= 0 && mIdx + 1 < args.size) {
                        File(args[mIdx + 1]).writeBytes("quote-message-blob".toByteArray())
                    }
                    val sIdx = args.indexOf("-s")
                    if (sIdx >= 0 && sIdx + 1 < args.size) {
                        File(args[sIdx + 1]).writeBytes("sig".toByteArray())
                    }
                    return Sys.Result(0, "ok")
                }
                return runner.run(*args, timeoutSec = timeoutSec)
            }
        }

        val snap = TpmAttestation.collect(
            configRoot = config,
            runner = writingRunner,
            workDir = work,
        )
        assertEquals("quote", snap.status)
        assertEquals("quote", snap.fields["tpm"])
        val expected = TpmAttestation.sha256Hex("quote-message-blob".toByteArray())
        assertEquals(expected, snap.fields["tpm_quote"])
        assertFalse(snap.fields.values.any { it.contains("PRIVATE") })
        assertEquals("0x8101000A", TpmAttestation.resolveAkHandle(config))
    }

    @Test
    fun quoteSkippedWhenAkHandleMissing() {
        val config = tmp.newFolder("no-ak")
        val runner = FakeRunner(
            whichMap = mapOf(
                "tpm2_pcrread" to "/usr/bin/tpm2_pcrread",
                "tpm2_quote" to "/usr/bin/tpm2_quote",
            ),
        )
        runner.respond(
            "tpm2_pcrread",
            Sys.Result(
                0,
                """
                sha256:
                  0 : 0xEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE
                  7 : 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF
                """.trimIndent(),
            ),
        )
        val snap = TpmAttestation.collect(configRoot = config, runner = runner)
        assertEquals("pcr", snap.status)
        assertNull(snap.fields["tpm_quote"])
        assertTrue(runner.calls.none { it.any { a -> a.contains("tpm2_quote") } })
    }

    @Test
    fun hostIntegritySnapshotIncludesTpmViaInjectableRunner() {
        PortableTraceGuard.clearSelectedRoot()
        System.clearProperty("telos.portable.active")
        val runner = FakeRunner(whichMap = emptyMap())
        val guard = TelosOpSecGuard(
            tpmRunner = runner,
            tpmConfigRoot = { tmp.root },
        )
        val snap = guard.hostIntegritySnapshot()
        assertEquals("unavailable", snap["tpm"])
        assertEquals("unavailable", guard.tpmStatusForUi())
        assertTrue(snap.containsKey("os"))
        assertTrue(snap.containsKey("platform"))
    }
}
