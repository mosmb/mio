package io.morgan.mio.data.provider.api

@JvmInline
public value class ProviderCollectionEntryId(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}
