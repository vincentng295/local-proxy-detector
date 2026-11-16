package com.huskydg.localproxydetector.probe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object IfconfigClient {
    private const val ENDPOINT = "https://ifconfig.me/ip"
    private const val USER_AGENT = "LocalProxyDetector/1.0 (Android)"

    suspend fun fetchDirectIp(timeoutMs: Int = 7000): Result<String> = fetchIp(timeoutMs = timeoutMs)

    suspend fun fetchIpViaProxy(
        endpoint: ProxyEndpoint,
        timeoutMs: Int = 7000,
    ): Result<String> {
        // dokodemo-door style inbounds (dns-in, redirect-in, tproxy-in) are
        // not something java.net.Proxy can route through: they forward raw
        // bytes to a single fixed destination rather than acting as a
        // SOCKS/HTTP proxy, so there's no "connect to ifconfig.me via it" to
        // do here. The scan result itself (an open, unauthenticated
        // transparent inbound on loopback) is the finding -- there's nothing
        // further to fetch.
        val proxyType = when (endpoint.type) {
            ProxyType.SOCKS5 -> Proxy.Type.SOCKS
            ProxyType.HTTP -> Proxy.Type.HTTP
            ProxyType.DOKODEMO_DNS,
            ProxyType.DOKODEMO_HTTP,
            ProxyType.DOKODEMO_TLS,
            ProxyType.DOKODEMO_UNKNOWN,
            -> return Result.failure(
                IllegalArgumentException(
                    "${endpoint.type} is a transparent forward, not a SOCKS/HTTP proxy -- cannot route an IP check through it",
                ),
            )
        }
        return fetchIp(
            timeoutMs = timeoutMs,
            proxy = Proxy(proxyType, InetSocketAddress(endpoint.host, endpoint.port)),
        )
    }

    private suspend fun fetchIp(
        timeoutMs: Int,
        proxy: Proxy? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = URL(ENDPOINT)
        val connection = (if (proxy == null) url.openConnection() else url.openConnection(proxy))
        val https = connection as? HttpsURLConnection
            ?: return@withContext Result.failure(IllegalStateException("Not an HTTPS connection"))

        try {
            https.instanceFollowRedirects = true
            https.requestMethod = "GET"
            https.useCaches = false
            https.connectTimeout = timeoutMs
            https.readTimeout = timeoutMs
            https.setRequestProperty("User-Agent", USER_AGENT)
            https.setRequestProperty("Accept", "text/plain")

            val code = https.responseCode
            if (code !in 200..299) {
                val errorText = https.errorStream?.bufferedReader()?.use { it.readText() }?.trim()
                return@withContext Result.failure(
                    IOException(
                        buildString {
                            append("HTTP ")
                            append(code)
                            if (!errorText.isNullOrBlank()) {
                                append(": ")
                                append(errorText)
                            }
                        },
                    ),
                )
            }

            val body = https.inputStream.bufferedReader().use { it.readText() }.trim()
            if (body.isBlank()) {
                return@withContext Result.failure(IOException("Empty response body"))
            }
            Result.success(body)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            https.disconnect()
        }
    }
}

