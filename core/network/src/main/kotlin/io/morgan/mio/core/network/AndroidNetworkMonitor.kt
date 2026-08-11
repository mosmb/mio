package io.morgan.mio.core.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

public class AndroidNetworkMonitor internal constructor(
    private val connectivityAccess: ConnectivityAccess,
) : NetworkMonitor {
    public constructor(connectivityManager: ConnectivityManager) : this(AndroidConnectivityAccess(connectivityManager))

    override val availability: Flow<NetworkAvailability> =
        callbackFlow {
            val initialCapabilities =
                try {
                    connectivityAccess.currentCapabilities()
                } catch (_: SecurityException) {
                    trySend(NetworkAvailability.UNAVAILABLE)
                    close()
                    return@callbackFlow
                }

            trySend(initialCapabilities.toAvailability(blocked = false))

            var lastCapabilities: NetworkCapabilities? = initialCapabilities
            var blocked = false
            val callback =
                object : ConnectivityCallback {
                    override fun onCapabilitiesChanged(networkCapabilities: NetworkCapabilities) {
                        lastCapabilities = networkCapabilities
                        trySend(networkCapabilities.toAvailability(blocked))
                    }

                    override fun onLost() {
                        lastCapabilities = null
                        trySend(NetworkAvailability.UNAVAILABLE)
                    }

                    override fun onBlockedStatusChanged(blockedStatus: Boolean) {
                        blocked = blockedStatus
                        trySend(lastCapabilities.toAvailability(blocked))
                    }
                }

            val registration =
                try {
                    connectivityAccess.registerDefaultNetworkCallback(callback)
                } catch (_: SecurityException) {
                    trySend(NetworkAvailability.UNAVAILABLE)
                    close()
                    return@callbackFlow
                }

            awaitClose {
                try {
                    registration.unregister()
                } catch (_: SecurityException) {
                    // Permission can be revoked while the flow is active.
                } catch (_: IllegalArgumentException) {
                    // The platform may report an already-unregistered callback during teardown races.
                }
            }
        }.distinctUntilChanged().conflate()

    private fun NetworkCapabilities?.toAvailability(blocked: Boolean): NetworkAvailability =
        if (!blocked &&
            this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            this.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) {
            NetworkAvailability.AVAILABLE
        } else {
            NetworkAvailability.UNAVAILABLE
        }
}

internal interface ConnectivityAccess {
    fun currentCapabilities(): NetworkCapabilities?

    fun registerDefaultNetworkCallback(callback: ConnectivityCallback): ConnectivityRegistration
}

internal interface ConnectivityCallback {
    fun onCapabilitiesChanged(networkCapabilities: NetworkCapabilities)

    fun onLost()

    fun onBlockedStatusChanged(blockedStatus: Boolean)
}

internal fun interface ConnectivityRegistration {
    fun unregister()
}

private class AndroidConnectivityAccess(
    private val connectivityManager: ConnectivityManager,
) : ConnectivityAccess {
    override fun currentCapabilities(): NetworkCapabilities? =
        connectivityManager.activeNetwork?.let(connectivityManager::getNetworkCapabilities)

    override fun registerDefaultNetworkCallback(callback: ConnectivityCallback): ConnectivityRegistration {
        val platformCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    callback.onCapabilitiesChanged(networkCapabilities)
                }

                override fun onLost(network: Network) {
                    callback.onLost()
                }

                override fun onBlockedStatusChanged(
                    network: Network,
                    blocked: Boolean,
                ) {
                    callback.onBlockedStatusChanged(blocked)
                }
            }

        connectivityManager.registerDefaultNetworkCallback(platformCallback)
        return ConnectivityRegistration {
            connectivityManager.unregisterNetworkCallback(platformCallback)
        }
    }
}
