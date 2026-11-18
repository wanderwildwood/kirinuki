package com.wanderwildwood.kirinuki.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.wanderwildwood.kirinuki.sync.greader.GReaderApi
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** What the phone is on right now, as far as a plain http home server is concerned. */
data class NetworkNow(
    /** A VPN (Tailscale, say) is up. */
    val vpn: Boolean,
    /** The phone's own connection is Wi-Fi or Ethernet, not mobile data. */
    val wifiOrEthernet: Boolean,
)

/** A request to an http home server was stopped before it left: the phone is not on the home network. */
class NotOnHomeNetwork : IOException("not on the home network")

/**
 * When a feed server at a plain http:// address may be sent the login. An address only looks
 * like home (a private range, `.lan`, `.local`, ...): on a café's Wi-Fi or a carrier's network
 * the same address can be somebody else's. So the address alone is not enough:
 *
 * - this phone itself (localhost, 127.x, ::1): always;
 * - a Tailscale address (100.64.0.0/10, fd7a:115c:a1e0::/48): only while a VPN is up, since
 *   without one that range is the carrier's shared network;
 * - any other home address: on Wi-Fi, Ethernet or a VPN, never on mobile data.
 *
 * A Wi-Fi that reuses the same private range still passes; telling which Wi-Fi it is would
 * need the location permission.
 */
object HomeNetwork {
    fun httpAllowed(
        host: String,
        now: NetworkNow,
    ): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (isLoopback(h)) return true
        if (!GReaderApi.isLocalAddress(h)) return false
        if (isTailscaleRange(h)) return now.vpn
        return now.vpn || now.wifiOrEthernet
    }

    internal fun isLoopback(h: String): Boolean =
        h == "localhost" || h == "::1" || (ipv4(h)?.let { it[0] == 127 } ?: false)

    internal fun isTailscaleRange(h: String): Boolean {
        if (h.contains(':')) return h.startsWith("fd7a:115c:a1e0:")
        val ip = ipv4(h) ?: return false
        return ip[0] == 100 && ip[1] in 64..127
    }

    private fun ipv4(h: String): List<Int>? {
        val parts = h.split('.')
        if (parts.size != 4) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        return numbers.takeIf { n -> n.all { it in 0..255 } }
    }

    /**
     * The phone's network now. A VPN counts when the active network is one or any network
     * the phone holds is one (a VPN that routes only its own range, as Tailscale does without
     * an exit node, need not be the active network).
     */
    fun now(context: Context): NetworkNow {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkNow(vpn = false, wifiOrEthernet = false)
        val active = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }

        @Suppress("DEPRECATION")
        val anyVpn = cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
        val vpn = active?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true || anyVpn
        val wifiOrEthernet =
            active?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                active?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        return NetworkNow(vpn = vpn, wifiOrEthernet = wifiOrEthernet)
    }
}

/**
 * Stops every plain http request to the feed server, redirects included, unless
 * [HomeNetwork.httpAllowed] says the network fits; checked again for each request, so a phone
 * that leaves the Wi-Fi mid-sync stops sending. https is never held back.
 */
class HomeNetworkGate(
    private val now: () -> NetworkNow,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (!url.isHttps && !HomeNetwork.httpAllowed(url.host, now())) throw NotOnHomeNetwork()
        return chain.proceed(chain.request())
    }
}
