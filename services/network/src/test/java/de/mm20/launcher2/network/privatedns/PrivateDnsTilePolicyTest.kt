package de.mm20.launcher2.network.privatedns

import de.mm20.launcher2.network.api.PrivateDnsMode
import de.mm20.launcher2.network.api.PrivateDnsState
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivateDnsTilePolicyTest {
    @Test
    fun enablesAutomaticModeWhenPrivateDnsIsOff() {
        assertEquals(
            PrivateDnsMode.AUTOMATIC,
            PrivateDnsTilePolicy.nextMode(PrivateDnsState(PrivateDnsMode.OFF)),
        )
    }

    @Test
    fun disablesAutomaticOrProviderMode() {
        assertEquals(
            PrivateDnsMode.OFF,
            PrivateDnsTilePolicy.nextMode(PrivateDnsState(PrivateDnsMode.AUTOMATIC)),
        )
        assertEquals(
            PrivateDnsMode.OFF,
            PrivateDnsTilePolicy.nextMode(PrivateDnsState(PrivateDnsMode.PROVIDER, "dns.example")),
        )
    }
}
