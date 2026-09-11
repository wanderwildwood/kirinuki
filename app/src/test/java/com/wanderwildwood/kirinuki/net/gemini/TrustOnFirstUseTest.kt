package com.wanderwildwood.kirinuki.net.gemini

import okhttp3.tls.HeldCertificate
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TrustOnFirstUseTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun hosts() = KnownHosts(tmp.newFile())

    private fun certificateFor(
        host: String,
        validFrom: Instant = Instant.now().minus(Duration.ofDays(1)),
        validUntil: Instant = Instant.now().plus(Duration.ofDays(365)),
    ): X509Certificate =
        HeldCertificate
            .Builder()
            .commonName(host)
            .addSubjectAlternativeName(host)
            .validityInterval(validFrom.toEpochMilli(), validUntil.toEpochMilli())
            .build()
            .certificate

    @Test
    fun `first sight is trusted and remembered`() {
        val knownHosts = hosts()
        val certificate = certificateFor("example.space")

        TrustOnFirstUse("example.space", knownHosts)
            .checkServerTrusted(arrayOf(certificate), "RSA")

        val remembered = knownHosts.get("example.space")
        assertNotNull(remembered)
        assertEquals(TrustOnFirstUse.fingerprintOf(certificate), remembered.fingerprint)
    }

    @Test
    fun `the same certificate is trusted again`() {
        val knownHosts = hosts()
        val certificate = certificateFor("example.space")

        repeat(2) {
            TrustOnFirstUse("example.space", knownHosts)
                .checkServerTrusted(arrayOf(certificate), "RSA")
        }
    }

    @Test
    fun `a different certificate is refused while the pinned one is still valid`() {
        val knownHosts = hosts()
        TrustOnFirstUse("example.space", knownHosts)
            .checkServerTrusted(arrayOf(certificateFor("example.space")), "RSA")

        val impostor = certificateFor("example.space")

        assertFailsWith<CertificateException> {
            TrustOnFirstUse("example.space", knownHosts)
                .checkServerTrusted(arrayOf(impostor), "RSA")
        }
    }

    @Test
    fun `a different certificate is accepted once the pinned one has expired`() {
        val knownHosts = hosts()
        val old =
            certificateFor(
                "example.space",
                validFrom = Instant.now().minus(Duration.ofDays(400)),
                validUntil = Instant.now().minus(Duration.ofDays(1)),
            )
        // Pin it while it was still valid, by telling the manager it is a year ago.
        val backThen = Instant.now().minus(Duration.ofDays(200))
        TrustOnFirstUse("example.space", knownHosts) { backThen }
            .checkServerTrusted(arrayOf(old), "RSA")

        val replacement = certificateFor("example.space")
        TrustOnFirstUse("example.space", knownHosts)
            .checkServerTrusted(arrayOf(replacement), "RSA")

        assertEquals(
            TrustOnFirstUse.fingerprintOf(replacement),
            knownHosts.get("example.space")?.fingerprint,
        )
    }

    @Test
    fun `an expired certificate is refused even when it is the pinned one`() {
        val knownHosts = hosts()
        val expired =
            certificateFor(
                "example.space",
                validFrom = Instant.now().minus(Duration.ofDays(400)),
                validUntil = Instant.now().minus(Duration.ofDays(1)),
            )
        val backThen = Instant.now().minus(Duration.ofDays(200))
        TrustOnFirstUse("example.space", knownHosts) { backThen }
            .checkServerTrusted(arrayOf(expired), "RSA")

        assertFailsWith<CertificateException> {
            TrustOnFirstUse("example.space", knownHosts)
                .checkServerTrusted(arrayOf(expired), "RSA")
        }
    }

    @Test
    fun `each host is pinned separately`() {
        val knownHosts = hosts()
        TrustOnFirstUse("one.space", knownHosts)
            .checkServerTrusted(arrayOf(certificateFor("one.space")), "RSA")

        assertNotNull(knownHosts.get("one.space"))
        assertNull(knownHosts.get("two.space"))
    }

    @Test
    fun `it refuses to vouch for clients`() {
        assertFailsWith<CertificateException> {
            TrustOnFirstUse("example.space", hosts())
                .checkClientTrusted(arrayOf(certificateFor("example.space")), "RSA")
        }
    }
}
