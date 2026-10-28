package de.mm20.launcher2.network.impl

import de.mm20.launcher2.network.api.*
import de.mm20.launcher2.network.impl.wg.WgHopPolicy
import org.junit.Assert.*
import org.junit.Test

class WgHopPolicyTest {
    private fun config(id: Int, via: Int? = null, routes: List<String> = listOf("0.0.0.0/0", "::/0")) = WireguardConfig(id, "Tunnel $id", WgInterface("test", listOf("10.0.0.2/32")), listOf(WgPeer("test", allowedIps = routes)), hopVia = via)
    @Test fun validTwoHopPairIsAccepted() {
        val entry = config(1); val exit = config(2, 1)
        WgHopPolicy.validate(exit, listOf(entry, exit))
    }
    @Test fun selfMissingPartialAndThreeHopChainsAreRejected() {
        val invalid = listOf(
            config(1,1) to listOf(config(1,1)),
            config(2,9) to listOf(config(2,9)),
            config(2,1) to listOf(config(1, routes = listOf("0.0.0.0/0")), config(2,1)),
            config(3,2) to listOf(config(1),config(2,1),config(3,2)),
            config(2,1) to listOf(config(1),config(2,1),config(3,2)),
        )
        for ((candidate, all) in invalid) {
            val error = runCatching { WgHopPolicy.validate(candidate, all) }.exceptionOrNull()
            assertTrue(error is WgConfigException)
            assertEquals(WgConfigError.InvalidHop, (error as WgConfigException).error)
        }
    }
}
