package de.mm20.launcher2.network.privatedns

import de.mm20.launcher2.network.api.PrivateDnsMode
import de.mm20.launcher2.network.api.PrivateDnsState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateDnsControllerTest {
    @Test
    fun providerChangeWritesAndVerifiesSystemState() = runBlocking {
        val runner = FakeRunner()
        val controller = PrivateDnsController(runner, GrantedAccess)

        val result = controller.setProvider("DNS.Example")

        assertEquals(
            PrivateDnsResult.Success(PrivateDnsState(PrivateDnsMode.PROVIDER, "dns.example")),
            result,
        )
        assertEquals("hostname", runner.settings["private_dns_mode"])
        assertEquals("dns.example", runner.settings["private_dns_specifier"])
    }

    @Test
    fun providerChangeRestoresPreviousValuesWhenWriteFails() = runBlocking {
        val runner = FakeRunner(
            initial = mapOf("private_dns_mode" to "opportunistic", "private_dns_specifier" to "old.example"),
            failModeWrite = true,
        )
        val controller = PrivateDnsController(runner, GrantedAccess)

        val result = controller.setProvider("new.example")

        assertEquals(
            PrivateDnsResult.Failure(PrivateDnsError.COMMAND_FAILED),
            result,
        )
        assertEquals("opportunistic", runner.settings["private_dns_mode"])
        assertEquals("old.example", runner.settings["private_dns_specifier"])
    }

    @Test
    fun unavailableAndUnpermissionedAccessAreReportedExplicitly() = runBlocking {
        val unavailable = PrivateDnsController(FakeRunner(), UnavailableAccess)
        val unpermissioned = PrivateDnsController(FakeRunner(), AvailableWithoutPermission)

        assertEquals(
            PrivateDnsResult.Failure(PrivateDnsError.SHIZUKU_UNAVAILABLE),
            unavailable.observe(),
        )
        assertEquals(
            PrivateDnsResult.Failure(PrivateDnsError.PERMISSION_REQUIRED),
            unpermissioned.observe(),
        )
    }

    @Test
    fun invalidHostnameDoesNotRunPrivilegedCommands() = runBlocking {
        val runner = FakeRunner()
        val controller = PrivateDnsController(runner, GrantedAccess)

        val result = controller.setProvider("https://dns.example/path")

        assertEquals(
            PrivateDnsResult.Failure(PrivateDnsError.INVALID_HOSTNAME),
            result,
        )
        assertTrue(runner.commands.isEmpty())
    }

    private class FakeRunner(
        initial: Map<String, String> = emptyMap(),
        private val failModeWrite: Boolean = false,
    ) : PrivateDnsCommandRunner {
        val settings = initial.toMutableMap()
        val commands = mutableListOf<List<String>>()

        override fun execute(arguments: Array<out String>): PrivateDnsCommandResult {
            val args = arguments.toList()
            commands += args
            return when {
                args.take(3) == listOf("settings", "get", "global") -> {
                    PrivateDnsCommandResult(0, settings[args[3]] ?: "null")
                }
                args.take(3) == listOf("settings", "put", "global") -> {
                    val key = args[3]
                    val value = args[4]
                    if (failModeWrite && key == "private_dns_mode" && value == "hostname") {
                        PrivateDnsCommandResult(1, "")
                    } else {
                        settings[key] = value
                        PrivateDnsCommandResult(0, "")
                    }
                }
                args.take(3) == listOf("settings", "delete", "global") -> {
                    settings.remove(args[3])
                    PrivateDnsCommandResult(0, "")
                }
                else -> error("Unexpected command")
            }
        }
    }

    private object GrantedAccess : PrivateDnsAccess {
        override fun isAvailable() = true
        override fun hasPermission() = true
    }

    private object UnavailableAccess : PrivateDnsAccess {
        override fun isAvailable() = false
        override fun hasPermission() = false
    }

    private object AvailableWithoutPermission : PrivateDnsAccess {
        override fun isAvailable() = true
        override fun hasPermission() = false
    }
}
