package com.telos.desktop.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class StegoShardArrayManagerTest {
    @Test
    fun assemblePreparedWritesManifestAndTrailers() {
        val dir = File(System.getProperty("java.io.tmpdir"), "telos-stego-prep-${System.nanoTime()}")
        dir.mkdirs()
        try {
            val a = File(dir, "track-a.mp3").also { it.writeBytes(ByteArray(8192) { 1 }) }
            val b = File(dir, "track-b.flac").also { it.writeBytes(ByteArray(4096) { 2 }) }
            val key = ByteArray(64) { it.toByte() }
            val mgr = StegoShardArrayManager()
            val result = mgr.assemblePrepared(key, dir, perShardBytes = 1024L * 1024)
            assertTrue(result is StegoAssembleResult.Prepared)
            val prepared = result as StegoAssembleResult.Prepared
            assertEquals(2, prepared.bindings.size)
            val manifestFile = File(dir, StegoCarrierManifest.FILE_NAME)
            assertTrue(manifestFile.isFile)
            val roundTrip = StegoCarrierManifest.read(manifestFile)
            assertNotNull(roundTrip)
            assertEquals(2, roundTrip!!.shards.size)
            assertEquals(prepared.manifest.shards.map { it.relativePath }, roundTrip.shards.map { it.relativePath })
            for (binding in prepared.bindings) {
                assertTrue(binding.file.length() >= binding.payloadOffset + binding.payloadBytes)
            }
            assertTrue(a.length() > 8192)
            assertTrue(b.length() > 4096)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun assembleLinearImageConcatenatesPayloads() {
        val dir = File(System.getProperty("java.io.tmpdir"), "telos-stego-lin-${System.nanoTime()}")
        dir.mkdirs()
        try {
            File(dir, "one.png").writeBytes(ByteArray(2048) { 7 })
            File(dir, "two.jpg").writeBytes(ByteArray(1024) { 9 })
            val key = ByteArray(32) { (it * 3).toByte() }
            val mgr = StegoShardArrayManager()
            val prepared = mgr.assemblePrepared(key, dir, perShardBytes = 64L * 1024) as StegoAssembleResult.Prepared
            val out = File(dir, "out.tvault")
            mgr.assembleLinearImage(prepared.bindings, out)
            val expected = prepared.bindings.sumOf { it.payloadBytes }
            assertEquals(expected, out.length())
            val first = prepared.bindings.first()
            val expectedByte = RandomAccessFile(first.file, "r").use { raf ->
                raf.seek(first.payloadOffset)
                raf.read()
            }
            RandomAccessFile(out, "r").use { raf ->
                assertEquals(expectedByte, raf.read())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun loadBindingsFromManifestMatchesPrepare() {
        val dir = File(System.getProperty("java.io.tmpdir"), "telos-stego-man-${System.nanoTime()}")
        dir.mkdirs()
        try {
            File(dir, "clip.mp4").writeBytes(ByteArray(3000) { 5 })
            val key = ByteArray(48) { 11 }
            val mgr = StegoShardArrayManager()
            val prepared = mgr.assemblePrepared(key, dir, perShardBytes = 32L * 1024) as StegoAssembleResult.Prepared
            val loaded = mgr.loadBindingsFromManifest(dir)
            assertNotNull(loaded)
            assertEquals(prepared.bindings.size, loaded!!.size)
            assertEquals(prepared.bindings.first().payloadOffset, loaded.first().payloadOffset)
            assertEquals(prepared.bindings.first().payloadBytes, loaded.first().payloadBytes)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun portableCarriersRootStaysOutsideHostHome() {
        val host = PortableTraceGuard.hostProfileHome().canonicalFile
        val previous = System.getProperty("telos.portable.data")
        val root = File(System.getProperty("java.io.tmpdir"), "telos-portable-stego-${System.nanoTime()}")
        try {
            System.setProperty("telos.portable.data", root.absolutePath)
            PortableTraceGuard.selectDataRoot(root)
            val carriers = PortableStegoFs.ensureCarriersRoot()
            assertTrue(carriers.absolutePath.startsWith(root.absolutePath))
            assertFalse(
                carriers.canonicalPath.startsWith(host.canonicalPath + File.separator) ||
                    carriers.canonicalPath == host.canonicalPath,
            )
            PortableTraceGuard.assertNoHostWrites(root)
        } finally {
            if (previous == null) System.clearProperty("telos.portable.data")
            else System.setProperty("telos.portable.data", previous)
            PortableTraceGuard.clearSelectedRoot()
            root.deleteRecursively()
        }
    }
}
