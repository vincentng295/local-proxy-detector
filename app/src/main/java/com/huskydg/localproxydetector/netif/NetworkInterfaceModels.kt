package com.huskydg.localproxydetector.netif

/** One network interface found on the device, with a verdict on whether its
 * name/shape looks like a VPN/tunnel interface rather than a normal NIC. */
data class InterfaceReport(
    val name: String,
    val displayName: String,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val isPointToPoint: Boolean,
    val isVirtual: Boolean,
    val mtu: Int,
    val addresses: List<String>,
    val matchedPattern: String?,
    val suspicious: Boolean,
    val suspicionReason: String?,
)

/** Whether the device currently has an active VPN network (VpnService-backed
 * tunnel, or any network advertising NetworkCapabilities.TRANSPORT_VPN). */
data class VpnStatus(
    val vpnActive: Boolean,
    val transportNetworkCount: Int,
    val underlyingInterfaceNames: List<String>,
)

data class NetworkScanResult(
    val vpnStatus: VpnStatus,
    val interfaces: List<InterfaceReport>,
) {
    val suspiciousInterfaces: List<InterfaceReport> get() = interfaces.filter { it.suspicious }
}
