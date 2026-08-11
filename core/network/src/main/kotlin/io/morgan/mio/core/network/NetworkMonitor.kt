package io.morgan.mio.core.network

import kotlinx.coroutines.flow.Flow

public enum class NetworkAvailability {
    AVAILABLE,
    UNAVAILABLE,
}

public interface NetworkMonitor {
    public val availability: Flow<NetworkAvailability>
}
