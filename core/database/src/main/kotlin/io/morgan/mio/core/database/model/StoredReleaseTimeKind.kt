package io.morgan.mio.core.database.model

public enum class StoredReleaseTimeKind(
    public val serializedValue: String,
) {
    UNKNOWN("unknown"),
    TRUSTED("trusted"),
    UNTRUSTED("untrusted"),
    ;

    public companion object {
        public fun fromSerializedValue(value: String): StoredReleaseTimeKind =
            entries.firstOrNull { it.serializedValue == value }
                ?: throw IllegalArgumentException("Unknown release time kind value: $value")
    }
}
