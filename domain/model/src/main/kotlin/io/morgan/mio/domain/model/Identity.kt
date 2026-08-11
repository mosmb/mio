package io.morgan.mio.domain.model

@JvmInline
public value class AccountId(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
public value class MediaId(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
public value class CollectionEntryId(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
public value class OperationId(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}
