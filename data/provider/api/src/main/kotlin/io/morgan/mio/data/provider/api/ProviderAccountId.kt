package io.morgan.mio.data.provider.api

@JvmInline
public value class ProviderAccountId(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}
