package com.huskydg.localproxydetector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huskydg.localproxydetector.netif.InterfaceReport
import com.huskydg.localproxydetector.netif.NetworkInterfaceScanner
import com.huskydg.localproxydetector.netif.NetworkScanResult
import com.huskydg.localproxydetector.probe.IfconfigClient
import com.huskydg.localproxydetector.probe.ProxyEndpoint
import com.huskydg.localproxydetector.probe.ProxyScanner
import com.huskydg.localproxydetector.probe.ProxyType
import com.huskydg.localproxydetector.probe.ScanMode
import com.huskydg.localproxydetector.probe.ScanPhase
import com.huskydg.localproxydetector.probe.ScanProgress
import com.huskydg.localproxydetector.ui.components.AccentRow
import com.huskydg.localproxydetector.ui.components.Badge
import com.huskydg.localproxydetector.ui.components.CategoryCard
import com.huskydg.localproxydetector.ui.components.ControlButton
import com.huskydg.localproxydetector.ui.components.DashTitle
import com.huskydg.localproxydetector.ui.components.ExpandRow
import com.huskydg.localproxydetector.ui.components.InfoRow
import com.huskydg.localproxydetector.ui.components.MagicIcons
import com.huskydg.localproxydetector.ui.components.MagicTextField
import com.huskydg.localproxydetector.ui.components.MutedText
import com.huskydg.localproxydetector.ui.components.Notice
import com.huskydg.localproxydetector.ui.components.NoticeKind
import com.huskydg.localproxydetector.ui.components.SectionCaption
import com.huskydg.localproxydetector.ui.components.SettingsGroup
import com.huskydg.localproxydetector.ui.components.StatusChip
import com.huskydg.localproxydetector.ui.components.StatusKind
import com.huskydg.localproxydetector.ui.components.SwitchRow
import com.huskydg.localproxydetector.ui.theme.LocalProxyDetectorTheme
import com.huskydg.localproxydetector.ui.theme.MagicTheme
import com.huskydg.localproxydetector.ui.theme.MonoFamily
import com.huskydg.localproxydetector.xray.XrayApiScanResult
import com.huskydg.localproxydetector.xray.XrayApiScanner
import com.huskydg.localproxydetector.xray.XrayOutboundSummary
import com.huskydg.localproxydetector.xray.XrayScanProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalProxyDetectorTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scanner = remember { ProxyScanner() }
    val xrayScanner = remember { XrayApiScanner() }

    var automaticMode by rememberSaveable { mutableStateOf(true) }
    var manualPortText by rememberSaveable { mutableStateOf("1080") }
    var showAbout by rememberSaveable { mutableStateOf(false) }

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

    val status = proxyStatus(foundEndpoint, direct, viaProxy, scanning, hasTested)
    val endpointNow = foundEndpoint
    val subtitle = when {
        !hasTested -> "Tap Start to scan localhost"
        scanning -> "Scanning localhost\u2026"
        endpointNow != null -> "${endpointNow.type.toUiText()} \u2022 ${formatHostPort(endpointNow.host, endpointNow.port)}"
        else -> "No proxy found on localhost"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
        ) {
            AppHeader(
                status = status,
                subtitle = subtitle,
                running = running,
                onStart = { start() },
                onStop = { stop() },
                onInfo = { showAbout = true },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashTitle("Detection", Modifier.padding(start = 4.dp, top = 4.dp))

                // ── Scan settings ──
                SettingsGroup(title = "Scan settings") {
                    SwitchRow(
                        title = "Automatic scan",
                        description = "Look for the local proxy port yourself: popular ports first, then the full range.",
                        checked = automaticMode,
                        onCheckedChange = { if (!running) automaticMode = it },
                        enabled = !running,
                    )
                    if (!automaticMode) {
                        Spacer(Modifier.height(16.dp))
                        MagicTextField(
                            value = manualPortText,
                            onValueChange = { manualPortText = it.filter { ch -> ch.isDigit() }.take(5) },
                            label = "Port (localhost)",
                            enabled = !running,
                        )
                    }
                }

                // ── Local proxy ──
                CategoryCard(
                    title = "Local proxy",
                    trailing = { StatusChip(status.text, status.kind, small = true) },
                ) {
                    val p = progress
                    if (p != null && scanning) {
                        val label = when (p.phase) {
                            ScanPhase.POPULAR_PORTS -> "Popular ports"
                            ScanPhase.LOW_PORTS -> "Low ports 1-1023"
                            ScanPhase.FULL_RANGE -> "Full scan 1024-65535"
                        }
                        ScanProgressRow(label, p.scanned, p.total, "port ${p.currentPort}")
                    }
                    InfoRow(
                        label = "Proxy",
                        value = when {
                            endpointNow != null -> "${endpointNow.type.toUiText()} ${formatHostPort(endpointNow.host, endpointNow.port)}"
                            !hasTested -> "-"
                            scanning -> "scanning..."
                            else -> "not found"
                        },
                    )
                    InfoRow(
                        label = "Direct IP",
                        value = direct.toUiText(),
                        mono = direct.ip != null,
                        valueColor = direct.color(),
                    )
                    InfoRow(
                        label = "IP via proxy",
                        value = viaProxy.toUiText(),
                        mono = viaProxy.ip != null,
                        valueColor = viaProxy.color(),
                        divider = false,
                    )
                    errorText?.let {
                        Notice("Error: $it", Modifier.padding(12.dp), NoticeKind.Error)
                    }
                }

                // ── Xray API ──
                val api = xrayApi
                val xrayChip = when {
                    !hasTested -> UiStatus("Not tested", StatusKind.Neutral)
                    xrayJob != null -> UiStatus("Scanning", StatusKind.Busy)
                    api != null -> UiStatus("Found", StatusKind.Active)
                    else -> UiStatus("Not found", StatusKind.Inactive)
                }
                CategoryCard(
                    title = "Xray API",
                    trailing = { StatusChip(xrayChip.text, xrayChip.kind, small = true) },
                ) {
                    val xp = xrayProgress
                    if (xp != null && xrayJob != null) {
                        ScanProgressRow(
                            label = "Xray API scan",
                            scanned = xp.scanned,
                            total = xp.total,
                            detail = formatHostPort(xp.host, xp.currentPort),
                        )
                    }
                    if (api != null) {
                        InfoRow("Endpoint", formatHostPort(api.endpoint.host, api.endpoint.port), mono = true)
                        InfoRow("Outbounds", api.outbounds.size.toString(), divider = api.outbounds.isNotEmpty())
                        api.outbounds.take(20).forEachIndexed { index, outbound ->
                            OutboundItem(outbound, divider = index < minOf(api.outbounds.size, 20) - 1 || api.outbounds.size > 20)
                        }
                        if (api.outbounds.size > 20) {
                            MutedText("...and ${api.outbounds.size - 20} more")
                        }
                    } else if (xrayJob == null) {
                        MutedText(if (hasTested) "No Xray API found on localhost." else "Not tested yet.")
                    }
                    xrayErrorText?.let {
                        Notice("Xray API error: $it", Modifier.padding(12.dp), NoticeKind.Error)
                    }
                }

                // ── Network interfaces ──
                NetworkInterfaceCard(
                    hasTested = hasTested,
                    scanning = netifJob != null,
                    result = netifResult,
                    errorText = netifErrorText,
                )

                Spacer(Modifier.height(20.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = MagicTheme.colors.surface3,
            title = { Text("Proxy Detector", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Checks whether other apps on this device can reach a local SOCKS5/HTTP proxy " +
                        "(the \"localhost attack\"), whether an Xray API is exposed and leaks your " +
                        "outbound config, and which VPN / tunnel interfaces are visible.\n\n" +
                        "Tip: run proxies with random credentials on every device.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } },
        )
    }
}

// ───────────────────────── Header (app bar + status + Start/Stop) ─────────────────────────

@Composable
private fun AppHeader(
    status: UiStatus,
    subtitle: String,
    running: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onInfo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
            )
            Text(
                text = "Proxy Detector",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MagicTheme.colors.surface3)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable(onClick = onInfo),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MagicIcons.Info,
                    contentDescription = "About",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(text = "Status: ${status.text}", kind = status.kind)
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton("Start", isStop = false, enabled = !running, onClick = onStart, modifier = Modifier.weight(1f))
            ControlButton("Stop", isStop = true, enabled = running, onClick = onStop, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScanProgressRow(label: String, scanned: Int, total: Int, detail: String) {
    val fraction = if (total > 0) (scanned.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "$scanned/$total (${(fraction * 100).toInt()}%)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = MonoFamily,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MagicTheme.colors.surface4,
        )
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = MonoFamily)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

// ───────────────────────── Xray outbound row ─────────────────────────

@Composable
private fun OutboundItem(outbound: XrayOutboundSummary, divider: Boolean) {
    val addressPort = if (outbound.address != null && outbound.port != null) {
        "${outbound.address}:${outbound.port}"
    } else {
        "-"
    }
    AccentRow(accent = MaterialTheme.colorScheme.error, divider = divider) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = outbound.tag,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Badge(outbound.protocolName ?: "-")
        }
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MetaLine("address:port", addressPort)
                MetaLine("uuid", outbound.uuid ?: "-")
                MetaLine("reality sni", outbound.sni ?: "-")
                MetaLine("reality publicKey", outbound.publicKey ?: "-")
            }
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontFamily = MonoFamily,
        lineHeight = 17.sp,
    )
}

// ───────────────────────── Network interfaces card ─────────────────────────

@Composable
private fun NetworkInterfaceCard(
    hasTested: Boolean,
    scanning: Boolean,
    result: NetworkScanResult?,
    errorText: String?,
) {
    val (chip, detail) = when {
        !hasTested -> UiStatus("Not tested", StatusKind.Neutral) to "not tested yet"
        scanning -> UiStatus("Scanning", StatusKind.Busy) to "scanning..."
        result == null -> UiStatus("Unknown", StatusKind.Neutral) to "unknown"
        result.vpnStatus.vpnActive -> {
            val n = result.vpnStatus.transportNetworkCount
            val ifaces = result.vpnStatus.underlyingInterfaceNames
            UiStatus("VPN active", StatusKind.Active) to
                "ACTIVE ($n network${if (n != 1) "s" else ""}" +
                (if (ifaces.isNotEmpty()) ", ${ifaces.joinToString(", ")})" else ")")
        }
        else -> UiStatus("No VPN", StatusKind.Inactive) to "none reported by the OS"
    }

    var showOthers by rememberSaveable { mutableStateOf(false) }

    CategoryCard(
        title = "Network interfaces",
        trailing = { StatusChip(chip.text, chip.kind, small = true) },
    ) {
        InfoRow("VPN transport", detail, divider = result != null)

        if (result != null) {
            val suspicious = result.suspiciousInterfaces
            if (suspicious.isNotEmpty()) {
                SectionCaption("Suspicious interfaces (${suspicious.size})", color = MaterialTheme.colorScheme.error)
                suspicious.forEachIndexed { i, r -> InterfaceItem(r, divider = i < suspicious.size - 1) }
            } else {
                Notice(
                    "No tun/wg/tailscale-like interfaces found",
                    Modifier.padding(12.dp),
                    NoticeKind.Success,
                )
            }

            val normal = result.interfaces.filterNot { it.suspicious }
            if (normal.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ExpandRow(
                    title = "Other interfaces (${normal.size})",
                    expanded = showOthers,
                    onToggle = { showOthers = !showOthers },
                )
                if (showOthers) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    normal.forEachIndexed { i, r -> InterfaceItem(r, divider = i < normal.size - 1) }
                }
            }
        }

        errorText?.let {
            Notice("Network interface scan error: $it", Modifier.padding(12.dp), NoticeKind.Error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterfaceItem(r: InterfaceReport, divider: Boolean) {
    val cs = MaterialTheme.colorScheme
    val ext = MagicTheme.colors
    AccentRow(accent = if (r.suspicious) cs.error else Color.Transparent, divider = divider) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(r.name, color = cs.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = MonoFamily)
            if (r.displayName.isNotBlank() && r.displayName != r.name) {
                Text(
                    "(${r.displayName})",
                    color = cs.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 2.dp),
        ) {
            if (r.isUp) Badge("up", container = ext.successContainer, content = ext.onSuccessContainer)
            else Badge("down", container = ext.surface4, content = cs.onSurfaceVariant)
            if (r.isPointToPoint) Badge("p2p")
            if (r.isVirtual) Badge("virtual")
            if (r.isLoopback) Badge("loopback")
        }
        val addr = if (r.addresses.isEmpty()) "no addr" else r.addresses.joinToString(", ")
        SelectionContainer {
            Text(
                text = "mtu=${r.mtu} \u2022 $addr",
                color = cs.onSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = MonoFamily,
                lineHeight = 17.sp,
            )
        }
        r.suspicionReason?.let {
            Text(it, color = cs.error, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

// ───────────────────────── State helpers ─────────────────────────

private data class UiStatus(val text: String, val kind: StatusKind)

private fun proxyStatus(
    endpoint: ProxyEndpoint?,
    direct: IpResult,
    viaProxy: IpResult,
    scanning: Boolean,
    hasTested: Boolean,
): UiStatus = when {
    !hasTested -> UiStatus("Not tested yet", StatusKind.Neutral)
    scanning -> UiStatus("Scanning...", StatusKind.Busy)
    endpoint == null -> UiStatus("VPN not found", StatusKind.Inactive)
    direct.ip != null && viaProxy.ip != null && direct.ip == viaProxy.ip ->
        UiStatus("Per-app split disabled", StatusKind.Warning)
    direct.ip != null && viaProxy.ip != null && direct.ip != viaProxy.ip ->
        UiStatus("Detected", StatusKind.Active)
    else -> UiStatus("Unable to compare IPs", StatusKind.Inactive)
}

private data class IpResult(
    val ip: String? = null,
    val error: String? = null,
    val loading: Boolean = false,
)

@Composable
private fun IpResult.color(): Color = when {
    error != null -> MaterialTheme.colorScheme.error
    loading || ip == null -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}

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
