package com.telos.desktop.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.io.path.createTempDirectory

class DesktopSignedUpdateTest {
    @Test
    fun verifiesLocalManifestAndRequiresConfirmBeforeInstall() {
        val root = createTempDirectory("telos-update").toFile()
        val prevTmp = System.getProperty("java.io.tmpdir")
        try {
            System.setProperty("java.io.tmpdir", root.absolutePath)
            val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            val artifact = File(root, "telos-desktop-test.jar").apply {
                writeBytes("fake-uber-jar-payload-vos5".toByteArray())
            }
            val sha = DesktopSignedUpdate.sha256Hex(artifact)
            val uri = artifact.toURI().toString()
            val version = "9.9.9-test"
            val canonical = DesktopSignedUpdate.canonicalPayload(version, artifact.name, sha, uri)
            val signer = Signature.getInstance("Ed25519")
            signer.initSign(kp.private)
            signer.update(canonical)
            val sigB64 = Base64.getEncoder().encodeToString(signer.sign())

            val manifest = File(root, DesktopSignedUpdate.MANIFEST_NAME)
            manifest.writeText(
                """
                version=$version
                artifact=${artifact.name}
                sha256=$sha
                uri=$uri
                algorithm=Ed25519
                signature=$sigB64
                """.trimIndent() + "\n",
            )

            val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            val wrong = DesktopSignedUpdate.check(manifest.absolutePath, publicKey = other.public)
            assertEquals(DesktopSignedUpdate.CheckStatus.SignatureInvalid, wrong.status)

            val ok = DesktopSignedUpdate.check(manifest.absolutePath, publicKey = kp.public)
            assertEquals(DesktopSignedUpdate.CheckStatus.ReadyForConfirm, ok.status)
            val offer = ok.offer!!
            assertEquals(version, offer.version)
            assertTrue(offer.stagedArtifact.isFile)

            val refused = DesktopSignedUpdate.installConfirmed(offer, "INSTALL:nope")
            assertTrue(refused.isFailure)

            val installed = DesktopSignedUpdate.installConfirmed(
                offer,
                offer.confirmToken,
                destDir = File(root, "updates-out"),
            ).getOrThrow()
            assertTrue(installed.isFile)
            assertEquals(sha, DesktopSignedUpdate.sha256Hex(installed))
            assertFalse(wrong.offer != null)
        } finally {
            if (prevTmp != null) System.setProperty("java.io.tmpdir", prevTmp) else System.clearProperty("java.io.tmpdir")
            root.deleteRecursively()
        }
    }

    @Test
    fun embeddedPublicKeyLoads() {
        val pk = DesktopUpdateTrust.embeddedPublicKey()
        assertTrue(pk.algorithm.equals("Ed25519", ignoreCase = true) || pk.algorithm.equals("EdDSA", ignoreCase = true))
    }

    @Test
    fun refusesCleartextHttpManifest() {
        val result = DesktopSignedUpdate.check("http://example.invalid/update.manifest")
        assertEquals(DesktopSignedUpdate.CheckStatus.ManifestMissing, result.status)
    }
}
