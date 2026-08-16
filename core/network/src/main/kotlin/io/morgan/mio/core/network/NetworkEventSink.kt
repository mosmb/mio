package io.morgan.mio.core.network

public fun interface NetworkEventSink {
    public fun record(event: NetworkEvent)

    public companion object {
        public val None: NetworkEventSink = NetworkEventSink { }
    }
}
