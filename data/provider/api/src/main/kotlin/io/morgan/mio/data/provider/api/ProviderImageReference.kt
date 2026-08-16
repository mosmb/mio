package io.morgan.mio.data.provider.api

@JvmInline
public value class ProviderImageReference(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}
