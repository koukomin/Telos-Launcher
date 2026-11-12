package com.telos.desktop.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalDeviceAccessTest {
    @Test
    fun detectFromWhichRequiresPortalAndDbus() {
        assertTrue(
            PortalDeviceAccess.detectFromWhich(
                mapOf(
                    "xdg-desktop-portal" to "/usr/libexec/xdg-desktop-portal",
                    "gdbus" to "/usr/bin/gdbus",
                ),
            ),
        )
        assertTrue(
            PortalDeviceAccess.detectFromWhich(
                mapOf(
                    "xdg-desktop-portal-gtk" to "/usr/libexec/xdg-desktop-portal-gtk",
                    "dbus-send" to "/usr/bin/dbus-send",
                ),
            ),
        )
        assertFalse(
            PortalDeviceAccess.detectFromWhich(
                mapOf("xdg-desktop-portal" to "/usr/libexec/xdg-desktop-portal"),
            ),
        )
        assertFalse(
            PortalDeviceAccess.detectFromWhich(
                mapOf("gdbus" to "/usr/bin/gdbus"),
            ),
        )
        assertFalse(PortalDeviceAccess.detectFromWhich(emptyMap()))
    }

    @Test
    fun deviceTokensMatchPortalApi() {
        assertEquals("camera", PortalDeviceAccess.deviceToken(PortalDeviceKind.Camera))
        assertEquals("microphone", PortalDeviceAccess.deviceToken(PortalDeviceKind.Microphone))
    }
}
