package io.morgan.mio.core.network

public data class NetworkTimeouts(
    public val connectMillis: Long = 15_000,
    public val readMillis: Long = 30_000,
    public val writeMillis: Long = 30_000,
    public val callMillis: Long = 60_000,
) {
    init {
        require(connectMillis > 0)
        require(readMillis > 0)
        require(writeMillis > 0)
        require(callMillis > 0)
    }
}
