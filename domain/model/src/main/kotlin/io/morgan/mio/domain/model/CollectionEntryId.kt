package io.morgan.mio.domain.model

@JvmInline
public value class CollectionEntryId(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}
