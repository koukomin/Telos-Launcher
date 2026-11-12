package com.telos.desktop.startup

import com.telos.desktop.cursor.CursorThemeApplier
import com.telos.desktop.cursor.XCursorThemeDiscovery
import com.telos.desktop.icons.LinuxIconThemeResolver
import com.telos.desktop.network.DesktopNetworkController
import com.telos.desktop.vault.HardwareCryptoAccelerator
import com.telos.desktop.vault.PhantomVaultEngine
import com.telos.desktop.vault.PortableStegoFs
import com.telos.desktop.vault.PortableTraceGuard
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Behavioral checks that heavy desktop caches stay cold until first use.
 * Not a wall-clock microbenchmark — asserts lazy/cached semantics.
 */
class DesktopColdStartLazyInitTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() {
        XCursorThemeDiscovery.invalidate()
        HardwareCryptoAccelerator.clearProbeCache()
        DesktopNetworkController.clearConfigHomeCache()
        PortableTraceGuard.clearSelectedRoot()
    }

    @After
    fun tearDown() {
        XCursorThemeDiscovery.invalidate()
        HardwareCryptoAccelerator.clearProbeCache()
        DesktopNetworkController.clearConfigHomeCache()
        PortableTraceGuard.clearSelectedRoot()
        System.clearProperty("telos.portable.data")
        System.clearProperty("telos.portable.active")
    }

    @Test
    fun iconThemeScanIsLazyThenCached() {
        val themeDir = tmp.newFolder("icons", "LazyTheme")
        File(themeDir, "index.theme").writeText(
            """
            [Icon Theme]
            Name=LazyTheme
            Comment=Test
            Inherits=hicolor
            Directories=48x48/apps
            [48x48/apps]
            Size=48
            Context=Applications
            Type=Fixed
            """.trimIndent(),
        )
        File(themeDir, "48x48/apps").mkdirs()

        val resolver = LinuxIconThemeResolver(
            preferredTheme = "LazyTheme",
            extraRoots = listOf(themeDir.parentFile),
        )
        assertFalse(resolver.hasCachedThemes())
        val first = resolver.listInstalledThemes()
        assertTrue(resolver.hasCachedThemes())
        assertTrue(first.any { it.name == "LazyTheme" })
        val second = resolver.listInstalledThemes()
        assertSame(first, second)
        resolver.invalidate()
        assertFalse(resolver.hasCachedThemes())
    }

    @Test
    fun cursorThemeListIsLazyThenCached() {
        assertFalse(XCursorThemeDiscovery.isCached())
        val listed = XCursorThemeDiscovery.listInstalled(includeHostSystem = false)
        assertTrue(XCursorThemeDiscovery.isCached(includeHostSystem = false))
        val again = XCursorThemeDiscovery.listInstalled(includeHostSystem = false)
        assertSame(listed, again)
        XCursorThemeDiscovery.invalidate()
        assertFalse(XCursorThemeDiscovery.isCached(includeHostSystem = false))
    }

    @Test
    fun cursorApplyDefersDecodeUntilEnsureDecoded() {
        val result = CursorThemeApplier.apply(
            themeName = "Nonexistent-ColdStart-Theme",
            size = 24,
            followSystemTheme = false,
        )
        assertNotNull(result.theme)
        // Windows/macOS use stock AWT cursors and never defer XCursor decode.
        if (com.telos.desktop.platform.PlatformEnvironment.current !=
            com.telos.desktop.platform.TelosRuntimePlatform.LinuxDesktop
        ) {
            assertFalse(CursorThemeApplier.isDecodePending())
            return
        }
        assertTrue(CursorThemeApplier.isDecodePending())
        // Env published without walking icon roots for decode.
        assertFalse(XCursorThemeDiscovery.isCached())
        CursorThemeApplier.ensureDecoded()
        assertFalse(CursorThemeApplier.isDecodePending())
        // ensureDecoded triggers discovery once (theme missing → Compose fallback).
        assertTrue(XCursorThemeDiscovery.isCached())
    }

    @Test
    fun networkConfigHomeCreateFalseDoesNotMkdir() {
        val previousHome = System.getProperty("user.home")
        val isolated = tmp.newFolder("home-isolated")
        try {
            System.setProperty("user.home", isolated.absolutePath)
            DesktopNetworkController.clearConfigHomeCache()
            val dir = DesktopNetworkController.configHome(create = false)
            assertFalse("cold-start probe must not mkdir ~/.config/telos", dir.exists())
            assertTrue(dir.path.replace('\\', '/').endsWith(".config/telos"))
            val created = DesktopNetworkController.configHome(create = true)
            assertTrue(created.isDirectory)
            assertTrue(created.exists())
        } finally {
            System.setProperty("user.home", previousHome)
            DesktopNetworkController.clearConfigHomeCache()
        }
    }

    @Test
    fun hardwareProbeCachesAndVaultEngineDefersStego() {
        assertFalse(HardwareCryptoAccelerator.isProbed())
        val a = HardwareCryptoAccelerator.probe()
        assertTrue(HardwareCryptoAccelerator.isProbed())
        val b = HardwareCryptoAccelerator.probe()
        assertSame(a, b)

        HardwareCryptoAccelerator.clearProbeCache()
        assertFalse(HardwareCryptoAccelerator.isProbed())
        // Constructing the engine must not probe /proc or allocate stego manager eagerly.
        PhantomVaultEngine()
        assertFalse(HardwareCryptoAccelerator.isProbed())
    }

    @Test
    fun stegoCarriersRootPathDoesNotMkdir() {
        val root = tmp.newFolder("portable-root")
        System.setProperty("telos.portable.data", root.absolutePath)
        PortableTraceGuard.selectDataRoot(root)
        val carriers = PortableStegoFs.carriersRoot()
        assertFalse(carriers.exists())
        // ensure still available for unlock/create paths
        val ensured = PortableStegoFs.ensureCarriersRoot()
        assertTrue(ensured.isDirectory)
        // Compare canonical Files — Windows may surface 8.3 short paths via absolutePath.
        val ensuredCanon = ensured.canonicalFile
        val rootCanon = root.canonicalFile
        assertTrue(
            ensuredCanon == rootCanon ||
                ensuredCanon.toPath().startsWith(rootCanon.toPath()),
        )
    }
}
