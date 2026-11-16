package com.huskydg.localproxydetector.probe

import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.math.min

object ProxyProber {
    /**
     * Probe a TCP endpoint that may be a no-auth SOCKS5/HTTP proxy, or a
     * dokodemo-door-style transparent inbound (redirect-in / tproxy-in),
     * which has no handshake of its own and just forwards raw bytes to
     * whatever destination is configured in xray/sing-box.
     */
    fun probeNoAuthProxyType(
        host: String,
        port: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): ProxyType? {
        return when (probeSocks5NoAuth(host, port, connectTimeoutMs, readTimeoutMs)) {
            SocksProbeResult.SOCKS5_NO_AUTH -> ProxyType.SOCKS5
            SocksProbeResult.CLOSED -> null
            SocksProbeResult.NOT_SOCKS -> {
                if (probeHttpConnectNoAuth(host, port, connectTimeoutMs, readTimeoutMs)) {
                    ProxyType.HTTP
                } else {
                    // Open, but neither SOCKS5 nor an HTTP(S) proxy that answers
                    // CONNECT -- could still be a dokodemo-door style transparent
                    // forward. Fingerprint what's on the other end.
                    probeDokodemo(host, port, connectTimeoutMs, readTimeoutMs)
                }
            }
        }
    }

    /**
     * Probe a port for a DNS (dns-in) listener. Tries UDP first since that's
     * how dns-in is almost always exposed, then falls back to TCP DNS.
     */
    fun probeDns(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): ProxyType? {
        if (probeDnsUdp(host, port, timeoutMs)) return ProxyType.DOKODEMO_DNS
        if (probeDnsTcp(host, port, timeoutMs)) return ProxyType.DOKODEMO_DNS
        return null
    }

    // ---- dokodemo-door / redirect-in / tproxy-in fingerprinting ----

    /**
     * A dokodemo-door inbound (redirect-in, tproxy-in) has no protocol of its
     * own: it just splices the TCP connection through to a fixed destination.
     * So to tell "this is some transparent forward" apart from "nothing is
     * listening here" we throw a few well-known protocol probes at it and see
     * whether something answers in a recognizable way. If the port accepts
     * the connection at all but gives no recognizable response, we still
     * flag it as a candidate (DOKODEMO_UNKNOWN) since a silently-accepting
     * socket is itself the signature of these transparent inbounds.
     */
    private fun probeDokodemo(
        host: String,
        port: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): ProxyType? {
        // 1) Does a plain TCP connect even succeed? If not, nothing here.
        val opens = try {
            Socket().use { it.connect(InetSocketAddress(host, port), connectTimeoutMs) }
            true
        } catch (_: Exception) {
            false
        }
        if (!opens) return null

        // 2) Try a raw (non-CONNECT) HTTP GET -- if the forward target is a
        // plain web server, it will answer with a normal HTTP response line.
        if (probeRawHttpGet(host, port, connectTimeoutMs, readTimeoutMs)) {
            return ProxyType.DOKODEMO_HTTP
        }

        // 3) Try a minimal TLS ClientHello -- if the forward target is a TLS
        // endpoint, it will answer with a TLS record (handshake or alert).
        if (probeTlsClientHello(host, port, connectTimeoutMs, readTimeoutMs)) {
            return ProxyType.DOKODEMO_TLS
        }

        // 4) Open, but nothing recognizable answered -- still a real finding:
        // flag it as an unidentified transparent forward rather than silently
        // dropping it, since that silence is exactly what redirect/tproxy-in
        // looks like against protocols it wasn't fed.
        return ProxyType.DOKODEMO_UNKNOWN
    }

    private fun probeRawHttpGet(
        host: String,
        port: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.soTimeout = readTimeoutMs
                socket.tcpNoDelay = true

                val request =
                    "GET / HTTP/1.1\r\n" +
                        "Host: localhost\r\n" +
                        "User-Agent: LocalProxyDetector/1.0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
                socket.getOutputStream().write(request.toByteArray(Charsets.ISO_8859_1))
                socket.getOutputStream().flush()

                val statusLine = socket.getInputStream().readAsciiLine(maxBytes = 256) ?: return false
                statusLine.startsWith("HTTP/")
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun probeTlsClientHello(
        host: String,
        port: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.soTimeout = readTimeoutMs
                socket.tcpNoDelay = true

                socket.getOutputStream().write(buildMinimalClientHello())
                socket.getOutputStream().flush()

                val header = socket.getInputStream().readExactly(3) ?: return false
                // TLS record header starts with content type (0x14-0x17) and
                // a TLS major version byte of 0x03 for any real TLS reply
                // (handshake, alert, etc. all share this record layer).
                val contentType = header[0].toInt() and 0xFF
                val majorVersion = header[1].toInt() and 0xFF
                contentType in 0x14..0x17 && majorVersion == 0x03
            }
        } catch (_: Exception) {
            false
        }
    }

    /** A syntactically-valid-enough TLS 1.2 ClientHello for an unused SNI. */
    private fun buildMinimalClientHello(): ByteArray {
        val sni = "probe.invalid".toByteArray(Charsets.US_ASCII)
        val random = ByteArray(32) { 0x42 }
        val sessionId = byteArrayOf(0x00)
        val cipherSuites = byteArrayOf(0x00, 0x02, 0xC0.toByte(), 0x2F) // TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
        val compression = byteArrayOf(0x01, 0x00)

        val serverNameEntry = byteArrayOf(0x00) + u16(sni.size) + sni
        val serverNameList = u16(serverNameEntry.size) + serverNameEntry
        val sniExtension = byteArrayOf(0x00, 0x00) + u16(serverNameList.size) + serverNameList
        val extensions = u16(sniExtension.size) + sniExtension

        val body = byteArrayOf(0x03, 0x03) + random + sessionId + cipherSuites + compression + extensions
        val handshake = byteArrayOf(0x01) + u24(body.size) + body
        val record = byteArrayOf(0x16, 0x03, 0x01) + u16(handshake.size) + handshake
        return record
    }

    private fun u16(value: Int): ByteArray = byteArrayOf(((value shr 8) and 0xFF).toByte(), (value and 0xFF).toByte())

    private fun u24(value: Int): ByteArray = byteArrayOf(
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    // ---- dns-in fingerprinting ----

    private fun probeDnsUdp(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val query = buildDnsQuery()
                val packet = DatagramPacket(query, query.size, InetSocketAddress(host, port))
                socket.send(packet)

                val buffer = ByteArray(512)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                isPlausibleDnsResponse(buffer, response.length)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun probeDnsTcp(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs
                socket.tcpNoDelay = true

                val query = buildDnsQuery()
                // DNS-over-TCP is length-prefixed.
                socket.getOutputStream().write(u16(query.size))
                socket.getOutputStream().write(query)
                socket.getOutputStream().flush()

                val lengthPrefix = socket.getInputStream().readExactly(2) ?: return false
                val replyLength = ((lengthPrefix[0].toInt() and 0xFF) shl 8) or (lengthPrefix[1].toInt() and 0xFF)
                if (replyLength < 12 || replyLength > 4096) return false
                val reply = socket.getInputStream().readExactly(replyLength) ?: return false
                isPlausibleDnsResponse(reply, reply.size)
            }
        } catch (_: Exception) {
            false
        }
    }

    private const val DNS_QUERY_ID = 0x51A1

    /** A standard-query DNS packet asking for the A record of a sentinel name. */
    private fun buildDnsQuery(): ByteArray {
        val header = byteArrayOf(
            ((DNS_QUERY_ID shr 8) and 0xFF).toByte(), (DNS_QUERY_ID and 0xFF).toByte(),
            0x01, 0x00, // flags: standard query, recursion desired
            0x00, 0x01, // QDCOUNT = 1
            0x00, 0x00, // ANCOUNT
            0x00, 0x00, // NSCOUNT
            0x00, 0x00, // ARCOUNT
        )
        val name = "probe-bypass-check.invalid".split(".").joinToString(separator = "") { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            (byteArrayOf(bytes.size.toByte()) + bytes).toString(Charsets.ISO_8859_1)
        }.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0x00)
        val question = name + byteArrayOf(0x00, 0x01) + byteArrayOf(0x00, 0x01) // QTYPE=A, QCLASS=IN
        return header + question
    }

    private fun isPlausibleDnsResponse(data: ByteArray, length: Int): Boolean {
        if (length < 12) return false
        val id = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        if (id != DNS_QUERY_ID) return false
        val flags = data[2].toInt() and 0xFF
        val qr = (flags shr 7) and 0x01
        val opcode = (flags shr 3) and 0x0F
        // QR=1 marks it a response; opcode 0 is a standard query reply.
        return qr == 1 && opcode == 0
    }

    // ---- existing SOCKS5 / HTTP CONNECT probes ----

    private enum class SocksProbeResult {
        CLOSED,
        NOT_SOCKS,
        SOCKS5_NO_AUTH,
    }

    private fun probeSocks5NoAuth(
        host: String,
        port: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): SocksProbeResult {
        return Socket().use { socket ->
            try {
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            } catch (_: Exception) {
                return SocksProbeResult.CLOSED
            }

            socket.soTimeout = readTimeoutMs
            socket.tcpNoDelay = true

            try {
                socket.getOutputStream().writeSocks5NoAuthGreeting()
                val response = socket.getInputStream().readExactly(2) ?: return SocksProbeResult.NOT_SOCKS
                val version = response[0].toInt() and 0xFF
                val method = response[1].toInt() and 0xFF
                if (version == 0x05 && method == 0x00) {
                    SocksProbeResult.SOCKS5_NO_AUTH
                } else {
                    SocksProbeResult.NOT_SOCKS
                }
            } catch (_: SocketTimeoutException) {
                SocksProbeResult.NOT_SOCKS
            } catch (_: Exception) {
                SocksProbeResult.NOT_SOCKS
            }
        }
    }

    private fun OutputStream.writeSocks5NoAuthGreeting() {
        write(byteArrayOf(0x05, 0x01, 0x00))
        flush()
    }

    private fun probeHttpConnectNoAuth(
        host: String,
        port: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.soTimeout = readTimeoutMs
                socket.tcpNoDelay = true

                val request =
                    "CONNECT ifconfig.me:443 HTTP/1.1\r\n" +
                        "Host: ifconfig.me:443\r\n" +
                        "User-Agent: LocalProxyDetector/1.0\r\n" +
                        "Proxy-Connection: keep-alive\r\n" +
                        "\r\n"
                socket.getOutputStream().write(request.toByteArray(Charsets.ISO_8859_1))
                socket.getOutputStream().flush()

                val statusLine = socket.getInputStream().readAsciiLine(maxBytes = 256) ?: return false
                val parts = statusLine.trim().split(Regex("\\s+"), limit = 3)
                if (parts.size < 2 || !parts[0].startsWith("HTTP/")) return false
                val code = parts[1].toIntOrNull() ?: return false
                code == 200
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun InputStream.readExactly(byteCount: Int): ByteArray? {
        val buffer = ByteArray(byteCount)
        var offset = 0
        while (offset < byteCount) {
            val read = read(buffer, offset, byteCount - offset)
            if (read <= 0) return null
            offset += read
        }
        return buffer
    }

    private fun InputStream.readAsciiLine(maxBytes: Int): String? {
        val buffer = ByteArray(min(maxBytes, 1024))
        var count = 0
        while (count < maxBytes) {
            val b = read()
            if (b == -1) return null
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                if (count >= buffer.size) return null
                buffer[count] = b.toByte()
                count++
            }
        }
        return if (count == 0) null else String(buffer, 0, count, Charsets.ISO_8859_1)
    }
}
