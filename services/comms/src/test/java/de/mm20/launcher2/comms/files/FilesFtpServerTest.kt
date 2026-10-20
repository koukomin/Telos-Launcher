package de.mm20.launcher2.comms.files

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.nio.file.Files

class FilesFtpServerTest {
    private fun client(server: FilesFtpServer): FTPClient = FTPClient().apply {
        defaultTimeout = 3000
        controlEncoding = "UTF-8"
        connect("127.0.0.1", server.port)
        soTimeout = 3000
        assertTrue(login(server.username, server.password))
        enterLocalPassiveMode(); setFileType(FTP.BINARY_FILE_TYPE)
    }
    @Test fun realClientListsDownloadsResumesAndCloses() {
        val root = Files.createTempDirectory("ftp-root").toFile()
        try {
            root.resolve("hello.txt").writeText("0123456789")
            FilesFtpServer(root, InetAddress.getLoopbackAddress()).use { server ->
                val ftp = client(server)
                try {
                    assertTrue(ftp.listNames().contains("hello.txt"))
                    val full = ByteArrayOutputStream()
                    assertTrue(ftp.retrieveFile("hello.txt", full)); assertEquals("0123456789", full.toString("UTF-8"))
                    ftp.restartOffset = 4
                    val tail = ByteArrayOutputStream()
                    assertTrue(ftp.retrieveFile("hello.txt", tail)); assertEquals("456789", tail.toString("UTF-8"))
                    assertFalse(ftp.storeFile("blocked.txt", ByteArrayInputStream(byteArrayOf(1))))
                    assertFalse(root.resolve("blocked.txt").exists())
                } finally { ftp.disconnect() }
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun uploadsRequireOptInNeverOverwriteAndCannotEscapeRoot() {
        val root = Files.createTempDirectory("ftp-root").toFile()
        val outside = Files.createTempDirectory("ftp-outside").toFile()
        try {
            root.resolve("keep.txt").writeText("keep")
            outside.resolve("secret").writeText("private")
            Files.createSymbolicLink(root.resolve("escape").toPath(), outside.toPath())
            FilesFtpServer(root, InetAddress.getLoopbackAddress(), true).use { server ->
                val ftp = client(server)
                try {
                    assertTrue(ftp.storeFile("new.txt", ByteArrayInputStream("upload".toByteArray())))
                    assertEquals("upload", root.resolve("new.txt").readText())
                    assertFalse(ftp.storeFile("keep.txt", ByteArrayInputStream("replace".toByteArray())))
                    assertEquals("keep", root.resolve("keep.txt").readText())
                    assertFalse(ftp.retrieveFile("../secret", ByteArrayOutputStream()))
                    assertFalse(ftp.retrieveFile("escape/secret", ByteArrayOutputStream()))
                    assertFalse(ftp.changeWorkingDirectory("escape"))
                    assertFalse(ftp.storeFile("../written", ByteArrayInputStream(byteArrayOf(1))))
                    assertFalse(outside.resolve("written").exists())
                } finally { ftp.disconnect() }
            }
        } finally { root.resolve("escape").delete(); root.deleteRecursively(); outside.deleteRecursively() }
    }
    @Test fun oversizedUploadsRemoveOnlyTheirOwnPartialFile() {
        val root = Files.createTempDirectory("ftp-root").toFile()
        try {
            FilesFtpServer(root, InetAddress.getLoopbackAddress(), true, maxUploadBytes = 8).use { server ->
                val ftp = client(server)
                try {
                    assertFalse(ftp.storeFile("too-large", ByteArrayInputStream(ByteArray(1024))))
                    assertFalse(root.resolve("too-large").exists())
                } finally { ftp.disconnect() }
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun anonymousAndWrongCredentialsCannotOpenDataListeners() {
        val root = Files.createTempDirectory("ftp-root").toFile()
        try {
            FilesFtpServer(root, InetAddress.getLoopbackAddress()).use { server ->
                val ftp = FTPClient()
                try {
                    ftp.connect("127.0.0.1", server.port)
                    assertEquals(530, ftp.sendCommand("EPSV"))
                    assertFalse(ftp.login("telos", "wrong"))
                    assertEquals(530, ftp.sendCommand("PASV"))
                } finally { ftp.disconnect() }
            }
        } finally { root.deleteRecursively() }
    }
}
