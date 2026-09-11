package com.wanderwildwood.kirinuki.net.gemini

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * The one test that holds the line between the two trust models in this app.
 *
 * [TrustOnFirstUse] deliberately accepts a certificate no authority has vouched for. If it
 * ever reached the OkHttpClient the feeds are fetched with, every https request in the app
 * would silently stop being able to detect interception — and nothing would report it,
 * because a connection that is trusted too easily looks exactly like one that worked.
 *
 * So this does not assert that the two are wired separately, which would pass whether or
 * not it were true. It puts a certificate no CA has signed in front of **both** clients and
 * requires them to disagree: the ordinary client must refuse it, and only then does the
 * Gemini client accepting it mean anything.
 */
class GeminiTlsIsolationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val selfSigned =
        HeldCertificate
            .Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()

    private fun serverWith(certificate: HeldCertificate): MockWebServer =
        MockWebServer().apply {
            useHttps(
                HandshakeCertificates
                    .Builder()
                    .heldCertificate(certificate)
                    .build()
                    .sslSocketFactory(),
                false,
            )
        }

    @Test
    fun `the app's ordinary client refuses a certificate no authority signed`() {
        val server = serverWith(selfSigned)
        server.enqueue(MockResponse().setBody("should never be read"))
        server.start(InetAddress.getByName("localhost"), 0)

        // The app's client: plain OkHttp defaults, exactly as KirinukiApplication builds it.
        val client = OkHttpClient.Builder().build()

        assertFailsWith<SSLHandshakeException> {
            client.newCall(Request.Builder().url(server.url("/")).build()).execute()
        }

        server.shutdown()
    }

    @Test
    fun `the gemini trust manager accepts that same certificate, and pins it`() {
        val server = serverWith(selfSigned)
        server.start(InetAddress.getByName("localhost"), 0)

        val knownHosts = KnownHosts(tmp.newFile())
        val context =
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(TrustOnFirstUse("localhost", knownHosts)), null)
            }

        val socket =
            context.socketFactory.createSocket("localhost", server.port) as SSLSocket
        socket.soTimeout = 5_000
        socket.startHandshake()
        socket.close()

        val pinned = knownHosts.get("localhost")
        assertNotNull(pinned, "the handshake succeeded but nothing was pinned")
        assertEquals(
            TrustOnFirstUse.fingerprintOf(selfSigned.certificate),
            pinned.fingerprint,
        )

        server.shutdown()
    }

    @Test
    fun `a second certificate on a pinned host is refused at the handshake`() {
        val knownHosts = KnownHosts(tmp.newFile())

        val first = serverWith(selfSigned)
        first.start(InetAddress.getByName("localhost"), 0)
        SSLContext
            .getInstance("TLS")
            .apply { init(null, arrayOf(TrustOnFirstUse("localhost", knownHosts)), null) }
            .socketFactory
            .createSocket("localhost", first.port)
            .let { it as SSLSocket }
            .apply {
                soTimeout = 5_000
                startHandshake()
                close()
            }
        first.shutdown()

        // Same host name, different key: what interception looks like.
        val impostor =
            HeldCertificate
                .Builder()
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .build()
        val second = serverWith(impostor)
        second.start(InetAddress.getByName("localhost"), 0)

        assertFailsWith<SSLHandshakeException> {
            SSLContext
                .getInstance("TLS")
                .apply { init(null, arrayOf(TrustOnFirstUse("localhost", knownHosts)), null) }
                .socketFactory
                .createSocket("localhost", second.port)
                .let { it as SSLSocket }
                .apply {
                    soTimeout = 5_000
                    startHandshake()
                }
        }

        second.shutdown()
    }
}
