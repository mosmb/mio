package io.morgan.mio.domain.sync

public class SyncInfrastructureException(
    public val kind: Kind,
) : RuntimeException(fixedMessage(kind)) {
    public enum class Kind {
        STORAGE,
        CONFIGURATION,
    }

    override fun toString(): String = "SyncInfrastructureException($kind)"

    private companion object {
        fun fixedMessage(kind: Kind): String =
            when (kind) {
                Kind.STORAGE -> "Synchronization storage failed"
                Kind.CONFIGURATION -> "Synchronization configuration is unavailable"
            }
    }
}
