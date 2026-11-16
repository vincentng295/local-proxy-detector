package com.huskydg.localproxydetector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.huskydg.localproxydetector.probe.IfconfigClient
import com.huskydg.localproxydetector.probe.ProxyEndpoint
import com.huskydg.localproxydetector.probe.ProxyScanner
import com.huskydg.localproxydetector.probe.ProxyType
import com.huskydg.localproxydetector.probe.ScanMode
import com.huskydg.localproxydetector.probe.ScanProgress
import com.huskydg.localproxydetector.netif.InterfaceReport
import com.huskydg.localproxydetector.netif.NetworkInterfaceScanner
import com.huskydg.localproxydetector.netif.NetworkScanResult
import com.huskydg.localproxydetector.ui.theme.LocalProxyDetectorTheme
import com.huskydg.localproxydetector.xray.XrayApiScanResult
import com.huskydg.localproxydetector.xray.XrayApiScanner
import com.huskydg.localproxydetector.xray.XrayScanProgress
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalProxyDetectorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun MainScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scanner = remember { ProxyScanner() }
    val xrayScanner = remember { XrayApiScanner() }

    var automaticMode by rememberSaveable { mutableStateOf(true) }
    var manualPortText by rememberSaveable { mutableStateOf("1080") }

    var scanJob by remember { mutableStateOf<Job?>(null) }
    var directJob by remember { mutableStateOf<Job?>(null) }
    var proxyJob by remember { mutableStateOf<Job?>(null) }
    var xrayJob by remember { mutableStateOf<Job?>(null) }
    var netifJob by remember { mutableStateOf<Job?>(null) }

    var runId by remember { mutableStateOf(0L) }
    var hasTested by rememberSaveable { mutableStateOf(false) }

    val scanning = scanJob != null
    val running = scanJob != null || directJob != null || proxyJob != null || xrayJob != null || netifJob != null

    var progress by remember { mutableStateOf<ScanProgress?>(null) }
    var foundEndpoint by remember { mutableStateOf<ProxyEndpoint?>(null) }
    var direct by remember { mutableStateOf(IpResult()) }
    var viaProxy by remember { mutableStateOf(IpResult()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    var xrayProgress by remember { mutableStateOf<XrayScanProgress?>(null) }
    var xrayApi by remember { mutableStateOf<XrayApiScanResult?>(null) }
    var xrayErrorText by remember { mutableStateOf<String?>(null) }

    var netifResult by remember { mutableStateOf<NetworkScanResult?>(null) }
    var netifErrorText by remember { mutableStateOf<String?>(null) }

    fun stop() {
        runId += 1
        directJob?.cancel()
        proxyJob?.cancel()
        scanJob?.cancel()
        xrayJob?.cancel()
        netifJob?.cancel()
        directJob = null
        proxyJob = null
        scanJob = null
        xrayJob = null
        netifJob = null
    }

    fun start() {
        stop()

        val mode = if (automaticMode) ScanMode.AUTO else ScanMode.MANUAL
        val manualPort = if (mode == ScanMode.MANUAL) manualPortText.trim().toIntOrNull() else null
        if (mode == ScanMode.MANUAL && (manualPort == null || manualPort !in 1..65535)) {
            errorText = "Invalid port (1-65535)"
            return
        }

        runId += 1
        val thisRunId = runId
        hasTested = true

        progress = null
        foundEndpoint = null
        errorText = null
        xrayProgress = null
        xrayApi = null
        xrayErrorText = null
        netifResult = null
        netifErrorText = null

        direct = IpResult(loading = true)
        viaProxy = IpResult()

        directJob = scope.launch {
            try {
                val result = IfconfigClient.fetchDirectIp()
                if (runId == thisRunId) direct = result.toIpResult()
            } catch (_: CancellationException) {
                // user-cancelled
            } catch (e: Exception) {
                if (runId == thisRunId) direct = IpResult(error = e.message ?: e.toString())
            } finally {
                if (runId == thisRunId) directJob = null
            }
        }

        scanJob = scope.launch {
            try {
                val endpoint = scanner.findOpenProxyEndpoint(
                    mode = mode,
                    manualPort = manualPort,
                    onProgress = { progress = it },
                )
                foundEndpoint = endpoint
                if (endpoint != null) {
                    viaProxy = IpResult(loading = true)
                    proxyJob = scope.launch {
                        try {
                            val result = IfconfigClient.fetchIpViaProxy(endpoint)
                            if (runId == thisRunId) viaProxy = result.toIpResult()
                        } catch (_: CancellationException) {
                            // user-cancelled
                        } catch (e: Exception) {
                            if (runId == thisRunId) viaProxy = IpResult(error = e.message ?: e.toString())
                        } finally {
                            if (runId == thisRunId) proxyJob = null
                        }
                    }
                }
            } catch (_: CancellationException) {
                // user-cancelled
            } catch (e: Exception) {
                errorText = e.message ?: e.toString()
            } finally {
                if (runId == thisRunId) scanJob = null
            }
        }

        xrayJob = scope.launch {
            try {
                val result = xrayScanner.findXrayApi(
                    onProgress = { xrayProgress = it },
                )
                if (runId == thisRunId) xrayApi = result
            } catch (_: CancellationException) {
                // user-cancelled
            } catch (e: Exception) {
                if (runId == thisRunId) {
                    xrayErrorText = e.message ?: e.toString()
                }
            } finally {
                if (runId == thisRunId) xrayJob = null
            }
        }

        netifJob = scope.launch {
            try {
                val result = NetworkInterfaceScanner.scan(context.applicationContext)
                if (runId == thisRunId) netifResult = result
            } catch (_: CancellationException) {
                // user-cancelled
            } catch (e: Exception) {
                if (runId == thisRunId) netifErrorText = e.message ?: e.toString()
            } finally {
                if (runId == thisRunId) netifJob = null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Per-app split tunnel bypass",
            style = MaterialTheme.typography.titleLarge,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Automatic scan")
            Switch(
                checked = automaticMode,
                onCheckedChange = { if (!running) automaticMode = it },
                enabled = !running,
            )
        }

        if (!automaticMode) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = manualPortText,
                onValueChange = { manualPortText = it.filter { ch -> ch.isDigit() }.take(5) },
                enabled = !running,
                label = { Text("Port (localhost)") },
                singleLine = true,
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { if (running) stop() else start() },
        ) {
            Text(if (running) "Stop" else "Start")
        }

        if (progress != null && scanning) {
            val p = progress!!
            val percent = if (p.total > 0) (p.scanned * 100 / p.total) else 0
            val label = when (p.phase) {
                com.huskydg.localproxydetector.probe.ScanPhase.POPULAR_PORTS -> "Popular ports"
                com.huskydg.localproxydetector.probe.ScanPhase.LOW_PORTS -> "Low ports 1-1023"
                com.huskydg.localproxydetector.probe.ScanPhase.FULL_RANGE -> "Full scan 1024-65535"
            }
            Text("$label: ${p.scanned}/${p.total} ($percent%), port ${p.currentPort}")
        }

        StatusBlock(
            endpoint = foundEndpoint,
            direct = direct,
            viaProxy = viaProxy,
            scanning = scanning,
            hasTested = hasTested,
        )

        if (foundEndpoint != null) {
            val ep = foundEndpoint!!
            Text("Proxy: ${ep.type.toUiText()} ${formatHostPort(ep.host, ep.port)}")
        }

        Text("Direct IP: ${direct.toUiText()}")
        Text("IP via proxy: ${viaProxy.toUiText()}")

        if (errorText != null) {
            Text(
                text = "Error: $errorText",
                color = Color(0xFFD32F2F),
            )
        }

        if (xrayProgress != null && xrayJob != null) {
            val p = xrayProgress!!
            val percent = if (p.total > 0) (p.scanned * 100 / p.total) else 0
            Text("Xray API scan: ${p.scanned}/${p.total} ($percent%), ${formatHostPort(p.host, p.currentPort)}")
        }

        XrayApiStatusBlock(
            hasTested = hasTested,
            scanning = xrayJob != null,
            api = xrayApi,
        )

        if (xrayApi != null) {
            val api = xrayApi!!
            Text("Xray API: ${formatHostPort(api.endpoint.host, api.endpoint.port)}")
            Text("Outbounds: ${api.outbounds.size}")
            api.outbounds.take(20).forEach { outbound ->
                val protocolName = outbound.protocolName ?: "-"
                val addressPort = if (outbound.address != null && outbound.port != null) {
                    "${outbound.address}:${outbound.port}"
                } else {
                    "-"
                }
                val uuid = outbound.uuid ?: "-"
                val sni = outbound.sni ?: "-"
                val publicKey = outbound.publicKey ?: "-"

                Text("Outbound '${outbound.tag}'")
                Text("protocolName: $protocolName")
                Text("address:port: $addressPort")
                Text("uuid: $uuid")
                Text("reality sni: $sni")
                Text("reality publicKey: $publicKey")
            }
            if (api.outbounds.size > 20) {
                Text("...and ${api.outbounds.size - 20} more")
            }
        }

        if (xrayErrorText != null) {
            Text(
                text = "Xray API error: $xrayErrorText",
                color = Color(0xFFD32F2F),
            )
        }

        NetworkInterfaceBlock(
            hasTested = hasTested,
            scanning = netifJob != null,
            result = netifResult,
            errorText = netifErrorText,
        )
    }
}

@Composable
private fun NetworkInterfaceBlock(
    hasTested: Boolean,
    scanning: Boolean,
    result: NetworkScanResult?,
    errorText: String?,
) {
    val (vpnText, vpnColor) = when {
        !hasTested -> "VPN transport: not tested yet" to Color(0xFF9E9E9E)
        scanning -> "VPN transport: scanning..." to Color.Unspecified
        result == null -> "VPN transport: unknown" to Color(0xFF9E9E9E)
        result.vpnStatus.vpnActive ->
            "VPN transport: ACTIVE (${result.vpnStatus.transportNetworkCount} network" +
                (if (result.vpnStatus.transportNetworkCount != 1) "s" else "") +
                if (result.vpnStatus.underlyingInterfaceNames.isNotEmpty()) {
                    ", ${result.vpnStatus.underlyingInterfaceNames.joinToString(", ")})"
                } else {
                    ")"
                } to Color(0xFF388E3C)
        else -> "VPN transport: none reported by the OS" to Color(0xFFD32F2F)
    }

    Text(
        text = vpnText,
        color = vpnColor,
        style = MaterialTheme.typography.titleMedium,
    )

    if (result != null) {
        val suspicious = result.suspiciousInterfaces
        if (suspicious.isNotEmpty()) {
            Text(
                text = "Suspicious interfaces (${suspicious.size}):",
                color = Color(0xFFD32F2F),
                style = MaterialTheme.typography.titleSmall,
            )
            suspicious.forEach { Text(it.toUiLine(), color = Color(0xFFD32F2F)) }
        } else {
            Text("No tun/wg/tailscale-like interfaces found", color = Color(0xFF388E3C))
        }

        val normal = result.interfaces.filterNot { it.suspicious }
        if (normal.isNotEmpty()) {
            Text(
                text = "Other interfaces (${normal.size}):",
                style = MaterialTheme.typography.titleSmall,
            )
            normal.forEach { Text(it.toUiLine()) }
        }
    }

    if (errorText != null) {
        Text(
            text = "Network interface scan error: $errorText",
            color = Color(0xFFD32F2F),
        )
    }
}

private fun InterfaceReport.toUiLine(): String {
    val flags = buildList {
        if (isUp) add("up") else add("down")
        if (isPointToPoint) add("p2p")
        if (isVirtual) add("virtual")
        if (isLoopback) add("loopback")
    }.joinToString(",")
    val addr = if (addresses.isEmpty()) "no addr" else addresses.joinToString(", ")
    val reasonSuffix = suspicionReason?.let { " -- $it" } ?: ""
    return "$name ($displayName) [$flags] mtu=$mtu $addr$reasonSuffix"
}

@Composable
private fun StatusBlock(
    endpoint: ProxyEndpoint?,
    direct: IpResult,
    viaProxy: IpResult,
    scanning: Boolean,
    hasTested: Boolean,
) {
    val (text, color) = when {
        !hasTested -> "Not tested yet" to Color(0xFF9E9E9E)
        scanning -> "Scanning..." to Color.Unspecified
        endpoint == null -> "VPN not found" to Color(0xFFD32F2F)
        direct.ip != null && viaProxy.ip != null && direct.ip == viaProxy.ip ->
            "Per-app split disabled" to Color(0xFFFFA000)
        direct.ip != null && viaProxy.ip != null && direct.ip != viaProxy.ip ->
            "Detected" to Color(0xFF388E3C)
        else -> "Unable to compare IPs" to Color(0xFFD32F2F)
    }

    Text(
        text = "Status: $text",
        color = color,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun XrayApiStatusBlock(
    hasTested: Boolean,
    scanning: Boolean,
    api: XrayApiScanResult?,
) {
    val (text, color) = when {
        !hasTested -> "Xray API: Not tested yet" to Color(0xFF9E9E9E)
        scanning -> "Xray API: Scanning..." to Color.Unspecified
        api != null -> "Xray API: Found" to Color(0xFF388E3C)
        else -> "Xray API: Not found" to Color(0xFFD32F2F)
    }

    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.titleMedium,
    )
}

private data class IpResult(
    val ip: String? = null,
    val error: String? = null,
    val loading: Boolean = false,
)

private fun Result<String>.toIpResult(): IpResult {
    return fold(
        onSuccess = { IpResult(ip = it.trim()) },
        onFailure = { IpResult(error = it.message ?: it.toString()) },
    )
}

private fun IpResult.toUiText(): String {
    if (loading) return "loading..."
    ip?.let { return it }
    error?.let { return "error: $it" }
    return "-"
}

private fun ProxyType.toUiText(): String = when (this) {
    ProxyType.SOCKS5 -> "SOCKS5"
    ProxyType.HTTP -> "HTTP"
    ProxyType.DOKODEMO_DNS -> "dokodemo / dns-in"
    ProxyType.DOKODEMO_HTTP -> "dokodemo / redirect-in (HTTP target)"
    ProxyType.DOKODEMO_TLS -> "dokodemo / redirect-in (TLS target)"
    ProxyType.DOKODEMO_UNKNOWN -> "dokodemo / redirect-in or tproxy-in (unidentified target)"
}

private fun formatHostPort(host: String, port: Int): String {
    return if (host.contains(':')) {
        "[$host]:$port"
    } else {
        "$host:$port"
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    LocalProxyDetectorTheme {
        MainScreen()
    }
}
