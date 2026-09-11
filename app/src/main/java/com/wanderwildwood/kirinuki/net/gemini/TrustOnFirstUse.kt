package com.wanderwildwood.kirinuki.net.gemini

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Instant
import javax.net.ssl.X509TrustManager

/**
 * Gemini's trust model: there are no certificate authorities, so the only question worth
 * asking is whether this is the same certificate the host showed last time.
 *
 * ⚠ **This must never reach the app's shared OkHttpClient.** It accepts a certificate no
 * CA has vouched for, which is correct here and catastrophic for https: installed on the
 * client the feeds are fetched with, every feed would be fetched over a connection that
 * cannot detect interception, and nothing anywhere would report it. It is constructed per
 * connection by [GeminiClient] and reachable from nowhere else. `GeminiTlsIsolationTest`
 * holds that line by proving the ordinary client still refuses the same certificate.
 *
 * One manager per connection, because [X509TrustManager] is not told which host it is
 * being asked about and a fingerprint without a host means nothing.
 */
class TrustOnFirstUse(
    private val host: String,
    private val knownHosts: KnownHosts,
    private val now: () -> Instant = Instant::now,
) : X509TrustManager {
    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ): Unit = throw CertificateException("This is a client, it has no clients of its own")

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
        val leaf =
            chain?.firstOrNull()
                ?: throw CertificateException("$host presented no certificate")

        // An expired certificate is refused even when it is the one we pinned: the host
        // has stopped maintaining it, and "same as last time" stops being reassuring.
        leaf.checkValidity(java.util.Date.from(now()))

        val fingerprint = fingerprintOf(leaf)
        val known = knownHosts.get(host)

        when {
            known == null ->
                // First sight. Nothing to compare against, so this is the one we trust
                // from here on -- which is the whole of the model, and its whole weakness.
                knownHosts.remember(KnownHost(host, fingerprint, leaf.notAfter.toInstant()))

            known.fingerprint == fingerprint ->
                // Same certificate. Carry its expiry forward in case it was reissued
                // with the same key and a later date.
                knownHosts.remember(KnownHost(host, fingerprint, leaf.notAfter.toInstant()))

            known.expiresAt.isBefore(now()) ->
                // The one we pinned has lapsed, so a new one is expected rather than
                // suspicious. This is the only case where a changed certificate is taken.
                knownHosts.remember(KnownHost(host, fingerprint, leaf.notAfter.toInstant()))

            else ->
                throw CertificateException(
                    "$host presented a different certificate and the one recorded for it " +
                        "has not expired. Either the host changed keys early, or this is " +
                        "not the host. Forget it in settings if you know which.",
                )
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        fun fingerprintOf(certificate: X509Certificate): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(certificate.encoded)
                .joinToString("") { "%02x".format(it) }
    }
}
