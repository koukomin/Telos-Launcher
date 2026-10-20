package de.mm20.launcher2.comms.files

import de.mm20.launcher2.helper.InstalledApkExport
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream

class InstalledApkExportTest {
    @Test fun singleApkIsByteIdenticalAndSplitExportContainsAllComponents() {
        val root = Files.createTempDirectory("apk-export").toFile()
        try {
            val base = root.resolve("base.apk").apply { writeBytes(byteArrayOf(1,2,3)) }
            val split = root.resolve("config.apk").apply { writeBytes(byteArrayOf(4,5,6)) }
            val single = ByteArrayOutputStream()
            InstalledApkExport.write(listOf(base), single)
            assertArrayEquals(base.readBytes(), single.toByteArray())
            val bundle = ByteArrayOutputStream()
            InstalledApkExport.write(listOf(base, split), bundle)
            ZipInputStream(ByteArrayInputStream(bundle.toByteArray())).use { zip ->
                assertEquals("base.apk", zip.nextEntry.name); assertArrayEquals(base.readBytes(), zip.readBytes())
                assertEquals("split-001.apk", zip.nextEntry.name); assertArrayEquals(split.readBytes(), zip.readBytes())
                assertNull(zip.nextEntry)
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun missingComponentsCannotProduceASuccessfulPartialBackup() {
        val root = Files.createTempDirectory("apk-export").toFile()
        try {
            val base = root.resolve("base.apk").apply { writeText("base") }
            assertThrows(IllegalArgumentException::class.java) {
                InstalledApkExport.write(listOf(base, root.resolve("missing")), ByteArrayOutputStream())
            }
        } finally { root.deleteRecursively() }
    }
}
