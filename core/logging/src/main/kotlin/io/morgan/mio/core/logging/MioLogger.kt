package io.morgan.mio.core.logging

public enum class MioLogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}

public enum class MioLogEvent {
    NETWORK_REQUEST_STARTED,
    NETWORK_REQUEST_SUCCEEDED,
    NETWORK_REQUEST_FAILED,
}

public fun interface MioLogger {
    public fun log(
        level: MioLogLevel,
        event: MioLogEvent,
    )

    public companion object {
        public val None: MioLogger = MioLogger { _, _ -> }
    }
}
