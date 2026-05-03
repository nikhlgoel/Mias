package dev.kid.core.resilience

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Real-time connectivity state for the device. */
data class ConnectivityState(
    val isConnected: Boolean = false,
    val isWifi: Boolean = false,
    val isMetered: Boolean = true,
    val isVpn: Boolean = false,
    val downstreamBandwidthKbps: Int = 0,
)

/**
 * Monitors network connectivity and provides reactive state.
 * Used by download manager, agent web tools, and mesh networking.
 */
@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(currentState())
    val state: StateFlow<ConnectivityState> = _state.asStateFlow()

    val isOnline: Boolean get() = _state.value.isConnected

    /** Observe connectivity changes as a Flow. */
    fun observe(): Flow<ConnectivityState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val newState = currentState()
                _state.value = newState
                trySend(newState)
            }

            override fun onLost(network: Network) {
                val newState = currentState()
                _state.value = newState
                trySend(newState)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities,
            ) {
                val newState = buildState(caps)
                _state.value = newState
                trySend(newState)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        // Emit initial state
        trySend(currentState())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    private fun currentState(): ConnectivityState {
        val network = connectivityManager.activeNetwork ?: return ConnectivityState()
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return ConnectivityState()
        return buildState(caps)
    }

    private fun buildState(caps: NetworkCapabilities): ConnectivityState {
        return ConnectivityState(
            isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            downstreamBandwidthKbps = caps.linkDownstreamBandwidthKbps,
        )
    }
}
