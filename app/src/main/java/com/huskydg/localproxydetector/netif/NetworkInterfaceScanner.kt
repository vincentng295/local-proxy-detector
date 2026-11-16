package com.huskydg.localproxydetector.netif

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.NetworkInterface
import java.util.Collections

/**
 * Enumerates every network interface visible to the app (loopback does NOT
 * isolate this the way VpnService does -- see README) and flags ones that
 * look like a VPN/tunnel rather than ordinary Wi-Fi/mobile data, plus checks
 * whether the OS currently reports an active VPN transport at all.
 *
 * This complements the localhost proxy scan: a user can disable or kill the
 * VPN app's own detection and still leave a tun/wg/tailscale interface
 * sitting there, or spyware can check "is there a VPN interface at all"
 * without ever touching the loopback proxy.
 */
object NetworkInterfaceScanner {

    /** Interface name patterns that strongly suggest a VPN/tunnel adapter.
     * Matched against the raw interface name (e.g. "tun0", "wg0-mullvad").
     * Patterns are matched as "name starts with X" after stripping trailing
     * digits/suffixes is unnecessary since regex already allows them. */
    private val KNOWN_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("^tun\\d*$") to "tun* (generic VPN tunnel, incl. xray's xraytun0-style tun devices)",
        Regex("^xraytun\\d*$") to "xraytun* (xray-core tun2socks device)",
        Regex("^wg\\d*$") to "wg* (WireGuard)",
        Regex("^tailscale\\d*$") to "tailscale* (Tailscale)",
        Regex("^ts\\d*$") to "ts* (Tailscale alias / userspace WireGuard)",
        Regex("^utun\\d*$") to "utun* (userspace tunnel, WireGuard/IPsec clients)",
        Regex("^ppp\\d*$") to "ppp* (point-to-point, legacy VPN)",
        Regex("^ipsec\\d*$") to "ipsec* (IPsec tunnel)",
        Regex("^zt\\d*$") to "zt* (ZeroTier)",
        Regex("^tap\\d*$") to "tap* (OpenVPN TAP device)",
        Regex("^nordlynx$") to "nordlynx (NordVPN's WireGuard interface)",
        Regex("^wgcf$") to "wgcf (WARP/wgcf WireGuard interface)",
        Regex("^clat\\d*$") to "clat* (464XLAT -- not a VPN but easily confused for one)",
    )

    suspend fun scan(context: Context): NetworkScanResult = withContext(Dispatchers.IO) {
        NetworkScanResult(
            vpnStatus = checkVpnStatus(context),
            interfaces = enumerateInterfaces(),
        )
    }

    private fun checkVpnStatus(context: Context): VpnStatus {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return VpnStatus(vpnActive = false, transportNetworkCount = 0, underlyingInterfaceNames = emptyList())

            val networks = cm.allNetworks
            val vpnNetworks = networks.filter { net ->
                val caps = cm.getNetworkCapabilities(net)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }

            val underlyingNames = vpnNetworks.mapNotNull { net ->
                try {
                    cm.getLinkProperties(net)?.interfaceName
                } catch (_: Exception) {
                    null
                }
            }

            VpnStatus(
                vpnActive = vpnNetworks.isNotEmpty(),
                transportNetworkCount = vpnNetworks.size,
                underlyingInterfaceNames = underlyingNames,
            )
        } catch (_: Exception) {
            VpnStatus(vpnActive = false, transportNetworkCount = 0, underlyingInterfaceNames = emptyList())
        }
    }

    private fun enumerateInterfaces(): List<InterfaceReport> {
        val interfaces = try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
        } catch (_: Exception) {
            emptyList()
        }

        return interfaces.map { nif -> nif.toReport() }
            .sortedWith(compareByDescending<InterfaceReport> { it.suspicious }.thenBy { it.name })
    }

    private fun NetworkInterface.toReport(): InterfaceReport {
        val isUp = safeCall { isUp } ?: false
        val isLoopback = safeCall { isLoopback } ?: false
        val isPointToPoint = safeCall { isPointToPoint } ?: false
        val isVirtual = safeCall { isVirtual } ?: false
        val mtu = safeCall { mtu } ?: -1
        val addresses = safeCall {
            interfaceAddresses.mapNotNull { it.address?.hostAddress }
        } ?: emptyList()

        val patternMatch = KNOWN_PATTERNS.firstOrNull { (regex, _) -> regex.matches(name) }

        val (suspicious, reason) = when {
            patternMatch != null -> true to patternMatch.second
            // Heuristic fallback for VPN apps that use an unlisted interface
            // name: a non-loopback, point-to-point interface that is up is
            // the structural signature of nearly every mobile VPN/tunnel
            // (TUN devices are point-to-point by construction), even when
            // its name doesn't match any known VPN product.
            !isLoopback && isPointToPoint && isUp ->
                true to "unrecognized name, but point-to-point + up is the structural signature of a VPN/TUN interface"
            else -> false to null
        }

        return InterfaceReport(
            name = name,
            displayName = safeCall { displayName } ?: name,
            isUp = isUp,
            isLoopback = isLoopback,
            isPointToPoint = isPointToPoint,
            isVirtual = isVirtual,
            mtu = mtu,
            addresses = addresses,
            matchedPattern = patternMatch?.first?.pattern,
            suspicious = suspicious,
            suspicionReason = reason,
        )
    }

    private inline fun <T> safeCall(block: () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }
}
