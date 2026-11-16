package com.huskydg.localproxydetector.xray

data class XrayApiEndpoint(
    val host: String,
    val port: Int,
)

data class XrayOutboundSummary(
    val tag: String,
    val protocolName: String?,
    val address: String?,
    val port: Int?,
    val uuid: String?,
    val sni: String?,
    val publicKey: String?,
    val senderSettingsType: String?,
    val proxySettingsType: String?,
)

data class XrayApiScanResult(
    val endpoint: XrayApiEndpoint,
    val outbounds: List<XrayOutboundSummary>,
)

data class XrayScanProgress(
    val host: String,
    val scanned: Int,
    val total: Int,
    val currentPort: Int,
)
