package com.example.service.call

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

class CallNetworkMonitor(
    private val context: Context,
    private val onNetworkQualityChanged: (NetworkQualityState) -> Unit,
    private val onNetworkInterruption: () -> Unit,
    private val onNetworkRestored: () -> Unit
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isMonitoring = false
    private var activeNetwork: Network? = null

    companion object {
        private const val TAG = "CallNetworkMonitor"
    }

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available: $network")
                activeNetwork = network
                onNetworkRestored()
                evaluateNetworkQuality(network)
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Network lost: $network")
                if (activeNetwork == network) {
                    activeNetwork = null
                    onNetworkInterruption()
                    onNetworkQualityChanged(NetworkQualityState.RECONNECTING)
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                evaluateCapabilities(networkCapabilities)
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
            val current = connectivityManager.activeNetwork
            if (current != null) {
                activeNetwork = current
                evaluateNetworkQuality(current)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network monitor callback: ${e.message}")
        }
    }

    private fun evaluateNetworkQuality(network: Network) {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return
        evaluateCapabilities(caps)
    }

    private fun evaluateCapabilities(caps: NetworkCapabilities) {
        val downstreamKbps = caps.linkDownstreamBandwidthKbps
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        val quality = when {
            downstreamKbps > 2000 || isWifi -> NetworkQualityState.EXCELLENT
            downstreamKbps > 800 || isCellular -> NetworkQualityState.GOOD
            downstreamKbps > 0 -> NetworkQualityState.POOR
            else -> NetworkQualityState.POOR
        }
        onNetworkQualityChanged(quality)
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false
        try {
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
            networkCallback = null
            activeNetwork = null
            Log.d(TAG, "Network monitor stopped.")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping network callback: ${e.message}")
        }
    }
}
