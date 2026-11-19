package net.devemperor.tapedeck.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetState(val connected: Boolean, val unmetered: Boolean)

/** Follows the default network, so "Wi-Fi only" downloads can wait and continue by themselves. */
class NetworkMonitor(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val _state = MutableStateFlow(read(connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)))
    val state: StateFlow<NetState> = _state.asStateFlow()

    init {
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _state.value = read(caps)
            }

            override fun onLost(network: Network) {
                _state.value = NetState(connected = false, unmetered = false)
            }
        })
    }

    private fun read(caps: NetworkCapabilities?): NetState {
        if (caps == null) return NetState(connected = false, unmetered = false)
        val connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return NetState(connected, unmetered)
    }
}
