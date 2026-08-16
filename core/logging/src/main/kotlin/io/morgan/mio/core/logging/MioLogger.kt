package io.morgan.mio.core.logging

public fun interface MioLogger {
    public fun log(
        level: MioLogLevel,
        event: MioLogEvent,
    )

    public companion object {
        public val None: MioLogger = MioLogger { _, _ -> }
    }
}
