/*
 * Copyright (c) 2026 Telos contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vitorpamplona.amethyst

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.ui.tor.ArtiNative
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the packaged Android JNI bridge without starting Tor or making network requests. */
@RunWith(AndroidJUnit4::class)
class ArtiNativeSmokeTest {
    @Test
    fun packagedLibraryLoadsAndExposesTheLifecycleBridge() {
        assertEquals("Arti 2.6.0 (custom build with rustls)", ArtiNative.getVersion())
        assertEquals(0, ArtiNative.stopSocksProxy())
        assertEquals(0, ArtiNative.destroy())
        assertEquals(-1, ArtiNative.isBootstrapped())
        assertEquals(-1, ArtiNative.bootstrapProgressPermille())
    }
}
