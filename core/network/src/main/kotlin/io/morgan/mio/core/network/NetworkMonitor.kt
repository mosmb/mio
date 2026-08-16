package io.morgan.mio.core.network

import kotlinx.coroutines.flow.Flow

public interface NetworkMonitor {
    public val availability: Flow<NetworkAvailability>
}
