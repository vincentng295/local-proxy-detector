package com.huskydg.localproxydetector.probe

enum class ScanMode {
    AUTO,
    MANUAL,
}

enum class ScanPhase {
    POPULAR_PORTS,
    LOW_PORTS,
    FULL_RANGE,
}

enum class ProxyType {
    SOCKS5,
    HTTP,
    // dokodemo-door style transparent inbounds (dns-in, redirect-in, tproxy-in):
    // these just forward raw bytes to a fixed destination configured in xray,
    // so they have no handshake of their own — we fingerprint them by what
    // comes back when we throw known protocol traffic at them.
    DOKODEMO_DNS,
    DOKODEMO_HTTP,
    DOKODEMO_TLS,
    DOKODEMO_UNKNOWN,
}

data class ProxyEndpoint(
    val host: String,
    val port: Int,
    val type: ProxyType,
)

data class FoundProxy(
    val endpoint: ProxyEndpoint,
    val ipViaProxy: String,
)

data class ScanProgress(
    val phase: ScanPhase,
    val scanned: Int,
    val total: Int,
    val currentPort: Int,
)

