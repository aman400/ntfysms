package dev.zophop.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import dev.zophop.worker.ForwardingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConnectivityMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            triggerFlush()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: android.net.NetworkCapabilities) {
            triggerFlush()
        }
    }

    private fun triggerFlush() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ForwardingManager.getInstance(context).processPendingQueue()
            } catch (e: Exception) {
                // Ignore network callback errors
            }
        }
    }

    fun register() {
        try {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Fallback or permission check
        }
    }

    fun unregister() {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignore unregister exception
        }
    }
}
