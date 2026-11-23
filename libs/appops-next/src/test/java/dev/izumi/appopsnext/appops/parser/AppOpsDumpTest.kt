package dev.izumi.appopsnext.appops.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpsDumpTest {
    @Test
    fun `legacy and ColorOS service headers are accepted`() {
        assertTrue(AppOpsDump.looksLikeServiceDump("Current AppOps Service state:\n  Uid 1000:\n"))
        assertTrue(
            AppOpsDump.looksLikeServiceDump(
                """
                AppOps Service state:
                  Settings:
                AppOps Uid Op State
                  Uid u0a546:
                    CAMERA: mode=ignore
                """.trimIndent(),
            ),
        )
        assertFalse(AppOpsDump.looksLikeServiceDump(""))
        assertFalse(AppOpsDump.looksLikeServiceDump("Permission Denial\n"))
        assertFalse(AppOpsDump.looksLikeServiceDump("Unknown option: --watchers\n"))
    }
}
