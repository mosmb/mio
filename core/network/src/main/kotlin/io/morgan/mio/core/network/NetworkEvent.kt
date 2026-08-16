package io.morgan.mio.core.network

public sealed interface NetworkEvent {
    public val method: String
    public val host: String

    public data class Request(
        override val method: String,
        override val host: String,
    ) : NetworkEvent {
        init {
            require(method.isNotBlank())
            require(host.isNotBlank())
        }
    }

    public data class Response(
        override val method: String,
        override val host: String,
        public val statusCode: Int,
    ) : NetworkEvent {
        init {
            require(method.isNotBlank())
            require(host.isNotBlank())
            require(statusCode in 100..599)
        }
    }

    public data class Failure(
        override val method: String,
        override val host: String,
        public val category: NetworkFailureCategory,
    ) : NetworkEvent {
        init {
            require(method.isNotBlank())
            require(host.isNotBlank())
        }
    }
}
