package `in`.ntfysms.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import `in`.ntfysms.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

sealed class PreCheckResult {
    object Passed : PreCheckResult()
    data class Failed(val reason: String) : PreCheckResult()
}

class PreCheckManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {

    suspend fun verifyPreChecks(settings: AppSettings): PreCheckResult = withContext(Dispatchers.IO) {
        // 1. Check Internet Connection if required
        if (settings.requireInternet) {
            if (!isInternetAvailable()) {
                return@withContext PreCheckResult.Failed("No active internet connection")
            }
        }

        // 2. Check VPN Connection if required
        if (settings.requireVpn) {
            if (!isVpnConnected()) {
                return@withContext PreCheckResult.Failed("Active VPN connection required (e.g. Tailscale, WireGuard)")
            }
        }

        // 3. Check Target Server Healthcheck if required
        if (settings.requireHealthcheck) {
            val targetUrl = settings.activeServerUrl
            if (targetUrl.isNotBlank()) {
                val healthResult = checkServerHealth(targetUrl)
                if (healthResult is PreCheckResult.Failed) {
                    return@withContext healthResult
                }
            }
        }

        PreCheckResult.Passed
    }

    fun isInternetAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isVpnConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            val activeNetwork = cm.activeNetwork
            if (activeNetwork != null) {
                val caps = cm.getNetworkCapabilities(activeNetwork)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true
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

    suspend fun checkServerHealth(serverUrl: String): PreCheckResult = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = serverUrl.trim().trimEnd('/')
            val healthUrl = "$cleanUrl/health"

            val request = Request.Builder()
                .url(healthUrl)
                .get()
                .build()

            clientCall(request)
        } catch (e: Exception) {
            // Fallback to checking root server URL
            try {
                val cleanUrl = serverUrl.trim().trimEnd('/')
                val rootRequest = Request.Builder()
                    .url(cleanUrl)
                    .head()
                    .build()
                clientCall(rootRequest)
            } catch (rootError: Exception) {
                PreCheckResult.Failed("Server healthcheck ping failed: ${rootError.javaClass.simpleName}")
            }
        }
    }

    private fun clientCall(request: Request): PreCheckResult {
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 401 || response.code == 403 || response.code == 404) {
                    // Server responded -> host is reachable
                    PreCheckResult.Passed
                } else {
                    PreCheckResult.Failed("Healthcheck HTTP status: ${response.code}")
                }
            }
        } catch (e: Exception) {
            PreCheckResult.Failed("Server healthcheck unreachable: ${e.message}")
        }
    }
}
