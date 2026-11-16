package com.huskydg.localproxydetector.probe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.math.max

class ProxyScanner(
    private val loopbackHosts: List<String> = listOf("127.0.0.1", "::1"),
    private val popularPorts: List<Int> = listOf(1080, 2080, 1081, 10808, 10809, 12334, 7890, 53, 1053, 12345),
    // Low/"well-known" ports (1-1023) are included by default now: dns-in,
    // redirect-in and tproxy-in commonly get bound there (53 for DNS, 80/443
    // for transparent redirect), and nothing stops an unprivileged app from
    // *connecting* to a low port even though it couldn't *bind* one itself.
    private val lowPortRange: IntRange = 1..1023,
    private val scanRange: IntRange = 1024..65535,
    private val connectTimeoutMs: Int = 80,
    private val readTimeoutMs: Int = 120,
    private val maxConcurrency: Int = 200,
    private val progressUpdateEvery: Int = 256,
) {
    suspend fun findOpenProxyEndpoint(
        mode: ScanMode,
        manualPort: Int?,
        onProgress: suspend (ScanProgress) -> Unit,
    ): ProxyEndpoint? {
        return when (mode) {
            ScanMode.MANUAL -> {
                val port = manualPort ?: return null
                onProgress(
                    ScanProgress(
                        phase = ScanPhase.POPULAR_PORTS,
                        scanned = 1,
                        total = 1,
                        currentPort = port,
                    ),
                )
                tryPort(port)
            }

            ScanMode.AUTO -> {
                val foundOnPopular = scanPopularPorts(onProgress)
                if (foundOnPopular != null) return foundOnPopular
                val foundOnLow = scanPortSet(lowPortRange, ScanPhase.LOW_PORTS, onProgress)
                if (foundOnLow != null) return foundOnLow
                scanFullRange(onProgress)
            }
        }
    }

    private suspend fun scanPopularPorts(
        onProgress: suspend (ScanProgress) -> Unit,
    ): ProxyEndpoint? {
        for ((index, port) in popularPorts.withIndex()) {
            coroutineContext.ensureActive()
            onProgress(
                ScanProgress(
                    phase = ScanPhase.POPULAR_PORTS,
                    scanned = index + 1,
                    total = popularPorts.size,
                    currentPort = port,
                ),
            )
            val found = tryPort(port)
            if (found != null) return found
        }
        return null
    }

    /** Scan an explicit port range (e.g. the low 1-1023 block) with the same
     * concurrency/progress-reporting shape as the full-range scan below. */
    private suspend fun scanPortSet(
        range: IntRange,
        phase: ScanPhase,
        onProgress: suspend (ScanProgress) -> Unit,
    ): ProxyEndpoint? = withContext(Dispatchers.IO) {
        coroutineScope {
            val popularSet = popularPorts.toHashSet()
            val total = range.count { it !in popularSet }
            val scanned = AtomicInteger(0)
            val found = AtomicReference<ProxyEndpoint?>(null)

            val dispatcher = Dispatchers.IO.limitedParallelism(max(1, maxConcurrency))

            withContext(Dispatchers.Main.immediate) {
                onProgress(
                    ScanProgress(
                        phase = phase,
                        scanned = 0,
                        total = total,
                        currentPort = range.first,
                    ),
                )
            }

            val workerCount = max(1, min(maxConcurrency, range.count()))
            val jobs = (0 until workerCount).map { workerIndex ->
                launch(dispatcher) {
                    var port = range.first + workerIndex
                    while (port <= range.last) {
                        coroutineContext.ensureActive()
                        if (found.get() != null) return@launch
                        if (port !in popularSet) {
                            val count = scanned.incrementAndGet()
                            if (count % progressUpdateEvery == 0) {
                                withContext(Dispatchers.Main.immediate) {
                                    onProgress(
                                        ScanProgress(
                                            phase = phase,
                                            scanned = count,
                                            total = total,
                                            currentPort = port,
                                        ),
                                    )
                                }
                            }

                            val candidate = tryPort(port)
                            if (candidate != null) {
                                found.compareAndSet(null, candidate)
                                return@launch
                            }
                        }
                        port += workerCount
                    }
                }
            }
            jobs.joinAll()

            found.get()
        }
    }

    private fun min(a: Int, b: Int): Int = if (a < b) a else b

    private suspend fun scanFullRange(
        onProgress: suspend (ScanProgress) -> Unit,
    ): ProxyEndpoint? = scanPortSet(scanRange, ScanPhase.FULL_RANGE, onProgress)

    private suspend fun tryPort(port: Int): ProxyEndpoint? = withContext(Dispatchers.IO) {
        for (host in loopbackHosts) {
            // 1) SOCKS5 / HTTP proxy / dokodemo-door (redirect-in, tproxy-in)
            // fingerprinting over TCP.
            val type = ProxyProber.probeNoAuthProxyType(
                host = host,
                port = port,
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs,
            )
            if (type != null) return@withContext ProxyEndpoint(host, port, type)

            // 2) dns-in: UDP-first DNS fingerprint (TCP DNS fallback is tried
            // inside probeDns). Only worth trying on "DNS-shaped" ports to
            // keep the scan fast, since a UDP probe can't reuse the TCP
            // connect-refused signal to quickly skip closed ports.
            if (port == 53 || port in lowPortRange || port in popularPorts) {
                val dnsType = ProxyProber.probeDns(host, port, readTimeoutMs)
                if (dnsType != null) return@withContext ProxyEndpoint(host, port, dnsType)
            }
        }
        null
    }
}
