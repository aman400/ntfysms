package `in`.ntfysms.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import `in`.ntfysms.data.DeliveryNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

sealed class PreCheckResult {
    object Passed : PreCheckResult()
    data class Failed(val reason: String) : PreCheckResult()
}

open class PreCheckManager(
    private val context: Context?,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {

    open suspend fun verifyNodePreChecks(node: DeliveryNode): PreCheckResult = withContext(Dispatchers.IO) {
        if (!node.isEnabled) {
            return@withContext PreCheckResult.Failed("Node disabled")
        }

        if (node.serverUrl.isBlank()) {
            return@withContext PreCheckResult.Failed("Server URL is empty")
        }

        // 1. Node-level Internet Check
        if (node.requireInternet) {
            if (!isInternetAvailable()) {
                return@withContext PreCheckResult.Failed("Internet connection unavailable for ${node.name}")
            }
        }

        // 2. Node-level VPN Check
        if (node.requireVpn) {
            if (!isVpnConnected()) {
                return@withContext PreCheckResult.Failed("VPN required for ${node.name} (Awaiting Tailscale/VPN)")
            }
        }

        // 3. Node-level Custom Server Healthcheck Ping
        if (node.requireHealthcheck) {
            val healthResult = checkCustomServerHealth(
                serverUrl = node.serverUrl,
                customUrl = node.customHealthcheckUrl,
                method = node.healthcheckMethod,
                headers = node.healthcheckHeaders,
                expectedStatus = node.healthcheckExpectedStatus
            )
            if (healthResult is PreCheckResult.Failed) {
                return@withContext PreCheckResult.Failed("Healthcheck failed for ${node.name}: ${healthResult.reason}")
            }
        }

        PreCheckResult.Passed
    }

    fun isInternetAvailable(): Boolean {
        val ctx = context ?: return true // Default true in test environment without context
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isVpnConnected(): Boolean {
        val ctx = context
        if (ctx != null) {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetwork
                if (activeNetwork != null) {
                    val caps = cm.getNetworkCapabilities(activeNetwork)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        return true
                    }
                }
            }
        }

        // Fallback: Check network interfaces for tun0, ppp0, tailscale, etc.
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isUp) {
                    val name = iface.name.lowercase()
                    if (name.contains("tun") || name.contains("ppp") || name.contains("tailscale") || name.contains("wireguard") || name.contains("utun")) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    suspend fun checkCustomServerHealth(
        serverUrl: String,
        customUrl: String = "",
        method: String = "GET",
        headers: String = "",
        expectedStatus: Int = 200
    ): PreCheckResult = withContext(Dispatchers.IO) {
        try {
            val targetUrl = if (customUrl.isNotBlank()) {
                customUrl.trim()
            } else {
                val cleanUrl = serverUrl.trim().trimEnd('/')
                if (cleanUrl.endsWith("/health")) cleanUrl else "$cleanUrl/health"
            }

            val requestBuilder = Request.Builder().url(targetUrl)

            when (method.uppercase()) {
                "HEAD" -> requestBuilder.head()
                "POST" -> requestBuilder.post(ByteArray(0).toRequestBody(null))
                else -> requestBuilder.get()
            }

            if (headers.isNotBlank()) {
                headers.split("\n", ",").forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        requestBuilder.addHeader(parts[0].trim(), parts[1].trim())
                    }
                }
            }

            val request = requestBuilder.build()
            clientCallCustom(request, expectedStatus)
        } catch (e: Exception) {
            PreCheckResult.Failed("Healthcheck request error: ${e.message}")
        }
    }

    suspend fun checkServerHealth(serverUrl: String): PreCheckResult = withContext(Dispatchers.IO) {
        checkCustomServerHealth(serverUrl = serverUrl)
    }

    private fun clientCallCustom(request: Request, expectedStatus: Int): PreCheckResult {
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                if (code == expectedStatus || (expectedStatus == 200 && response.isSuccessful) || code in listOf(200, 204, 401, 403, 404)) {
                    PreCheckResult.Passed
                } else {
                    PreCheckResult.Failed("HTTP status $code (expected $expectedStatus)")
                }
            }
        } catch (e: Exception) {
            PreCheckResult.Failed("Unreachable: ${e.message}")
        }
    }
}
