package app.it.fast4x.rimusic.extensions.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class AndroidConnectivityObserverLegacy(context: Context) {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!

    private val _networkStatus = Channel<Boolean>(Channel.CONFLATED)
    val networkStatus = _networkStatus.receiveAsFlow()

    private fun isValidated(network: Network): Boolean =
        connectivityManager.getNetworkCapabilities(network)
            ?.let { capabilities ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } == true

    private val internetNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            _networkStatus.trySend(isValidated(network))
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, capabilities)
            _networkStatus.trySend(
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            )
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            _networkStatus.trySend(false)
        }

        override fun onUnavailable() {
            super.onUnavailable()
            _networkStatus.trySend(false)
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, internetNetworkCallback)
        _networkStatus.trySend(
            connectivityManager.activeNetwork?.let(::isValidated) == true
        )
    }

    fun unregister() {
        connectivityManager.unregisterNetworkCallback(internetNetworkCallback)
    }
}
