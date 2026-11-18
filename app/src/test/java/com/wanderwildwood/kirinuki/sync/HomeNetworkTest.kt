package com.wanderwildwood.kirinuki.sync

import com.wanderwildwood.kirinuki.sync.greader.GReaderException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeNetworkTest {
    private val mobileData = NetworkNow(vpn = false, wifiOrEthernet = false)
    private val wifi = NetworkNow(vpn = false, wifiOrEthernet = true)
    private val vpnOnMobileData = NetworkNow(vpn = true, wifiOrEthernet = false)

    @Test
    fun thisPhoneIsAlwaysAllowed() {
        assertTrue(HomeNetwork.httpAllowed("localhost", mobileData))
        assertTrue(HomeNetwork.httpAllowed("127.0.0.1", mobileData))
        assertTrue(HomeNetwork.httpAllowed("::1", mobileData))
    }

    @Test
    fun homeAddressesNeedWifiOrAVpn() {
        assertFalse(HomeNetwork.httpAllowed("192.168.1.20", mobileData))
        assertFalse(HomeNetwork.httpAllowed("freshrss.lan", mobileData))
        assertTrue(HomeNetwork.httpAllowed("192.168.1.20", wifi))
        assertTrue(HomeNetwork.httpAllowed("freshrss.lan", wifi))
        assertTrue(HomeNetwork.httpAllowed("10.0.2.2", vpnOnMobileData))
    }

    @Test
    fun tailscaleAddressesNeedAVpn() {
        assertFalse(HomeNetwork.httpAllowed("100.101.102.103", mobileData))
        assertFalse(HomeNetwork.httpAllowed("100.101.102.103", wifi))
        assertTrue(HomeNetwork.httpAllowed("100.101.102.103", vpnOnMobileData))
        assertFalse(HomeNetwork.httpAllowed("fd7a:115c:a1e0::1", wifi))
        assertTrue(HomeNetwork.httpAllowed("[fd7a:115c:a1e0::1]", vpnOnMobileData))
        // Just outside 100.64.0.0/10: not a home address at all.
        assertFalse(HomeNetwork.httpAllowed("100.128.0.1", vpnOnMobileData))
    }

    @Test
    fun publicAddressesAreNeverAllowed() {
        val everything = NetworkNow(vpn = true, wifiOrEthernet = true)
        assertFalse(HomeNetwork.httpAllowed("203.0.113.9", everything))
        assertFalse(HomeNetwork.httpAllowed("feeds.example.org", everything))
    }

    @Test
    fun theGateStopsPlainHttpBeforeItLeaves() {
        val client = OkHttpClient.Builder().addInterceptor(HomeNetworkGate { mobileData }).build()
        val request = Request.Builder().url("http://192.168.1.20/api/greader.php/accounts/ClientLogin").build()
        assertFailsWith<NotOnHomeNetwork> { client.newCall(request).execute() }
    }

    @Test
    fun aStoppedRequestReadsAsNotOnTheHomeNetwork() {
        assertEquals(ServerProblem.NOT_HOME_NETWORK, GReaderException.Unreachable(NotOnHomeNetwork()).toProblem())
        assertEquals(ServerProblem.UNREACHABLE, GReaderException.Unreachable(java.io.IOException()).toProblem())
    }
}
