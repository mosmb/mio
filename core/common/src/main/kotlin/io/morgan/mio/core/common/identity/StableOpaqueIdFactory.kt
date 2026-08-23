package io.morgan.mio.core.common.identity

import java.security.MessageDigest

public object StableOpaqueIdFactory {
    public fun create(
        namespace: String,
        kind: String,
        remoteId: String,
    ): String {
        require(namespace.isNotBlank())
        require(kind.isNotBlank())
        require(remoteId.isNotBlank())

        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest("$namespace|$kind|$remoteId".toByteArray(Charsets.UTF_8))
        return digest
            .take(16)
            .joinToString(separator = "") { byte ->
                ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1)
            }
    }
}
